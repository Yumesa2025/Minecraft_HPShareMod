package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 허기가 쌓이는 속도에 배율을 건다.
 *
 * <p>정의는 {@code { "type": "hunger_drain", "multiplier": 2.0 }} 하나뿐이다. 실버 1
 * "무한 회복, 무한 식비" 가 재생 I 의 대가로 이 타입을 쓴다.
 *
 * <h2>무엇에 곱하는가</h2>
 * <p>배가 줄어드는 양이 아니라 <b>소모도(exhaustion)</b> 에 곱한다. 마인크래프트는 달리기·점프·
 * 채굴·수영·공격·허기 상태이상이 모두 {@code Player.causeFoodExhaustion} 으로 소모도를 쌓고,
 * 그 값이 4.0 을 넘을 때마다 {@code FoodData.tick} 이 포만감이나 허기를 1 깎는다.
 * 그래서 소모도 입구에 곱하면 "허기가 줄어드는 속도"가 그대로 배가 된다. 배가 줄어드는 순간을
 * 잡으려 하면 포만감이 먼저 닳는 구간을 놓치고, 팀 공유 허기와도 어긋난다.
 *
 * <p>자연 회복이 치르는 대가는 이 배율을 타지 않는다. 회복의 대가는 플레이어가 한 행동이
 * 아니기 때문이다. 까닭은 {@link NoHungerDrainEffect} 와
 * {@link com.sharedfate.mixin.FoodDataRegenExhaustionMixin} 에 적어 뒀다.
 *
 * <p>실제로 곱하는 자리는 {@link com.sharedfate.mixin.PlayerSharedExhaustionMixin} 이고,
 * 지금 이 팀의 배율이 얼마인지 세는 것은 {@link com.sharedfate.perk.PerkFoodRules} 다.
 *
 * <h2>이중 적용이 없는 이유</h2>
 * <p>소모도는 팀원 개인이 자기 움직임으로 쌓는 값이라 사람마다 따로 세는 것이 맞다. 하나의
 * 원인이 팀 인원수만큼 복제되는 경우 — 공유된 허기 상태이상 — 는 같은 자리에 먼저 걸려 있는
 * {@code SharedEffectDamage} 판정이 대표 한 명만 남기고 나머지를 버린다. 이 배율은 그 판정을
 * 통과한 1인분에만 곱해지므로 배수 버그가 다시 살아나지 않는다.
 */
public final class HungerDrainEffect implements PerkEffect {
	/** 배율 상한. 이보다 크면 몇 초 만에 배가 비어 증강이 아니라 사고다. */
	static final double MAX_MULTIPLIER = 16.0;

	private final double multiplier;

	public HungerDrainEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < 0.0 || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: hunger_drain 의 multiplier 가 없거나 범위를 벗어났습니다 ({})",
					perkId, multiplier);
			return null;
		}
		if (multiplier == 1.0) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: hunger_drain 의 multiplier 가 1 이라 아무 일도 하지 않습니다", perkId);
			return null;
		}
		return new HungerDrainEffect(multiplier);
	}

	/** 소모도에 곱할 배율. */
	public double multiplier() {
		return multiplier;
	}
}
