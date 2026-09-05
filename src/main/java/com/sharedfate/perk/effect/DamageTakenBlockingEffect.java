package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;

/**
 * 방패로 막고 있는 <b>동안에만</b> 받는 피해에 배율을 건다.
 *
 * <pre>{@code
 * { "type": "damage_taken_blocking", "multiplier": 0.5 }
 * }</pre>
 *
 * <p>방어 2단계 「방패 뒤가 편하다」가 이 타입 하나로 만들어진다. 방패를 <b>손에 들고만</b>
 * 있어서는 걸리지 않고 실제로 막는 중이어야 한다 — 시야가 좁아지고 손이 묶이는 것이 이 이득의
 * 값이라는 판단은 {@link ShieldFallImmunityEffect} 와 같다.
 *
 * <h2>왜 {@code damage_taken} 을 넓히지 않았는가</h2>
 * <p>{@link DamageTakenFromEffect} 가 피해원 때문에 갈라져 나온 것과 똑같은 이유다.
 * {@link PerkEffect#damageTakenMultiplier()} 에는 <b>피해자의 자세가 넘어오지 않는다.</b>
 * 그 자리를 고치면 조건을 모르는 옛 경로({@code PerkManager.damageTakenMultiplier})가 "막는 중
 * 전용" 배율을 언제나 곱하게 된다. 그래서 조건 없는 배율은 {@code damage_taken} 에 그대로 두고,
 * 자세를 보는 쪽만 별도 타입으로 나눴다. 두 타입은 서로 영향을 주지 않으며 한 정의에 함께 적어도
 * 된다.
 *
 * <h2>{@code shield_fall_immunity} 와 무엇이 다른가</h2>
 * <p>둘 다 「막는 중인가」를 보지만 하는 일이 다르다. 낙하 면역은 피해 <b>사건 자체를</b>
 * 진입점에서 버려 피격 소리도 무적시간도 남기지 않는다. 이쪽은 피해량을 <b>깎을</b> 뿐이라
 * 맞은 사실은 그대로 남는다. 절반으로 줄이는 것이 목적이므로 사건을 없앨 수는 없다.
 *
 * <p>둘을 함께 가진 팀이 방패를 든 채 떨어지면 낙하 피해는 면역이 먼저 걸려 통째로 사라진다.
 * 이 배율은 그 뒤에 남은 다른 피해에만 걸린다. 순서는
 * {@code LivingEntityPerkDamageMixin} 이 정한다.
 *
 * <h2>최상위에만 놓을 수 있다</h2>
 * <p>자세를 아는 자리에서 이 효과를 찾는 {@link com.sharedfate.perk.PerkDamage} 는 정의의
 * 최상위 효과만 훑는다. {@code periodic} 이나 {@code conditional} 안에 넣으면 조용히 아무 일도
 * 하지 않으므로, {@link DamageTakenFromEffect} 와 같은 기준으로 읽는 시점에 걸러 낸다.
 */
public final class DamageTakenBlockingEffect implements PerkEffect {
	private final double multiplier;

	public DamageTakenBlockingEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier =
				DamageDealtEffect.readMultiplier(perkId, "damage_taken_blocking", json);
		if (multiplier == null) {
			return null;
		}
		if (index < 0 || index >= DamageTakenFromEffect.MAX_TOP_LEVEL_INDEX) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: damage_taken_blocking 은 최상위에만 놓을 수 있습니다 (순번 {})",
					perkId, index);
			return null;
		}
		if (multiplier == 1.0) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: damage_taken_blocking 의 multiplier 가 1 이라 아무 일도 하지 않습니다",
					perkId);
			return null;
		}
		return new DamageTakenBlockingEffect(multiplier);
	}

	/**
	 * 조건 없는 배율은 없다.
	 *
	 * <p>자세를 모르는 이 자리에서 배율을 돌려주면 막지 않을 때까지 걸린다. 실제 배율은
	 * {@link #multiplierFor(boolean)} 으로만 나간다.
	 */
	@Override
	public double damageTakenMultiplier() {
		return 1.0;
	}

	/** 막는 중이면 배율, 아니면 1.0. */
	public double multiplierFor(boolean blocking) {
		return blocking ? DamageDealtEffect.clamp(multiplier) : 1.0;
	}

	/** 정의에 적힌 값 그대로. */
	public double multiplier() {
		return multiplier;
	}
}
