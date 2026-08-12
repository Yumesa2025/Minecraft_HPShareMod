package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionSwapManagerTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 두명부터_네명까지_자기위치_없이_모든_위치를_한번씩_배정한다() {
		Random random = new Random(20260810L);
		for (int size = 2; size <= 4; size++) {
			for (int attempt = 0; attempt < 100; attempt++) {
				int[] donors = PositionSwapManager.derangedDonors(size, random);
				boolean[] used = new boolean[size];
				for (int receiver = 0; receiver < size; receiver++) {
					assertTrue(donors[receiver] >= 0 && donors[receiver] < size);
					assertFalse(used[donors[receiver]], "같은 출발 위치가 두 번 배정되면 안 된다");
					assertFalse(receiver == donors[receiver], "자기 위치를 다시 받으면 안 된다");
					used[donors[receiver]] = true;
				}
			}
		}
	}

	@Test
	void 한주기가_끝나야_교환하고_다음주기를_다시_센다() {
		TeamState state = TeamState.fresh(20.0F);
		state.enablePositionSwap(1);

		for (int tick = 1; tick < TeamState.PositionSwapLimits.TICKS_PER_MINUTE; tick++) {
			assertFalse(state.advancePositionSwapTick(true));
		}
		assertTrue(state.advancePositionSwapTick(true));
		assertEquals(TeamState.PositionSwapLimits.TICKS_PER_MINUTE,
				state.positionSwapRemainingTicks);
	}

	@Test
	void 팀원이_한명뿐이면_교환하지_않고_일초뒤_재시도한다() {
		TeamState state = TeamState.fresh(20.0F);
		state.enablePositionSwap(1);
		state.positionSwapRemainingTicks = 1;

		assertFalse(state.advancePositionSwapTick(false));
		assertEquals(TeamState.PositionSwapLimits.RETRY_TICKS, state.positionSwapRemainingTicks);
	}

	@Test
	void 명령범위_밖의_주기는_거부하고_끄면_카운트다운도_지운다() {
		TeamState state = TeamState.fresh(20.0F);

		assertThrows(IllegalArgumentException.class, () -> state.enablePositionSwap(0));
		assertThrows(IllegalArgumentException.class, () -> state.enablePositionSwap(121));
		state.enablePositionSwap(5);
		state.disablePositionSwap();

		assertFalse(state.positionSwapEnabled());
		assertEquals(0, state.positionSwapRemainingTicks);
	}
}
