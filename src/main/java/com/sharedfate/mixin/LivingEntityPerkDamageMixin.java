package com.sharedfate.mixin;

import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.PerkDamage;
import com.sharedfate.sync.DifficultyEscalation;
import com.sharedfate.sync.GameStartManager;
import com.sharedfate.sync.SharedEffectDamage;
import com.sharedfate.sync.SpreadDamageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 서버측 피해 진입점에 걸린 SharedFate 의 처리들.
 *
 * <p>하나는 증강의 피해 배율을 실제 피해량에 꽂아 넣는 것이고, 다른 하나는 공유된
 * 상태이상이 팀 전원에게 똑같이 주는 중복 피해를 버리는 것이다. 둘 다 같은
 * {@code hurtServer} 진입점을 보므로 한 mixin 에 모아 둔다.
 *
 * <p>여기에 「완충」({@code spread_damage})의 두 지점이 함께 붙어 있다. 피해를 미뤄 두는 곳은
 * 배율을 먹이는 자리 바로 뒤이고, 나뉘어 들어오는 동안 회복을 막는 곳은 {@code heal} 진입점이다.
 * 회복 쪽은 피해와 상관없어 보이지만, <b>미뤄 둔 몫과 회복 금지는 한 몸</b>이라 갈라 두면 한쪽만
 * 고쳐지는 사고가 난다. 자세한 까닭은 {@link SpreadDamageManager} 머리말에 있다.
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
 * <p>피해가 아닌 것이 하나 섞여 있다. 「몽둥이찜질」({@code weapon_knockback})의 넉백을 갈아
 * 끼우는 {@link #sharedfate$applyPerkWeaponKnockback} 다. 26.2 에서 근접 공격의 추가 넉백이
 * 정해지는 {@code LivingEntity.getKnockback} 이 마침 이 mixin 이 이미 잡고 있는 클래스에 있어,
 * 새 mixin 을 만들고 등록하는 대신 여기에 붙였다.
 *
 * <p>HEAD 에서 인자를 갈아 끼우므로 방패({@code applyItemBlocking})·방어구·흡수·무적시간
 * 비교({@code lastHurt})가 모두 배율이 반영된 값을 본다. 공유 체력을 맞추는
 * {@code StatMirror} 는 다음 틱에 체력 변화량을 관측하는 방식이라, 이미 배율이 반영되고 난
 * 결과만 본다. 즉 배율이 두 번 곱해질 여지가 없다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPerkDamageMixin {

	/**
	 * 직전에 받은 피해량. 무적시간 안에 들어온 공격이 <b>실제로 얼마나 아픈지</b>를 재는 데
	 * 쓴다.
	 *
	 * <p>26.2 의 {@code hurtServer} 는 {@code invulnerableTime > 10} 이고
	 * {@code BYPASSES_COOLDOWN} 이 아니면 <b>{@code amount - lastHurt} 만</b> 실제 피해로 치고,
	 * 그 값이 0 이하이면 통째로 버린다(바이트코드 206~216행). 「호위」가 그 버려질 한 대를
	 * 막았다고 치고 쿨타임을 쓰면 정작 아픈 대를 못 막는다.
	 */
	@Shadow
	protected float lastHurt;
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
	 *   <li><b>방패를 든 채 착지</b> — {@code shield_fall_immunity} 를 가진 팀원이 방패로 막는
	 *       중에 받는 낙하 피해는 통째로 버린다. 배율을 0 으로 깎지 않고 여기서 버리는 이유는
	 *       {@link com.sharedfate.perk.effect.ShieldFallImmunityEffect} 에 있다. 낙하가 아닌
	 *       피해는 {@link PerkDamage#blocksFallDamage} 첫 줄에서 곧바로 빠져나간다.</li>
	 *   <li><b>공유 상태이상의 중복 피해</b> — 아래 설명 참고.</li>
	 *   <li><b>몹에게 받은 한 대</b> — {@code damage_ward} 를 고른 사람은 쿨타임마다 한 번, 몹이
	 *       준 피해를 통째로 버린다. 낙하 면역과 같은 이유로 배율 0 이 아니라 여기서 버린다.
	 *       {@link com.sharedfate.perk.effect.DamageWardEffect} 참고. 이 검사만은 <b>맨 마지막</b>
	 *       이어야 한다 — 참이 되는 순간 쿨타임이 시작되므로, 앞의 규칙들이 이미 버릴 피해에
	 *       한 번을 낭비하면 안 된다.</li>
	 * </ol>
	 *
	 * <p>{@code false} 를 돌려주면 바닐라 입장에서는 "피해가 들어가지 않았다"와 같다. 체력·흡수·
	 * 무적시간·피격 애니메이션 어느 것도 건드리지 않으므로 {@code StatMirror} 가 다음 틱에 관측할
	 * 델타도 0 이고, {@code DamageLedger} 에도 이 몫이 기록되지 않는다.
	 *
	 * <p>배율을 먹이는 {@link #sharedfate$applyPerkDamageMultipliers} 와 같은 HEAD 에 붙지만
	 * 순서는 상관없다. 버릴 피해면 배율을 곱한 값도 함께 버려지고, 실제로 피해를 받는 대표
	 * 한 명에게는 배율이 정확히 한 번 걸린다. 즉 증강의 {@code damage_taken} 배율은
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
		if (PerkDamage.blocksFallDamage(self, source)) {
			callback.setReturnValue(false);
			return;
		}
		if (SharedEffectDamage.isDuplicateEffectDamage(self)) {
			callback.setReturnValue(false);
			return;
		}
		// 「호위」는 맨 마지막이다. 참을 돌려주는 순간 그 사람의 쿨타임이 시작되므로, 앞에서 이미
		// 버려질 피해에 한 번을 낭비하지 않으려면 다른 모든 검사 뒤여야 한다.
		//
		// 그리고 바닐라가 무적시간 안에서 버릴 몫도 미리 덜어 낸다. 좀비 셋에게 동시에 맞으면
		// 실제로 아픈 것은 첫 대뿐인데, 그 판정 없이는 「호위」가 어차피 0 이 될 두 번째 대에
		// 소모될 수 있다.
		if (effectiveAmount(self, source, amount) > 0.0F
				&& PerkDamage.blocksMobDamage(level, self, source, amount)) {
			callback.setReturnValue(false);
		}
	}

	/**
	 * 인자 셋 중 {@code float amount}(로컬 3번)만 바꾼다. {@code argsOnly} 로 지역변수는 건드리지
	 * 않고, 앞쪽 인자 둘은 캡처해 피해원을 넘겨받는다.
	 *
	 * <p>증강 배율을 먹인 값에 이어서 「난이도 상승」배율을 곱한다. 같은 자리에 두 mixin 이
	 * 붙으면 어느 쪽이 먼저 도는지가 우선순위에 달려 눈에 안 보인다. 둘 다 곱셈이라 순서는
	 * 어차피 결과를 바꾸지 않는다.
	 *
	 * <p>마지막으로 「완충」이 이 값을 통째로 미뤄 갈 수 있다. 미뤄 가면 이번 피해량은 0 이 되고,
	 * 같은 값이 몇 초에 걸쳐 나뉘어 다시 이 진입점으로 들어온다. <b>배율을 다 먹인 뒤에</b>
	 * 미루는 것이 중요하다. 그래야 다시 넣을 때 배율을 한 번 더 곱하지 않는다.
	 */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, index = 3)
	private float sharedfate$applyPerkDamageMultipliers(float amount, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		// 미뤄 두었던 몫이 다시 들어오는 중이면 손대지 않는다. 배율은 미룰 때 이미 걸었고, 여기서
		// 또 미루면 같은 피해가 영원히 나뉘기만 하고 끝나지 않는다.
		if (SpreadDamageManager.isDeliveringSlice()) {
			return amount;
		}
		float scaled = PerkDamage.scale(self, source, amount);
		float escalated = DifficultyEscalation.scaleDamage(source, scaled);
		return SpreadDamageManager.intercept(self, source, escalated);
	}

	/**
	 * 피해를 나누어 받는 동안에는 회복되지 않는다. 「완충」이 치르는 대가다.
	 *
	 * <p>26.2 의 회복 진입점은 {@code LivingEntity.heal(float)} 하나라, 자연 회복도 재생
	 * 상태이상도 금사과도 모두 여기를 지난다. 그래서 한 지점만 막으면 대가가 성립한다.
	 *
	 * <p>취소해도 {@code heal} 은 {@code void} 라 호출자에게 아무 신호도 가지 않는다.
	 * 재생 상태이상은 아이콘도 입자도 그대로 남고 회복량만 사라진다.
	 *
	 * <p>{@link SpreadDamageManager#blocksHealing} 은 미뤄 둔 몫이 하나도 없으면 첫 줄에서 곧바로
	 * 거짓이므로, 평소 회복 경로에는 사실상 아무 부담도 얹히지 않는다.
	 */
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void sharedfate$blockHealingWhileSpreading(float amount, CallbackInfo callback) {
		if (SpreadDamageManager.blocksHealing((LivingEntity) (Object) this)) {
			callback.cancel();
		}
	}

	/**
	 * 「몽둥이찜질」({@code weapon_knockback})의 넉백을 이 자리에서 갈아 끼운다.
	 *
	 * <p>26.2 에서 근접 공격의 추가 넉백이 정해지는 자리는
	 * {@code LivingEntity.getKnockback(Entity, DamageSource)} 하나다. 바이트코드로 보면
	 * {@code Player.attack} 이 {@code causeExtraKnockback(대상, getKnockback(대상, 피해원) + 질주보정, …)}
	 * 로 넘기고, {@code getKnockback} 자신은
	 * {@code EnchantmentHelper.modifyKnockback(…, 속성값) / 2} 를 돌려준다. <b>여기서 {@code this}
	 * 는 때리는 쪽이고 첫 인자가 맞는 쪽이다</b> — 그래서 이 한 자리에서 「누가 때리는가」와
	 * 「누구를 때리는가」를 모두 알 수 있고, 플레이어를 뺀다는 약속을 지킬 수 있다.
	 *
	 * <p>바닐라 속성 {@code minecraft:attack_knockback} 으로는 안 된다. 26.2 의 등록값이
	 * {@code RangedAttribute(0, 0, 5)} 라 5 에서 잘리고, 속성은 때리는 사람에게 붙는 값이라 맞는
	 * 쪽이 플레이어인지 알 수 없기 때문이다. 자세한 근거는
	 * {@link com.sharedfate.perk.effect.WeaponKnockbackEffect} 머리말에 있다.
	 *
	 * <p>{@code LivingEntity} 는 이 mixin 이 이미 잡고 있는 클래스라 <b>새 mixin 도 등록도
	 * 필요 없다.</b>
	 *
	 * <p>{@code RETURN} 에 붙어 원래 값을 읽고, <b>더 큰 경우에만</b> 갈아 끼운다. 넉백 인챈트가
	 * 이미 더 세게 붙어 있으면 그쪽이 그대로 이기므로 이 증강이 무기를 약하게 만드는 일은 없다.
	 * 걸릴 것이 없으면 {@link com.sharedfate.perk.PerkDamage#weaponKnockback} 이 음수를 돌려주고,
	 * 넉백은 언제나 0 이상이라 그 값이 원래 값을 이기는 일도 없다.
	 */
	@Inject(method = "getKnockback", at = @At("RETURN"), cancellable = true)
	private void sharedfate$applyPerkWeaponKnockback(Entity target, DamageSource source,
			CallbackInfoReturnable<Float> callback) {
		float perk = PerkDamage.weaponKnockback((LivingEntity) (Object) this, target);
		if (perk > callback.getReturnValueF()) {
			callback.setReturnValue(perk);
		}
	}

	/**
	 * 이번 공격이 <b>실제로</b> 줄 피해. 무적시간에 걸려 버려질 몫을 덜어 낸 값이다.
	 *
	 * <p>26.2 {@code LivingEntity.hurtServer} 의 판정을 그대로 옮긴 것이라, 마인크래프트가 그
	 * 규칙을 바꾸면 여기도 함께 바뀌어야 한다.
	 */
	private float effectiveAmount(LivingEntity self, DamageSource source, float amount) {
		if (self.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
			return amount - this.lastHurt;
		}
		return amount;
	}

}
