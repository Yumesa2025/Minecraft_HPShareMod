package com.sharedfate.ui;

/**
 * 팀 화면의 「게임 시작」 단추가 무엇을 보여 주고 무엇을 보내는지.
 *
 * <p>이 단추가 하는 일은 <b>되돌릴 수 없다.</b> 팀이 가진 아이템이 전부 사라지고 시각이 1일차
 * 아침으로 돌아간다. 팀원 하나가 아직 접속하지 않았는데 눌러 버리면 그것으로 끝이다. 그래서
 * 한 번 누르면 글자가 경고로 바뀌고, 그 상태에서 한 번 더 눌러야 명령이 나간다.
 *
 * <p>여기서 정하는 것은 <b>보여 주기</b>뿐이다. 리더인지, 이미 시작했는지는 서버가
 * {@code GameStartManager.start} 에서 처음부터 다시 따진다.
 */
public final class GameStartButton {
	/** 단추가 실제로 보내는 명령. {@code /shareteam} 뒤에 붙는다. */
	public static final String CONFIRM_COMMAND = "start confirm";

	private GameStartButton() {
	}

	/**
	 * 단추를 그릴지.
	 *
	 * <p>팀이 있고, 내가 리더이고, 아직 시작하지 않았을 때만이다. 리더가 아닌 사람에게 보이면
	 * 눌러도 서버가 거부만 하는 단추가 되고, 이미 시작한 팀에게 보이면 「다시 시작하면 아이템이
	 * 날아간다」는 사고를 부른다.
	 */
	public static boolean visible(boolean inTeam, boolean leader, boolean runStarted) {
		return inTeam && leader && !runStarted;
	}

	/** 단추에 적을 글자. */
	public static String label(boolean confirming) {
		return confirming
				? "한 번 더 누르면 시작 — 모든 아이템이 사라집니다"
				: "게임 시작";
	}

	/**
	 * 시작을 기다리는 동안 보여 줄 회차 번호. <b>언제나 1이다.</b>
	 *
	 * <p>{@code GameStartManager.autoStarts} 가 2회차부터는 단추 없이 회차를 켜므로,
	 * 「아직 시작하지 않았다」는 상태는 1회차에서만 존재한다. 클라이언트는 회차 번호를 받지
	 * 않는데(어느 동기화 묶음에도 들어 있지 않다) 그래도 번호를 적을 수 있는 것이 이 때문이다.
	 */
	public static final int WAITING_RUN_NUMBER = 1;

	/**
	 * 팀 화면 「현황」 탭에 적을 한 줄.
	 *
	 * <p>판 폭이 300px 이라 한 줄에 들어갈 만큼만 적는다. 채팅용 긴 문장은
	 * {@link #waitingChatLine}, 보스바용 짧은 문장은 {@code RunProgressManager.label} 이다.
	 */
	public static String waitingNotice(boolean leader) {
		return leader
				? WAITING_RUN_NUMBER + "회차 — 「팀」 탭에서 「게임 시작」을 눌러 주세요."
				: WAITING_RUN_NUMBER + "회차 — 리더가 게임을 시작할 때까지 기다려 주세요.";
	}

	/**
	 * {@code /shareteam status} 의 회차 한 줄. 시작 전일 때만 쓴다.
	 */
	public static String waitingChatLine(boolean leader) {
		return leader
				? WAITING_RUN_NUMBER + "회차 — 아직 시작하지 않았습니다."
						+ " /shareteam start confirm 을 입력하거나 팀 화면(/st)의"
						+ " 「게임 시작」을 눌러 주세요."
				: WAITING_RUN_NUMBER + "회차 — 아직 시작하지 않았습니다."
						+ " 리더가 게임을 시작할 때까지 기다려 주세요.";
	}
}
