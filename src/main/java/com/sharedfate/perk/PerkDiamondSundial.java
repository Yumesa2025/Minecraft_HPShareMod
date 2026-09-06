package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.DiamondSundialEffect;
import com.sharedfate.sync.TitleMessenger;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@code diamond_sundial} 증강(골드 「해시계」)의 집행부.
 *
 * <p>해시계를 주 손에 들고 <b>허공을</b> 우클릭하면 반경 안의 다이아몬드 광석 자리에 파티클이
 * 뜨고, 몇 개를 찾았는지가 액션바에 뜬다. 그다음 쿨타임이 걸린다.
 *
 * <h2>등록 지점</h2>
 * <p>{@code UseItemCallback.EVENT} 에 붙는다. {@link PerkOreExchange} 와 같은 자리이고, 그쪽은
 * 나무 도끼만 반응하므로 서로 부딪히지 않는다(둘 다 자기 아이템이 아니면 {@code PASS} 를
 * 돌려준다). 시계는 바닐라에서 허공 우클릭에 아무 동작이 없어 가로채도 잃는 것이 없다.
 *
 * <h2>통신 규약을 올리지 않는다</h2>
 * <p>파티클({@code ClientboundLevelParticlesPacket})과 액션바({@code ClientboundSetActionBarTextPacket})
 * 는 둘 다 바닐라 패킷이다. 모드를 깔지 않은 클라이언트도 그대로 본다.
 *
 * <h2>성능 — 청크 섹션 팔레트로 먼저 거른다</h2>
 * <p>반경 20칸을 순진하게 훑으면 41³ = 68,921칸을 매번 {@code getBlockState} 해야 한다. 대신
 * 여기서는 세 겹으로 줄인다.
 *
 * <ol>
 *   <li><b>이미 올라온 청크만</b> 본다. {@code getChunkNow} 가 null 이면 건너뛴다 — 탐지 때문에
 *       청크를 새로 불러오면 그것이 가장 비싼 일이 된다.</li>
 *   <li><b>{@code LevelChunkSection.maybeHas}</b> 로 섹션의 팔레트를 한 번 훑어 다이아몬드 광석이
 *       아예 없는 16³ 덩어리를 통째로 버린다. 팔레트는 보통 항목이 수십 개뿐이라 4,096칸을
 *       읽는 것과 비교가 안 되게 싸다.</li>
 *   <li>남은 섹션 안에서도 <b>거리부터 재고</b> 구 안에 드는 칸만 {@code getBlockState} 한다.</li>
 * </ol>
 *
 * <p>반경 20이면 상자는 4×4 청크 × 4 섹션 = 최대 64개 섹션에 걸친다. 굴속에서 실제로 팔레트를
 * 통과하는 섹션은 보통 0~3개이므로 한 번 쓸 때 읽는 블록은 대개 만 칸 미만이고, 다이아몬드가
 * 아예 없으면 0칸이다. 모든 섹션이 통과하는 최악에도 구 안쪽 33,510칸을 넘지 않는다.
 *
 * <h2>이 클래스가 하지 못하는 일</h2>
 * <p><b>돌 뒤에 묻힌 광석은 파티클이 보이지 않는다.</b> 파티클은 클라이언트가 지형 뒤에 깊이
 * 시험을 걸어 그리므로 벽 너머는 가려진다. 그래서 광석 자리뿐 아니라 <b>그 광석에 맞닿은 빈
 * 칸</b>에도 함께 띄운다 — 굴이나 파 놓은 갱도에 노출된 광석은 그 빈 칸의 파티클로 보인다.
 * 사방이 돌로 막힌 광석은 개수(액션바)로만 알 수 있다. 블록을 발광시키는 방법은 26.2 에 없다.
 */
public final class PerkDiamondSundial {
	/** 광석 자리에 띄우는 파티클 수. */
	private static final int PARTICLES_AT_ORE = 6;
	/** 광석에 맞닿은 빈 칸에 띄우는 파티클 수. */
	private static final int PARTICLES_AT_OPENING = 4;
	/** 파티클이 퍼지는 반지름(칸). 블록 한 칸 안에 머물게 한다. */
	private static final double SPREAD = 0.22;

	private static volatile boolean warned;

	private PerkDiamondSundial() {
	}

	// ------------------------------------------------------------------ 지급

	/**
	 * 증강 하나가 가진 {@code diamond_sundial} 효과의 해시계를 지급한다.
	 *
	 * <p><b>부르는 곳은 {@link PerkGrantChain#run} 하나뿐이다.</b> {@link PerkEffect#apply} 는
	 * 접속·부활·효과 갱신 때마다 다시 불리므로 거기서 주면 접속할 때마다 해시계가 늘어난다.
	 *
	 * <p>아이템을 넣는 곳도 {@link PerkItemGrants} 와 같다. 개인 인벤토리가 아니라 팀 공유
	 * 목록이고, 자리가 없으면 바닥에 떨어뜨리지 않고 넘침 목록에 남긴다.
	 *
	 * @return 실제로 지급한 개수. 줄 것이 없었으면 0
	 */
	public static int grantOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk) {
		if (state == null || perk == null) {
			return 0;
		}

		List<ItemStack> granted = new ArrayList<>();
		for (PerkEffect effect : perk.effects()) {
			if (!(effect instanceof DiamondSundialEffect sundial)) {
				continue;
			}
			try {
				ItemStack stack = sundial.createItem();
				if (stack != null && !stack.isEmpty()) {
					granted.add(stack);
				}
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("증강 '{}' 의 해시계 지급에 실패했습니다.", perk.id(), error);
			}
		}
		if (granted.isEmpty()) {
			return 0;
		}

		state.overflowItems.addAll(granted);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		state.overflowItems.removeIf(ItemStack::isEmpty);
		SharedFateMod.LOGGER.info("[PERK] 증강 {} 해시계 지급={}", perk.id(), granted.size());

		if (server != null && team != null) {
			refreshScreens(server, team);
		}
		return granted.size();
	}

	/** 공유 목록을 직접 고쳤으니 접속 중인 팀원의 화면을 맞춰 준다. */
	private static void refreshScreens(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online == null || online.containerMenu == null) {
				continue;
			}
			online.containerMenu.broadcastChanges();
		}
	}

	// ------------------------------------------------------------------ 우클릭

	/** {@code UseItemCallback.EVENT} 에 붙는 지점. */
	public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		try {
			return handle(player, level, hand);
		} catch (RuntimeException error) {
			warnOnce(error);
			return InteractionResult.PASS;
		}
	}

	private static InteractionResult handle(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || level == null || level.isClientSide()
				|| !(player instanceof ServerPlayer user)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!DiamondSundialEffect.isSundial(held)) {
			return InteractionResult.PASS;
		}

		TeamState state = TeamLookup.stateOf(user.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return InteractionResult.PASS;
		}
		DiamondSundialEffect effect = findEffect(state);
		if (effect == null) {
			// 증강을 잃었으면 시계는 남아 있어도 아무 일도 하지 않는다.
			return InteractionResult.PASS;
		}
		if (user.getCooldowns().isOnCooldown(held)) {
			return InteractionResult.FAIL;
		}

		ServerLevel serverLevel = user.level();
		List<BlockPos> found = scan(serverLevel, user.blockPosition(), effect.radius(),
				effect.maxResults());
		for (BlockPos ore : found) {
			showOre(serverLevel, user, ore);
		}

		TitleMessenger.showActionBar(user, Component.literal(found.isEmpty()
				? "[해시계] 근처에 다이아몬드가 없습니다"
				: "[해시계] " + effect.radius() + "칸 안에 다이아몬드 광석 " + found.size() + "개"));
		// 찾지 못했을 때도 쿨타임은 건다. 그래야 연타로 훑는 일이 없다.
		user.getCooldowns().addCooldown(held, effect.cooldownTicks());
		return InteractionResult.SUCCESS;
	}

	/** 팀이 가진 해시계 효과. 없으면 null. */
	private static @Nullable DiamondSundialEffect findEffect(TeamState state) {
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof DiamondSundialEffect sundial) {
					return sundial;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ 훑기

	/**
	 * {@code center} 에서 {@code radius} 칸 안의 다이아몬드 광석 자리.
	 *
	 * <p>가까운 것부터 최대 {@code maxResults} 개까지 돌려준다. 훑는 방법과 그 비용은 클래스
	 * 문서에 적어 뒀다.
	 */
	public static List<BlockPos> scan(ServerLevel level, BlockPos center, int radius,
			int maxResults) {
		if (level == null || center == null || radius < 1 || maxResults < 1) {
			return List.of();
		}
		Found found = new Found(center, radius, maxResults);

		int minX = center.getX() - radius;
		int maxX = center.getX() + radius;
		int minZ = center.getZ() - radius;
		int maxZ = center.getZ() + radius;
		int minY = Math.max(level.getMinY(), center.getY() - radius);
		int maxY = Math.min(level.getMaxY(), center.getY() + radius);
		if (minY > maxY) {
			return List.of();
		}

		int firstSection = level.getSectionIndex(minY);
		int lastSection = level.getSectionIndex(maxY);

		for (int chunkX = minX >> 4; chunkX <= (maxX >> 4) && !found.full(); chunkX++) {
			for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4) && !found.full(); chunkZ++) {
				// 올라와 있는 청크만 본다. 탐지 때문에 청크를 불러오면 그것이 가장 비싸다.
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int index = firstSection; index <= lastSection && !found.full(); index++) {
					if (index < 0 || index >= sections.length) {
						continue;
					}
					LevelChunkSection section = sections[index];
					if (section == null || section.hasOnlyAir()) {
						continue;
					}
					// 팔레트 한 번으로 16³ 덩어리를 통째로 거른다.
					if (!section.maybeHas(DiamondSundialEffect::isDiamondOre)) {
						continue;
					}
					scanSection(section, chunkX << 4,
							level.getSectionYFromSectionIndex(index) << 4, chunkZ << 4,
							minX, maxX, minY, maxY, minZ, maxZ, found);
				}
			}
		}
		return found.hits();
	}

	/**
	 * 섹션 하나에서 상자와 겹치는 부분만 훑는다.
	 *
	 * <p>{@code y → z → x} 순으로 도는 것은 {@code PalettedContainer} 의 첨자가
	 * {@code (y << 8) | (z << 4) | x} 라 x 를 안쪽에 두어야 이어 읽기 때문이다.
	 */
	private static void scanSection(LevelChunkSection section, int originX, int originY,
			int originZ, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Found found) {
		int startX = Math.max(minX, originX);
		int endX = Math.min(maxX, originX + 15);
		int startY = Math.max(minY, originY);
		int endY = Math.min(maxY, originY + 15);
		int startZ = Math.max(minZ, originZ);
		int endZ = Math.min(maxZ, originZ + 15);

		for (int y = startY; y <= endY; y++) {
			for (int z = startZ; z <= endZ; z++) {
				for (int x = startX; x <= endX; x++) {
					if (!found.inRange(x, y, z)) {
						continue;
					}
					BlockState state = section.getBlockState(x - originX, y - originY, z - originZ);
					if (!DiamondSundialEffect.isDiamondOre(state)) {
						continue;
					}
					if (!found.add(x, y, z)) {
						return;
					}
				}
			}
		}
	}

	/**
	 * 반경 안의 광석 자리를 모은다.
	 *
	 * <p>바로 {@code maxResults} 개에서 끊지 않고 그 {@value #OVERSCAN} 배까지 모은 다음 거리순으로
	 * 잘라 낸다. 훑는 순서는 청크·섹션 순이라 그대로 끊으면 「구석에 몰린 16개」가 나온다.
	 * 상한을 배수로 두어 다이아몬드 광석으로 벽을 쌓아 둔 경우에도 목록이 무한정 커지지 않는다.
	 */
	public static final class Found {
		/** 자르기 전에 모아 두는 배수. */
		public static final int OVERSCAN = 4;

		private final int centerX;
		private final int centerY;
		private final int centerZ;
		private final long radiusSquared;
		private final int maxResults;
		private final int collectLimit;
		private final List<BlockPos> positions = new ArrayList<>();

		public Found(BlockPos center, int radius, int maxResults) {
			this.centerX = center.getX();
			this.centerY = center.getY();
			this.centerZ = center.getZ();
			this.radiusSquared = (long) radius * radius;
			this.maxResults = Math.max(1, maxResults);
			this.collectLimit = this.maxResults * OVERSCAN;
		}

		/** 더 모을 필요가 없는가. */
		public boolean full() {
			return positions.size() >= collectLimit;
		}

		/** 이 칸이 구 안에 드는가. 상자가 아니라 구로 재야 「20칸」이 방향에 상관없이 같다. */
		public boolean inRange(int x, int y, int z) {
			return distanceSquared(x, y, z) <= radiusSquared;
		}

		/**
		 * 찾은 자리를 넣는다.
		 *
		 * @return 더 받을 수 있으면 true, 상한에 닿았으면 false
		 */
		public boolean add(int x, int y, int z) {
			if (full()) {
				return false;
			}
			positions.add(new BlockPos(x, y, z));
			return !full();
		}

		/** 가까운 것부터 최대 {@code maxResults} 개. */
		public List<BlockPos> hits() {
			if (positions.size() > 1) {
				positions.sort(Comparator.comparingLong(
						pos -> distanceSquared(pos.getX(), pos.getY(), pos.getZ())));
			}
			return List.copyOf(positions.subList(0, Math.min(maxResults, positions.size())));
		}

		private long distanceSquared(int x, int y, int z) {
			long dx = x - centerX;
			long dy = y - centerY;
			long dz = z - centerZ;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	// ------------------------------------------------------------------ 보여 주기

	/**
	 * 광석 한 자리를 보여 준다.
	 *
	 * <p>광석 자리 자체와, 그 광석에 맞닿은 빈 칸에 함께 띄운다. 파티클은 지형 뒤에 가려지므로
	 * 돌 안에 묻힌 자리는 그대로는 보이지 않고, 굴이나 갱도 쪽으로 노출된 면이 있어야 보인다.
	 *
	 * <p>파티클은 <b>쓴 사람에게만</b> 보낸다.
	 */
	private static void showOre(ServerLevel level, ServerPlayer user, BlockPos ore) {
		sendAt(level, user, ore, PARTICLES_AT_ORE);
		for (Direction direction : Direction.values()) {
			BlockPos side = ore.relative(direction);
			BlockState state = loadedStateAt(level, side);
			// 돌로 막힌 면은 어차피 안 보인다. 물이나 반블록처럼 속이 비치는 면도 띄운다.
			if (state != null && !state.isSolidRender()) {
				sendAt(level, user, side, PARTICLES_AT_OPENING);
			}
		}
	}

	private static void sendAt(ServerLevel level, ServerPlayer user, BlockPos pos, int count) {
		level.sendParticles(user, DiamondSundialEffect.particle(),
				// 거리 제한을 넘기고(기본 32칸) 파티클 설정이 「최소」여도 보이게 한다.
				true, true,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				count, SPREAD, SPREAD, SPREAD, 0.0);
	}

	/**
	 * 이미 올라와 있는 청크에서만 블록을 읽는다.
	 *
	 * <p>{@code level.getBlockState} 를 그냥 쓰면 아직 안 올라온 청크를 불러온다.
	 */
	private static @Nullable BlockState loadedStateAt(ServerLevel level, BlockPos pos) {
		if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
			return null;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk == null ? null : chunk.getBlockState(pos);
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn("해시계 처리에 실패했습니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		warned = false;
	}
}
