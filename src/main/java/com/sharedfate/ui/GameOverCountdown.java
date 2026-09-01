package com.sharedfate.ui;

/**
 * 게임 오버 카운트다운의 <b>순수 계산</b>.
 *
 * <p>팀이 전멸하면 게임 오버 화면 한가운데에서 빨간 숫자가 5부터 1까지 내려가고, 다 내려가면
 * 서버가 종료된다. 숫자를 실제로 그리는 것은 {@code GameOverHud}(클라이언트), 서버를 멈추는
 * 것은 {@code com.sharedfate.sync.WorldResetCoordinator} 이고, 여기에는 <b>남은 틱에서 화면에
 * 적을 숫자를 뽑는 계산과 문구</b>만 있다.
 *
 * <p>{@link GameStartButton}·{@link PerkRerollButton} 과 같은 이유로 이 패키지에 있다 —
 * 화면 코드({@code src/client})는 시험 소스셋이 볼 수 없으므로, 값이 맞는지 시험할 수 있는
 * 부분만 공용 소스셋으로 내려 둔다.
 */
public final class GameOverCountdown {
	public static final int TICKS_PER_SECOND = 20;

	/** 카운트다운 기본 길이(초). */
	public static final int DEFAULT_SECONDS = 5;

	/** 화면 한가운데 큰 글씨로 뜨는 한 줄. */
	public static final String TITLE = "게임 오버";

	private GameOverCountdown() {
	}

	/**
	 * 남은 틱을 초로 올림한다. 1틱이라도 남아 있으면 1초로 보여 준다.
	 *
	 * <p>내림하면 마지막 19틱이 「0초」로 보인다. 0 은 이미 끝났다는 뜻이라 아직 살아 있는
	 * 카운트다운에 적을 숫자가 아니다.
	 */
	public static int secondsRemaining(int ticksRemaining) {
		if (ticksRemaining <= 0) {
			return 0;
		}
		return (ticksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
	}

	/**
	 * 서버에서 받은 남은 틱을 그대로 쓰기 좋게 자른다.
	 *
	 * <p>클라이언트는 {@code WorldResetPayload} 로 받은 길이를 스스로 세어 내려간다. 음수나
	 * 터무니없이 큰 값이 와도 화면이 이상해지지 않게 여기서 접는다.
	 *
	 * @param maxTicks 허용할 최대 틱. 서버 설정의 상한과 같은 값을 넘기면 된다.
	 */
	public static int sanitizeTicks(int ticksRemaining, int maxTicks) {
		if (ticksRemaining <= 0) {
			return 0;
		}
		return Math.min(ticksRemaining, Math.max(0, maxTicks));
	}

	/** 카운트다운 아래 작은 글씨. 숫자가 무엇을 세는 것인지 적는다. */
	public static String shutdownNotice(int seconds) {
		return seconds + "초 후 서버가 종료됩니다";
	}

	/**
	 * 전멸을 알리는 채팅 한 줄.
	 *
	 * <p>화면 연출과 따로 한 번만 나간다. 나중에 로그를 보거나 채팅을 되짚을 때 남아 있어야
	 * 하는 사실은 「몇 초 남았는가」가 아니라 <b>어느 팀이 언제 전멸했는가</b>이다.
	 */
	public static String wipeAnnouncement(String teamName, int seconds) {
		return "게임 오버! '" + teamName + "' 팀이 전멸했습니다. "
				+ seconds + "초 후 새 월드로 서버를 다시 엽니다.";
	}
}
