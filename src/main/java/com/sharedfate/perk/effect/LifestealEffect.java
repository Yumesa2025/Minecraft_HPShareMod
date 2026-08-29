package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 팀원이 준 피해의 일부를 팀 체력으로 되돌린다.
 *
 * <p>정의는 {@code { "type": "lifesteal", "fraction": 0.15 }} 다. 프리즘 11 흡혈귀가 이 타입을
 * {@code no_natural_regen} 과 짝지어 쓴다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "얼마를 되돌리는가"만 들고 있는 자료 그릇이다. "누가 누구에게 얼마를 입혔는가"를
 * 보고 실제로 공유 풀을 채우는 일은 {@link com.sharedfate.perk.PerkLifesteal} 이 맡는다.
 * 회복이 개인이 아니라 <b>팀 공유 값</b>에 들어가는 이유와 이중 적용을 피하는 방법도 그쪽에
 * 적어 뒀다.
 */
public final class LifestealEffect implements PerkEffect {
	/** 흡혈 비율 상한. 준 피해를 그대로 되돌리는 1.0 을 넘길 이유가 없다. */
	static final double MAX_FRACTION = 1.0;

	private final double fraction;

	public LifestealEffect(double fraction) {
		this.fraction = fraction;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double fraction = PerkEffectType.readDouble(json, "fraction");
		if (fraction == null || !(fraction > 0.0) || fraction > MAX_FRACTION) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: lifesteal 의 fraction 이 없거나 범위를 벗어났습니다 ({})", perkId, fraction);
			return null;
		}
		return new LifestealEffect(fraction);
	}

	/** 정의에 적힌 비율. */
	public double fraction() {
		return fraction;
	}

	/**
	 * 안전한 범위로 자른 비율.
	 *
	 * <p>JSON 경로는 이미 범위를 검사하지만 생성자는 공개돼 있어 Java 쪽에서 어떤 값이든 들어올
	 * 수 있다. 회복량 계산이 NaN 이나 무한대를 보면 안 된다.
	 */
	public double fractionFor() {
		if (!Double.isFinite(fraction)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(MAX_FRACTION, fraction));
	}
}
