package com.sharedfate.mixin;

import com.sharedfate.sync.SharedEffectDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 공유된 상태이상이 이미 다른 팀원의 배를 곯린 몫이면 여기서 버린다.
 *
 * <p>26.2 의 {@code HungerMobEffect.applyEffectTick} 은
 * {@code player.causeFoodExhaustion(0.005F * (등급 + 1))} 한 번이 전부다.
 * {@code Player.causeFoodExhaustion} 은 {@code ServerPlayer} 가 재정의하지 않고, 그 안에서
 * {@code FoodData.addExhaustion} 을 부르는 유일한 통로라 여기 한 곳만 잡으면 된다.
 *
 * <p>{@code FoodData} 쪽이 아니라 {@code Player} 쪽에 거는 이유는 {@code FoodData} 가 주인을
 * 모르기 때문이다. 여기서는 {@code this} 가 곧 그 플레이어라 "지금 상태이상 틱이 도는 대상과
 * 같은가"를 그대로 물어볼 수 있다.
 *
 * <p>달리기·점프·수영·채굴로 쌓이는 평소 허기 소모는 상태이상 틱 구간 밖이라 그대로 통과한다.
 * 팀원마다 따로 움직인 결과이므로 합산되는 게 맞다.
 */
@Mixin(Player.class)
public abstract class PlayerSharedExhaustionMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void sharedfate$skipDuplicateSharedEffectExhaustion(float exhaustion, CallbackInfo callback) {
		if (SharedEffectDamage.isDuplicateEffectExhaustion((LivingEntity) (Object) this)) {
			callback.cancel();
		}
	}
}
