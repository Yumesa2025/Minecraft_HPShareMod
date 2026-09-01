package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.inventory.ExpandedInventoryMoves;
import com.sharedfate.inventory.ExpandedInventorySlot;
import com.sharedfate.inventory.ExpandedMenuLayout;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 플레이어 인벤토리 화면에 추가 27칸을 붙입니다.
 *
 * <p>{@link ExpandedStandardMenuMixin} 이 이 메뉴만 빼는 이유는 오프핸드 칸 때문입니다 —
 * 바닐라는 인벤토리 36칸을 붙인 <b>뒤에</b> 오프핸드를 45번으로 붙이므로, 그 사이에
 * 끼워 넣으면 오프핸드 번호가 어긋납니다. 그래서 생성자 맨 끝에서 붙이고, 배치 정보만
 * 공통 통로에 알려 줍니다.
 */
@Mixin(InventoryMenu.class)
public abstract class ExpandedInventoryMenuMixin {
	/** 인벤토리 세 줄이 시작하는 메뉴 번호. 0~3 제작, 4 결과, 5~8 장비 다음입니다. */
	private static final int PLAYER_SLOT_START = 9;
	/** 바닐라 인벤토리 첫 줄의 화면 y. */
	private static final int INVENTORY_TOP_Y = 84;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void sharedfate$appendExpandedSlots(
			Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		if (!ExpandedInventoryManager.enabled()) {
			return;
		}

		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(owner);
		AbstractContainerMenuAccessor menu = (AbstractContainerMenuAccessor) this;
		for (int extraIndex = 0; extraIndex < ExpandedInventoryManager.EXTRA_SIZE; extraIndex++) {
			int column = extraIndex % ExpandedInventoryManager.EXTRA_COLUMNS;
			int row = extraIndex / ExpandedInventoryManager.EXTRA_COLUMNS;
			menu.sharedfate$invokeAddSlot(new ExpandedInventorySlot(extra, extraIndex,
					8 + column * ExpandedInventoryManager.SLOT_PITCH,
					INVENTORY_TOP_Y + ExpandedInventoryManager.EXTRA_TOP_OFFSET
							+ row * ExpandedInventoryManager.SLOT_PITCH));
		}
		((ExpandedMenuLayout) this).sharedfate$setExpandedLayout(
				PLAYER_SLOT_START,
				ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE,
				INVENTORY_TOP_Y);
		ExpandedInventoryManager.updateMenuLayout(
				(InventoryMenu) (Object) this, extra.active());
	}

	/** 추가 칸에서 쉬프트 클릭하면 바닐라 36칸으로 돌려보냅니다. */
	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void sharedfate$quickMoveFromExtra(
			Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (!ExpandedInventoryManager.enabled()
				|| index < ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE
				|| index >= ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE) {
			return;
		}
		InventoryMenu menu = (InventoryMenu) (Object) this;
		Slot slot = menu.getSlot(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int[] order = ExpandedInventoryMoves.order(
				PLAYER_SLOT_START, ExpandedInventoryManager.EXTRA_SIZE,
				PLAYER_SLOT_START + ExpandedInventoryManager.EXTRA_SIZE,
				ExpandedInventoryManager.EXTRA_COLUMNS);
		if (!ExpandedInventoryMoves.move(menu, stack, order, false)) {
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
