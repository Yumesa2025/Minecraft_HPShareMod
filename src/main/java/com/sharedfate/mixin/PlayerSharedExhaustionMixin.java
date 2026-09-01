package com.sharedfate.mixin;

import com.sharedfate.perk.PerkFoodRules;
import com.sharedfate.sync.SharedEffectDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 허기 소모도가 쌓이는 단 하나의 입구에 두 가지로 끼어든다.
 *
 * <ol>
 *   <li>공유된 상태이상이 이미 다른 팀원의 배를 곯린 몫이면 버린다.</li>
 *   <li>{@code hunger_drain} / {@code no_hunger_drain} 증강의 배율을 곱한다.</li>
 * </ol>
 *
 * <p>둘의 순서는 어느 쪽이 먼저 돌아도 결과가 같다. 버려질 소모도에 배율을 곱해 봐야 그 값은
 * 그대로 사라지고, 버려지지 않은 1인분에는 배율이 곱해지기 때문이다. 중요한 것은 <b>배율이
 * 중복 제거를 통과한 몫에만</b> 걸린다는 점이다. 그래서 팀 인원수만큼 배수가 되던 예전 버그가
 * 배율을 타고 되살아나지 않는다.
 *
 * <h2>공유된 상태이상의 중복 제거</h2>
 *
 * <p>26.2 의 {@code HungerMobEffect.applyEffectTick} 은
 * {@code player.causeFoodExhaustion(0.005F * (등급 + 1))} 한 번이 전부다.
 * {@code Player.causeFoodExhaustion} 은 {@code ServerPlayer} 가 재정의하지 않고, 그 안에서
 * {@code FoodData.addExhaustion} 을 부르는 유일한 통로라 여기 한 곳만 잡으면 된다.
 * ({@code FoodData.tick} 도 자기 {@code addExhaustion} 을 부르지만 그건 상태이상이 아니라
 * 자연 회복이 치르는 대가다. 아래에 따로 적었다.)
 *
 * <p>{@code FoodData} 쪽이 아니라 {@code Player} 쪽에 거는 이유는 {@code FoodData} 가 주인을
 * 모르기 때문이다. 여기서는 {@code this} 가 곧 그 플레이어라 "지금 상태이상 틱이 도는 대상과
 * 같은가"를 그대로 물어볼 수 있다.
 *
 * <p>달리기·점프·수영·채굴로 쌓이는 평소 허기 소모는 상태이상 틱 구간 밖이라 그대로 통과한다.
 * 팀원마다 따로 움직인 결과이므로 합산되는 게 맞다. 자연 회복의 대가도 마찬가지다. 사람마다
 * 따로 회복한 몫이라 사람마다 따로 치르는 것이 맞다.
 *
 * <h2>허기 소모 배율</h2>
 * <p>{@code Player.causeFoodExhaustion} 은 <b>플레이어의 행동</b>이 소모도를 쌓는 통로다.
 * 달리기·점프·수영·채굴·공격은 물론이고 허기 상태이상과 마법 부여 효과까지 전부 이 한 줄을
 * 지난다. 배가 줄어드는 순간이 아니라 여기에 곱해야 하는 이유는 소모도가 4.0 을 넘을 때에야
 * 배가 1 줄기 때문이다. 줄어든 결과를 보고 되짚으면 포만감이 먼저 닳는 구간을 통째로 놓친다.
 *
 * <p><b>자연 회복은 여기를 지나지 않는다.</b> 26.2 의 {@code FoodData.tick} 은 회복 갈래에서
 * {@code causeFoodExhaustion} 이 아니라 {@code FoodData} 자신의 {@code addExhaustion} 을 부른다
 * (javap 로 확인했다). 그래서 회복의 대가는 이 배율을 타지 않고 따로 치러진다 — 기본적으로는
 * 그대로, 고행자처럼 {@code includeNaturalRegen: true} 를 든 팀만 예외로 면제된다. 그 갈림을
 * 통로에 기대지 않고 못 박아 두는 것이 {@link FoodDataRegenExhaustionMixin} 이다.
 *
 * <p>배율이 1 이면 받은 값을 그대로 돌려주므로 증강이 없는 서버의 소모도는 조금도 달라지지
 * 않는다. 클라이언트 쪽 플레이어는 팀 상태를 볼 수 없어 언제나 배율이 1 이고, 애초에 바닐라가
 * 클라이언트에서는 소모도를 쌓지 않는다.
 */
@Mixin(Player.class)
public abstract class PlayerSharedExhaustionMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void sharedfate$skipDuplicateSharedEffectExhaustion(float exhaustion, CallbackInfo callback) {
		if (SharedEffectDamage.isDuplicateEffectExhaustion((LivingEntity) (Object) this)) {
			callback.cancel();
		}
	}

	/**
	 * {@code hunger_drain} / {@code no_hunger_drain} 증강의 배율을 소모도에 곱한다.
	 *
	 * <p>인자를 바꾸는 것뿐이라 위의 중복 제거는 손대지 않는다. 위가 먼저 돌아 취소되면 여기는
	 * 아예 불리지 않고, 여기가 먼저 돌아도 바꾼 값이 곧바로 버려질 뿐이다.
	 */
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float sharedfate$scaleExhaustion(float exhaustion) {
		return PerkFoodRules.scaleExhaustion((LivingEntity) (Object) this, exhaustion);
	}
}
