package com.sharedfate.inventory;

public final class CreativeInventoryLayout {
	public static final int VANILLA_SCREEN_WIDTH = 195;
	public static final int EXTRA_PANEL_WIDTH = 9 * 18;
	public static final int TOTAL_SCREEN_WIDTH = VANILLA_SCREEN_WIDTH + EXTRA_PANEL_WIDTH;
	public static final int EXTRA_SLOT_X = VANILLA_SCREEN_WIDTH + 1;
	public static final int EXTRA_SLOT_Y = 54;

	private CreativeInventoryLayout() {
	}

	public static int expandedLeft(int screenWidth) {
		return Math.max(0, (screenWidth - TOTAL_SCREEN_WIDTH) / 2);
	}

	public static int extraSlotX(int extraIndex) {
		return EXTRA_SLOT_X + Math.floorMod(extraIndex, 9) * 18;
	}

	public static int extraSlotY(int extraIndex) {
		return EXTRA_SLOT_Y + Math.floorDiv(extraIndex, 9) * 18;
	}

	public static int maximumServerSlot(boolean expandedActive) {
		return expandedActive
				? ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE - 1
				: ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE - 1;
	}
}
