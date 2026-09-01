package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「팀 만들기」를 누른 뒤 화면이 무엇을 해야 하는가.
 *
 * <p>예전에는 팀을 만들면 창이 닫혔고, 「게임 시작」을 누르려면 창을 다시 열어야 했다.
 * 이제 창을 열어 둔 채 결과를 기다리므로 <b>언제 결과가 왔는지</b>를 가릴 판정이 필요하다.
 */
class TeamCreationFlowTest {

	/** 명령을 보냈는지의 기준은 이름 칸의 검사와 같아야 한다. */
	@Test
	void 이름이_쓸_만할_때만_보낸_것으로_친다() {
		assertTrue(TeamCreationFlow.submitted("우리팀"));
		assertTrue(TeamCreationFlow.submitted("  우리팀  "), "앞뒤 공백은 털어 낸 뒤에 본다");
		assertFalse(TeamCreationFlow.submitted(""));
		assertFalse(TeamCreationFlow.submitted("   "));
		assertFalse(TeamCreationFlow.submitted(null));
	}

	/**
	 * 누른 그 틱에는 아직 팀이 없다.
	 *
	 * <p>단추는 명령을 보낼 뿐이고 팀이 생겼다는 사실은 몇 틱 뒤 동기화가 알려 준다. 보낸
	 * 직후를 성공으로 읽으면 화면이 팀이 있는 척하는 상태를 그린다.
	 */
	@Test
	void 보낸_직후에는_아직_성공이_아니다() {
		assertFalse(TeamCreationFlow.created(true, false));
	}

	@Test
	void 동기화가_와서_팀이_생기면_성공이다() {
		assertTrue(TeamCreationFlow.created(true, true));
	}

	/**
	 * 보내지 않았는데 팀에 들어간 것은 성공이 아니다.
	 *
	 * <p>남이 초대해서 들어간 경우다. 만들기 양식을 건드린 적이 없으므로 정리할 것도 없다.
	 */
	@Test
	void 초대로_들어간_것은_만들기_성공이_아니다() {
		assertFalse(TeamCreationFlow.created(false, true));
		assertFalse(TeamCreationFlow.created(false, false));
	}

	/**
	 * 실패해도 적던 이름이 남아야 한다.
	 *
	 * <p>이름이 겹쳤을 때가 이 경우다. 서버는 채팅으로 사유만 알려 주고 팀은 생기지 않는데,
	 * 여기서 이름까지 지우면 사람이 서른두 자를 다시 적어야 한다.
	 */
	@Test
	void 실패하면_적던_이름을_지우지_않는다() {
		assertEquals("우리팀", TeamCreationFlow.nameAfterResult(true, false, "우리팀"));
		assertEquals("우리팀", TeamCreationFlow.nameAfterResult(false, false, "우리팀"));
	}

	/** 성공하면 비운다. 양식 자체가 사라지고, 나중에 팀을 나왔을 때 옛 이름이 남으면 안 된다. */
	@Test
	void 성공하면_이름_칸을_비운다() {
		assertEquals("", TeamCreationFlow.nameAfterResult(true, true, "우리팀"));
	}
}
