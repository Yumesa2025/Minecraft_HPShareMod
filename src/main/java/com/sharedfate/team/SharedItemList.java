package com.sharedfate.team;

import com.mojang.serialization.Codec;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SharedItemList extends NonNullList<ItemStack> {

	private SharedItemList(List<ItemStack> delegate) {
		super(delegate, ItemStack.EMPTY);
	}

	public static SharedItemList ofSize(int size) {
		if (size < 0) {
			throw new IllegalArgumentException("아이템 목록 크기는 음수일 수 없습니다: " + size);
		}
		List<ItemStack> backing = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			backing.add(ItemStack.EMPTY);
		}
		return new SharedItemList(backing);
	}

	public static SharedItemList copyOf(List<ItemStack> stacks, int size) {
		SharedItemList list = ofSize(size);
		for (int i = 0; i < Math.min(size, stacks.size()); i++) {
			list.set(i, stacks.get(i));
		}
		return list;
	}

	public static Codec<SharedItemList> codec(int size) {
		return ItemStack.OPTIONAL_CODEC.listOf().xmap(
				stacks -> copyOf(stacks, size),
				List::copyOf
		);
	}

	public List<ItemStack> snapshot() {
		return new ArrayList<>(this);
	}

	@Override
	public String toString() {
		return "SharedItemList" + Arrays.toString(toArray());
	}
}
