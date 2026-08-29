package com.sharedfate.net;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamSyncLevelTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 증강을_쓰지_않는_팀은_다음_구간이_없다() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = false;
		state.xpLevel = 12;

		assertEquals(0, TeamBroadcaster.nextPerkLevel(state));
	}

	@Test
	void 아직_아무_구간도_지나지_않았으면_첫_구간이_목표다() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.xpLevel = 3;

		assertEquals(5, TeamBroadcaster.nextPerkLevel(state));
	}

	@Test
	void 이미_받은_구간_다음_구간을_목표로_삼는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.lastPerkMilestone = 10;
		// 인챈트로 경험치를 써서 레벨이 내려가도 이미 받은 10 구간이 다시 목표가 되지는 않는다.
		state.xpLevel = 8;

		assertEquals(15, TeamBroadcaster.nextPerkLevel(state));
	}

	@Test
	void 마지막_구간까지_받았으면_다음_구간이_없다() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.lastPerkMilestone = 35;
		state.xpLevel = 40;

		assertEquals(0, TeamBroadcaster.nextPerkLevel(state));
	}

	@Test
	void 남은_레벨은_다음_구간에서_현재_레벨을_뺀_값이다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), 12, 15);

		assertEquals(12, payload.xpLevel());
		assertEquals(3, payload.levelsToNextPerk());
	}

	@Test
	void 다음_구간이_없으면_남은_레벨은_음수로_알린다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), 37, 0);

		assertEquals(-1, payload.levelsToNextPerk());
	}

	@Test
	void 현재_레벨이_다음_구간을_이미_넘었으면_0으로_묶는다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), 17, 15);

		assertEquals(0, payload.levelsToNextPerk());
	}

	@Test
	void 음수_레벨은_0으로_맞춘다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), -5, -1);

		assertEquals(0, payload.xpLevel());
		assertEquals(0, payload.nextPerkLevel());
		assertEquals(-1, payload.levelsToNextPerk());
	}
}
