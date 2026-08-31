package com.sharedfate.perk;

import com.sharedfate.perk.effect.GatherEffect;
import com.sharedfate.perk.effect.OnSwapEffect;
import com.sharedfate.perk.effect.ProximityEffect;
import com.sharedfate.perk.effect.SwapBlockEffect;
import com.sharedfate.perk.effect.StaggeredSwapEffect;
import com.sharedfate.perk.effect.SwapExplosionEffect;
import com.sharedfate.perk.effect.SwapIntervalEffect;
import com.sharedfate.perk.effect.SwapRallyEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 팀원 위치 교환과 집합에 끼어드는 증강들의 판정부.
 *
 * <p>{@link com.sharedfate.sync.PositionSwapManager} 와
 * {@link com.sharedfate.sync.TeamGathering} 이 처리 한가운데서 여기에 물어보고, 답에 따라
 * 순간이동을 건너뛰거나 남은 틱을 고쳐 쓴다. 판정을 실행부 밖에 떼어 둔 이유는
 * {@link PerkFoodRules} 와 같다. 실행부에는 "어디서 끼어드는가"만 남는 편이 읽기 쉽고,
 * 판정을 월드 없이 시험할 수 있다.
 *
 * <p>여기서 답하는 물음은 다섯이다.
 *
 * <ul>
 *   <li>{@link #blocksSwap} — 자리를 바꾸지 않는가 ({@code swap_block})</li>
 *   <li>{@link #nextRemainingTicks} — 다음 교환까지 몇 틱을 남길 것인가 ({@code swap_interval})</li>
 *   <li>{@link #grantOnSwap} — 교환 시점에 무엇을 얹을 것인가 ({@code on_swap})</li>
 *   <li>{@link #gathers} — 멀어지면 모으는 규칙이 있는가 ({@code gather})</li>
 *   <li>{@link #proximities} — 붙어 있으면 무엇을 얹을 것인가 ({@code proximity})</li>
 * </ul>
 *
 * <p>보유 증강이 하나도 없으면 어느 물음도 팀 상태 두 번만 보고 곧바로 "해당 없음"이다.
 * 증강을 쓰지 않는 팀의 교환 경로에는 사실상 아무 부담도 얹히지 않는다.
 *
 * <h2>세 물음은 서로를 막지 않는다</h2>
 * <p>{@code swap_block} 이 참이어도 {@link #nextRemainingTicks} 와 {@link #grantOnSwap} 은
 * 그대로 쓴다. 막히는 것은 자리를 바꾸는 한 자리뿐이고, 주기와 시점은 계속 흘러야
 * 「뿌리내린 발」의 "원래 바뀔 시점마다 디버프"가 성립한다.
 */
public final class PerkSwapRules {
	/**
	 * 배율을 먹인 뒤에도 남겨 두는 최소 주기. 1초다.
	 *
	 * <p>배율을 여러 개 곱하면 주기가 0 에 가까워질 수 있다. 그러면 매 틱 순간이동이 일어나
	 * 아무도 움직일 수 없고 서버도 버티지 못한다.
	 */
	public static final int MIN_REMAINING_TICKS = 20;

	private PerkSwapRules() {
	}

	// ------------------------------------------------------------------ 순간이동 차단

	/** 이 팀이 지금 자리를 바꾸지 않는가. */
	public static boolean blocksSwap(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return false;
		}
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof SwapBlockEffect) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 주기 배율

	/**
	 * 이 팀이 가진 {@code swap_interval} 배율을 모두 곱한 값. 해당 없으면 1.0.
	 *
	 * <p>여러 개를 가졌으면 전부 곱한다. 서로 다른 증강이 각각 약속한 배율이라 하나만 골라
	 * 줄 이유가 없다. {@link PerkFoodRules#nutritionMultiplier} 와 같은 규칙이다.
	 */
	public static double intervalMultiplier(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return 1.0;
		}
		double total = 1.0;
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof SwapIntervalEffect interval && !interval.hasFixedMinutes()) {
				total *= interval.multiplier();
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	/**
	 * 주기를 못박는 증강이 있으면 그 값(틱). 없으면 0.
	 *
	 * <p>여러 개면 <b>가장 짧은 것</b>이 이긴다. 못박는 증강은 "이 팀은 이 간격으로 흔들린다"를
	 * 약속하는 것이라, 둘을 곱하거나 평균 내면 어느 쪽 약속도 지켜지지 않는다. 짧은 쪽을
	 * 택하면 적어도 그 증강의 약속은 그대로 성립한다.
	 */
	public static int fixedIntervalTicks(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return 0;
		}
		int shortest = 0;
		for (PerkEffect effect : effectsOf(state)) {
			if (!(effect instanceof SwapIntervalEffect interval) || !interval.hasFixedMinutes()) {
				continue;
			}
			int ticks = interval.fixedMinutes() * TeamState.PositionSwapLimits.TICKS_PER_MINUTE;
			if (shortest == 0 || ticks < shortest) {
				shortest = ticks;
			}
		}
		return shortest;
	}

	/**
	 * 교환이 끝난 직후 다음 교환까지 남길 틱.
	 *
	 * <p>{@code TeamState.advancePositionSwapTick} 이 이미 주기 그대로를 채워 넣은 뒤에 부른다.
	 * 배율이 없으면 채워 넣은 값과 같으므로 덮어써도 아무 일도 일어나지 않는다.
	 */
	public static int nextRemainingTicks(@Nullable TeamState state) {
		int interval = state == null ? 0 : state.positionSwapIntervalTicks;
		if (interval <= 0) {
			return 0;
		}
		// 못박는 증강이 있으면 팀이 정한 주기와 배율을 모두 제친다.
		//
		// 다만 팀이 정한 주기보다 길게는 못 간다. TeamState.sanitize 가 저장을 읽을 때 남은
		// 틱을 [0, 주기] 로 자르기 때문에, 더 긴 값을 남겨 두면 서버를 껐다 켜는 순간 조용히
		// 줄어들어 재시작 전후로 팀이 다르게 움직인다. 어차피 이 증강들은 주기를 "짧게"
		// 만드는 쪽이라 실제로 걸리는 일은 거의 없다.
		int fixed = fixedIntervalTicks(state);
		if (fixed > 0) {
			return Math.max(MIN_REMAINING_TICKS, Math.min(interval, fixed));
		}
		return scaleInterval(interval, intervalMultiplier(state));
	}

	/**
	 * 곱셈 규칙만 떼어 놓은 것. 월드 없이 시험하려고 나눠 뒀다.
	 *
	 * <h2>왜 주기보다 길어질 수 없는가</h2>
	 * <p>{@code TeamState.sanitize} 가 저장을 읽을 때 남은 틱을 {@code [0, 주기]} 로 자른다.
	 * 배율이 1보다 커서 주기보다 긴 값을 남겨 두면 서버를 껐다 켜는 순간 조용히 주기로
	 * 되돌아가, 같은 팀이 재시작 전후로 다르게 움직인다. 그럴 바에는 처음부터 주기에서
	 * 멈추는 편이 예측 가능하다. 즉 <b>1보다 큰 배율은 지금 아무 효과가 없다.</b>
	 * 지금 쓰이는 정의는 모두 1보다 작은 배율이다.
	 */
	static int scaleInterval(int intervalTicks, double multiplier) {
		if (intervalTicks <= 0) {
			return 0;
		}
		if (!Double.isFinite(multiplier) || multiplier <= 0.0 || multiplier == 1.0) {
			return intervalTicks;
		}
		double scaled = intervalTicks * multiplier;
		if (!Double.isFinite(scaled)) {
			return intervalTicks;
		}
		long rounded = Math.max(MIN_REMAINING_TICKS, Math.round(scaled));
		return (int) Math.min(intervalTicks, rounded);
	}

	// ------------------------------------------------------------------ 교환 시점 효과

	/**
	 * 교환 시점에 팀원 전원에게 {@code on_swap} 의 하위 효과를 얹는다.
	 *
	 * <p>순간이동이 막혔든 아니든 그대로 얹는다. 이유는 {@link OnSwapEffect} 에 적어 뒀다.
	 * 하위 효과가 없는 팀이 대부분이라 이 순회는 대개 아무 일도 하지 않는다.
	 */
	public static void grantOnSwap(@Nullable TeamState state, List<ServerPlayer> members) {
		if (!usesPerks(state) || members.isEmpty()) {
			return;
		}
		for (PerkEffect effect : effectsOf(state)) {
			if (!(effect instanceof OnSwapEffect onSwap)) {
				continue;
			}
			for (ServerPlayer member : members) {
				onSwap.grantTo(member);
			}
		}
	}

	// ------------------------------------------------------------------ 교환 시점 폭발

	/**
	 * 이 팀이 가진 {@code swap_explosion} 정의들. 없으면 빈 목록.
	 *
	 * <p>이 목록이 비어 있지 않다는 것은 두 가지 뜻이다. 자리를 바꿀 때 방금 비운 자리에서
	 * 폭발을 일으켜야 하고({@code PositionSwapManager.swapTeamPositions}), 5초 카운트다운과
	 * 효과음도 이 팀에게는 보내지 말아야 한다({@code PositionSwapManager.tick}). 두 곳 모두
	 * 여기 하나만 물어본다.
	 */
	public static List<SwapExplosionEffect> swapExplosions(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return List.of();
		}
		List<SwapExplosionEffect> found = new ArrayList<>();
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof SwapExplosionEffect explosion) {
				found.add(explosion);
			}
		}
		return found;
	}

	// ------------------------------------------------------------------ 집합형 교환

	/**
	 * 이 팀이 {@code swap_rally}(골드 「정거장」)를 가졌는가.
	 *
	 * <p>참이면 {@code PositionSwapManager.swapMoment}가 순열 교환 대신
	 * {@code RallyPointManager}에게 집합·복귀를 넘긴다. {@link #staggered}보다 먼저 확인한다 —
	 * 골드가 실버보다 우선한다는 규칙이 아니라, 한 팀이 어쩌다 둘 다 가진 극단적인 경우에도
	 * 판정 순서가 매번 같아야 하기 때문이다.
	 */
	public static boolean rallyPoint(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return false;
		}
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof SwapRallyEffect) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 순차 이동

	/**
	 * 이 팀이 {@code staggered_swap}(실버 「시차」)을 가졌는가.
	 *
	 * <p>참이면 {@code PositionSwapManager.swapMoment}가 한 틱 안에서 전부 옮기는 대신
	 * {@code StaggeredSwapManager}에게 진행을 넘긴다.
	 */
	public static boolean staggered(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return false;
		}
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof StaggeredSwapEffect) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 집합

	/** 이 팀이 가진 {@code proximity} 효과들. 해당 없으면 빈 목록. */
	public static List<ProximityEffect> proximities(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return List.of();
		}
		List<ProximityEffect> found = new ArrayList<>();
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof ProximityEffect proximity) {
				found.add(proximity);
			}
		}
		return found;
	}

	/** 이 팀이 가진 {@code gather} 규칙들. 없으면 빈 목록. */
	public static List<GatherEffect> gathers(@Nullable TeamState state) {
		if (!usesPerks(state)) {
			return List.of();
		}
		List<GatherEffect> found = new ArrayList<>();
		for (PerkEffect effect : effectsOf(state)) {
			if (effect instanceof GatherEffect gather) {
				found.add(gather);
			}
		}
		return found;
	}

	// ------------------------------------------------------------------ 공통

	/** 이 팀이 증강을 쓰고 있고 가진 것이 하나라도 있는가. */
	private static boolean usesPerks(@Nullable TeamState state) {
		return state != null && state.perksEnabled && !state.ownedPerks.isEmpty();
	}

	/**
	 * 이 팀이 보유한 증강들의 효과를 한 줄로 펼친다.
	 *
	 * <p>풀에서 사라진 id 는 건너뛴다. 증강 정의를 손으로 고칠 수 있는 이상 저장에만 남은
	 * id 는 언제든 생긴다.
	 */
	private static List<PerkEffect> effectsOf(TeamState state) {
		List<PerkEffect> effects = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk != null) {
				effects.addAll(perk.effects());
			}
		}
		return effects;
	}
}
