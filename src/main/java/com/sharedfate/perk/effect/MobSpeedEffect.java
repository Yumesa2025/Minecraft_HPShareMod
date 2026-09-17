package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.PerkEffect;
import net.minecraft.world.entity.EntityType;

/**
 * 몹의 이동 속도에 배율을 건다.
 *
 * <p>예: {@code { "type": "mob_speed", "multiplier": 1.15,
 * "excludes": ["minecraft:ender_dragon"] }} 는 엔더 드래곤을 뺀 적대적 몹의 이동 속도를 15%
 * 올린다.
 *
 * <p>이 효과는 팀원에게 붙이는 것이 아니므로
 * {@link #apply}/{@link #remove} 는 아무 일도 하지 않고, 실제로 몹에게 수정자를 붙이고 떼는
 * 일은 {@link MobPerkModifiers} 가 맡는다. 여기서는 "얼마를, 누구에게" 만 들고 있다.
 *
 * <h2>{@code mob_action_speed} 와 헷갈리지 말 것</h2>
 * <p>이 효과는 {@code MOVEMENT_SPEED} <b>속성 하나</b>만 바꾼다. 걷는 속도만 빨라지고 때리는
 * 주기·크리퍼 부풀기·활 쏘는 간격은 그대로다. 몹이 하는 <b>모든 것</b>을 빠르게(느리게) 하려면
 * {@link MobActionSpeedEffect}({@code mob_action_speed})를 쓴다. 그쪽은 속성이 아니라 몹이 받는
 * 틱 수를 바꾼다.
 *
 * <p>둘을 같은 배율로 <b>함께 적으면 안 된다.</b> 걸음의 폭과 걸음의 수에 각각 곱해져 이동만
 * 곱절로 빨라진다. ×1.2 씩 둘을 걸면 이동은 ×1.44 가 된다.
 *
 * <h2>왜 범위가 체력보다 좁은가</h2>
 * <p>최대 체력은 64배가 되어도 「오래 때려야 하는 몹」일 뿐이지만, 이동 속도는 배율이 조금만
 * 커져도 도망칠 수 없는 판이 된다. 반대로 0 에 가까워지면 몹이 제자리에 굳어 위협이 사라진다.
 * 그래서 {@value #MIN_MULTIPLIER} ~ {@value #MAX_MULTIPLIER} 로 좁게 잡았다. 실제로 쓰는 값은
 * ×0.8 ~ ×1.3 언저리다.
 */
public final class MobSpeedEffect implements PerkEffect {
	/** 설정에서 받아들이는 배율 범위. */
	static final double MIN_MULTIPLIER = 0.1;
	static final double MAX_MULTIPLIER = 4.0;

	private final double multiplier;
	private final MobPerkModifiers.Targets targets;

	public MobSpeedEffect(double multiplier, MobPerkModifiers.Targets targets) {
		this.multiplier = multiplier;
		this.targets = targets == null ? MobPerkModifiers.Targets.ALL_HOSTILE : targets;
	}

	/** JSON에서 만든다. 배율이 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = MobHealthEffect.readMultiplier(
				perkId, "mob_speed", json, MIN_MULTIPLIER, MAX_MULTIPLIER);
		if (multiplier == null) {
			return null;
		}
		return new MobSpeedEffect(multiplier, MobPerkModifiers.parseTargets(perkId, json));
	}

	/** 안전한 범위로 자른 배율. */
	public double multiplierFor() {
		return DamageDealtEffect.clamp(multiplier);
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
