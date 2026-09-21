package com.sharedfate.ui;

import com.sharedfate.ui.GameOverCountdown.Reason;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 게임 오버 카운트다운의 숫자 계산과 <b>경로별 문구</b>.
 *
 * <p>실제로 타이틀을 보내고 서버를 멈추는 {@code WorldResetCoordinator} 는 살아 있는 서버가
 * 있어야 해서 단위 시험으로 닿지 않는다. 여기서는 <b>남은 틱에서 어떤 숫자가 나오는지</b>와
 * <b>어느 경로에 어떤 글자가 뜨는지</b>를 확인한다 — 5초짜리 카운트다운이 5·4·3·2·1 을 한 번씩
 * 정확히 보여 주는지, 그리고 전멸이 아닌 경로에서 「게임 오버」가 뜨지 않는지가 전부다.
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
		assertEquals("5초 후 서버가 종료됩니다",
				GameOverCountdown.shutdownNotice(Reason.TEAM_WIPE, 5));
		assertEquals("게임 오버! '화이팅' 팀이 전멸했습니다. 5초 후 새 월드로 서버를 다시 엽니다.",
				GameOverCountdown.wipeAnnouncement("화이팅", 5));
	}

	/**
	 * 화면 한가운데 큰 글씨는 <b>경로마다 달라야 한다.</b>
	 *
	 * <p>이 시험이 서는 이유가 사고 하나다 — 운영자가 {@code /shareteam reset} 을 쳤을 뿐인데
	 * 살아 있는 팀원 전원의 화면에 「게임 오버」가 떴다. 아무도 죽지 않았는데 <b>전멸한 줄
	 * 아는</b> 것이다. 두 경로가 같은 상수를 쓰는 한 이 사고는 언제든 되돌아온다.
	 */
	@Test
	void 전멸과_초기화는_다른_제목을_쓴다() {
		assertEquals("게임 오버", GameOverCountdown.title(Reason.TEAM_WIPE));
		assertEquals("서버 초기화", GameOverCountdown.title(Reason.RUN_RESET));
		assertNotEquals(GameOverCountdown.title(Reason.TEAM_WIPE),
				GameOverCountdown.title(Reason.RUN_RESET));
	}

	/**
	 * 초기화 제목에 <b>전멸을 뜻하는 낱말이 섞이면 안 된다.</b>
	 *
	 * <p>「게임 오버 (초기화)」처럼 덧붙이는 식으로 고치면 큰 글씨의 첫 두 낱말이 그대로라
	 * 읽는 사람은 여전히 전멸로 읽는다. 낱말 자체가 없어야 한다.
	 */
	@Test
	void 초기화_제목에는_전멸을_뜻하는_낱말이_없다() {
		String title = GameOverCountdown.title(Reason.RUN_RESET);

		assertFalse(title.contains("게임 오버"), title);
		assertFalse(title.contains("전멸"), title);
		assertTrue(title.contains("초기화"), title);
	}

	/**
	 * 경로가 <b>셋</b>이 되는 날을 위한 시험.
	 *
	 * <p>{@link Reason} 에 상수를 하나 더하면 {@link GameOverCountdown#title} 의 switch 가
	 * 컴파일 단계에서 막는다. 그래도 남의 제목을 복사해 붙이는 것까지는 못 막으므로, 제목이
	 * 서로 겹치지 않는다는 것만 여기서 붙들어 둔다.
	 */
	@Test
	void 어떤_경로든_제목은_비어_있지_않고_서로_겹치지_않는다() {
		Set<String> seen = new HashSet<>();
		for (Reason reason : Reason.values()) {
			String title = GameOverCountdown.title(reason);
			assertFalse(title.isBlank(), reason.name());
			assertTrue(seen.add(title), reason.name() + " 의 제목이 다른 경로와 겹친다: " + title);
		}
	}

	/**
	 * 사망 화면의 제목.
	 *
	 * <p>회차를 붙이는 것은 전멸 경로뿐이다. 초기화는 <b>그 회차를 통째로 버리는</b> 일이라
	 * 「서버 초기화 · 7회차」라고 적으면 7회차가 이어진다는 뜻으로 읽힌다.
	 */
	@Test
	void 사망_화면_제목은_전멸일_때만_회차를_붙인다() {
		assertEquals("게임 오버 · 7회차", GameOverCountdown.screenTitle(Reason.TEAM_WIPE, 7));
		assertEquals("서버 초기화", GameOverCountdown.screenTitle(Reason.RUN_RESET, 7));
	}

	/**
	 * 숫자 아래 작은 글씨도 경로를 따라간다.
	 *
	 * <p>초기화 쪽은 <b>서버가 돌아온다</b>는 것까지 적는다. 제목이 「서버 초기화」라도 그 아래가
	 * 「종료됩니다」로 끝나면 읽는 사람은 여기서 끝나는 줄 안다.
	 */
	@Test
	void 아래_작은_글씨도_경로마다_다르다() {
		assertEquals("5초 후 서버가 종료됩니다",
				GameOverCountdown.shutdownNotice(Reason.TEAM_WIPE, 5));
		assertEquals("5초 후 서버가 종료되고 새 월드로 다시 열립니다",
				GameOverCountdown.shutdownNotice(Reason.RUN_RESET, 5));
		assertNotEquals(GameOverCountdown.shutdownNotice(Reason.TEAM_WIPE, 5),
				GameOverCountdown.shutdownNotice(Reason.RUN_RESET, 5));
	}

	/**
	 * 패킷에 실리는 이름.
	 *
	 * <p>칸 차례가 아니라 <b>이름</b>을 싣는다. 상수를 하나 끼워 넣거나 차례를 바꿔도 예전에 쓰던
	 * 뜻이 조용히 다른 경로로 옮겨 가지 않는다.
	 */
	@Test
	void 경로에는_패킷에_실리는_이름이_있다() {
		assertEquals("team_wipe", Reason.TEAM_WIPE.id());
		assertEquals("run_reset", Reason.RUN_RESET.id());
		assertEquals(Reason.TEAM_WIPE, Reason.fromId("team_wipe"));
		assertEquals(Reason.RUN_RESET, Reason.fromId("run_reset"));
	}

	/**
	 * 모르는 이름이 오면 전멸로 본다.
	 *
	 * <p>규약 번호를 올렸으므로 <b>판이 맞는 클라이언트에서는 올 수 없는 값</b>이다. 여기까지
	 * 오는 것은 손상된 묶음뿐이고, 그때 고를 수 있는 답은 둘 다 추측이다. 원래 하던 대로
	 * 두는 쪽을 골랐다 — 이 카운트다운이 도는 이유는 거의 언제나 전멸이다.
	 */
	@Test
	void 모르는_이름은_전멸로_본다() {
		assertEquals(Reason.TEAM_WIPE, Reason.fromId("모르는_값"));
		assertEquals(Reason.TEAM_WIPE, Reason.fromId(""));
		assertEquals(Reason.TEAM_WIPE, Reason.fromId(null));
	}

	@Test
	void 기본_길이는_오초다() {
		assertEquals(5, GameOverCountdown.DEFAULT_SECONDS);
		assertEquals(100,
				GameOverCountdown.DEFAULT_SECONDS * GameOverCountdown.TICKS_PER_SECOND);
	}
}
