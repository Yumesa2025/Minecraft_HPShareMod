package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「다음 보급까지 04:12」를 만드는 계산.
 *
 * <p>이 계산이 지켜야 하는 것은 다섯이다 — 서버가 재는 경계와 <b>같은 자리</b>를 볼 것,
 * <b>켜진 시점부터</b> 셀 것, 00:00 에서 멈춰 있는 것처럼 보이지 않을 것, 도는 보급이 없으면
 * 아무것도 안 적을 것, 그리고 <b>한 주기 내내 글자 수가 같을 것</b>.
 *
 * <p>서버의 경계는 {@code PerkSupplyDrops.cycleAt} 이 켜진 시점부터 {@code floorDiv} 로 정한
 * 자리다. 여기서 쓰는 {@code floorMod} 는 그것의 짝이라, 두 식이 같은 자리를 가리키는지가 곧
 * 「화면의 00:01 다음 틱에 보급이 오는가」이다.
 */
class SupplyCountdownTest {
	/** 20 * 60. 1분. */
	private static final int MINUTE = 20 * 60;
	/** 「보급 2」의 주기. */
	private static final int TEN_MINUTES = 10 * MINUTE;
	/** 「보급 4」의 주기. */
	private static final int FIVE_MINUTES = 5 * MINUTE;

	// ------------------------------------------------------------------ 남은 틱

	/**
	 * 경계에 정확히 서 있으면 주기 전체가 남은 것이다.
	 *
	 * <p>그 틱에 보급이 오고 다음 것은 꼬박 한 주기 뒤다. 여기서 0 을 돌려주면 화면이 한 틱
	 * 동안 「00:00」을 띄운다.
	 */
	@Test
	void 경계에서는_주기_전체가_남는다() {
		assertEquals(TEN_MINUTES, SupplyCountdown.remainingTicks(0L, TEN_MINUTES));
		assertEquals(TEN_MINUTES, SupplyCountdown.remainingTicks(TEN_MINUTES, TEN_MINUTES));
		assertEquals(TEN_MINUTES, SupplyCountdown.remainingTicks(TEN_MINUTES * 37L, TEN_MINUTES));
	}

	@Test
	void 경계_직전에는_한_틱_남는다() {
		assertEquals(1, SupplyCountdown.remainingTicks(TEN_MINUTES - 1, TEN_MINUTES));
		assertEquals(1, SupplyCountdown.remainingTicks(TEN_MINUTES * 5L - 1, TEN_MINUTES));
	}

	/** 주기가 5분으로 줄면(4단계) 남은 시간도 그 주기 안에서 다시 잡힌다. */
	@Test
	void 주기가_바뀌면_남은_시간도_그_주기를_따른다() {
		long time = TEN_MINUTES * 3L + MINUTE;
		assertEquals(9 * MINUTE, SupplyCountdown.remainingTicks(time, TEN_MINUTES));
		assertEquals(4 * MINUTE, SupplyCountdown.remainingTicks(time, FIVE_MINUTES));
	}

	/** 도는 보급이 없으면 0 이다. 주기가 0 이하인 것이 곧 그 뜻이다. */
	@Test
	void 주기가_없으면_0이다() {
		assertEquals(0, SupplyCountdown.remainingTicks(12345L, 0));
		assertEquals(0, SupplyCountdown.remainingTicks(12345L, -1));
	}

	/**
	 * 게임 시간이 음수여도 주기보다 큰 값이 나오지 않는다.
	 *
	 * <p>실제로 음수가 되는 일은 없지만, {@code %} 를 쓰면 음수에서 음수가 나와 남은 시간이
	 * 주기보다 커진다. {@code floorMod} 를 쓰는 까닭이 이것이라 지켜 둔다.
	 */
	@Test
	void 음수_시간에서도_주기_안에_들어온다() {
		for (long time = -TEN_MINUTES * 2L; time < 0; time += 137) {
			int remaining = SupplyCountdown.remainingTicks(time, TEN_MINUTES);
			assertTrue(remaining >= 1 && remaining <= TEN_MINUTES,
					"남은 틱이 주기를 벗어났습니다: " + remaining);
		}
	}

	// ------------------------------------------------------------------ 켜진 시점

	/**
	 * <b>경계는 켜진 시점부터 주기마다다.</b> 게임 시간의 배수가 아니다.
	 *
	 * <p>이것이 지켜지지 않으면 세트를 켠 자리에 따라 첫 보급이 몇 초 만에 오기도 하고 꼬박
	 * 10분 뒤에 오기도 한다.
	 */
	@Test
	void 켜진_시점부터_한_주기가_남는다() {
		// 어중간한 자리에서 켰다. 주기의 배수와는 아무 상관이 없는 값이다.
		long anchor = TEN_MINUTES * 3L + 7777;

		assertEquals(TEN_MINUTES, SupplyCountdown.remainingTicks(anchor, TEN_MINUTES, anchor));
		assertEquals(1, SupplyCountdown.remainingTicks(anchor + TEN_MINUTES - 1, TEN_MINUTES, anchor));
		assertEquals(TEN_MINUTES,
				SupplyCountdown.remainingTicks(anchor + TEN_MINUTES, TEN_MINUTES, anchor));
		assertEquals("10:00", SupplyCountdown.text(anchor, TEN_MINUTES, anchor));
		assertEquals("00:01", SupplyCountdown.text(anchor + TEN_MINUTES - 1, TEN_MINUTES, anchor));
	}

	/**
	 * <b>주기가 5분으로 줄어도 시계가 되감기지 않는다.</b>
	 *
	 * <p>4단계가 켜지는 순간이다. 켜진 시점은 그대로 두고 주기만 바꾸므로 이미 지난 시간은
	 * 그대로 지난 것으로 남는다. 켜진 시점을 그때 다시 잡으면 단계를 올린 벌로 시계가 5분으로
	 * 되감긴다.
	 */
	@Test
	void 주기가_줄어도_시계가_되감기지_않는다() {
		long anchor = 5000L;

		// 켠 지 2분. 10분 주기라면 8분 남았다.
		long time = anchor + 2 * MINUTE;
		assertEquals(8 * MINUTE, SupplyCountdown.remainingTicks(time, TEN_MINUTES, anchor));
		// 여기서 4단계가 켜진다. 지난 2분은 그대로 지난 것이라 5분 주기에서는 3분이 남는다.
		assertEquals(3 * MINUTE, SupplyCountdown.remainingTicks(time, FIVE_MINUTES, anchor));

		// 어느 자리에서 바뀌어도 남은 시간이 늘어나지는 않는다. 늘어나면 「보급이 빨라졌는데
		// 더 기다린다」가 되어 단계를 올린 것이 손해로 읽힌다.
		for (long now = anchor; now < anchor + TEN_MINUTES; now += 37) {
			assertTrue(SupplyCountdown.remainingTicks(now, FIVE_MINUTES, anchor)
							<= SupplyCountdown.remainingTicks(now, TEN_MINUTES, anchor),
					"주기가 줄었는데 남은 시간이 늘었습니다 (틱 " + now + ")");
		}
	}

	/**
	 * 켜진 시점을 안 싣는 옛 서버에서는 예전 모습 그대로다.
	 *
	 * <p>0 이 곧 「모른다」이고, 그러면 경계가 게임 시간의 배수가 된다. 시계가 아예 안 뜨는
	 * 것보다 낫다.
	 */
	@Test
	void 켜진_시점을_모르면_게임_시간의_배수가_경계다() {
		assertEquals(SupplyCountdown.remainingTicks(12345L, TEN_MINUTES),
				SupplyCountdown.remainingTicks(12345L, TEN_MINUTES, 0L));
		assertEquals(TEN_MINUTES, SupplyCountdown.remainingTicks(TEN_MINUTES * 4L, TEN_MINUTES, 0L));
	}

	/**
	 * 클라이언트의 게임 시간이 켜진 시점보다 한 틱 이르러도 주기 안에 들어온다.
	 *
	 * <p>바닐라가 20틱마다 시간을 맞춰 주는 사이에 실제로 일어난다. {@code %} 를 쓰면 여기서
	 * 남은 시간이 주기보다 커져 「10:01」 같은 글자가 뜬다.
	 */
	@Test
	void 켜진_시점보다_이른_시각에서도_주기_안에_들어온다() {
		long anchor = 20000L;
		for (long time = anchor - 100; time < anchor; time++) {
			int remaining = SupplyCountdown.remainingTicks(time, TEN_MINUTES, anchor);
			assertTrue(remaining >= 1 && remaining <= TEN_MINUTES,
					"남은 틱이 주기를 벗어났습니다: " + remaining);
		}
	}

	// ------------------------------------------------------------------ 남은 초

	/**
	 * 초는 <b>올림</b>이다.
	 *
	 * <p>내림을 쓰면 마지막 1초 동안 「00:00」이 멈춰 있어 고장으로 읽힌다.
	 */
	@Test
	void 남은_초는_올림이다() {
		assertEquals(1, SupplyCountdown.remainingSeconds(TEN_MINUTES - 1, TEN_MINUTES));
		assertEquals(1, SupplyCountdown.remainingSeconds(TEN_MINUTES - 20, TEN_MINUTES));
		assertEquals(2, SupplyCountdown.remainingSeconds(TEN_MINUTES - 21, TEN_MINUTES));
		assertEquals(600, SupplyCountdown.remainingSeconds(0L, TEN_MINUTES));
	}

	/** 한 주기를 통째로 훑어도 0 초는 한 번도 나오지 않는다. */
	@Test
	void 한_주기_동안_0초는_없다() {
		for (long time = 0; time < FIVE_MINUTES; time++) {
			assertTrue(SupplyCountdown.remainingSeconds(time, FIVE_MINUTES) >= 1,
					"0 초가 나왔습니다: " + time);
		}
	}

	// ------------------------------------------------------------------ 글자

	/** 한 자리 분은 앞을 0 으로 채운다. 「09:59」 모양이 이 시험이 못박는 것이다. */
	@Test
	void 한_자리_분은_두_자리로_채운다() {
		assertEquals("04:12", SupplyCountdown.format(4 * 60 + 12));
		assertEquals("09:59", SupplyCountdown.format(9 * 60 + 59));
	}

	/** 분이 0 이어도 자리를 비우지 않는다. */
	@Test
	void 분이_0이어도_두_자리다() {
		assertEquals("00:07", SupplyCountdown.format(7));
		assertEquals("00:01", SupplyCountdown.format(1));
	}

	/** 이미 두 자리인 분은 그대로다. 채우기가 멀쩡한 값을 건드리면 안 된다. */
	@Test
	void 두_자리_분은_그대로다() {
		assertEquals("10:00", SupplyCountdown.format(600));
		assertEquals("99:59", SupplyCountdown.format(99 * 60 + 59));
	}

	/**
	 * <b>세 자리 분은 잘리지 않는다.</b>
	 *
	 * <p>주기의 상한이 240분이라({@code SupplyDropEffect.MAX_INTERVAL_MINUTES}) 「240:00」까지
	 * 나올 수 있다. 채우기를 자릿수 맞추기로 잘못 고치면 여기서 「40:00」이 되어, 남은 시간이
	 * 갑자기 200분 줄어든 것처럼 보인다.
	 */
	@Test
	void 세_자리_분은_안_잘린다() {
		assertEquals("100:00", SupplyCountdown.format(100 * 60));
		assertEquals("240:00", SupplyCountdown.format(240 * 60));
		assertEquals("123:45", SupplyCountdown.format(123 * 60 + 45));
	}

	@Test
	void 음수는_0으로_본다() {
		assertEquals("00:00", SupplyCountdown.format(-1));
	}

	/**
	 * <b>한 주기를 통째로 훑어도 글자 수가 변하지 않는다.</b>
	 *
	 * <p>이것이 분을 채우는 까닭 그 자체다. 구분선의 길이는 {@code PerkSetLines.blockWidth} 가
	 * 가장 긴 줄에 맞춰 매 프레임 다시 재므로, 시계의 글자 수가 한 번이라도 줄면 그 순간 선이
	 * 눈에 띄게 짧아졌다가 다음 주기에 다시 길어진다.
	 *
	 * <p>훑는 범위를 실제로 도는 주기(10분·5분)로 잡았다. 이 둘이 지금
	 * {@code sharedfate-sets-default.json} 에 적힌 전부다. 100분 이상 주기는 한 주기 안에서
	 * 「100:00 → 99:59」를 지나며 글자가 한 번 줄어 이 성질이 성립하지 않는데, 그것은 채우기로
	 * 막을 수 있는 종류가 아니다(분의 자릿수 자체가 바뀐다). 잘라서 막으면 남은 시간이
	 * 틀리므로 잘못 표시하는 쪽보다 선이 한 번 움직이는 쪽을 택했다.
	 */
	@Test
	void 한_주기_동안_글자_수가_변하지_않는다() {
		assertEquals(5, 글자_수가_하나로_모이는지(TEN_MINUTES));
		assertEquals(5, 글자_수가_하나로_모이는지(FIVE_MINUTES));
		// 1분 주기(주기 하한)도 마찬가지다. 남은 시간이 「01:00」~「00:01」 사이라 다섯 자다.
		assertEquals(5, 글자_수가_하나로_모이는지(MINUTE));
	}

	/**
	 * 한 주기를 틱 단위로 훑어 시계 글자의 길이를 모은다. 길이가 하나로 모이면 그 값을
	 * 돌려주고, 도중에 달라지면 시험을 실패시킨다.
	 */
	private static int 글자_수가_하나로_모이는지(int intervalTicks) {
		String first = SupplyCountdown.text(0L, intervalTicks);
		for (long time = 0; time < intervalTicks; time++) {
			String now = SupplyCountdown.text(time, intervalTicks);
			assertEquals(first.length(), now.length(),
					"글자 수가 달라졌습니다: " + first + " → " + now + " (틱 " + time + ")");
		}
		return first.length();
	}

	/** 도는 보급이 없으면 빈 문자열이다. 부르는 쪽은 이 값만 보고 시계를 그릴지 정한다. */
	@Test
	void 주기가_없으면_글자도_없다() {
		assertEquals("", SupplyCountdown.text(12345L, 0));
		assertEquals("", SupplyCountdown.text(12345L, -5));
	}

	@Test
	void 게임_시간과_주기로_바로_글자가_나온다() {
		// 10분 주기에서 5분 48초가 지난 자리. 남은 것은 4분 12초다.
		long time = TEN_MINUTES * 12L + (5 * 60 + 48) * 20L;
		assertEquals("04:12", SupplyCountdown.text(time, TEN_MINUTES));
	}
}
