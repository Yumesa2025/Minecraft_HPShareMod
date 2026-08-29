package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 음식이 채워 주는 양에 배율을 걸고, 먹는 순간에 잠깐 얹을 효과를 붙인다.
 *
 * <pre>{@code
 * { "type": "food_nutrition", "multiplier": 3.0,
 *   "effects": [ { "type": "status_effect", "effect": "minecraft:poison",
 *                  "amplifier": 0, "duration": 4 } ] }
 * }</pre>
 *
 * <p>골드 3 "상한 진수성찬" 이 이 타입 하나로 좋은 면과 나쁜 면을 함께 적는다.
 *
 * <h2>무엇에 곱하는가</h2>
 * <p>26.2 에서 음식이 배를 채우는 곳은 {@code FoodData.eat(FoodProperties)} 한 줄이고, 그 안은
 * {@code add(properties.nutrition(), properties.saturation())} 이 전부다. 그래서 배율은 그 한
 * 줄에 <b>배율을 먹인 {@link FoodProperties} 를 대신 건네는</b> 방식으로 건다. 계산을 우리가
 * 다시 쓰지 않고 바닐라 경로를 그대로 지나가므로, 20 상한도 포만감이 허기를 넘지 못하는 규칙도
 * 바닐라와 완전히 같다. 실제로 바꿔치기하는 자리는
 * {@link com.sharedfate.mixin.FoodPropertiesMixin} 이다.
 *
 * <p>허기와 포만감에 같은 배율이 걸린다. 포만감만 그대로 두면 배는 세 배로 차는데 유지 시간은
 * 그대로라, 화면에 보이는 "든든함"과 실제가 어긋난다.
 *
 * <h2>{@code no_food_hunger} 와 함께 걸렸을 때</h2>
 * <p><b>막는 쪽이 이긴다.</b> {@code no_food_hunger} 는 "음식으로는 허기가 회복되지 않는다"는
 * 절대적인 약속이라 배수의 대상이 남지 않는다. 3 × 0 은 0 이다. 다만 아래 {@code effects} 는
 * 그래도 걸린다. 그건 "회복량"의 대가가 아니라 "먹는 행위"의 대가이기 때문이다. 판정 순서는
 * {@link com.sharedfate.perk.PerkFoodRules} 에 한 곳으로 모아 뒀다.
 *
 * <h2>{@code effects} — 먹는 순간에 잠깐 얹을 효과</h2>
 * <p>{@link OnKillEffect} 의 {@code effects} 와 형식·규칙이 똑같다. {@code status_effect} 는
 * 무한이 아니라 {@code duration} 초 동안만 걸리고, 적지 않으면
 * {@link OnKillEffect#DEFAULT_DURATION_SECONDS} 초다. 지속시간이 유한하므로
 * {@code PerkStatusEffects} 가 증강분으로 오해해 팀 공유에서 빼는 일도 없다.
 */
public final class FoodNutritionEffect implements PerkEffect {
	/** 배율 상한. 빵 한 조각으로 배가 다 차고도 남는 수준을 넘기지 않는다. */
	static final double MAX_MULTIPLIER = 16.0;
	/** 배율을 먹인 영양의 상한. {@code FoodData} 는 20 에서 자르므로 그 위는 뜻이 없다. */
	public static final int MAX_SCALED_NUTRITION = 400;

	private final double multiplier;
	private final List<OnKillEffect.Grant> grants;

	public FoodNutritionEffect(double multiplier, List<OnKillEffect.Grant> grants) {
		this.multiplier = multiplier;
		this.grants = List.copyOf(grants);
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		double multiplier = 1.0;
		JsonElement raw = json.get("multiplier");
		if (raw != null && !raw.isJsonNull()) {
			Double value = PerkEffectType.readDouble(json, "multiplier");
			if (value == null || value < 0.0 || value > MAX_MULTIPLIER) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: food_nutrition 의 multiplier 가 범위를 벗어났습니다 ({})", perkId, raw);
				return null;
			}
			multiplier = value;
		}

		List<OnKillEffect.Grant> grants = readGrants(perkId, index, json);
		if (grants == null) {
			return null;
		}
		if (multiplier == 1.0 && grants.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: food_nutrition 이 아무것도 바꾸지 않습니다. multiplier 나 effects 중 하나는 있어야 합니다",
					perkId);
			return null;
		}
		return new FoodNutritionEffect(multiplier, grants);
	}

	/** 회복량에 곱할 배율. */
	public double multiplier() {
		return multiplier;
	}

	/** 먹는 순간에 얹을 하위 효과들. */
	public List<OnKillEffect.Grant> grants() {
		return grants;
	}

	/**
	 * 배율을 먹인 음식 정의.
	 *
	 * <p>배율이 1 이면 <b>받은 것을 그대로</b> 돌려준다. 증강이 없는 팀의 먹기 경로에 새 객체가
	 * 하나도 생기지 않아야 하고, 부르는 쪽이 {@code ==} 로 "손대지 않았음"을 알 수 있어야 한다.
	 */
	public static FoodProperties scale(FoodProperties properties, double multiplier) {
		if (properties == null || multiplier == 1.0) {
			return properties;
		}
		long nutrition = Math.round(properties.nutrition() * multiplier);
		int safeNutrition = (int) Math.max(0L, Math.min(MAX_SCALED_NUTRITION, nutrition));
		float saturation = (float) (properties.saturation() * multiplier);
		if (!Float.isFinite(saturation)) {
			saturation = properties.saturation();
		}
		return new FoodProperties(safeNutrition, Math.max(0.0F, saturation),
				properties.canAlwaysEat());
	}

	/**
	 * 먹은 팀원에게 하위 효과를 얹는다.
	 *
	 * <p>상태이상은 한 명에게만 걸어도 {@code EffectSync} 가 팀 전원에게 퍼뜨린다. 여기서 굳이
	 * 팀 전원을 돌지 않는 이유가 그것이다. {@link OnKillEffect#grantTemporaryEffects} 와 같은
	 * 규칙이고, 하위 효과 하나가 실패해도 나머지는 계속 얹는다.
	 */
	public void grantOnEat(@Nullable ServerPlayer eater) {
		if (eater == null || grants.isEmpty()) {
			return;
		}
		for (OnKillEffect.Grant grant : grants) {
			try {
				grantOne(eater, grant);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("food_nutrition 의 하위 효과를 얹지 못했습니다", error);
			}
		}
	}

	private static void grantOne(ServerPlayer eater, OnKillEffect.Grant grant) {
		if (!(grant.effect() instanceof StatusEffectPerk status)) {
			// 붙였다 떼는 보통의 효과다. 그대로 적용한다.
			grant.effect().apply(eater);
			return;
		}
		Holder<MobEffect> resolved = status.resolvedEffect();
		if (resolved == null) {
			return;
		}
		// 무한 지속이 아니라 정해진 시간만 걸어야 "먹는 순간에 잠깐"이 된다.
		eater.addEffect(new MobEffectInstance(
				resolved, grant.durationTicks(), status.amplifier(), false, false, true));
	}

	/**
	 * {@code effects} 배열을 재귀적으로 읽는다.
	 *
	 * <p>필드가 없으면 빈 목록이다. 하나라도 잘못됐으면 null 을 돌려주고, 그러면 이 증강 전체가
	 * 버려진다. 설명은 그대로인데 효과 일부만 빠진 증강은 플레이어를 속이는 셈이기 때문이다.
	 * 하위 효과의 순번은 {@link OnKillEffect#nestedIndex} 와 같은 식으로 민다. 부모 순번이
	 * 다르면 자식 순번 묶음도 달라서, 한 증강에 {@code on_kill} 과 {@code food_nutrition} 이
	 * 함께 있어도 속성 수정자 이름이 겹치지 않는다.
	 */
	private static @Nullable List<OnKillEffect.Grant> readGrants(String perkId, int index,
			JsonObject json) {
		JsonElement element = json.get("effects");
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: food_nutrition 의 effects 가 배열이 아닙니다", perkId);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		List<OnKillEffect.Grant> grants = new ArrayList<>(array.size());
		for (int child = 0; child < array.size(); child++) {
			JsonElement raw = array.get(child);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: food_nutrition 의 {}번째 하위 효과가 객체가 아닙니다",
						perkId, child);
				return null;
			}
			JsonObject childJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(childJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: food_nutrition 하위 효과의 알 수 없는 type 입니다 ({})",
						perkId, typeId);
				return null;
			}
			PerkEffect effect = type.create(perkId, OnKillEffect.nestedIndex(index, child), childJson);
			if (effect == null) {
				return null;
			}
			Integer duration = readDurationTicks(perkId, childJson);
			if (duration == null) {
				return null;
			}
			grants.add(new OnKillEffect.Grant(effect, duration));
		}
		return grants;
	}

	/** 하위 효과의 {@code duration}(초)을 틱으로 바꾼다. 범위를 벗어나면 null. */
	private static @Nullable Integer readDurationTicks(String perkId, JsonObject json) {
		Double seconds = PerkEffectType.readDouble(json, "duration");
		if (seconds == null) {
			if (json.has("duration") && !json.get("duration").isJsonNull()) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: food_nutrition 하위 효과의 duration 이 숫자가 아닙니다", perkId);
				return null;
			}
			seconds = OnKillEffect.DEFAULT_DURATION_SECONDS;
		}
		if (seconds <= 0.0 || seconds > OnKillEffect.MAX_DURATION_SECONDS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: food_nutrition 하위 효과의 duration 이 범위를 벗어났습니다 ({})", perkId, seconds);
			return null;
		}
		return Math.max(1, (int) Math.round(seconds * OnKillEffect.TICKS_PER_SECOND));
	}
}
