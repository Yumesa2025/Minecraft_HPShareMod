package com.sharedfate.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 클라이언트가 알려 준 판을 <b>로그에 적어도 안전한 모양</b>으로 다듬는가.
 *
 * <p>이 값은 밖에서 온 문자열이다. 그대로 적으면 줄바꿈 하나로 로그 한 줄이 여러 줄이 되어
 * <b>없던 줄을 지어낸 것처럼</b> 보인다. 서버 로그는 무슨 일이 있었는지 되짚는 유일한 기록이라,
 * 거기에 남이 쓴 줄이 섞이면 기록 전체를 믿을 수 없게 된다.
 *
 * <p>길이는 코덱이 {@link ClientVersionPayload#MAX_LENGTH} 로 이미 자른다.
 */
class ClientVersionLogTest {

	@Test
	void 평범한_판은_그대로_적는다() {
		assertEquals("0.25.4-dev", SharedFateNetworking.sanitizeVersion("0.25.4-dev"));
	}

	@Test
	void 줄바꿈으로_로그를_쪼갤_수_없다() {
		String forged = SharedFateNetworking.sanitizeVersion(
				"0.25.4-dev\n[CLIENT] 관리자 — sharedfate 9.9.9");

		assertTrue(forged.indexOf('\n') < 0, "줄바꿈이 남으면 로그에 가짜 줄을 심을 수 있다");
		assertTrue(forged.indexOf('\r') < 0);
	}

	@Test
	void 보이지_않는_글자는_물음표로_바꾼다() {
		// (char) 7 은 터미널에서 소리만 내고 아무것도 그리지 않는다. 판 이름에 있을 이유가 없다.
		assertEquals("0.25.?4", SharedFateNetworking.sanitizeVersion("0.25." + (char) 7 + "4"));
		assertEquals("0.25.4-dev??", SharedFateNetworking.sanitizeVersion("0.25.4-dev\r\n"));
	}

	@Test
	void 비어_있으면_알_수_없음으로_적는다() {
		assertEquals("(알 수 없음)", SharedFateNetworking.sanitizeVersion(""));
		assertEquals("(알 수 없음)", SharedFateNetworking.sanitizeVersion("   "));
		assertEquals("(알 수 없음)", SharedFateNetworking.sanitizeVersion(null));
	}

	/** 한글이나 이모지가 섞여도 지우지 않는다. 판 이름을 마음대로 짓는 사람이 있을 수 있다. */
	@Test
	void 보이는_글자는_지우지_않는다() {
		assertEquals("0.25.4-개발판", SharedFateNetworking.sanitizeVersion("0.25.4-개발판"));
	}
}
