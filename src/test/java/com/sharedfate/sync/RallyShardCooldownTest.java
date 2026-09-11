package com.sharedfate.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「소집의 조각」의 쿨타임은 사람이 아니라 <b>팀</b>이 쓴다.
 *
 * <p>조각이 팀 공유 인벤토리에 들어 있어 누구나 집어 쓸 수 있으므로, 사람마다 따로 돌면 네
 * 명이 번갈아 눌러 4분 쿨타임을 1분으로 만들 수 있다.
 */
class RallyShardCooldownTest {
	private static final UUID TEAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTHER_TEAM = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@AfterEach
	void reset() {
		RallyShardCooldown.clearForTesting();
	}

	@Test
	void 쿨타임을_걸면_그_팀만_막힌다() {
		RallyShardCooldown.beginForTesting(TEAM, 40);

		assertTrue(RallyShardCooldown.onCooldown(TEAM));
		assertFalse(RallyShardCooldown.onCooldown(OTHER_TEAM), "다른 팀은 자기 시계를 쓴다");
		assertEquals(40, RallyShardCooldown.remainingTicks(TEAM));
	}

	@Test
	void 틱마다_하나씩_줄어들고_다_되면_사라진다() {
		RallyShardCooldown.beginForTesting(TEAM, 3);

		RallyShardCooldown.tick(null);
		assertEquals(2, RallyShardCooldown.remainingTicks(TEAM));
		RallyShardCooldown.tick(null);
		assertEquals(1, RallyShardCooldown.remainingTicks(TEAM));
		RallyShardCooldown.tick(null);

		assertEquals(0, RallyShardCooldown.remainingTicks(TEAM));
		assertFalse(RallyShardCooldown.onCooldown(TEAM));
	}

	/** 다 된 뒤에도 계속 틱이 돌지만 음수로 내려가지 않는다. */
	@Test
	void 다_된_뒤에는_아무_일도_없다() {
		RallyShardCooldown.beginForTesting(TEAM, 1);
		for (int tick = 0; tick < 100; tick++) {
			RallyShardCooldown.tick(null);
		}

		assertEquals(0, RallyShardCooldown.remainingTicks(TEAM));
	}

	/** 전멸은 회차의 끝이다. 다음 회차에 「분명 안 썼는데 못 쓴다」가 되면 안 된다. */
	@Test
	void 팀이_전멸하면_쿨타임도_사라진다() {
		RallyShardCooldown.beginForTesting(TEAM, 4000);

		RallyShardCooldown.forget(TEAM);

		assertFalse(RallyShardCooldown.onCooldown(TEAM));
	}

	@Test
	void 팀이_없으면_조용히_비어_있다() {
		assertFalse(RallyShardCooldown.onCooldown(null));
		assertEquals(0, RallyShardCooldown.remainingTicks(null));
		RallyShardCooldown.forget(null);
		RallyShardCooldown.tick(null);
	}
}
