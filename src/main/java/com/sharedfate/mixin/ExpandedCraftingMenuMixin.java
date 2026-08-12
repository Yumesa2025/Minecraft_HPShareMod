package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventorySlot;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingMenu.class)
public abstract class ExpandedCraftingMenuMixin {
	private static final int CRAFT_INPUT_START = 1;
	private static final int CRAFT_INPUT_END = 10;
	private static final int PLAYER_INVENTORY_START = 10;
	private static final int VANILLA_MENU_END = 46;

	@Inject(
			method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL")
	)
	private void sharedfate$appendExtraSlots(
			int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		if (!ExpandedInventoryManager.enabled()) {
			return;
		}

		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(inventory.player);
		AbstractContainerMenuAccessor menu = (AbstractContainerMenuAccessor) this;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				int slot = column + row * 9;
				menu.sharedfate$invokeAddSlot(
						new ExpandedInventorySlot(extra, slot, 8 + column * 18, 138 + row * 18));
			}
		}
		ExpandedInventoryManager.updateCraftingMenuLayout(
				(CraftingMenu) (Object) this, extra.active());
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void sharedfate$quickMoveFromExtra(
			Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (!ExpandedInventoryManager.enabled()
				|| index < VANILLA_MENU_END
				|| index >= ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE) {
			return;
		}

		CraftingMenu menu = (CraftingMenu) (Object) this;
		Slot slot = menu.getSlot(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = sharedfate$moveItemStackTo(
				stack, CRAFT_INPUT_START, CRAFT_INPUT_END, false);
		if (!moved) {
			moved = sharedfate$moveItemStackTo(
					stack, PLAYER_INVENTORY_START, VANILLA_MENU_END, false);
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
		if (stack.getCount() == original.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		slot.onTake(player, stack);
		cir.setReturnValue(original);
	}

	@WrapOperation(
			method = "quickMoveStack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/CraftingMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z"
			)
	)
	private boolean sharedfate$fallBackToExtra(
			CraftingMenu menu, ItemStack stack, int start, int end, boolean reverse,
			Operation<Boolean> original) {
		boolean moved = original.call(menu, stack, start, end, reverse);
		if (!ExpandedInventoryManager.enabled()
				|| start < PLAYER_INVENTORY_START
				|| end > VANILLA_MENU_END
				|| stack.isEmpty()) {
			return moved;
		}
		boolean movedToExtra = sharedfate$moveItemStackTo(
				stack,
				VANILLA_MENU_END,
				ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE,
				reverse);
		return moved || movedToExtra;
	}

	private boolean sharedfate$moveItemStackTo(
			ItemStack stack, int start, int end, boolean reverse) {
		return ((AbstractContainerMenuInvoker) this)
				.sharedfate$invokeMoveItemStackTo(stack, start, end, reverse);
	}
}
