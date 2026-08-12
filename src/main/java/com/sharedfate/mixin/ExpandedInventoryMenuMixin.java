package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.inventory.ExpandedInventorySlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class ExpandedInventoryMenuMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void sharedfate$appendExpandedSlots(
			Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		if (!ExpandedInventoryManager.enabled()) {
			return;
		}

		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(owner);
		AbstractContainerMenuAccessor menu = (AbstractContainerMenuAccessor) this;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				int slot = column + row * 9;
				menu.sharedfate$invokeAddSlot(
						new ExpandedInventorySlot(extra, slot, 8 + column * 18, 138 + row * 18));
			}
		}
		ExpandedInventoryManager.updateMenuLayout((InventoryMenu) (Object) this, extra.active());
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void sharedfate$quickMoveFromExtra(
			Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (!ExpandedInventoryManager.enabled()
				|| index < ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE
				|| index >= ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE) {
			return;
		}
		Slot slot = ((InventoryMenu) (Object) this).getSlot(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (!sharedfate$moveItemStackTo(stack, 9, 45, false)) {
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

	@WrapOperation(
			method = "quickMoveStack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/InventoryMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z"
			)
	)
	private boolean sharedfate$fallBackToExtra(
			InventoryMenu menu, ItemStack stack, int start, int end, boolean reverse,
			Operation<Boolean> original) {
		boolean moved = original.call(menu, stack, start, end, reverse);
		if (moved || !ExpandedInventoryManager.enabled() || start < 9 || end > 45) {
			return moved;
		}
		return sharedfate$moveItemStackTo(
				stack,
				ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE,
				ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE,
				false);
	}

	private boolean sharedfate$moveItemStackTo(
			ItemStack stack, int start, int end, boolean reverse) {
		return ((AbstractContainerMenuInvoker) this)
				.sharedfate$invokeMoveItemStackTo(stack, start, end, reverse);
	}
}
