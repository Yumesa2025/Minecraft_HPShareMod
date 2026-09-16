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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 장비·무기 관련 증강의 집행부.
 *
 * <p>{@link PerkGearRules} 가 "막아야 하는가"를 답한다면 여기는 "이미 어긋난 상태를 되돌린다".
 * 막는 일 자체는 mixin 이 하지만, 막기 전에 이미 입고 있던 장비나 명령·다른 모드가 억지로 넣은
 * 아이템은 mixin 이 손댈 수 없다. 그 뒤처리를 이 클래스가 맡는다.
 *
 * <h2>주기</h2>
 * <p>공격력은 매 틱 맞춘다. 장비를 벗기고 왼손을 채우는 일은 {@value #SWEEP_INTERVAL_TICKS}
 * 틱마다 한다. 그 정도면 사람이 알아채기 전에 끝나고, 인벤토리를 건드리는 일이라 자주 할수록
 * 손해다.
 *
 * <h2>공유 인벤토리</h2>
 * <p>벗긴 장비는 개인 인벤토리 API 로 넣지 않는다. 이 모드는 팀원의 인벤토리를 통째로
 * {@link TeamState#mainItems} 로 바꿔 끼우므로, 넣어야 할 곳은 언제나 그 공유 목록이다.
 * 자리가 없으면 바닥에 떨어뜨리지 않고 {@link TeamState#overflowItems} 에 얹는다.
 * {@code TeamManager.markDirtyIfActive} 가 칸이 빌 때마다 다시 밀어 넣어 주고 월드와 함께
 * 저장되므로 잃어버리지 않는다.
 *
 * <p><b>단 「핫바에 두면 안 되는」 아이템만은 이 길로 보내면 안 된다.</b> 넘침 대기열은 빈 칸을
 * 0번부터 찾는데 0~8번이 곧 핫바라, 핫바에서 치운 아이템이 그대로 핫바로 돌아온다. 그러면
 * 다음 점검이 또 치우고 또 돌아오는 무한 왕복이 된다. 그래서 그쪽은 {@link #pushToStorage} 라는
 * 별도의 길을 쓴다 — 핫바를 건너뛰고 보관 칸에만 넣는다.
 *
 * <h2>제한이 풀리는 것</h2>
 * <p>{@link #tick} 은 접속 중인 <b>모든</b> 플레이어를 훑고, 팀이 없거나 증강이 없는 사람에게는
 * {@link PerkWeaponDamage#clear} 를 부른다. 그래서 회차 리셋·팀 해체·증강 상실 어느 쪽으로도
 * 공격력 수정자가 남지 않는다. 벗기기·왼손 고정은 애초에 상태를 남기지 않으므로 규칙이
 * 사라지면 그 즉시 아무 일도 일어나지 않는다.
 *
 * <h2>말은 1초에 한 번</h2>
 * <p>점검이 {@value #SWEEP_INTERVAL_TICKS} 틱마다 도는 탓에, 밀려난 아이템을 사람이 계속
 * 핫바로 되가져오면 알림이 초당 네 번 쏟아진다. 그래서 <b>알림에만</b>
 * {@link GearNoticeCooldown} 을 끼웠다. <b>밀어내기·버리기 동작 자체는 쿨다운과 무관하게
 * 그대로 매 점검마다 일어난다</b> — 제한이 느슨해지는 순간이 생기면 그 사이에 금지 장비를
 * 쓸 수 있게 되기 때문이다. 조용히 치워지되 말만 덜 하는 것이다.
 */
public final class PerkGearManager {
	/** 장비를 훑는 주기. */
	public static final int SWEEP_INTERVAL_TICKS = 5;

	/**
	 * 알림을 사람마다 재우는 시계. 재우는 시간은
	 * {@link GearNoticeCooldown#NOTICE_COOLDOWN_TICKS} 하나로 정해진다.
	 */
	private static final GearNoticeCooldown NOTICES = new GearNoticeCooldown();

	private static int tickCounter;

	/**
	 * 자체 틱 카운터. {@link #tick} 이 불릴 때마다 1씩 오른다.
	 *
	 * <p>서버를 껐다 켜면 0 에서 다시 시작하는데 {@link #reset} 이 알림 기록도 함께 비우므로
	 * 어긋날 여지가 없다.
	 */
	private static long now;

	private PerkGearManager() {
	}

	/** 서버가 멈출 때 주기 상태를 비운다. */
	public static void reset() {
		tickCounter = 0;
		now = 0;
		NOTICES.clear();
	}

	// ------------------------------------------------------------------ 주기

	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		now++;
		boolean sweep = ++tickCounter >= SWEEP_INTERVAL_TICKS;
		if (sweep) {
			tickCounter = 0;
		}

		// 점검하는 틱에만 모은다. 알림 기록을 걸러 낼 때 쓰고, 그 외의 틱에는 쓸 데가 없다.
		Set<UUID> online = sweep ? new HashSet<>() : null;
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (online != null) {
				online.add(player.getUUID());
			}
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
			PerkDamageBoostBan.refresh(player, state);
			if (sweep) {
				enforce(player, state);
			}
		}

		if (online != null) {
			// 나간 사람의 기록과 이미 식은 기록을 여기서 함께 버린다. 서버가 며칠 돌아도
			// 남는 것은 "지금 도배되고 있는 접속자" 뿐이다.
			NOTICES.prune(online, now);
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
			// 왼손을 먼저 비워 둔다. 왼손 고정이 걸린 팀이라면 그 뒤에 지정 아이템으로 채워진다.
			stowed += relocateBannedOffhand(player, active);
			stowed += enforceOffhandLock(player, active);
			stowed += relocateBannedHotbarItems(player, active);
			if (stowed > 0 && player.containerMenu != null) {
				player.containerMenu.broadcastChanges();
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("증강의 장비 제한을 맞추다가 실패했습니다.", error);
		}
	}

	// ------------------------------------------------------------------ 방어구 벗기기

	/**
	 * 막힌 칸이나 막힌 아이템을 벗긴다.
	 *
	 * <p>보통은 공유 인벤토리로 돌려보낸다. {@code item_ban} 에 {@code discard: true} 가 걸린
	 * 이유로 벗겨진 것(예: 「금기의 광석」의 다이아몬드 방어구)은 핫바로 되돌아오면 안 되므로
	 * {@link #banish} 로 보관 칸에만 밀어 넣고, 보관 칸까지 꽉 차 있을 때만 떨어뜨린다.
	 */
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
			if (PerkGearRules.itemBanDiscards(state, worn)) {
				banish(player, state, taken,
						"금지된 방어구를 장착할 수 없어 인벤토리로 옮겼습니다",
						"금지된 방어구를 옮길 자리가 없어 버렸습니다");
			} else {
				stow(player, state, taken, "착용할 수 없는 장비를 벗었습니다");
			}
			stowed++;
		}
		return stowed;
	}

	// ------------------------------------------------------------------ 핫바에서 밀어내기

	/**
	 * {@code item_ban} 에 {@code discard: true} 가 걸린 아이템이 핫바(9칸)에 있으면 치운다.
	 *
	 * <p><b>바로 버리지 않는다.</b> 먼저 인벤토리 위쪽 보관 칸으로 밀어 올리고, 거기가 꽉 차서
	 * 옮길 자리가 없을 때만 떨어뜨린다.
	 *
	 * <p>핫바만 본다. 보관 칸에 깊숙이 넣어 두는 것은 막지 않는다.
	 * {@link net.minecraft.world.entity.player.Inventory} 의 핫바 크기를 그대로 쓴다
	 * ({@code getSelectionSize}) — 바닐라가 바뀌면 이 판정도 자동으로 따라온다.
	 */
	private static int relocateBannedHotbarItems(ServerPlayer player, TeamState state) {
		int moved = 0;
		Inventory inventory = player.getInventory();
		int size = Inventory.getSelectionSize();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !PerkGearRules.itemBanDiscards(state, stack)) {
				continue;
			}
			ItemStack taken = stack.copy();
			inventory.setItem(slot, ItemStack.EMPTY);
			banish(player, state, taken,
					"금지된 장비를 핫바에 둘 수 없어 인벤토리로 옮겼습니다",
					"금지된 장비를 옮길 자리가 없어 버렸습니다");
			moved++;
		}
		return moved;
	}

	/**
	 * 왼손 칸에 놓인 자동 폐기 대상을 치운다. 핫바와 같은 순서로 밀어내고, 자리가 없을 때만 버린다.
	 *
	 * <p>왼손도 「장착」이다. 핫바만 훑으면 다이아몬드 검을 왼손에 걸어 두는 것으로 제한을
	 * 통째로 비켜 갈 수 있다. 반대로 <b>{@code discard} 가 없는 평범한 {@code item_ban} 은
	 * 여기서 건드리지 않는다</b> — 그쪽은 어차피 무력해진 아이템이라 어디에 두든 상관없다.
	 */
	private static int relocateBannedOffhand(ServerPlayer player, TeamState state) {
		ItemStack held = player.getItemBySlot(EquipmentSlot.OFFHAND);
		if (held.isEmpty() || !PerkGearRules.itemBanDiscards(state, held)) {
			return 0;
		}
		ItemStack taken = held.copy();
		player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		banish(player, state, taken,
				"금지된 장비를 왼손에 들 수 없어 인벤토리로 옮겼습니다",
				"금지된 장비를 옮길 자리가 없어 버렸습니다");
		return 1;
	}

	/**
	 * 핫바·장착 칸에서 치운 금지 아이템을 보관 칸으로 올려 보낸다. 자리가 없으면 떨어뜨린다.
	 *
	 * <p>일부만 들어가는 경우도 있다. 옮긴 몫과 버린 몫이 함께 생기므로 두 알림이 같이 나갈 수
	 * 있고, 그게 실제로 일어난 일이다. 두 알림은 종류가 다르므로 쿨다운도 따로 돈다 — 방금
	 * 「옮겼습니다」가 나갔다는 이유로 「버렸습니다」가 삼켜지지 않는다.
	 *
	 * <p>옮기고 버리는 일 자체는 언제나 한다. 재우는 것은 말뿐이다.
	 */
	private static void banish(ServerPlayer player, TeamState state, ItemStack stack,
			String movedReason, String droppedReason) {
		if (stack.isEmpty()) {
			return;
		}
		int before = stack.getCount();
		pushToStorage(state, stack, ExpandedInventoryManager.enabled());
		if (stack.getCount() < before) {
			notify(player, GearNoticeCooldown.Kind.RELOCATED, movedReason);
		}
		if (!stack.isEmpty()) {
			discard(player, stack, droppedReason);
		}
	}

	/** 아이템을 발밑에 떨어뜨리고 알린다. 공유 인벤토리로 돌아가지 않으므로 실제로 잃는다. */
	private static void discard(ServerPlayer player, ItemStack stack, String reason) {
		if (stack.isEmpty()) {
			return;
		}
		// 떨어뜨리는 일은 쿨다운을 보지 않는다. 알림만 재운다.
		player.drop(stack, true, false);
		notify(player, GearNoticeCooldown.Kind.DROPPED, reason);
	}

	// ------------------------------------------------------------------ 알리기

	/** {@link #notify(ServerPlayer, GearNoticeCooldown.Kind, String, String)} 의 꼬리 없는 판. */
	private static void notify(ServerPlayer player, GearNoticeCooldown.Kind kind, String reason) {
		notify(player, kind, reason, "");
	}

	/**
	 * 쿨다운을 지키며 한 줄 알린다. 재우는 중이면 조용히 넘어간다.
	 *
	 * <p><b>이 메서드를 부르기 전에 동작은 이미 끝나 있어야 한다.</b> 여기서 거짓이 나온다고
	 * 되돌릴 것은 아무것도 없다. 알림을 재우는 것과 제한을 푸는 것은 전혀 다른 일이다.
	 *
	 * @param reason 마침표 없이 적은 사유. 여기서 "[증강] " 과 마침표를 붙인다
	 * @param tail 마침표 뒤에 덧붙일 말. 없으면 빈 문자열
	 */
	private static void notify(ServerPlayer player, GearNoticeCooldown.Kind kind,
			String reason, String tail) {
		if (!NOTICES.claim(player.getUUID(), kind, now)) {
			return;
		}
		player.sendSystemMessage(Component.literal("[증강] " + reason + "." + tail));
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
	 * <p>일단 넘침 목록에 얹고 {@link TeamState#restoreOverflow} 를 부른다.
	 * 바닥에 떨어뜨리는 길은 없다. 공유 인벤토리 밖으로 새어 나가면 다른 팀원에게는 보이지 않는
	 * 아이템이 생긴다.
	 *
	 * <p>이쪽 알림도 재운다. 다만 종류는 따로 두어 밀어냄·버림 알림과 서로를 삼키지 않게 했다.
	 */
	private static void stow(ServerPlayer player, TeamState state, ItemStack stack, String reason) {
		if (stack.isEmpty()) {
			return;
		}
		boolean leftover = deliver(state, stack);
		notify(player, GearNoticeCooldown.Kind.STOWED, reason,
				leftover ? " 자리가 없어 대기열로 갔습니다. 칸을 비우면 자동으로 들어옵니다." : "");
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

	// ------------------------------------------------------------------ 보관 칸으로만 밀어 넣기

	/**
	 * 아이템을 <b>핫바를 뺀 보관 칸</b>에만 밀어 넣는다. 테스트가 직접 부른다.
	 *
	 * <p>{@link #deliver} 는 빈 칸을 0번부터 찾는데 공유 목록의 0~8번이 곧 핫바라, 핫바에서
	 * 치운 아이템을 그 길로 보내면 제자리로 돌아온다.
	 * 여기서는 {@link Inventory#getSelectionSize()} 번째 칸부터만 본다.
	 *
	 * <h2>확장 칸도 「템창」이다</h2>
	 * <p>이 모드는 {@link ExpandedInventoryManager} 로 27칸을 더 붙일 수 있고, 그 칸은 창
	 * 오른쪽이 아니라 <b>인벤토리 세 줄 바로 아래</b>에 그려진다. 플레이어에게는 그냥 인벤토리
	 * 여섯 줄이다. 그래서 "템창까지 꽉 찼는가"를 따질 때 이 칸을 빼면 아직 빈 칸을 눈앞에 두고
	 * 아이템이 버려진다. 켜져 있으면 함께 센다.
	 *
	 * <p>넘침 대기열({@link TeamState#overflowItems})에는 넣지 않는다. 대기열은 칸이 비는 대로
	 * 0번부터 다시 채우는 곳이라 결국 핫바로 돌아오기 때문이다.
	 *
	 * @param stack 밀어 넣을 묶음. <b>들어간 만큼 제자리에서 깎인다.</b> 다 들어가면 빈 묶음이 된다
	 * @param includeExtra 확장 27칸도 보관 칸으로 셀 것인가
	 * @return 실제로 옮긴 개수
	 */
	static int pushToStorage(TeamState state, ItemStack stack, boolean includeExtra) {
		if (stack.isEmpty()) {
			return 0;
		}
		int before = stack.getCount();
		insertInto(state.mainItems, stack, Inventory.getSelectionSize(), state.mainItems.size());
		if (includeExtra) {
			// 추가 칸은 「열린 만큼만」이다. 잠긴 칸은 화면 밖이라 넣으면 꺼낼 수 없다.
			insertInto(state.extraItems, stack, 0, PerkInventorySlots.unlockedFor(state));
		}
		return before - stack.getCount();
	}

	/**
	 * 한 목록의 {@code from} 번 칸부터 같은 아이템에 합치고, 그래도 남으면 빈 칸에 넣는다.
	 *
	 * <p>{@code TeamState.restoreOverflow} 가 쓰는 규칙과 같되 시작 칸과 한도를 고를 수 있게 한
	 * 판이다.
	 *
	 * @param limit 이 칸 번호 <b>앞까지만</b> 쓴다. 추가 칸의 잠긴 자리를 건너뛰기 위한 한도다
	 */
	private static void insertInto(SharedItemList items, ItemStack stack, int from, int limit) {
		int end = Math.max(0, Math.min(limit, items.size()));
		for (int slot = from; slot < end && !stack.isEmpty(); slot++) {
			ItemStack existing = items.get(slot);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
				continue;
			}
			int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
			if (moved > 0) {
				existing.grow(moved);
				stack.shrink(moved);
			}
		}
		for (int slot = from; slot < end && !stack.isEmpty(); slot++) {
			if (items.get(slot).isEmpty()) {
				int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
				items.set(slot, stack.copyWithCount(moved));
				stack.shrink(moved);
			}
		}
	}
}
