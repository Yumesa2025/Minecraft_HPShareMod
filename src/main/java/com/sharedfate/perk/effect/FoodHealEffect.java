package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 음식을 먹을 때마다 팀 공유 체력을 정해진 만큼 채운다.
 *
 * <p>정의는 {@code { "type": "food_heal", "health": 2.0 }} 다. 회복 2단계 「먹는 게 남는 거다」가
 * 이 타입 하나로 만들어진다.
 *
 * <p>이 효과는 개인의 체력을 건드리지 않는다. 개인 체력이 움직이면 {@code StatMirror} 가 그
 * 변화량을 관측해 공유 풀에 더하는데, 그 관측은 팀원 전원을 대상으로 돌기 때문에 회복이
 * 인원수만큼 불어나거나 최소한 양이 정확하지 않다.
 *
 * <p>{@link #apply}/{@link #remove} 를 재정의하지 않는 것이 그 뜻이다. 실제로 공유 풀에 더하는 일은
 * {@link com.sharedfate.perk.PerkFoodRules} 가 맡는다 — 왜 개인이 아니라 풀에 더하는지는
 * {@link com.sharedfate.perk.PerkKillRewards} 머리말에 자세히 적혀 있다.
 *
 * <h2>{@code no_food_hunger} 와 함께 걸렸을 때</h2>
 * <p><b>그래도 회복은 일어난다.</b> 이 효과는 「허기를 얼마나 채우는가」와 아무 상관이 없고
 * 「먹는 행위」에 붙는 보상이다. {@code food_nutrition} 의 하위 {@code effects} 가 회복이
 * 막혀도 그대로 걸리는 것과 같은 규칙이다.
 */
public final class FoodHealEffect implements PerkEffect {
	/**
	 * 한 번 먹을 때 채울 수 있는 체력의 상한.
	 *
	 * <p>기본 최대 체력 20 을 넘겨 봐야 뜻이 없다. 실제로 공유 풀에 들어갈 때 팀 최대 체력에서
	 * 한 번 더 잘리므로 여기서는 「정의 실수를 걸러 내는 그물」 노릇만 한다.
	 */
	static final double MAX_HEALTH = 20.0;

	private final double health;

	public FoodHealEffect(double health) {
		this.health = health;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double health = PerkEffectType.readDouble(json, "health");
		if (health == null || !(health > 0.0) || health > MAX_HEALTH) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: food_heal 의 health 가 없거나 범위를 벗어났습니다 ({})", perkId, health);
			return null;
		}
		return new FoodHealEffect(health);
	}

	/** 정의에 적힌 회복량. */
	public double health() {
		return health;
	}

	/**
	 * 안전한 범위로 자른 회복량.
	 *
	 * <p>JSON 경로는 이미 범위를 검사하지만 생성자는 공개돼 있어 Java 쪽에서 어떤 값이든 들어올
	 * 수 있다. 공유 풀 계산이 NaN 이나 무한대를 보면 안 된다.
	 */
	public float healthFor() {
		if (!Double.isFinite(health)) {
			return 0.0F;
		}
		return (float) Math.max(0.0, Math.min(MAX_HEALTH, health));
	}
}
