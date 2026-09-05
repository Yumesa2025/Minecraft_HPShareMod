package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 선택 화면 왼쪽 세트 판의 자리.
 *
 * <p>이 시험이 지키는 것은 하나다 — <b>판이 카드를 가리지 않는다.</b> 카드 세 장이 이 화면의
 * 전부인데 왼쪽 판이 그 위로 넘어오면 고르는 일 자체가 안 된다. 그래서 남은 폭이 모자랄 때
 * 판이 물러나는 차례(머리글 접기 → 통째로 감추기)를 못 박아 둔다.
 *
 * <p>글자 폭은 {@link FakeFont} 가 잰다. 실제 화면은 {@code font::width} 를 넘긴다.
 */
class PerkSetPanelLayoutTest {
	/** 마인크래프트 기본 폰트의 글줄 높이. */
	private static final int LINE_HEIGHT = 9;
	private static final int PADDING = 5;
	private static final int HEADER_GAP = 3;
	/** 판 오른쪽 변과 첫 카드 사이의 틈. 화면 상수와 같은 값이다. */
	private static final int CARD_GAP = 10;
	private static final String TITLE = "지금 켜진 세트";

	private static List<PerkSetLines.Line> lines() {
		return List.of(
				new PerkSetLines.Line("mining", "◆ 채굴 3/3", true),
				new PerkSetLines.Line("defense", "◇ 방어 1/2", false),
				new PerkSetLines.Line("power", "◇ 화력 1/2", false));
	}

	private static int widest() {
		return PerkSetLines.blockWidth(lines(), FakeFont::width, 0);
	}

	private static int titleWidth() {
		return FakeFont.width(TITLE);
	}

	/**
	 * 카드가 {@code cardsLeft} 에서 시작할 때의 자리.
	 *
	 * <p>화면이 넘기는 것과 <b>똑같이</b> 만든다 — 오른쪽 한계는 카드 왼쪽 변에서 틈만큼
	 * 물러난 자리다.
	 */
	private static PerkSetPanelLayout.Room room(int cardsLeft, int height) {
		return new PerkSetPanelLayout.Room(8, cardsLeft - CARD_GAP, 40, 40 + height,
				LINE_HEIGHT, PADDING, HEADER_GAP);
	}

	private static PerkSetPanelLayout fit(int cardsLeft, int height) {
		return PerkSetPanelLayout.fit(room(cardsLeft, height), lines().size(), widest(),
				titleWidth());
	}

	// ------------------------------------------------------------------ 가로

	@Test
	void 자리가_넉넉하면_머리글까지_그린다() {
		PerkSetPanelLayout panel = fit(300, 160);

		assertTrue(panel.visible());
		assertTrue(panel.header());
		assertEquals(3, panel.rowCount());
		assertEquals(Math.max(widest(), titleWidth()) + PADDING * 2, panel.width());
	}

	/**
	 * 머리글이 제일 긴 줄이라 그것만 접으면 들어가는 폭이 있다.
	 *
	 * <p>이때 판을 통째로 감추면 <b>들어갈 수 있었는데 안 보여 준 것</b>이 된다.
	 */
	@Test
	void 머리글이_넘치면_머리글을_접는다() {
		// 세트 줄은 들어가고 머리글은 못 들어가는 폭을 정확히 만든다.
		int cardsLeft = 8 + widest() + PADDING * 2 + CARD_GAP;
		PerkSetPanelLayout panel = fit(cardsLeft, 160);

		assertTrue(panel.visible());
		assertFalse(panel.header());
		assertEquals(widest() + PADDING * 2, panel.width());
		assertEquals(3, panel.rowCount());
	}

	@Test
	void 세트_줄도_안_들어가면_감춘다() {
		PerkSetPanelLayout panel = fit(8 + widest() + PADDING * 2 + CARD_GAP - 1, 160);

		assertFalse(panel.visible());
		assertEquals(0, panel.rowCount());
	}

	/**
	 * <b>이 시험이 이 파일의 본론이다.</b>
	 *
	 * <p>화면 폭을 잘게 훑으며 판의 오른쪽 변이 카드 왼쪽 변을 넘지 않는지 본다. 넘으면 카드
	 * 위에 글자가 겹쳐 그려진다.
	 */
	@Test
	void 어떤_폭에서도_카드를_가리지_않는다() {
		for (int cardsLeft = 0; cardsLeft <= 400; cardsLeft++) {
			PerkSetPanelLayout panel = fit(cardsLeft, 160);
			if (!panel.visible()) {
				continue;
			}
			assertTrue(panel.right() <= cardsLeft - CARD_GAP,
					"cardsLeft=" + cardsLeft + " 에서 판이 카드를 침범했다: " + panel.right());
		}
	}

	@Test
	void 그릴_줄이_없으면_감춘다() {
		assertFalse(PerkSetPanelLayout.fit(room(300, 160), 0, widest(), titleWidth()).visible());
	}

	// ------------------------------------------------------------------ 세로

	@Test
	void 세로가_모자라면_들어가는_줄만_남긴다() {
		// 여백 10 + 머리글 12 + 두 줄 18 = 40.
		PerkSetPanelLayout panel = fit(300, 40);

		assertTrue(panel.visible());
		assertTrue(panel.header());
		assertEquals(2, panel.rowCount());
		assertTrue(panel.bottom() <= 40 + 40);
	}

	/**
	 * 세로가 아주 낮으면 머리글을 접어서라도 한 줄은 남긴다.
	 *
	 * <p>「지금 켜진 세트」라는 글자보다 「◆ 채굴 3/3」 한 줄이 백 배 값지다.
	 */
	@Test
	void 세로가_아주_낮으면_머리글을_접어서라도_남긴다() {
		// 여백 10 + 한 줄 9 = 19. 머리글까지 넣으면 31 이 필요해 들어가지 못한다.
		PerkSetPanelLayout panel = fit(300, 19);

		assertTrue(panel.visible());
		assertFalse(panel.header());
		assertEquals(1, panel.rowCount());
	}

	@Test
	void 한_줄도_안_들어가면_감춘다() {
		assertFalse(fit(300, 18).visible());
	}

	@Test
	void 판은_준_자리_안에_들어간다() {
		PerkSetPanelLayout panel = fit(300, 160);

		assertEquals(8, panel.left());
		assertEquals(40, panel.top());
		assertTrue(panel.bottom() <= 40 + 160);
		assertEquals(PADDING * 2 + LINE_HEIGHT + HEADER_GAP + 3 * LINE_HEIGHT, panel.height());
	}

	// ------------------------------------------------------------------ 마우스 판정

	@Test
	void 세트_줄_위에_마우스를_두면_그_줄이_나온다() {
		PerkSetPanelLayout panel = fit(300, 160);

		assertEquals(0, panel.rowAt(panel.contentLeft() + 1, panel.rowY(0)));
		assertEquals(1, panel.rowAt(panel.contentLeft() + 1, panel.rowY(1) + LINE_HEIGHT - 1));
		assertEquals(2, panel.rowAt(panel.contentLeft() + 1, panel.rowY(2)));
	}

	/** 머리글은 유형이 아니다. 그 위에서 툴팁이 뜨면 어느 유형의 것인지 알 수 없다. */
	@Test
	void 머리글_위는_어느_줄도_아니다() {
		PerkSetPanelLayout panel = fit(300, 160);

		assertEquals(-1, panel.rowAt(panel.contentLeft() + 1, panel.headerY()));
	}

	/** 글자가 끝난 자리부터는 마우스를 받지 않는다. 아니면 왼쪽 어디에 두어도 툴팁이 뜬다. */
	@Test
	void 글자_폭_밖은_받지_않는다() {
		PerkSetPanelLayout panel = fit(300, 160);

		assertEquals(-1, panel.rowAt(panel.contentLeft() - 1, panel.rowY(0)));
		assertEquals(-1, panel.rowAt(panel.contentLeft() + panel.contentWidth(), panel.rowY(0)));
	}

	@Test
	void 마지막_줄_아래는_어느_줄도_아니다() {
		PerkSetPanelLayout panel = fit(300, 160);

		assertEquals(-1, panel.rowAt(panel.contentLeft() + 1, panel.rowY(3)));
	}

	@Test
	void 감춘_판은_아무_줄도_돌려주지_않는다() {
		PerkSetPanelLayout hidden = PerkSetPanelLayout.hidden();

		assertFalse(hidden.visible());
		assertEquals(-1, hidden.rowAt(0, 0));
		assertEquals(0, hidden.height());
	}
}
