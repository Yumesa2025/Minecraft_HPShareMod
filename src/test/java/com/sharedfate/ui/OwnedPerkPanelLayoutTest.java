package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「보유 증강」 모달이 화면 가운데에 서고, 밖으로 나가지 않고, 좁으면 아예 안 열리는지.
 *
 * <p>이 모달은 선택 화면 위에 덮는 것이라 <b>밖으로 삐져나가면 글자가 잘린다.</b> 그 상태에서는
 * 닫을 단추도 안 보일 수 있다.
 */
class OwnedPerkPanelLayoutTest {

	private static final int MARGIN = 10;
	private static final int LINE = 9;
	private static final int PADDING = 6;

	@Test
	void 넉넉한_화면에서는_가운데_선다() {
		OwnedPerkPanelLayout panel = OwnedPerkPanelLayout.fit(854, 480, MARGIN, LINE, PADDING);

		assertTrue(panel.visible());
		assertEquals(854 - panel.right(), panel.left(), "좌우 여백이 같아야 가운데다");
		assertEquals(480 - panel.bottom(), panel.top(), "위아래 여백이 같아야 가운데다");
	}

	@Test
	void 화면을_절대_벗어나지_않는다() {
		int[][] screens = {{854, 480}, {427, 240}, {320, 180}, {200, 120}, {1920, 1080}};
		for (int[] screen : screens) {
			OwnedPerkPanelLayout panel =
					OwnedPerkPanelLayout.fit(screen[0], screen[1], MARGIN, LINE, PADDING);
			if (!panel.visible()) {
				continue;
			}
			String where = screen[0] + "x" + screen[1];
			assertTrue(panel.left() >= MARGIN, where + " 왼쪽");
			assertTrue(panel.top() >= MARGIN, where + " 위");
			assertTrue(panel.right() <= screen[0] - MARGIN, where + " 오른쪽");
			assertTrue(panel.bottom() <= screen[1] - MARGIN, where + " 아래");
		}
	}

	@Test
	void 너무_좁으면_열지_않는다() {
		assertFalse(OwnedPerkPanelLayout.fit(100, 480, MARGIN, LINE, PADDING).visible());
		assertFalse(OwnedPerkPanelLayout.fit(854, 50, MARGIN, LINE, PADDING).visible());
		assertFalse(OwnedPerkPanelLayout.fit(0, 0, MARGIN, LINE, PADDING).visible());
	}

	/** 아주 넓은 화면에서 글줄이 끝없이 길어지면 읽기가 힘들다. */
	@Test
	void 아무리_넓어도_한계가_있다() {
		OwnedPerkPanelLayout panel = OwnedPerkPanelLayout.fit(3840, 2160, MARGIN, LINE, PADDING);

		assertTrue(panel.visible());
		assertTrue(panel.width() <= 320, "가로 한계를 넘었다: " + panel.width());
	}

	@Test
	void 목록_자리는_머리글_아래이고_판_안이다() {
		OwnedPerkPanelLayout panel = OwnedPerkPanelLayout.fit(854, 480, MARGIN, LINE, PADDING);

		assertTrue(panel.listTop() > panel.headerY(), "목록이 머리글 위로 올라갔다");
		assertTrue(panel.listBottom() <= panel.bottom(), "목록이 판 아래로 넘쳤다");
		assertTrue(panel.viewHeight() > 0, "목록이 보일 자리가 없다");
		assertTrue(panel.wrapWidth(6) >= 8, "글줄을 접을 폭이 없다");
		assertTrue(panel.contentLeft() >= panel.left(), "글자가 판 왼쪽 밖이다");
		assertTrue(panel.contentRight() <= panel.right(), "글자가 판 오른쪽 밖이다");
	}

	/** 숨긴 판은 어떤 값도 그리기에 쓰이면 안 된다. */
	@Test
	void 숨긴_판은_비어_있다() {
		OwnedPerkPanelLayout hidden = OwnedPerkPanelLayout.hidden();

		assertFalse(hidden.visible());
		assertEquals(0, hidden.width());
		assertEquals(0, hidden.height());
	}
}
