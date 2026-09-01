package com.sharedfate.ui;

/**
 * 「팀 만들기」를 누른 뒤 팀 화면이 무엇을 해야 하는지 정하는 계산.
 *
 * <p>{@link TeamCreationCycle} 과 같은 이유로 여기 있다 — 화면을 그리는 일은 클라이언트
 * 소스셋의 {@code TeamScreen} 이 하지만 시험 소스셋이 그쪽을 보지 못하므로 <b>순수 판정만</b>
 * 공용 소스셋으로 내려 두었다.
 *
 * <h2>단추를 누른 순간에는 팀이 없다</h2>
 * <p>이 화면의 단추는 전부 {@code /shareteam ...} 명령을 보낼 뿐이라, 눌린 그 틱에는 아직
 * 아무것도 달라지지 않는다. 팀이 생겼다는 사실은 몇 틱 뒤 서버가 보내 주는 동기화가 알려
 * 준다. 그래서 화면은 <b>보냈다</b>는 것만 기억해 두고({@code awaitingResult}) 매 틱
 * {@link #created} 로 결과가 왔는지 본다.
 *
 * <h2>실패는 따로 알려 주지 않는다</h2>
 * <p>이름이 겹치거나 이미 팀에 속해 있으면 서버가 채팅으로 거절 사유를 보내고 끝이다.
 * 그 경우 팀이 생기지 않으므로 {@link #created} 는 계속 거짓이고, 화면은 <b>팀 만들기 양식
 * 그대로</b> 남는다. 적던 이름을 지우는 일도 성공을 확인한 뒤에만 한다
 * ({@link #nameAfterResult}) — 실패했는데 이름까지 사라지면 다시 처음부터 적어야 한다.
 */
public final class TeamCreationFlow {
	private TeamCreationFlow() {
	}

	/**
	 * 「팀 만들기」 명령을 실제로 보냈는가. 보냈다면 화면은 결과를 기다리는 상태가 된다.
	 *
	 * <p>{@code TeamScreen} 이 보내기 직전에 하는 검사와 <b>같은 검사</b>여야 한다. 보내지도
	 * 않았는데 기다리기 시작하면, 나중에 초대를 받아 팀에 들어가는 순간 만들기가 성공한 것으로
	 * 잘못 읽힌다.
	 */
	public static boolean submitted(String typedName) {
		return TeamNameInput.valid(TeamNameInput.normalize(typedName));
	}

	/**
	 * 기다리던 결과가 「팀이 생겼다」로 확인됐는가.
	 *
	 * @param awaitingResult 만들기 명령을 보내 두고 아직 결과를 못 본 상태인가
	 * @param inTeam         지금 팀에 속해 있는가(서버 동기화가 채워 준 값)
	 */
	public static boolean created(boolean awaitingResult, boolean inTeam) {
		return awaitingResult && inTeam;
	}

	/**
	 * 결과를 보고 난 뒤 이름 칸에 남겨 둘 글자.
	 *
	 * <p>성공했으면 비운다 — 양식 자체가 사라지고, 나중에 팀을 나와 다시 이 탭을 볼 때 예전
	 * 팀 이름이 적혀 있으면 그것이 무슨 뜻인지 알 수 없다. 실패했으면 <b>고스란히 남긴다.</b>
	 */
	public static String nameAfterResult(boolean awaitingResult, boolean inTeam, String typedName) {
		return created(awaitingResult, inTeam) ? "" : typedName;
	}
}
