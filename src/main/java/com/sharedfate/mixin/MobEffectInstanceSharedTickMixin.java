package com.sharedfate.mixin;

import com.sharedfate.sync.SharedEffectDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 상태이상의 주기 효과가 도는 구간을 표시한다.
 *
 * <p>26.2 에서 {@code MobEffectInstance.tickServer} 는 주기가 돌아온 틱에 딱 한 번
 * {@code MobEffect.applyEffectTick(ServerLevel, LivingEntity, int)} 을 부른다. 독은 그 안에서
 * {@code hurtServer(magic)}, 위더는 {@code hurtServer(wither)} 를 호출한다. 즉 이 호출을
 * 감싸면 "이 피해는 이 상태이상이 원인이다"를 정확히 알 수 있다.
 *
 * <p>{@link Redirect} 를 쓰는 이유는 두 가지다. 첫째, 여기서 {@code this} 가
 * {@link MobEffectInstance} 라 {@code getEffect()} 로 상태이상 종류를 그대로 넘길 수 있다.
 * 둘째, 자바 {@code finally} 로 감싸므로 안쪽에서 예외가 터져도 구간 표시가 새지 않는다.
 * HEAD/RETURN 두 개의 {@code @Inject} 로는 예외 경로에서 구간이 열린 채 남는다.
 *
 * <p>원래 호출은 그대로 한다. 이 mixin 자체는 어떤 동작도 바꾸지 않고, 판정은 전부
 * {@link SharedEffectDamage} 가 한다.
 */
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceSharedTickMixin {
	@Redirect(
			method = "tickServer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick"
							+ "(Lnet/minecraft/server/level/ServerLevel;"
							+ "Lnet/minecraft/world/entity/LivingEntity;I)Z"))
	private boolean sharedfate$markSharedEffectTick(MobEffect effect, ServerLevel level,
			LivingEntity entity, int amplifier) {
		SharedEffectDamage.beginEffectTick(entity, ((MobEffectInstance) (Object) this).getEffect());
		try {
			return effect.applyEffectTick(level, entity, amplifier);
		} finally {
			SharedEffectDamage.endEffectTick();
		}
	}
}
