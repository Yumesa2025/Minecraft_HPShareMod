package com.sharedfate.mixin;

import com.sharedfate.perk.PerkFoodRules;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 자연 회복이 스스로 치르는 허기 소모도에 표시를 달아 준다.
 *
 * <p>{@code no_hunger_drain}(고행자)과 {@code hunger_drain} 의 배율은
 * <b>플레이어가 행동해서 생긴 소모도</b>에만 걸려야 한다. 자연 회복은 체력을 돌려주는 대가로
 * 그 자리에서 소모도를 치르므로, 그 대가까지 0 이 되면 체력이 공짜로 무한히 차오른다.
 * 그래서 이 경로의 소모도는 배율을 타지 않는다는 표시를 달고 지나간다. 판정과 그 까닭은
 * {@link PerkFoodRules#addNaturalRegenExhaustion} 에 적어 뒀다.
 *
 * <h2>26.2 의 실제 갈래</h2>
 * <p>javap 로 확인한 {@code FoodData.tick} 의 자연 회복 두 갈래는 이렇다.
 *
 * <ul>
 *   <li>포만감이 남아 있고 허기가 20 이면 10틱마다
 *       {@code player.heal(f/6); this.addExhaustion(f)} — {@code f = min(포만감, 6)}</li>
 *   <li>그렇지 않고 허기가 18 이상이면 80틱마다
 *       {@code player.heal(1); this.addExhaustion(6.0F)}</li>
 * </ul>
 *
 * <p>둘 다 {@code player.causeFoodExhaustion(..)} 이 아니라 <b>{@code FoodData} 자신의</b>
 * {@code addExhaustion} 을 부른다. 그래서 {@code tick} 안의 {@code addExhaustion} 호출은
 * "자연 회복이 치르는 대가" 와 정확히 일치한다. 굶어 죽는 셋째 갈래는 소모도를 쌓지 않으므로
 * 이 우회를 지나지 않는다.
 *
 * <h2>다른 두 mixin 과 어떻게 어울리는가</h2>
 * <ul>
 *   <li>{@link FoodDataNaturalRegenMixin} 도 {@code FoodData.tick} 에 걸리지만, 그쪽이 잡는 것은
 *       {@code ServerPlayer.isHurt} 호출이다. 호출 지점이 겹치지 않아 서로를 덮지 않는다.
 *       {@code no_natural_regen}(흡혈귀)이 걸린 팀에서는 그쪽이 회복 갈래에 아예 들어가지 못하게
 *       막으므로 여기 우회도 자연히 불리지 않는다. 회복도 대가도 함께 사라지는 것이 맞다.</li>
 *   <li>{@link PlayerSharedExhaustionMixin} 은 아예 다른 메서드
 *       ({@code Player.causeFoodExhaustion})에 걸린다. 26.2 에서 이 두 경로는 만나지 않고,
 *       설령 만나더라도 여기서 달아 둔 표시가 그쪽 배율을 건너뛰게 한다.</li>
 * </ul>
 */
@Mixin(FoodData.class)
public abstract class FoodDataRegenExhaustionMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"))
	private void sharedfate$payNaturalRegenExhaustion(FoodData foodData, float exhaustion) {
		PerkFoodRules.addNaturalRegenExhaustion(foodData, exhaustion);
	}
}
