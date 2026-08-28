package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.world.entity.EntityType;

/**
 * 몹의 최대 체력에 배율을 건다.
 *
 * <p>예: {@code { "type": "mob_health", "multiplier": 0.5,
 * "excludes": ["minecraft:ender_dragon"] }} 는 엔더 드래곤을 뺀 적대적 몹의 체력을 절반으로
 * 만든다.
 *
 * <p>이 효과는 팀원에게 붙이는 것이 아니므로 {@link #apply}/{@link #remove} 는 아무 일도 하지
 * 않는다. 실제로 몹에게 수정자를 붙이고 떼는 일은 {@link MobPerkModifiers} 가 맡는다.
 * 여기서는 "얼마를, 누구에게" 만 들고 있다.
 *
 * <p>중첩은 거듭제곱이다. 0.8배 증강을 두 번 쌓으면 0.64배가 된다.
 */
public final class MobHealthEffect implements PerkEffect {
	/**
	 * 설정에서 받아들이는 배율 범위. 0을 허용하면 최대 체력이 0인 몹이 되므로 하한을 둔다.
	 */
	static final double MIN_MULTIPLIER = 0.01;
	static final double MAX_MULTIPLIER = 64.0;

	private final double multiplier;
	private final MobPerkModifiers.Targets targets;

	public MobHealthEffect(double multiplier, MobPerkModifiers.Targets targets) {
		this.multiplier = multiplier;
		this.targets = targets == null ? MobPerkModifiers.Targets.ALL_HOSTILE : targets;
	}

	/** JSON에서 만든다. 배율이 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = readMultiplier(perkId, "mob_health", json, MIN_MULTIPLIER, MAX_MULTIPLIER);
		if (multiplier == null) {
			return null;
		}
		return new MobHealthEffect(multiplier, MobPerkModifiers.parseTargets(perkId, json));
	}

	/** 중첩까지 반영한 배율. */
	public double multiplierFor(int stacks) {
		return DamageDealtEffect.power(multiplier, stacks);
	}

	/** 이 종류의 몹에 걸리는 효과인지. */
	public boolean appliesTo(EntityType<?> type, boolean hostile) {
		return targets.matches(type, hostile);
	}

	public double multiplier() {
		return multiplier;
	}

	public MobPerkModifiers.Targets targets() {
		return targets;
	}

	/**
	 * {@code mob_health}/{@code mob_damage}가 함께 쓰는 multiplier 읽기.
	 *
	 * <p>범위가 서로 달라 {@link DamageDealtEffect#readMultiplier}를 그대로 쓸 수 없다.
	 * 최대 체력은 0이 될 수 없고 피해는 0이 될 수 있다.
	 */
	static Double readMultiplier(String perkId, String typeId, JsonObject json,
			double min, double max) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < min || multiplier > max) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 효과의 multiplier 값이 없거나 범위를 벗어났습니다 ({})",
					perkId, typeId, multiplier);
			return null;
		}
		return multiplier;
	}
}
