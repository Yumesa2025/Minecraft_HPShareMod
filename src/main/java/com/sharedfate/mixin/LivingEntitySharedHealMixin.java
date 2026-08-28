package com.sharedfate.mixin;

import com.sharedfate.sync.SharedEffectDamage;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 공유된 상태이상이 이미 다른 팀원을 회복시킨 몫이면 여기서 버린다.
 *
 * <p>26.2 의 회복 진입점은 {@code LivingEntity.heal(float)} 하나다. {@code Avatar} 도
 * {@code Player} 도 {@code ServerPlayer} 도 재정의하지 않으므로 여기 한 곳만 잡으면 된다.
 * {@code RegenerationMobEffect.applyEffectTick} 은 {@code heal(1.0F)} 만 부르고 나머지는
 * 최대 체력 비교뿐이라, 이 지점을 막는 것이 곧 "그 팀원의 재생 틱은 회복을 만들지 않는다"다.
 *
 * <p>취소해도 {@code heal} 은 {@code void} 라 호출자에게 아무 신호도 가지 않는다.
 * {@code applyEffectTick} 은 원래대로 {@code true} 를 돌려주고 재생은 계속 남는다. 즉 팀원
 * 넷 모두 재생 아이콘과 입자를 그대로 갖는다.
 *
 * <p>{@link SharedEffectDamage} 의 판정은 상태이상 틱 구간 밖이면 필드 비교 두 번에 끝나므로,
 * 금사과·명령어·평화 난이도 자연 회복 같은 평소 회복 경로에는 사실상 부담이 없다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySharedHealMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void sharedfate$skipDuplicateSharedEffectHeal(float amount, CallbackInfo callback) {
		if (SharedEffectDamage.isDuplicateEffectHeal((LivingEntity) (Object) this)) {
			callback.cancel();
		}
	}
}
