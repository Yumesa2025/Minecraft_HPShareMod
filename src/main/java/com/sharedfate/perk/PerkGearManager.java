package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.EquipBanEffect;
import com.sharedfate.perk.effect.OffhandLockEffect;
import com.sharedfate.team.SharedItemList;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 장비·무기 관련 증강의 집행부.
 *
 * <p>{@link PerkGearRules} 가 "막아야 하는가"를 답한다면 여기는 "이미 어긋난 상태를 되돌린다".
 * 막는 일 자체는 mixin 이 하지만, 막기 전에 이미 입고 있던 장비나 명령·다른 모드가 억지로 넣은
 * 아이템은 mixin 이 손댈 수 없다. 그 뒤처리를 이 클래스가 맡는다.
 *
 * <h2>주기</h2>
 * <p>공격력은 매 틱 맞춘다. 무기를 바꾸자마자 반영되지 않으면 전투에서 곧바로 체감되기 때문이다.
 * 장비를 벗기고 왼손을 채우는 일은 {@value #SWEEP_INTERVAL_TICKS} 틱마다 한다. 그 정도면
 * 사람이 알아채기 전에 끝나고, 인벤토리를 건드리는 일이라 자주 할수록 손해다.
 *
 * <h2>공유 인벤토리</h2>
 * <p>벗긴 장비는 개인 인벤토리 API 로 넣지 않는다. 이 모드는 팀원의 인벤토리를 통째로
 * {@link TeamState#mainItems} 로 바꿔 끼우므로, 넣어야 할 곳은 언제나 그 공유 목록이다.
 * 자리가 없으면 바닥에 떨어뜨리지 않고 {@link TeamState#overflowItems} 에 얹는다.
 * {@code TeamManager.markDirtyIfActive} 가 칸이 빌 때마다 다시 밀어 넣어 주고 월드와 함께
 * 저장되므로 잃어버리지 않는다. {@link PerkItemGrants} 가 쓰는 길과 같다.
 *
 * <h2>제한이 풀리는 것</h2>
 * <p>{@link #tick} 은 접속 중인 <b>모든</b> 플레이어를 훑고, 팀이 없거나 증강이 없는 사람에게는
 * {@link PerkWeaponDamage#clear} 를 부른다. 그래서 회차 리셋·팀 해체·증강 상실 어느 쪽으로도
 * 공격력 수정자가 남지 않는다. 벗기기·왼손 고정은 애초에 상태를 남기지 않으므로 규칙이
 * 사라지면 그 즉시 아무 일도 일어나지 않는다.
 */
public final class PerkGearManager {
	/** 장비를 훑는 주기. */
	public static final int SWEEP_INTERVAL_TICKS = 5;

	private static int tickCounter;

	private PerkGearManager() {
	}

	/** 서버가 멈출 때 주기 상태를 비운다. */
	public static void reset() {
		tickCounter = 0;
	}

	// ------------------------------------------------------------------ 주기

	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		boolean sweep = ++tickCounter >= SWEEP_INTERVAL_TICKS;
		if (sweep) {
			tickCounter = 0;
		}

		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			TeamState state = PerkGearRules.activeState(TeamLookup.stateOf(player.getUUID()));
			if (state == null) {
				// 제한이 없는 사람. 예전에 붙여 둔 수정자만 걷어내면 된다.
				if (sweep) {
					PerkWeaponDamage.clear(player);
					PerkDamageBoostBan.clear(player);
				}
				continue;
			}
			PerkWeaponDamage.refresh(player, state);
			// PerkWeaponDamage 뒤에 두면 이번 틱의 무기 배율이 이미 반영된 상태에서 스캔한다.
			// (그 수정자 자체는 id로 예외 처리하므로 순서가 정답을 바꾸지는 않지만, 매 틱
			// 최신 상태를 보는 편이 더 이해하기 쉽다.)
			PerkDamageBoostBan.refresh(player, state);
			if (sweep) {
				enforce(player, state);
			}
		}
	}

	/** 한 플레이어의 장비를 규칙에 맞춘다. 접속·부활 직후처럼 즉시 맞추고 싶을 때도 쓴다. */
	public static void enforce(@Nullable ServerPlayer player, @Nullable TeamState state) {
		TeamState active = PerkGearRules.activeState(state);
		if (player == null || active == null) {
			return;
		}
		try {
			int stowed = stripBlockedArmor(player, active);
			stowed += enforceOffhandLock(player, active);
			if (stowed > 0 && player.containerMenu != null) {
				player.containerMenu.broadcastChanges();
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("증강의 장비 제한을 맞추다가 실패했습니다.", error);
		}
	}

	// ------------------------------------------------------------------ 방어구 벗기기

	/** 막힌 칸이나 막힌 아이템을 벗겨 공유 인벤토리로 보낸다. */
	private static int stripBlockedArmor(ServerPlayer player, TeamState state) {
		int stowed = 0;
		for (EquipmentSlot slot : EquipBanEffect.ARMOR_SLOTS) {
			ItemStack worn = player.getItemBySlot(slot);
			if (worn.isEmpty() || !PerkGearRules.equipmentBlocked(state, slot, worn)) {
				continue;
			}
			// 칸을 비우기 전에 사본을 뜬다. 칸을 비우면 원래 묶음을 누가 쥐고 있을지 보장이 없다.
			ItemStack taken = worn.copy();
			player.setItemSlot(slot, ItemStack.EMPTY);
			stow(player, state, taken, "착용할 수 없는 장비를 벗었습니다");
			stowed++;
		}
		return stowed;
	}

	// ------------------------------------------------------------------ 왼손 고정

	/**
	 * 왼손 칸을 규칙에 맞춘다.
	 *
	 * <p>다른 것이 들어 있으면 공유 인벤토리로 되돌리고, 칸이 비었는데 공유 인벤토리에 지정
	 * 아이템이 있으면 한 개를 끌어와 채운다. 지정 아이템이 하나도 없으면 칸은 빈 채로 잠긴다.
	 * 없어진 아이템을 새로 만들어 주지는 않는다.
	 */
	private static int enforceOffhandLock(ServerPlayer player, TeamState state) {
		OffhandLockEffect lock = PerkGearRules.offhandLock(state);
		if (lock == null) {
			return 0;
		}

		int changed = 0;
		ItemStack held = player.getItemBySlot(EquipmentSlot.OFFHAND);
		if (!held.isEmpty() && !lock.matches(held)) {
			ItemStack taken = held.copy();
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			stow(player, state, taken, "왼손 칸이 고정되어 있어 아이템을 인벤토리로 옮겼습니다");
			held = ItemStack.EMPTY;
			changed++;
		}

		if (held.isEmpty()) {
			ItemStack pulled = takeOne(state, lock);
			if (!pulled.isEmpty()) {
				player.setItemSlot(EquipmentSlot.OFFHAND, pulled);
				changed++;
			}
		}
		return changed;
	}

	/** 공유 인벤토리에서 고정 아이템 한 개를 꺼낸다. 없으면 빈 묶음. 테스트가 직접 부른다. */
	static ItemStack takeOne(TeamState state, OffhandLockEffect lock) {
		ItemStack found = takeOne(state.mainItems, lock);
		if (found.isEmpty() && ExpandedInventoryManager.enabled()) {
			found = takeOne(state.extraItems, lock);
		}
		if (found.isEmpty()) {
			found = takeOneFromOverflow(state, lock);
		}
		return found;
	}

	private static ItemStack takeOne(SharedItemList items, OffhandLockEffect lock) {
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (!lock.matches(stack)) {
				continue;
			}
			ItemStack one = stack.copyWithCount(1);
			stack.shrink(1);
			if (stack.isEmpty()) {
				items.set(slot, ItemStack.EMPTY);
			}
			return one;
		}
		return ItemStack.EMPTY;
	}

	/** 넘침 대기열에도 들어 있을 수 있다. 인벤토리가 꽉 찬 팀은 이쪽에만 있을 수 있다. */
	private static ItemStack takeOneFromOverflow(TeamState state, OffhandLockEffect lock) {
		for (var iterator = state.overflowItems.iterator(); iterator.hasNext();) {
			ItemStack stack = iterator.next();
			if (!lock.matches(stack)) {
				continue;
			}
			ItemStack one = stack.copyWithCount(1);
			stack.shrink(1);
			if (stack.isEmpty()) {
				iterator.remove();
			}
			return one;
		}
		return ItemStack.EMPTY;
	}

	// ------------------------------------------------------------------ 공유 인벤토리로 보내기

	/**
	 * 아이템 하나를 공유 인벤토리에 밀어 넣는다.
	 *
	 * <p>일단 넘침 목록에 얹고 {@link TeamState#restoreOverflow} 를 부른다. 빈 칸 찾기와
	 * 같은 아이템 합치기 규칙을 이 모드가 이미 한 곳에 갖고 있으므로 그대로 쓴다.
	 * 바닥에 떨어뜨리는 길은 없다. 공유 인벤토리 밖으로 새어 나가면 다른 팀원에게는 보이지 않는
	 * 아이템이 생긴다.
	 */
	private static void stow(ServerPlayer player, TeamState state, ItemStack stack, String reason) {
		if (stack.isEmpty()) {
			return;
		}
		boolean leftover = deliver(state, stack);
		player.sendSystemMessage(Component.literal("[증강] " + reason + "."
				+ (leftover ? " 자리가 없어 대기열로 갔습니다. 칸을 비우면 자동으로 들어옵니다." : "")));
	}

	/**
	 * 공유 목록에 실제로 밀어 넣는 부분. 테스트가 직접 부른다.
	 *
	 * @return 자리가 없어 넘침 대기열에 남았으면 true
	 */
	static boolean deliver(TeamState state, ItemStack stack) {
		state.overflowItems.add(stack);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		state.overflowItems.removeIf(ItemStack::isEmpty);

		// restoreOverflow 는 묶음을 새로 만들지 않고 제자리에서 깎으므로 동일성 비교가 성립한다.
		for (ItemStack pending : state.overflowItems) {
			if (pending == stack) {
				return true;
			}
		}
		return false;
	}
}
