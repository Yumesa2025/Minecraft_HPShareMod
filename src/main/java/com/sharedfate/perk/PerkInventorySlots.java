package com.sharedfate.perk;

import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.perk.effect.InventorySlotsEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 팀 공유 인벤토리에서 지금 열려 있는 추가 칸 수.
 *
 * <p>기본은 {@link ExpandedInventoryManager#BASE_EXTRA_SIZE} 두 줄이고, 실버 「짐꾼」
 * ({@code inventory_slots})이 더 연다. 「가호 3」이 켜지면 짐꾼이 여는 칸이 늘어난다.
 *
 * <table border="1">
 *   <caption>실제로 보이는 칸</caption>
 *   <tr><th>상태</th><th>추가 칸</th><th>전체</th></tr>
 *   <tr><td>기본</td><td>18</td><td>54 (9×6)</td></tr>
 *   <tr><td>짐꾼</td><td>24</td><td>60</td></tr>
 *   <tr><td>짐꾼 + 가호 3</td><td>27</td><td>63 (9×7)</td></tr>
 * </table>
 *
 * <h2>⚠ 칸은 절대 줄어들지 않는다</h2>
 * <p>「가호 4」로 넘어가 강화가 사라지거나 「환골탈태」로 짐꾼을 잃으면 계산상 칸이 준다.
 * 그런데 <b>줄어든 자리에 물건이 있으면 갈 곳을 잃는다</b> — 화면에서 사라지고 꺼낼 수도 없다.
 *
 * <p>그래서 {@link #unlockedFor} 는 계산값과 <b>물건이 들어 있는 마지막 칸</b> 중 큰 쪽을
 * 돌려준다. 저장해 두는 값이 아니라 <b>매번 다시 세는 파생 상태</b>라, 서버를 껐다 켜도
 * 회차가 넘어가도 스스로 맞는다. 세트 판정이 {@code ownedPerks} 를 매번 다시 세는 것과 같은
 * 방식이다.
 *
 * <p>칸이 비면 그때는 줄어도 된다 — 잃을 물건이 없다.
 */
public final class PerkInventorySlots {

	private PerkInventorySlots() {
	}

	/**
	 * 이 팀에 지금 열려 있는 추가 칸 수.
	 *
	 * <p>팀이 없거나 증강을 안 쓰면 기본값이다. 물건이 들어 있는 칸은 언제나 열려 있다.
	 */
	public static int unlockedFor(@Nullable TeamState state) {
		int granted = ExpandedInventoryManager.BASE_EXTRA_SIZE + bonusOf(state);
		int floor = occupiedFloor(state);
		return clamp(Math.max(granted, floor));
	}

	/** 증강이 열어 주는 칸. 없으면 0. */
	static int bonusOf(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return 0;
		}
		int best = 0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			// 「가호 3」이 켜져 있으면 강화값을 쓴다. 4단계로 넘어가면 강화가 사라지지만,
			// 그때 칸이 줄지 않는 것은 occupiedFloor 가 막는다.
			boolean amplified = PerkBlessingSet.modeFor(state, perkId) == HolderEffect.HolderMode.AMPLIFIED;
			int value = InventorySlotsEffect.bonusOf(perk.effects(), amplified);
			if (value > best) {
				best = value;
			}
		}
		return best;
	}

	/**
	 * 물건이 들어 있는 마지막 칸의 다음 자리. 전부 비어 있으면 0.
	 *
	 * <p>이 값 아래로는 절대 잠그지 않는다.
	 */
	static int occupiedFloor(@Nullable TeamState state) {
		if (state == null || state.extraItems == null) {
			return 0;
		}
		int size = Math.min(state.extraItems.size(), ExpandedInventoryManager.EXTRA_SIZE);
		for (int index = size - 1; index >= 0; index--) {
			ItemStack stack = state.extraItems.get(index);
			if (stack != null && !stack.isEmpty()) {
				return index + 1;
			}
		}
		return 0;
	}

	/** 계산 결과를 실제로 존재하는 칸 범위로 접는다. */
	static int clamp(int unlocked) {
		return Math.max(0, Math.min(ExpandedInventoryManager.EXTRA_SIZE, unlocked));
	}
}
