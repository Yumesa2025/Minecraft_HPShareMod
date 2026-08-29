package com.sharedfate.sync;

import com.sharedfate.sync.VictoryCelebration.Schedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VictoryCelebrationTest {

	@Test
	void 처치_오초뒤_타이틀_다시_오초뒤_폭죽을_정확히_한번씩_낸다() {
		Schedule schedule = new Schedule();
		schedule.start(VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS,
				VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS);

		for (int tick = 1; tick < VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS; tick++) {
			assertSame(Schedule.Step.NONE, schedule.advance(), tick + "틱째에는 아직 조용해야 한다");
		}
		assertSame(Schedule.Step.TITLE, schedule.advance(), "100틱(5초)째에 타이틀이 떠야 한다");

		for (int tick = 1; tick < VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS; tick++) {
			assertSame(Schedule.Step.NONE, schedule.advance());
		}
		assertSame(Schedule.Step.FIREWORK, schedule.advance(), "타이틀 100틱 뒤에 폭죽이 터져야 한다");
	}

	@Test
	void 연출이_끝나면_아무리_틱을_돌려도_반복되지_않는다() {
		Schedule schedule = new Schedule();
		schedule.start(2, 2);

		assertSame(Schedule.Step.NONE, schedule.advance());
		assertSame(Schedule.Step.TITLE, schedule.advance());
		assertSame(Schedule.Step.NONE, schedule.advance());
		assertSame(Schedule.Step.FIREWORK, schedule.advance());

		assertFalse(schedule.isRunning(), "두 단계가 끝나면 예약이 남아 있으면 안 된다");
		for (int tick = 0; tick < 1000; tick++) {
			assertSame(Schedule.Step.NONE, schedule.advance());
		}
	}

	@Test
	void 시작하지_않은_예약은_아무것도_하지_않는다() {
		Schedule schedule = new Schedule();

		assertFalse(schedule.isRunning());
		assertSame(Schedule.Step.NONE, schedule.advance());
	}

	@Test
	void 취소하면_남은_단계가_사라진다() {
		Schedule schedule = new Schedule();
		schedule.start(100, 100);
		assertTrue(schedule.isRunning());

		schedule.cancel();

		assertFalse(schedule.isRunning());
		assertSame(Schedule.Step.NONE, schedule.advance());
	}

	@Test
	void 지연이_영이하여도_다음틱에_한번씩만_진행한다() {
		Schedule schedule = new Schedule();
		schedule.start(0, -5);

		assertSame(Schedule.Step.TITLE, schedule.advance());
		assertSame(Schedule.Step.FIREWORK, schedule.advance());
		assertSame(Schedule.Step.NONE, schedule.advance());
	}

	@Test
	void 기본_지연은_각각_오초다() {
		assertEquals(100, VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS);
		assertEquals(100, VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS);
	}
}
