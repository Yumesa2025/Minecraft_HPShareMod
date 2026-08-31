package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.BonusDropEffect;
import com.sharedfate.perk.effect.EchoMiningEffect;
import com.sharedfate.perk.effect.MiningSpeedEffect;
import com.sharedfate.perk.effect.OnBreakEffect;
import com.sharedfate.perk.effect.PairedMiningEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 블록 파괴에 걸리는 증강 효과({@code bonus_drop}, {@code on_break}, {@code mining_speed})의
 * 실행부.
 *
 * <p>효과 클래스들은 "무엇을 얼마나"만 들고 있고, "언제 누가 무엇을 캤는가"와 결과를 어디에
 * 넣는가는 전부 여기서 정한다. {@code on_kill} 과 {@link PerkKillRewards} 의 관계와 같은
 * 구도다.
 *
 * <h2>어느 순간에 끼어드는가</h2>
 * <p>{@code PlayerBlockBreakEvents.AFTER} 하나만 쓴다. Fabric 이 이 이벤트를 부르는 자리는
 * {@code ServerPlayerGameMode.destroyBlock} 안의 {@code Block.destroy(...)} 직후, 즉
 * <b>블록은 이미 사라졌지만 도구 손상과 전리품 지급은 아직인</b> 지점이다. 클라이언트에는
 * 같은 이름의 이벤트가 없으므로({@code ClientPlayerBlockBreakEvents} 는 별개다) 이 경로는
 * 서버에서만 지난다.
 *
 * <p>그 순서 때문에 지켜야 하는 것이 하나 있다. 이 시점에 도구를 부러뜨리면 곧바로 이어지는
 * {@code player.getMainHandItem()} 이 빈 손이 되고, {@code hasCorrectToolForDrops} 가 거짓이
 * 되어 <b>블록이 아무것도 떨어뜨리지 않는다</b>. 그래서 추가 내구도 소모는 도구를 절대 부러뜨리지
 * 않도록 최소 1 을 남긴다. 남은 1 은 바로 뒤의 {@code ItemStack.mineBlock} 이 평소대로 가져가고,
 * 도구는 바닐라와 똑같은 자리에서 똑같은 소리와 함께 부러진다.
 *
 * <h2>추가 드롭은 어디로 가는가</h2>
 * <p>{@link Block#popResource} 로 <b>캔 자리에 아이템으로 떨어뜨린다.</b> 공유 인벤토리에 직접
 * 밀어 넣지 않는다.
 *
 * <p>이 모드는 팀원의 인벤토리 칸 목록 자체를 {@code TeamState.mainItems} 로 갈아 끼워
 * 공유한다({@code InventorySwapper.finishJoin}). 즉 <b>바닥에 떨어진 아이템을 주우면 그것이
 * 곧 팀 공유 인벤토리에 들어간 것</b>이다. 별도의 경로가 필요 없다. 떨어뜨리는 쪽을 고르면
 * 얻는 것이 셋 있다.
 * <ul>
 *   <li>인벤토리가 꽉 찼을 때의 동작이 바닐라와 같다. 직접 넣으면 넘치는 몫을 어디에 둘지
 *       이 코드가 따로 정해야 하고, {@code ExpandedInventoryManager} 의 확장 칸 규칙과
 *       {@code overflowItems} 처리를 여기서 다시 구현하게 된다.</li>
 *   <li>줍기 애니메이션·소리·통계({@code picked_up})가 바닐라 그대로다.</li>
 *   <li>바로 다음 줄에서 바닐라가 본래 드롭을 같은 자리에 떨어뜨리므로, 플레이어 눈에는
 *       "이번엔 하나 더 나왔다"로 자연스럽게 보인다.</li>
 * </ul>
 *
 * <h2>증강이 없으면 아무 일도 하지 않는다</h2>
 * <p>블록을 캘 때마다 지나는 자리이므로 빠져나가는 길이 짧아야 한다. 팀이 없거나 보유 증강이
 * 비어 있으면 증강 풀을 들여다보지도 않고 곧바로 돌아간다. 어떤 예외도 밖으로 내보내지 않는다.
 * 증강 하나가 잘못돼 블록 파괴가 멈추면 안 된다.
 */
public final class PerkBlockBreaks {
	private static volatile boolean warned;

	private PerkBlockBreaks() {
	}

	// ------------------------------------------------------------------ 등록 지점

	/**
	 * {@code PlayerBlockBreakEvents.AFTER} 에 붙는 지점.
	 *
	 * @param blockEntity 블록이 사라지기 전에 Fabric 이 잡아 둔 블록 엔티티. 없으면 null
	 */
	public static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity) {
		try {
			handleBreak(level, player, pos, state, blockEntity);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void handleBreak(Level level, Player player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer breaker)
				|| state == null || pos == null) {
			return;
		}
		TeamState teamState = TeamLookup.stateOf(breaker.getUUID());
		if (teamState == null || !teamState.perksEnabled || teamState.ownedPerks.isEmpty()) {
			return;
		}

		for (String perkId : teamState.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof OnBreakEffect onBreak && onBreak.appliesTo(state)) {
					onBreak.grantTemporaryEffects(breaker);
				} else if (effect instanceof BonusDropEffect bonus && bonus.appliesTo(state)) {
					tryBonusDrop(serverLevel, breaker, pos, state, blockEntity, bonus);
				} else if (effect instanceof PairedMiningEffect) {
					PerkResonantMining.onBreak(serverLevel.getServer(), breaker, state, serverLevel.getGameTime());
				} else if (effect instanceof EchoMiningEffect) {
					tryEchoMining(serverLevel, breaker, pos);
				}
			}
		}
	}

	// ------------------------------------------------------------------ 메아리 채굴

	/** 발밑이 아니라 방금 캔 자리 주변을 훑는 반경(블록). 3×3×3 이웃(가운데 제외) 26칸이다. */
	private static final int ECHO_SEARCH_RADIUS = 1;

	/**
	 * 방금 캔 블록 근처의 블록 하나를 더 캔다. 팀원과는 무관하다.
	 *
	 * <p>캔 것과 같은 종류인지는 따지지 않는다 — "메아리"는 행동이 반복된다는 뜻이지 같은
	 * 자원이 나온다는 약속이 아니다. 후보 중 하나를 무작위로 고른다.
	 *
	 * <p>{@code destroyBlock} 이 아니라 {@link ServerLevel#removeBlock} 을 쓴다. 이 메서드는
	 * {@code PlayerBlockBreakEvents.AFTER} 를 다시 발화시키지 않으므로, 이 사건이 자기 자신을
	 * 다시 부르는 고리가 애초에 생기지 않는다.
	 *
	 * <p>로드되지 않은 청크의 블록은 후보에서 빠진다. 청크를 억지로 불러오지 않는다
	 * ({@link ServerLevel#isLoaded} 는 조회만 하고 불러오지는 않는다).
	 */
	private static void tryEchoMining(ServerLevel level, ServerPlayer breaker, BlockPos origin) {
		BlockPos echoPos = pickEchoTarget(level, breaker, origin);
		if (echoPos == null) {
			return;
		}
		BlockState echoState = level.getBlockState(echoPos);
		BlockEntity echoBlockEntity = level.getBlockEntity(echoPos);
		List<ItemStack> drops = Block.getDrops(
				echoState, level, echoPos, echoBlockEntity, breaker, breaker.getMainHandItem());
		level.removeBlock(echoPos, false);
		for (ItemStack drop : drops) {
			if (drop != null && !drop.isEmpty()) {
				Block.popResource(level, echoPos, drop);
			}
		}
		// 원래 소모는 바닐라가 이 사건 뒤에 처리한다. 여기서는 정확히 한 점만 더 먹여
		// 합계가 2배가 되게 한다. 도구를 부러뜨리지 않는 가드는 그대로 재사용한다.
		spendExtraDurability(breaker, 1);
	}

	/**
	 * 방금 캔 자리를 둘러싼 26칸 중 다시 캘 수 있는 것을 모아 무작위로 하나 고른다.
	 *
	 * <p>공기, 로드되지 않은 자리, 캘 수 없는 블록({@code getDestroySpeed} 가 음수), 도구가
	 * 맞지 않는 블록({@code hasCorrectToolForDrops})은 후보에서 뺀다. 후보가 하나도 없으면 null.
	 */
	static @Nullable BlockPos pickEchoTarget(ServerLevel level, ServerPlayer breaker, BlockPos origin) {
		List<BlockPos> candidates = new ArrayList<>();
		for (int dx = -ECHO_SEARCH_RADIUS; dx <= ECHO_SEARCH_RADIUS; dx++) {
			for (int dy = -ECHO_SEARCH_RADIUS; dy <= ECHO_SEARCH_RADIUS; dy++) {
				for (int dz = -ECHO_SEARCH_RADIUS; dz <= ECHO_SEARCH_RADIUS; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (!level.isLoaded(candidate)) {
						continue;
					}
					BlockState candidateState = level.getBlockState(candidate);
					if (candidateState.isAir() || candidateState.getDestroySpeed(level, candidate) < 0.0F) {
						continue;
					}
					if (!breaker.hasCorrectToolForDrops(candidateState)) {
						continue;
					}
					candidates.add(candidate);
				}
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(level.getRandom().nextInt(candidates.size()));
	}

	// ------------------------------------------------------------------ 추가 드롭

	/**
	 * 확률을 굴려 성공하면 드롭을 하나 더 떨어뜨리고 도구를 더 닳게 한다.
	 *
	 * <p>바닐라가 아무것도 떨어뜨리지 않을 상황에서는 증강도 아무것도 주지 않는다. 두 가지를
	 * 본다.
	 * <ul>
	 *   <li>{@code preventsBlockDrops()} — 크리에이티브다. 바닐라도 이 뒤에서 곧바로 돌아간다.</li>
	 *   <li>{@code hasCorrectToolForDrops(state)} — 맨손으로 철광석을 캔 것처럼 등급이 모자란
	 *       도구다. 이때 바닐라는 {@code playerDestroy} 를 부르지 않아 전리품이 없다.</li>
	 * </ul>
	 *
	 * <p>확률을 먼저 굴리지 않고 위 두 가지를 먼저 보는 이유는, 어차피 줄 수 없는 상황에서
	 * 난수를 소비해 다음 판정을 흔들지 않기 위해서다.
	 */
	private static void tryBonusDrop(ServerLevel level, ServerPlayer breaker, BlockPos pos,
			BlockState state, @Nullable BlockEntity blockEntity, BonusDropEffect effect) {
		if (breaker.preventsBlockDrops() || !breaker.hasCorrectToolForDrops(state)) {
			return;
		}
		double chance = effect.chanceFor();
		if (chance <= 0.0) {
			return;
		}
		RandomSource random = level.getRandom();
		if (chance < 1.0 && random.nextDouble() >= chance) {
			return;
		}

		// extra 가 1이면(기본값) 예전과 완전히 같다: 한 번만 굴려 하나 준다. 그보다 크면
		// 성공할 때마다 그 횟수만큼 다시 굴려 매번 하나씩 떨어뜨린다("비옥한 땅"의 3배 등).
		int granted = 0;
		for (int i = 0; i < effect.extra(); i++) {
			ItemStack bonus = rollBonusStack(level, breaker, pos, state, blockEntity);
			if (bonus == null || bonus.isEmpty()) {
				// 전리품표가 이번엔 아무것도 주지 않았다(자갈→부싯돌 같은 경우).
				continue;
			}
			Block.popResource(level, pos, bonus);
			granted++;
		}
		if (granted > 0) {
			spendExtraDurability(breaker, effect.extraDurability());
		}
	}

	/**
	 * 이 블록의 전리품표를 지금 든 도구로 한 번 더 굴려 그중 하나를 개수 1 로 잘라 온다.
	 *
	 * <p>전리품표를 쓰므로 섬세한 손길이면 광석 블록이, 아니면 원석이 나온다. 행운은 굴린 값에
	 * 이미 반영돼 있지만 개수를 1 로 자르기 때문에 증강분이 행운 배수만큼 불어나지는 않는다.
	 * 증강은 언제나 정확히 "하나 더"다.
	 */
	private static @Nullable ItemStack rollBonusStack(ServerLevel level, ServerPlayer breaker,
			BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		List<ItemStack> drops = Block.getDrops(
				state, level, pos, blockEntity, breaker, breaker.getMainHandItem());
		if (drops == null) {
			return null;
		}
		for (ItemStack drop : drops) {
			if (drop != null && !drop.isEmpty()) {
				return drop.copyWithCount(1);
			}
		}
		return null;
	}

	/**
	 * 손에 든 도구를 추가로 닳게 한다.
	 *
	 * <p>{@code hurtAndBreak} 에 그대로 넘기므로 내구성(Unbreaking) 마법과 {@code unbreakable}
	 * 성분이 붙은 도구는 바닐라 규칙 그대로 덜 닳거나 아예 닳지 않는다. 여기서 따로 판단하지
	 * 않는 이유가 그것이다.
	 *
	 * <p>다만 <b>여기서 도구를 부러뜨리지는 않는다.</b> 남은 내구도에서 1 을 남기고 그만큼만
	 * 깎는다. 이 시점에 도구가 사라지면 바로 뒤의 {@code hasCorrectToolForDrops} 가 거짓이 되어
	 * 방금 캔 블록의 전리품이 통째로 사라지기 때문이다. 남긴 1 은 바닐라의 {@code mineBlock} 이
	 * 이어서 가져가므로, 내구도를 다 쓴 도구는 이번 블록에서 평소대로 부러진다.
	 */
	private static void spendExtraDurability(ServerPlayer breaker, int amount) {
		if (amount <= 0) {
			return;
		}
		ItemStack tool = breaker.getMainHandItem();
		if (tool.isEmpty() || !tool.isDamageableItem()) {
			return;
		}
		int allowed = allowedExtraDurability(
				amount, tool.getMaxDamage() - tool.getDamageValue());
		if (allowed <= 0) {
			return;
		}
		tool.hurtAndBreak(allowed, breaker, EquipmentSlot.MAINHAND);
	}

	/**
	 * 도구를 부러뜨리지 않고 추가로 깎을 수 있는 양.
	 *
	 * @param amount    증강이 요구한 추가 소모량
	 * @param remaining 지금 남아 있는 내구도
	 * @return 실제로 깎을 양. 언제나 {@code remaining} 보다 작아 도구가 여기서 부러지지 않는다
	 */
	static int allowedExtraDurability(int amount, int remaining) {
		if (amount <= 0 || remaining <= 1) {
			return 0;
		}
		return Math.max(0, Math.min(amount, remaining - 1));
	}

	// ------------------------------------------------------------------ 채굴 속도

	/**
	 * {@code Player.getDestroySpeed} 가 내놓은 값에 {@code mining_speed} 배율을 곱한다.
	 *
	 * <p>{@code PlayerMiningSpeedMixin} 이 부른다. 팀에 속하지 않았거나 보유 증강이 없으면
	 * 받은 값을 그대로 돌려주므로 바닐라와 완전히 같다.
	 *
	 * <p>{@code TeamLookup.serverStateOf} 는 {@code ServerPlayer} 가 아니면 null 이다. 이
	 * mixin 은 공용 설정에 들어 있어 클라이언트의 {@code LocalPlayer} 에도 걸리지만, 그쪽에서는
	 * 첫 줄에서 곧바로 원래 값이 나온다.
	 *
	 * @param base {@code getDestroySpeed} 의 원래 반환값
	 * @return 배율을 먹인 값. 해당 없으면 {@code base} 그대로
	 */
	public static float scaleDestroySpeed(@Nullable Player player, @Nullable BlockState state,
			float base) {
		if (player == null || state == null || !(base > 0.0F) || !Float.isFinite(base)) {
			return base;
		}
		try {
			double multiplier = multiplierFor(player, state);
			if (multiplier == 1.0) {
				return base;
			}
			float scaled = (float) (base * multiplier);
			// 0 이나 음수가 되면 그 블록을 영영 캘 수 없다. 그럴 바에는 원래 값이 낫다.
			return Float.isFinite(scaled) && scaled > 0.0F ? scaled : base;
		} catch (RuntimeException error) {
			warnOnce(error);
			return base;
		}
	}

	/** 이 플레이어가 속한 팀이 이 블록에 걸어 둔 채굴 속도 배율. 팀이 없으면 1.0. */
	static double multiplierFor(@Nullable Player player, @Nullable BlockState state) {
		return multiplierFor(TeamLookup.serverStateOf(player), state);
	}

	/**
	 * 이 팀이 이 블록에 걸어 둔 채굴 속도 배율의 곱.
	 *
	 * <p>서로 다른 증강이 같은 블록을 각각 느리게 한다면 둘 다 적용하는 것이 맞다. 걸리는
	 * 효과가 없으면 1.0 이다.
	 */
	static double multiplierFor(@Nullable TeamState teamState, @Nullable BlockState state) {
		if (teamState == null || !teamState.perksEnabled || teamState.ownedPerks.isEmpty()
				|| state == null) {
			return 1.0;
		}
		double multiplier = 1.0;
		for (String perkId : teamState.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof MiningSpeedEffect mining && mining.appliesTo(state)) {
					multiplier *= mining.multiplierFor();
				}
			}
		}
		return multiplier;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"블록 파괴 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		warned = false;
	}
}
