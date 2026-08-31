package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelScrollTest {

	@Test
	void 내용이_짧으면_스크롤이_없다() {
		assertEquals(0, PanelScroll.maxOffset(100, 200));
		assertFalse(PanelScroll.overflows(100, 200));
		assertEquals(0, PanelScroll.clamp(50, 100, 200), "넘칠 것이 없으면 어떤 값을 줘도 맨 위다");
	}

	@Test
	void 딱_맞으면_스크롤이_없다() {
		assertEquals(0, PanelScroll.maxOffset(200, 200));
		assertFalse(PanelScroll.overflows(200, 200));
	}

	@Test
	void 넘친_만큼만_밀어_올릴_수_있다() {
		assertEquals(60, PanelScroll.maxOffset(260, 200));
		assertTrue(PanelScroll.overflows(260, 200));
	}

	@Test
	void 범위를_벗어난_값은_잘린다() {
		assertEquals(0, PanelScroll.clamp(-30, 260, 200));
		assertEquals(60, PanelScroll.clamp(999, 260, 200));
		assertEquals(25, PanelScroll.clamp(25, 260, 200));
	}

	@Test
	void 창이_커지면_스크롤이_저절로_되돌아온다() {
		// 증강 목록을 끝까지 내려 둔 채 창을 키우면 빈 화면이 보이던 자리다.
		int scrolled = PanelScroll.clamp(60, 260, 200);
		assertEquals(60, scrolled);
		assertEquals(0, PanelScroll.clamp(scrolled, 260, 300), "창이 내용보다 커지면 맨 위로 돌아온다");
	}

	@Test
	void 손잡이는_보이는_비율만큼_길다() {
		// 내용 400, 창 200 이면 절반이 보이므로 손잡이도 절반이다.
		assertEquals(100, PanelScroll.thumbHeight(400, 200, 10));
	}

	@Test
	void 손잡이는_아무리_길어도_최소_길이를_지킨다() {
		assertEquals(10, PanelScroll.thumbHeight(100000, 200, 10));
	}

	@Test
	void 손잡이는_창보다_길어지지_않는다() {
		assertEquals(200, PanelScroll.thumbHeight(100, 200, 10), "넘칠 것이 없으면 홈을 가득 채운다");
		assertEquals(50, PanelScroll.thumbHeight(400, 50, 200), "최소 길이도 창을 넘지 못한다");
	}

	@Test
	void 손잡이는_맨_위와_맨_아래에_딱_붙는다() {
		int content = 400;
		int view = 200;
		int thumb = PanelScroll.thumbHeight(content, view, 10);
		int top = PanelScroll.thumbTop(50, view, thumb, 0, content);
		int bottom = PanelScroll.thumbTop(50, view, thumb, PanelScroll.maxOffset(content, view), content);
		assertEquals(50, top);
		assertEquals(50 + view - thumb, bottom);
	}

	@Test
	void 손잡이는_스크롤을_따라_단조롭게_내려간다() {
		int content = 500;
		int view = 120;
		int thumb = PanelScroll.thumbHeight(content, view, 10);
		int previous = -1;
		for (int offset = 0; offset <= PanelScroll.maxOffset(content, view); offset++) {
			int top = PanelScroll.thumbTop(0, view, thumb, offset, content);
			assertTrue(top >= previous, offset + "px 에서 손잡이가 거꾸로 올라갔다");
			assertTrue(top + thumb <= view, offset + "px 에서 손잡이가 홈 밖으로 나갔다");
			previous = top;
		}
	}

	@Test
	void 넘칠_것이_없으면_손잡이는_맨_위다() {
		assertEquals(7, PanelScroll.thumbTop(7, 200, 200, 999, 100));
	}
}
