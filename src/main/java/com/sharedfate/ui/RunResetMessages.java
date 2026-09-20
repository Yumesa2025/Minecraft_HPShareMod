package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버 초기화({@code /shareteam reset})가 화면에 적는 글자들.
 *
 * <p>여태 서버를 처음 상태로 되돌리려면 남에게 <b>「서버 내리고 파일 넷을 손으로 지우세요」</b>
 * 라고 시켜야 했다. 그중 {@code sharedfate-team-roster.json} 을 빠뜨리면 <b>새 월드인데 팀이
 * 살아 있는</b> 상태가 되는데, 받아서 자기 서버를 여는 사람이 가장 잘 밟는 지뢰였다. 그래서
 * 명령 하나로 감쌌고, 그 명령이 되돌릴 수 없는 일을 하므로 <b>되묻는 글</b>이 필요해졌다.
 *
 * <h2>글자를 여기에 두는 이유</h2>
 * <p>실제로 지우는 {@code com.sharedfate.sync.RunResetCoordinator} 는 살아 있는 서버가 있어야
 * 해서 단위 시험으로 닿지 않는다. 사라지는 것을 <b>빠뜨리고 적는</b> 사고는 글자만 봐도 잡히므로,
 * {@link TeamDisbandWarning} 과 {@link GameOverCountdown} 이 그랬듯 글자만 떼어 여기에 둔다.
 * {@code RunResetMessagesTest} 가 이 목록을 붙들어 둔다.
 *
 * <h2>루프 스크립트 경고를 크게 적는 이유</h2>
 * <p>이 명령은 <b>서버를 종료</b>하고, 월드를 지우고 다시 여는 일은 재시작 루프 스크립트가
 * 한다. 스크립트 없이 {@code java -jar} 로 띄웠다면 서버는 <b>꺼진 채 돌아오지 않는다.</b>
 * 사람이 「막지는 말고 확인 문구에 크게 적는다」고 정했으므로, 이 경고는 문구의 장식이 아니라
 * 기능의 일부다.
 */
public final class RunResetMessages {
	/** {@code /shareteam} 뒤에 붙는 이 명령의 이름. 문구가 이 글자를 그대로 적는다. */
	public static final String RESET_COMMAND = "shareteam reset";

	/** 영어 확인 낱말. {@code /yes} 로도 {@code /shareteam yes} 로도 받는다. */
	public static final String CONFIRM_WORD_EN = "yes";
	/** 한글 확인 낱말. 자판을 바꾸지 않고 칠 수 있어야 한다. */
	public static final String CONFIRM_WORD_KO = "수락";

	/**
	 * 받아들이는 확인 낱말 전부. <b>순서도 뜻이 있다</b> — 문구에 적는 차례가 이것이다.
	 *
	 * <p>명령 트리도 이 목록을 그대로 돌며 가지를 만든다. 낱말을 여기서만 고치면 문구와 트리가
	 * 함께 따라오므로, 「문구는 {@code /수락} 이라 적는데 실제로는 안 먹는」 어긋남이 생기지
	 * 않는다.
	 */
	public static final List<String> CONFIRM_WORDS = List.of(CONFIRM_WORD_EN, CONFIRM_WORD_KO);

	/** 수락을 기다리는 시간(초). 사람이 정한 값이다. */
	public static final int TIMEOUT_SECONDS = 30;

	private RunResetMessages() {
	}

	/** 되묻는 글 전체를 한 덩이로. 채팅 한 번에 나간다. */
	public static String confirmation(int runNumber, int teamCount, int seconds) {
		return String.join("\n", confirmationLines(runNumber, teamCount, seconds));
	}

	/**
	 * 되묻는 글의 줄들.
	 *
	 * <p>지워지는 것 <b>다섯</b>을 모두 적는다 — 회차·팀·보유 증강·피해 기록·월드. 하나라도
	 * 빠지면 「초기화해도 저건 남는 줄 알았다」는 말이 나오고, 그때는 서버가 이미 새 월드로
	 * 다시 열린 뒤다.
	 *
	 * @param runNumber 지금 회차. 손상된 값이 들어와도 「0회차」 같은 글자가 나오지 않게 접는다
	 * @param teamCount 지금 있는 팀 수. 0 이면 개수 대신 없다고 적는다
	 * @param seconds   수락을 기다리는 시간(초)
	 */
	public static List<String> confirmationLines(int runNumber, int teamCount, int seconds) {
		int run = Math.max(1, runNumber);
		int wait = Math.max(1, seconds);
		List<String> lines = new ArrayList<>();
		lines.add("⚠ 서버를 처음 상태로 되돌립니다. 되돌릴 수 없습니다.");
		lines.add("지워지는 것:");
		lines.add("· 회차 — 지금 " + run + "회차가 1회차로 돌아갑니다");
		lines.add("· 팀 — " + teamLine(teamCount));
		lines.add("· 보유 증강 — 팀이 모은 증강이 전부 사라집니다");
		lines.add("· 피해 기록 — 회차별 피해·사망 기록이 전부 사라집니다");
		lines.add("· 월드 — 지금 월드를 지우고 새 월드로 다시 엽니다");
		// 막지 않는 대신 크게 적기로 한 경고. 두 줄로 끊어야 채팅 폭에서 잘리지 않는다.
		lines.add("⚠ 이 서버를 sharedfate-server-loop.ps1 없이 java -jar 로 띄웠다면");
		lines.add("  서버가 꺼진 뒤 스스로 다시 켜지지 않습니다. 직접 켜야 합니다.");
		lines.add("계속하려면 " + wait + "초 안에 " + topLevelCommands() + " 을 입력하세요.");
		lines.add("(" + subCommands() + " 도 같습니다. " + wait + "초가 지나면 저절로 취소됩니다.)");
		return List.copyOf(lines);
	}

	/**
	 * 팀 줄의 뒷부분.
	 *
	 * <p>팀이 없는 서버에 「0개 팀이 사라집니다」라고 적으면 거짓말이다. 그래도 줄 자체는
	 * 남긴다 — 다섯 가지 목록에서 하나가 통째로 빠지면 읽는 사람이 「팀은 안 지우나」로 읽는다.
	 */
	private static String teamLine(int teamCount) {
		return teamCount <= 0
				? "지금은 팀이 없습니다"
				: teamCount + "개 팀이 통째로 사라집니다 (명단 파일도 함께 비웁니다)";
	}

	/** {@code /yes 또는 /수락}. */
	private static String topLevelCommands() {
		return String.join(" 또는 ", CONFIRM_WORDS.stream().map(word -> "/" + word).toList());
	}

	/** {@code /shareteam yes · /st 수락}. 최상위 낱말이 다른 모드와 겹칠 때를 위한 길이다. */
	private static String subCommands() {
		return "/shareteam " + CONFIRM_WORD_EN + " · /st " + CONFIRM_WORD_KO;
	}

	/** 전원에게 한 번 나가는 공지. 카운트다운이 시작될 때 {@code WorldResetCoordinator} 가 뿌린다. */
	public static String announcement(int seconds) {
		return "운영자가 서버를 초기화했습니다. " + Math.max(1, seconds)
				+ "초 후 서버가 종료되고, 1회차 새 월드로 다시 엽니다.";
	}

	/** 30초가 지났을 때 요청한 사람에게만 간다. */
	public static String expired() {
		return "서버 초기화를 수락할 시간이 지났습니다. 아무것도 지우지 않았습니다."
				+ " 다시 하려면 /" + RESET_COMMAND + " 을 입력하세요.";
	}

	/** 기다리는 요청이 없는데 확인 낱말을 친 경우. */
	public static String nothingPending() {
		return "지금 수락을 기다리는 서버 초기화 요청이 없습니다."
				+ " 먼저 /" + RESET_COMMAND + " 을 입력하세요.";
	}

	/**
	 * 남의 요청을 대신 수락하려 한 경우.
	 *
	 * <p>친 사람만 수락할 수 있다. 그래야 서버 로그에 「누가 요청하고 누가 수락했는지」가 한
	 * 사람으로 남는다.
	 */
	public static String notRequester(String requesterName) {
		return "서버 초기화는 요청한 사람만 수락할 수 있습니다. 지금 요청한 사람: " + requesterName;
	}

	/** 남이 이미 되묻기를 띄워 둔 사이에 또 요청한 경우. */
	public static String alreadyWaiting(String requesterName, int seconds) {
		return "이미 " + requesterName + "님의 서버 초기화 요청이 " + Math.max(1, seconds)
				+ "초 남아 있습니다. 끝난 뒤에 다시 시도하세요.";
	}

	/** 이미 게임 오버 카운트다운이 도는 중이라면 초기화를 겹쳐 걸지 않는다. */
	public static String alreadyCountingDown() {
		return "이미 서버 종료 카운트다운이 돌고 있습니다. 서버가 다시 열린 뒤에 시도하세요.";
	}

	/**
	 * 표식을 못 써서 그만둔 경우.
	 *
	 * <p><b>아무것도 지우지 않았다</b>는 사실을 가장 먼저 적는다. 표식은 지우기 전에 쓰므로,
	 * 여기까지 왔다는 것은 팀도 기록도 그대로라는 뜻이다.
	 */
	public static String markerFailed() {
		return "초기화 표식을 만들지 못해 아무것도 지우지 않았습니다. 서버 로그를 확인해 주세요.";
	}

	/** 콘솔에서 친 경우. 팀을 해체하려면 실제 플레이어가 하나 필요하다. */
	public static String playerOnly() {
		return "서버 초기화는 게임 안에서 운영자가 직접 입력해야 합니다. 콘솔에서는 쓸 수 없습니다.";
	}
}
