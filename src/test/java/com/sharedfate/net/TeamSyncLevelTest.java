package com.sharedfate.net;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSyncLevelTest {
	private static final java.util.UUID LEADER = new java.util.UUID(1L, 2L);
	/** 이 시험이 보는 것은 레벨 계산이라 켜고 끄기는 증강만 켠 채로 고정해 둔다. */
	private static final TeamSyncPayload.Options PERKS_ONLY =
			new TeamSyncPayload.Options(true, false, false);
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
		// 마지막 구간 값을 여기 적어 두면 구간이 늘어날 때마다 이 시험이 같이 깨진다.
		// 규칙을 들고 있는 곳에서 그대로 가져온다.
		state.lastPerkMilestone = com.sharedfate.perk.PerkMilestones.MAX;
		state.xpLevel = com.sharedfate.perk.PerkMilestones.MAX + 5;

		assertEquals(0, TeamBroadcaster.nextPerkLevel(state));
	}

	@Test
	void 남은_레벨은_다음_구간에서_현재_레벨을_뺀_값이다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), "우리팀", 12, 15, 20.0F, 0, PERKS_ONLY, LEADER);

		assertEquals(12, payload.xpLevel());
		assertEquals(3, payload.levelsToNextPerk());
	}

	@Test
	void 다음_구간이_없으면_남은_레벨은_음수로_알린다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), "우리팀", 37, 0, 20.0F, 0, PERKS_ONLY, LEADER);

		assertEquals(-1, payload.levelsToNextPerk());
	}

	@Test
	void 현재_레벨이_다음_구간을_이미_넘었으면_0으로_묶는다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), "우리팀", 17, 15, 20.0F, 0, PERKS_ONLY, LEADER);

		assertEquals(0, payload.levelsToNextPerk());
	}

	@Test
	void 음수_레벨은_0으로_맞춘다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), "우리팀", -5, -1, 20.0F, 0, PERKS_ONLY, LEADER);

		assertEquals(0, payload.xpLevel());
		assertEquals(0, payload.nextPerkLevel());
		assertEquals(-1, payload.levelsToNextPerk());
	}

	@Test
	void 리더_판정은_받는_사람의_UUID로_한다() {
		TeamSyncPayload payload =
				new TeamSyncPayload(List.of(), "우리팀", 5, 10, 20.0F, 0, PERKS_ONLY, LEADER);

		assertTrue(payload.isLeader(LEADER));
		assertFalse(payload.isLeader(new java.util.UUID(9L, 9L)));
	}

	@Test
	void 교환_주기가_0이면_꺼진_것이고_음수는_0으로_맞춘다() {
		TeamSyncPayload off =
				new TeamSyncPayload(List.of(), "우리팀", 5, 10, 20.0F, 0, PERKS_ONLY, LEADER);
		TeamSyncPayload on =
				new TeamSyncPayload(List.of(), "우리팀", 5, 10, 20.0F, 7, PERKS_ONLY, LEADER);
		TeamSyncPayload negative =
				new TeamSyncPayload(List.of(), "우리팀", 5, 10, 20.0F, -3, PERKS_ONLY, LEADER);

		assertFalse(off.swapEnabled());
		assertTrue(on.swapEnabled());
		assertEquals(7, on.swapIntervalMinutes());
		assertEquals(0, negative.swapIntervalMinutes());
		assertFalse(negative.swapEnabled());
	}
}
