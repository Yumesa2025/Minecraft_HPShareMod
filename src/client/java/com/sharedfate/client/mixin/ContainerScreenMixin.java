package com.sharedfate.client.mixin;

import com.sharedfate.client.ClientTeamState;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
	private static final int RED = 0xFFFF3030;
	/**
	 * 추가 슬롯 칸 색. 바닐라 인벤토리 칸과 같아야 한다.
	 *
	 * <p>바닐라 텍스처의 슬롯은 <b>테두리가 어둡고 안쪽이 밝은 회색</b>이다. 예전에는 이 둘이
	 * 뒤집혀 있어서 추가 칸만 유독 어둡게 보였고, 같은 공유 인벤토리인데 두 종류처럼 읽혔다.
	 */
	private static final int EXTRA_SLOT_BORDER = 0xFF373737;
	private static final int EXTRA_SLOT_INNER = 0xFF8B8B8B;

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void sharedfate$includeExtraPanelInClickBounds(
			double mouseX, double mouseY, int left, int top,
			CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
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

	@Inject(method = "init", at = @At("HEAD"))
	private void sharedfate$restoreExpandedInventoryAfterPlayerReplacement(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && ExpandedInventoryManager.enabled()) {
			ExpandedInventoryManager.setClientTeamActive(client.player, ClientTeamState.inTeam());
		}
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void sharedfate$drawExtraSlotBackground(GuiGraphicsExtractor graphics, Slot slot,
			int mouseX, int mouseY, CallbackInfo ci) {
		if (slot.container instanceof ExpandedInventoryContainer && slot.isActive()) {
			graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, EXTRA_SLOT_BORDER);
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, EXTRA_SLOT_INNER);
		}
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void sharedfate$markAllySlot(GuiGraphicsExtractor graphics, Slot slot,
			int mouseX, int mouseY, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (!ClientTeamState.inTeam() || client.player == null
				|| slot.container != client.player.getInventory()) {
			return;
		}
		int hotbarSlot = slot.index;
		if ((Object) this instanceof CreativeModeInventoryScreen
				&& hotbarSlot >= 36 && hotbarSlot < 45) {
			hotbarSlot -= 36;
		}
		if (hotbarSlot >= 0 && hotbarSlot < 9
				&& ClientTeamState.isAllyUsingHotbarSlot(hotbarSlot)) {
			graphics.outline(slot.x - 1, slot.y - 1, 18, 18, RED);
		}
	}
}
