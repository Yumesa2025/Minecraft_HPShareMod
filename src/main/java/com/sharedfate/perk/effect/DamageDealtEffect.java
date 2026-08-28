package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 팀원이 주는 피해에 배율을 건다.
 *
 * <p>중첩은 거듭제곱이다. 1.2배 증강을 두 번 쌓으면 1.44배가 된다. 곱셈이 아니라 덧셈으로
 * 쌓으면 중첩 상한이 높은 증강에서 배율이 걷잡을 수 없이 커진다.
 *
 * <p>붙였다 떼는 효과가 아니라 피해 계산 때 조회하는 효과라
 * {@link #apply}/{@link #remove}는 아무 일도 하지 않는다.
 */
public final class DamageDealtEffect implements PerkEffect {
	/** 설정에서 받아들이는 배율 범위. */
	static final double MIN_MULTIPLIER = 0.0;
	static final double MAX_MULTIPLIER = 64.0;
	/** 중첩을 다 곱한 뒤의 상한. */
	static final double MAX_RESULT = 1.0e6;

	private final double multiplier;

	public DamageDealtEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = readMultiplier(perkId, "damage_dealt", json);
		return multiplier == null ? null : new DamageDealtEffect(multiplier);
	}

	@Override
	public double damageDealtMultiplier(int stacks) {
		return power(multiplier, stacks);
	}

	public double multiplier() {
		return multiplier;
	}

	/** {@code damage_dealt}/{@code damage_taken}이 함께 쓰는 multiplier 읽기. */
	static Double readMultiplier(String perkId, String typeId, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 효과의 multiplier 값이 없거나 범위를 벗어났습니다 ({})",
					perkId, typeId, multiplier);
			return null;
		}
		return multiplier;
	}

	/** 중첩 수만큼 거듭제곱하고 안전한 범위로 자른다. */
	static double power(double multiplier, int stacks) {
		int safeStacks = Math.max(1, stacks);
		double result = Math.pow(multiplier, safeStacks);
		if (!Double.isFinite(result)) {
			return MAX_RESULT;
		}
		return Math.max(0.0, Math.min(MAX_RESULT, result));
	}
}
