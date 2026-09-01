package com.sharedfate.ui;

/**
 * 팀 만들기 화면의 이름 칸이 지금 쓸 만한 값을 담고 있는가.
 *
 * <p>{@link TeamCreationCycle} 와 같은 이유로 여기 있다 — 판단을 화면 코드에 묻어 두면
 * 시험할 수 없다. 화면은 이 답으로 「팀 만들기」 단추를 켜고 끈다.
 *
 * <h2>왜 단추를 끄는가</h2>
 * <p>예전에는 이름이 비어 있어도 단추가 눌렸고, 눌러도 <b>아무 일도 일어나지 않았다.</b>
 * 화면이 명령을 만들다 말고 조용히 돌아섰기 때문이다. 눌리는데 아무 일도 없는 단추는
 * 「모드가 고장 났다」로 읽힌다. 아예 못 누르게 하고 왜 못 누르는지를 한 줄로 적는 편이 낫다.
 *
 * <h2>서버가 보는 것과 여기서 보는 것</h2>
 * <p>서버({@code ShareTeamCommand.createTeam})는 이름을 다듬은 뒤 <b>세 가지</b>를 본다 —
 * 비어 있지 않은가, {@value #MAX_LENGTH}자 이하인가, 같은 이름의 팀이 이미 없는가.
 * 앞의 둘은 화면이 미리 걸러 준다. <b>마지막 하나는 걸러 줄 수 없다</b> — 클라이언트는 서버에
 * 어떤 팀이 있는지 모른다. 그것만은 눌러 본 뒤 서버가 돌려주는 문구로 알게 된다.
 *
 * <p>길이 상한은 이름 칸 자체가 이미 막고 있지만({@code EditBox.setMaxLength}) 여기서도
 * 본다. 두 곳이 같은 값을 보는 것이 아니라, <b>이 함수 하나만 보고도 판단이 끝나야</b>
 * 부르는 쪽이 늘어나도 안전하다.
 */
public final class TeamNameInput {
	/** 이름 길이 상한. 서버의 {@code ShareTeamCommand.MAX_TEAM_NAME_LENGTH} 와 같아야 한다. */
	public static final int MAX_LENGTH = 32;

	/** 못 누르는 까닭. 단추 아래에 그대로 적는다. */
	public static final String EMPTY_HINT = "팀 이름을 적어야 「팀 만들기」를 누를 수 있습니다.";

	private TeamNameInput() {
	}

	/**
	 * 실제로 서버에 보낼 이름. 앞뒤 공백을 턴다.
	 *
	 * <p>서버도 {@code trim()} 한 값을 쓴다. 두 곳이 다르게 다듬으면 화면에서 통과한 이름이
	 * 서버에서 거절당한다.
	 */
	public static String normalize(String raw) {
		return raw == null ? "" : raw.trim();
	}

	/**
	 * 이 이름으로 팀을 만들 수 있는가.
	 *
	 * <p>공백만 넣은 것은 비어 있는 것으로 본다 — 다듬고 나면 남는 것이 없기 때문이다.
	 */
	public static boolean valid(String raw) {
		String name = normalize(raw);
		return !name.isEmpty() && name.length() <= MAX_LENGTH;
	}
}
