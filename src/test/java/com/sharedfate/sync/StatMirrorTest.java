package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatMirrorTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 여러_팀원의_델타를_공유_풀에_합산하고_범위를_제한한다() {
		TeamState state = TeamState.fresh(40.0F);
		state.health = 35.0F;
		state.foodLevel = 18;
		state.saturation = 4.0F;
		state.totalExperience = 100;

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(-12.0F, 0.0F, 0.0F, 0.0F, 5, 30.0F, -150), true);

		assertEquals(23.0F, state.health);
		assertEquals(20, state.foodLevel);
		assertEquals(20.0F, state.saturation);
		assertEquals(0, state.totalExperience);
	}

	@Test
	void 체력은_0과_설정_최대치_사이로_제한한다() {
		TeamState state = TeamState.fresh(40.0F);

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(0.0F, 20.0F, 0.0F, 0.0F, 0, 0.0F, 0), true);
		assertEquals(40.0F, state.health);

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(-100.0F, 0.0F, 0.0F, 0.0F, 0, 0.0F, 0), true);
		assertEquals(0.0F, state.health);
	}

	@Test
	void 경험치_공유를_끄면_경험치_델타를_무시한다() {
		TeamState state = TeamState.fresh(40.0F);
		state.totalExperience = 20;

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0F, 100), false);

		assertEquals(20, state.totalExperience);
	}

	@Test
	void 개인_경험치를_공유_풀에_합칠_때_오버플로하지_않는다() {
		TeamState state = TeamState.fresh(40.0F);
		state.totalExperience = Integer.MAX_VALUE - 2;

		StatMirror.addSharedExperience(state, 10);

		assertEquals(Integer.MAX_VALUE, state.totalExperience);
	}

	@Test
	void 인벤토리_유지_사망은_공유_경험치를_보존한다() {
		TeamState state = TeamState.fresh(40.0F);
		state.health = 0.0F;
		state.foodLevel = 2;
		state.saturation = 0.0F;
		state.xpLevel = 7;
		state.xpProgress = 0.5F;
		state.totalExperience = 100;

		state.resetAfterDeath(40.0F, true);

		assertEquals(40.0F, state.health);
		assertEquals(20, state.foodLevel);
		assertEquals(5.0F, state.saturation);
		assertEquals(7, state.xpLevel);
		assertEquals(0.5F, state.xpProgress);
		assertEquals(100, state.totalExperience);
	}

	@Test
	void 현재_경험치는_레벨과_진행도에서_환산한다() {
		assertEquals(0, StatMirror.experiencePointsFor(0, 0.0F));
		assertEquals(315, StatMirror.experiencePointsFor(15, 0.0F));
		assertEquals(352, StatMirror.experiencePointsFor(16, 0.0F));
		assertEquals(1395, StatMirror.experiencePointsFor(30, 0.0F));
		assertEquals(1507, StatMirror.experiencePointsFor(31, 0.0F));
		assertEquals(373, StatMirror.experiencePointsFor(16, 0.5F));
	}

	@Test
	void 흡수_보호막은_중복_부여하지_않고_동시_소비는_합산한다() {
		TeamState state = TeamState.fresh(40.0F);
		StatMirror.AbsorptionDelta combined = new StatMirror.AbsorptionDelta(0.0F, 0.0F);
		combined = StatMirror.mergeAbsorptionDelta(combined, 4.0F);
		combined = StatMirror.mergeAbsorptionDelta(combined, 4.0F);
		combined = StatMirror.mergeAbsorptionDelta(combined, -2.0F);
		combined = StatMirror.mergeAbsorptionDelta(combined, -2.0F);

		assertEquals(4.0F, combined.gain());
		assertEquals(-4.0F, combined.loss());

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(
						0.0F, 0.0F, 0.0F, combined.gain(), 0, 0.0F, 0), true);
		assertEquals(4.0F, state.absorption);

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(
						0.0F, 0.0F, combined.loss(), 0.0F, 0, 0.0F, 0), true);
		assertEquals(0.0F, state.absorption);
	}

	@Test
	void 동시에_공유_흡수량보다_많이_소비하면_초과분은_체력에서_차감한다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 2.0F;

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(-4.0F, 0.0F, -4.0F, 0.0F, 0, 0.0F, 0), true);

		assertEquals(0.0F, state.absorption);
		assertEquals(34.0F, state.health);
	}

	@Test
	void 로컬_보호막이_없는_팀원의_체력_피해도_공유_흡수에서_먼저_차감한다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 4.0F;

		StatMirror.applyDeltas(state, 40.0F, 4.0F,
				new StatMirror.StatDelta(-2.0F, 0.0F, 0.0F, 0.0F, 0, 0.0F, 0), true);

		assertEquals(2.0F, state.absorption);
		assertEquals(40.0F, state.health);
	}

	@Test
	void 흡수_효과_만료로_최대치가_사라지면_일반_체력_피해로_보지_않는다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 4.0F;

		StatMirror.applyDeltas(state, 40.0F, 0.0F,
				new StatMirror.StatDelta(0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0F, 0), true);

		assertEquals(0.0F, state.absorption);
		assertEquals(40.0F, state.health);
	}

	@Test
	void 흡수_최대치_감소와_실제_소비를_분리한다() {
		float firstConsumption = StatMirror.consumedAbsorption(8.0F, 2.0F, 4.0F);
		assertEquals(2.0F, firstConsumption);
		TeamState first = TeamState.fresh(40.0F);
		first.absorption = 8.0F;
		StatMirror.applyDeltas(first, 40.0F, 4.0F,
				new StatMirror.StatDelta(0.0F, 0.0F, -firstConsumption, 0.0F, 0, 0.0F, 0), true);
		assertEquals(2.0F, first.absorption);
		assertEquals(40.0F, first.health);

		float secondConsumption = StatMirror.consumedAbsorption(8.0F, 0.0F, 4.0F);
		assertEquals(4.0F, secondConsumption);
		TeamState second = TeamState.fresh(40.0F);
		second.absorption = 8.0F;
		StatMirror.applyDeltas(second, 40.0F, 4.0F,
				new StatMirror.StatDelta(-2.0F, 0.0F, -secondConsumption, 0.0F, 0, 0.0F, 0), true);
		assertEquals(0.0F, second.absorption);
		assertEquals(38.0F, second.health);
	}

	@Test
	void 흡수_효과의_자연_만료는_피해_소비가_아니다() {
		assertEquals(0.0F, StatMirror.consumedAbsorption(4.0F, 0.0F, 0.0F));
	}

	@Test
	void 서로_다른_원인의_동시_피해는_그대로_합산한다() {
		// 아라는 좀비에게 3, 보라는 스켈레톤에게 4. 팀이 진짜로 두 번 맞았으니 둘 다 센다.
		StatMirror.StatDelta folded = StatMirror.fold(java.util.List.of(
				new StatMirror.PlayerDelta(-3.0F, 0.0F, 0.0F, 0, 0.0F, 0L),
				new StatMirror.PlayerDelta(-4.0F, 0.0F, 0.0F, 0, 0.0F, 0L)));

		assertEquals(-7.0F, folded.healthLoss());
		assertEquals(0.0F, folded.healthGain());
	}

	@Test
	void 접을_때_체력_손실과_회복을_따로_담는다() {
		// 한 명은 맞고 한 명은 재생으로 회복한 틱. 상쇄하지 않고 각각 모은다.
		StatMirror.StatDelta folded = StatMirror.fold(java.util.List.of(
				new StatMirror.PlayerDelta(-5.0F, 0.0F, 0.0F, -1, -0.5F, 3L),
				new StatMirror.PlayerDelta(2.0F, 0.0F, 0.0F, 0, 0.0F, 4L)));

		assertEquals(-5.0F, folded.healthLoss());
		assertEquals(2.0F, folded.healthGain());
		assertEquals(-1, folded.foodLevel());
		assertEquals(-0.5F, folded.saturation());
		assertEquals(7L, folded.totalExperience());
	}

	@Test
	void 접을_때_흡수_획득은_최댓값_소비는_합산으로_모은다() {
		StatMirror.StatDelta folded = StatMirror.fold(java.util.List.of(
				new StatMirror.PlayerDelta(0.0F, 4.0F, 0.0F, 0, 0.0F, 0L),
				new StatMirror.PlayerDelta(0.0F, 4.0F, 0.0F, 0, 0.0F, 0L),
				new StatMirror.PlayerDelta(0.0F, -2.0F, 2.0F, 0, 0.0F, 0L),
				new StatMirror.PlayerDelta(0.0F, -2.0F, 2.0F, 0, 0.0F, 0L)));

		assertEquals(4.0F, folded.absorptionGain());
		assertEquals(-4.0F, folded.absorptionLoss());
	}
}
