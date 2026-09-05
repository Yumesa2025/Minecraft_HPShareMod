package com.sharedfate.ui;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 팀 화면 「증강」 탭의 보유 목록에서 <b>마우스가 어느 증강 위에 있는가</b>.
 *
 * <p>이 시험이 지키는 것은 하나다 — <b>스크롤한 뒤에도 눈에 보이는 줄이 집혀야 한다.</b>
 * 화면 좌표와 목록 좌표를 헷갈리면 목록을 내린 만큼 툴팁이 어긋나 <b>다른 증강의 세트</b>가
 * 뜨는데, 그 화면은 「이 증강이 채굴이었나?」 하고 사람을 헷갈리게 만들 뿐 고장으로는 보이지
 * 않아 오래 남는다.
 *
 * <p>숫자는 팀 화면의 실제 값을 본떴다 — 한 줄 12px, 증강 하나가 이름 한 줄과 설명 몇 줄이다.
 */
class PerkListHoverTest {

	/** 목록 창의 윗변. 세트 덩어리 아래라 화면 한가운데쯤이다. */
	private static final int VIEW_TOP = 100;
	/** 목록 창의 세로 길이. 아래 단추 자리를 뺀 값이다. */
	private static final int VIEW_HEIGHT = 48;
	private static final int LEFT = 20;
	private static final int WIDTH = 300;

	@BeforeAll
	static void 준비() {
		TestBootstrap.ensureInitialized();
	}

	/** 증강 셋. 각각 36px(이름 + 설명 두 줄), 24px, 60px 이다. */
	private static List<PerkListHover.Span> spans() {
		return PerkListHover.stack(List.of(36, 24, 60));
	}

	private static int at(double mouseY, int scroll) {
		return PerkListHover.indexAt(spans(), LEFT + 5, mouseY, LEFT, WIDTH,
				VIEW_TOP, VIEW_HEIGHT, scroll);
	}

	// ------------------------------------------------------------------ 쌓기

	@Test
	void 높이를_위에서부터_쌓아_자리로_바꾼다() {
		List<PerkListHover.Span> spans = spans();

		assertEquals(new PerkListHover.Span(0, 36), spans.getFirst());
		assertEquals(new PerkListHover.Span(36, 24), spans.get(1));
		assertEquals(new PerkListHover.Span(60, 60), spans.get(2));
		assertEquals(120, spans.get(2).bottom());
	}

	@Test
	void 목록이_비면_자리도_없다() {
		assertEquals(List.of(), PerkListHover.stack(List.of()));
		assertEquals(List.of(), PerkListHover.stack(null));
	}

	// ------------------------------------------------------------------ 안 밀린 목록

	@Test
	void 맨_위부터_보고_있으면_보이는_그대로_집힌다() {
		assertEquals(0, at(VIEW_TOP, 0));
		assertEquals(0, at(VIEW_TOP + 35, 0));
		assertEquals(1, at(VIEW_TOP + 36, 0));
		// 셋째 증강은 60px 부터다. 창이 48px 이라 맨 위에서는 아직 닿지 않는다.
		assertEquals(1, at(VIEW_TOP + 47, 0));
	}

	// ------------------------------------------------------------------ 밀린 목록

	/**
	 * 목록을 36px 내렸으면 창 맨 위에 둘째 증강이 와 있다.
	 *
	 * <p>화면 좌표를 그대로 쓰면 여기서 첫째 증강이 집힌다 — 이미 창 위로 밀려 올라가 보이지도
	 * 않는 증강이다.
	 */
	@Test
	void 스크롤한_뒤에는_밀려_올라온_증강이_집힌다() {
		assertEquals(1, at(VIEW_TOP, 36));
		assertEquals(1, at(VIEW_TOP + 23, 36));
		assertEquals(2, at(VIEW_TOP + 24, 36));
	}

	@Test
	void 많이_내리면_마지막_증강이_창을_채운다() {
		// 120 − 48 = 72 가 끝까지 내린 자리다. 창 전체가 마지막 증강(60~120)이다.
		assertEquals(2, at(VIEW_TOP, 72));
		assertEquals(2, at(VIEW_TOP + 47, 72));
	}

	// ------------------------------------------------------------------ 창 밖

	/**
	 * 창 위는 스크롤한 만큼 <b>목록 좌표로는 멀쩡한 자리</b>다. 그래서 창 범위를 먼저 보지
	 * 않으면, 머리글 위에 마우스를 올렸는데 안 보이는 증강의 툴팁이 뜬다.
	 */
	@Test
	void 창_위쪽은_스크롤_중이어도_안_집힌다() {
		assertEquals(-1, at(VIEW_TOP - 1, 36));
		assertEquals(-1, at(VIEW_TOP - 20, 36));
	}

	@Test
	void 창_아래쪽은_안_집힌다() {
		// 잘라내기가 여기서 끝난다. 아래는 「증강 선택 창 열기」 단추 자리다.
		assertEquals(-1, at(VIEW_TOP + VIEW_HEIGHT, 0));
		assertEquals(-1, at(VIEW_TOP + VIEW_HEIGHT + 10, 72));
	}

	@Test
	void 목록보다_아래는_창_안이어도_안_집힌다() {
		// 증강이 둘뿐이라 창 아래쪽이 비어 있는 경우다. 빈자리에서 툴팁이 뜨면 안 된다.
		List<PerkListHover.Span> shortList = PerkListHover.stack(List.of(12, 12));

		assertEquals(-1, PerkListHover.indexAt(shortList, LEFT + 5, VIEW_TOP + 30, LEFT, WIDTH,
				VIEW_TOP, VIEW_HEIGHT, 0));
	}

	@Test
	void 판_밖으로_나간_가로_자리는_안_집힌다() {
		assertEquals(-1, PerkListHover.indexAt(spans(), LEFT - 1, VIEW_TOP + 5, LEFT, WIDTH,
				VIEW_TOP, VIEW_HEIGHT, 0));
		assertEquals(-1, PerkListHover.indexAt(spans(), LEFT + WIDTH, VIEW_TOP + 5, LEFT, WIDTH,
				VIEW_TOP, VIEW_HEIGHT, 0));
	}

	@Test
	void 증강이_하나도_없으면_안_집힌다() {
		assertEquals(-1, PerkListHover.indexAt(List.of(), LEFT + 5, VIEW_TOP + 5, LEFT, WIDTH,
				VIEW_TOP, VIEW_HEIGHT, 0));
		assertEquals(-1, PerkListHover.indexAt(null, LEFT + 5, VIEW_TOP + 5, LEFT, WIDTH,
				VIEW_TOP, VIEW_HEIGHT, 0));
	}
}
