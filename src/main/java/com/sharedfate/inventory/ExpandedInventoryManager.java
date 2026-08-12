package com.sharedfate.inventory;

import com.sharedfate.SharedFateMod;
import com.sharedfate.net.HandshakePayload;
import com.sharedfate.mixin.SlotAccessor;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ExpandedInventoryManager {
	public static final int EXTRA_SIZE = 27;
	public static final int VANILLA_INVENTORY_MENU_SIZE = 46;
	public static final int EXPANDED_INVENTORY_MENU_SIZE = VANILLA_INVENTORY_MENU_SIZE + EXTRA_SIZE;

	private static final Map<Player, ExpandedInventoryContainer> PLAYER_CONTAINERS =
			Collections.synchronizedMap(new IdentityHashMap<>());
	private static Boolean negotiatedClientLayout;
	private static Player clientPlayer;

	private ExpandedInventoryManager() {
	}

	public static boolean enabled() {
		if (negotiatedClientLayout != null) {
			return negotiatedClientLayout;
		}
		return SharedFateMod.config != null && SharedFateMod.config.mainInventoryRows == 6;
	}

	public static void applyNegotiatedClientLayout(int inventoryLayout) {
		negotiatedClientLayout = inventoryLayout == HandshakePayload.SIX_ROW_LAYOUT;
	}

	public static void clearNegotiatedClientLayout() {
		negotiatedClientLayout = null;
		if (clientPlayer != null) {
			PLAYER_CONTAINERS.remove(clientPlayer);
			clientPlayer = null;
		}
	}

	public static ExpandedInventoryContainer extraFor(Player player) {
		return PLAYER_CONTAINERS.computeIfAbsent(player, ExpandedInventoryContainer::new);
	}

	static com.sharedfate.team.SharedItemList backingFor(
			Player player, com.sharedfate.team.SharedItemList local) {
		if (player == null) {
			return local;
		}
		TeamState state = TeamLookup.serverStateOf(player);
		if (state == null) {
			return local;
		}
		return state.extraItems;
	}

	public static void removePlayer(Player player) {
		PLAYER_CONTAINERS.remove(player);
	}

	public static void refreshBacking(ServerPlayer player, boolean resetPersonal) {
		ExpandedInventoryContainer extra = extraFor(player);
		if (resetPersonal) {
			extra.resetLocal();
		}
		updateMenuLayout(player, extra.active());
		if (enabled()) {
			player.inventoryMenu.broadcastFullState();
		}
	}

	public static void setClientTeamActive(Player player, boolean active) {
		if (player == null || !enabled()) {
			return;
		}
		if (clientPlayer != player) {
			if (clientPlayer != null) {
				PLAYER_CONTAINERS.remove(clientPlayer);
			}
			clientPlayer = player;
		}
		ExpandedInventoryContainer extra = extraFor(player);
		extra.setClientActive(active);
		updateMenuLayout(player, active);
	}

	public static void updateMenuLayout(Player player, boolean expandedActive) {
		if (player == null || player.inventoryMenu == null) {
			return;
		}
		updateMenuLayout(player.inventoryMenu, expandedActive);
		if (player.containerMenu instanceof CraftingMenu craftingMenu) {
			updateCraftingMenuLayout(craftingMenu, expandedActive);
		}
	}

	public static void updateMenuLayout(
			net.minecraft.world.inventory.InventoryMenu menu, boolean expandedActive) {
		if (!enabled() || menu.slots.size() < EXPANDED_INVENTORY_MENU_SIZE) {
			return;
		}
		for (int menuSlot = 36; menuSlot < 45; menuSlot++) {
			((SlotAccessor) (Object) menu.getSlot(menuSlot))
					.sharedfate$setY(expandedActive ? 196 : 142);
		}
		for (int menuSlot = 46; menuSlot < EXPANDED_INVENTORY_MENU_SIZE; menuSlot++) {
			int extraIndex = menuSlot - VANILLA_INVENTORY_MENU_SIZE;
			int y = expandedActive ? 138 + (extraIndex / 9) * 18 : -1000;
			((SlotAccessor) (Object) menu.getSlot(menuSlot)).sharedfate$setY(y);
		}
	}

	public static void updateCraftingMenuLayout(
			CraftingMenu menu, boolean expandedActive) {
		if (!enabled() || menu.slots.size() < EXPANDED_INVENTORY_MENU_SIZE) {
			return;
		}
		for (int menuSlot = 37; menuSlot < VANILLA_INVENTORY_MENU_SIZE; menuSlot++) {
			((SlotAccessor) (Object) menu.getSlot(menuSlot))
					.sharedfate$setY(expandedActive ? 196 : 142);
		}
		for (int menuSlot = VANILLA_INVENTORY_MENU_SIZE;
				menuSlot < EXPANDED_INVENTORY_MENU_SIZE; menuSlot++) {
			int extraIndex = menuSlot - VANILLA_INVENTORY_MENU_SIZE;
			int y = expandedActive ? 138 + (extraIndex / 9) * 18 : -1000;
			((SlotAccessor) (Object) menu.getSlot(menuSlot)).sharedfate$setY(y);
		}
	}

	public static void clearRuntimeState() {
		PLAYER_CONTAINERS.clear();
		negotiatedClientLayout = null;
		clientPlayer = null;
	}
}
