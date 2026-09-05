package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 흡혈로 되돌리는 양에 배율을 건다.
 *
 * <p>정의는 {@code { "type": "lifesteal_efficiency", "multiplier": 1.3 }} 다. 회복 3단계
 * 「더 진하게 빤다」가 이 타입 하나로 만들어진다.
 *
 * <h2><b>덧셈이 아니라 곱셈이다</b></h2>
 * <p>「흡혈 효율이 30% 증가」는 <b>흡혈률에 0.3 을 더하는 것이 아니다.</b> 지금 흡혈률이 5% 라면
 * 결과는 6.5% 이지 35% 가 아니다. 덧셈으로 근사하면 흡혈률이 바뀔 때마다 어긋난다 — 화력
 * 4단계가 흡혈을 15% 로 올리는 순간 덧셈 근사는 45% 를 주고, 이는 의도한 19.5% 의 두 배가 넘는다.
 *
 * <p>그래서 {@link com.sharedfate.perk.PerkLifesteal#healingFor} 는 흡혈 비율을 <b>모두 더한
 * 뒤</b> 이 배율들을 <b>곱한다.</b> 순서가 그렇게 정해져 있어야 「효율」이라는 말이 뜻대로 산다.
 * 배율이 여럿이면 전부 곱한다 — {@code food_nutrition} 배율을 모으는 규칙과 같다.
 *
 * <h2>혼자 있으면 아무 일도 하지 않는다</h2>
 * <p>흡혈이 하나도 없는 팀에서 이 효과만 켜지면 {@code 0 × 1.3 = 0} 이라 여전히 0 이다.
 * 곱셈 단계이므로 그것이 옳다. 이 효과는 스스로 흡혈을 만들지 않는다.
 */
public final class LifestealEfficiencyEffect implements PerkEffect {
	/** 배율 상한. 흡혈 효율을 여덟 배까지 올리는 것 이상은 정의 실수로 본다. */
	static final double MAX_MULTIPLIER = 8.0;

	private final double multiplier;

	public LifestealEfficiencyEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < 0.0 || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: lifesteal_efficiency 의 multiplier 가 없거나 범위를 벗어났습니다 ({})",
					perkId, multiplier);
			return null;
		}
		return new LifestealEfficiencyEffect(multiplier);
	}

	/** 정의에 적힌 배율. */
	public double multiplier() {
		return multiplier;
	}

	/**
	 * 안전한 범위로 자른 배율.
	 *
	 * <p>말이 안 되는 값일 때 <b>1.0</b> 을 돌려주는 것이 중요하다. 곱셈 단계라 0 을 돌려주면
	 * 흡혈이 통째로 사라진다. 값을 못 믿을 때 할 수 있는 가장 안전한 일은 「아무것도 곱하지
	 * 않는 것」이다.
	 */
	public double multiplierFor() {
		if (!Double.isFinite(multiplier)) {
			return 1.0;
		}
		return Math.max(0.0, Math.min(MAX_MULTIPLIER, multiplier));
	}
}
