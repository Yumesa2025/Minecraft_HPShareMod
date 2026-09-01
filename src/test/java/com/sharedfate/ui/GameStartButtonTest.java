package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀 화면 「게임 시작」 단추의 순수 계산.
 *
 * <p>화면은 {@code src/client} 에 있어 시험 소스셋이 보지 못하므로, 여기서 확인하는 것은
 * <b>언제 보이고 무엇을 보내는가</b>다. 되돌릴 수 없는 단추라 「보이면 안 될 때 보이지 않는지」가
 * 가장 중요하다.
 */
class GameStartButtonTest {

	@Test
	void 팀이_있고_리더이고_아직_시작하지_않았을_때만_보인다() {
		assertTrue(GameStartButton.visible(true, true, false));
	}

	@Test
	void 이미_시작한_팀에게는_보이지_않는다() {
		// 여기가 뚫리면 진행 중인 회차의 아이템이 통째로 날아간다.
		assertFalse(GameStartButton.visible(true, true, true));
	}

	@Test
	void 리더가_아니면_보이지_않는다() {
		assertFalse(GameStartButton.visible(true, false, false));
	}

	@Test
	void 팀이_없으면_보이지_않는다() {
		assertFalse(GameStartButton.visible(false, true, false));
		assertFalse(GameStartButton.visible(false, false, false));
	}

	@Test
	void 확인_단계의_글자는_무엇을_잃는지_적는다() {
		String confirming = GameStartButton.label(true);
		assertTrue(confirming.contains("아이템"), confirming);
		assertNotEquals(GameStartButton.label(false), confirming,
				"확인 단계인지 글자로 구분되지 않으면 두 번 누르게 한 뜻이 없다");
	}

	@Test
	void 보내는_명령은_확인_낱말을_반드시_포함한다() {
		// 서버는 confirm 없는 start 를 안내로만 받는다. 화면이 그 절차를 건너뛰면 안 된다.
		assertEquals("start confirm", GameStartButton.CONFIRM_COMMAND);
	}

	@Test
	void 대기_안내는_리더인지에_따라_다르다() {
		assertTrue(GameStartButton.waitingNotice(true).contains("팀"),
				"리더에게는 어디서 시작하는지 알려 줘야 한다");
		assertTrue(GameStartButton.waitingNotice(false).contains("리더"),
				"리더가 아니면 무엇을 기다리는지 알려 줘야 한다");
	}
}
