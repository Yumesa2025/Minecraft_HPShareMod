package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀 화면 「게임 시작」 단추의 순수 계산.
 *
 * <p>화면은 {@code src/client} 에 있어 시험 소스셋이 보지 못하므로, 여기서 확인하는 것은
 * <b>언제 보이고 무엇을 보내는가</b>다. 되돌릴 수 없는 단추라 「보이면 안 될 때 보이지 않는지」가
 * 가장 중요하다.
 */
class GameStartButtonTest {

	@Test
	void 팀이_있고_리더이고_아직_시작하지_않았을_때만_보인다() {
		assertTrue(GameStartButton.visible(true, true, false));
	}

	@Test
	void 이미_시작한_팀에게는_보이지_않는다() {
		// 여기가 뚫리면 진행 중인 회차의 아이템이 통째로 날아간다.
		assertFalse(GameStartButton.visible(true, true, true));
	}

	@Test
	void 리더가_아니면_보이지_않는다() {
		assertFalse(GameStartButton.visible(true, false, false));
	}

	@Test
	void 팀이_없으면_보이지_않는다() {
		assertFalse(GameStartButton.visible(false, true, false));
		assertFalse(GameStartButton.visible(false, false, false));
	}

	@Test
	void 확인_단계의_글자는_무엇을_잃는지_적는다() {
		String confirming = GameStartButton.label(true);
		assertTrue(confirming.contains("아이템"), confirming);
		assertNotEquals(GameStartButton.label(false), confirming,
				"확인 단계인지 글자로 구분되지 않으면 두 번 누르게 한 뜻이 없다");
	}

	@Test
	void 보내는_명령은_확인_낱말을_반드시_포함한다() {
		// 서버는 confirm 없는 start 를 안내로만 받는다. 화면이 그 절차를 건너뛰면 안 된다.
		assertEquals("start confirm", GameStartButton.CONFIRM_COMMAND);
	}

	@Test
	void 대기_안내는_리더인지에_따라_다르다() {
		assertTrue(GameStartButton.waitingNotice(true).contains("「팀」 탭"),
				"리더에게는 어디서 시작하는지 알려 줘야 한다");
		assertTrue(GameStartButton.waitingNotice(false).contains("리더"),
				"리더가 아니면 무엇을 기다리는지 알려 줘야 한다");
	}

	/**
	 * 대기 안내는 <b>상태가 아니라 할 일</b>을 적는다.
	 *
	 * <p>「N회차 시작 대기」로는 무엇을 해야 하는지 알 수 없어 아무도 시작하지 않은 채 돌아다녔다.
	 * 회차 번호는 그대로 남는다 — 지금 몇 회차인지는 상태가 아니라 정보다.
	 */
	@Test
	void 대기_안내는_회차_번호와_할_일을_함께_적는다() {
		for (boolean leader : new boolean[] {true, false}) {
			for (String line : new String[] {
					GameStartButton.waitingNotice(leader), GameStartButton.waitingChatLine(leader)}) {
				assertTrue(line.startsWith(GameStartButton.WAITING_RUN_NUMBER + "회차"), line);
				assertFalse(line.contains("시작 대기"), "상태만 나열하면 안 된다: " + line);
				assertTrue(line.contains("주세요"), "무엇을 하면 되는지 적어야 한다: " + line);
			}
		}
	}

	/** 리더가 아닌 사람에게는 그 사람이 <b>할 수 있는 것</b>만 적는다. */
	@Test
	void 리더가_아니면_기다리라고만_적는다() {
		String notice = GameStartButton.waitingNotice(false);
		String chat = GameStartButton.waitingChatLine(false);
		assertTrue(notice.contains("기다려 주세요"), notice);
		assertTrue(chat.contains("기다려 주세요"), chat);
		assertFalse(chat.contains("/shareteam start"),
				"누를 수 없는 사람에게 명령을 알려 주면 눌러도 거부만 당한다");
	}

	/** 채팅은 자리가 넉넉하므로 두 가지 길을 모두 적고, 화면 한 줄은 하나만 적는다. */
	@Test
	void 채팅_안내가_화면_한_줄보다_길다() {
		assertTrue(GameStartButton.waitingChatLine(true).contains("/shareteam start confirm"));
		assertTrue(GameStartButton.waitingChatLine(true).length()
						> GameStartButton.waitingNotice(true).length(),
				"보스바 · 화면 · 채팅은 자리에 맞게 길이가 달라야 한다");
	}
}
