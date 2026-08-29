package com.sharedfate.perk;

import com.mojang.datafixers.util.Pair;
import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.CompassTargetEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.SharedItemList;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 나침반 지시 증강({@code compass_target})의 집행부.
 *
 * <p>{@link CompassTargetEffect} 가 "무엇을 어디서 얼마나 넓게 찾을 것인가"만 들고 있고,
 * 실제로 찾아서 나침반에 꽂는 일은 전부 여기서 한다. {@code max_health_lock} 과
 * {@link PerkHealthRules} 의 관계와 같은 구도다.
 *
 * <h2>어떻게 가리키게 하는가</h2>
 * <p>바닐라의 자철석 나침반과 같은 길을 쓴다. 26.2 의 나침반은
 * {@code assets/minecraft/items/compass.json} 에서 {@code minecraft:lodestone_tracker} 성분이
 * 붙어 있는지로 갈라지고, 붙어 있으면 그 성분이 가리키는 자리를 향한다. 그래서 우리가 할 일은
 * {@code DataComponents.LODESTONE_TRACKER} 에
 * {@code new LodestoneTracker(Optional.of(위치), false)} 를 꽂는 것뿐이다. 클라이언트에 아무것도
 * 깔지 않아도 바닐라 클라이언트가 그대로 그려 준다.
 *
 * <h2>{@code tracked} 는 반드시 {@code false} 여야 한다</h2>
 * <p>{@code LodestoneTracker.tick(ServerLevel)} 은 {@code tracked} 가 참일 때 그 자리에 자철석
 * 블록이 실제로 있는지 확인하고, 없으면 성분을 <b>빈 값으로 갈아 버린다</b>. 요새에는 자철석이
 * 없으므로 참으로 꽂으면 다음 틱에 지워진다. 거짓이면 {@code tick} 이 맨 앞에서 그대로 돌려주고
 * 끝나므로 우리 값이 살아남는다.
 *
 * <h2>어떤 나침반이 우리 것인가</h2>
 * <p>증강을 잃으면 성분을 걷어내야 하는데, 플레이어가 직접 만든 자철석 나침반까지 망가뜨리면
 * 안 된다. 구분 기준은 {@code tracked} 다. 바닐라에서 {@code tracked} 가 거짓인 성분은 절대
 * 생기지 않는다. {@code CompassItem.useOn} 은 언제나 참으로 만들고, 자철석이 사라진 나침반도
 * "참 + 빈 목표"가 되지 별도의 값이 되지 않는다. 그래서 <b>{@code tracked} 가 거짓이고 목표가
 * 있는</b> 성분은 우리가 꽂은 것뿐이고, 걷어낼 때도 그것만 본다({@link #isOurs}).
 *
 * <h2>인벤토리는 팀이 통째로 공유한다</h2>
 * <p>이 모드에서 팀원의 인벤토리는 {@link TeamState#mainItems} 하나를 함께 쓴다. 그래서
 * 손보는 대상도 플레이어별 인벤토리가 아니라 그 공유 목록이고, 한 사람의 나침반을 고치면
 * 팀 전원의 나침반이 같이 고쳐진다. 뒤집어 말하면 <b>한 명이라도 그 차원에 있으면 팀의 나침반이
 * 전부 그쪽을 가리킨다.</b> 다른 차원에 있는 팀원의 화면에서는 지시 자리의 차원이 맞지 않아
 * 바늘이 헛도는데, 그건 원래 나침반보다 나쁜 상태다. 그래서 <b>아무도 그 차원에 없으면 성분을
 * 걷어내</b> 평범한 나침반으로 되돌린다.
 *
 * <h2>탐색은 비싸다</h2>
 * <p>{@code findNearestMapStructure} 는 반경 안의 구조물 배치 후보를 훑는 동기 작업이라 서버
 * 스레드를 그대로 붙잡는다. 그래서 세 겹으로 아낀다.
 *
 * <ol>
 *   <li>{@value #SWEEP_INTERVAL_TICKS} 틱마다만 이 자리를 지난다.</li>
 *   <li>팀원이 그 차원에 <b>있을 때만</b> 찾는다.</li>
 *   <li>찾은 결과를 팀마다 캐시하고 {@value #SEARCH_INTERVAL_TICKS} 틱(10초) 안에는 다시 찾지
 *       않는다.</li>
 * </ol>
 *
 * <p>증강을 쓰지 않는 팀은 {@link #firstTarget} 이 곧바로 null 을 돌려주므로 목록 두 개를 훑는
 * 것이 전부다.
 */
public final class PerkCompassTargets {
	/** 나침반을 훑는 주기. */
	public static final int SWEEP_INTERVAL_TICKS = 20;
	/** 구조물을 다시 찾기까지 기다리는 최소 시간. */
	public static final int SEARCH_INTERVAL_TICKS = 200;

	/** 팀마다 마지막으로 찾은 결과. */
	private static final Map<UUID, Cached> CACHE = new HashMap<>();

	private static int tickCounter;

	private PerkCompassTargets() {
	}

	/**
	 * 한 팀의 마지막 탐색 결과.
	 *
	 * @param effect     그때 쓴 정의. 증강 풀을 다시 읽어 정의가 바뀌면 캐시를 버리게 한다
	 * @param searchedAt 찾은 시점의 게임 시각(틱)
	 * @param found      찾은 자리. 반경 안에 없었으면 null
	 */
	private record Cached(CompassTargetEffect effect, long searchedAt, @Nullable GlobalPos found) {
	}

	/** 서버가 멈출 때 캐시와 주기 상태를 비운다. */
	public static void reset() {
		CACHE.clear();
		tickCounter = 0;
	}

	// ------------------------------------------------------------------ 주기

	/**
	 * {@value #SWEEP_INTERVAL_TICKS} 틱마다 팀별로 나침반을 맞춘다.
	 *
	 * <p>{@code SharedFateMod} 의 서버 틱에 붙는다. 증강을 잃은 팀도 <b>반드시 이 자리를 지나야</b>
	 * 꽂아 둔 성분이 걷힌다. 그래서 보유 여부와 무관하게 모든 팀을 훑는다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++tickCounter < SWEEP_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		TeamManager manager = TeamManager.get(server);
		Set<UUID> living = new HashSet<>();
		for (ShareTeam team : List.copyOf(manager.allTeams())) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				continue;
			}
			living.add(team.teamId());
			try {
				updateTeam(server, team, state);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("나침반 지시 증강을 적용하지 못했습니다.", error);
			}
		}
		// 해체된 팀의 캐시는 남겨 둘 이유가 없다.
		CACHE.keySet().retainAll(living);
	}

	private static void updateTeam(MinecraftServer server, ShareTeam team, TeamState state) {
		CompassTargetEffect effect = firstTarget(state);
		int changed;
		if (effect == null) {
			CACHE.remove(team.teamId());
			changed = clearTargets(state);
		} else {
			ServerPlayer scout = memberInDimension(server, team, effect.dimension());
			if (scout == null) {
				// 아무도 그 차원에 없다. 평범한 나침반으로 돌려 둔다. 캐시는 남겨 두므로
				// 곧바로 돌아와도 다시 찾지 않는다.
				changed = clearTargets(state);
			} else {
				GlobalPos target = locate(server, team.teamId(), effect, scout);
				changed = target == null ? clearTargets(state) : applyTarget(state, target);
			}
		}
		if (changed > 0) {
			broadcast(server, team);
		}
	}

	/**
	 * 이 팀이 가진 첫 {@code compass_target}. 없으면 null.
	 *
	 * <p>여러 개를 가졌어도 첫 번째만 쓴다. 바늘은 하나뿐이라 두 곳을 동시에 가리킬 방법이
	 * 없고, 매번 다른 쪽을 골라 흔들리는 것보다는 하나로 못 박는 편이 낫다.
	 */
	public static @Nullable CompassTargetEffect firstTarget(@Nullable TeamState state) {
		TeamState active = PerkWorldRules.activeState(state);
		if (active == null) {
			return null;
		}
		for (String perkId : active.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof CompassTargetEffect target) {
					return target;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ 구조물 찾기

	/** 캐시가 살아 있으면 그 값, 아니면 새로 찾는다. */
	private static @Nullable GlobalPos locate(MinecraftServer server, UUID teamId,
			CompassTargetEffect effect, ServerPlayer scout) {
		long now = server.overworld().getGameTime();
		Cached cached = CACHE.get(teamId);
		// 회차가 초기화되면 게임 시각이 뒤로 갈 수 있다. 그때는 캐시를 믿지 않는다.
		if (cached != null && cached.effect() == effect
				&& now >= cached.searchedAt()
				&& now - cached.searchedAt() < SEARCH_INTERVAL_TICKS) {
			return cached.found();
		}
		GlobalPos found = search(server, effect, scout);
		CACHE.put(teamId, new Cached(effect, now, found));
		return found;
	}

	/**
	 * 실제 탐색. 반경 안에 없으면 null.
	 *
	 * <p>{@code ServerLevel.findNearestMapStructure} 는 26.2 에서 <b>태그만</b> 받는다
	 * ({@code TagKey<Structure>}). 요새에는 태그가 없으므로 그 메서드를 그대로 쓸 수 없다.
	 * 그래서 그 메서드가 안에서 하는 일과 똑같이, 정의를 {@code HolderSet} 으로 푼 뒤
	 * {@code ChunkGenerator.findNearestMapStructure(ServerLevel, HolderSet, BlockPos, int, boolean)}
	 * 를 직접 부른다. 마지막 인자는 "이미 만들어진 구조물을 건너뛸지"이고, 바닐라
	 * {@code /locate structure} 와 같이 거짓을 준다.
	 */
	private static @Nullable GlobalPos search(MinecraftServer server, CompassTargetEffect effect,
			ServerPlayer scout) {
		ServerLevel level = server.getLevel(effect.dimension());
		if (level == null) {
			SharedFateMod.LOGGER.warn("compass_target 이 가리키는 차원이 없습니다: {}",
					effect.dimension().identifier());
			return null;
		}
		HolderSet<Structure> structures = effect.resolve(server.registryAccess());
		if (structures == null || structures.size() == 0) {
			SharedFateMod.LOGGER.warn("compass_target 이 가리키는 구조물을 찾을 수 없습니다: {}",
					effect.structureTag() == null
							? String.valueOf(effect.structureKey())
							: "#" + effect.structureTag().location());
			return null;
		}

		BlockPos origin = scout.blockPosition();
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, structures, origin, effect.searchRadius(), false);
		return found == null ? null : GlobalPos.of(effect.dimension(), found.getFirst());
	}

	private static @Nullable ServerPlayer memberInDimension(MinecraftServer server, ShareTeam team,
			ResourceKey<Level> dimension) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null && online.level().dimension().equals(dimension)) {
				return online;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ 나침반 손보기

	/**
	 * 이 성분을 우리가 꽂았는가.
	 *
	 * <p>플레이어가 만든 자철석 나침반은 언제나 {@code tracked} 가 참이다. 그래서 거짓이면서
	 * 목표가 들어 있는 성분은 우리 것뿐이다.
	 */
	public static boolean isOurs(@Nullable LodestoneTracker tracker) {
		return tracker != null && !tracker.tracked() && tracker.target().isPresent();
	}

	/** 팀의 나침반이 이 자리를 가리키게 한다. 실제로 바뀐 개수를 돌려준다. */
	public static int applyTarget(TeamState state, GlobalPos target) {
		LodestoneTracker wanted = new LodestoneTracker(Optional.of(target), false);
		return visitCompasses(state, stack -> {
			LodestoneTracker existing = stack.get(DataComponents.LODESTONE_TRACKER);
			if (existing != null && !isOurs(existing)) {
				// 플레이어가 직접 만든 자철석 나침반. 증강이 빼앗아 갈 물건이 아니다.
				return false;
			}
			if (wanted.equals(existing)) {
				return false;
			}
			stack.set(DataComponents.LODESTONE_TRACKER, wanted);
			return true;
		});
	}

	/** 우리가 꽂아 둔 성분을 걷어내 평범한 나침반으로 되돌린다. 실제로 바뀐 개수를 돌려준다. */
	public static int clearTargets(TeamState state) {
		return visitCompasses(state, stack -> {
			if (!isOurs(stack.get(DataComponents.LODESTONE_TRACKER))) {
				return false;
			}
			stack.remove(DataComponents.LODESTONE_TRACKER);
			return true;
		});
	}

	/**
	 * 팀이 가진 나침반을 모두 훑는다.
	 *
	 * <p>보는 곳은 공유 인벤토리(그리고 확장 칸을 켰으면 그쪽까지)와 공유 왼손 칸이다.
	 * 오른손에 든 것은 단축바에 있으므로 공유 인벤토리에 이미 들어 있다. 넘침 대기열은 보지
	 * 않는다. 화면에 보이지 않는 자리라 가리켜 봐야 쓸 데가 없고, 칸이 비면 어차피
	 * 인벤토리로 들어와 다음 주기에 잡힌다.
	 *
	 * @return {@code action} 이 참을 돌려준 묶음 수
	 */
	private static int visitCompasses(TeamState state, Predicate<ItemStack> action) {
		int changed = visitList(state.mainItems, action);
		if (ExpandedInventoryManager.enabled()) {
			changed += visitList(state.extraItems, action);
		}
		ItemStack offhand = state.equipment.get(EquipmentSlot.OFFHAND);
		if (isCompass(offhand) && action.test(offhand)) {
			changed++;
		}
		return changed;
	}

	private static int visitList(SharedItemList items, Predicate<ItemStack> action) {
		int changed = 0;
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (isCompass(stack) && action.test(stack)) {
				changed++;
			}
		}
		return changed;
	}

	private static boolean isCompass(@Nullable ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() == Items.COMPASS;
	}

	/**
	 * 바뀐 나침반을 화면에 바로 반영한다.
	 *
	 * <p>묶음을 제자리에서 고쳤을 뿐이라 창은 다음 방송 때 알아서 따라오지만, 성분이 바뀌면
	 * 이름과 반짝임까지 달라지므로 한 틱이라도 늦으면 눈에 띈다.
	 */
	private static void broadcast(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null && online.containerMenu != null) {
				online.containerMenu.broadcastChanges();
			}
		}
	}
}
