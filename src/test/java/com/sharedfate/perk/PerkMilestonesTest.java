package com.sharedfate.perk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkMilestonesTest {

	@Test
	void 구간에_도달하지_않으면_아무것도_없다() {
		assertTrue(PerkMilestones.newlyReached(0, 0).isEmpty());
		assertTrue(PerkMilestones.newlyReached(0, 1).isEmpty());
		assertTrue(PerkMilestones.newlyReached(0, 2).isEmpty());
	}

	@Test
	void 첫_구간은_3렙에서_발동한다() {
		assertEquals(List.of(3), PerkMilestones.newlyReached(0, 3));
	}

	@Test
	void 한_구간씩_차례로_올라간다() {
		int last = 0;
		for (int level = 3; level <= PerkMilestones.MAX; level += PerkMilestones.STEP) {
			List<Integer> reached = PerkMilestones.newlyReached(last, level);
			assertEquals(List.of(level), reached, level + "렙에서는 그 구간 하나만 나와야 한다");
			last = reached.getLast();
		}
	}

	@Test
	void 구간_사이의_레벨은_새_구간을_만들지_않는다() {
		assertEquals(List.of(), PerkMilestones.newlyReached(3, 4));
		assertEquals(List.of(), PerkMilestones.newlyReached(3, 5));
		assertEquals(List.of(6), PerkMilestones.newlyReached(3, 6));
	}

	@Test
	void 여러_구간을_건너뛰면_건너뛴_구간이_모두_쌓인다() {
		assertEquals(List.of(3, 6, 9), PerkMilestones.newlyReached(0, 9));
		assertEquals(List.of(6, 9, 12), PerkMilestones.newlyReached(3, 13));
	}

	@Test
	void 레벨이_내려갔다_다시_올라와도_재발동하지_않는다() {
		// 12렙까지 처리한 팀이 경험치를 써서 5렙으로 내려갔다가 12렙으로 돌아온 상황
		assertEquals(List.of(), PerkMilestones.newlyReached(12, 5));
		assertEquals(List.of(), PerkMilestones.newlyReached(12, 11));
		assertEquals(List.of(), PerkMilestones.newlyReached(12, 12));
		assertEquals(List.of(15), PerkMilestones.newlyReached(12, 15));
	}

	@Test
	void 마지막_구간은_36이고_그_위로는_발동하지_않는다() {
		assertEquals(List.of(36), PerkMilestones.newlyReached(33, 36));
		assertEquals(List.of(36), PerkMilestones.newlyReached(33, 37));
		assertEquals(List.of(36), PerkMilestones.newlyReached(33, 200));
		assertEquals(List.of(), PerkMilestones.newlyReached(36, 37));
		assertEquals(List.of(), PerkMilestones.newlyReached(36, 999));
	}

	@Test
	void 처음부터_한번에_최고레벨까지_가면_구간_열두개가_모두_나온다() {
		List<Integer> reached = PerkMilestones.newlyReached(0, 100);

		assertEquals(PerkMilestones.COUNT, reached.size());
		assertEquals(List.of(3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36), reached);
	}

	@Test
	void 음수_레벨은_아무것도_만들지_않는다() {
		assertEquals(List.of(), PerkMilestones.newlyReached(0, -5));
	}

	@Test
	void 손상된_마지막_구간은_안전하게_보정한다() {
		assertEquals(0, PerkMilestones.clampMilestone(-7));
		assertEquals(0, PerkMilestones.clampMilestone(0));
		assertEquals(3, PerkMilestones.clampMilestone(3));
		assertEquals(3, PerkMilestones.clampMilestone(5));
		assertEquals(PerkMilestones.MAX, PerkMilestones.clampMilestone(1000));

		// 구간 값이 3의 배수가 아니게 손상돼도 그 아래 구간부터 이어서 발동한다
		assertEquals(List.of(6, 9), PerkMilestones.newlyReached(4, 9));
	}

	@Test
	void 구간_판별() {
		assertTrue(PerkMilestones.isMilestone(3));
		assertTrue(PerkMilestones.isMilestone(36));
		assertTrue(!PerkMilestones.isMilestone(0));
		assertTrue(!PerkMilestones.isMilestone(4));
		assertTrue(!PerkMilestones.isMilestone(39));
	}

	@Test
	void 반환된_목록은_수정할_수_없다() {
		List<Integer> reached = PerkMilestones.newlyReached(0, 9);

		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> reached.add(12));
	}
}
