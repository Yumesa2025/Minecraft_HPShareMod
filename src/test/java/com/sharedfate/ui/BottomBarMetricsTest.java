package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 핫바 위 바닐라 표시가 차지하는 높이.
 *
 * <p>그리는 일은 살아 있는 클라이언트가 해야 하지만, <b>하트가 몇 줄이고 어디까지 올라가는가</b>
 * 는 순수 계산이라 여기서 확인할 수 있다. 이 시험이 지키는 것은 하나다 — 최대 체력이 증강으로
 * 바뀌어도 우리 HUD 가 체력 표시를 덮지 않는 것.
 *
 * <p>기대값은 26.2 의 {@code net.minecraft.client.gui.Hud} 에서 그대로 따왔다. 맨 아랫줄이
 * {@code guiHeight - 39}, 줄 간격이 {@code max(10 - (줄수 - 2), 3)} 이다.
 */
class BottomBarMetricsTest {

	/** 하트가 아무리 늘어도 재생 효과로 2픽셀 튀어오르는 몫은 늘 비워 둔다. */
	private static final int LIFT = BottomBarMetrics.REGENERATION_LIFT;

	// ── 줄 수 ──────────────────────────────────────────────────────────────

	@Test
	void 기본_체력_20은_한_줄이다() {
		assertEquals(1, BottomBarMetrics.heartRows(20.0F, 0.0F));
	}

	/** 「거인」 +6, 「장님 거인」 +15. 둘 다 하트가 두 줄이 된다. */
	@Test
	void 최대_체력이_20을_넘으면_두_줄이다() {
		assertEquals(2, BottomBarMetrics.heartRows(26.0F, 0.0F));
		assertEquals(2, BottomBarMetrics.heartRows(35.0F, 0.0F));
		assertEquals(2, BottomBarMetrics.heartRows(40.0F, 0.0F));
	}

	@Test
	void 최대_체력이_40을_넘으면_세_줄이다() {
		assertEquals(3, BottomBarMetrics.heartRows(41.0F, 0.0F));
		assertEquals(3, BottomBarMetrics.heartRows(60.0F, 0.0F));
	}

	/** 「전리품 방패」·「흡혈귀」·세트 「교환 2」가 거는 흡수(노란 하트)도 줄을 차지한다. */
	@Test
	void 흡수_체력도_줄_수에_들어간다() {
		assertEquals(1, BottomBarMetrics.heartRows(20.0F, 0.0F));
		assertEquals(2, BottomBarMetrics.heartRows(20.0F, 4.0F));
		assertEquals(3, BottomBarMetrics.heartRows(20.0F, 24.0F));
	}

	/**
	 * 바닐라는 간격을 잡을 때와 하트를 찍을 때 올림하는 자리가 다르다.
	 *
	 * <p>간격은 {@code (체력 + 흡수) / 2} 를 한 번에 올리고, 하트는 각각 따로 올린다. 체력 39에
	 * 흡수 1이면 간격은 두 줄 기준이지만 하트는 세 줄로 그려진다. 겹침을 막는 쪽은 실제로
	 * 그려지는 줄이라 이 어긋남을 그대로 따라가야 한다.
	 */
	@Test
	void 홀수가_섞이면_간격_기준_줄_수보다_한_줄_더_그려진다() {
		assertEquals(2, BottomBarMetrics.spacingRows(39.0F, 1.0F));
		assertEquals(3, BottomBarMetrics.heartRows(39.0F, 1.0F));
	}

	// ── 줄 간격 ────────────────────────────────────────────────────────────

	@Test
	void 줄이_늘수록_간격이_촘촘해진다() {
		assertEquals(11, BottomBarMetrics.rowSpacing(20.0F, 0.0F));
		assertEquals(10, BottomBarMetrics.rowSpacing(26.0F, 0.0F));
		assertEquals(9, BottomBarMetrics.rowSpacing(41.0F, 0.0F));
		assertEquals(8, BottomBarMetrics.rowSpacing(61.0F, 0.0F));
	}

	/** 아무리 줄이 늘어도 3픽셀 아래로는 내려가지 않는다. */
	@Test
	void 간격은_3픽셀에서_멈춘다() {
		assertEquals(3, BottomBarMetrics.rowSpacing(400.0F, 0.0F));
		assertEquals(3, BottomBarMetrics.rowSpacing(1024.0F, 512.0F));
	}

	// ── 왼쪽 높이(하트 + 방어구) ───────────────────────────────────────────

	@Test
	void 한_줄이면_맨_아랫줄_높이만_쓴다() {
		assertEquals(BottomBarMetrics.BOTTOM_ROW + LIFT,
				BottomBarMetrics.leftHeight(20.0F, 0.0F, false));
	}

	@Test
	void 두_줄이면_간격만큼_올라간다() {
		// 39 + (2-1) * 10 + 2
		assertEquals(51, BottomBarMetrics.leftHeight(26.0F, 0.0F, false));
	}

	@Test
	void 세_줄이면_간격이_줄어든_만큼만_올라간다() {
		// 39 + (3-1) * 9 + 2
		assertEquals(59, BottomBarMetrics.leftHeight(41.0F, 0.0F, false));
	}

	@Test
	void 흡수가_붙으면_한_줄_더_올라간다() {
		int without = BottomBarMetrics.leftHeight(20.0F, 0.0F, false);
		int with = BottomBarMetrics.leftHeight(20.0F, 20.0F, false);
		assertEquals(41, without);
		assertEquals(51, with);
		assertTrue(with > without, "흡수가 붙었는데 높이가 그대로다");
	}

	@Test
	void 방어구를_입으면_하트_위로_한_줄_더_생긴다() {
		// 39 + (1-1) * 11 + 10
		assertEquals(49, BottomBarMetrics.leftHeight(20.0F, 0.0F, true));
		// 39 + (2-1) * 10 + 10
		assertEquals(59, BottomBarMetrics.leftHeight(26.0F, 0.0F, true));
		// 39 + (3-1) * 9 + 10
		assertEquals(67, BottomBarMetrics.leftHeight(41.0F, 0.0F, true));
	}

	@Test
	void 방어구가_0이면_그_줄을_세지_않는다() {
		assertEquals(BottomBarMetrics.leftHeight(41.0F, 0.0F, false) + 8,
				BottomBarMetrics.leftHeight(41.0F, 0.0F, true));
	}

	/**
	 * 최대 체력이 늘어나는 동안 높이가 뒷걸음질하지 않는다.
	 *
	 * <p>줄 간격이 함께 촘촘해지므로 "줄이 늘면 그만큼 많이 올라간다"가 아니다. 일곱 줄(체력
	 * 141)을 넘기면 간격이 줄어드는 속도가 줄 수보다 빨라서 <b>바닐라 표시 자체가 다시
	 * 낮아진다.</b> 그건 우리 잘못이 아니라 바닐라 규칙이라 그대로 따라가고, 여기서는 이
	 * 모드에서 실제로 닿는 범위만 지킨다 — 기본 20에 「장님 거인」(+15)·「거인」(+6)을 여러 번
	 * 겹쳐도 140에는 닿지 않는다.
	 */
	@Test
	void 하트가_늘면_높이가_줄지_않는다() {
		for (boolean armor : new boolean[] { false, true }) {
			int previous = 0;
			for (float maxHealth = 2.0F; maxHealth <= 140.0F; maxHealth += 1.0F) {
				int height = BottomBarMetrics.leftHeight(maxHealth, 0.0F, armor);
				assertTrue(height >= previous,
						"최대 체력 " + maxHealth + " 에서 높이가 " + previous + " → " + height);
				previous = height;
			}
		}
	}

	@Test
	void 흡수가_늘어도_높이가_줄지_않는다() {
		int previous = 0;
		for (float absorption = 0.0F; absorption <= 120.0F; absorption += 1.0F) {
			int height = BottomBarMetrics.leftHeight(20.0F, absorption, false);
			assertTrue(height >= previous,
					"흡수 " + absorption + " 에서 높이가 " + previous + " → " + height);
			previous = height;
		}
	}

	// ── 오른쪽 높이(배고픔·탈것 체력·공기 방울) ────────────────────────────

	/** 배고픔은 한 줄이지만 포만감이 0이면 1픽셀 위로 떨린다. */
	@Test
	void 배고픔만_있으면_한_줄에_떨림_몫만_더한다() {
		assertEquals(BottomBarMetrics.BOTTOM_ROW + BottomBarMetrics.FOOD_SHAKE,
				BottomBarMetrics.rightHeight(0, false));
	}

	@Test
	void 물속에서는_공기_방울이_한_줄_더_올라간다() {
		assertEquals(49, BottomBarMetrics.rightHeight(0, true));
	}

	/** 탈것을 타면 배고픔 대신 탈것 하트가 나온다. 말은 두 줄까지 간다. */
	@Test
	void 탈것_하트도_줄_수만큼_올라간다() {
		assertEquals(39, BottomBarMetrics.rightHeight(10, false));
		assertEquals(49, BottomBarMetrics.rightHeight(11, false));
		assertEquals(59, BottomBarMetrics.rightHeight(30, false));
		// 공기 방울은 탈것 하트 맨 윗줄보다 또 한 줄 위다.
		assertEquals(69, BottomBarMetrics.rightHeight(30, true));
	}

	// ── 전체 높이 ──────────────────────────────────────────────────────────

	@Test
	void 전체_높이는_왼쪽과_오른쪽_중_높은_쪽이다() {
		// 하트 한 줄뿐이면 물속 공기 방울이 더 높다.
		assertEquals(49, BottomBarMetrics.height(20.0F, 0.0F, false, 0, true));
		// 하트가 세 줄이면 왼쪽이 이긴다.
		assertEquals(59, BottomBarMetrics.height(41.0F, 0.0F, false, 0, true));
	}

	@Test
	void 실제_y는_화면_높이에서_뺀_값이다() {
		assertEquals(360 - 41, BottomBarMetrics.top(360, 20.0F, 0.0F, false, 0, false));
		assertEquals(240 - 67, BottomBarMetrics.top(240, 41.0F, 0.0F, true, 0, false));
	}

	/**
	 * 체력이 20일 때도 겹쳤다는 사실을 못으로 박아 둔다.
	 *
	 * <p>고치기 전 게이지는 경험치 레벨 숫자에 붙어 화면 아래끝에서 43~37픽셀 자리를 썼다.
	 * 그런데 하트 맨 윗줄은 39픽셀에서 시작한다. 하트가 한 줄뿐인 기본 상태에서도 3픽셀이
	 * 겹쳤다는 뜻이다. 하트 줄이 늘면 더 깊이 파고든다.
	 */
	@Test
	void 예전_게이지_자리는_하트_한_줄에도_파고들었다() {
		int oldBarTop = 43;
		int oldBarBottom = 37;
		int heartTop = BottomBarMetrics.BOTTOM_ROW;
		assertTrue(oldBarBottom < heartTop && heartTop <= oldBarTop,
				"예전 자리가 하트와 겹치지 않는다고 나온다");

		// 지금 계산은 어느 조합에서도 하트 윗변보다 위에 있어야 한다.
		for (float maxHealth : new float[] { 20.0F, 26.0F, 35.0F, 41.0F, 60.0F }) {
			for (float absorption : new float[] { 0.0F, 4.0F, 20.0F }) {
				for (boolean armor : new boolean[] { false, true }) {
					assertTrue(BottomBarMetrics.height(maxHealth, absorption, armor, 0, false)
									> oldBarBottom,
							"체력 " + maxHealth + " 흡수 " + absorption + " 방어구 " + armor);
				}
			}
		}
	}
}
