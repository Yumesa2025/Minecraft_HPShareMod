package com.sharedfate.sync;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;

import java.util.Set;
import java.util.UUID;

/**
 * 「폭발 교환」이 쓰는 폭발 손상 계산기.
 *
 * <p>두 가지만 바닐라 기본값과 다르다.
 *
 * <ul>
 *   <li>{@code exemptPlayers} 에 든 사람은 이 폭발에서 절대 안 맞는다. <b>이 교환에 참여한
 *       사람 전원</b>이 여기 들어간다({@link SwapExplosionScheduler} 참고). 참여하지 않은
 *       다른 팀원이나 몹은 그대로 맞는다.</li>
 *   <li>실제 피해량에 {@code damageMultiplier}를 곱한다. 폭발 반경({@code power})은 블록이
 *       부서지는 범위와 피해가 줄어드는 거리 둘 다를 정하는데, 이 배율은 그 둘을 건드리지
 *       않고 <b>아픈 정도만</b> 따로 조절한다.</li>
 * </ul>
 */
final class SwapExplosionDamageCalculator extends ExplosionDamageCalculator {
	private final Set<UUID> exemptPlayers;
	private final double damageMultiplier;

	SwapExplosionDamageCalculator(Set<UUID> exemptPlayers, double damageMultiplier) {
		this.exemptPlayers = exemptPlayers;
		this.damageMultiplier = damageMultiplier;
	}

	@Override
	public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
		if (exemptPlayers.contains(entity.getUUID())) {
			return false;
		}
		return super.shouldDamageEntity(explosion, entity);
	}

	@Override
	public float getEntityDamageAmount(Explosion explosion, Entity entity, float seenPercent) {
		float base = super.getEntityDamageAmount(explosion, entity, seenPercent);
		double scaled = base * damageMultiplier;
		return Float.isFinite((float) scaled) ? (float) Math.max(0.0, scaled) : base;
	}
}
