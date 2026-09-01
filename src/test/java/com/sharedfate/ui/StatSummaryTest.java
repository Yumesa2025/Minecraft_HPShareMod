package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「능력치」 탭 한 줄의 표기.
 *
 * <p>화면은 {@code src/client} 에 있어 시험 소스셋이 보지 못하므로, 여기서 확인하는 것은
 * <b>무엇이 얼마나 바뀌었다고 적는가</b>다.
 */
class StatSummaryTest {

	@Test
	void 기본값과_지금_값을_나란히_적고_증감을_붙인다() {
		assertEquals("방어력  0 → 8  (+8)",
				StatSummary.line("방어력", 0.0, 8.0, StatSummary.Unit.RAW));
	}

	@Test
	void 내려간_값도_부호와_함께_적는다() {
		assertEquals("최대 체력  20 → 14  (-6)",
				StatSummary.line("최대 체력", 20.0, 14.0, StatSummary.Unit.RAW));
	}

	/** 「(+0)」은 아무것도 알려 주지 않으면서 줄만 길게 만든다. */
	@Test
	void 그대로면_괄호를_붙이지_않는다() {
		String line = StatSummary.line("방어력", 0.0, 0.0, StatSummary.Unit.RAW);
		assertEquals("방어력  0 → 0", line);
		assertFalse(line.contains("("), line);
	}

	/**
	 * 이동 속도는 백분율로 적는다.
	 *
	 * <p>바닐라 기본값이 0.1 이라 그대로 적으면 빠른지 느린지 알 수 없다.
	 */
	@Test
	void 이동_속도는_기본값을_100으로_놓고_적는다() {
		assertEquals("이동 속도  100% → 115%  (+15%)",
				StatSummary.line("이동 속도", 0.1, 0.115, StatSummary.Unit.PERCENT));
		assertEquals("이동 속도  100% → 90%  (-10%)",
				StatSummary.line("이동 속도", 0.1, 0.09, StatSummary.Unit.PERCENT));
	}

	@Test
	void 기본값이_0이면_백분율을_포기하고_숫자로_적는다() {
		// 0 으로 나눌 수 없다. 방어력처럼 기본값이 0 인 값에 백분율을 잘못 걸어도 깨지지 않아야 한다.
		assertEquals("방어력  0 → 8  (+8)",
				StatSummary.line("방어력", 0.0, 8.0, StatSummary.Unit.PERCENT));
	}

	@Test
	void 부동소수_찌꺼기를_증감으로_읽지_않는다() {
		assertEquals(0, StatSummary.direction(20.0, 20.0 + 1.0E-9));
		assertFalse(StatSummary.line("최대 체력", 20.0, 20.0 + 1.0E-9, StatSummary.Unit.RAW)
				.contains("("));
	}

	@Test
	void 오름과_내림과_그대로를_구분한다() {
		assertEquals(1, StatSummary.direction(0.0, 4.0));
		assertEquals(-1, StatSummary.direction(20.0, 14.0));
		assertEquals(0, StatSummary.direction(0.1, 0.1));
	}

	/**
	 * 「달라졌다」의 뜻은 화면과 네트워크가 나눠 쓴다.
	 *
	 * <p>서버가 공격력을 다시 보낼지 정할 때 이 판단을 쓴다. 화면이 어차피 같은 글자를 그릴
	 * 값이라면 패킷을 쓸 이유가 없다.
	 */
	@Test
	void 화면이_다르게_그릴_만큼_달라졌을_때만_달라진_것이다() {
		assertTrue(StatSummary.changed(1.0, 7.0));
		assertTrue(StatSummary.changed(7.0, 1.0));
		assertFalse(StatSummary.changed(7.0, 7.0));
		// 부동소수 찌꺼기로 패킷을 보내지 않는다.
		assertFalse(StatSummary.changed(7.0, 7.0 + 1.0E-9));
	}

	@Test
	void 소수점이_의미_없는_값은_정수로_적는다() {
		assertEquals("20", StatSummary.number(20.0));
		assertEquals("6.5", StatSummary.number(6.5));
		assertEquals("-2", StatSummary.number(-2.0));
	}

	/** 소수가 나오는 값도 한 자리까지는 보여 준다. 반올림해서 0 으로 만들면 거짓말이 된다. */
	@Test
	void 소수_한_자리까지_보여_준다() {
		String line = StatSummary.line("최대 체력", 20.0, 21.5, StatSummary.Unit.RAW);
		assertTrue(line.contains("21.5"), line);
		assertTrue(line.contains("(+1.5)"), line);
	}
}
