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
 * ({@code inventory_slots})이 <b>한 줄을 통째로</b> 더 연다. 짐꾼이 이미 마지막 줄을 열기
 * 때문에 「가호 3」의 강화는 칸 수를 더 늘리지 못한다.
 *
 * <table border="1">
 *   <caption>실제로 보이는 칸</caption>
 *   <tr><th>상태</th><th>추가 칸</th><th>전체</th></tr>
 *   <tr><td>기본</td><td>18</td><td>54 (9×6)</td></tr>
 *   <tr><td>짐꾼</td><td>27</td><td>63 (9×7)</td></tr>
 * </table>
 *
 * <h2>⚠ 언제나 줄 단위다</h2>
 * <p>결과를 반드시 <b>9의 배수</b>로 올린다. 이것이 없으면 창이 두 가지로 깨진다.
 *
 * <ul>
 *   <li><b>반쪽 줄</b> — 24칸이면 셋째 줄에 여섯 칸만 놓이고 오른쪽 세 칸은 배경만 남는다.
 *       칸 배경을 그리는 쪽({@code ContainerScreenMixin})은 줄 단위로 올림해 그리는데 칸을
 *       놓는 쪽은 개수대로 놓기 때문이다.</li>
 *   <li><b>창이 오르내림</b> — 아래 칸의 물건을 꺼내는 것만으로 {@link #occupiedFloor} 가
 *       21 에서 18 로 떨어져 창 높이가 한 줄 접혔다 펴진다. 줄 단위로 올리면 18 과 27 두
 *       모습밖에 없어 그런 일이 생기지 않는다.</li>
 * </ul>
 *
 * <h2>⚠ 칸은 절대 줄어들지 않는다</h2>
 * <p>「환골탈태」로 짐꾼을 잃으면 계산상 칸이 준다. 그런데 <b>줄어든 자리에 물건이 있으면 갈
 * 곳을 잃는다</b> — 화면에서 사라지고 꺼낼 수도 없다.
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
		int granted = ceilToRow(ExpandedInventoryManager.BASE_EXTRA_SIZE + bonusOf(state));
		int floor = ceilToRow(occupiedFloor(state));
		return clamp(Math.max(granted, floor));
	}

	/**
	 * 칸 수를 <b>줄 단위로 올린다.</b> 20 이면 27 이 된다.
	 *
	 * <p>여기가 창이 반듯한지를 혼자 책임진다. 증강 정의가 9의 배수가 아닌 값을 적어도, 물건이
	 * 줄 가운데까지만 차 있어도, 밖으로 나가는 값은 언제나 온전한 줄이다.
	 */
	static int ceilToRow(int slots) {
		int columns = ExpandedInventoryManager.EXTRA_COLUMNS;
		return ((Math.max(0, slots) + columns - 1) / columns) * columns;
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
