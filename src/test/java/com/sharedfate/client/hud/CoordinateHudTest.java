package com.sharedfate.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 왼쪽 위에 적는 좌표 한 줄.
 *
 * <p>그리는 부분은 살아 있는 클라이언트가 있어야 하지만, <b>실수 좌표를 어떤 글자로 바꾸는가</b>
 * 는 순수 함수라 여기서 확인할 수 있다. 이 시험이 지키는 것은 하나다 — 음수 쪽에서 한 칸씩
 * 어긋나지 않는 것. 좌표를 불러 주고 찾아가는 놀이에서 그 한 칸이 제일 나쁜 오류다.
 */
class CoordinateHudTest {

	@Test
	void 좌표는_정수로_내림해서_적는다() {
		assertEquals("X 128  Y 64  Z -302",
				CoordinateHud.positionLine(128.62, 64.0, -302.0));
	}

	/**
	 * 음수는 자르지 말고 내려야 한다.
	 *
	 * <p>{@code (int)} 로 자르면 x 가 -0.5 일 때 0 이 나오는데 실제로 서 있는 블록은 -1 이다.
	 * 이 어긋남은 원점 근처와 음수 쪽 전체에서 생긴다.
	 */
	@Test
	void 음수_좌표는_자르지_않고_내린다() {
		assertEquals("X -1  Y 70  Z -1", CoordinateHud.positionLine(-0.5, 70.9, -0.01));
		assertEquals("X -303  Y -60  Z -1",
				CoordinateHud.positionLine(-302.4, -59.2, -0.9));
	}

	@Test
	void 원점은_영으로_적는다() {
		assertEquals("X 0  Y 0  Z 0", CoordinateHud.positionLine(0.0, 0.0, 0.0));
	}

	/** F3 처럼 소수점을 늘어놓지 않는다. */
	@Test
	void 소수점은_적지_않는다() {
		assertEquals("X 1  Y 2  Z 3", CoordinateHud.positionLine(1.99999, 2.5, 3.000001));
	}
}
