package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「팀 해체」 경고창이 <b>사라지는 것을 빠짐없이</b> 적는지.
 *
 * <p>이 창을 지나면 되돌릴 수 없다. 서버 쪽 {@code InventorySwapper.disbandTeam} 은 팀 아이템을
 * 바닥에 쏟지 않고 <b>지우고</b>, 증강 효과를 걷어내고, 경험치를 0으로 되돌린다. 문구가 그것과
 * 어긋나면 사람은 「드랍되겠지」 하고 누른다 — 같은 화면의 옆 단추인 「팀 나가기」가 실제로
 * 드랍이라 더 그렇다.
 *
 * <p>화면 자체는 {@code Minecraft.getInstance()} 없이 만들 수 없어 시험에서 띄우지 못한다.
 * 그래서 글자와 잠금 판정만 {@link TeamDisbandWarning} 으로 떼어 두었고, 여기서 그것을 붙든다.
 */
class TeamDisbandWarningTest {
	/**
	 * 경고 줄이 들어가야 할 폭.
	 *
	 * <p>{@code TeamDisbandConfirmScreen.PANEL_WIDTH}(300)에서 좌우 여백을 뺀 값이다. 판 폭은
	 * {@code TeamScreen} 과 같은 300 으로 맞춰 두었다.
	 */
	private static final int PANEL_TEXT_WIDTH = 300 - 8;

	@Test
	void 사라지는_것_넷이_모두_적혀_있다() {
		String all = String.join("\n", TeamDisbandWarning.lines());

		assertTrue(all.contains("아이템"), "아이템이 사라진다는 말이 없다");
		assertTrue(all.contains("증강"), "증강이 사라진다는 말이 없다");
		assertTrue(all.contains("회차"), "회차 진행이 사라진다는 말이 없다");
		assertTrue(all.contains("되돌릴 수 없"), "되돌릴 수 없다는 말이 없다");
	}

	/**
	 * 「드랍된다」고 읽힐 여지를 남기지 않는다.
	 *
	 * <p>바로 옆 「팀 나가기」가 개인 아이템을 떨어뜨리므로, 해체도 그럴 것이라고 짐작하기 쉽다.
	 * 실제로는 통째로 지워진다.
	 */
	@Test
	void 아이템이_바닥에_떨어지지_않는다는_것까지_적는다() {
		String all = String.join("\n", TeamDisbandWarning.lines());

		assertTrue(all.contains("떨어지지 않"), "떨어지지 않는다는 말이 없다");
	}

	@Test
	void 빈_줄이_없다() {
		for (String line : TeamDisbandWarning.lines()) {
			assertFalse(line.isBlank(), "빈 줄이 섞여 있다");
		}
		assertFalse(TeamDisbandWarning.QUESTION.isBlank(), "물음이 비어 있다");
		assertFalse(TeamDisbandWarning.TITLE.isBlank(), "제목이 비어 있다");
		assertFalse(TeamDisbandWarning.CONFIRM_LABEL.isBlank(), "확인 단추 글자가 비어 있다");
		assertFalse(TeamDisbandWarning.CANCEL_LABEL.isBlank(), "취소 단추 글자가 비어 있다");
	}

	/**
	 * 줄이 판 밖으로 넘치지 않는다.
	 *
	 * <p>이 창은 증강 목록과 달리 <b>줄을 접지 않는다.</b> 넷을 한 눈에 훑게 하려고 한 줄에
	 * 하나씩 놓았고, 넘치면 그대로 판 밖으로 나가 잘린 채 그려진다. 하필 잘리는 것은 줄 끝이라
	 * 「…사라집니다」가 날아간다.
	 */
	@Test
	void 줄이_판_폭_안에_들어간다() {
		for (String line : TeamDisbandWarning.lines()) {
			assertTrue(FakeFont.width(line) <= PANEL_TEXT_WIDTH,
					"판을 넘치는 줄이 있다: " + line + " (" + FakeFont.width(line) + "px)");
		}
		assertTrue(FakeFont.width(TeamDisbandWarning.QUESTION) <= PANEL_TEXT_WIDTH,
				"물음이 판을 넘친다");
	}

	/** 단추 둘이 판을 반씩 나눠 쓴다. 글자가 그 절반 안에 들어가야 잘리지 않는다. */
	@Test
	void 단추_글자가_절반_폭_안에_들어간다() {
		int half = 300 / 2 - 2;

		assertTrue(FakeFont.width(TeamDisbandWarning.CONFIRM_LABEL)
				+ InventoryTeamButton.LABEL_PADDING <= half, "확인 단추 글자가 넘친다");
		assertTrue(FakeFont.width(TeamDisbandWarning.CANCEL_LABEL)
				+ InventoryTeamButton.LABEL_PADDING <= half, "취소 단추 글자가 넘친다");
	}

	/**
	 * 확인 단추는 창이 뜬 <b>그 순간</b> 눌리지 않는다.
	 *
	 * <p>해체를 부른 단추와 이 창의 확인 단추가 세로로 가까이 설 수 있어, 잠그지 않으면 딸깍
	 * 두 번에 팀이 사라진다. 경고창을 띄운 뜻이 통째로 없어지는 사고다.
	 */
	@Test
	void 확인_단추는_잠깐_잠겨_있다() {
		assertFalse(TeamDisbandWarning.confirmActive(0), "뜨자마자 눌린다");
		assertTrue(TeamDisbandWarning.CONFIRM_DELAY_TICKS > 0, "잠금이 0틱이면 잠그지 않은 것이다");
		assertFalse(TeamDisbandWarning.confirmActive(TeamDisbandWarning.CONFIRM_DELAY_TICKS - 1),
				"잠금이 끝나기 전에 풀렸다");
		assertTrue(TeamDisbandWarning.confirmActive(TeamDisbandWarning.CONFIRM_DELAY_TICKS),
				"잠금이 끝났는데 안 풀렸다");
	}

	/** 한 번 풀린 잠금은 다시 걸리지 않는다. 틱이 더 흘러도 눌리는 채로 있어야 한다. */
	@Test
	void 풀린_잠금은_다시_걸리지_않는다() {
		boolean seenActive = false;
		for (int tick = 0; tick <= TeamDisbandWarning.CONFIRM_DELAY_TICKS * 4; tick++) {
			boolean active = TeamDisbandWarning.confirmActive(tick);
			if (seenActive) {
				assertTrue(active, tick + "틱에서 잠금이 다시 걸렸다");
			}
			seenActive = active;
		}
		assertTrue(seenActive, "끝까지 풀리지 않았다");
	}

	/**
	 * 보내는 명령이 서버가 받는 그대로다.
	 *
	 * <p>{@code ShareTeamCommand} 가 {@code disband confirm} 두 낱말로 받는다. {@code confirm}
	 * 을 빠뜨리면 서버는 「정말 해체하려면…」 안내만 보내고 아무 일도 하지 않는데, 화면은 이미
	 * 확인을 받은 뒤라 사람은 해체된 줄 안다.
	 */
	@Test
	void 확인_명령은_disband_confirm_이다() {
		assertEquals("disband confirm", TeamDisbandWarning.CONFIRM_COMMAND);
	}

	/** 목록은 밖에서 못 바꾼다. 화면이 그리는 도중에 줄이 늘면 자리 계산과 어긋난다. */
	@Test
	void 경고_줄_목록은_고쳐지지_않는다() {
		List<String> lines = TeamDisbandWarning.lines();

		assertTrue(lines.size() >= 4, "사라지는 것이 넷인데 줄이 모자란다");
		try {
			lines.add("끼워 넣기");
			throw new AssertionError("경고 줄을 밖에서 더할 수 있다");
		} catch (UnsupportedOperationException expected) {
			// List.of 는 고칠 수 없다. 이것이 바라는 바다.
		}
	}
}
