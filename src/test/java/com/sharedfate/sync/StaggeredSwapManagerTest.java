package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StaggeredSwapManager}의 순수 로직(실행 순서 셔플, 상태 조회)을 본다.
 *
 * <p>실제로 한 명씩 옮기고 간격을 세는 것은 살아 있는 서버·{@code ServerPlayer}가 있어야
 * 확인할 수 있어({@code PositionSwapManagerTest}와 같은 이유) 여기서 다루지 않는다.
 */
class StaggeredSwapManagerTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		StaggeredSwapManager.reset();
	}

	@Test
	void 실행_순서는_0부터_size_1까지_한_번씩만_담는다() {
		Random random = new Random(20260831L);
		for (int size = 2; size <= 5; size++) {
			for (int attempt = 0; attempt < 50; attempt++) {
				List<Integer> order = StaggeredSwapManager.shuffledIndices(size, random);

				assertEquals(size, order.size());
				assertEquals(size, java.util.Set.copyOf(order).size(), "중복 없이 전부 달라야 한다");
				for (int index : order) {
					assertTrue(index >= 0 && index < size);
				}
			}
		}
	}

	@Test
	void 간격_상수는_5초에서_10초_사이다() {
		assertEquals(100, StaggeredSwapManager.MIN_GAP_TICKS, "5초 = 100틱");
		assertEquals(200, StaggeredSwapManager.MAX_GAP_TICKS, "10초 = 200틱");
	}

	@Test
	void 진행_중인_시퀀스가_없으면_거짓이다() {
		UUID teamId = UUID.randomUUID();

		assertFalse(StaggeredSwapManager.hasActiveSequence(teamId));
	}

	@Test
	void forget_과_reset_은_없는_팀에도_안전하다() {
		UUID teamId = UUID.randomUUID();

		org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> StaggeredSwapManager.forget(teamId));
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(StaggeredSwapManager::reset);
	}

	@Test
	void 인원이_한_명뿐이면_시퀀스를_시작하지_않는다() {
		// beginSequence 는 ServerPlayer 가 있어야 온전히 부를 수 있지만, 인원 미달 가드는
		// 그 전에 걸리므로 빈 목록으로도 확인할 수 있다.
		var team = new com.sharedfate.team.ShareTeam(UUID.randomUUID(), "혼자", List.of(UUID.randomUUID()));
		var state = com.sharedfate.team.TeamState.fresh(20.0F);

		StaggeredSwapManager.beginSequence(team, state, List.of(), new Random(1L), List.of());

		assertFalse(StaggeredSwapManager.hasActiveSequence(team.teamId()));
	}
}
