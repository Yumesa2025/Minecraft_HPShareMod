package com.sharedfate.perk;

import com.sharedfate.ui.PerkCardDismiss;
import com.sharedfate.ui.PerkCardFocus;
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

	/** 카드가 늘 세 장 서는 창이라 자리잡기 시간의 기준으로 쓴다. */
	private static final int CARDS = 3;

	@Test
	void 추첨_연출은_3점5초다() {
		// 5초(100틱)에서 30% 줄인 값. 클라이언트는 이 값을 PerkDrawPayload 로 받아 그 안에서
		// 이름을 굴린다.
		assertEquals(70, PerkChoiceSession.DRAW_TICKS);
	}

	@Test
	void 결과_연출은_5초다() {
		// 이 5초가 곧 「N초 뒤 다시 시작합니다」 카운트다운의 길이이자 서버가 시간을 더 멈춰
		// 두는 길이다. 3초로는 카드가 자리를 잡는 앞머리를 빼면 읽을 시간이 2.5초도 남지
		// 않았다. 5로 시작해 1까지 떨어져야 하므로 1초보다 길어야 한다.
		assertEquals(100, PerkChoiceSession.RESULT_TICKS);
		assertTrue(PerkChoiceSession.RESULT_TICKS > 20, "카운트다운이 되려면 1초보다 길어야 한다");
	}

	@Test
	void 결과_연출이_카드_자리잡기의_세_배보다_길다() {
		// 안 고른 카드가 내려가고 고른 카드가 가운데로 오는 앞머리가 결과 시간을 다 먹으면
		// 정작 읽을 시간이 없다. 앞머리에서만 끝나는지 못 박아 둔다.
		long settleMillis =
				Math.max(PerkCardDismiss.totalMillis(CARDS), PerkCardFocus.MOVE_MILLIS);
		long holdMillis = PerkChoiceSession.RESULT_TICKS * 50L;
		assertTrue(settleMillis * 3 < holdMillis,
				"자리잡기 " + settleMillis + "ms 가 결과 " + holdMillis + "ms 의 3분의 1을 넘는다");
	}

	@Test
	void 고른_카드가_옆_카드보다_먼저_자리를_잡는다() {
		// 옆 카드가 아직 내려가는 중에 가운데 카드가 멈춰 있어야, 마지막에 남는 그림이
		// 「가운데 한 장」으로 또렷하게 정리된다.
		assertTrue(PerkCardFocus.MOVE_MILLIS < PerkCardDismiss.totalMillis(CARDS));
	}

	@Test
	void 한_번의_연출이_10초를_넘지_않는다() {
		// 추첨과 결과는 둘 다 시간이 멈춘 채로 흐른다. 한 회차에 여덟 번 겪으므로 한 번이
		// 길어지면 여덟 배로 지루해진다.
		int forced = PerkChoiceSession.DRAW_TICKS + PerkChoiceSession.RESULT_TICKS;
		assertTrue(forced <= 10 * 20, "고르는 시간을 빼고도 " + (forced / 20.0) + "초나 멈춰 있다");
	}

	@Test
	void 연출_둘을_합쳐도_제한시간보다_짧다() {
		// 연출이 제한시간보다 길면 고를 시간이 사라진다.
		assertTrue(PerkChoiceSession.DRAW_TICKS + PerkChoiceSession.RESULT_TICKS
				< PerkChoiceSession.TIMEOUT_TICKS);
	}
}
