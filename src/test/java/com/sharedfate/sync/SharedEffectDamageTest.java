package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 공유 상태이상의 중복 피해를 걸러 내는 판정과, 그 결과가 공유 체력·피해 기록에 어떻게
 * 반영되는지를 본다.
 *
 * <p>실제 게임에서는 {@code MobEffectInstanceSharedTickMixin} 이 상태이상 틱 구간을 열고
 * {@code LivingEntityPerkDamageMixin} 이 {@code hurtServer} 에서 중복 피해를 버린다. 여기서는
 * 월드 없이 그 흐름만 그대로 흉내 낸다. 각 팀원에 대해 판정을 물어보고, 막힌 팀원은 체력이
 * 줄지 않은 것으로 두고 {@link StatMirror#fold} 에 넣는다.
 */
class SharedEffectDamageTest {
	private static final UUID TEAM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
	private static final UUID ARA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
	private static final UUID BORA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
	private static final UUID CHAE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
	private static final UUID DAON = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");
	private static final List<UUID> MEMBERS = List.of(ARA, BORA, CHAE, DAON);

	/** 독 1등급이 한 번 터질 때의 피해량. */
	private static final float POISON_TICK = 1.0F;
	private static final float SHARED_MAX_HEALTH = 40.0F;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void reset() {
		SharedEffectDamage.clearState();
		DamageLedger.clearState();
	}

	@Test
	void 네_명이_같은_공유_독을_맞아도_공유_체력에서는_1인분만_깎인다() {
		List<StatMirror.PlayerDelta> observed = sharedEffectTick(ARA, true, true);

		StatMirror.StatDelta folded = StatMirror.fold(observed);
		assertEquals(-POISON_TICK, folded.healthLoss(), 1.0e-6F,
				"공유 때문에 네 번으로 보이는 독 피해는 한 번만 세야 한다");

		TeamState state = freshTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		// 고치기 전에는 4인분이 깎여 36.0 이었다.
		assertEquals(39.0F, state.health, 1.0e-6F);
	}

	@Test
	void 서로_다른_원인의_동시_피해는_계속_합산한다() {
		// 몹에게 맞은 피해는 상태이상 틱 구간 밖에서 난다. 그래서 억제 판정이 아예 서지 않는다.
		assertFalse(SharedEffectDamage.isDuplicateEffectDamage(true, false, true, false, true),
				"상태이상 틱 밖의 피해는 무엇이든 버리면 안 된다");

		// 같은 틱에 아라는 좀비에게, 보라는 스켈레톤에게 맞았다. 팀은 진짜로 두 번 맞았다.
		StatMirror.StatDelta folded = StatMirror.fold(List.of(
				healthOnly(-3.0F), healthOnly(-4.0F), healthOnly(0.0F), healthOnly(0.0F)));
		assertEquals(-7.0F, folded.healthLoss(), 1.0e-6F);

		TeamState state = freshTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		assertEquals(33.0F, state.health, 1.0e-6F);
	}

	@Test
	void 공유_독과_몹_피해가_겹치면_독은_1인분_몹_피해는_그대로_합산한다() {
		List<StatMirror.PlayerDelta> observed = new ArrayList<>(sharedEffectTick(ARA, true, true));
		// 같은 틱에 다온이 크리퍼에게 6 을 맞았다. 원인이 다르니 독과 함께 세야 한다.
		observed.set(MEMBERS.indexOf(DAON), healthOnly(observed.get(MEMBERS.indexOf(DAON)).health() - 6.0F));

		StatMirror.StatDelta folded = StatMirror.fold(observed);
		assertEquals(-7.0F, folded.healthLoss(), 1.0e-6F, "독 1 + 크리퍼 6");

		TeamState state = freshTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		assertEquals(33.0F, state.health, 1.0e-6F);
	}

	@Test
	void 상태이상_공유_설정이_꺼져_있으면_아무_피해도_버리지_않는다() {
		// 설정이 꺼지면 판정이 항상 거짓이다. 즉 관측되는 체력 손실 처리가 이 수정 전과 똑같다.
		assertFalse(SharedEffectDamage.isDuplicateEffectDamage(false, true, true, false, true));

		List<StatMirror.PlayerDelta> observed = sharedEffectTick(ARA, false, true);
		assertEquals(-4.0F, StatMirror.fold(observed).healthLoss(), 1.0e-6F);
	}

	@Test
	void 대표가_그_상태이상을_갖고_있지_않으면_아무도_막지_않는다() {
		// 공짜 피해 면역을 막는 안전장치다. 대표에게 그 상태이상이 없으면 억제도 없다.
		assertFalse(SharedEffectDamage.isDuplicateEffectDamage(true, true, true, false, false));

		List<StatMirror.PlayerDelta> observed = sharedEffectTick(ARA, true, false);
		assertEquals(-4.0F, StatMirror.fold(observed).healthLoss(), 1.0e-6F);
	}

	@Test
	void 대표_본인의_피해는_절대_버리지_않는다() {
		assertFalse(SharedEffectDamage.isDuplicateEffectDamage(true, true, true, true, true));
	}

	@Test
	void 팀에_속하지_않은_대상의_피해는_건드리지_않는다() {
		// 몹이나 무소속 플레이어가 독에 걸린 경우다. 바닐라와 완전히 같아야 한다.
		assertFalse(SharedEffectDamage.isDuplicateEffectDamage(true, true, false, false, true));
	}

	@Test
	void 회차별_피해_기록도_배수로_부풀지_않는다() {
		List<StatMirror.PlayerDelta> observed = sharedEffectTick(ARA, true, true);
		for (int index = 0; index < MEMBERS.size(); index++) {
			// StatMirror.recordDamage 와 같은 규칙이다. 줄어든 체력만큼만 장부에 올린다.
			float loss = Math.max(0.0F, -observed.get(index).health());
			DamageLedger.record(TEAM_ID, "독팀", MEMBERS.get(index), "팀원" + index, 1, loss);
		}

		String book = String.join("\n", DamageLedger.buildPageTexts(
				new ShareTeam(TEAM_ID, "독팀", MEMBERS), 1));

		assertTrue(book.contains("1회차: 1.0 / 0.5♥"), book);
		assertEquals(1, occurrences(book, "1회차: 1.0 / 0.5♥"),
				"독 한 방은 장부에도 한 번만 남아야 한다");
		assertEquals(3, occurrences(book, "1회차: 0.0 / 0.0♥"),
				"막힌 팀원 셋에게는 피해가 기록되지 않아야 한다");
	}

	/**
	 * 팀 전원이 같은 공유 상태이상 틱을 맞은 상황을 흉내 낸다.
	 *
	 * @param representative           {@code StatMirror.damageRepresentative} 가 고른 대표
	 * @param shareStatusEffects       상태이상 공유 설정
	 * @param representativeHasEffect  대표도 같은 상태이상을 갖고 있는가
	 * @return 팀원 순서대로의 관측 변화량. 막힌 팀원은 0 이다
	 */
	private static List<StatMirror.PlayerDelta> sharedEffectTick(UUID representative,
			boolean shareStatusEffects, boolean representativeHasEffect) {
		List<StatMirror.PlayerDelta> deltas = new ArrayList<>(MEMBERS.size());
		for (UUID member : MEMBERS) {
			boolean isRepresentative = member.equals(representative);
			boolean dropped = SharedEffectDamage.isDuplicateEffectDamage(
					shareStatusEffects, true, true, isRepresentative,
					!isRepresentative && representativeHasEffect);
			deltas.add(healthOnly(dropped ? 0.0F : -POISON_TICK));
		}
		return deltas;
	}

	private static StatMirror.PlayerDelta healthOnly(float health) {
		return new StatMirror.PlayerDelta(health, 0.0F, 0.0F, 0, 0.0F, 0L);
	}

	private static TeamState freshTeam() {
		TeamState state = TeamState.fresh(SHARED_MAX_HEALTH);
		state.health = SHARED_MAX_HEALTH;
		return state;
	}

	private static int occurrences(String text, String needle) {
		int count = 0;
		for (int from = text.indexOf(needle); from >= 0; from = text.indexOf(needle, from + needle.length())) {
			count++;
		}
		return count;
	}
}
