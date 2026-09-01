package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인벤토리 화면 SharedFate 단추의 크기와 자리.
 *
 * <p>화면은 {@code src/client} 에 있어 시험 소스셋이 보지 못하므로, 여기서 확인하는 것은
 * <b>어디에 얼마나 크게 놓이는가</b>다. 가장 중요한 것은 <b>바닐라 조합법 책과 겹치지
 * 않는지</b>다 — 겹치면 조합법을 열려다 팀 화면이 열린다.
 */
class InventoryTeamButtonTest {
	/** 바닐라 인벤토리 창 폭. */
	private static final int IMAGE_WIDTH = 176;
	/** 조합법 책 판의 폭과 왼쪽 치우침. 바닐라 {@code RecipeBookComponent} 값이다. */
	private static final int BOOK_WIDTH = 147;
	private static final int BOOK_OFFSET = 86;
	/** 조합법 책 단추의 창 안 x. 바닐라 {@code InventoryScreen.getRecipeBookButtonPosition}. */
	private static final int RECIPE_BUTTON_OFFSET_X = 104;
	private static final int RECIPE_BUTTON_WIDTH = 20;

	/** 실제 폰트로 잰 「SharedFate」 의 폭. */
	private static final int LABEL_WIDTH = FakeFont.width(InventoryTeamButton.LABEL);

	/** 조합법 책이 닫혀 있을 때의 창 왼쪽. */
	private static int closedLeftPos(int screenWidth) {
		return (screenWidth - IMAGE_WIDTH) / 2;
	}

	/** 조합법 책이 펼쳐졌을 때 바닐라가 창을 밀어 놓는 자리. */
	private static int openLeftPos(int screenWidth) {
		return 177 + (screenWidth - IMAGE_WIDTH - 200) / 2;
	}

	private static int bookLeft(int screenWidth) {
		return (screenWidth - BOOK_WIDTH) / 2 - BOOK_OFFSET;
	}

	/** 단추만 있을 때의 덩어리 왼쪽 x. 능력치가 감춰진 경우가 이것이다. */
	private static int buttonOnlyX(int screenWidth, int leftPos) {
		int available = InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos);
		return InventoryTeamButton.blockLeft(available,
				InventoryTeamButton.buttonWidth(LABEL_WIDTH, available));
	}

	// -------------------------------------------------------------------- 크기

	/**
	 * 이름이 길어졌으므로 단추도 넓어진다.
	 *
	 * <p>예전 20×20 은 「팀」 한 글자만 담을 수 있었다. 폭을 못 박지 않고 글자를 재서 정하므로,
	 * 자원팩이 폰트를 바꾸거나 글자가 바뀌어도 넘치지 않는다.
	 */
	@Test
	void 단추는_글자_폭에_맞춰_넓어진다() {
		int width = InventoryTeamButton.buttonWidth(LABEL_WIDTH, 400);

		assertEquals(LABEL_WIDTH + InventoryTeamButton.LABEL_PADDING, width);
		assertTrue(width > 20, "예전 20px 로는 이름이 들어가지 않는다");
	}

	/**
	 * 자리가 모자라면 단추가 줄어든다.
	 *
	 * <p>화면 폭이 379 언저리이고 조합법 책이 펼쳐지면 왼쪽에 서른 픽셀도 안 남는다.
	 * 그때는 단추가 그 자리까지만 줄고, 글자는 바닐라가 잘라 보여 준다
	 * ({@code AbstractWidget.renderScrollingString}).
	 */
	@Test
	void 자리가_모자라면_단추가_남은_자리까지만_넓어진다() {
		assertEquals(26, InventoryTeamButton.buttonWidth(LABEL_WIDTH, 26));
		assertEquals(InventoryTeamButton.MIN_WIDTH,
				InventoryTeamButton.buttonWidth(LABEL_WIDTH, 5),
				"눌 수 없을 만큼 작아지지는 않는다");
	}

	// -------------------------------------------------------------------- 자리

	@Test
	void 조합법_책이_닫혀_있으면_창_왼쪽_바깥에_붙는다() {
		int screenWidth = 427;
		int leftPos = closedLeftPos(screenWidth);
		int x = buttonOnlyX(screenWidth, leftPos);
		int width = InventoryTeamButton.buttonWidth(LABEL_WIDTH,
				InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos));

		assertEquals(leftPos - InventoryTeamButton.GAP, x + width, "덩어리 오른쪽 끝이 창에 붙는다");
		assertTrue(x + width < leftPos, "창을 덮으면 칸을 가린다");
	}

	/**
	 * 조합법 책 <b>단추</b>와 겹치지 않는다.
	 *
	 * <p>그 단추는 창 안 {@code leftPos + 104} 에 있고 책을 펼치면 그 자리도 함께 밀린다.
	 * 두 자리 모두에서 확인한다 — 겹치면 조합법을 열려다 팀 화면이 열린다.
	 */
	@Test
	void 조합법_책_단추와_가로로_겹치지_않는다() {
		int screenWidth = 427;
		for (int leftPos : new int[] {closedLeftPos(screenWidth), openLeftPos(screenWidth)}) {
			int available = InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos);
			int width = InventoryTeamButton.buttonWidth(LABEL_WIDTH, available);
			int x = InventoryTeamButton.blockLeft(available, width);
			int recipeLeft = leftPos + RECIPE_BUTTON_OFFSET_X;
			assertTrue(x + width <= recipeLeft || x >= recipeLeft + RECIPE_BUTTON_WIDTH,
					"창 왼쪽 " + leftPos + " 에서 두 단추가 겹친다: x=" + x);
		}
	}

	@Test
	void 조합법_책이_펼쳐지면_그_판보다_더_왼쪽으로_물러난다() {
		int screenWidth = 640;
		int leftPos = openLeftPos(screenWidth);
		int available = InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos);
		int width = InventoryTeamButton.buttonWidth(LABEL_WIDTH, available);

		assertTrue(InventoryTeamButton.blockLeft(available, width) + width
						<= bookLeft(screenWidth),
				"펼쳐진 조합법 책에 깔리면 눌리지도 않는다");
	}

	/**
	 * 화면이 좁아 조합법 책이 창을 덮는 경우.
	 *
	 * <p>바닐라는 이때 창을 밀지 않고({@code widthTooNarrow}) 책을 화면 가운데에 그린다.
	 * 창 왼쪽 바깥은 여전히 비어 있다.
	 */
	@Test
	void 좁은_화면에서_책이_창을_덮어도_왼쪽은_비어_있다() {
		int screenWidth = 320;
		int leftPos = closedLeftPos(screenWidth);
		int available = InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos);
		int width = InventoryTeamButton.buttonWidth(LABEL_WIDTH, available);

		assertEquals(leftPos, InventoryTeamButton.anchorLeft(screenWidth, IMAGE_WIDTH, leftPos),
				"창이 밀리지 않았으므로 기준도 창이다");
		assertTrue(InventoryTeamButton.blockLeft(available, width) + width
						<= (screenWidth - BOOK_WIDTH) / 2,
				"덮어 그려진 책보다 왼쪽이어야 한다");
	}

	/**
	 * 어떤 폭에서도 화면 밖으로 나가지 않는다.
	 *
	 * <p>덩어리 폭에는 단추보다 넓은 능력치 줄이 들어올 수 있으므로 그 경우도 함께 훑는다.
	 */
	@Test
	void 어떤_화면_폭에서도_덩어리가_화면_밖으로_나가지_않는다() {
		for (int screenWidth = 320; screenWidth <= 1920; screenWidth += 7) {
			for (int leftPos : new int[] {closedLeftPos(screenWidth), openLeftPos(screenWidth)}) {
				int available = InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos);
				for (int blockWidth : new int[] {InventoryTeamButton.MIN_WIDTH, 66, 140, 200}) {
					int x = InventoryTeamButton.blockLeft(available, blockWidth);
					assertTrue(x >= 0, "화면 폭 " + screenWidth + " 에서 x=" + x);
					assertTrue(x + blockWidth <= screenWidth,
							"화면 폭 " + screenWidth + " 에서 x=" + x);
				}
			}
		}
	}

	/**
	 * 자리에 들어가는 덩어리는 창도 책도 덮지 않는다.
	 *
	 * <p>{@link InventoryStatPanel} 이 남은 폭 안에 들어가는 모양만 고르므로, 실제로 그려지는
	 * 덩어리는 언제나 이 조건을 만족한다.
	 */
	@Test
	void 남은_자리에_들어가는_덩어리는_창을_덮지_않는다() {
		for (int screenWidth = 320; screenWidth <= 1920; screenWidth += 13) {
			for (int leftPos : new int[] {closedLeftPos(screenWidth), openLeftPos(screenWidth)}) {
				int available = InventoryTeamButton.available(screenWidth, IMAGE_WIDTH, leftPos);
				if (available < InventoryTeamButton.MIN_WIDTH) {
					continue;
				}
				int blockWidth = Math.min(available, 200);
				assertTrue(InventoryTeamButton.blockLeft(available, blockWidth) + blockWidth
								<= InventoryTeamButton.anchorLeft(screenWidth, IMAGE_WIDTH, leftPos),
						"화면 폭 " + screenWidth + " 에서 덩어리가 창(또는 책)을 넘본다");
			}
		}
	}

	// -------------------------------------------------------------------- 세로

	/** 창이 아래로 늘어나도 위 끝은 창 안이다. 확장 27칸이 붙으면 창이 54px 커진다. */
	@Test
	void 세로는_창_위_끝에_맞춘다() {
		assertEquals(40, InventoryTeamButton.y(40));
		assertEquals(0, InventoryTeamButton.y(-5), "화면 위로 넘어가면 잘린다");
	}

	/**
	 * 능력치가 쓸 수 있는 세로는 늘 넉넉하다.
	 *
	 * <p>바닐라는 GUI 배율을 스스로 낮춰 화면을 240 아래로 두지 않는다. 확장 27칸으로
	 * 220px 이 된 창을 가운데 놓아도 단추 아래로 200px 가까이 남는다 — 여덟 줄을 두 줄씩
	 * 접어도(16줄 × 10px) 들어간다.
	 */
	@Test
	void 가장_낮은_화면에서도_여덟_줄을_접어_넣을_자리가_남는다() {
		int screenHeight = 240;
		int imageHeight = 220;
		int topPos = (screenHeight - imageHeight) / 2;

		assertEquals(10, topPos);
		assertTrue(InventoryTeamButton.statHeight(screenHeight, topPos)
						>= 16 * InventoryStatPanel.LINE_HEIGHT + InventoryStatPanel.GROUP_GAP,
				"두 줄로 접은 여덟 줄이 들어가야 한다");
	}
}
