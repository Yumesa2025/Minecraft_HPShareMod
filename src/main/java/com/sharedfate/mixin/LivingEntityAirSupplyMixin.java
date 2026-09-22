package com.sharedfate.mixin;

import com.sharedfate.perk.effect.NoAirLossEffect;
import com.sharedfate.team.TeamLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 물속에서 산소가 닳지 않게 한다. {@code no_air_loss} 증강(「아가미」)의 실제 지점이다.
 *
 * <h2>바닐라가 산소를 깎는 자리</h2>
 * <p>26.3 의 {@code LivingEntity.baseTick} 은 눈이 물에 잠겨 있고 거품 기둥이 아닐 때
 * 이 한 줄로 산소를 줄인다(javap 로 확인했다).
 *
 * <pre>{@code this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));}</pre>
 *
 * <p>그리고 {@code decreaseAirSupply(int air)} 자신은 이렇게 생겼다.
 *
 * <pre>{@code
 * double bonus = 숨 참기(OXYGEN_BONUS) 속성 값;
 * if (bonus > 0.0 && this.random.nextDouble() >= 1.0 / (bonus + 1.0)) {
 *     return air;      // 이번 틱은 안 깎는다
 * }
 * return air - 1;
 * }</pre>
 *
 * <p>즉 <b>받은 값을 그대로 돌려주는 것</b>이 곧 "이번 틱은 산소를 깎지 않는다"이고, 이건
 * 바닐라가 이미 쓰고 있는 갈래다. 그래서 여기서 할 일은 머리에서 들어온 값을 되돌려
 * 주는 것뿐이다.
 *
 * <h2>왜 여기인가</h2>
 * <p>「매 틱 산소를 최대로 채운다」로도 막대는 안 줄지만, 그건 막대가 한 칸 줄었다 다시
 * 차오르는 모양이 되어 화면이 깜빡이고 익사 판정·거품 기둥·물 밖 회복 갈래와 매 틱
 * 부딪힌다. 값을 가로채면 <b>호출자가 {@code setAirSupply} 에 넣는 값 자체</b>가 원래
 * 값이라 바닐라 흐름이 조금도 흐트러지지 않는다.
 *
 * <p>물 밖으로 나왔을 때의 회복은 {@code increaseAirSupply} 쪽이라 건드리지 않는다.
 * {@code shouldTakeDrowningDamage} 도 그대로라, 어떤 이유로 산소가 0 까지 떨어진
 * 상황이라면 익사 피해는 여전히 들어온다.
 *
 * <h2>재정의 함정</h2>
 * <p>{@code decreaseAirSupply(I)I} 는 {@code LivingEntity} 에만 있고
 * {@code Player} 와 {@code ServerPlayer} 는 재정의하지 않는다(javap 로 확인했다). 재정의가
 * 생기면 이 믹스인은 <b>조용히 아무 일도 하지 않게 되므로</b>
 * {@code AirSupplyTargetTest} 가 그 자리를 못박아 둔다.
 *
 * <h2>바닐라와 같아지는 길</h2>
 * <p>서버 플레이어가 아니면 곧바로 돌아간다. 몹·클라이언트 쪽 개체는 아예 판정에 들어오지
 * 않는다. {@link NoAirLossEffect#heldBy} 도 팀 미소속·증강 미사용을 두 번 만에 걸러 내므로,
 * 증강을 쓰지 않는 서버에서는 원래 {@code decreaseAirSupply} 가 그대로 돈다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAirSupplyMixin {
	@Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
	private void sharedfate$keepAirSupply(int air, CallbackInfoReturnable<Integer> callback) {
		if (!((Object) this instanceof ServerPlayer player)) {
			return;
		}
		if (NoAirLossEffect.heldBy(TeamLookup.stateOf(player.getUUID()))) {
			// 줄이려던 값을 그대로 돌려준다 = 이번 틱은 한 칸도 안 깎인다.
			callback.setReturnValue(air);
		}
	}
}
