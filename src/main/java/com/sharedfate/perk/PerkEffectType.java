package com.sharedfate.perk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.CustomEffect;
import com.sharedfate.perk.effect.DamageDealtEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.MobDamageEffect;
import com.sharedfate.perk.effect.MobHealthEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JSON의 {@code type} 문자열을 효과 팩토리에 연결한다.
 *
 * <p>팩토리는 실패를 예외가 아니라 {@code null}로 알린다. 증강 정의가 잘못돼도 본 게임이
 * 멈추면 안 되기 때문에, 읽는 쪽은 {@code null}을 받으면 그 증강만 건너뛰고 계속 진행한다.
 *
 * <p>JSON 필드를 읽는 도우미도 여기 모아 둔다. 효과 구현들이 같은 방식으로 필드를 읽어야
 * 오류 처리가 한 곳에 남기 때문이다.
 */
public enum PerkEffectType {
	ATTRIBUTE("attribute", AttributeEffect::fromJson),
	DAMAGE_DEALT("damage_dealt", DamageDealtEffect::fromJson),
	DAMAGE_TAKEN("damage_taken", DamageTakenEffect::fromJson),
	STATUS_EFFECT("status_effect", StatusEffectPerk::fromJson),
	MOB_HEALTH("mob_health", MobHealthEffect::fromJson),
	MOB_DAMAGE("mob_damage", MobDamageEffect::fromJson),
	CUSTOM("custom", CustomEffect::fromJson);

	/** 효과 하나를 만드는 팩토리. 정의가 잘못됐으면 {@code null}을 돌려준다. */
	@FunctionalInterface
	public interface Factory {
		/**
		 * @param perkId 이 효과를 가진 증강의 식별자. 수정자 이름을 고유하게 만드는 데 쓴다
		 * @param index  그 증강 안에서 이 효과가 몇 번째인지
		 * @param json   효과 정의 객체
		 */
		PerkEffect create(String perkId, int index, JsonObject json);
	}

	private final String id;
	private final Factory factory;

	PerkEffectType(String id, Factory factory) {
		this.id = id;
		this.factory = factory;
	}

	public String id() {
		return id;
	}

	/** JSON의 type 문자열에 맞는 타입. 알 수 없는 값이면 null. */
	public static PerkEffectType fromId(String id) {
		if (id == null) {
			return null;
		}
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		for (PerkEffectType type : values()) {
			if (type.id.equals(normalized)) {
				return type;
			}
		}
		return null;
	}

	/** 효과를 만든다. 정의가 잘못됐으면 null. */
	public PerkEffect create(String perkId, int index, JsonObject json) {
		if (json == null) {
			return null;
		}
		try {
			return factory.create(perkId, index, json);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn(
					"증강 {} 의 {}번째 효과({})를 만들지 못했습니다", perkId, index, id, error);
			return null;
		}
	}

	/** 문자열 필드. 없거나 문자열이 아니면 null. */
	public static String readString(JsonObject json, String key) {
		JsonPrimitive primitive = primitive(json, key);
		return primitive != null && primitive.isString() ? primitive.getAsString() : null;
	}

	/** 실수 필드. 없거나 숫자가 아니면 null. */
	public static Double readDouble(JsonObject json, String key) {
		JsonPrimitive primitive = primitive(json, key);
		if (primitive == null || !primitive.isNumber()) {
			return null;
		}
		double value = primitive.getAsDouble();
		return Double.isFinite(value) ? value : null;
	}

	/** 정수 필드. 없거나 숫자가 아니면 기본값. */
	public static int readInt(JsonObject json, String key, int fallback) {
		JsonPrimitive primitive = primitive(json, key);
		if (primitive == null || !primitive.isNumber()) {
			return fallback;
		}
		double value = primitive.getAsDouble();
		if (!Double.isFinite(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			return fallback;
		}
		return (int) value;
	}

	/** 참거짓 필드. 없거나 참거짓이 아니면 기본값. */
	public static boolean readBoolean(JsonObject json, String key, boolean fallback) {
		JsonPrimitive primitive = primitive(json, key);
		return primitive != null && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
	}

	/**
	 * 문자열 배열 필드.
	 *
	 * <p>세 가지 결과를 구분해야 하는 자리에 쓴다. 필드가 아예 없으면 {@code null},
	 * 배열이긴 한데 쓸 만한 문자열이 하나도 없으면 빈 목록, 그 외에는 문자열만 골라낸 목록이다.
	 * {@code targets} 처럼 "필드를 안 적었다"와 "적었는데 결과가 비었다"가 다른 뜻인 곳에서
	 * 이 구분이 필요하다. 배열이 아닌 값이 들어오면 빈 목록으로 보고 판단은 부르는 쪽에 맡긴다.
	 */
	public static @Nullable List<String> readStringList(JsonObject json, String key) {
		if (json == null || key == null) {
			return null;
		}
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (!element.isJsonArray()) {
			return List.of();
		}
		JsonArray array = element.getAsJsonArray();
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement entry : array) {
			if (entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
				String value = entry.getAsString().trim();
				if (!value.isEmpty()) {
					values.add(value);
				}
			}
		}
		return values;
	}

	private static JsonPrimitive primitive(JsonObject json, String key) {
		if (json == null || key == null) {
			return null;
		}
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}
}
