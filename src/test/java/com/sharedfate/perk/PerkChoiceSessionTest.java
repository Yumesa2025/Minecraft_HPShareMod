package com.sharedfate.perk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 강제 선택 세션에서 서버 없이 확인할 수 있는 부분.
 *
 * <p>얼리기·녹이기는 살아 있는 {@code MinecraftServer} 가 필요해 여기서 다루지 못한다.
 * 대신 "세션이 없으면 어떤 일이 있어도 아무 영향이 없다"는 기본 성질과, 서버·팀이 없을 때
 * 절대 세션을 열지 않는다는 방어선을 확인한다. 이 두 가지가 무너지면 평상시 피해 처리가
 * 바뀌거나 아무도 없는 서버가 얼어붙는다.
 */
class PerkChoiceSessionTest {
	@BeforeEach
	@AfterEach
	void clearSession() {
		PerkChoiceSession.reset();
	}

	@Test
	void 기본_제한시간은_60초다() {
		assertEquals(1200, PerkChoiceSession.TIMEOUT_TICKS);
		assertEquals(1200, PerkChoiceSession.timeoutTicksForTesting());
	}

	@Test
	void 세션이_없으면_아무도_무적이_아니다() {
		assertFalse(PerkChoiceSession.isActive());
		assertFalse(PerkChoiceSession.blocksDamage(null));
	}

	@Test
	void 세션이_없으면_조회값은_전부_비어_있다() {
		assertNull(PerkChoiceSession.activeTeamId());
		assertEquals(0, PerkChoiceSession.activeMilestone());
		assertEquals(0, PerkChoiceSession.remainingTicks());
	}

	@Test
	void 서버가_없으면_세션을_열지_않는다() {
		assertFalse(PerkChoiceSession.begin(null, null, null));
		assertFalse(PerkChoiceSession.isActive());
	}

	@Test
	void 서버가_없으면_틱과_종료가_조용히_넘어간다() {
		// 월드 초기화 직후처럼 서버가 없는 순간에 불려도 예외가 나면 안 된다.
		PerkChoiceSession.tick(null);
		PerkChoiceSession.onServerStarted(null);
		PerkChoiceSession.onServerStopping(null);

		assertFalse(PerkChoiceSession.isActive());
	}

	@Test
	void reset_은_제한시간을_기본값으로_되돌린다() {
		PerkChoiceSession.setTimeoutTicksForTesting(40);
		assertEquals(40, PerkChoiceSession.timeoutTicksForTesting());

		PerkChoiceSession.reset();

		assertEquals(PerkChoiceSession.TIMEOUT_TICKS, PerkChoiceSession.timeoutTicksForTesting());
	}
}
