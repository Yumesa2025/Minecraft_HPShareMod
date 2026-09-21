package com.sharedfate.client;

import com.sharedfate.ui.GameOverCountdown;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 카운트다운이 <b>왜 도는지</b>를 클라이언트가 들고 있는 자리.
 *
 * <p>실제로 그리는 {@code GameOverHud} 와 사망 화면을 고쳐 칠하는 {@code tick} 은 살아 있는
 * 클라이언트가 있어야 해서 단위 시험으로 닿지 않는다. 여기서는 <b>서버가 보낸 이유가 화면이
 * 읽는 자리까지 그대로 오는지</b>와 <b>연결이 끊기면 지워지는지</b>만 본다.
 */
class GameOverClientDisplayTest {

	@AfterEach
	void 치운다() {
		GameOverClientDisplay.clear();
	}

	@Test
	void 받은_이유를_그대로_들고_있는다() {
		GameOverClientDisplay.show(3, 100, GameOverCountdown.Reason.RUN_RESET);

		assertEquals(GameOverCountdown.Reason.RUN_RESET, GameOverClientDisplay.reason());
		assertEquals(5, GameOverClientDisplay.countdownSeconds());
	}

	@Test
	void 전멸_예고는_전멸인_채로_남는다() {
		GameOverClientDisplay.show(3, 100, GameOverCountdown.Reason.TEAM_WIPE);

		assertEquals(GameOverCountdown.Reason.TEAM_WIPE, GameOverClientDisplay.reason());
	}

	/**
	 * 나갈 때 이유까지 지운다.
	 *
	 * <p>이 값은 {@code static} 이라 <b>게임을 끄기 전까지 살아 있다.</b> 초기화로 내려간 서버에
	 * 다시 들어가 이번에는 전멸하는 것이 바로 다음에 일어날 일인데, 그때 지난번 이유가 남아
	 * 있으면 전멸 화면에 「서버 초기화」가 뜬다. {@code SharedFateClient} 가 연결이 끊길 때
	 * {@link GameOverClientDisplay#clear} 를 부른다.
	 */
	@Test
	void 연결이_끊기면_이유도_기본값으로_돌아간다() {
		GameOverClientDisplay.show(3, 100, GameOverCountdown.Reason.RUN_RESET);

		GameOverClientDisplay.clear();

		assertEquals(GameOverCountdown.Reason.TEAM_WIPE, GameOverClientDisplay.reason());
		assertEquals(0, GameOverClientDisplay.countdownSeconds());
	}
}
