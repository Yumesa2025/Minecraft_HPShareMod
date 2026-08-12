package com.sharedfate.sync;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public final class TeamEnderChestView extends PlayerEnderChestContainer {
	private final Supplier<PlayerEnderChestContainer> backingSupplier;

	public TeamEnderChestView(Supplier<PlayerEnderChestContainer> backingSupplier) {
		this.backingSupplier = backingSupplier;
	}

	private PlayerEnderChestContainer backing() {
		return backingSupplier.get();
	}

	@Override
	public ItemStack getItem(int slot) {
		return backing().getItem(slot);
	}

	@Override
	public List<ItemStack> removeAllItems() {
		return backing().removeAllItems();
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return backing().removeItem(slot, amount);
	}

	@Override
	public ItemStack removeItemType(Item item, int amount) {
		return backing().removeItemType(item, amount);
	}

	@Override
	public ItemStack addItem(ItemStack stack) {
		return backing().addItem(stack);
	}

	@Override
	public boolean canAddItem(ItemStack stack) {
		return backing().canAddItem(stack);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return backing().removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		backing().setItem(slot, stack);
	}

	@Override
	public void setChanged() {
		backing().setChanged();
	}

	@Override
	public int getContainerSize() {
		return backing().getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return backing().isEmpty();
	}

	@Override
	public void clearContent() {
		backing().clearContent();
	}

	@Override
	public void fillStackedContents(StackedItemContents contents) {
		backing().fillStackedContents(contents);
	}

	@Override
	public void fromItemList(ValueInput.TypedInputList<ItemStack> input) {
		backing().fromItemList(input);
	}

	@Override
	public void storeAsItemList(ValueOutput.TypedOutputList<ItemStack> output) {
		backing().storeAsItemList(output);
	}

	@Override
	public NonNullList<ItemStack> getItems() {
		return backing().getItems();
	}

	@Override
	public void fromSlots(ValueInput.TypedInputList<net.minecraft.world.ItemStackWithSlot> input) {
		backing().fromSlots(input);
	}

	@Override
	public void storeAsSlots(ValueOutput.TypedOutputList<net.minecraft.world.ItemStackWithSlot> output) {
		backing().storeAsSlots(output);
	}

	@Override
	public Iterator<ItemStack> iterator() {
		return backing().iterator();
	}

	@Override
	public String toString() {
		return backing().toString();
	}
}
