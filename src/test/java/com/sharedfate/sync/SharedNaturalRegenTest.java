package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 허기에 의한 자연 회복이 <b>인원수만큼 곱해지지 않는지</b>.
 *
 * <p>{@link StatMirror} 는 팀원 각자의 체력 변화를 재서 공유 풀 하나에 합산한다. 그런데 허기·
 * 포만감도 공유라 팀원 전원이 <b>같은 틱에 똑같이</b> 자연 회복 조건을 만족한다. 그대로 두면
 * 4인 팀의 공유 체력이 한 번에 4인분씩 차오른다.
 *
 * <p>{@link SharedEffectDamage} 가 공유 상태이상에 쓰는 「대표 한 명만 실제로 겪는다」 방식을
 * 자연 회복에도 그대로 적용한다. 다른 점은 <b>상태이상 틱 구간을 보지 않는다</b>는 것뿐이다 —
 * 자연 회복은 {@code FoodData.tick} 안에서 나므로 그 구간 밖이다.
 *
 * <p>여기서는 월드 없이 그 흐름만 흉내 낸다. 팀원마다 판정을 물어보고, 막힌 팀원은 회복이
 * 일어나지 않은 것으로 두고 {@link StatMirror#fold} 에 넣는다.
 */
class SharedNaturalRegenTest {
	private static final UUID ARA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
	private static final UUID BORA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
	private static final UUID CHAE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003");
	private static final UUID DAON = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000004");
	private static final List<UUID> MEMBERS = List.of(ARA, BORA, CHAE, DAON);

	/** 허기가 18 이상일 때 80틱마다 들어오는 자연 회복 한 번. */
	private static final float REGEN_TICK = 1.0F;
	private static final float SHARED_MAX_HEALTH = 20.0F;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 네_명이_동시에_자연_회복해도_공유_체력은_1인분만_찬다() {
		List<StatMirror.PlayerDelta> observed = naturalRegenTick(ARA);

		StatMirror.StatDelta folded = StatMirror.fold(observed);
		assertEquals(REGEN_TICK, folded.healthGain(), 1.0e-6F,
				"공유 때문에 네 번으로 보이는 자연 회복은 한 번만 세야 한다");

		TeamState state = hurtTeam(10.0F);
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		assertEquals(11.0F, state.health, 1.0e-6F);
	}

	/**
	 * 고치기 전에는 이 값이 나왔다. 회귀를 눈에 보이게 못박아 둔다.
	 *
	 * <p>4인 팀이 공유 체력 20 을 쓰는데 한 번에 4 가 차면 다섯 번에 만피가 된다.
	 */
	@Test
	void 막지_않으면_인원수만큼_곱해진다() {
		List<StatMirror.PlayerDelta> unblocked = new ArrayList<>();
		for (int index = 0; index < MEMBERS.size(); index++) {
			unblocked.add(healthOnly(REGEN_TICK));
		}

		assertEquals(4.0F, StatMirror.fold(unblocked).healthGain(), 1.0e-6F,
				"막지 않으면 4인분이 된다 — 이것이 고치려는 증상이다");
	}

	@Test
	void 대표_본인의_회복은_절대_버리지_않는다() {
		assertFalse(SharedNaturalRegen.isDuplicateNaturalRegen(true, true));
	}

	@Test
	void 팀에_속하지_않으면_바닐라와_완전히_같다() {
		assertFalse(SharedNaturalRegen.isDuplicateNaturalRegen(false, false));
		assertFalse(SharedNaturalRegen.isDuplicateNaturalRegen(false, true));
	}

	@Test
	void 대표가_아닌_팀원의_회복만_버린다() {
		assertTrue(SharedNaturalRegen.isDuplicateNaturalRegen(true, false));
	}

	/**
	 * 대표를 못 고르는 상황 — 전원 오프라인·사망 — 에서는 아무도 막지 않는다.
	 *
	 * <p>{@link SharedEffectDamage} 의 안전장치와 같은 규칙이다. 막을 기준이 없는데 막으면 팀이
	 * 회복을 통째로 잃는다.
	 */
	@Test
	void 대표를_고르지_못하면_아무도_막지_않는다() {
		// 대표가 null 이면 진입점이 subjectIsRepresentative 를 참으로 넘긴다.
		for (UUID ignored : MEMBERS) {
			assertFalse(SharedNaturalRegen.isDuplicateNaturalRegen(true, true));
		}
	}

	/**
	 * 팀 전원이 같은 틱에 자연 회복 조건을 만족한 상황을 흉내 낸다.
	 *
	 * @param representative {@code StatMirror.sharedEffectRepresentative} 가 고른 대표
	 * @return 팀원 순서대로의 관측 변화량. 막힌 팀원은 0 이다
	 */
	private static List<StatMirror.PlayerDelta> naturalRegenTick(UUID representative) {
		List<StatMirror.PlayerDelta> deltas = new ArrayList<>(MEMBERS.size());
		for (UUID member : MEMBERS) {
			boolean isRepresentative = member.equals(representative);
			boolean dropped = SharedNaturalRegen.isDuplicateNaturalRegen(true, isRepresentative);
			deltas.add(healthOnly(dropped ? 0.0F : REGEN_TICK));
		}
		return deltas;
	}

	private static StatMirror.PlayerDelta healthOnly(float health) {
		return new StatMirror.PlayerDelta(health, 0.0F, 0.0F, 0, 0.0F, 0L);
	}

	private static TeamState hurtTeam(float health) {
		TeamState state = TeamState.fresh(SHARED_MAX_HEALTH);
		state.health = health;
		return state;
	}
}
