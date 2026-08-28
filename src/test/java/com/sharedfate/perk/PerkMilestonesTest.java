package com.sharedfate.perk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkMilestonesTest {

	@Test
	void 구간_상수는_5의_배수_일곱_개다() {
		assertEquals(5, PerkMilestones.STEP);
		assertEquals(35, PerkMilestones.MAX);
		assertEquals(7, PerkMilestones.COUNT);
	}

	@Test
	void 구간에_도달하지_않으면_아무것도_없다() {
		for (int level = 0; level < PerkMilestones.STEP; level++) {
			assertTrue(PerkMilestones.newlyReached(0, level).isEmpty(), level + "렙은 아직 이르다");
		}
	}

	@Test
	void 첫_구간은_5렙에서_발동한다() {
		assertEquals(List.of(5), PerkMilestones.newlyReached(0, 5));
	}

	@Test
	void 한_구간씩_차례로_올라간다() {
		int last = 0;
		for (int level = PerkMilestones.STEP; level <= PerkMilestones.MAX; level += PerkMilestones.STEP) {
			List<Integer> reached = PerkMilestones.newlyReached(last, level);
			assertEquals(List.of(level), reached, level + "렙에서는 그 구간 하나만 나와야 한다");
			last = reached.getLast();
		}
	}

	@Test
	void 구간_사이의_레벨은_새_구간을_만들지_않는다() {
		assertEquals(List.of(), PerkMilestones.newlyReached(5, 6));
		assertEquals(List.of(), PerkMilestones.newlyReached(5, 9));
		assertEquals(List.of(10), PerkMilestones.newlyReached(5, 10));
	}

	@Test
	void 여러_구간을_건너뛰면_건너뛴_구간이_모두_쌓인다() {
		assertEquals(List.of(5, 10, 15), PerkMilestones.newlyReached(0, 15));
		assertEquals(List.of(10, 15, 20), PerkMilestones.newlyReached(5, 22));
	}

	@Test
	void 레벨이_내려갔다_다시_올라와도_재발동하지_않는다() {
		// 20렙까지 처리한 팀이 경험치를 써서 8렙으로 내려갔다가 20렙으로 돌아온 상황
		assertEquals(List.of(), PerkMilestones.newlyReached(20, 8));
		assertEquals(List.of(), PerkMilestones.newlyReached(20, 19));
		assertEquals(List.of(), PerkMilestones.newlyReached(20, 20));
		assertEquals(List.of(25), PerkMilestones.newlyReached(20, 25));
	}

	@Test
	void 마지막_구간은_35이고_그_위로는_발동하지_않는다() {
		assertEquals(List.of(35), PerkMilestones.newlyReached(30, 35));
		assertEquals(List.of(35), PerkMilestones.newlyReached(30, 36));
		assertEquals(List.of(35), PerkMilestones.newlyReached(30, 200));
		assertEquals(List.of(), PerkMilestones.newlyReached(35, 36));
		assertEquals(List.of(), PerkMilestones.newlyReached(35, 999));
	}

	@Test
	void 처음부터_한번에_최고레벨까지_가면_구간_일곱개가_모두_나온다() {
		List<Integer> reached = PerkMilestones.newlyReached(0, 100);

		assertEquals(PerkMilestones.COUNT, reached.size());
		assertEquals(List.of(5, 10, 15, 20, 25, 30, 35), reached);
	}

	@Test
	void 플레_구간은_전체에서_한_번뿐이다() {
		List<Integer> reached = PerkMilestones.newlyReached(0, 100);

		assertEquals(1, reached.stream().filter(m -> m == PerkDraft.PLATINUM_MILESTONE).count());
		assertTrue(reached.contains(PerkDraft.PLATINUM_MILESTONE), "15렙 구간이 실제로 발동해야 한다");
	}

	@Test
	void 음수_레벨은_아무것도_만들지_않는다() {
		assertEquals(List.of(), PerkMilestones.newlyReached(0, -5));
	}

	@Test
	void 손상된_마지막_구간은_안전하게_보정한다() {
		assertEquals(0, PerkMilestones.clampMilestone(-7));
		assertEquals(0, PerkMilestones.clampMilestone(0));
		assertEquals(5, PerkMilestones.clampMilestone(5));
		assertEquals(5, PerkMilestones.clampMilestone(9));
		assertEquals(PerkMilestones.MAX, PerkMilestones.clampMilestone(1000));

		// 5의 배수가 아니게 손상돼도 그 아래 구간부터 이어서 발동한다
		assertEquals(List.of(10, 15), PerkMilestones.newlyReached(7, 15));
	}

	@Test
	void 구간이_3의_배수이던_시절의_저장값도_안전하다() {
		// 구 데이터는 3·6·…·36 로 저장돼 있다. 내림 보정이라 이미 받은 구간을 다시 주지 않는다.
		assertEquals(0, PerkMilestones.clampMilestone(3));
		assertEquals(5, PerkMilestones.clampMilestone(6));
		assertEquals(5, PerkMilestones.clampMilestone(9));
		assertEquals(10, PerkMilestones.clampMilestone(12));
		assertEquals(30, PerkMilestones.clampMilestone(33));
		assertEquals(PerkMilestones.MAX, PerkMilestones.clampMilestone(36));

		// 12까지 처리한 구 데이터가 20렙으로 넘어오면 15·20 두 개만 나온다
		assertEquals(List.of(15, 20), PerkMilestones.newlyReached(12, 20));
		// 구 최고구간 36까지 처리한 팀은 더 받지 않는다
		assertEquals(List.of(), PerkMilestones.newlyReached(36, 999));
	}

	@Test
	void 구간_판별() {
		assertTrue(PerkMilestones.isMilestone(5));
		assertTrue(PerkMilestones.isMilestone(15));
		assertTrue(PerkMilestones.isMilestone(35));
		assertFalse(PerkMilestones.isMilestone(0));
		assertFalse(PerkMilestones.isMilestone(3));
		assertFalse(PerkMilestones.isMilestone(6));
		assertFalse(PerkMilestones.isMilestone(36));
		assertFalse(PerkMilestones.isMilestone(40));
	}

	@Test
	void 반환된_목록은_수정할_수_없다() {
		List<Integer> reached = PerkMilestones.newlyReached(0, 15);

		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> reached.add(20));
	}
}
