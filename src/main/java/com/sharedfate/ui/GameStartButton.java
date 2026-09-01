package com.sharedfate.ui;

/**
 * 팀 화면의 「게임 시작」 단추가 무엇을 보여 주고 무엇을 보내는지.
 *
 * <p>{@link PerkRerollButton}·{@link TeamCreationCycle} 과 같은 이유로 여기 있다 — 화면
 * ({@code TeamScreen})은 {@code src/client} 에 있어 시험 소스셋이 볼 수 없으므로 <b>순수
 * 계산만</b> 공용 소스셋으로 내려 둔다.
 *
 * <h2>왜 두 번 눌러야 하는가</h2>
 * <p>이 단추가 하는 일은 <b>되돌릴 수 없다.</b> 팀이 가진 아이템이 전부 사라지고 시각이 1일차
 * 아침으로 돌아간다. 팀원 하나가 아직 접속하지 않았는데 눌러 버리면 그것으로 끝이다. 그래서
 * 한 번 누르면 글자가 경고로 바뀌고, 그 상태에서 한 번 더 눌러야 명령이 나간다.
 * {@code /shareteam disband} 가 {@code confirm} 을 요구하는 것과 같은 방식이고, 실제로 보내는
 * 명령도 {@link #CONFIRM_COMMAND} 하나뿐이라 <b>서버 쪽 확인 절차를 건너뛰지 않는다.</b>
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

	/**
	 * 단추에 적을 글자.
	 *
	 * <p>확인 단계에서는 <b>무엇을 잃는지</b>가 글자에 있어야 한다. 「정말요?」만 적으면 무엇이
	 * 정말인지 알 수 없다.
	 */
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
	 * <p>시작하기 전에는 회차가 「진행 중」이 아니라는 사실이 기본 탭에서도 보여야 한다. 다만
	 * 단추는 이 탭에 두지 않는다 — 창을 열면 바로 보이는 자리에 되돌릴 수 없는 단추를 두면
	 * 잘못 누른다. 그래서 어디로 가야 하는지만 알려 준다.
	 *
	 * <p>「N회차 시작 대기」처럼 <b>상태만</b> 적으면 읽는 사람이 할 일을 알 수 없다. 회차 번호는
	 * 그대로 두되 뒤에는 <b>지금 무엇을 하면 되는지</b>를 적는다. 리더가 아닌 사람에게 시작 방법을
	 * 알려 줘도 눌릴 단추가 없으므로, 그 사람이 실제로 할 수 있는 것(기다리기)을 적는다.
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
	 *
	 * <p>채팅은 자리가 넉넉하므로 <b>두 가지 길을 모두</b> 적는다 — 명령과 팀 화면. 화면 쪽은
	 * 한 줄에 들어가야 해서 하나만 적는다({@link #waitingNotice}).
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
