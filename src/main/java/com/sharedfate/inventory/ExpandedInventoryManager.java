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
	/**
	 * 추가 칸이 <b>가질 수 있는 최대</b> 개수. 메뉴에 실제로 만들어지는 칸 수이기도 하다.
	 *
	 * <p>이 값은 팀마다 달라지지 않는다. 달라지는 것은 {@link #unlockedFor 해금된 개수}이고,
	 * 잠긴 칸은 지우는 것이 아니라 화면 밖({@link #HIDDEN_Y})으로 치우고 물건을 못 넣게 막는다.
	 * 메뉴의 칸 수 자체가 팀마다 다르면 바닐라의 칸 동기화가 어긋나기 때문이다.
	 */
	public static final int EXTRA_SIZE = 27;

	/**
	 * 아무 증강 없이 열려 있는 추가 칸. 두 줄이다.
	 *
	 * <p>바닐라 36칸과 합쳐 <b>9×6 = 54칸</b>이 기본이다. 나머지 아홉 칸은 「짐꾼」
	 * ({@code inventory_slots})으로 연다 — 여섯 칸을 받고, 「가호 3」이 켜지면 아홉 칸이 되어
	 * 9×7 = 63칸이 전부 열린다.
	 */
	public static final int BASE_EXTRA_SIZE = 18;
	public static final int VANILLA_INVENTORY_MENU_SIZE = 46;
	public static final int EXPANDED_INVENTORY_MENU_SIZE = VANILLA_INVENTORY_MENU_SIZE + EXTRA_SIZE;

	/** 한 줄에 들어가는 칸 수. */
	public static final int EXTRA_COLUMNS = 9;
	/** 추가 칸이 차지할 수 있는 최대 줄 수. */
	public static final int EXTRA_ROWS = EXTRA_SIZE / EXTRA_COLUMNS;
	/** 기본으로 열려 있는 줄 수. */
	public static final int BASE_EXTRA_ROWS = BASE_EXTRA_SIZE / EXTRA_COLUMNS;
	/** 칸 하나의 간격. */
	public static final int SLOT_PITCH = 18;
	/** 추가 칸이 최대로 잡아먹는 높이. */
	public static final int EXTRA_PANEL_HEIGHT = EXTRA_ROWS * SLOT_PITCH;

	/** 해금된 칸 수를 줄 수로. 9로 나누어떨어지지 않아도 줄은 올림한다. */
	public static int rowsFor(int unlocked) {
		int clamped = Math.max(0, Math.min(EXTRA_SIZE, unlocked));
		return (clamped + EXTRA_COLUMNS - 1) / EXTRA_COLUMNS;
	}

	/** 해금된 칸이 잡아먹는 높이. 창이 이만큼 커진다. */
	public static int panelHeightFor(int unlocked) {
		return rowsFor(unlocked) * SLOT_PITCH;
	}

	/**
	 * 인벤토리 첫 줄에서 <b>추가 첫 줄</b>까지의 거리.
	 *
	 * <p>바닐라 세 줄이 {@code y}, {@code y+18}, {@code y+36} 이므로 그 바로 아래다.
	 */
	public static final int EXTRA_TOP_OFFSET = EXTRA_ROWS * SLOT_PITCH;
	/** 인벤토리 첫 줄에서 추가 첫 줄까지. 바닐라 세 줄 바로 아래라 해금 수와 무관하다. */
	public static final int EXTRA_TOP_OFFSET_FIXED = 3 * SLOT_PITCH;

	/** 인벤토리 첫 줄에서 핫바까지의 거리. 바닐라 {@code addStandardInventorySlots} 값이다. */
	public static final int HOTBAR_OFFSET = 58;
	/** 추가 칸이 최대로 끼어들었을 때의 핫바 거리. */
	public static final int EXPANDED_HOTBAR_OFFSET = HOTBAR_OFFSET + EXTRA_PANEL_HEIGHT;

	/** 해금된 칸 수만큼 밀린 핫바 거리. */
	public static int hotbarOffsetFor(int unlocked) {
		return HOTBAR_OFFSET + panelHeightFor(unlocked);
	}

	/** 숨긴 칸을 치워 두는 y. 화면 밖이라 그려지지도, 눌리지도 않는다. */
	public static final int HIDDEN_Y = -1000;

	private static final Map<Player, ExpandedInventoryContainer> PLAYER_CONTAINERS =
			Collections.synchronizedMap(new IdentityHashMap<>());
	private static Boolean negotiatedClientLayout;
	private static Player clientPlayer;
	/**
	 * 클라이언트가 서버에게서 받은 해금 칸 수.
	 *
	 * <p>서버는 팀 상태에서 곧바로 세지만 클라이언트에는 팀 상태가 없다. 그래서
	 * {@code TeamSyncPayload} 로 받은 값을 여기에 적어 두고 화면을 그릴 때 쓴다.
	 */
	private static int clientUnlockedSlots = BASE_EXTRA_SIZE;

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

	/** 클라이언트가 받은 해금 칸 수를 적어 둔다. {@code TeamSyncPayload} 를 처리하는 자리에서 부른다. */
	public static void setClientUnlockedSlots(int unlocked) {
		clientUnlockedSlots = Math.max(0, Math.min(EXTRA_SIZE, unlocked));
	}

	/** 클라이언트가 마지막으로 받은 해금 칸 수. */
	public static int clientUnlockedSlots() {
		return clientUnlockedSlots;
	}

	/**
	 * 이 사람에게 지금 열려 있는 추가 칸 수.
	 *
	 * <p>서버는 팀 상태에서 곧바로 센다. 클라이언트는 서버가 보내 준 값을 쓴다 — 클라이언트에는
	 * 팀의 보유 증강이 없어서 스스로 셀 수 없다.
	 */
	public static int unlockedFor(Player player) {
		if (player instanceof ServerPlayer) {
			return com.sharedfate.perk.PerkInventorySlots.unlockedFor(
					TeamLookup.stateOf(player.getUUID()));
		}
		return clientUnlockedSlots;
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
		int unlocked = expandedActive ? unlockedFor(player) : 0;
		updateMenuLayout(player.inventoryMenu, expandedActive, unlocked);
		if (player.containerMenu != player.inventoryMenu) {
			updateMenuLayout(player.containerMenu, expandedActive, unlocked);
		}
	}

	/** 해금 수를 따로 주지 않으면 전부 열린 것으로 본다. 기존 호출자를 위한 자리다. */
	public static void updateMenuLayout(AbstractContainerMenu menu, boolean expandedActive) {
		updateMenuLayout(menu, expandedActive, expandedActive ? EXTRA_SIZE : 0);
	}

	/**
	 * 메뉴 하나의 <b>핫바와 추가 27칸</b> 좌표를 다시 잡는다.
	 *
	 * <p>추가 칸은 인벤토리 세 줄 <b>바로 아래</b>로 들어가고 핫바가 그만큼 내려간다.
	 * 그래서 여섯 줄이 끊김 없이 이어져 보이고, 창 안쪽이라 바닐라 칸과 똑같이 눌린다.
	 *
	 * <p>팀에 속하지 않으면 추가 칸을 화면 밖({@link #HIDDEN_Y})으로 치우고 핫바를
	 * 바닐라 자리로 되돌린다.
	 */
	public static void updateMenuLayout(AbstractContainerMenu menu, boolean expandedActive,
			int unlocked) {
		if (menu == null || !enabled() || !(menu instanceof ExpandedMenuLayout layout)) {
			return;
		}
		int open = expandedActive ? Math.max(0, Math.min(EXTRA_SIZE, unlocked)) : 0;
		int playerStart = layout.sharedfate$playerSlotStart();
		int extraStart = layout.sharedfate$extraSlotStart();
		int inventoryTop = layout.sharedfate$inventoryTopY();
		if (playerStart < 0 || extraStart < 0 || inventoryTop < 0
				|| extraStart + EXTRA_SIZE > menu.slots.size()) {
			return;
		}

		// 핫바는 「열린 칸이 잡아먹는 높이」만큼만 내려간다. 잠긴 줄은 자리를 차지하지 않는다.
		int hotbarY = inventoryTop + (expandedActive ? hotbarOffsetFor(open) : HOTBAR_OFFSET);
		for (int column = 0; column < EXTRA_COLUMNS; column++) {
			((SlotAccessor) (Object) menu.getSlot(playerStart + EXTRA_SIZE + column))
					.sharedfate$setY(hotbarY);
		}
		for (int extraIndex = 0; extraIndex < EXTRA_SIZE; extraIndex++) {
			// 잠긴 칸은 화면 밖으로 치운다. 지우는 것이 아니라 숨기는 것이라 메뉴의 칸 수는
			// 언제나 같다 — 팀마다 칸 수가 다르면 바닐라의 칸 동기화가 어긋난다.
			int y = extraIndex < open
					? inventoryTop + EXTRA_TOP_OFFSET_FIXED
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
