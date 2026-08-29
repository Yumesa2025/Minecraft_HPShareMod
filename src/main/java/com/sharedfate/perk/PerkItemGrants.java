package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.ItemGrantEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code item_grant} 증강의 즉시 지급을 실행한다.
 *
 * <p><b>부르는 곳은 {@link PerkManager#applyChoice} 하나뿐이다.</b> 증강을 고르는 순간에만
 * 지나가는 경로라 지급이 정확히 한 번 일어난다. {@link PerkEffect#apply}는 접속·부활·효과
 * 갱신 때마다 다시 불리므로 그쪽에서는 절대 부르면 안 된다.
 *
 * <p>아이템은 개인 인벤토리가 아니라 팀 공유 목록({@link TeamState#mainItems},
 * {@link TeamState#extraItems})에 넣는다. 이 모드는 접속할 때
 * {@code InventorySwapper.finishJoin} 이 팀원의 {@code Inventory.items} 를 통째로
 * {@code state.mainItems} 로 바꿔 끼운다. 즉 공유 목록이 곧 팀원 모두의 인벤토리다.
 * 공유 목록에 넣으면 온라인 팀원 전원의 화면에 똑같이 보이고, 접속 중이 아닌 팀원도 나중에
 * 그대로 받는다. 반대로 개인 인벤토리 API로 넣으면 팀에 붙지 않은 순간(교체 전후)에
 * 아이템이 공유 목록 밖으로 새어 나갈 수 있다.
 *
 * <p>자리가 없으면 바닥에 떨어뜨리지 않고 {@link TeamState#overflowItems} 에 남긴다.
 * 넘침 목록은 {@code TeamManager.markDirtyIfActive} 가 공유 칸이 빌 때마다 다시 밀어 넣어 주고
 * 월드와 함께 저장되므로, 인벤토리를 정리하기만 하면 잃어버리지 않고 돌려받는다.
 */
public final class PerkItemGrants {
	private PerkItemGrants() {
	}

	/**
	 * 증강 하나가 가진 {@code item_grant} 효과를 모두 실행한다.
	 *
	 * @return 실제로 지급한 아이템 묶음 수. 줄 것이 없었으면 0
	 */
	public static int grantOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk) {
		if (state == null || perk == null) {
			return 0;
		}

		List<ItemStack> granted = collect(perk);
		if (granted.isEmpty()) {
			return 0;
		}

		int leftover = deliver(state, granted);
		SharedFateMod.LOGGER.info("[PERK] 증강 {} 지급 묶음={} 넘침={}",
				perk.id(), granted.size(), leftover);

		if (server != null && team != null) {
			refreshScreens(server, team);
			if (leftover > 0) {
				notifyOverflow(server, team, leftover);
			}
		}
		return granted.size();
	}

	/** 이 증강이 이번 한 번에 줄 아이템 묶음들. */
	private static List<ItemStack> collect(Perk perk) {
		List<ItemStack> granted = new ArrayList<>();
		for (PerkEffect effect : perk.effects()) {
			if (!(effect instanceof ItemGrantEffect grant)) {
				continue;
			}
			try {
				for (ItemStack stack : grant.grantStacks()) {
					if (!stack.isEmpty()) {
						granted.add(stack);
					}
				}
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("증강 '{}' 의 아이템 지급에 실패했습니다.", perk.id(), error);
			}
		}
		return granted;
	}

	/**
	 * 공유 목록에 밀어 넣는다.
	 *
	 * <p>일단 넘침 목록에 얹고 {@link TeamState#restoreOverflow}를 부른다. 빈 칸 찾기와
	 * 같은 아이템 합치기 규칙을 이 모드가 이미 한 곳에 갖고 있으므로 그대로 쓴다.
	 *
	 * @return 이번에 준 것 중 자리가 없어 넘침 목록에 남은 묶음 수
	 */
	private static int deliver(TeamState state, List<ItemStack> granted) {
		state.overflowItems.addAll(granted);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		state.overflowItems.removeIf(ItemStack::isEmpty);

		// 원래 있던 넘침까지 세면 안 되므로, 이번에 넣은 그 객체가 남았는지만 본다.
		// restoreOverflow 는 묶음을 새로 만들지 않고 제자리에서 깎으므로 동일성 비교가 성립한다.
		int leftover = 0;
		for (ItemStack stack : granted) {
			for (ItemStack pending : state.overflowItems) {
				if (pending == stack) {
					leftover++;
					break;
				}
			}
		}
		return leftover;
	}

	/** 공유 목록을 직접 고쳤으니 접속 중인 팀원의 화면을 맞춰 준다. */
	private static void refreshScreens(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online == null || online.containerMenu == null) {
				continue;
			}
			online.containerMenu.broadcastChanges();
		}
	}

	private static void notifyOverflow(MinecraftServer server, ShareTeam team, int leftover) {
		Component message = Component.literal(
				"[증강] 공유 인벤토리에 자리가 없어 " + leftover
						+ "묶음이 대기열로 갔습니다. 칸을 비우면 자동으로 들어옵니다.");
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null) {
				online.sendSystemMessage(message);
			}
		}
	}
}
