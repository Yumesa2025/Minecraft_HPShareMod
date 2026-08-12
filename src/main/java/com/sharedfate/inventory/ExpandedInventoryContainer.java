package com.sharedfate.inventory;

import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.SharedItemList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

public final class ExpandedInventoryContainer implements Container, StackedContentsCompatible {
	private final Player player;
	private SharedItemList local = SharedItemList.ofSize(ExpandedInventoryManager.EXTRA_SIZE);
	private boolean clientActive;

	ExpandedInventoryContainer(Player player) {
		this.player = player;
	}

	private SharedItemList backing() {
		return ExpandedInventoryManager.backingFor(player, local);
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < backing().size() ? backing().get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(backing(), slot, amount);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (slot < 0 || slot >= backing().size()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = backing().get(slot);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		backing().set(slot, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= backing().size()) {
			return;
		}
		backing().set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
		setChanged();
	}

	@Override
	public void setChanged() {
	}

	@Override
	public int getContainerSize() {
		return ExpandedInventoryManager.EXTRA_SIZE;
	}

	@Override
	public boolean isEmpty() {
		return backing().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		backing().clear();
		setChanged();
	}

	@Override
	public void fillStackedContents(StackedItemContents contents) {
		backing().forEach(contents::accountStack);
	}

	public NonNullList<ItemStack> getItems() {
		return backing();
	}

	public boolean active() {
		if (player instanceof ServerPlayer) {
			return TeamLookup.serverStateOf(player) != null;
		}
		return clientActive;
	}

	public void setClientActive(boolean active) {
		clientActive = active;
	}

	public boolean addStack(ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return false;
		}
		int before = stack.getCount();
		mergeExisting(stack);
		insertIntoEmptySlots(stack);
		if (stack.getCount() != before) {
			setChanged();
			return true;
		}
		return false;
	}

	public boolean mergeExisting(ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return false;
		}
		int before = stack.getCount();
		for (int slot = 0; slot < getContainerSize() && !stack.isEmpty(); slot++) {
			ItemStack existing = getItem(slot);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
				continue;
			}
			int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
			if (moved > 0) {
				existing.grow(moved);
				stack.shrink(moved);
			}
		}
		if (stack.getCount() != before) {
			setChanged();
			return true;
		}
		return false;
	}

	private void insertIntoEmptySlots(ItemStack stack) {
		for (int slot = 0; slot < getContainerSize() && !stack.isEmpty(); slot++) {
			if (getItem(slot).isEmpty()) {
				ItemStack inserted = stack.copyAndClear();
				inserted.setPopTime(5);
				setItem(slot, inserted);
			}
		}
	}

	public int clearOrCountMatchingItems(Predicate<ItemStack> predicate, int maximum, boolean countOnly) {
		return ContainerHelper.clearOrCountMatchingItems(this, predicate, maximum, countOnly);
	}

	public void resetLocal() {
		local = SharedItemList.ofSize(ExpandedInventoryManager.EXTRA_SIZE);
	}
}
