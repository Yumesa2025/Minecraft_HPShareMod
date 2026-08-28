package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.PerkEffect;
import net.minecraft.world.entity.EntityType;

/**
 * 몹이 주는 피해에 배율을 건다.
 *
 * <p>예: {@code { "type": "mob_damage", "multiplier": 0.85,
 * "targets": ["minecraft:zombie", "minecraft:skeleton"] }} 는 좀비와 스켈레톤의 공격력만
 * 15% 깎는다.
 *
 * <p>몹에게 아무것도 붙이지 않는다. {@code LivingEntity.hurtServer} 진입점에서 가해자를 보고
 * 그때그때 배율을 곱하는 방식이라, 팀원의 {@code damage_dealt}/{@code damage_taken}과 정확히
 * 같은 자리에서 정확히 한 번만 걸린다. 화살처럼 던진 것에 맞아도
 * {@code DamageSource.getEntity()}가 쏜 몹을 가리키므로 함께 반영된다.
 *
 * <p>중첩은 거듭제곱이다. 값 범위는 0.0 ~ 64.0 으로, 0이면 그 몹의 피해가 사라진다.
 */
public final class MobDamageEffect implements PerkEffect {
	static final double MIN_MULTIPLIER = 0.0;
	static final double MAX_MULTIPLIER = 64.0;

	private final double multiplier;
	private final MobPerkModifiers.Targets targets;

	public MobDamageEffect(double multiplier, MobPerkModifiers.Targets targets) {
		this.multiplier = multiplier;
		this.targets = targets == null ? MobPerkModifiers.Targets.ALL_HOSTILE : targets;
	}

	/** JSON에서 만든다. 배율이 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = MobHealthEffect.readMultiplier(
				perkId, "mob_damage", json, MIN_MULTIPLIER, MAX_MULTIPLIER);
		if (multiplier == null) {
			return null;
		}
		return new MobDamageEffect(multiplier, MobPerkModifiers.parseTargets(perkId, json));
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
}
