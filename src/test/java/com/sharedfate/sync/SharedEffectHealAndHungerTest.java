package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 공유된 상태이상의 <em>회복</em>과 <em>배고픔</em>이 팀 인원수만큼 배수로 적용되던 문제를 본다.
 *
 * <p>피해 쪽 짝은 {@link SharedEffectDamageTest} 다. 규칙은 셋 다 같은
 * {@code SharedEffectDamage.isDuplicateSharedEffectTick} 이지만, 공유 풀에 닿는 경로가 서로
 * 달라 결과를 따로 확인한다.
 *
 * <p>실제 게임에서는 {@code MobEffectInstanceSharedTickMixin} 이 상태이상 틱 구간을 열고,
 * {@code LivingEntitySharedHealMixin} 이 {@code LivingEntity.heal} 에서 중복 회복을,
 * {@code PlayerSharedExhaustionMixin} 이 {@code Player.causeFoodExhaustion} 에서 중복 허기
 * 소모를 버린다. 여기서는 월드 없이 그 흐름만 흉내 낸다. 각 팀원에 대해 판정을 물어보고,
 * 막힌 팀원은 아무 변화도 없었던 것으로 두고 {@link StatMirror#fold} 에 넣는다.
 */
class SharedEffectHealAndHungerTest {
	private static final UUID ARA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
	private static final UUID BORA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
	private static final UUID CHAE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003");
	private static final UUID DAON = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000004");
	private static final List<UUID> MEMBERS = List.of(ARA, BORA, CHAE, DAON);

	/** 재생 1등급이 한 번 터질 때의 회복량. {@code RegenerationMobEffect} 는 heal(1.0F) 한 번이다. */
	private static final float REGENERATION_TICK = 1.0F;
	/** 허기 효과가 쌓은 소모도가 결국 깎아 내는 배고픔. */
	private static final int HUNGER_FOOD_DROP = -1;

	private static final float SHARED_MAX_HEALTH = 40.0F;
	private static final float WOUNDED_HEALTH = 30.0F;

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
	void 네_명이_같은_공유_재생을_받아도_공유_체력은_1인분만_찬다() {
		StatMirror.StatDelta folded = StatMirror.fold(sharedRegenerationTick(ARA, true, true));
		assertEquals(REGENERATION_TICK, folded.healthGain(), 1.0e-6F,
				"공유 때문에 네 번으로 보이는 재생은 한 번만 세야 한다");

		TeamState state = woundedTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		// 고치기 전에는 4인분이 차서 34.0 이었다.
		assertEquals(31.0F, state.health, 1.0e-6F);
	}

	@Test
	void 네_명이_같은_공유_허기를_받아도_공유_배고픔은_1인분만_준다() {
		StatMirror.StatDelta folded = StatMirror.fold(sharedHungerTick(ARA, true, true));
		assertEquals(HUNGER_FOOD_DROP, folded.foodLevel(),
				"공유 때문에 네 번으로 보이는 허기 소모는 한 번만 세야 한다");

		TeamState state = fedTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		// 고치기 전에는 4인분이 깎여 16 이었다.
		assertEquals(19, state.foodLevel);
	}

	@Test
	void 서로_다른_팀원이_각자_다른_음식을_먹으면_그대로_합산한다() {
		// 음식 섭취는 상태이상 틱 구간 밖에서 난다. 그래서 억제 판정이 아예 서지 않는다.
		assertFalse(SharedEffectDamage.isDuplicateSharedEffectTick(true, false, true, false, true),
				"상태이상 틱 밖의 배고픔 변화는 무엇이든 버리면 안 된다");
		assertFalse(SharedEffectDamage.isDuplicateEffectExhaustion(null),
				"틱 구간 밖에서는 어떤 허기 소모도 막히지 않는다");
		assertFalse(SharedEffectDamage.isDuplicateEffectHeal(null),
				"틱 구간 밖에서는 어떤 회복도 막히지 않는다");

		// 아라는 금사과(4, 9.6), 보라는 빵(5, 6.0)을 먹었다. 원인이 서로 달라 둘 다 세야 한다.
		StatMirror.StatDelta folded = StatMirror.fold(List.of(
				food(4, 9.6F), food(5, 6.0F), food(0, 0.0F), food(0, 0.0F)));
		assertEquals(9, folded.foodLevel(), "금사과 4 + 빵 5");
		assertEquals(15.6F, folded.saturation(), 1.0e-5F);

		TeamState state = hungryTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		assertEquals(15, state.foodLevel, "6 에서 9 만큼 찬다");
	}

	@Test
	void 상태이상_공유_설정이_꺼져_있으면_회복도_허기도_거르지_않는다() {
		assertFalse(SharedEffectDamage.isDuplicateSharedEffectTick(false, true, true, false, true));

		StatMirror.StatDelta regeneration = StatMirror.fold(sharedRegenerationTick(ARA, false, true));
		assertEquals(4.0F * REGENERATION_TICK, regeneration.healthGain(), 1.0e-6F,
				"설정이 꺼지면 이 수정 전과 똑같이 네 번 다 세야 한다");

		StatMirror.StatDelta hunger = StatMirror.fold(sharedHungerTick(ARA, false, true));
		assertEquals(4 * HUNGER_FOOD_DROP, hunger.foodLevel());
	}

	@Test
	void 대표가_그_상태이상을_갖고_있지_않으면_회복도_허기도_막지_않는다() {
		// 공짜 이득도 공짜 손해도 없어야 한다는 안전장치다.
		assertFalse(SharedEffectDamage.isDuplicateSharedEffectTick(true, true, true, false, false));

		assertEquals(4.0F * REGENERATION_TICK,
				StatMirror.fold(sharedRegenerationTick(ARA, true, false)).healthGain(), 1.0e-6F);
		assertEquals(4 * HUNGER_FOOD_DROP,
				StatMirror.fold(sharedHungerTick(ARA, true, false)).foodLevel());
	}

	@Test
	void 대표_본인의_회복과_허기는_절대_버리지_않는다() {
		assertFalse(SharedEffectDamage.isDuplicateSharedEffectTick(true, true, true, true, true));
	}

	@Test
	void 팀에_속하지_않은_대상의_회복과_허기는_건드리지_않는다() {
		assertFalse(SharedEffectDamage.isDuplicateSharedEffectTick(true, true, false, false, true));
	}

	@Test
	void 공유_재생과_몹_피해가_겹치면_회복은_1인분_몹_피해는_그대로_합산한다() {
		List<StatMirror.PlayerDelta> observed =
				new ArrayList<>(sharedRegenerationTick(ARA, true, true));
		// 같은 틱에 다온이 크리퍼에게 6 을 맞았다. 회복과 원인이 다르니 따로 세야 한다.
		int daon = MEMBERS.indexOf(DAON);
		observed.set(daon, health(observed.get(daon).health() - 6.0F));

		StatMirror.StatDelta folded = StatMirror.fold(observed);
		assertEquals(REGENERATION_TICK, folded.healthGain(), 1.0e-6F);
		assertEquals(-6.0F, folded.healthLoss(), 1.0e-6F);

		TeamState state = woundedTeam();
		StatMirror.applyDeltas(state, SHARED_MAX_HEALTH, 0.0F, folded, true);
		assertEquals(25.0F, state.health, 1.0e-6F, "30 + 재생 1 - 크리퍼 6");
	}

	/**
	 * 회복을 막는 자리가 정말 하나뿐인지 확인한다.
	 *
	 * <p>{@code LivingEntitySharedHealMixin} 은 {@code LivingEntity.heal} 한 곳에만 붙는다.
	 * {@code Avatar}·{@code Player}·{@code ServerPlayer} 중 하나라도 이걸 재정의하면서
	 * {@code super.heal} 을 부르지 않으면 재생이 그 경로로 새어 나간다. 26.2 에서는 아무도
	 * 재정의하지 않는데, 다음 버전에서 바뀌면 여기서 먼저 걸리게 해 둔다.
	 */
	@Test
	void 회복_진입점은_LivingEntity_heal_하나뿐이다() throws Exception {
		assertEquals(void.class,
				net.minecraft.world.entity.LivingEntity.class
						.getDeclaredMethod("heal", float.class).getReturnType());
		assertEquals(1, declarationsOf("heal", float.class),
				"heal 을 선언하는 클래스가 늘면 mixin 이 한 곳만 막아서는 부족하다");
	}

	/**
	 * 배고픔을 막는 자리가 정말 하나뿐인지 확인한다.
	 *
	 * <p>{@code PlayerSharedExhaustionMixin} 은 {@code Player.causeFoodExhaustion} 한 곳에만
	 * 붙는다. {@code ServerPlayer} 가 재정의하기 시작하면 허기 소모가 새어 나간다.
	 */
	@Test
	void 허기_소모_진입점은_Player_causeFoodExhaustion_하나뿐이다() throws Exception {
		assertEquals(void.class,
				net.minecraft.world.entity.player.Player.class
						.getDeclaredMethod("causeFoodExhaustion", float.class).getReturnType());
		assertEquals(1, declarationsOf("causeFoodExhaustion", float.class),
				"causeFoodExhaustion 을 선언하는 클래스가 늘면 mixin 이 한 곳만 막아서는 부족하다");
	}

	/** {@code ServerPlayer} 부터 {@code LivingEntity} 까지 올라가며 그 메서드를 선언한 클래스를 센다. */
	private static int declarationsOf(String name, Class<?>... parameters) {
		int found = 0;
		for (Class<?> type = net.minecraft.server.level.ServerPlayer.class;
				type != null && net.minecraft.world.entity.LivingEntity.class.isAssignableFrom(type);
				type = type.getSuperclass()) {
			try {
				type.getDeclaredMethod(name, parameters);
				found++;
			} catch (NoSuchMethodException ignored) {
				// 이 단계에서는 선언하지 않는다.
			}
		}
		return found;
	}

	/**
	 * 팀 전원이 같은 공유 재생 틱을 받은 상황을 흉내 낸다.
	 *
	 * @param representative           {@code StatMirror.sharedEffectRepresentative} 가 고른 대표
	 * @param shareStatusEffects       상태이상 공유 설정
	 * @param representativeHasEffect  대표도 같은 상태이상을 갖고 있는가
	 * @return 팀원 순서대로의 관측 변화량. 막힌 팀원은 0 이다
	 */
	private static List<StatMirror.PlayerDelta> sharedRegenerationTick(UUID representative,
			boolean shareStatusEffects, boolean representativeHasEffect) {
		List<StatMirror.PlayerDelta> deltas = new ArrayList<>(MEMBERS.size());
		for (UUID member : MEMBERS) {
			boolean dropped = duplicate(member, representative, shareStatusEffects, representativeHasEffect);
			deltas.add(health(dropped ? 0.0F : REGENERATION_TICK));
		}
		return deltas;
	}

	/** 팀 전원이 같은 공유 허기 틱을 받은 상황을 흉내 낸다. 인자 뜻은 위와 같다. */
	private static List<StatMirror.PlayerDelta> sharedHungerTick(UUID representative,
			boolean shareStatusEffects, boolean representativeHasEffect) {
		List<StatMirror.PlayerDelta> deltas = new ArrayList<>(MEMBERS.size());
		for (UUID member : MEMBERS) {
			boolean dropped = duplicate(member, representative, shareStatusEffects, representativeHasEffect);
			deltas.add(food(dropped ? 0 : HUNGER_FOOD_DROP, 0.0F));
		}
		return deltas;
	}

	private static boolean duplicate(UUID member, UUID representative,
			boolean shareStatusEffects, boolean representativeHasEffect) {
		boolean isRepresentative = member.equals(representative);
		return SharedEffectDamage.isDuplicateSharedEffectTick(
				shareStatusEffects, true, true, isRepresentative,
				!isRepresentative && representativeHasEffect);
	}

	private static StatMirror.PlayerDelta health(float health) {
		return new StatMirror.PlayerDelta(health, 0.0F, 0.0F, 0, 0.0F, 0L);
	}

	private static StatMirror.PlayerDelta food(int foodLevel, float saturation) {
		return new StatMirror.PlayerDelta(0.0F, 0.0F, 0.0F, foodLevel, saturation, 0L);
	}

	private static TeamState woundedTeam() {
		TeamState state = TeamState.fresh(SHARED_MAX_HEALTH);
		state.health = WOUNDED_HEALTH;
		return state;
	}

	private static TeamState fedTeam() {
		TeamState state = TeamState.fresh(SHARED_MAX_HEALTH);
		state.health = SHARED_MAX_HEALTH;
		state.foodLevel = 20;
		state.saturation = 5.0F;
		return state;
	}

	private static TeamState hungryTeam() {
		TeamState state = TeamState.fresh(SHARED_MAX_HEALTH);
		state.health = SHARED_MAX_HEALTH;
		state.foodLevel = 6;
		state.saturation = 0.0F;
		return state;
	}
}
