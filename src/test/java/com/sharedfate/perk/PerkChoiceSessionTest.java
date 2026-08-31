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

	// ------------------------------------------------------------------ 공기량 고정
	//
	// 선택 중엔 무적이라 익사 피해는 안 받지만, 공기 게이지 자체는 얼어 있는 동안에도 계속
	// 줄어든다. 그대로 두면 선택이 끝나는 순간 이미 산소가 0이라 곧바로 익사 피해를 받는다.
	// 세션이 시작될 때 공기량을 기억해 뒀다가 매 틱 그 값으로 되돌려야 한다. 실제로 되돌리는
	// 자리(ServerPlayer.setAirSupply)는 살아 있는 서버가 있어야 확인할 수 있으므로, 여기서는
	// "되돌려야 하는가·얼마로"를 정하는 순수 계산만 본다.

	@Test
	void 기억한_값이_없으면_손대지_않는다() {
		assertNull(PerkChoiceSession.airToRestore(null, 0));
		assertNull(PerkChoiceSession.airToRestore(null, 300));
	}

	@Test
	void 이미_기억한_값과_같으면_손대지_않는다() {
		assertNull(PerkChoiceSession.airToRestore(300, 300));
		assertNull(PerkChoiceSession.airToRestore(0, 0));
	}

	@Test
	void 물속에서_시작했으면_줄어든_만큼_되돌린다() {
		// 선택이 시작될 때 산소가 가득(300)이었는데, 얼어 있는 동안 몇 틱 줄어들었다(280).
		assertEquals(300, PerkChoiceSession.airToRestore(300, 280));
	}

	@Test
	void 시작_시점에_이미_산소가_0이었으면_0으로_묶어_둔다() {
		// 선택창을 열기 직전 이미 익사 직전이었던 경우다. 세션이 공짜로 산소를 채워 주면
		// 안 되므로 원래 상태(0) 그대로 묶어 둔다.
		assertEquals(0, PerkChoiceSession.airToRestore(0, -5));
	}
}
