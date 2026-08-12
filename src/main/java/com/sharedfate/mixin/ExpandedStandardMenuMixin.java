package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventorySlot;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class ExpandedStandardMenuMixin {
	@Shadow
	@Final
	public net.minecraft.core.NonNullList<Slot> slots;

	@Shadow
	protected abstract Slot addSlot(Slot slot);

	@Shadow
	protected abstract boolean moveItemStackTo(ItemStack stack, int start, int end, boolean reverse);

	@Unique
	private int sharedfate$standardInventoryStart = -1;
	@Unique
	private int sharedfate$extraStart = -1;
	@Unique
	private boolean sharedfate$movingToExtra;

	@Inject(method = "addStandardInventorySlots", at = @At("HEAD"))
	private void sharedfate$rememberStandardInventoryStart(
			Container container, int x, int y, CallbackInfo ci) {
		if (ExpandedInventoryManager.enabled()
				&& container instanceof Inventory
				&& !sharedfate$isSpecialPlayerMenu()) {
			sharedfate$standardInventoryStart = slots.size();
		}
	}

	@Inject(method = "addStandardInventorySlots", at = @At("RETURN"))
	private void sharedfate$appendExtraInventory(
			Container container, int x, int y, CallbackInfo ci) {
		if (!ExpandedInventoryManager.enabled()
				|| !(container instanceof Inventory inventory)
				|| sharedfate$isSpecialPlayerMenu()
				|| sharedfate$extraStart >= 0) {
			return;
		}
		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(inventory.player);
		sharedfate$extraStart = slots.size();
		int panelY = Math.max(0, y - 54);
		for (int row = 0; row < 9; row++) {
			for (int column = 0; column < 3; column++) {
				int slot = column + row * 3;
				addSlot(new ExpandedInventorySlot(extra, slot,
						x + 176 + column * 18, panelY + row * 18));
			}
		}
	}

	@Inject(method = "moveItemStackTo", at = @At("RETURN"), cancellable = true)
	private void sharedfate$fallBackToExtraInventory(
			ItemStack stack, int start, int end, boolean reverse,
			CallbackInfoReturnable<Boolean> cir) {
		if (sharedfate$movingToExtra || stack.isEmpty()
				|| sharedfate$standardInventoryStart < 0 || sharedfate$extraStart < 0) {
			return;
		}
		int standardEnd = sharedfate$standardInventoryStart + Inventory.INVENTORY_SIZE;
		if (start > sharedfate$standardInventoryStart || end < standardEnd
				|| end > sharedfate$extraStart) {
			return;
		}
		sharedfate$movingToExtra = true;
		try {
			boolean moved = moveItemStackTo(
					stack, sharedfate$extraStart,
					sharedfate$extraStart + ExpandedInventoryManager.EXTRA_SIZE, reverse);
			cir.setReturnValue(cir.getReturnValue() || moved);
		} finally {
			sharedfate$movingToExtra = false;
		}
	}

	@Unique
	private boolean sharedfate$isSpecialPlayerMenu() {
		return (Object) this instanceof InventoryMenu || (Object) this instanceof CraftingMenu;
	}
}
