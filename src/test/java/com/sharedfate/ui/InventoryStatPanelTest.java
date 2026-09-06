package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인벤토리 화면 왼쪽 능력치가 <b>자리에 따라 어떻게 물러나는가</b>.
 *
 * <p>이 시험의 값들은 짐작이 아니다. 줄의 폭은 실제 폰트 자원에서 뽑은 {@link FakeFont} 로
 * 재고, 남는 자리는 바닐라 인벤토리 창(176px)이 화면 한가운데 놓인다는 사실에서 나온다.
 * 그래서 「1920×1080 에서 GUI 배율 4면 이렇게 보인다」를 여기서 그대로 확인할 수 있다.
 *
 * <p>가장 중요한 것은 <b>어떤 경우에도 자리를 넘지 않는다</b>는 것이다. 넘으면 인벤토리 창을
 * 덮거나 화면 밖으로 삐져나간다.
 */
class InventoryStatPanelTest {
	private static final int IMAGE_WIDTH = 176;
	/** 확장 27칸이 붙은 인벤토리 창 높이(166 + 54). */
	private static final int IMAGE_HEIGHT = 220;

	/**
	 * 실제로 그려지는 여덟 줄.
	 *
	 * <p>값은 「증강을 여럿 가진 팀이 검을 들고 있는」 흔한 상태로 잡았다. 자릿수가 가장 많이
	 * 나오는 쪽이라야 폭 계산이 낙관적으로 흐르지 않는다.
	 */
	private static List<List<StatRow>> rows() {
		return List.of(
				List.of(
						StatRow.of("최대 체력", "체력", 20, 26, StatSummary.Unit.RAW,
								StatRow.Sense.HIGHER_IS_BETTER).withSuffix("  (하트 13개)"),
						StatRow.of("공격력", "공격", 1, 7.5, StatSummary.Unit.RAW,
								StatRow.Sense.HIGHER_IS_BETTER),
						StatRow.of("공격 속도", "공속", 4, 1.6, StatSummary.Unit.RAW,
								StatRow.Sense.HIGHER_IS_BETTER),
						StatRow.of("방어력", "방어", 0, 12, StatSummary.Unit.RAW,
								StatRow.Sense.HIGHER_IS_BETTER),
						StatRow.of("이동 속도", "속도", 0.1, 0.115, StatSummary.Unit.PERCENT,
								StatRow.Sense.HIGHER_IS_BETTER),
						StatRow.multiplier("받는 피해", "피해", 2.5, StatRow.Sense.LOWER_IS_BETTER)),
				List.of(
						StatRow.multiplier("몹 체력", "몹체력", 1.15, StatRow.Sense.LOWER_IS_BETTER),
						StatRow.multiplier("몹 공격력", "몹공격", 1.15,
								StatRow.Sense.LOWER_IS_BETTER)));
	}

	/** GUI 배율에서 화면 폭이 나오고, 그 폭에서 창 왼쪽에 남는 자리가 나온다. */
	private static int available(int screenWidth) {
		return InventoryTeamButton.available(screenWidth, IMAGE_WIDTH,
				(screenWidth - IMAGE_WIDTH) / 2);
	}

	/** 조합법 책을 펼쳤을 때 남는 자리. 바닐라가 창을 오른쪽으로 민다. */
	private static int availableWithBook(int screenWidth) {
		return InventoryTeamButton.available(screenWidth, IMAGE_WIDTH,
				177 + (screenWidth - IMAGE_WIDTH - 200) / 2);
	}

	/** 화면 세로에서 능력치가 쓸 수 있는 높이. */
	private static int height(int screenHeight) {
		return InventoryTeamButton.statHeight(screenHeight, (screenHeight - IMAGE_HEIGHT) / 2);
	}

	private static InventoryStatPanel.Layout layout(int width, int height) {
		return InventoryStatPanel.layout(rows(), width, height, FakeFont::width);
	}

	// ------------------------------------------------------------- 배율별로 어떻게 보이는가

	/**
	 * 1920×1080 · 배율 1·2·3 — 온전한 이름과 증감까지 다 들어간다.
	 *
	 * <p>화면 폭이 각각 1920 · 960 · 640 이고 남는 자리는 868 · 388 · 228 px 다. 가장 긴 줄이
	 * 「최대 체력  20 → 26  (+6)  (하트 13개)」로 172px 이므로 셋 다 넉넉하다.
	 */
	@Test
	void 배율_1_2_3_에서는_온전한_이름으로_다_보인다() {
		for (int screenWidth : new int[] {1920, 960, 640}) {
			assertEquals(InventoryStatPanel.Style.FULL,
					layout(available(screenWidth), height(1080 / (1920 / screenWidth))).style(),
					"화면 폭 " + screenWidth);
		}
	}

	/**
	 * 1920×1080 · 배율 4 — 이름만 줄인다.
	 *
	 * <p>화면이 480×270 이 되어 남는 자리가 148px 다. 온전한 이름(172px)은 못 들어가지만
	 * 줄인 이름(가장 긴 줄 138px)은 들어간다.
	 */
	@Test
	void 배율_4_에서는_이름을_줄여_보인다() {
		assertEquals(148, available(480));
		assertEquals(InventoryStatPanel.Style.SHORT, layout(148, height(270)).style());
	}

	/**
	 * 1280×720 · 배율 3 — 증감 괄호까지 뗀다.
	 *
	 * <p>화면이 427×240 이라 남는 자리가 121px 다. 줄인 이름에 증감까지(138px)는 못 들어가고,
	 * 괄호를 떼면(94px) 들어간다. 오르내림은 색이 그대로 말해 준다.
	 */
	@Test
	void 좁은_화면에서는_증감_괄호를_뗀다() {
		assertEquals(121, available(427));
		assertEquals(InventoryStatPanel.Style.TIGHT, layout(121, height(240)).style());
	}

	/**
	 * 가로가 아주 좁으면 이름과 값을 두 줄로 접는다. <b>세로가 넉넉할 때만이다.</b>
	 *
	 * <p>가로 68px 은 바닐라가 허용하는 가장 작은 GUI(320×240)에서 창 왼쪽에 남는 자리다.
	 * 한 줄로는 어떤 모양도 못 들어가지만, 접으면 가장 긴 줄이 「100% → 250%」 64px 이라
	 * 들어간다. 대신 세로를 두 배 쓴다 — 16줄에 묶음 틈까지 164px 이다.
	 */
	@Test
	void 가로가_좁고_세로가_넉넉하면_두_줄로_접는다() {
		assertEquals(68, available(320));
		InventoryStatPanel.Layout layout = layout(68, 300);

		assertEquals(InventoryStatPanel.Style.WRAPPED, layout.style());
		assertEquals(16, layout.lines().size(), "여덟 줄이 두 줄씩 접힌다");
		assertEquals(164, layout.height());
	}

	/**
	 * 짧은 화면에서는 접는 것도 못 하고 감춘다.
	 *
	 * <p>덩어리가 HUD 세트 줄을 피해 {@link InventoryTeamButton#TOP_LIMIT} 까지 내려가므로,
	 * 화면 세로 240 에서 능력치에 남는 것은 121px 뿐이다. 접은 여덟 줄은 164px 이라 들어가지
	 * 않는다. <b>HUD 가 위쪽 91px 을 쓰는 이상 240px 짜리 화면에 둘을 함께 세울 자리가
	 * 없다</b> — 겹쳐 그려 둘 다 못 읽느니 이쪽을 접고, 같은 값은 단추가 여는 팀 화면
	 * 「능력치」 탭에 그대로 있다.
	 *
	 * <p>세로 283 부터는 접은 여덟 줄이 다시 들어간다(단추 아래 자리 = 화면 세로 − 119).
	 */
	@Test
	void 세로가_모자라면_접지_못하고_감춘다() {
		assertEquals(121, height(240));
		assertFalse(layout(68, height(240)).visible());

		assertEquals(164, height(283), "여기서부터 접은 여덟 줄이 들어간다");
		assertEquals(InventoryStatPanel.Style.WRAPPED, layout(68, height(283)).style());
		assertFalse(layout(68, height(282)).visible(), "1px 만 모자라도 감춘다");
	}

	/**
	 * 조합법 책을 펼치면 가로가 더 줄고, 어느 지점부터는 아무것도 못 그린다.
	 *
	 * <p>바닐라는 화면 폭이 379 이상일 때 창을 오른쪽으로 밀어 왼쪽에 책 자리를 만든다.
	 * 그 지점이 왼쪽 자리가 가장 좁아지는 순간이다 — 화면 폭 427 에서는 50px 밖에 안 남아
	 * 접은 줄(64px)도 못 들어간다. 그때는 <b>단추만 남기고 능력치는 감춘다.</b>
	 */
	@Test
	void 조합법_책을_펼쳐_자리가_없으면_감춘다() {
		assertEquals(50, availableWithBook(427));
		assertFalse(layout(50, height(240)).visible());

		// 배율 4(480×270)에서는 책을 펼쳐도 가로는 76px 이 남지만, 세로가 151px 이라
		// 접은 여덟 줄(164px)이 못 들어간다. 같은 폭이라도 화면이 길어지면 다시 접힌다.
		assertEquals(76, availableWithBook(480));
		assertEquals(151, height(270));
		assertFalse(layout(76, height(270)).visible());
		assertEquals(InventoryStatPanel.Style.WRAPPED, layout(76, height(320)).style());
	}

	/**
	 * <b>어떤 화면 세로에서도 마지막 줄이 화면 아래로 나가지 않는다.</b>
	 *
	 * <p>덩어리를 HUD 아래로 내렸으므로 이번에는 아래쪽이 위험해진다. 그릴 자리를
	 * {@link InventoryTeamButton#statHeight} 가 화면 바닥까지로 잡고
	 * {@link InventoryStatPanel#layout} 이 그 안에 드는 모양만 고르므로, 둘을 이어 붙여
	 * 실제 마지막 줄의 아랫변을 화면과 견준다.
	 */
	@Test
	void 어떤_화면_세로에서도_마지막_줄이_화면_아래로_나가지_않는다() {
		for (int screenHeight = 240; screenHeight <= 1200; screenHeight++) {
			int topPos = (screenHeight - IMAGE_HEIGHT) / 2;
			int statTop = InventoryTeamButton.statTop(topPos);
			for (int width : new int[] {50, 68, 76, 121, 148, 228, 868}) {
				InventoryStatPanel.Layout layout =
						layout(width, InventoryTeamButton.statHeight(screenHeight, topPos));
				if (!layout.visible()) {
					continue;
				}
				assertTrue(statTop + layout.height() <= screenHeight,
						"화면 세로 " + screenHeight + " · 가로 " + width + " 에서 아래로 넘쳤다");
				assertTrue(statTop >= InventoryTeamButton.HUD_BOTTOM,
						"화면 세로 " + screenHeight + " 에서 세트 줄과 겹친다");
			}
		}
	}

	/** 자리가 없어 감출 때는 줄을 하나도 만들지 않는다. 반쯤 그리는 일이 없어야 한다. */
	@Test
	void 감출_때는_아무_줄도_남기지_않는다() {
		InventoryStatPanel.Layout layout = layout(20, 400);

		assertEquals(InventoryStatPanel.Style.HIDDEN, layout.style());
		assertTrue(layout.lines().isEmpty());
		assertEquals(0, layout.width());
	}

	// ------------------------------------------------------------- 어떤 경우에도 넘지 않는다

	/**
	 * 그리기로 한 이상 준 자리를 넘지 않는다.
	 *
	 * <p>넘으면 인벤토리 창을 덮거나 화면 밖으로 나간다.
	 */
	@Test
	void 그리기로_했으면_준_자리를_넘지_않는다() {
		for (int width = 8; width <= 900; width += 3) {
			for (int height : new int[] {40, 90, 170, 206, 600}) {
				InventoryStatPanel.Layout layout = layout(width, height);
				if (!layout.visible()) {
					continue;
				}
				assertTrue(layout.width() <= width, "가로 " + width + " 를 넘었다");
				assertTrue(layout.height() <= height, "세로 " + height + " 를 넘었다");
				for (InventoryStatPanel.Line line : layout.lines()) {
					assertTrue(FakeFont.width(line.text()) <= width, line.text());
					assertTrue(line.y() + InventoryStatPanel.LINE_HEIGHT <= height, line.text());
				}
			}
		}
	}

	/** 자리가 넓어질수록 더 자세해지지, 덜 자세해지지 않는다. */
	@Test
	void 자리가_넓어질수록_더_자세해진다() {
		InventoryStatPanel.Style previous = InventoryStatPanel.Style.FULL;
		assertEquals(InventoryStatPanel.Style.FULL, layout(900, 600).style());
		for (int width = 900; width >= 8; width -= 1) {
			InventoryStatPanel.Style style = layout(width, 600).style();
			assertTrue(style.ordinal() >= previous.ordinal(),
					"가로 " + width + " 에서 " + previous + " → " + style);
			previous = style;
		}
		assertEquals(InventoryStatPanel.Style.HIDDEN, previous, "끝에서는 감춘다");
	}

	// ------------------------------------------------------------- 묶음과 색

	/** 내 능력치와 몹 사이에는 틈이 있다. 몹 체력이 내 체력처럼 읽히면 안 된다. */
	@Test
	void 두_묶음_사이에는_틈이_있다() {
		List<InventoryStatPanel.Line> lines = layout(400, 600).lines();

		assertEquals(8, lines.size());
		int lastOfFirstGroup = lines.get(5).y();
		int firstOfSecondGroup = lines.get(6).y();
		assertEquals(InventoryStatPanel.LINE_HEIGHT + InventoryStatPanel.GROUP_GAP,
				firstOfSecondGroup - lastOfFirstGroup);
	}

	/**
	 * 몹이 세지면 <b>빨강</b>이다.
	 *
	 * <p>다른 줄과 반대다. 「기본값에서 올랐다」만 보고 초록을 칠하면, 몹이 두 배가 된 것을
	 * 좋은 소식으로 알리게 된다.
	 */
	@Test
	void 몹이_세지면_빨강으로_적는다() {
		List<InventoryStatPanel.Line> lines = layout(400, 600).lines();

		assertEquals(StatRow.COLOR_BAD, lines.get(6).color(), "몹 체력 115%");
		assertEquals(StatRow.COLOR_BAD, lines.get(7).color(), "몹 공격력 115%");
		assertEquals(StatRow.COLOR_BAD, lines.get(5).color(), "받는 피해 250%");
		assertEquals(StatRow.COLOR_GOOD, lines.get(0).color(), "최대 체력 26");
		assertEquals(StatRow.COLOR_BAD, lines.get(2).color(), "검을 들어 내려간 공격 속도");
	}
}
