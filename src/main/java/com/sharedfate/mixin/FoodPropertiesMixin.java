package com.sharedfate.mixin;

import com.sharedfate.sync.FoodOverflowBuffer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void sharedfate$recordNutrition(Level level, LivingEntity entity, ItemStack stack,
			Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player) {
			FoodOverflowBuffer.recordConsumption(player, (FoodProperties) (Object) this);
		}
	}
}
