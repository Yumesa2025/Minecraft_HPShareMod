package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.ShareTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link RallyPointManager}의 순수 로직(상태 조회, 인원 가드)을 본다.
 *
 * <p>실제로 모으고 15초 뒤 되돌리는 것은 살아 있는 서버·{@code ServerPlayer}가 있어야
 * 확인할 수 있어({@code PositionSwapManagerTest}와 같은 이유) 여기서 다루지 않는다.
 * 특히 <b>복귀 안전성 검사는 의도적으로 없다</b> — 확정된 설계이므로 이 클래스에서 그런
 * 검사를 시험하지 않는다(애초에 존재하지 않는다).
 */
class RallyPointManagerTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		RallyPointManager.reset();
	}

	@Test
	void 복귀_대기_시간은_15초다() {
		assertEquals(300, RallyPointManager.RETURN_DELAY_TICKS, "15초 = 300틱");
	}

	@Test
	void 대기_중인_복귀가_없으면_거짓이다() {
		assertFalse(RallyPointManager.hasPendingReturn(UUID.randomUUID()));
	}

	@Test
	void forget_과_reset_은_없는_팀에도_안전하다() {
		UUID teamId = UUID.randomUUID();

		assertDoesNotThrow(() -> RallyPointManager.forget(teamId));
		assertDoesNotThrow(RallyPointManager::reset);
	}

	@Test
	void 인원이_한_명뿐이면_모으지_않는다() {
		ShareTeam team = new ShareTeam(UUID.randomUUID(), "혼자", List.of(UUID.randomUUID()));

		RallyPointManager.beginGather(team, List.of(), new Random(1L), List.of());

		assertFalse(RallyPointManager.hasPendingReturn(team.teamId()));
	}
}
