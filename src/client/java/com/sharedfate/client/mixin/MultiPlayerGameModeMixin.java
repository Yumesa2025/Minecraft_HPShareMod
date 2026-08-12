package com.sharedfate.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.client.ClientTeamState;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@WrapOperation(
			method = "sameDestroyTarget",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
			)
	)
	private boolean sharedfate$ignoreSharedDurabilityChanges(
			ItemStack current, ItemStack destroying, Operation<Boolean> original) {
		if (ExpandedInventoryManager.enabled() && ClientTeamState.inTeam()) {
			return ItemStack.isSameItem(current, destroying);
		}
		return original.call(current, destroying);
	}
}
