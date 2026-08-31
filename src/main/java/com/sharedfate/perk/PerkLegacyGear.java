package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.LegacyGearEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.SharedItemList;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code legacy_gear} 증강(프리즘 「유산」)의 몰수를 실행한다.
 *
 * <p><b>부르는 곳은 {@link PerkManager#applyChoice}(정확히는 그 안의 {@code commit}) 하나뿐이다.</b>
 * {@link PerkItemGrants#grantOnChoice}와 같은 자리, 같은 시점, 반대 방향이다. 증강은 한 회차에
 * 한 번만 고를 수 있으므로 이 자리를 지나는 것도 증강마다 한 번뿐이다.
 *
 * <h2>어디를 훑는가</h2>
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
 * <h2>몰수한 것은 어디로 가는가</h2>
 * <p>버리지 않고 {@link TeamState#legacyGear}에 쌓아 둔다. 팀이 훗날 전멸하면 {@code
 * TeamRosterStore}가 이 목록을 회차 경계 너머로 실어 날라, 다음 회차의 시작 인벤토리에
 * 그대로 돌려준다({@code TeamManager#restoreFreshRoster}). 전멸하지 않고 회차가 끝나면(승리)
 * 이 목록은 그냥 쓰이지 않고 남는다 — 감수한 위험이 실현되지 않았을 뿐 손실이 두 번 나는
 * 것은 아니다. {@link TeamState#resetAfterDeath}도 이 필드는 일부러 건드리지 않는다.
 */
public final class PerkLegacyGear {
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private PerkLegacyGear() {
	}

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
		state.legacyGear.addAll(seized);
		SharedFateMod.LOGGER.info("[PERK] 증강 {} 로 팀 장비 {}개를 몰수해 다음 회차로 넘깁니다.",
				perk.id(), seized.size());

		if (server != null && team != null) {
			refreshScreens(server, team);
			Component message = Component.literal(
					"[증강] 「" + perk.name() + "」의 대가로 지금 가진 도구·무기·방어구 " + seized.size()
							+ "개를 잃었습니다. 전멸하면 다음 회차 시작 인벤토리로 돌아옵니다.");
			for (UUID member : team.members()) {
				ServerPlayer online = server.getPlayerList().getPlayer(member);
				if (online != null) {
					online.sendSystemMessage(message);
				}
			}
		}
		return seized.size();
	}

	private static @Nullable LegacyGearEffect find(Perk perk) {
		for (PerkEffect effect : perk.effects()) {
			if (effect instanceof LegacyGearEffect legacy) {
				return legacy;
			}
		}
		return null;
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
