package com.sharedfate.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ExpandedInventorySlot extends Slot {
	private final ExpandedInventoryContainer extra;

	public ExpandedInventorySlot(ExpandedInventoryContainer extra, int slot, int x, int y) {
		super(extra, slot, x, y);
		this.extra = extra;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return extra.active();
	}

	@Override
	public boolean mayPickup(Player player) {
		return extra.active();
	}

	@Override
	public boolean isActive() {
		return extra.active();
	}
}
