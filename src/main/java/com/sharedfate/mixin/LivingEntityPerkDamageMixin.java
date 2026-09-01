package com.sharedfate.mixin;

import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.PerkDamage;
import com.sharedfate.sync.DifficultyEscalation;
import com.sharedfate.sync.GameStartManager;
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
 * <p>몹이 주는 피해를 깎는 {@code mob_damage} 증강도 여기를 지난다. 가해자를 보는 자리가
 * 이미 있으므로 별도의 mixin 을 두지 않고 {@link PerkDamage} 안에서 팀원의
 * {@code damage_dealt} 와 나란히 처리한다. 자세한 내용은
 * {@link com.sharedfate.perk.MobPerkModifiers} 참고.
 *
 * <p>HEAD 에서 인자를 갈아 끼우므로 방패({@code applyItemBlocking})·방어구·흡수·무적시간
 * 비교({@code lastHurt})가 모두 배율이 반영된 값을 본다. 공유 체력을 맞추는
 * {@code StatMirror} 는 다음 틱에 체력 변화량을 관측하는 방식이라, 이미 배율이 반영되고 난
 * 결과만 본다. 즉 배율이 두 번 곱해질 여지가 없다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPerkDamageMixin {
	/**
	 * 버려야 할 피해를 여기서 전부 걸러낸다.
	 *
	 * <p>두 가지를 본다.
	 *
	 * <ol>
	 *   <li><b>강제 증강 선택 중의 무적</b> — 시간이 멈춰 있어도 용암·낙하·불·익사는 플레이어 자기
	 *       틱에서 계산돼 그대로 들어온다. 선택창이 떠 있는 동안에는 팀원의 피해를 통째로 버린다.
	 *       무적은 {@link com.sharedfate.perk.PerkChoiceSession} 이 시간을 녹이는 순간 함께 풀린다.
	 *       세션이 없으면 첫 줄에서 곧바로 빠져나가므로 평소 피해 처리에는 비용이 없다.</li>
	 *   <li><b>회차 시작 전의 무적</b> — 리더가 「게임 시작」을 누르기 전에는 팀원의 피해를 통째로
	 *       버린다. 체력이 공유라 한 명만 죽어도 팀이 전멸하는데, 아직 시작도 안 한 회차 때문에
	 *       월드가 지워지는 일도 하드코어에서 관전자로 갇히는 일도 없어야 한다. 자세한 까닭은
	 *       {@link com.sharedfate.sync.GameStartManager#blocksDamage} 에 있다.</li>
	 *   <li><b>공유 상태이상의 중복 피해</b> — 아래 설명 참고.</li>
	 * </ol>
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
		LivingEntity self = (LivingEntity) (Object) this;
		if (PerkChoiceSession.blocksDamage(self) || GameStartManager.blocksDamage(self)) {
			callback.setReturnValue(false);
			return;
		}
		if (SharedEffectDamage.isDuplicateEffectDamage(self)) {
			callback.setReturnValue(false);
		}
	}

	/**
	 * 인자 셋 중 {@code float amount}(로컬 3번)만 바꾼다. {@code argsOnly} 로 지역변수는 건드리지
	 * 않고, 앞쪽 인자 둘은 캡처해 피해원을 넘겨받는다.
	 *
	 * <p>증강 배율을 먹인 값에 이어서 「난이도 상승」배율을 곱한다. 새 mixin 을 하나 더 두는
	 * 대신 여기서 이어 부르는 이유는 둘이다 — 같은 자리에 두 mixin 이 붙으면 어느 쪽이 먼저
	 * 도는지가 우선순위에 달려 눈에 안 보이고, 이미 동작이 확인된 진입점을 그대로 쓰는 편이
	 * 안전하다. 둘 다 곱셈이라 순서는 어차피 결과를 바꾸지 않는다.
	 */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, index = 3)
	private float sharedfate$applyPerkDamageMultipliers(float amount, ServerLevel level, DamageSource source) {
		float scaled = PerkDamage.scale((LivingEntity) (Object) this, source, amount);
		return DifficultyEscalation.scaleDamage(source, scaled);
	}
}
