package com.sharedfate.ui;

import java.util.List;

/**
 * 「팀 해체」를 누르면 뜨는 경고창이 무엇을 적고 무엇을 보내는지.
 *
 * <p>이 동작은 <b>되돌릴 수 없다.</b> 서버 쪽 {@code InventorySwapper.disbandTeam} 은 팀이 가진
 * 아이템을 <b>바닥에 쏟지 않고 통째로 지우고</b>, 증강 효과를 사람에게서 걷어내고, 경험치를 0
 * 으로 되돌린다. 「게임 시작」과 달리 다시 만들 팀조차 남지 않는다.
 *
 * <p>그래서 「게임 시작」이 쓰는 <b>두 번 누르기</b>({@link GameStartButton})로는 모자라다고 봤다.
 * 그 방식은 단추 글자 한 줄로만 경고하는데, 여기서 사라지는 것은 네 가지라 한 줄에 안 들어간다.
 * 창을 하나 띄워 네 줄을 다 읽히고, 그 창의 확인 단추를 누르게 한다.
 *
 * <h2>글자를 여기에 두는 이유</h2>
 * <p>화면 클래스는 {@code Minecraft.getInstance()} 없이는 시험에서 만들 수 없다. 사라지는 것을
 * 빠뜨리거나 판 밖으로 넘치는 문구를 적는 사고는 <b>글자만 봐도</b> 잡히므로, 글자와 명령만
 * 떼어 여기에 둔다. {@code TeamDisbandWarningTest} 가 이 목록을 붙들어 둔다.
 */
public final class TeamDisbandWarning {
	/** 확인 단추가 실제로 보내는 명령. {@code /shareteam} 뒤에 붙는다. */
	public static final String CONFIRM_COMMAND = "disband confirm";

	/** 창 제목. 내레이터가 읽는 줄이기도 하다. */
	public static final String TITLE = "팀 해체 확인";

	/** 경고 줄들 위에 굵게 적는 물음. */
	public static final String QUESTION = "정말 팀을 해체하시겠습니까?";

	/** 되돌릴 수 없는 쪽 단추. */
	public static final String CONFIRM_LABEL = "팀 해체";
	/** 빠져나오는 쪽 단추. ESC 도 같은 곳으로 간다. */
	public static final String CANCEL_LABEL = "취소";

	/**
	 * 확인 단추가 잠겨 있는 시간(틱).
	 *
	 * <p>「팀 해체」를 부르는 단추와 이 창의 확인 단추는 화면이 짧을 때 세로로 가깝게 선다.
	 * 잠그지 않으면 <b>두 번 딸깍</b>한 것만으로 팀이 사라진다 — 경고창을 띄운 뜻이 통째로
	 * 없어지는 사고다. 바닐라 {@code ConfirmScreen.setDelay} 가 링크를 여는 창에서 쓰는 장치와
	 * 같은 것이고, 길이는 겹쳐 눌린 딸깍만 먹을 만큼으로 짧게 잡았다.
	 */
	public static final int CONFIRM_DELAY_TICKS = 10;

	/**
	 * 사라지는 것들. <b>다섯 줄이 다 있어야 한다.</b>
	 *
	 * <p>아이템 · 증강 · 회차 진행 · 스폰 귀환 · 되돌릴 수 없음. 하나라도 빠지면 「해체해도
	 * 증강은 남는 줄 알았다」 같은 말이 나오고, 그때는 이미 되돌릴 수 없다.
	 *
	 * <p>아이템 줄에 「바닥에 떨어지지 않습니다」를 굳이 붙인 것은, 이 모드의 「팀 나가기」가
	 * 개인 아이템을 <b>드랍</b>하기 때문이다. 같은 화면의 옆 단추가 드랍이라 해체도 드랍이라고
	 * 읽기 쉽다.
	 *
	 * <p>스폰 줄은 <b>사라지는 것이 아니라 일어나는 일</b>이라 「되돌릴 수 없습니다」 바로 위에
	 * 둔다. 멀리 나가 있던 사람에게는 그 자체가 큰 일이라, 경고 없이 끌려가면 안 된다.
	 */
	private static final List<String> LINES = List.of(
			"· 가지고 있던 아이템이 전부 사라집니다. 바닥에 떨어지지 않습니다.",
			"· 지금까지 모은 증강이 전부 사라집니다.",
			"· 팀 레벨과 회차 진행 상황이 사라지고 경험치도 0이 됩니다.",
			"· 팀원 전원이 월드 스폰으로 돌아갑니다.",
			"· 되돌릴 수 없습니다. 다시 만들어도 처음부터 시작합니다.");

	private TeamDisbandWarning() {
	}

	/** 경고 줄들. 적은 순서 그대로 위에서 아래로 그린다. */
	public static List<String> lines() {
		return LINES;
	}

	/**
	 * 확인 단추를 이제 누를 수 있는가.
	 *
	 * @param ticksOpen 창이 떠 있은 틱 수. 0이면 방금 떴다
	 */
	public static boolean confirmActive(int ticksOpen) {
		return ticksOpen >= CONFIRM_DELAY_TICKS;
	}
}
