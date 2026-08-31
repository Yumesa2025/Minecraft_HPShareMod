package com.sharedfate.perk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 연출 길이를 못 박아 두는 시험.
 *
 * <p>이 값들은 <b>클라이언트 연출 길이인 동시에 시간이 멈춰 있는 길이</b>다. 화면 쪽만 고치고
 * 여기를 빠뜨리면 연출이 끝난 뒤 아무것도 없는 화면을 몇 초 더 보게 되고, 반대로 여기만
 * 고치면 연출이 도중에 잘린다. 그래서 값 자체를 시험으로 묶어 둔다.
 */
class PerkPresentationTimingTest {

	@Test
	void 추첨_연출은_3점5초다() {
		// 5초(100틱)에서 30% 줄인 값. 클라이언트는 이 값을 PerkDrawPayload 로 받아 그 안에서
		// 이름을 굴린다.
		assertEquals(70, PerkChoiceSession.DRAW_TICKS);
	}

	@Test
	void 결과_연출은_3초다() {
		// 이 3초가 곧 「N초 뒤 다시 시작합니다」 카운트다운의 길이다. 3으로 시작해 1까지
		// 떨어져야 하므로 1초보다 길어야 한다.
		assertEquals(60, PerkChoiceSession.RESULT_TICKS);
		assertTrue(PerkChoiceSession.RESULT_TICKS > 20, "카운트다운이 되려면 1초보다 길어야 한다");
	}

	@Test
	void 연출_둘을_합쳐도_제한시간보다_짧다() {
		// 연출이 제한시간보다 길면 고를 시간이 사라진다.
		assertTrue(PerkChoiceSession.DRAW_TICKS + PerkChoiceSession.RESULT_TICKS
				< PerkChoiceSession.TIMEOUT_TICKS);
	}
}
