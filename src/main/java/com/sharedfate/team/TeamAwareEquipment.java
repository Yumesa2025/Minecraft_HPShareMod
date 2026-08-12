package com.sharedfate.team;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public class TeamAwareEquipment extends PlayerEquipment {
	private final Player player;
	private final Supplier<@Nullable SharedEquipmentStore> sharedStore;

	public TeamAwareEquipment(Player player) {
		this(player, () -> {
			TeamState state = TeamLookup.serverStateOf(player);
			return state == null ? null : state.equipment;
		});
	}

	TeamAwareEquipment(Player player, Supplier<@Nullable SharedEquipmentStore> sharedStore) {
		super(player);
		this.player = player;
		this.sharedStore = sharedStore;
	}

	private @Nullable SharedEquipmentStore currentSharedStore() {
		return sharedStore.get();
	}

	@Override
	public ItemStack set(EquipmentSlot slot, ItemStack stack) {
		if (slot == EquipmentSlot.MAINHAND) {
			return super.set(slot, stack);
		}
		SharedEquipmentStore store = currentSharedStore();
		return store == null ? super.set(slot, stack) : store.set(slot, stack);
	}

	@Override
	public ItemStack get(EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND) {
			return super.get(slot);
		}
		SharedEquipmentStore store = currentSharedStore();
		return store == null ? super.get(slot) : store.get(slot);
	}

	@Override
	public boolean isEmpty() {
		SharedEquipmentStore store = currentSharedStore();
		return store == null ? super.isEmpty() : store.isEmpty();
	}

	@Override
	public void setAll(EntityEquipment other) {
		if (currentSharedStore() == null) {
			super.setAll(other);
		}
	}

	@Override
	public void clear() {
		SharedEquipmentStore store = currentSharedStore();
		if (store == null) {
			super.clear();
		} else {
			store.clear();
		}
	}

	@Override
	public void tick(Entity entity) {
		SharedEquipmentStore store = currentSharedStore();
		if (store == null) {
			super.tick(entity);
			return;
		}
		for (Map.Entry<EquipmentSlot, ItemStack> entry : store.view().entrySet()) {
			ItemStack stack = entry.getValue();
			if (!stack.isEmpty()) {
				stack.inventoryTick(entity.level(), entity, entry.getKey());
			}
		}
	}

	@Override
	public void dropAll(LivingEntity entity) {
		if (currentSharedStore() == null) {
			super.dropAll(entity);
		}
	}
}
