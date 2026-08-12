package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.mixin.InventoryAccessor;
import com.sharedfate.mixin.PlayerEnderChestAccessor;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class InventorySwapper {
	private InventorySwapper() {
	}

	public static void prepareJoin(ServerPlayer player) {
		player.closeContainer();
		dropAndClear(player, player.getInventory());
		if (ExpandedInventoryManager.enabled()) {
			dropAndClear(player, ExpandedInventoryManager.extraFor(player));
		}
		if (SharedFateMod.config.shareEnderChest) {
			Container personalEnder = ((PlayerEnderChestAccessor) player).sharedfate$getPersonalEnderChest();
			dropAndClear(player, personalEnder);
		}
	}

	public static void finishJoin(ServerPlayer player, TeamState state) {
		((InventoryAccessor) player.getInventory()).sharedfate$setItems(state.mainItems);
		ExpandedInventoryManager.refreshBacking(player, false);
		player.containerMenu.broadcastChanges();
	}

	public static void prepareLeave(ServerPlayer player) {
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(player.getUUID());
		if (state != null) {
			stashCarried(player, state);
		}
		player.closeContainer();
		((InventoryAccessor) player.getInventory()).sharedfate$setItems(
				NonNullList.withSize(TeamState.MAIN_SIZE, ItemStack.EMPTY));
		player.containerMenu.broadcastChanges();
	}

	public static void finishLeave(ServerPlayer player) {
		ExpandedInventoryManager.refreshBacking(player, true);
	}

	public static void stashCarried(ServerPlayer player, TeamState state) {
		ItemStack carried = player.containerMenu.getCarried();
		if (!carried.isEmpty()) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			state.overflowItems.add(carried);
			state.restoreOverflow(ExpandedInventoryManager.enabled());
		}
	}

	public static void disbandTeam(
			ServerPlayer dropper, ShareTeam team, TeamState state, TeamManager manager) {
		int sharedExperience = SharedFateMod.config.shareExperience ? state.totalExperience : 0;
		List<ServerPlayer> onlineMembers = new ArrayList<>();
		List<ItemStack> carriedItems = new ArrayList<>();
		for (var memberId : team.members()) {
			ServerPlayer online = dropper.level().getServer().getPlayerList().getPlayer(memberId);
			if (online != null) {
				ItemStack carried = online.containerMenu.getCarried();
				if (!carried.isEmpty()) {
					carriedItems.add(carried);
					online.containerMenu.setCarried(ItemStack.EMPTY);
				}
				prepareLeave(online);
				onlineMembers.add(online);
			}
		}

		drainSharedItems(state, stack -> dropper.drop(stack, true, false));
		carriedItems.forEach(stack -> dropper.drop(stack, true, false));
		if (SharedFateMod.config.shareStatusEffects) {
			team.members().forEach(manager::markEffectClear);
		}
		if (SharedFateMod.config.shareExperience) {
			team.members().forEach(manager::markExperienceClear);
		}
		manager.disband(team.teamId());
		for (ServerPlayer online : onlineMembers) {
			finishLeave(online);
			MaxHealthAttribute.remove(online);
			StatMirror.forget(online.getUUID());
			EffectSync.clearDetachedPlayer(online);
			manager.consumeEffectClear(online.getUUID());
			if (SharedFateMod.config.shareExperience) {
				StatMirror.setTotalExperience(online, 0);
				manager.consumeExperienceClear(online.getUUID());
			}
			TeamBroadcaster.sendEmpty(online);
		}
		if (SharedFateMod.config.shareExperience) {
			StatMirror.setTotalExperience(dropper, sharedExperience);
		}
	}

	static void drainSharedItems(TeamState state, Consumer<ItemStack> consumer) {
		drainDeathDrops(state, consumer);

		for (int slot = 0; slot < state.enderContainer.getContainerSize(); slot++) {
			ItemStack stack = state.enderContainer.getItem(slot);
			if (!stack.isEmpty()) {
				consumer.accept(stack);
				state.enderContainer.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	public static void drainDeathDrops(TeamState state, Consumer<ItemStack> consumer) {
		for (int slot = 0; slot < state.mainItems.size(); slot++) {
			ItemStack stack = state.mainItems.get(slot);
			if (!stack.isEmpty()) {
				consumer.accept(stack);
				state.mainItems.set(slot, ItemStack.EMPTY);
			}
		}
		for (int slot = 0; slot < state.extraItems.size(); slot++) {
			ItemStack stack = state.extraItems.get(slot);
			if (!stack.isEmpty()) {
				consumer.accept(stack);
				state.extraItems.set(slot, ItemStack.EMPTY);
			}
		}

		for (ItemStack stack : new ArrayList<>(state.equipment.view().values())) {
			if (!stack.isEmpty()) {
				consumer.accept(stack);
			}
		}
		state.equipment.clear();
		for (ItemStack stack : state.overflowItems) {
			if (!stack.isEmpty()) {
				consumer.accept(stack);
			}
		}
		state.overflowItems.clear();
	}

	private static void dropAndClear(ServerPlayer player, Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				player.drop(stack, true, false);
				container.setItem(slot, ItemStack.EMPTY);
			}
		}
	}
}
