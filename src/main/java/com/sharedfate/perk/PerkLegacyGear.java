package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.LegacyGearEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.SharedItemList;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code legacy_gear} 증강(프리즘 「유산」)의 몰수와, 전멸 시점 승계분 스냅샷을 실행한다.
 *
 * <h2>몰수(고르는 순간)와 승계(전멸하는 순간)는 서로 다른 시점이다</h2>
 * <p>고른 즉시 {@link #sacrificeOnChoice}가 그 순간 가진 도구·무기·방어구를 전부 없앤다 —
 * 이것이 이 증강의 대가다. 하지만 <b>다음 회차로 넘어가는 물건은 이 몰수분이 아니다.</b>
 * 몰수 이후에 팀이 새로 갖춘 장비도 승계 대상이어야 하므로, "무엇이 넘어가는가"는
 * {@link #onDeath}가 <b>전멸하는 그 순간</b>에 다시 스냅샷을 떠서 정한다. 몰수는 물건을
 * 없애는 일만 하고 {@link TeamState#legacyGear}는 건드리지 않는다.
 *
 * <h2>몰수 — 어디를 훑는가</h2>
 * <ul>
 *   <li>{@link TeamState#mainItems}·{@link TeamState#extraItems}·공유 엔더상자 — {@link
 *       LegacyGearEffect#matcher()}(태그 {@code sharedfate:legacy_gear})에 걸리는 스택을
 *       전부 비운다. 손에 쥔 도구도 여기 걸린다 — 마인핸드 선택 칸이 {@code mainItems} 안에
 *       있기 때문이다({@code TeamAwareEquipment}가 오프핸드부터 위쪽 슬롯만 공유 장비고 마인핸드는
 *       바닐라 인벤토리 그대로 두는 것과 같은 이유).</li>
 *   <li>{@link TeamState#equipment}의 HEAD·CHEST·LEGS·FEET 네 칸 — 태그 판정이 아니라 직접 비운다.
 *       지금 입고 있는 방어구는 스택이 줄지어 있는 목록이 아니라 이 네 칸에만 있다.</li>
 * </ul>
 *
 * <h2>승계 — {@link #onDeath}는 왜 {@code DeathHandler}보다 먼저 등록돼야 하는가</h2>
 * <p>{@code DeathHandler.onDeath}가 팀 전멸을 처리하면서(!keepInventory 면)
 * {@code InventorySwapper.drainDeathDrops}로 공유 인벤토리를 바닥에 쏟아 비운다. 그 뒤에
 * 스냅샷을 뜨면 이미 빈 목록만 보인다. 그래서 {@code SharedFateMod}에 이 메서드를
 * {@code DeathHandler::onDeath}보다 <b>앞서</b> {@code ServerLivingEntityEvents.AFTER_DEATH}에
 * 등록해 둬야 한다({@code DeathHandler}는 다른 담당이라 그 파일은 고치지 않고 등록 순서만
 * 앞에 끼워 넣었다).
 *
 * <p>전멸 하나에 팀원 여럿이 죽는다({@code DeathHandler}가 나머지 팀원도 {@code die}를 불러
 * 연쇄시킨다). 그 각각의 죽음마다 {@code AFTER_DEATH}가 다시 발화하므로 이 메서드도 여러 번
 * 불리는데, <b>맨 처음(아직 아무것도 지워지지 않은) 호출에서만 스냅샷을 떠야 한다.</b> 이후
 * 호출은 이미 드레인된 상태를 볼 수 있어 그대로 두면 방금 뜬 정확한 스냅샷을 빈 목록으로
 * 덮어써 버린다. 이걸 막으려고 팀마다 "이번 전멸에서 이미 스냅샷을 떴는가"를 게임 시각(틱)
 * 으로 표시해 둔다({@link #LAST_CAPTURE_TICK}) — 같은 전멸의 연쇄 죽음은 전부 같은 틱 안에서
 * 동기적으로 일어나므로, 게임 시각이 다르면 새로운 전멸이라는 뜻이다.
 *
 * <h2>몰수한 것은 어디로 가는가(승계분)</h2>
 * <p>{@link TeamState#legacyGear}에 담아 둔다. {@code TeamRosterStore}가 이 목록을 회차
 * 경계 너머로 실어 날라, 다음 회차의 시작 인벤토리에 그대로 돌려준다
 * ({@code TeamManager#restoreFreshRoster}). 전멸하지 않고 회차가 끝나면(승리) 이 메서드
 * 자체가 안 불리므로 예전 값이 남아 있을 수 있는데, 쓰이는 자리가 전멸 경계뿐이라 무해하다.
 * {@link TeamState#resetAfterDeath}도 이 필드는 일부러 건드리지 않는다.
 */
public final class PerkLegacyGear {
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/**
	 * 팀마다, 이번 전멸에서 승계 스냅샷을 이미 떴는지 표시한다. 값은 그 스냅샷을 뜬 게임 시각
	 * (틱)이다. 같은 전멸의 연쇄 죽음은 전부 이 값과 같은 틱에 일어나므로, 다음 죽음이 다른
	 * 틱이면 새 전멸로 본다.
	 */
	private static final Map<UUID, Long> LAST_CAPTURE_TICK = new HashMap<>();

	private PerkLegacyGear() {
	}

	// ------------------------------------------------------------------ 몰수(고르는 순간)

	/** @return 몰수한 아이템 묶음 수. 가리키는 것이 없거나 가진 게 없었으면 0 */
	public static int sacrificeOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk) {
		if (state == null || perk == null) {
			return 0;
		}
		LegacyGearEffect effect = find(perk);
		if (effect == null) {
			return 0;
		}

		List<ItemStack> seized = new ArrayList<>();
		seizeFromList(state.mainItems, effect, seized);
		seizeFromList(state.extraItems, effect, seized);
		seizeFromEnderChest(state.enderContainer, effect, seized);
		seizeArmor(state, seized);

		if (seized.isEmpty()) {
			return 0;
		}
		// 다음 회차로 넘길 것은 이 몰수분이 아니라 전멸하는 순간의 스냅샷이다(아래 onDeath).
		// 여기서는 없애기만 한다 — 그것이 이 증강의 대가다.
		SharedFateMod.LOGGER.info("[PERK] 증강 {} 로 팀 장비 {}개를 몰수했습니다.",
				perk.id(), seized.size());

		if (server != null && team != null) {
			refreshScreens(server, team);
			Component message = Component.literal(
					"[증강] 「" + perk.name() + "」의 대가로 지금 가진 도구·무기·방어구 " + seized.size()
							+ "개를 잃었습니다. 전멸하면 그 시점에 가진 도구·무기·방어구가 다음 회차로 넘어옵니다.");
			for (UUID member : team.members()) {
				ServerPlayer online = server.getPlayerList().getPlayer(member);
				if (online != null) {
					online.sendSystemMessage(message);
				}
			}
		}
		return seized.size();
	}

	// ------------------------------------------------------------------ 승계(전멸하는 순간)

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DEATH}에 붙는 지점.
	 *
	 * <p>{@code SharedFateMod}가 이 메서드를 {@code DeathHandler::onDeath}보다 <b>먼저</b>
	 * 등록해야 한다. 클래스 문서에 이유를 적어 뒀다.
	 */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer dead)) {
			return;
		}
		MinecraftServer server = dead.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(dead.getUUID());
		TeamState state = manager.stateOf(dead.getUUID());
		if (team == null || state == null) {
			return;
		}

		long now = server.overworld().getGameTime();
		Long last = LAST_CAPTURE_TICK.get(team.teamId());
		if (last != null && last == now) {
			// 같은 전멸의 연쇄 죽음이다. 이미 이번 틱에 스냅샷을 떴다.
			return;
		}
		LAST_CAPTURE_TICK.put(team.teamId(), now);

		int captured = captureAtDeath(state);
		if (captured > 0) {
			SharedFateMod.LOGGER.info(
					"[PERK] 「유산」— 팀 {} 이(가) 전멸하는 시점의 장비 {}개를 다음 회차로 넘깁니다.",
					team.teamId(), captured);
		}
	}

	/**
	 * 이 팀이 「유산」을 가졌으면, 지금(전멸하는 이 순간) 가진 도구·무기·방어구를 스냅샷으로
	 * 남긴다. 없으면 아무것도 하지 않는다.
	 *
	 * <p>{@link TeamState#legacyGear}를 통째로 다시 채운다 — 고를 때 몰수한 것과는 무관하게,
	 * 지금 이 순간의 보유물이 유일한 기준이다. 월드 없이 시험하려고 서버 인자 없이 순수하게
	 * {@link TeamState}만 받는다.
	 *
	 * @return 스냅샷에 담긴 아이템 묶음 수. 「유산」이 없거나 가진 게 없었으면 0
	 */
	static int captureAtDeath(TeamState state) {
		LegacyGearEffect effect = find(state);
		if (effect == null) {
			return 0;
		}

		List<ItemStack> captured = new ArrayList<>();
		collectFromList(state.mainItems, effect, captured);
		collectFromList(state.extraItems, effect, captured);
		collectFromEnderChest(state.enderContainer, effect, captured);
		collectArmor(state, captured);

		state.legacyGear.clear();
		state.legacyGear.addAll(captured);
		return captured.size();
	}

	/** 이 팀이 지금 가진 증강 중 {@code legacy_gear} 효과. 없으면 null. */
	private static @Nullable LegacyGearEffect find(TeamState state) {
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			LegacyGearEffect effect = find(perk);
			if (effect != null) {
				return effect;
			}
		}
		return null;
	}

	private static @Nullable LegacyGearEffect find(Perk perk) {
		for (PerkEffect effect : perk.effects()) {
			if (effect instanceof LegacyGearEffect legacy) {
				return legacy;
			}
		}
		return null;
	}

	/** 목록을 훑어 걸리는 스택을 <b>사본으로</b> 모은다. 원본은 손대지 않는다. */
	private static void collectFromList(SharedItemList items, LegacyGearEffect effect,
			List<ItemStack> collected) {
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (effect.matcher().matches(stack)) {
				collected.add(stack.copy());
			}
		}
	}

	private static void collectFromEnderChest(PlayerEnderChestContainer container,
			LegacyGearEffect effect, List<ItemStack> collected) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (effect.matcher().matches(stack)) {
				collected.add(stack.copy());
			}
		}
	}

	private static void collectArmor(TeamState state, List<ItemStack> collected) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack worn = state.equipment.get(slot);
			if (!worn.isEmpty()) {
				collected.add(worn.copy());
			}
		}
	}

	/**
	 * 서버 종료·시험 정리용. 팀 상태는 {@code TeamManager}가 따로 관리하므로 여기서는
	 * {@link #LAST_CAPTURE_TICK} 표시만 비운다. 안 비우면 다음 서버는 게임 시각이 다시 0부터
	 * 시작하는데 예전 팀 id 가 우연히 같은 틱값으로 남아 있어, 극히 드물게 첫 전멸의 스냅샷을
	 * 건너뛸 수 있다.
	 */
	public static void reset() {
		LAST_CAPTURE_TICK.clear();
	}

	private static void seizeFromList(SharedItemList items, LegacyGearEffect effect,
			List<ItemStack> seized) {
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (effect.matcher().matches(stack)) {
				seized.add(stack.copy());
				items.set(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void seizeFromEnderChest(PlayerEnderChestContainer container,
			LegacyGearEffect effect, List<ItemStack> seized) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (effect.matcher().matches(stack)) {
				seized.add(stack.copy());
				container.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void seizeArmor(TeamState state, List<ItemStack> seized) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack worn = state.equipment.get(slot);
			if (!worn.isEmpty()) {
				seized.add(worn.copy());
				state.equipment.set(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void refreshScreens(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null && online.containerMenu != null) {
				online.containerMenu.broadcastChanges();
			}
		}
	}
}
