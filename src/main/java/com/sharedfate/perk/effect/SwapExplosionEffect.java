package com.sharedfate.perk.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * 위치 교환 순간, 방금 비운 자리에서 폭발을 일으키는 효과.
 *
 * <pre>{@code
 * { "type": "swap_explosion", "radius": 4.0, "damage_multiplier": 1.0, "break_blocks": true }
 * }</pre>
 *
 * <p>골드 「폭발 교환」이 이 타입을 쓴다. 세 필드 모두 안 적으면 그 증강의 기본값(반경 4.0,
 * 피해 배율 ×1.0, 블록 파괴 켜짐)이다. 세기를 조절하려면 코드가 아니라 정의 파일에서 이 세
 * 값만 고치면 된다.
 *
 * <h2>필드</h2>
 * <ul>
 *   <li>{@code radius} — 폭발 반경이자 바닐라 {@code power}. 블록이 부서지는 범위와 피해가
 *       줄어드는 거리 둘 다 이 값 하나로 정해진다({@code 0.5}~{@code 24.0}).</li>
 *   <li>{@code damage_multiplier}(={@code damageMultiplier}) — 반경과 별개로 피해량에만
 *       곱하는 배율({@code 0.0}~{@code 10.0}). 반경은 그대로 두고 아픈 정도만 줄이거나 늘릴 때
 *       쓴다.</li>
 *   <li>{@code break_blocks}(={@code breakBlocks}) — 블록을 부수는가. 꺼도 피해·넉백은
 *       그대로다.</li>
 * </ul>
 *
 * <h2>불은 붙지 않는다</h2>
 * <p>화염 여부는 JSON 값이 아니라 코드에 고정돼 있다. 위치 교환마다 반복해서 터지는
 * 폭발이라 불이 붙으면 본진 주변이 계속 타들어 간다.
 *
 * <h2>누가 맞는가는 여기서 정하지 않는다</h2>
 * <p>이 클래스는 "얼마나 세게, 블록을 부수며"만 들고 있는 자료 그릇이다. 실제로 어디서
 * 터뜨리고 누구를 면역으로 둘지는 {@link com.sharedfate.sync.PositionSwapManager}가
 * {@link com.sharedfate.perk.PerkSwapRules#swapExplosions}로 이 정의를 받아 처리한다.
 */
public final class SwapExplosionEffect implements PerkEffect {
	public static final float DEFAULT_RADIUS = 4.0F;
	public static final double DEFAULT_DAMAGE_MULTIPLIER = 1.0;
	public static final boolean DEFAULT_BREAK_BLOCKS = true;

	static final float MIN_RADIUS = 0.5F;
	/**
	 * 반경 상한.
	 *
	 * <p>바닐라에서 가장 큰 폭발이 차지 크리퍼의 6 이라 원래 16 으로 두었지만, 「폭발 교환」이
	 * 20 을 쓰면서 그 위로 올렸다. 블록을 부수지 않는 폭발이라 반경이 커도 지형은 그대로다.
	 */
	static final float MAX_RADIUS = 24.0F;
	static final double MIN_DAMAGE_MULTIPLIER = 0.0;
	static final double MAX_DAMAGE_MULTIPLIER = 10.0;

	private final float radius;
	private final double damageMultiplier;
	private final boolean breakBlocks;

	private SwapExplosionEffect(float radius, double damageMultiplier, boolean breakBlocks) {
		this.radius = radius;
		this.damageMultiplier = damageMultiplier;
		this.breakBlocks = breakBlocks;
	}

	/** JSON에서 만든다. 값이 있는데 범위를 벗어나면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Float radius = readFloatOrDefault(perkId, json, "radius", DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS);
		if (radius == null) {
			return null;
		}
		String multiplierKey = json.has("damage_multiplier") ? "damage_multiplier" : "damageMultiplier";
		Double damageMultiplier = readDoubleOrDefault(perkId, json, multiplierKey,
				DEFAULT_DAMAGE_MULTIPLIER, MIN_DAMAGE_MULTIPLIER, MAX_DAMAGE_MULTIPLIER);
		if (damageMultiplier == null) {
			return null;
		}
		String breakKey = json.has("break_blocks") ? "break_blocks" : "breakBlocks";
		Boolean breakBlocks = readBoolean(perkId, json, breakKey, DEFAULT_BREAK_BLOCKS);
		if (breakBlocks == null) {
			return null;
		}
		return new SwapExplosionEffect(radius, damageMultiplier, breakBlocks);
	}

	/** 폭발 반경이자 바닐라 {@code power}. */
	public float radius() {
		return radius;
	}

	/** 반경과 별개로 피해량에만 곱하는 배율. */
	public double damageMultiplier() {
		return damageMultiplier;
	}

	/** 블록을 부수는가. */
	public boolean breakBlocks() {
		return breakBlocks;
	}

	private static @Nullable Float readFloatOrDefault(String perkId, JsonObject json, String key,
			float fallback, float min, float max) {
		if (!json.has(key)) {
			return fallback;
		}
		Double value = PerkEffectType.readDouble(json, key);
		if (value == null || value < min || value > max) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: swap_explosion 의 {} 값이 범위를 벗어났습니다 ({})", perkId, key, value);
			return null;
		}
		return value.floatValue();
	}

	private static @Nullable Double readDoubleOrDefault(String perkId, JsonObject json, String key,
			double fallback, double min, double max) {
		if (!json.has(key)) {
			return fallback;
		}
		Double value = PerkEffectType.readDouble(json, key);
		if (value == null || value < min || value > max) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: swap_explosion 의 {} 값이 범위를 벗어났습니다 ({})", perkId, key, value);
			return null;
		}
		return value;
	}

	/** 참·거짓 필드. 없으면 {@code fallback}, 적었는데 참·거짓이 아니면 null. */
	private static @Nullable Boolean readBoolean(String perkId, JsonObject json, String key,
			boolean fallback) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: swap_explosion 의 {} 가 참·거짓이 아닙니다 ({})", perkId, key, element);
			return null;
		}
		return element.getAsBoolean();
	}
}
