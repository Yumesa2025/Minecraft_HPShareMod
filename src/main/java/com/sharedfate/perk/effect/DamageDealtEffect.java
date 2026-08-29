package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 팀원이 주는 피해에 배율을 건다.
 *
 * <p>붙였다 떼는 효과가 아니라 피해 계산 때 조회하는 효과라
 * {@link #apply}/{@link #remove}는 아무 일도 하지 않는다.
 */
public final class DamageDealtEffect implements PerkEffect {
	/** 설정에서 받아들이는 배율 범위. */
	static final double MIN_MULTIPLIER = 0.0;
	static final double MAX_MULTIPLIER = 64.0;
	/** 실제로 돌려주는 배율의 상한. */
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
	public double damageDealtMultiplier() {
		return clamp(multiplier);
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

	/**
	 * 배율을 안전한 범위로 자른다.
	 *
	 * <p>JSON 경로는 이미 {@link #readMultiplier}가 범위를 검사하지만, 생성자는 공개돼 있어
	 * Java 쪽에서 어떤 값이든 들어올 수 있다. 피해 계산이 NaN 이나 무한대를 보면 안 된다.
	 */
	static double clamp(double multiplier) {
		if (!Double.isFinite(multiplier)) {
			return MAX_RESULT;
		}
		return Math.max(0.0, Math.min(MAX_RESULT, multiplier));
	}
}
