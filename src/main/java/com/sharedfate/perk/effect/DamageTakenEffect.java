package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 팀원이 받는 피해에 배율을 건다.
 *
 * <p>1보다 작으면 방어형, 크면 트레이드오프형 증강이 된다. 값 범위는
 * {@link DamageDealtEffect}와 같다.
 */
public final class DamageTakenEffect implements PerkEffect {
	private final double multiplier;

	public DamageTakenEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = DamageDealtEffect.readMultiplier(perkId, "damage_taken", json);
		return multiplier == null ? null : new DamageTakenEffect(multiplier);
	}

	@Override
	public double damageTakenMultiplier() {
		return DamageDealtEffect.clamp(multiplier);
	}

	public double multiplier() {
		return multiplier;
	}
}
