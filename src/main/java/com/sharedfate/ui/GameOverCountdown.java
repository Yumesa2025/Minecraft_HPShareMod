package com.sharedfate.ui;

/**
 * 게임 오버 카운트다운의 <b>순수 계산</b>.
 *
 * <p>팀이 전멸하면 게임 오버 화면 한가운데에서 빨간 숫자가 5부터 1까지 내려가고, 다 내려가면
 * 서버가 종료된다. 숫자를 실제로 그리는 것은 {@code GameOverHud}(클라이언트), 서버를 멈추는
 * 것은 {@code com.sharedfate.sync.WorldResetCoordinator} 이고, 여기에는 <b>남은 틱에서 화면에
 * 적을 숫자를 뽑는 계산과 문구</b>만 있다.
 *
 * <h2>같은 카운트다운이 두 가지 이유로 돈다</h2>
 * <p>서버를 내리는 절차는 전멸이든 운영자 초기화든 똑같은 것을 쓴다({@code WorldResetCoordinator}
 * 의 {@code arm}). 그래서 <b>화면에 적을 글자만</b> {@link Reason} 으로 가른다. 여기가 갈라지기
 * 전에는 초기화 때도 살아 있는 팀원 전원의 화면에 「게임 오버」가 떴다 — 아무도 죽지 않았는데
 * 전멸한 줄 아는 것이다.
 */
public final class GameOverCountdown {
	public static final int TICKS_PER_SECOND = 20;

	/** 카운트다운 기본 길이(초). */
	public static final int DEFAULT_SECONDS = 5;

	/** 전멸일 때 화면 한가운데 큰 글씨로 뜨는 한 줄. */
	public static final String TITLE = "게임 오버";

	/**
	 * 운영자 초기화일 때 그 자리에 뜨는 한 줄.
	 *
	 * <p>되묻는 글({@link RunResetMessages#confirmationLines})도 채팅 공지
	 * ({@link RunResetMessages#announcement})도 이 일을 「초기화」라고 부른다. 화면만 다른
	 * 낱말을 쓰면 같은 일이 두 가지로 보인다. 큰 글씨라 짧아야 해서 네 글자로 맞췄다.
	 */
	public static final String RESET_TITLE = "서버 초기화";

	/**
	 * 이 카운트다운이 도는 이유.
	 *
	 * <p><b>패킷에 실린다</b>({@code WorldResetPayload}). 클라이언트에는 두 경로를 가를 다른
	 * 근거가 없다 — 회차도 남은 틱도 양쪽이 똑같이 채운다.
	 *
	 * <p>{@code boolean} 한 칸이 아니라 열거형인 이유는 <b>세 번째 이유가 생길 수 있기</b>
	 * 때문이다. 승리로 끝나는 회차든 운영자가 손으로 거는 점검이든, 그때 {@code boolean} 은
	 * 「전멸인가 아닌가」밖에 못 말한다. 여기에 상수를 더하면 {@link #title}·
	 * {@link #shutdownNotice} 의 switch 가 <b>컴파일 단계에서</b> 글자를 정하라고 막는다.
	 */
	public enum Reason {
		/** 팀이 전멸해서 회차가 끝났다. */
		TEAM_WIPE("team_wipe"),
		/** 운영자가 {@code /shareteam reset} 으로 서버를 처음 상태로 되돌리는 중이다. */
		RUN_RESET("run_reset");

		private final String id;

		Reason(String id) {
			this.id = id;
		}

		/**
		 * 패킷에 실리는 이름.
		 *
		 * <p>상수 차례가 아니라 이름을 싣는다. 가운데에 상수를 끼워 넣어도 예전에 쓰던 뜻이
		 * 조용히 다른 경로로 옮겨 가지 않는다.
		 */
		public String id() {
			return id;
		}

		/**
		 * 패킷에서 읽은 이름을 되돌린다. <b>모르는 이름이면 전멸로 본다.</b>
		 *
		 * <p>칸이 늘어날 때 규약 번호를 함께 올리므로, 판이 맞는 클라이언트에는 모르는 이름이
		 * 올 수 없다. 여기까지 오는 것은 손상된 묶음뿐이고 그때 고를 수 있는 답은 둘 다
		 * 추측이다. 원래 하던 대로 두는 쪽을 골랐다 — 이 카운트다운이 도는 이유는 거의
		 * 언제나 전멸이다.
		 */
		public static Reason fromId(String raw) {
			if (raw == null) {
				return TEAM_WIPE;
			}
			String normalized = raw.trim();
			for (Reason reason : values()) {
				if (reason.id.equals(normalized)) {
					return reason;
				}
			}
			return TEAM_WIPE;
		}
	}

	private GameOverCountdown() {
	}

	/**
	 * 화면 한가운데 큰 글씨로 뜨는 한 줄.
	 *
	 * <p>{@code default} 를 두지 않는다. {@link Reason} 에 상수를 더하는 사람이 <b>글자를 정하지
	 * 않고는 빌드를 통과할 수 없게</b> 하려는 것이다.
	 */
	public static String title(Reason reason) {
		return switch (reason) {
			case TEAM_WIPE -> TITLE;
			case RUN_RESET -> RESET_TITLE;
		};
	}

	/**
	 * 사망 화면의 제목.
	 *
	 * <p>회차를 붙이는 것은 전멸 경로뿐이다. 전멸은 <b>그 회차가 끝난 사건</b>이라 몇 회차였는지가
	 * 남길 값이지만, 초기화는 회차를 통째로 버리는 일이라 「서버 초기화 · 7회차」라고 적으면
	 * 7회차가 이어진다는 뜻으로 읽힌다.
	 *
	 * <p>초기화 때도 사망 화면에 사람이 앉아 있을 수 있다 — 전멸 뒤 {@code resetWorldOnTeamDeath}
	 * 가 꺼져 있어 부활을 안 누른 사람, 팀에 속하지 않은 채 죽은 접속자. 그 사람들의 화면이
	 * 이 자리를 지난다.
	 */
	public static String screenTitle(Reason reason, int runNumber) {
		return switch (reason) {
			case TEAM_WIPE -> TITLE + " · " + Math.max(1, runNumber) + "회차";
			case RUN_RESET -> RESET_TITLE;
		};
	}

	/**
	 * 카운트다운 아래 작은 글씨. 숫자가 무엇을 세는 것인지 적는다.
	 *
	 * <p>초기화 쪽은 <b>서버가 돌아온다</b>는 것까지 적는다. 제목이 「서버 초기화」라도 그 아래가
	 * 「종료됩니다」로 끝나면 읽는 사람은 여기서 끝나는 줄 안다.
	 */
	public static String shutdownNotice(Reason reason, int seconds) {
		return switch (reason) {
			case TEAM_WIPE -> seconds + "초 후 서버가 종료됩니다";
			case RUN_RESET -> seconds + "초 후 서버가 종료되고 새 월드로 다시 열립니다";
		};
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
	 * @param maxTicks 허용할 최대 틱. 서버 설정의 상한과 같은 값을 넘긴다.
	 */
	public static int sanitizeTicks(int ticksRemaining, int maxTicks) {
		if (ticksRemaining <= 0) {
			return 0;
		}
		return Math.min(ticksRemaining, Math.max(0, maxTicks));
	}

	/**
	 * 전멸을 알리는 채팅 한 줄.
	 *
	 * <p>화면 연출과 따로 한 번만 나간다.
	 */
	public static String wipeAnnouncement(String teamName, int seconds) {
		return "게임 오버! '" + teamName + "' 팀이 전멸했습니다. "
				+ seconds + "초 후 새 월드로 서버를 다시 엽니다.";
	}
}
