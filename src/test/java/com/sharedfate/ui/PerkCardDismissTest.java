package com.sharedfate.ui;

import com.sharedfate.perk.PerkChoiceSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkCardDismissTest {

	private static final float EPSILON = 0.0001F;
	/** 결과 연출 3초를 ms 로. 서버 값이 바뀌면 이 시험이 먼저 걸린다. */
	private static final long HOLD_MILLIS = PerkChoiceSession.RESULT_TICKS * 50L;
	/** 카드가 늘 세 장 서는 창이라 시차 계산의 기준으로 쓴다. */
	private static final int CARDS = 3;

	@Test
	void 시작하기_전에는_제자리다() {
		assertEquals(0, PerkCardDismiss.offset(0L, 0, 200));
		assertEquals(0.0F, PerkCardDismiss.progress(0L, 0), EPSILON);
	}

	@Test
	void 왼쪽_카드가_먼저_떠난다() {
		assertEquals(0L, PerkCardDismiss.startMillis(0));
		assertTrue(PerkCardDismiss.startMillis(1) < PerkCardDismiss.startMillis(2));
		// 오른쪽 카드가 아직 가만히 있는 시점에 왼쪽 카드는 이미 내려가고 있어야 한다.
		long moment = PerkCardDismiss.STAGGER_MILLIS;
		assertTrue(PerkCardDismiss.offset(moment, 0, 200) > 0);
		assertEquals(0, PerkCardDismiss.offset(moment, 2, 200));
	}

	@Test
	void 음수_자리도_0번으로_본다() {
		assertEquals(0L, PerkCardDismiss.startMillis(-1));
	}

	@Test
	void 끝나면_이동_거리를_다_쓴다() {
		assertEquals(200, PerkCardDismiss.offset(PerkCardDismiss.SLIDE_MILLIS, 0, 200));
		assertEquals(1.0F, PerkCardDismiss.progress(PerkCardDismiss.SLIDE_MILLIS, 0), EPSILON);
	}

	@Test
	void 한참_뒤에도_더_내려가지_않는다() {
		assertEquals(200, PerkCardDismiss.offset(HOLD_MILLIS, 2, 200));
	}

	@Test
	void 처음엔_느리고_나중에_빠르다() {
		// 절반 지점까지 간 거리가 전체의 절반보다 짧아야 가속이다.
		int half = PerkCardDismiss.offset(PerkCardDismiss.SLIDE_MILLIS / 2, 0, 400);
		assertTrue(half < 200, "절반 시점에 이미 절반을 넘었다: " + half);

		// 앞 4분의 1보다 뒤 4분의 1에서 더 많이 움직여야 한다.
		long quarter = PerkCardDismiss.SLIDE_MILLIS / 4;
		int first = PerkCardDismiss.offset(quarter, 0, 400);
		int last = PerkCardDismiss.offset(PerkCardDismiss.SLIDE_MILLIS, 0, 400)
				- PerkCardDismiss.offset(PerkCardDismiss.SLIDE_MILLIS - quarter, 0, 400);
		assertTrue(last > first, "뒤가 더 느리다: 앞 " + first + " 뒤 " + last);
	}

	@Test
	void 내려가는_동안_계속_아래로만_간다() {
		int previous = -1;
		for (long time = 0; time <= PerkCardDismiss.SLIDE_MILLIS; time += 10) {
			int offset = PerkCardDismiss.offset(time, 0, 400);
			assertTrue(offset >= previous, time + "ms 에서 되돌아갔다: " + offset);
			previous = offset;
		}
	}

	@Test
	void 이동_거리가_0이면_계산이_깨지지_않는다() {
		assertEquals(0, PerkCardDismiss.offset(100L, 0, 0));
		assertEquals(0, PerkCardDismiss.offset(100L, 0, -5));
	}

	@Test
	void 어둠은_점점_짙어지고_최대를_넘지_않는다() {
		assertEquals(0.0F, PerkCardDismiss.shade(0L, 0), EPSILON);
		float middle = PerkCardDismiss.shade(PerkCardDismiss.SLIDE_MILLIS / 2, 0);
		assertTrue(middle > 0.0F && middle < PerkCardDismiss.SHADE_MAX, "중간 세기: " + middle);
		assertEquals(PerkCardDismiss.SHADE_MAX,
				PerkCardDismiss.shade(PerkCardDismiss.SLIDE_MILLIS, 0), EPSILON);
		assertEquals(PerkCardDismiss.SHADE_MAX, PerkCardDismiss.shade(HOLD_MILLIS, 2), EPSILON);
	}

	@Test
	void 어둠은_완전히_까맣게_덮지_않는다() {
		// 1.0 이면 내려가는 도중에 카드가 검은 판이 된다. 가라앉는 것으로 보여야 한다.
		assertTrue(PerkCardDismiss.SHADE_MAX < 1.0F);
	}

	@Test
	void 다_내려간_뒤에야_그리기를_그만둔다() {
		assertFalse(PerkCardDismiss.gone(0L, 0));
		assertFalse(PerkCardDismiss.gone(PerkCardDismiss.SLIDE_MILLIS - 1, 0));
		assertTrue(PerkCardDismiss.gone(PerkCardDismiss.SLIDE_MILLIS, 0));
		// 시차만큼 늦게 떠난 카드는 그만큼 늦게까지 남는다.
		assertFalse(PerkCardDismiss.gone(PerkCardDismiss.SLIDE_MILLIS, 2));
		assertTrue(PerkCardDismiss.gone(PerkCardDismiss.totalMillis(CARDS), 2));
	}

	@Test
	void 결과_연출_앞머리에서_끝난다() {
		long total = PerkCardDismiss.totalMillis(CARDS);
		assertTrue(total < HOLD_MILLIS / 3,
				"움직임이 3초의 3분의 1을 넘게 쓴다: " + total + "ms / " + HOLD_MILLIS + "ms");
		// 다 내려간 뒤 고른 카드만 남은 화면을 볼 시간이 2초 넘게 남아야 한다.
		assertTrue(HOLD_MILLIS - total > 2000L,
				"고른 카드를 보는 시간이 " + (HOLD_MILLIS - total) + "ms 밖에 안 남는다");
	}

	@Test
	void 카드가_한_장뿐이어도_시간이_음수가_되지_않는다() {
		assertEquals(PerkCardDismiss.SLIDE_MILLIS, PerkCardDismiss.totalMillis(1));
		assertEquals(PerkCardDismiss.SLIDE_MILLIS, PerkCardDismiss.totalMillis(0));
	}
}
