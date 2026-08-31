package com.sharedfate.sync;

import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「난이도 상승」의 계산부.
 *
 * <p>실제로 몹에게 속성을 붙이는 부분은 월드가 있어야 하므로 여기서 닿지 않는다. 대신 단계
 * 계산·상한·표시 문구처럼 <b>규칙 그 자체</b>를 못박아 둔다. 이 숫자들이 조용히 바뀌면
 * 회차의 체감이 통째로 달라진다.
 */
class DifficultyEscalationTest {

	@Test
	void 삼십분에_한_단계씩_오른다() {
		assertEquals(0, DifficultyEscalation.stepsFor(0));
		assertEquals(0, DifficultyEscalation.stepsFor(DifficultyEscalation.STEP_TICKS - 1));
		assertEquals(1, DifficultyEscalation.stepsFor(DifficultyEscalation.STEP_TICKS));
		assertEquals(2, DifficultyEscalation.stepsFor(DifficultyEscalation.STEP_TICKS * 2));
	}

	@Test
	void 한_단계는_삼십분이다() {
		assertEquals(30, DifficultyEscalation.STEP_MINUTES);
		assertEquals(30 * 60 * 20, DifficultyEscalation.STEP_TICKS);
	}

	@Test
	void 배율은_복리가_아니라_덧셈이다() {
		// 복리라면 1.04^2 = 1.0816 이 되어야 한다. 덧셈이므로 1.08 이다.
		assertEquals(1.08, DifficultyEscalation.multiplierForSteps(2), 1.0e-9);
		assertEquals(1.4, DifficultyEscalation.multiplierForSteps(10), 1.0e-9);
	}

	@Test
	void 한_시간이면_팔_퍼센트다() {
		assertEquals(8, DifficultyEscalation.percentFor(DifficultyEscalation.STEP_TICKS * 2));
		assertEquals(0, DifficultyEscalation.percentFor(0));
	}

	@Test
	void 두_배에서_멈춘다() {
		assertEquals(2.0, DifficultyEscalation.multiplierForSteps(DifficultyEscalation.MAX_STEPS),
				1.0e-9);
		assertEquals(2.0, DifficultyEscalation.multiplierForSteps(9999), 1.0e-9);
		assertEquals(100, DifficultyEscalation.percentFor(Integer.MAX_VALUE));
	}

	@Test
	void 상한에_닿는_데는_열두시간_반이_걸린다() {
		int minutes = DifficultyEscalation.MAX_STEPS * DifficultyEscalation.STEP_MINUTES;

		assertEquals(750, minutes, "25단계 × 30분");
		assertEquals(minutes * 60 * 20, DifficultyEscalation.MAX_ELAPSED_TICKS);
	}

	@Test
	void 음수나_이상한_값은_배율이_1_이다() {
		assertEquals(0, DifficultyEscalation.stepsFor(-1));
		assertEquals(1.0, DifficultyEscalation.multiplierFor(-1), 1.0e-9);
		assertEquals(1.0, DifficultyEscalation.multiplierForSteps(-3), 1.0e-9);
	}

	@Test
	void 다음_단계까지_남은_시간을_센다() {
		assertEquals(DifficultyEscalation.STEP_TICKS, DifficultyEscalation.ticksToNextStep(0));
		assertEquals(1, DifficultyEscalation.ticksToNextStep(DifficultyEscalation.STEP_TICKS - 1));
		assertEquals(-1, DifficultyEscalation.ticksToNextStep(DifficultyEscalation.MAX_ELAPSED_TICKS),
				"상한에서는 다음 단계가 없다");
	}

	@Test
	void 꺼_둔_팀은_그냥_끔이라고_보여_준다() {
		TeamState state = TeamState.fresh(20.0F);

		assertEquals("끔", DifficultyEscalation.describe(state));
	}

	@Test
	void 켜_둔_팀은_지금_몇_퍼센트인지_보여_준다() {
		TeamState state = TeamState.fresh(20.0F);
		state.difficultyEscalationEnabled = true;
		state.difficultyElapsedTicks = DifficultyEscalation.STEP_TICKS * 3;

		String text = DifficultyEscalation.describe(state);

		assertTrue(text.startsWith("켬"), text);
		assertTrue(text.contains("+12%"), text);
		assertTrue(text.contains("다음 상승까지 약 30분"), text);
	}

	@Test
	void 상한에_닿으면_상한이라고_보여_준다() {
		TeamState state = TeamState.fresh(20.0F);
		state.difficultyEscalationEnabled = true;
		state.difficultyElapsedTicks = DifficultyEscalation.MAX_ELAPSED_TICKS;

		String text = DifficultyEscalation.describe(state);

		assertTrue(text.contains("+100%"), text);
		assertTrue(text.contains("상한"), text);
	}
}
