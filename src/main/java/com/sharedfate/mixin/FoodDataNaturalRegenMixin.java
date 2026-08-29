package com.sharedfate.mixin;

import com.sharedfate.perk.PerkRegenRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 허기에 의한 자연 회복을 막는다. {@code no_natural_regen} 증강의 실제 지점이다.
 *
 * <p>26.2 의 자연 회복은 {@code FoodData.tick(ServerPlayer)} 안에 두 갈래로 들어 있다.
 *
 * <ul>
 *   <li>포만감이 남아 있고 허기가 20 이면 10틱마다 {@code player.heal(saturation/6)}</li>
 *   <li>그렇지 않고 허기가 18 이상이면 80틱마다 {@code player.heal(1)}</li>
 * </ul>
 *
 * <p>두 갈래 모두 조건에 {@code player.isHurt()} 가 들어 있다. 그래서 이 메서드 안에서만
 * {@code isHurt} 가 거짓을 돌려주게 하면 두 갈래가 통째로 건너뛰어진다.
 *
 * <h2>왜 {@code heal} 이 아니라 {@code isHurt} 를 가로채는가</h2>
 * <p>{@code heal} 만 막으면 바로 뒤의 {@code addExhaustion(...)} 은 그대로 돌아 회복은 없는데
 * 배만 고파진다. {@code tickTimer} 도 계속 오른다. {@code isHurt} 를 거짓으로 만들면 회복
 * 갈래에 아예 들어가지 않으므로 소모도도 타이머도 움직이지 않는다.
 *
 * <h2>굶주림은 그대로다</h2>
 * <p>세 번째 갈래인 굶어 죽기는 {@code foodLevel <= 0} 만 보고 {@code isHurt} 를 보지 않는다.
 * 허기가 0 이 되면 여전히 굶어 죽고, 난이도별 최소 체력 규칙도 그대로다.
 *
 * <p>{@code LivingEntity.heal} 쪽에 걸지 않은 이유도 있다. 그쪽을 막으면 재생 상태이상·금사과·
 * 평화 난이도 회복까지 함께 막히거나, 막지 않으려면 "지금 자연 회복 중인가"라는 표시를 따로
 * 들고 다녀야 한다. 여기서 막으면 그런 표시가 필요 없다.
 *
 * <p>{@link PerkRegenRules} 는 팀 미소속·증강 미사용을 먼저 걸러 내므로, 증강을 쓰지 않는
 * 서버에서는 이 우회가 원래 {@code isHurt} 를 그대로 돌려준다. 즉 바닐라와 완전히 같다.
 */
@Mixin(FoodData.class)
public abstract class FoodDataNaturalRegenMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"))
	private boolean sharedfate$blockNaturalRegen(ServerPlayer player) {
		return player.isHurt() && !PerkRegenRules.blocksNaturalRegen(player);
	}
}
