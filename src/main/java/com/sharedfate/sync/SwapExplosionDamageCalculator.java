package com.sharedfate.sync;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;

import java.util.UUID;

/**
 * 「폭발 교환」이 쓰는 폭발 손상 계산기.
 *
 * <p>두 가지만 바닐라 기본값과 다르다.
 *
 * <ul>
 *   <li>{@code exemptPlayer} 는 이 폭발에서 절대 안 맞는다. 위치 교환에서 <b>방금 그 자리를
 *       떠난 사람</b>이 여기 들어간다. 도착한 사람이나 다른 팀원, 몹은 그대로 맞는다 — "면역은
 *       떠난 사람만"이라는 정해진 규칙이다.</li>
 *   <li>실제 피해량에 {@code damageMultiplier}를 곱한다. 폭발 반경({@code power})은 블록이
 *       부서지는 범위와 피해가 줄어드는 거리 둘 다를 정하는데, 이 배율은 그 둘을 건드리지
 *       않고 <b>아픈 정도만</b> 따로 조절한다.</li>
 * </ul>
 */
final class SwapExplosionDamageCalculator extends ExplosionDamageCalculator {
	private final UUID exemptPlayer;
	private final double damageMultiplier;

	SwapExplosionDamageCalculator(UUID exemptPlayer, double damageMultiplier) {
		this.exemptPlayer = exemptPlayer;
		this.damageMultiplier = damageMultiplier;
	}

	@Override
	public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
		if (entity.getUUID().equals(exemptPlayer)) {
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
