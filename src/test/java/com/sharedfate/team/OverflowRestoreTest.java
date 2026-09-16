package com.sharedfate.team;

import com.sharedfate.TestBootstrap;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.PerkInventorySlots;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 넘침 대기열을 인벤토리로 되돌릴 때 <b>잠긴 칸을 건너뛰는가</b>.
 *
 * <h2>왜 중요한가</h2>
 *
 * <p>추가 27칸은 언제나 만들어져 있고 팀이 연 만큼만(기본 18칸) 화면에 보인다. 잠긴 칸은
 * 화면 밖({@link ExpandedInventoryManager#HIDDEN_Y})에 있어 <b>물건이 들어가면 사라진 것처럼
 * 보이고 꺼낼 수도 없다.</b>
 *
 * <p>추가 칸에 물건을 넣는 길이 넷인데 <b>한도가 서로 달랐다.</b>
 *
 * <ul>
 *   <li>바닥에서 줍기 — {@code ExpandedInventoryContainer.openSlots()} 까지 (0.25.1-dev)</li>
 *   <li>창에서 쉬프트 클릭 — {@code ExpandedInventorySlot.mayPlace} 가 막음 (0.25.3-dev)</li>
 *   <li><b>넘침 대기열 복원 — 27칸 전체</b> ← 여기</li>
 *   <li><b>밀어낸 장비 보관 — 27칸 전체</b> ← {@code PerkGearManager.pushToStorage}</li>
 * </ul>
 *
 * <p>「보급」 세트가 10분마다 주는 아이템도 이 길로 들어온다. 채팅에는 「보급 받음」이 뜨는데
 * 인벤토리 어디에도 없고, 재접속해야 나타났다.
 *
 * <p>덤으로 {@link PerkInventorySlots#occupiedFloor} 가 그 칸을 보고 <b>짐꾼도 없는 팀에
 * 27칸을 공짜로 열어 준다.</b>
 */
class OverflowRestoreTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 넘침_복원은_잠긴_칸에_넣지_않는다() {
		TeamState state = fullTeam();
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));

		state.restoreOverflow(true);

		for (int slot = ExpandedInventoryManager.BASE_EXTRA_SIZE;
				slot < ExpandedInventoryManager.EXTRA_SIZE; slot++) {
			assertTrue(state.extraItems.get(slot).isEmpty(),
					slot + "번 잠긴 칸에 들어갔다 — 화면 밖이라 꺼낼 수 없다");
		}
	}

	/**
	 * 넣을 곳이 없으면 <b>대기열에 그대로 남아야</b> 한다.
	 *
	 * <p>여기가 비면 아이템을 정말로 잃는다. 대기열은 칸이 비는 대로 매 틱 다시 밀어 넣으므로,
	 * 남아 있기만 하면 언젠가 돌아온다.
	 */
	@Test
	void 넣을_곳이_없으면_대기열에_남는다() {
		TeamState state = fullTeam();
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));

		state.restoreOverflow(true);

		assertEquals(1, state.overflowItems.size(), "대기열에서 사라지면 아이템을 잃는다");
		assertEquals(5, state.overflowItems.getFirst().getCount());
	}

	/** 열린 칸이 비어 있으면 당연히 들어간다. 막기만 하고 넣지 못하면 더 나쁘다. */
	@Test
	void 열린_칸에는_그대로_들어간다() {
		TeamState state = TeamState.fresh(20.0F);
		fill(state.mainItems.size(), state.mainItems);
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));

		state.restoreOverflow(true);

		assertFalse(state.extraItems.get(0).isEmpty(), "열린 첫 칸에 들어가야 한다");
		assertTrue(state.overflowItems.isEmpty());
	}

	/**
	 * 이미 잠긴 칸에 물건이 있으면 그 줄은 열린 것으로 세므로 계속 쓸 수 있다.
	 *
	 * <p>{@link PerkInventorySlots#unlockedFor} 가 「물건이 든 칸까지는 반드시 연다」를 보장한다.
	 * 이것이 없으면 예전 월드에서 아래 칸에 있던 물건이 갇힌다.
	 */
	@Test
	void 이미_물건이_든_칸이_있으면_그_줄까지_쓴다() {
		TeamState state = fullTeam();
		state.extraItems.set(26, new ItemStack(Items.DIAMOND, 1));
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));

		state.restoreOverflow(true);

		assertEquals(6, state.extraItems.get(26).getCount(), "열린 것으로 세어 합쳐져야 한다");
		assertTrue(state.overflowItems.isEmpty());
	}

	/** 메인 36칸과 열린 추가 18칸이 전부 찬 팀. */
	private static TeamState fullTeam() {
		TeamState state = TeamState.fresh(20.0F);
		fill(state.mainItems.size(), state.mainItems);
		fill(ExpandedInventoryManager.BASE_EXTRA_SIZE, state.extraItems);
		return state;
	}

	private static void fill(int count, SharedItemList items) {
		for (int slot = 0; slot < count; slot++) {
			items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
	}
}
