package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인벤토리 화면 「팀」 단추의 자리.
 *
 * <p>화면은 {@code src/client} 에 있어 시험 소스셋이 보지 못하므로, 여기서 확인하는 것은
 * <b>어디에 놓이는가</b>다. 가장 중요한 것은 <b>바닐라 조합법 책과 겹치지 않는지</b>다 —
 * 겹치면 조합법을 열려다 팀 화면이 열린다.
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

	@Test
	void 조합법_책이_닫혀_있으면_창_왼쪽_바깥에_붙는다() {
		int screenWidth = 427;
		int leftPos = closedLeftPos(screenWidth);
		int x = InventoryTeamButton.x(screenWidth, IMAGE_WIDTH, leftPos);

		assertEquals(leftPos - InventoryTeamButton.GAP - InventoryTeamButton.WIDTH, x);
		assertTrue(x + InventoryTeamButton.WIDTH < leftPos, "창을 덮으면 칸을 가린다");
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
			int x = InventoryTeamButton.x(screenWidth, IMAGE_WIDTH, leftPos);
			int recipeLeft = leftPos + RECIPE_BUTTON_OFFSET_X;
			assertTrue(x + InventoryTeamButton.WIDTH <= recipeLeft
							|| x >= recipeLeft + RECIPE_BUTTON_WIDTH,
					"창 왼쪽 " + leftPos + " 에서 두 단추가 겹친다: x=" + x);
		}
	}

	@Test
	void 조합법_책이_펼쳐지면_그_판보다_더_왼쪽으로_물러난다() {
		int screenWidth = 427;
		int leftPos = openLeftPos(screenWidth);
		int x = InventoryTeamButton.x(screenWidth, IMAGE_WIDTH, leftPos);

		assertTrue(x + InventoryTeamButton.WIDTH <= bookLeft(screenWidth),
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
		int x = InventoryTeamButton.x(screenWidth, IMAGE_WIDTH, leftPos);

		assertEquals(leftPos, InventoryTeamButton.anchorLeft(screenWidth, IMAGE_WIDTH, leftPos),
				"창이 밀리지 않았으므로 기준도 창이다");
		assertTrue(x + InventoryTeamButton.WIDTH <= (screenWidth - BOOK_WIDTH) / 2,
				"덮어 그려진 책보다 왼쪽이어야 한다");
	}

	@Test
	void 어떤_화면_폭에서도_화면_밖으로_나가지_않는다() {
		for (int screenWidth = 320; screenWidth <= 1920; screenWidth += 7) {
			for (int leftPos : new int[] {closedLeftPos(screenWidth), openLeftPos(screenWidth)}) {
				int x = InventoryTeamButton.x(screenWidth, IMAGE_WIDTH, leftPos);
				assertTrue(x >= 0, "화면 폭 " + screenWidth + " 에서 x=" + x);
				assertTrue(x + InventoryTeamButton.WIDTH <= screenWidth,
						"화면 폭 " + screenWidth + " 에서 x=" + x);
			}
		}
	}

	/** 창이 아래로 늘어나도 위 끝은 창 안이다. 확장 27칸이 붙으면 창이 54px 커진다. */
	@Test
	void 세로는_창_위_끝에_맞춘다() {
		assertEquals(40, InventoryTeamButton.y(40));
		assertEquals(0, InventoryTeamButton.y(-5), "화면 위로 넘어가면 잘린다");
	}
}
