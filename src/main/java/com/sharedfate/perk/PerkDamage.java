package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 증강의 피해 배율을 실제 피해량에 반영하는 계산기.
 *
 * <p>{@code LivingEntityPerkDamageMixin} 이 {@code LivingEntity.hurtServer} 진입 시점에
 * 이 클래스를 부른다. 한 번의 피해 이벤트에 대해 딱 한 번만 곱해지고, 그 뒤의 방패·방어구·흡수
 * 계산과 {@code StatMirror} 의 공유 체력 반영은 이미 배율이 반영된 수치를 보게 된다.
 *
 * <p>배율이 정확히 1.0 이면 원래 값을 그대로 돌려준다. 증강 풀이 비어 있는 동안에는 항상 이
 * 경로를 타므로 바닐라 피해 계산과 100% 같다.
 */
public final class PerkDamage {
	/** 배율을 곱한 뒤의 상한. 무한대·NaN 이 바닐라 계산으로 새어나가지 않게 막는다. */
	static final float MAX_DAMAGE = 1.0e9F;

	/** 조회가 한 번 터지면 매 피해마다 로그가 쌓이므로 한 번만 남긴다. */
	private static volatile boolean warned;

	private PerkDamage() {
	}

	/**
	 * 피해량에 "주는 피해"·"받는 피해" 배율을 반영한다.
	 *
	 * @param victim 피해를 받는 대상. 팀원이면 받는 피해 배율이 걸린다.
	 * @param source 피해원. 가해자가 팀원이면 주는 피해 배율이 걸린다.
	 * @param amount {@code hurtServer} 가 받은 원래 피해량
	 * @return 배율을 반영한 피해량. 반영할 게 없으면 {@code amount} 그대로
	 */
	public static float scale(@Nullable Entity victim, @Nullable DamageSource source, float amount) {
		if (!(amount > 0.0F) || !Float.isFinite(amount)) {
			return amount;
		}
		double factor;
		try {
			// 조회 실패가 피해 처리를 막으면 안 된다. 어떤 예외든 원래 값으로 돌아간다.
			factor = dealtFactor(source == null ? null : source.getEntity()) * takenFactor(victim);
		} catch (RuntimeException error) {
			warnOnce(error);
			return amount;
		}
		return combine(amount, factor);
	}

	/** 가해자가 증강을 가진 팀원일 때만 주는 피해 배율을 읽는다. */
	private static double dealtFactor(@Nullable Entity attacker) {
		if (!(attacker instanceof ServerPlayer player) || !perksActive(player)) {
			return 1.0;
		}
		return PerkManager.damageDealtMultiplier(player);
	}

	/** 피해자가 증강을 가진 팀원일 때만 받는 피해 배율을 읽는다. */
	private static double takenFactor(@Nullable Entity victim) {
		if (!(victim instanceof ServerPlayer player) || !perksActive(player)) {
			return 1.0;
		}
		return PerkManager.damageTakenMultiplier(player);
	}

	/**
	 * 배율을 따질 값어치가 있는 플레이어인지 본다.
	 *
	 * <p>팀 미소속, 증강을 끈 팀, 아직 아무 증강도 없는 팀은 여기서 걸러진다. 피해 계산은 초당
	 * 수십 번 도는 자리라 이 빠른 경로가 곧 기본 경로다.
	 */
	private static boolean perksActive(ServerPlayer player) {
		TeamState state = TeamLookup.stateOf(player.getUUID());
		return state != null && state.perksEnabled && !state.ownedPerks.isEmpty();
	}

	/** 배율을 곱하고 안전한 범위로 자른다. 배율이 1.0 이면 원래 값을 그대로 돌려준다. */
	static float combine(float amount, double factor) {
		if (factor == 1.0 || !Double.isFinite(factor) || factor < 0.0) {
			return amount;
		}
		double scaled = (double) amount * factor;
		if (!Double.isFinite(scaled)) {
			return MAX_DAMAGE;
		}
		return (float) Math.max(0.0, Math.min(MAX_DAMAGE, scaled));
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"증강 피해 배율을 읽지 못해 이번 피해는 배율 없이 처리합니다. 이 경고는 한 번만 남습니다.",
				error);
	}

	/** 테스트가 경고 억제 상태를 되돌릴 때 쓴다. */
	static void resetWarnedForTesting() {
		warned = false;
	}
}
