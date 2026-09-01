package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「팀 만들기」 단추를 켤지 끌지.
 *
 * <p>예전에는 이름이 비어 있어도 단추가 눌렸고 눌러도 아무 일이 없었다. 그 판단을 화면
 * 안쪽에 두면 시험할 수 없어 여기로 내려 두었다.
 */
class TeamNameInputTest {

	@Test
	void 이름이_있으면_만들_수_있다() {
		assertTrue(TeamNameInput.valid("우리팀"));
		assertTrue(TeamNameInput.valid("a"));
	}

	@Test
	void 비어_있으면_못_만든다() {
		assertFalse(TeamNameInput.valid(""));
		assertFalse(TeamNameInput.valid(null));
	}

	/** 공백만 넣은 것도 비어 있는 것이다. 다듬고 나면 남는 것이 없다. */
	@Test
	void 공백만_넣은_것도_비어_있는_것이다() {
		assertFalse(TeamNameInput.valid("   "));
		assertFalse(TeamNameInput.valid("\t \n"));
	}

	/**
	 * 다듬는 방법이 서버와 같아야 한다.
	 *
	 * <p>서버({@code ShareTeamCommand})도 {@code trim()} 한 값을 쓴다. 두 곳이 다르게 다듬으면
	 * 화면에서 통과한 이름이 서버에서 거절당한다.
	 */
	@Test
	void 앞뒤_공백은_털어_낸다() {
		assertEquals("우리팀", TeamNameInput.normalize("  우리팀  "));
		assertEquals("우리 팀", TeamNameInput.normalize(" 우리 팀 "), "가운데 공백은 이름의 일부다");
		assertEquals("", TeamNameInput.normalize(null));
	}

	/** 길이 상한은 서버가 다듬은 뒤에 잰다. 화면도 같은 자리에서 잰다. */
	@Test
	void 상한을_넘으면_못_만든다() {
		assertTrue(TeamNameInput.valid("가".repeat(TeamNameInput.MAX_LENGTH)));
		assertFalse(TeamNameInput.valid("가".repeat(TeamNameInput.MAX_LENGTH + 1)));
		assertTrue(TeamNameInput.valid(" " + "가".repeat(TeamNameInput.MAX_LENGTH) + " "),
				"앞뒤 공백은 길이에 세지 않는다");
	}
}
