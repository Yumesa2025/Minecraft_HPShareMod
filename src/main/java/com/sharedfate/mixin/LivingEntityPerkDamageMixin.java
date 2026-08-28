package com.sharedfate.mixin;

import com.sharedfate.perk.PerkDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 증강의 피해 배율을 실제 피해량에 꽂아 넣는 자리.
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
	 * 인자 셋 중 {@code float amount}(로컬 3번)만 바꾼다. {@code argsOnly} 로 지역변수는 건드리지
	 * 않고, 앞쪽 인자 둘은 캡처해 피해원을 넘겨받는다.
	 */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, index = 3)
	private float sharedfate$applyPerkDamageMultipliers(float amount, ServerLevel level, DamageSource source) {
		return PerkDamage.scale((LivingEntity) (Object) this, source, amount);
	}
}
