package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceMenu.class)
public abstract class ExpandedFurnaceMenuMixin {
	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void sharedfate$quickMoveFromExtra(
			Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (!ExpandedInventoryManager.enabled()) {
			return;
		}
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		int extraStart = menu.slots.size() - ExpandedInventoryManager.EXTRA_SIZE;
		if (index < extraStart || index >= menu.slots.size()) {
			return;
		}
		Slot slot = menu.getSlot(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = ((AbstractContainerMenuInvoker) this)
				.sharedfate$invokeMoveItemStackTo(stack, 0, 3, false);
		if (!moved) {
			moved = ((AbstractContainerMenuInvoker) this)
					.sharedfate$invokeMoveItemStackTo(stack, extraStart - 36, extraStart, false);
		}
		if (!moved) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY, original);
		} else {
			slot.setChanged();
		}
		slot.onTake(player, stack);
		cir.setReturnValue(original);
	}
}
