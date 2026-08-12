package com.sharedfate.team;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class SharedEquipmentStore {
	private final EnumMap<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);

	public ItemStack get(EquipmentSlot slot) {
		return items.getOrDefault(slot, ItemStack.EMPTY);
	}

	public ItemStack set(EquipmentSlot slot, ItemStack stack) {
		ItemStack previous = get(slot);
		if (stack.isEmpty()) {
			items.remove(slot);
		} else {
			items.put(slot, stack);
		}
		return previous;
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public void clear() {
		items.clear();
	}

	public Map<EquipmentSlot, ItemStack> view() {
		return Collections.unmodifiableMap(items);
	}

	public static final Codec<SharedEquipmentStore> CODEC =
			Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.OPTIONAL_CODEC).xmap(
					map -> {
						SharedEquipmentStore store = new SharedEquipmentStore();
						map.forEach(store::set);
						return store;
					},
					store -> Map.copyOf(store.items)
			);
}
