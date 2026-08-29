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
 * {@code hurtServer(magic)}, 위더는 {@code hurtServer(wither)}, 재생은 {@code heal(1.0F)},
 * 허기는 {@code causeFoodExhaustion(..)} 을 호출한다. 즉 이 호출을 감싸면 "이 변화는 이
 * 상태이상이 원인이다"를 정확히 알 수 있다.
 *
 * <p>{@link Redirect} 를 쓰는 이유는 두 가지다. 첫째, 여기서 {@code this} 가
 * {@link MobEffectInstance} 라 {@code getEffect()} 로 상태이상 종류를 그대로 넘길 수 있다.
 * 둘째, 자바 {@code finally} 로 감싸므로 안쪽에서 예외가 터져도 구간 표시가 새지 않는다.
 * HEAD/RETURN 두 개의 {@code @Inject} 로는 예외 경로에서 구간이 열린 채 남는다.
 *
 * <p><b>원래 호출은 반드시 그대로 한다.</b> 대표가 아닌 팀원이라고 해서 여기서 호출을 통째로
 * 건너뛰면 안 된다. {@code applyEffectTick} 은 공유 풀 말고도 하는 일이 많고, 그 반환값이
 * 상태이상의 수명을 정하기 때문이다. 26.2 기준으로
 *
 * <ul>
 *   <li>{@code BadOmenMobEffect} 는 그 안에서 습격 예고를 붙이고 {@code false} 를 돌려줘
 *       스스로 사라진다.
 *   <li>{@code RaidOmenMobEffect} 는 습격을 실제로 시작하고 {@code false} 를 돌려준다.
 *   <li>{@code AbsorptionMobEffect} 는 흡수량이 0 이 되면 {@code false} 로 스스로 사라진다.
 * </ul>
 *
 * <p>{@code tickServer} 는 이 반환값이 {@code false} 면 상태이상을 제거한다. 건너뛰고
 * {@code true} 를 지어내면 저 셋은 대표가 아닌 팀원에게 영원히 남고, {@code false} 를
 * 지어내면 재생이 첫 틱에 사라져 "상태이상 공유는 유지한다"가 깨진다. 모드가 추가한
 * 상태이상은 그 안에서 무엇이든 할 수 있으니 더 위험하다.
 *
 * <p>그래서 이 mixin 자체는 어떤 동작도 바꾸지 않는다. 구간만 표시하고, 공유 풀을 건드리는
 * 지점(피해·회복·허기)을 골라 막는 일은 {@link SharedEffectDamage} 와 각 진입점의 mixin 이
 * 나눠 맡는다.
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
