package com.sharedfate.client.mixin;

import com.sharedfate.client.ClientTeamState;
import com.sharedfate.mixin.SlotAccessor;
import com.sharedfate.inventory.CreativeInventoryLayout;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void sharedfate$includeExpandedInventoryInClickBounds(
			double mouseX, double mouseY, int left, int top,
			CallbackInfoReturnable<Boolean> cir) {
		CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
		if (!ExpandedInventoryManager.enabled()
				|| !ClientTeamState.inTeam() || !screen.isInventoryOpen()) {
			return;
		}
		for (Slot slot : screen.getMenu().slots) {
			if (!(slot.container instanceof ExpandedInventoryContainer) || !slot.isActive()) {
				continue;
			}
			double relativeX = mouseX - left;
			double relativeY = mouseY - top;
			if (relativeX >= slot.x - 1 && relativeX < slot.x + 17
					&& relativeY >= slot.y - 1 && relativeY < slot.y + 17) {
				cir.setReturnValue(false);
				return;
			}
		}
	}

	@Inject(method = "selectTab", at = @At("RETURN"))
	private void sharedfate$layoutExpandedInventoryTab(
			CreativeModeTab tab, CallbackInfo ci) {
		sharedfate$refreshExpandedInventoryTab();
	}

	@Inject(method = "containerTick", at = @At("HEAD"))
	private void sharedfate$refreshExpandedInventoryTab(CallbackInfo ci) {
		sharedfate$refreshExpandedInventoryTab();
	}

	@Unique
	private void sharedfate$refreshExpandedInventoryTab() {
		CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
		boolean expanded = ExpandedInventoryManager.enabled()
				&& ClientTeamState.inTeam() && screen.isInventoryOpen();
		if (!expanded) {
			accessor.sharedfate$setLeftPos(
					(((Screen) screen).width
							- CreativeInventoryLayout.VANILLA_SCREEN_WIDTH) / 2);
			return;
		}

		accessor.sharedfate$setLeftPos(CreativeInventoryLayout.expandedLeft(
				((Screen) screen).width));
		int extraIndex = 0;
		for (Slot slot : screen.getMenu().slots) {
			if (!(slot.container instanceof ExpandedInventoryContainer)) {
				continue;
			}
			SlotAccessor slotAccessor = (SlotAccessor) (Object) slot;
			slotAccessor.sharedfate$setX(
					CreativeInventoryLayout.extraSlotX(extraIndex));
			slotAccessor.sharedfate$setY(
					CreativeInventoryLayout.extraSlotY(extraIndex));
			extraIndex++;
		}
	}
}
