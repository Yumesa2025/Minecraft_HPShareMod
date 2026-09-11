package com.sharedfate.sync;

import com.sharedfate.sync.DamageLedger.VictorySummary;
import com.sharedfate.sync.VictoryCelebration.Card;
import com.sharedfate.sync.VictoryCelebration.Schedule;
import com.sharedfate.sync.VictoryCelebration.Schedule.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VictoryCelebrationTest {

	/** 장 번호만 뽑아 본다. 폭죽은 따로 세므로 여기서는 관심이 없다. */
	private static int cardAt(Schedule schedule) {
		return schedule.advance().cardIndex();
	}

	@Test
	void 첫_장은_정한_지연이_지난_뒤에_뜬다() {
		Schedule schedule = new Schedule();
		schedule.start(VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS,
				VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS, 3);

		for (int tick = 1; tick < VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS; tick++) {
			assertEquals(Schedule.NO_CARD, cardAt(schedule), tick + "틱째에는 아직 글이 없어야 한다");
		}
		assertEquals(0, cardAt(schedule), "100틱(5초)째에 첫 장이 떠야 한다");
	}

	@Test
	void 장은_간격마다_차례대로_한_장씩_뜬다() {
		Schedule schedule = new Schedule();
		schedule.start(10, 20, 3);

		assertEquals(0, drainUntilCard(schedule, 10));
		assertEquals(1, drainUntilCard(schedule, 20));
		assertEquals(2, drainUntilCard(schedule, 20));
	}

	/**
	 * 마지막 장이 지나간 뒤에도 간격 하나만큼 더 돌고 멎는다.
	 *
	 * <p>그 여운 동안 폭죽이 계속 터져야 맺음말이 화면에 떠 있는 채로 끝난다. 마지막 장에서
	 * 곧바로 멎으면 글이 뜨자마자 하늘이 조용해진다.
	 */
	@Test
	void 마지막_장_뒤에_여운이_한_간격_남았다가_멎는다() {
		Schedule schedule = new Schedule();
		schedule.start(1, 20, 2);

		assertEquals(0, cardAt(schedule));
		assertEquals(1, drainUntilCard(schedule, 20));
		assertTrue(schedule.isRunning(), "마지막 장 직후에는 아직 여운이 남아 있어야 한다");

		for (int tick = 1; tick < 20; tick++) {
			assertEquals(Schedule.NO_CARD, cardAt(schedule));
			assertTrue(schedule.isRunning());
		}
		assertEquals(Schedule.NO_CARD, cardAt(schedule));
		assertFalse(schedule.isRunning(), "여운까지 끝나면 예약이 남아 있으면 안 된다");
		for (int tick = 0; tick < 1000; tick++) {
			assertTrue(schedule.advance().isQuiet(), "끝난 뒤에는 아무 일도 일어나지 않는다");
		}
	}

	/** 폭죽은 첫 장과 함께 시작해 1초마다 한 무리씩 오른다. */
	@Test
	void 폭죽은_첫_장부터_일초마다_오른다() {
		Schedule schedule = new Schedule();
		schedule.start(5, 100, 2);

		for (int tick = 1; tick < 5; tick++) {
			assertFalse(schedule.advance().firework(), tick + "틱째에는 아직 폭죽이 없어야 한다");
		}
		Outcome first = schedule.advance();
		assertTrue(first.firework(), "첫 장과 함께 폭죽이 터져야 한다");
		assertEquals(0, first.cardIndex());

		for (int tick = 1; tick < VictoryCelebration.VOLLEY_PERIOD_TICKS; tick++) {
			assertFalse(schedule.advance().firework());
		}
		assertTrue(schedule.advance().firework(), "1초 뒤에 다음 무리가 올라야 한다");
	}

	@Test
	void 시작하지_않았거나_띄울_장이_없으면_아무것도_하지_않는다() {
		Schedule fresh = new Schedule();
		assertFalse(fresh.isRunning());
		assertTrue(fresh.advance().isQuiet());

		Schedule empty = new Schedule();
		empty.start(10, 10, 0);
		assertFalse(empty.isRunning(), "띄울 장이 없으면 예약 자체가 서지 않는다");
		assertTrue(empty.advance().isQuiet());
	}

	@Test
	void 취소하면_남은_장이_사라진다() {
		Schedule schedule = new Schedule();
		schedule.start(100, 100, 5);
		assertTrue(schedule.isRunning());

		schedule.cancel();

		assertFalse(schedule.isRunning());
		assertTrue(schedule.advance().isQuiet());
	}

	@Test
	void 지연이_영이하여도_다음틱에_한번씩만_진행한다() {
		Schedule schedule = new Schedule();
		schedule.start(0, -5, 2);

		assertEquals(0, cardAt(schedule));
		assertEquals(1, cardAt(schedule));
		assertEquals(Schedule.NO_CARD, cardAt(schedule));
		assertFalse(schedule.isRunning());
	}

	// ------------------------------------------------------------------ 무엇을 띄우는가

	@Test
	void 기록이_다_있으면_다섯_장이_정해진_차례로_선다() {
		List<Card> cards = VictoryCelebration.buildCards(3, "운명공동체",
				new VictorySummary("Kairen", 412.5D, "Aoi", 2, 17));

		assertEquals(List.of("3회차 승리", "최다 피해", "최다 사망", "고른 증강", "수고하셨습니다"),
				cards.stream().map(Card::title).toList());
		assertEquals("운명공동체", cards.get(0).subtitle());
		assertEquals("Kairen  412.5", cards.get(1).subtitle());
		assertEquals("Aoi  2회", cards.get(2).subtitle());
		assertEquals("합계 17개", cards.get(3).subtitle());
		assertEquals("제작자 카이렌", cards.getLast().subtitle());
	}

	/**
	 * 셀 것이 없는 장은 통째로 빠진다.
	 *
	 * <p>첫 승리에는 지난 회차 기록이 없다. 「최다 사망 : 없음」을 축하 자리에 띄우지 않는다.
	 */
	@Test
	void 셀_것이_없으면_그_장은_빠지고_처음과_끝만_남는다() {
		List<Card> cards = VictoryCelebration.buildCards(1, "혼자서", VictorySummary.EMPTY);

		assertEquals(List.of("1회차 승리", "수고하셨습니다"),
				cards.stream().map(Card::title).toList());
	}

	/** 기록을 아예 못 읽어도 엔딩은 선다. */
	@Test
	void 기록이_없어도_두_장은_뜬다() {
		List<Card> cards = VictoryCelebration.buildCards(0, "", null);

		assertEquals(2, cards.size());
		assertEquals("1회차 승리", cards.getFirst().title(), "회차는 최소 1로 올린다");
		assertEquals("모험가", cards.getFirst().subtitle(), "팀 이름이 없으면 기본 이름을 쓴다");
	}

	@Test
	void 기본_지연은_각각_오초다() {
		assertEquals(100, VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS);
		assertEquals(100, VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS);
	}

	/** {@code gap} 틱을 돌려 그 마지막 틱에 뜬 장 번호를 돌려준다. */
	private static int drainUntilCard(Schedule schedule, int gap) {
		for (int tick = 1; tick < gap; tick++) {
			assertEquals(Schedule.NO_CARD, cardAt(schedule), tick + "틱째에는 아직 다음 장이 없다");
		}
		return cardAt(schedule);
	}
}
