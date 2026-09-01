package com.sharedfate.inventory;

import com.sharedfate.SharedFateMod;
import com.sharedfate.net.HandshakePayload;
import com.sharedfate.mixin.SlotAccessor;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ExpandedInventoryManager {
	public static final int EXTRA_SIZE = 27;
	public static final int VANILLA_INVENTORY_MENU_SIZE = 46;
	public static final int EXPANDED_INVENTORY_MENU_SIZE = VANILLA_INVENTORY_MENU_SIZE + EXTRA_SIZE;

	/** 한 줄에 들어가는 칸 수. */
	public static final int EXTRA_COLUMNS = 9;
	/** 추가 칸이 차지하는 줄 수. */
	public static final int EXTRA_ROWS = EXTRA_SIZE / EXTRA_COLUMNS;
	/** 칸 하나의 간격. */
	public static final int SLOT_PITCH = 18;
	/** 추가 세 줄이 잡아먹는 높이. 창도 이만큼 커집니다. */
	public static final int EXTRA_PANEL_HEIGHT = EXTRA_ROWS * SLOT_PITCH;

	/**
	 * 인벤토리 첫 줄에서 <b>추가 첫 줄</b>까지의 거리.
	 *
	 * <p>바닐라 세 줄이 {@code y}, {@code y+18}, {@code y+36} 이므로 그 바로 아래입니다.
	 */
	public static final int EXTRA_TOP_OFFSET = EXTRA_ROWS * SLOT_PITCH;

	/** 인벤토리 첫 줄에서 핫바까지의 거리. 바닐라 {@code addStandardInventorySlots} 값입니다. */
	public static final int HOTBAR_OFFSET = 58;
	/** 추가 세 줄이 끼어들었을 때의 핫바 거리. */
	public static final int EXPANDED_HOTBAR_OFFSET = HOTBAR_OFFSET + EXTRA_PANEL_HEIGHT;

	/** 숨긴 칸을 치워 두는 y. 화면 밖이라 그려지지도, 눌리지도 않습니다. */
	public static final int HIDDEN_Y = -1000;

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
		if (player == null) {
			return;
		}
		updateMenuLayout(player.inventoryMenu, expandedActive);
		if (player.containerMenu != player.inventoryMenu) {
			updateMenuLayout(player.containerMenu, expandedActive);
		}
	}

	/**
	 * 메뉴 하나의 <b>핫바와 추가 27칸</b> 좌표를 다시 잡습니다.
	 *
	 * <p>추가 칸은 인벤토리 세 줄 <b>바로 아래</b>로 들어가고 핫바가 그만큼 내려갑니다.
	 * 그래서 여섯 줄이 끊김 없이 이어져 보이고, 창 안쪽이라 바닐라 칸과 똑같이 눌립니다.
	 * 예전에는 창 <b>오른쪽 바깥</b>에 붙어 있어서 바닐라가 「창 밖을 눌렀다」로 읽고
	 * 들고 있던 아이템을 바닥에 버렸습니다.
	 *
	 * <p>팀에 속하지 않으면 추가 칸을 화면 밖({@link #HIDDEN_Y})으로 치우고 핫바를
	 * 바닐라 자리로 되돌립니다.
	 */
	public static void updateMenuLayout(AbstractContainerMenu menu, boolean expandedActive) {
		if (menu == null || !enabled() || !(menu instanceof ExpandedMenuLayout layout)) {
			return;
		}
		int playerStart = layout.sharedfate$playerSlotStart();
		int extraStart = layout.sharedfate$extraSlotStart();
		int inventoryTop = layout.sharedfate$inventoryTopY();
		if (playerStart < 0 || extraStart < 0 || inventoryTop < 0
				|| extraStart + EXTRA_SIZE > menu.slots.size()) {
			return;
		}

		int hotbarY = inventoryTop + (expandedActive ? EXPANDED_HOTBAR_OFFSET : HOTBAR_OFFSET);
		for (int column = 0; column < EXTRA_COLUMNS; column++) {
			((SlotAccessor) (Object) menu.getSlot(playerStart + EXTRA_SIZE + column))
					.sharedfate$setY(hotbarY);
		}
		for (int extraIndex = 0; extraIndex < EXTRA_SIZE; extraIndex++) {
			int y = expandedActive
					? inventoryTop + EXTRA_TOP_OFFSET
							+ (extraIndex / EXTRA_COLUMNS) * SLOT_PITCH
					: HIDDEN_Y;
			((SlotAccessor) (Object) menu.getSlot(extraStart + extraIndex)).sharedfate$setY(y);
		}
	}

	public static void clearRuntimeState() {
		PLAYER_CONTAINERS.clear();
		negotiatedClientLayout = null;
		clientPlayer = null;
	}
}
