package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 능력치 한 줄의 글자와 색.
 *
 * <p>{@code StatSummaryTest} 가 「값 → 값 (증감)」의 모양을 붙들고, 여기서는 그 위에 얹힌
 * 두 가지를 본다 — <b>자리에 따라 줄이는 방법</b>과 <b>색이 뒤집히는 줄</b>이다.
 */
class StatRowTest {

	private static StatRow health() {
		return StatRow.of("최대 체력", "체력", 20, 26, StatSummary.Unit.RAW,
				StatRow.Sense.HIGHER_IS_BETTER);
	}

	// ------------------------------------------------------------------ 줄이는 방법

	@Test
	void 자리에_따라_세_가지_모양으로_줄인다() {
		StatRow row = health();

		assertEquals("최대 체력  20 → 26  (+6)", row.fullLine());
		assertEquals("체력  20 → 26  (+6)", row.shortLine());
		assertEquals("체력 20 → 26", row.tightLine());
		assertEquals("20 → 26", row.values());
	}

	/**
	 * 줄여도 <b>기준값은 끝까지 남는다.</b>
	 *
	 * <p>「26」만 적으면 그것이 높은 값인지 낮은 값인지 알 수 없다. 증감 괄호를 떼는 것과
	 * 「→」 왼쪽을 떼는 것은 잃는 것이 다르다.
	 */
	@Test
	void 가장_좁은_모양에도_기준값이_남는다() {
		for (String text : new String[] {health().tightLine(), health().values()}) {
			assertEquals(true, text.contains("20 → 26"), text);
		}
	}

	/** 덧붙임말은 넉넉한 자리에만 붙는다. 좁은 화면에서 「(하트 13개)」까지 넣을 자리는 없다. */
	@Test
	void 덧붙임말은_온전한_줄에만_붙는다() {
		StatRow row = health().withSuffix("  (하트 13개)");

		assertEquals("최대 체력  20 → 26  (+6)  (하트 13개)", row.fullLine());
		assertEquals("체력  20 → 26  (+6)", row.shortLine());
		assertEquals("체력 20 → 26", row.tightLine());
	}

	// ------------------------------------------------------------------ 배율 줄

	@Test
	void 배율은_100퍼센트를_기준으로_적는다() {
		StatRow row = StatRow.multiplier("몹 체력", "몹체력", 1.15,
				StatRow.Sense.LOWER_IS_BETTER);

		assertEquals("몹 체력  100% → 115%  (+15%)", row.fullLine());
		assertEquals("몹체력 100% → 115%", row.tightLine());
	}

	@Test
	void 아무것도_안_걸리면_배율은_100퍼센트_그대로다() {
		StatRow row = StatRow.multiplier("받는 피해", "피해", 1.0,
				StatRow.Sense.LOWER_IS_BETTER);

		assertEquals("받는 피해  100% → 100%", row.fullLine());
		assertEquals(StatRow.COLOR_NEUTRAL, row.color());
	}

	// ------------------------------------------------------------------ 색

	/** 보통 줄 — 오르면 초록. */
	@Test
	void 오르는_것이_좋은_줄은_오르면_초록이다() {
		assertEquals(StatRow.COLOR_GOOD, health().color());
		assertEquals(StatRow.COLOR_BAD,
				StatRow.of("공격 속도", "공속", 4, 1.6, StatSummary.Unit.RAW,
						StatRow.Sense.HIGHER_IS_BETTER).color(),
				"검을 들면 공격 속도가 내려간다. 실제로 느려지므로 빨강이 맞다");
	}

	/**
	 * 받는 피해·몹 줄 — <b>오르면 빨강</b>이다.
	 *
	 * <p>이 시험을 지우지 말 것. 「기본값에서 올랐다」만 보고 색을 칠하는 코드로 돌아가면
	 * 몹이 두 배가 된 것을 초록으로 알리게 된다.
	 */
	@Test
	void 오르는_것이_나쁜_줄은_색이_뒤집힌다() {
		StatRow worse = StatRow.multiplier("몹 체력", "몹체력", 2.0,
				StatRow.Sense.LOWER_IS_BETTER);
		StatRow better = StatRow.multiplier("받는 피해", "피해", 0.5,
				StatRow.Sense.LOWER_IS_BETTER);

		assertEquals(StatRow.COLOR_BAD, worse.color());
		assertEquals(-1, worse.tone());
		assertEquals(StatRow.COLOR_GOOD, better.color());
		assertEquals(1, better.tone());
	}

	// ------------------------------------------------------------------ 가려진 줄

	/**
	 * 가려진 줄은 <b>사라지지 않고</b> 물음표가 된다.
	 *
	 * <p>줄이 통째로 없어지면 「그런 능력치가 없다」로 읽힌다. 색도 알려 주면 안 된다 —
	 * 초록·빨강이 곧 답이 되어 가린 뜻이 없어진다.
	 */
	@Test
	void 가려진_줄은_물음표와_흐린_색이다() {
		StatRow row = StatRow.masked("방어력", "방어");

		assertEquals("방어력  ???", row.fullLine());
		assertEquals("방어  ???", row.shortLine());
		assertEquals("???", row.values());
		assertEquals(0, row.tone());
		assertEquals(StatRow.COLOR_MASKED, row.color());
	}
}
