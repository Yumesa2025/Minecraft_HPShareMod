package com.sharedfate.sync;

import com.sharedfate.sync.DamageLedger.VictorySummary;
import com.sharedfate.sync.VictoryCelebration.Card;
import com.sharedfate.sync.VictoryCelebration.Schedule;
import com.sharedfate.sync.VictoryCelebration.Schedule.Outcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VictoryCelebrationTest {

	private static final int FIRST = VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS;
	private static final int SUBTITLE = VictoryCelebration.DEFAULT_SUBTITLE_DELAY_TICKS;
	private static final int GAP = VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS;

	/** 장 번호만 뽑아 본다. 폭죽은 따로 세므로 여기서는 관심이 없다. */
	private static int cardAt(Schedule schedule) {
		return schedule.advance().cardIndex();
	}

	/** {@code ticks} 틱을 돌려 그 마지막 틱의 결과를 돌려준다. 중간 틱에는 글이 없어야 한다. */
	private static Outcome stepAfter(Schedule schedule, int ticks) {
		for (int tick = 1; tick < ticks; tick++) {
			assertEquals(Schedule.NO_CARD, cardAt(schedule), tick + "틱째에는 아직 글이 없어야 한다");
		}
		return schedule.advance();
	}

	// ------------------------------------------------------------------ 언제 뜨는가

	@Test
	void 첫_장은_정한_지연이_지난_뒤에_뜬다() {
		Schedule schedule = new Schedule();
		schedule.start(FIRST, SUBTITLE, GAP, 3);

		Outcome first = stepAfter(schedule, FIRST);

		assertEquals(0, first.cardIndex(), "200틱(10초)째에 첫 장이 떠야 한다");
		assertFalse(first.subtitle(), "첫 걸음은 제목만이다");
	}

	/** 드래곤을 잡고 승리가 뜬 뒤 10초가 지나서 정산이 시작된다. */
	@Test
	void 기본_첫_장_지연은_십초다() {
		assertEquals(200, VictoryCelebration.DEFAULT_TITLE_DELAY_TICKS);
	}

	@Test
	void 기본_장_간격은_오초_부제_지연은_삼점오초다() {
		assertEquals(100, VictoryCelebration.DEFAULT_FIREWORK_DELAY_TICKS);
		assertEquals(70, VictoryCelebration.DEFAULT_SUBTITLE_DELAY_TICKS);
	}

	/**
	 * 한 장은 두 걸음이다 — 제목만, 그리고 3.5초 뒤에 부제.
	 *
	 * <p>둘을 한꺼번에 띄우면 「고른 증강」과 「합계 17개」가 동시에 나타나 어느 쪽을 볼지
	 * 정하기도 전에 장이 넘어간다.
	 */
	@Test
	void 한_장은_제목이_먼저_뜨고_칠십틱_뒤에_부제가_따라_붙는다() {
		Schedule schedule = new Schedule();
		schedule.start(10, SUBTITLE, GAP, 2);

		Outcome title = stepAfter(schedule, 10);
		assertEquals(0, title.cardIndex());
		assertFalse(title.subtitle(), "먼저 제목만 뜬다");

		Outcome subtitle = stepAfter(schedule, SUBTITLE);
		assertEquals(0, subtitle.cardIndex(), "부제는 같은 장에 붙는다");
		assertTrue(subtitle.subtitle(), "70틱(3.5초) 뒤에 부제가 따라 붙어야 한다");
	}

	/** 부제가 붙고 나서 다음 장까지는 예전과 똑같이 장 간격만큼 기다린다. */
	@Test
	void 부제가_붙은_뒤_다음_장까지는_장_간격_그대로다() {
		Schedule schedule = new Schedule();
		schedule.start(10, SUBTITLE, GAP, 3);

		stepAfter(schedule, 10);
		stepAfter(schedule, SUBTITLE);

		Outcome second = stepAfter(schedule, GAP);
		assertEquals(1, second.cardIndex(), "부제 뒤 100틱(5초)째에 다음 장이 떠야 한다");
		assertFalse(second.subtitle());
	}

	/** 한 장의 길이는 부제 지연 + 장 간격이다. 장이 그만큼 길어졌다. */
	@Test
	void 장은_부제지연_더하기_장간격마다_차례대로_넘어간다() {
		Schedule schedule = new Schedule();
		schedule.start(10, 20, 30, 3);

		assertEquals(0, stepAfter(schedule, 10).cardIndex());
		assertTrue(stepAfter(schedule, 20).subtitle());
		assertEquals(1, stepAfter(schedule, 30).cardIndex());
		assertTrue(stepAfter(schedule, 20).subtitle());
		assertEquals(2, stepAfter(schedule, 30).cardIndex());
		assertTrue(stepAfter(schedule, 20).subtitle());
	}

	/**
	 * 마지막 장의 부제까지 얹은 뒤에도 간격 하나만큼 더 돌고 멎는다.
	 *
	 * <p>그 여운 동안 폭죽이 계속 터져야 맺음말이 화면에 떠 있는 채로 끝난다. 마지막 장에서
	 * 곧바로 멎으면 글이 뜨자마자 하늘이 조용해진다. 장이 두 걸음이 되면서 맺음말 한 장이
	 * 화면에 머무는 시간 자체가 {@code 부제 지연 + 간격}으로 늘었다.
	 */
	@Test
	void 마지막_장_뒤에_여운이_한_간격_남았다가_멎는다() {
		Schedule schedule = new Schedule();
		schedule.start(1, 20, 30, 2);

		assertEquals(0, cardAt(schedule));
		assertTrue(stepAfter(schedule, 20).subtitle());
		assertEquals(1, stepAfter(schedule, 30).cardIndex());
		assertTrue(stepAfter(schedule, 20).subtitle(), "마지막 장의 부제");
		assertTrue(schedule.isRunning(), "마지막 부제 직후에는 아직 여운이 남아 있어야 한다");

		for (int tick = 1; tick < 30; tick++) {
			assertEquals(Schedule.NO_CARD, cardAt(schedule));
			assertTrue(schedule.isRunning());
		}
		assertEquals(Schedule.NO_CARD, cardAt(schedule));
		assertFalse(schedule.isRunning(), "여운까지 끝나면 예약이 남아 있으면 안 된다");
		for (int tick = 0; tick < 1000; tick++) {
			assertTrue(schedule.advance().isQuiet(), "끝난 뒤에는 아무 일도 일어나지 않는다");
		}
	}

	// ------------------------------------------------------------------ 폭죽

	/** 폭죽은 첫 장과 함께 시작해 1초마다 한 무리씩 오른다. */
	@Test
	void 폭죽은_첫_장부터_일초마다_오른다() {
		Schedule schedule = new Schedule();
		schedule.start(5, SUBTITLE, GAP, 2);

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

	/**
	 * 장이 길어졌으면 폭죽도 그만큼 더 오래 올라야 한다.
	 *
	 * <p>기본값 다섯 장짜리 연출의 길이는 {@code 200 + 5 * (70 + 100) = 1050틱}(52.5초)이다.
	 * 그 마지막 틱까지 1초 주기가 한 번도 끊기지 않아야 하늘이 비지 않는다.
	 */
	@Test
	void 폭죽은_길어진_연출_내내_계속_오른다() {
		int cardCount = 5;
		int total = FIRST + cardCount * (SUBTITLE + GAP);
		assertEquals(1050, total, "기본값 다섯 장이면 연출은 1050틱(52.5초)이다");

		Schedule schedule = new Schedule();
		schedule.start(FIRST, SUBTITLE, GAP, cardCount);

		List<Integer> volleys = new ArrayList<>();
		for (int tick = 1; tick <= total; tick++) {
			assertTrue(schedule.isRunning() || tick == total,
					tick + "틱째에도 연출이 살아 있어야 한다");
			if (schedule.advance().firework()) {
				volleys.add(tick);
			}
		}
		assertFalse(schedule.isRunning(), "1050틱째에 연출이 끝난다");

		assertEquals(FIRST, volleys.getFirst(), "첫 무리는 첫 장과 함께 오른다");
		assertEquals(1040, volleys.getLast(), "마지막 무리는 연출이 끝나기 직전까지 오른다");
		assertEquals(43, volleys.size(), "1초 주기로 43무리");
		for (int index = 1; index < volleys.size(); index++) {
			assertEquals(VictoryCelebration.VOLLEY_PERIOD_TICKS,
					volleys.get(index) - volleys.get(index - 1),
					index + "번째 무리까지의 사이가 1초가 아니다");
		}
	}

	// ------------------------------------------------------------------ 가장자리

	@Test
	void 시작하지_않았거나_띄울_장이_없으면_아무것도_하지_않는다() {
		Schedule fresh = new Schedule();
		assertFalse(fresh.isRunning());
		assertTrue(fresh.advance().isQuiet());

		Schedule empty = new Schedule();
		empty.start(10, 10, 10, 0);
		assertFalse(empty.isRunning(), "띄울 장이 없으면 예약 자체가 서지 않는다");
		assertTrue(empty.advance().isQuiet());
	}

	@Test
	void 취소하면_남은_장이_사라진다() {
		Schedule schedule = new Schedule();
		schedule.start(FIRST, SUBTITLE, GAP, 5);
		assertTrue(schedule.isRunning());

		schedule.cancel();

		assertFalse(schedule.isRunning());
		assertTrue(schedule.advance().isQuiet());
	}

	@Test
	void 지연이_영이하여도_다음틱에_한번씩만_진행한다() {
		Schedule schedule = new Schedule();
		schedule.start(0, -5, -5, 2);

		assertEquals(0, cardAt(schedule));
		Outcome firstSubtitle = schedule.advance();
		assertEquals(0, firstSubtitle.cardIndex());
		assertTrue(firstSubtitle.subtitle());
		assertEquals(1, cardAt(schedule));
		assertTrue(schedule.advance().subtitle());
		assertEquals(Schedule.NO_CARD, cardAt(schedule));
		assertFalse(schedule.isRunning());
	}

	// ------------------------------------------------------------------ 무엇을 띄우는가

	@Test
	void 기록이_다_있으면_다섯_장이_정해진_차례로_선다() {
		List<Card> cards = VictoryCelebration.buildCards(3, "운명공동체",
				new VictorySummary("플레이어1", 412.5D, "플레이어2", 2, 17));

		assertEquals(List.of("3회차 승리", "받은 피해량", "최다 사망", "고른 증강", "수고하셨습니다"),
				cards.stream().map(Card::title).toList());
		assertEquals("운명공동체", cards.get(0).subtitle());
		assertEquals("플레이어1  412.5", cards.get(1).subtitle());
		assertEquals("플레이어2  2회", cards.get(2).subtitle());
		assertEquals("합계 17개", cards.get(3).subtitle());
		assertEquals("제작자 카이렌", cards.getLast().subtitle());
	}

	/**
	 * 모든 장에 부제가 있다.
	 *
	 * <p>{@link Schedule} 은 장마다 똑같이 두 걸음을 돈다 — 부제가 없는 장이 섞이면 3.5초를
	 * 빈 채로 기다리게 된다. 맺음말까지 부제를 갖고 있다는 것이 그 전제이므로 여기서 못박는다.
	 */
	@Test
	void 모든_장은_부제를_갖고_있다() {
		List<Card> full = VictoryCelebration.buildCards(3, "운명공동체",
				new VictorySummary("플레이어1", 412.5D, "플레이어2", 2, 17));
		List<Card> bare = VictoryCelebration.buildCards(1, "혼자서", VictorySummary.EMPTY);

		for (Card card : full) {
			assertFalse(card.subtitle().isBlank(), card.title() + " 장에 부제가 없다");
		}
		for (Card card : bare) {
			assertFalse(card.subtitle().isBlank(), card.title() + " 장에 부제가 없다");
		}
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
}
