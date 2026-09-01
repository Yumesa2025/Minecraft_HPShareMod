package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkCardFocusTest {

	private static final float EPSILON = 0.0001F;
	/** 왼쪽 카드가 골라졌을 때를 가정한 자리. 실제 화면의 배치와 같은 방향이면 된다. */
	private static final int FROM_LEFT = 40;
	private static final int TO_LEFT = 160;

	@Test
	void 시작하기_전에는_제자리다() {
		assertEquals(0.0F, PerkCardFocus.progress(0L), EPSILON);
		assertEquals(FROM_LEFT, PerkCardFocus.left(0L, FROM_LEFT, TO_LEFT));
		// 음수 시각이 들어와도 뒤로 튀지 않는다.
		assertEquals(FROM_LEFT, PerkCardFocus.left(-100L, FROM_LEFT, TO_LEFT));
	}

	@Test
	void 끝나면_정확히_가운데다() {
		assertEquals(1.0F, PerkCardFocus.progress(PerkCardFocus.MOVE_MILLIS), EPSILON);
		assertEquals(TO_LEFT, PerkCardFocus.left(PerkCardFocus.MOVE_MILLIS, FROM_LEFT, TO_LEFT));
		// 한참 뒤에도 지나쳐 가지 않는다.
		assertEquals(TO_LEFT, PerkCardFocus.left(60_000L, FROM_LEFT, TO_LEFT));
	}

	@Test
	void 순간이동하지_않는다() {
		// 한 프레임(약 16ms) 뒤에도 아직 가운데에 닿지 않아야 눈이 카드를 따라갈 수 있다.
		assertTrue(PerkCardFocus.left(16L, FROM_LEFT, TO_LEFT) < TO_LEFT);
		assertTrue(PerkCardFocus.left(16L, FROM_LEFT, TO_LEFT) > FROM_LEFT);
	}

	@Test
	void 처음엔_빠르고_나중에_느리다() {
		// 내려가는 카드(PerkCardDismiss)와 반대 곡선이다. 절반 시점에 이미 절반을 넘어야
		// 감속으로 읽힌다.
		float half = PerkCardFocus.progress(PerkCardFocus.MOVE_MILLIS / 2);
		assertTrue(half > 0.5F, "절반 시점 진행도가 " + half + " 뿐이다");

		long quarter = PerkCardFocus.MOVE_MILLIS / 4;
		float first = PerkCardFocus.progress(quarter);
		float last = 1.0F - PerkCardFocus.progress(PerkCardFocus.MOVE_MILLIS - quarter);
		assertTrue(first > last, "앞이 더 느리다: 앞 " + first + " 뒤 " + last);
	}

	@Test
	void 오는_동안_되돌아가지_않는다() {
		int previous = FROM_LEFT - 1;
		for (long time = 0; time <= PerkCardFocus.MOVE_MILLIS; time += 5) {
			int left = PerkCardFocus.left(time, FROM_LEFT, TO_LEFT);
			assertTrue(left >= previous, time + "ms 에서 되돌아갔다: " + left);
			previous = left;
		}
	}

	@Test
	void 오른쪽에서_왼쪽으로도_같다() {
		assertEquals(TO_LEFT, PerkCardFocus.left(0L, TO_LEFT, FROM_LEFT));
		assertEquals(FROM_LEFT, PerkCardFocus.left(PerkCardFocus.MOVE_MILLIS, TO_LEFT, FROM_LEFT));
		int middle = PerkCardFocus.left(PerkCardFocus.MOVE_MILLIS / 2, TO_LEFT, FROM_LEFT);
		assertTrue(middle > FROM_LEFT && middle < TO_LEFT, "중간 위치: " + middle);
	}

	@Test
	void 가운데_카드는_처음부터_움직이지_않는다() {
		// 세 장 중 가운데가 골라지면 옮길 곳이 제자리다. 계산을 건너뛴다.
		assertEquals(TO_LEFT, PerkCardFocus.left(0L, TO_LEFT, TO_LEFT));
		assertEquals(TO_LEFT, PerkCardFocus.left(PerkCardFocus.MOVE_MILLIS / 2, TO_LEFT, TO_LEFT));
	}
}
