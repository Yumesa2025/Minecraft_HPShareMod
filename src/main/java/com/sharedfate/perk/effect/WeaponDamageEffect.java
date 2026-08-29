package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkItemMatcher;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 손에 든 무기에 따라 근접 공격력을 갈아 끼우는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * {
 *   "type": "weapon_damage",
 *   "tags": ["minecraft:shovels"],
 *   "multiplier": 3.0,
 *   "othersDamage": 1.0
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code tags}/{@code items} — 우대할 무기 무리. 삽 전부는 바닐라 태그
 *       {@code minecraft:shovels} 하나로 적을 수 있다.</li>
 *   <li>{@code multiplier} — 그 무리를 들었을 때 공격력에 곱할 배수.</li>
 *   <li>{@code othersDamage} — 그 밖의 무기·도구를 들었을 때의 공격력. 적지 않으면 다른 무기는
 *       건드리지 않는다.</li>
 * </ul>
 *
 * <h2>맨손은 건드리지 않는다</h2>
 * <p>{@code othersDamage} 는 <b>공격력을 얹어 주는 아이템을 들었을 때만</b> 걸린다. 맨손이나
 * 흙덩이처럼 공격력이 없는 물건을 든 상태는 원래대로 둔다. 그렇게 하지 않으면 "다른 무기의
 * 공격력을 1로 낮춘다"는 대가가 맨손을 오히려 강화하거나 약화하는 이상한 규칙이 된다.
 * 바닐라 플레이어의 기본 공격력이 1이므로 맨손은 어차피 {@code othersDamage} 와 같다.
 *
 * <h2>어디서 걸리는가</h2>
 * <p>실제 계산은 {@link com.sharedfate.perk.PerkWeaponDamage} 가
 * {@code minecraft:attack_damage} 속성에 임시 수정자를 하나 붙여 처리한다. 피해 계산 한가운데를
 * 건드리지 않으므로 활·물약처럼 근접이 아닌 피해에는 영향이 없다.
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 수정자를 붙이고 떼는 일은
 * 손에 든 것이 바뀔 때마다 {@link com.sharedfate.perk.PerkGearManager} 가 한다.
 */
public final class WeaponDamageEffect implements PerkEffect {
	/** 배수 상한. 터무니없는 값으로 피해 계산을 깨뜨리지 않게 둔다. */
	public static final double MAX_MULTIPLIER = 64.0;
	/** 고정 공격력 상한. */
	public static final double MAX_FLAT = 1024.0;

	private final PerkItemMatcher matcher;
	private final double multiplier;
	private final @Nullable Double othersDamage;

	public WeaponDamageEffect(PerkItemMatcher matcher, double multiplier,
			@Nullable Double othersDamage) {
		this.matcher = matcher;
		this.multiplier = multiplier;
		this.othersDamage = othersDamage;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		PerkItemMatcher matcher = PerkItemMatcher.fromJson(perkId, "weapon_damage", json);
		if (matcher == null) {
			return null;
		}

		double multiplier = 1.0;
		if (json.has("multiplier")) {
			Double raw = PerkEffectType.readDouble(json, "multiplier");
			if (raw == null || raw < 0.0 || raw > MAX_MULTIPLIER) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: weapon_damage 의 multiplier 가 0~{} 범위를 벗어났습니다 ({})",
						perkId, MAX_MULTIPLIER, raw);
				return null;
			}
			multiplier = raw;
		}

		Double othersDamage = null;
		if (json.has("othersDamage")) {
			Double raw = PerkEffectType.readDouble(json, "othersDamage");
			if (raw == null || raw < 0.0 || raw > MAX_FLAT) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: weapon_damage 의 othersDamage 가 0~{} 범위를 벗어났습니다 ({})",
						perkId, MAX_FLAT, raw);
				return null;
			}
			othersDamage = raw;
		}

		if (multiplier == 1.0 && othersDamage == null) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: weapon_damage 가 아무것도 바꾸지 않습니다", perkId);
			return null;
		}
		return new WeaponDamageEffect(matcher, multiplier, othersDamage);
	}

	/** 이 무기가 우대 대상인가. */
	public boolean boosts(@Nullable ItemStack stack) {
		return matcher.matches(stack);
	}

	public PerkItemMatcher matcher() {
		return matcher;
	}

	/** 우대 대상에게 곱할 배수. */
	public double multiplier() {
		return multiplier;
	}

	/** 그 밖의 무기·도구의 공격력. 건드리지 않을 때는 null. */
	public @Nullable Double othersDamage() {
		return othersDamage;
	}
}
