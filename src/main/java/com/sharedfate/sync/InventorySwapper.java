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
		prepareJoin(player, false);
	}

	/**
	 * 팀에 들어가기 전, 이 사람이 들고 있던 것을 전부 바닥에 내려놓는다.
	 *
	 * @param keepScreenOpen 참이면 <b>인벤토리 메뉴만</b> 떠 있을 때 닫기 패킷을 보내지 않는다.
	 *                       상자·화로처럼 따로 연 창은 그래도 닫는다
	 */
	public static void prepareJoin(ServerPlayer player, boolean keepScreenOpen) {
		if (keepScreenOpen) {
			closeOpenedContainerOnly(player);
		} else {
			player.closeContainer();
		}
		dropAndClear(player, player.getInventory());
		if (ExpandedInventoryManager.enabled()) {
			dropAndClear(player, ExpandedInventoryManager.extraFor(player));
		}
		if (SharedFateMod.config.shareEnderChest) {
			Container personalEnder = ((PlayerEnderChestAccessor) player).sharedfate$getPersonalEnderChest();
			dropAndClear(player, personalEnder);
		}
	}

	/**
	 * 따로 연 창(상자·화로·조합대)만 닫고 인벤토리 메뉴는 건드리지 않는다.
	 *
	 * <p>{@code ServerPlayer.closeContainer()} 는 컨테이너 닫기 패킷을 보내는데, 클라이언트는
	 * 그것을 받으면 <b>지금 떠 있는 화면이 무엇이든</b> 없앤다 —
	 * {@code LocalPlayer.clientSideCloseContainer} 가 {@code Gui.setScreen(null)} 을 부르기
	 * 때문이다. 컨테이너와 아무 상관 없는 창까지 함께 사라진다.
	 *
	 * <p>여기서 창을 닫는 본래 이유는 상자를 연 채로 인벤토리를 통째로 바꿔치우면 그 창의
	 * 아래 칸이 옛 목록을 가리키기 때문이다. 인벤토리 메뉴만 떠 있으면 그럴 일이 없다 —
	 * {@link #finishJoin} 이 바뀐 목록을 곧바로 내려보낸다. 대신 창을 닫을 때 바닐라가 해 주던
	 * 뒷정리({@code AbstractContainerMenu.removed} — 커서에 쥔 것을 내려놓고 2×2 조합칸을
	 * 인벤토리로 되돌린다)는 그대로 부른다. 빠뜨리면 그 아이템만 공유되지 않고 남는다.
	 * 되돌려 놓은 조합칸은 바로 뒤의 {@code dropAndClear} 가 함께 바닥으로 내린다.
	 */
	private static void closeOpenedContainerOnly(ServerPlayer player) {
		if (player.containerMenu != player.inventoryMenu) {
			player.closeContainer();
			return;
		}
		player.containerMenu.removed(player);
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
