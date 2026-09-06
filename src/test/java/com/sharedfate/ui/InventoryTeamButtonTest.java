package com.sharedfate.ui;

import com.sharedfate.client.hud.BottomLeftStack;
import com.sharedfate.client.hud.CoordinateHud;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인벤토리 화면 SharedFate 단추의 크기와 자리.
 *
 * <p>여기서 확인하는 것은 <b>어디에 얼마나 크게 놓이는가</b>다. 지키는 것이 둘이다 —
 * <b>바닐라 조합법 책과 가로로 겹치지 않는 것</b>(겹치면 조합법을 열려다 팀 화면이 열린다)과
 * <b>HUD 세트 줄과 세로로 겹치지 않는 것</b>(겹치면 글씨가 서로 뭉갠다)이다.
 *
 * <p>HUD 쪽 값은 상수라 {@code javac} 가 자리에 박아 넣는다. 그래서 클라이언트 화면을 띄우지
 * 않고도 두 사본이 같은지 견줄 수 있다.
 */
class InventoryTeamButtonTest {
	/** 바닐라 인벤토리 창 폭. */
	private static final int IMAGE_WIDTH = 176;
	/** 확장 27칸이 붙은 인벤토리 창 높이(166 + 54). */
	private static final int IMAGE_HEIGHT = 220;
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
	 * <p>폭을 못 박지 않고 글자를 재서 정하므로, 자원팩이 폰트를 바꾸거나 글자가 바뀌어도
	 * 넘치지 않는다.
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

	/**
	 * HUD 세트 덩어리의 아래끝을 <b>HUD 쪽 값에서 다시 짚는다.</b>
	 *
	 * <p>{@link InventoryTeamButton} 은 {@code src/main} 에 있어 {@code src/client} 의
	 * {@code CoordinateHud} 를 부를 수 없고, 그래서 그 값을 옮겨 적어 두었다. 여기가 그 사본이
	 * 원본과 같은지 붙드는 자리다 — HUD 가 한 줄이라도 더 쌓거나 줄 높이를 바꾸면 여기서 먼저
	 * 터지고, 그러지 않으면 능력치가 조용히 세트 줄 위에 다시 올라앉는다.
	 *
	 * <p>구분선 덩어리 7 은 {@code CoordinateHud} 의 {@code SEPARATOR_GAP}(3) + 선 1 +
	 * {@code SEPARATOR_BOTTOM_GAP}(3) 이다. 그 셋은 {@code private} 이라 값을 옮겨 적는다.
	 */
	@Test
	void HUD_세트_덩어리의_아래끝을_HUD_쪽_값으로_다시_짚는다() {
		assertEquals(CoordinateHud.NEXT_LINE_Y, InventoryTeamButton.HUD_NEXT_LINE_Y,
				"좌표·바이옴 아래 자리가 옮겨졌다");
		assertEquals(BottomLeftStack.LINE_HEIGHT, InventoryTeamButton.HUD_LINE_HEIGHT,
				"HUD 글줄 높이가 바뀌었다");
		assertEquals(6, PerkSetLines.MAX_HUD_LINES, "세트 줄 상한이 바뀌었다");

		int hudBottom = CoordinateHud.NEXT_LINE_Y + 3 + 1 + 3
				+ PerkSetLines.MAX_HUD_LINES * BottomLeftStack.LINE_HEIGHT;
		assertEquals(91, hudBottom, "세트 줄이 가장 많을 때의 아래끝");
		assertEquals(hudBottom, InventoryTeamButton.HUD_BOTTOM);
	}

	/**
	 * 두 덩어리의 <b>세로 범위가 만나지 않는다.</b>
	 *
	 * <p>HUD 세트 줄과 이 덩어리는 둘 다 화면 왼쪽에 있고, 화면이 좁으면 가로 범위가 거의
	 * 통째로 겹친다. 그래서 세로로 갈라야 한다. 바닐라가 GUI 배율을 스스로 낮춰 화면 세로를
	 * 240 아래로 두지 않으므로 240 부터 훑는다.
	 *
	 * <p>세트 줄 수는 0~6 으로 변하지만 <b>6 줄일 때를 기준으로</b> 잡는다. 지금 줄 수에
	 * 맞추면 증강을 새 유형에서 뽑을 때마다 능력치 전체가 미끄러진다.
	 */
	@Test
	void 세트_줄이_가장_많을_때도_능력치와_세로로_겹치지_않는다() {
		for (int screenHeight = 240; screenHeight <= 2160; screenHeight++) {
			int topPos = (screenHeight - IMAGE_HEIGHT) / 2;
			int blockTop = InventoryTeamButton.y(topPos);

			assertTrue(blockTop >= InventoryTeamButton.HUD_BOTTOM + InventoryTeamButton.HUD_GAP,
					"화면 세로 " + screenHeight + " 에서 덩어리가 세트 줄에 올라탔다: " + blockTop);
			assertTrue(InventoryTeamButton.statTop(topPos) > blockTop,
					"능력치는 단추 아래다");
		}
	}

	/**
	 * 단추 자체도 세트 줄과 겹치지 않는다.
	 *
	 * <p>능력치만 내리고 단추를 창 위 끝에 두면, 배율이 큰 화면에서 단추가 「◆ 채굴 3/3」
	 * 위에 그대로 앉는다. 글자가 뭉개지는 것보다 <b>무엇을 누르는지 모르게 되는 쪽</b>이 더
	 * 나쁘다.
	 */
	@Test
	void 단추가_세트_줄_위에_앉지_않는다() {
		// 1920×1080 배율 4 · 1280×960 배율 4 · 1280×720 배율 3 — 창이 위로 올라붙는 자리들.
		for (int screenHeight : new int[] {240, 256, 262, 270, 288}) {
			int buttonTop = InventoryTeamButton.y((screenHeight - IMAGE_HEIGHT) / 2);

			assertTrue(buttonTop >= InventoryTeamButton.HUD_BOTTOM,
					"화면 세로 " + screenHeight + " 에서 단추가 세트 줄과 겹친다: " + buttonTop);
			assertTrue(buttonTop + InventoryTeamButton.HEIGHT < screenHeight,
					"단추가 화면 아래로 나갔다");
		}
	}

	/** 화면이 넉넉하면 창 위 끝 그대로다. 내려간 자리는 짧은 화면에서만 쓴다. */
	@Test
	void 창이_이미_아래에_있으면_그대로_창_위_끝에_맞춘다() {
		assertEquals(160, InventoryTeamButton.y(160), "1920×1080 배율 2 의 창 위 끝");
		assertEquals(InventoryTeamButton.TOP_LIMIT, InventoryTeamButton.y(-5),
				"창이 화면보다 커도 HUD 아래에서 시작한다");
		assertEquals(95, InventoryTeamButton.TOP_LIMIT, "세트 줄 아래끝 91 + 틈 4");
	}

	/**
	 * 내려간 뒤에도 한 줄짜리 여덟 줄은 어느 배율에서나 들어간다.
	 *
	 * <p>가장 짧은 화면(240)에서 121px 이 남고 여덟 줄은 84px 이다. <b>두 줄로 접은 여덟 줄
	 * (164px)은 들어가지 않는다</b> — HUD 가 위쪽 91px 을 쓰는 이상 240px 짜리 화면에 둘을
	 * 함께 세울 자리가 없다. 그때는 {@link InventoryStatPanel.Style#HIDDEN} 이 되어 단추만
	 * 남고, 같은 값을 팀 화면 「능력치」 탭에서 본다.
	 */
	@Test
	void 가장_낮은_화면에서도_한_줄짜리_여덟_줄은_들어간다() {
		int screenHeight = 240;
		int topPos = (screenHeight - IMAGE_HEIGHT) / 2;

		assertEquals(10, topPos);
		assertEquals(121, InventoryTeamButton.statHeight(screenHeight, topPos));
		assertTrue(InventoryTeamButton.statHeight(screenHeight, topPos)
						>= 8 * InventoryStatPanel.LINE_HEIGHT + InventoryStatPanel.GROUP_GAP,
				"여덟 줄은 어느 배율에서나 들어가야 한다");
	}

	/** 덩어리는 어떤 화면 세로에서도 화면 아래로 나가지 않는다. */
	@Test
	void 어떤_화면_세로에서도_단추가_화면_안에_있다() {
		for (int screenHeight = 240; screenHeight <= 2160; screenHeight++) {
			int topPos = (screenHeight - IMAGE_HEIGHT) / 2;
			assertTrue(InventoryTeamButton.statHeight(screenHeight, topPos) > 0,
					"화면 세로 " + screenHeight + " 에서 단추 아래에 아무 자리도 없다");
		}
	}
}
