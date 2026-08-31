package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「폭발 교환」의 지연 폭발 예약({@link SwapExplosionScheduler})에서 서버·{@code ServerLevel}
 * 없이 확인할 수 있는 부분.
 *
 * <p>{@link SwapExplosionScheduler#schedule}은 {@link PositionSwapManager.Position}을 받는데,
 * 그 레코드를 만들려면 살아 있는 {@code ServerLevel}이 있어야 한다. 그래서 실제로 폭발이
 * 예약되고 터지는 것은 여기서 다루지 못한다({@code PositionSwapManagerTest}와 같은 이유).
 * 대신 빈 상태에서의 기본 성질(지연 상수, 빈 상태에서의 {@code reset}·{@code tick}의 안전성)만
 * 확인한다.
 */
class SwapExplosionSchedulerTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		SwapExplosionScheduler.reset();
	}

	@Test
	void 지연은_0점5초다() {
		assertEquals(10, SwapExplosionScheduler.DELAY_TICKS, "0.5초 = 10틱");
	}

	@Test
	void 예약이_없으면_대기_개수는_0이다() {
		assertEquals(0, SwapExplosionScheduler.pendingCount());
	}

	@Test
	void reset_은_비어있어도_안전하다() {
		assertDoesNotThrow(SwapExplosionScheduler::reset);
		assertEquals(0, SwapExplosionScheduler.pendingCount());
	}

	@Test
	void 서버가_없어도_예외없이_넘어간다() {
		assertDoesNotThrow(() -> SwapExplosionScheduler.tick(null));
		assertEquals(0, SwapExplosionScheduler.pendingCount());
	}
}
