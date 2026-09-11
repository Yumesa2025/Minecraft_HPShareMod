package com.sharedfate.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 화면 왼쪽 위의 「위치 교환까지」 한 줄.
 *
 * <p>골드 「폭발 교환」의 혜택이다. 서버가 1초에 한 번 숫자를 보내 주고, 이쪽은 그것을 그대로
 * 보여 준다 — 스스로 세면 서버에서 멈춰 있는 주기가 화면에서만 흘러 시계가 거짓말을 한다.
 */
class ClientSwapTimerTest {

	@AfterEach
	void reset() {
		ClientSwapTimer.clear();
	}

	@Test
	void 받은_값을_그대로_보여_준다() {
		ClientSwapTimer.set(45, 1000L);

		assertEquals("위치 교환까지 45초", ClientSwapTimer.line(1000L));
	}

	@Test
	void 아무것도_안_받았으면_그리지_않는다() {
		assertNull(ClientSwapTimer.line(1000L));
	}

	/**
	 * 「그만 그려라」를 따로 받지 않는다.
	 *
	 * <p>증강을 잃거나 팀을 나가면 묶음이 그냥 멎으므로, 새 값이 한동안 없으면 스스로 지운다.
	 * 그렇지 않으면 멎은 숫자가 화면에 영영 남는다.
	 */
	@Test
	void 새_값이_한동안_없으면_스스로_사라진다() {
		ClientSwapTimer.set(45, 1000L);

		assertEquals("위치 교환까지 45초", ClientSwapTimer.line(1060L), "3초까지는 아직 살아 있다");
		assertNull(ClientSwapTimer.line(1061L), "3초를 넘기면 끊긴 것으로 본다");
	}

	@Test
	void 새_값이_오면_시계가_다시_산다() {
		ClientSwapTimer.set(45, 1000L);
		ClientSwapTimer.set(44, 1020L);

		assertEquals("위치 교환까지 44초", ClientSwapTimer.line(1070L));
	}

	/** 1분이 넘으면 분·초로 적는다. 「187초」는 한눈에 안 읽힌다. */
	@Test
	void 일분이_넘으면_분과_초로_적는다() {
		assertEquals("0초", ClientSwapTimer.clock(0));
		assertEquals("59초", ClientSwapTimer.clock(59));
		assertEquals("1:00", ClientSwapTimer.clock(60));
		assertEquals("3:07", ClientSwapTimer.clock(187));
		assertEquals("30:00", ClientSwapTimer.clock(1800));
	}

	@Test
	void 음수는_영으로_접는다() {
		assertEquals("0초", ClientSwapTimer.clock(-5));

		ClientSwapTimer.set(-5, 1000L);
		assertEquals("위치 교환까지 0초", ClientSwapTimer.line(1000L));
	}
}
