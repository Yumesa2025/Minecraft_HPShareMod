package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 증강 선택창의 「다시 뽑기」 단추가 언제 보이고 언제 눌리는지.
 *
 * <p>여기서 막는 것은 <b>헛수고</b>지 악용이 아니다. 남은 횟수 검사도 재추첨도 서버가
 * 다시 하므로({@code PerkManager.applyReroll}) 이 판단을 건너뛴 클라이언트도 남은 횟수보다
 * 더 뽑을 수 없다. 그래도 눌러 봐야 아무 일도 안 나는 단추를 살려 두면, 왜 안 되는지
 * 알 길이 없어 사람이 계속 누른다.
 */
class PerkRerollButtonTest {

	// ------------------------------------------------------------------ 보이는가

	@Test
	void 강제로_띄운_창의_선택자에게만_보인다() {
		assertTrue(PerkRerollButton.visible(true, true));
		assertFalse(PerkRerollButton.visible(true, false), "관전자는 후보를 갈아 끼울 수 없다");
	}

	@Test
	void 직접_연_창에는_아예_없다() {
		// /shareteam perk 로 연 창에는 강제 선택 세션이 없어 서버가 요청을 조용히 버린다.
		assertFalse(PerkRerollButton.visible(false, true));
		assertFalse(PerkRerollButton.visible(false, false));
	}

	@Test
	void 다_써도_사라지지_않는다() {
		// 단추가 사라지면 「원래 없는 기능」인지 「다 쓴 것」인지 알 수 없다.
		assertTrue(PerkRerollButton.visible(true, true));
		assertEquals("다시 뽑기 (남은 횟수 없음)", PerkRerollButton.label(0));
	}

	// ------------------------------------------------------------------ 눌리는가

	@Test
	void 남은_횟수가_있고_기다리는_것이_없을_때만_눌린다() {
		assertTrue(PerkRerollButton.enabled(true, true, false, false, false, 3));
	}

	@Test
	void 남은_횟수가_0이면_잠긴다() {
		assertFalse(PerkRerollButton.enabled(true, true, false, false, false, 0));
	}

	@Test
	void 서버의_다음_지시를_기다리는_동안에는_잠긴다() {
		// 이미 골라 보냈을 때 / 다시 뽑기를 눌러 두고 새 후보를 기다릴 때 /
		// 무엇이 정해졌는지 보여 주는 중. 셋 다 눌러도 서버가 버린다.
		assertFalse(PerkRerollButton.enabled(true, true, true, false, false, 3));
		assertFalse(PerkRerollButton.enabled(true, true, false, true, false, 3));
		assertFalse(PerkRerollButton.enabled(true, true, false, false, true, 3));
	}

	@Test
	void 보이지_않는_창에서는_눌리지도_않는다() {
		assertFalse(PerkRerollButton.enabled(false, true, false, false, false, 3));
		assertFalse(PerkRerollButton.enabled(true, false, false, false, false, 3));
	}

	// ------------------------------------------------------------------ 글자

	@Test
	void 남은_횟수가_단추에_그대로_보인다() {
		assertEquals("다시 뽑기 (3회 남음)", PerkRerollButton.label(3));
		assertEquals("다시 뽑기 (1회 남음)", PerkRerollButton.label(1));
	}
}
