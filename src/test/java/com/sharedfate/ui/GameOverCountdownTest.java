package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 게임 오버 카운트다운의 숫자 계산.
 *
 * <p>실제로 타이틀을 보내고 서버를 멈추는 {@code WorldResetCoordinator} 는 살아 있는 서버가
 * 있어야 해서 단위 시험으로 닿지 않는다. 여기서는 <b>남은 틱에서 어떤 숫자가 나오는지</b>만
 * 확인한다 — 5초짜리 카운트다운이 5·4·3·2·1 을 한 번씩 정확히 보여 주는지가 전부다.
 */
class GameOverCountdownTest {

	@Test
	void 남은_시간은_올림해서_보여_준다() {
		assertEquals(5, GameOverCountdown.secondsRemaining(100));
		// 1틱이라도 남아 있으면 1초다. 내림하면 마지막 19틱이 「0초」로 보인다.
		assertEquals(1, GameOverCountdown.secondsRemaining(1));
		assertEquals(1, GameOverCountdown.secondsRemaining(20));
		assertEquals(2, GameOverCountdown.secondsRemaining(21));
		assertEquals(0, GameOverCountdown.secondsRemaining(0));
		assertEquals(0, GameOverCountdown.secondsRemaining(-40));
	}

	/**
	 * 손상되거나 터무니없는 값이 와도 화면에 그대로 나오면 안 된다.
	 *
	 * <p>클라이언트는 서버가 보낸 길이를 그대로 믿고 세어 내려가므로, 받는 자리에서 한 번
	 * 접는다. 상한은 서버 설정의 {@code MAX_WORLD_RESET_DELAY_TICKS} 와 같은 1200 틱이다.
	 */
	@Test
	void 받은_길이는_상한과_0_사이로_접는다() {
		assertEquals(100, GameOverCountdown.sanitizeTicks(100, 1200));
		assertEquals(1200, GameOverCountdown.sanitizeTicks(999999, 1200));
		assertEquals(0, GameOverCountdown.sanitizeTicks(0, 1200));
		assertEquals(0, GameOverCountdown.sanitizeTicks(-5, 1200));
		assertEquals(0, GameOverCountdown.sanitizeTicks(100, -1));
	}

	/**
	 * 100틱짜리 카운트다운이 5·4·3·2·1 을 모두 거쳐 0 으로 끝나는지.
	 *
	 * <p>클라이언트는 매 틱 하나씩 줄이고 그때마다 {@link GameOverCountdown#secondsRemaining}
	 * 을 그린다. 숫자가 건너뛰거나, 0 이 화면에 뜨거나, 6 이 먼저 나오면 안 된다.
	 */
	@Test
	void 오초짜리_카운트다운은_다섯부터_하나까지_모두_거친다() {
		StringBuilder seen = new StringBuilder();
		int previous = -1;
		for (int ticks = 100; ticks > 0; ticks--) {
			int seconds = GameOverCountdown.secondsRemaining(ticks);
			if (seconds != previous) {
				seen.append(seconds);
				previous = seconds;
			}
		}

		assertEquals("54321", seen.toString());
		assertEquals(0, GameOverCountdown.secondsRemaining(0));
	}

	@Test
	void 문구는_숫자를_그대로_적는다() {
		assertEquals("5초 후 서버가 종료됩니다", GameOverCountdown.shutdownNotice(5));
		assertEquals("게임 오버! '화이팅' 팀이 전멸했습니다. 5초 후 새 월드로 서버를 다시 엽니다.",
				GameOverCountdown.wipeAnnouncement("화이팅", 5));
	}

	@Test
	void 기본_길이는_오초다() {
		assertEquals(5, GameOverCountdown.DEFAULT_SECONDS);
		assertEquals(100,
				GameOverCountdown.DEFAULT_SECONDS * GameOverCountdown.TICKS_PER_SECOND);
	}
}
