package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀이 없을 때 인벤토리 왼쪽에 <b>홀로 서는</b> 「팀 생성」 단추의 폭과 자리.
 *
 * <p>팀이 있으면 「SharedFate」 + 「현재 증강」 둘이 서고, 팀이 없으면 이 하나로 바뀐다. 줄을
 * 재는 규칙은 하나뿐이라야 한다 — 맨 앞 단추가 {@link InventoryTeamButton#buttonWidth} 로 남은
 * 자리 안에서 제 폭을 잡고, 뒤에 붙는 단추만
 * {@link InventoryTeamButton#perkButtonWidth} 로 남은 것을 넘겨받는다. 하나뿐일 때는 두 번째
 * 계산을 부르지 않는 것이 전부다. 이 시험이 그 「전부」를 붙들어 둔다.
 *
 * <p>둘일 때의 규칙은 {@code InventoryPerkButtonTest} 가 따로 지킨다. 여기서는 <b>하나로
 * 바뀌었을 때 그 규칙이 깨지지 않는지</b>만 본다.
 */
class InventoryCreateButtonTest {
	/** 실제 폰트로 잰 글자 폭. */
	private static final int CREATE_WIDTH = FakeFont.width(InventoryTeamButton.CREATE_LABEL);
	private static final int LABEL_WIDTH = FakeFont.width(InventoryTeamButton.LABEL);
	private static final int PERK_WIDTH = FakeFont.width(InventoryTeamButton.PERK_LABEL);

	/** 팀이 있을 때 줄이 차지하는 폭. */
	private static int inTeamRow(int available) {
		int lead = InventoryTeamButton.buttonWidth(LABEL_WIDTH, available);
		return InventoryTeamButton.buttonRowWidth(lead,
				InventoryTeamButton.perkButtonWidth(PERK_WIDTH, available, lead));
	}

	/** 팀이 없을 때 줄이 차지하는 폭. 뒤에 붙는 것이 없으므로 0을 넘긴다. */
	private static int noTeamRow(int available) {
		return InventoryTeamButton.buttonRowWidth(
				InventoryTeamButton.buttonWidth(CREATE_WIDTH, available), 0);
	}

	@Test
	void 글자가_비어_있지_않고_다른_단추와_겹치지_않는다() {
		assertFalse(InventoryTeamButton.CREATE_LABEL.isBlank(), "단추 글자가 비어 있다");
		assertFalse(InventoryTeamButton.CREATE_TOOLTIP.isBlank(), "설명이 비어 있다");
		assertFalse(InventoryTeamButton.CREATE_LABEL.equals(InventoryTeamButton.LABEL),
				"「SharedFate」와 글자가 같으면 무엇이 바뀐지 알 수 없다");
		assertFalse(InventoryTeamButton.CREATE_LABEL.equals(InventoryTeamButton.PERK_LABEL),
				"「현재 증강」과 글자가 같다");
	}

	/** 혼자 서므로 두 번째 단추 몫을 떼어 둘 이유가 없다. 줄 폭은 단추 폭 그대로다. */
	@Test
	void 줄_폭은_단추_폭_그대로다() {
		int available = 200;
		int width = InventoryTeamButton.buttonWidth(CREATE_WIDTH, available);

		assertEquals(width, noTeamRow(available), "혼자인데 무언가를 더 떼어 갔다");
	}

	/** 글자가 짧아진 만큼 줄도 좁아진다. 팀이 없을 때 인벤토리 왼쪽이 더 비어야 한다. */
	@Test
	void 단추_하나짜리_줄이_둘짜리보다_좁다() {
		for (int available = 40; available <= 400; available++) {
			assertTrue(noTeamRow(available) <= inTeamRow(available),
					"available=" + available + " 에서 하나짜리 줄이 더 넓었다");
		}
	}

	/** 덩어리의 오른쪽 끝은 창에 붙는다. 하나로 줄어도 그 규칙은 그대로다. */
	@Test
	void 줄의_오른쪽_끝이_창에_붙는다() {
		for (int available = 40; available <= 400; available++) {
			int row = noTeamRow(available);
			int left = InventoryTeamButton.blockLeft(available, row);

			assertEquals(available, left + row,
					"available=" + available + " 에서 줄이 창에 안 붙었다");
		}
	}

	/**
	 * 「팀 생성」은 <b>어떤 화면에서도 사라지지 않는다.</b>
	 *
	 * <p>「현재 증강」은 자리가 없으면 폭 0으로 돌아와 감춰지지만, 맨 앞 단추까지 사라지면
	 * 팀이 없는 사람에게 팀을 만들러 갈 길이 끊긴다. 0폭 단추는 안 보이는데 눌리기까지 해서,
	 * 그 길로 물러나서는 안 된다.
	 */
	@Test
	void 아무리_좁아도_감춰지지_않는다() {
		for (int available = -50; available <= 400; available++) {
			assertTrue(InventoryTeamButton.buttonWidth(CREATE_WIDTH, available)
							>= InventoryTeamButton.MIN_WIDTH,
					"available=" + available + " 에서 단추가 눌 수 없게 좁아졌다");
		}
	}

	/** 「현재 증강」을 포기할 만큼 좁은 화면에서도 「팀 생성」 하나는 온전히 선다. */
	@Test
	void 증강_단추를_포기하는_폭에서도_팀_생성은_온전하다() {
		int available = 70;
		int lead = InventoryTeamButton.buttonWidth(LABEL_WIDTH, available);

		assertEquals(0, InventoryTeamButton.perkButtonWidth(PERK_WIDTH, available, lead),
				"시험 전제가 깨졌다 — 이 폭에서는 증강 단추를 포기해야 한다");
		assertEquals(CREATE_WIDTH + InventoryTeamButton.LABEL_PADDING,
				InventoryTeamButton.buttonWidth(CREATE_WIDTH, available),
				"글자가 다 들어가는데도 단추가 잘렸다");
	}

	/**
	 * 팀이 생겨 단추가 둘로 늘어도 <b>오른쪽 끝은 그대로</b>고 왼쪽만 밀린다.
	 *
	 * <p>덩어리는 오른쪽 끝을 창에 붙이고 왼쪽으로 펼치므로, 단추가 늘면 왼쪽으로 자란다.
	 * 반대로 자라면 창을 덮는다.
	 */
	@Test
	void 팀이_생기면_줄은_왼쪽으로만_자란다() {
		int available = 200;
		int alone = noTeamRow(available);
		int both = inTeamRow(available);

		assertTrue(both > alone, "시험 전제가 깨졌다 — 단추 둘이 더 넓어야 한다");
		assertEquals(available, InventoryTeamButton.blockLeft(available, alone) + alone,
				"하나일 때 오른쪽 끝이 어긋났다");
		assertEquals(available, InventoryTeamButton.blockLeft(available, both) + both,
				"둘일 때 오른쪽 끝이 어긋났다");
		assertTrue(InventoryTeamButton.blockLeft(available, both)
						< InventoryTeamButton.blockLeft(available, alone),
				"단추가 늘었는데 왼쪽이 밀리지 않았다");
	}

	/** 단추가 몇이든 능력치 첫 줄은 단추 바로 아래다. 단추 줄의 높이는 하나일 때도 같다. */
	@Test
	void 능력치_첫_줄은_단추_아래에_붙는다() {
		int topPos = 100;

		assertEquals(InventoryTeamButton.y(topPos) + InventoryTeamButton.HEIGHT
						+ InventoryStatPanel.BUTTON_GAP,
				InventoryTeamButton.statTop(topPos),
				"능력치 첫 줄이 단추 아래에 붙지 않는다");
	}
}
