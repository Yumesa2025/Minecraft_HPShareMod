package com.sharedfate.mixin;

import com.sharedfate.perk.PerkFoodRules;
import com.sharedfate.sync.FoodOverflowBuffer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 먹기 처리에 두 가지로 끼어든다.
 *
 * <ol>
 *   <li>먹은 영양을 {@link FoodOverflowBuffer} 에 적어 팀의 남는 허기를 모아 둔다.</li>
 *   <li>{@code no_food_hunger} 증강을 가진 팀이면 영양 섭취 자체를 건너뛴다.</li>
 * </ol>
 *
 * <h2>왜 여기인가</h2>
 * <p>26.2 에서 음식이 실제로 배를 채우는 곳은 {@code FoodProperties.onConsume} 안의
 * {@code FoodData.eat(FoodProperties)} 호출 한 줄뿐이다. {@code FoodData} 쪽에 직접 걸지 않는
 * 이유는 {@code FoodData} 가 자기 주인을 모르기 때문이다. 여기서는 {@code entity} 가 곧 먹는
 * 사람이라 어느 팀인지 그대로 물어볼 수 있다.
 *
 * <p>그 한 줄만 건너뛰므로 먹기의 나머지는 전부 그대로다. 아이템은 줄어들고, 먹는 소리와 트림
 * 소리도 나고, 수상한 스튜의 상태이상이나 우유의 해독 같은 {@code consume_effects} 도 모두
 * 평소처럼 일어난다. 그것들은 {@code Consumable.onConsume} 의 다른 자리에서 처리된다.
 *
 * <p>영양 기록도 함께 막아야 한다. 안 그러면 먹은 만큼이 팀의 남는 허기로 쌓였다가 나중에
 * 배가 줄 때 되돌아와, 결국 음식으로 허기를 회복한 것과 같아진다.
 *
 * <p>증강 풀이 비어 있으면 {@link PerkFoodRules#blocksFoodHunger} 가 팀 상태만 보고 곧바로
 * 거짓이므로 먹기 동작이 증강 도입 전과 완전히 같다.
 */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void sharedfate$recordNutrition(Level level, LivingEntity entity, ItemStack stack,
			Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player
				&& !PerkFoodRules.blocksFoodHunger(player)) {
			FoodOverflowBuffer.recordConsumption(player, (FoodProperties) (Object) this);
		}
	}

	/**
	 * {@code no_food_hunger} 를 가진 팀원이면 영양 섭취를 건너뛴다.
	 *
	 * <p>클라이언트에서는 팀 상태를 볼 수 없어 판정이 언제나 거짓이라 예전처럼 먹기를 미리
	 * 그린다. 서버 쪽 허기는 바뀌지 않으므로 화면이 한 틱 동안 어긋나는데,
	 * {@link PerkFoodRules#resyncFoodDisplay} 가 다음 틱에 서버 값을 다시 보내게 만들어
	 * 곧바로 제자리를 찾는다.
	 */
	@Redirect(method = "onConsume",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void sharedfate$skipFoodHunger(FoodData foodData, FoodProperties properties,
			Level level, LivingEntity entity, ItemStack stack, Consumable consumable) {
		if (PerkFoodRules.blocksFoodHunger(entity)) {
			PerkFoodRules.resyncFoodDisplay(entity);
			return;
		}
		foodData.eat(properties);
	}
}
