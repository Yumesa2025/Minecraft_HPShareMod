package com.sharedfate.mixin;

import com.sharedfate.perk.PerkDamage;
import com.sharedfate.sync.SharedEffectDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 서버측 피해 진입점에 걸린 SharedFate 의 두 가지 처리.
 *
 * <p>하나는 증강의 피해 배율을 실제 피해량에 꽂아 넣는 것이고, 다른 하나는 공유된
 * 상태이상이 팀 전원에게 똑같이 주는 중복 피해를 버리는 것이다. 둘 다 같은
 * {@code hurtServer} 진입점을 보므로 한 mixin 에 모아 둔다.
 *
 * <p>26.2 의 서버측 피해 진입점은
 * {@code LivingEntity.hurtServer(ServerLevel, DamageSource, float)} 하나다.
 * {@code ServerPlayer.hurtServer} → {@code Player.hurtServer} → {@code LivingEntity.hurtServer}
 * 로 이어지는 사슬의 끝이라, 여기 한 곳만 잡으면 플레이어든 몹이든 피해당 정확히 한 번만
 * 배율이 걸린다. ({@code Avatar} 는 {@code hurtServer} 를 재정의하지 않는다.)
 *
 * <p>HEAD 에서 인자를 갈아 끼우므로 방패({@code applyItemBlocking})·방어구·흡수·무적시간
 * 비교({@code lastHurt})가 모두 배율이 반영된 값을 본다. 공유 체력을 맞추는
 * {@code StatMirror} 는 다음 틱에 체력 변화량을 관측하는 방식이라, 이미 배율이 반영되고 난
 * 결과만 본다. 즉 배율이 두 번 곱해질 여지가 없다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPerkDamageMixin {
	/**
	 * 공유된 상태이상이 이미 다른 팀원에게 준 피해면 여기서 버린다.
	 *
	 * <p>{@code false} 를 돌려주면 바닐라 입장에서는 "피해가 들어가지 않았다"와 같다. 체력·흡수·
	 * 무적시간·피격 애니메이션 어느 것도 건드리지 않으므로 {@code StatMirror} 가 다음 틱에 관측할
	 * 델타도 0 이고, {@code DamageLedger} 에도 이 몫이 기록되지 않는다.
	 *
	 * <p>배율을 먹이는 {@link #sharedfate$applyPerkDamageMultipliers} 와 같은 HEAD 에 붙지만
	 * 순서는 상관없다. 버릴 피해면 배율을 곱한 값도 함께 버려지고, 실제로 피해를 받는 대표
	 * 한 명에게는 배율이 정확히 한 번 걸린다. 즉 증강의 {@code damage_taken} 배율은 예전처럼
	 * 팀원 수만큼이 아니라 1인분에만 곱해진다.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void sharedfate$skipDuplicateSharedEffectDamage(ServerLevel level, DamageSource source,
			float amount, CallbackInfoReturnable<Boolean> callback) {
		if (SharedEffectDamage.isDuplicateEffectDamage((LivingEntity) (Object) this)) {
			callback.setReturnValue(false);
		}
	}

	/**
	 * 인자 셋 중 {@code float amount}(로컬 3번)만 바꾼다. {@code argsOnly} 로 지역변수는 건드리지
	 * 않고, 앞쪽 인자 둘은 캡처해 피해원을 넘겨받는다.
	 */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, index = 3)
	private float sharedfate$applyPerkDamageMultipliers(float amount, ServerLevel level, DamageSource source) {
		return PerkDamage.scale((LivingEntity) (Object) this, source, amount);
	}
}
