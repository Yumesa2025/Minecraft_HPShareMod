package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(InventoryChangeTrigger.TriggerInstance.class)
public abstract class InventoryChangeTriggerMixin {
	@Inject(method = "matches", at = @At("HEAD"), cancellable = true)
	private void sharedfate$matchExpandedInventory(
			Inventory inventory, ItemStack changedStack,
			int fullSlots, int emptySlots, int occupiedSlots,
			CallbackInfoReturnable<Boolean> cir) {
		if (!ExpandedInventoryManager.enabled()
				|| !(inventory.player instanceof ServerPlayer)
				|| !ExpandedInventoryManager.extraFor(inventory.player).active()) {
			return;
		}

		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(inventory.player);
		int expandedFull = fullSlots;
		int expandedEmpty = emptySlots;
		int expandedOccupied = occupiedSlots;
		for (ItemStack stack : extra.getItems()) {
			if (stack.isEmpty()) {
				expandedEmpty++;
			} else {
				expandedOccupied++;
				if (stack.getCount() >= stack.getMaxStackSize()) {
					expandedFull++;
				}
			}
		}

		InventoryChangeTrigger.TriggerInstance self =
				(InventoryChangeTrigger.TriggerInstance) (Object) this;
		if (!self.slots().matches(expandedFull, expandedEmpty, expandedOccupied)) {
			cir.setReturnValue(false);
			return;
		}
		List<ItemPredicate> remaining = new ArrayList<>(self.items());
		if (remaining.isEmpty()) {
			cir.setReturnValue(true);
			return;
		}
		for (int slot = 0; slot < inventory.getContainerSize() && !remaining.isEmpty(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				remaining.removeIf(predicate -> predicate.test(stack));
			}
		}
		for (ItemStack stack : extra.getItems()) {
			if (!stack.isEmpty() && !remaining.isEmpty()) {
				remaining.removeIf(predicate -> predicate.test(stack));
			}
		}
		cir.setReturnValue(remaining.isEmpty());
	}
}
