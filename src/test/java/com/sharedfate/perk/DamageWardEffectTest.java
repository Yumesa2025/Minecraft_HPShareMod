package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DamageTakenBlockingEffect;
import com.sharedfate.perk.effect.DamageWardEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 골드 「호위」({@code damage_ward})를 살아 있는 월드 없이 시험한다.
 *
 * <p>실제 피해원과 지금 시각은 살아 있는 월드가 있어야 읽을 수 있다. 그래서 그 값을 읽는 일은
 * {@link PerkDamage#blocksMobDamage} 에 두고, 여기서는 읽어 온 값으로 답을 내는 순수 계산만
 * 본다. 보는 것은 셋이다.
 *
 * <ul>
 *   <li>정의 읽기와 범위 자르기 ({@link DamageWardEffect#fromJson})</li>
 *   <li>「고른 사람」 가려내기 ({@link DamageWardEffect#chosenBy}·
 *       {@link DamageWardEffect#shortestOf})</li>
 *   <li>쿨타임 계산 ({@link DamageWardTracker})</li>
 * </ul>
 *
 * <p>정의 읽기는 {@code PerkEffectType} 을 거치지 않고 팩토리를 직접 부른다. 등록 여부는
 * {@code DefaultPerkPoolValuesTest} 가 「기본 정의가 조용히 사라지지 않았는가」로 잡는다.
 */
class DamageWardEffectTest {

	private static final int TICKS_PER_SECOND = 20;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	/** 다른 시험이 올려 둔 정의나 쿨타임 기록이 남아 있으면 답이 흔들린다. 앞뒤로 모두 비운다. */
	@BeforeEach
	void 준비() {
		PerkRegistry.clear();
		DamageWardTracker.reset();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		DamageWardTracker.reset();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 쿨타임을_적지_않으면_기본값이다() {
		DamageWardEffect effect = ward("{ \"type\": \"damage_ward\" }");

		assertEquals(DamageWardEffect.DEFAULT_COOLDOWN_SECONDS, effect.cooldownSeconds());
		assertEquals(DamageWardEffect.DEFAULT_COOLDOWN_SECONDS * TICKS_PER_SECOND,
				effect.cooldownTicks());
	}

	@Test
	void 쿨타임을_적으면_읽힌다() {
		DamageWardEffect effect =
				ward("{ \"type\": \"damage_ward\", \"cooldown_seconds\": 30 }");

		assertEquals(30, effect.cooldownSeconds());
		assertEquals(600, effect.cooldownTicks());
	}

	/** 카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다. */
	@Test
	void 카멜케이스로_적어도_읽힌다() {
		assertEquals(45, ward("{ \"type\": \"damage_ward\", \"cooldownSeconds\": 45 }")
				.cooldownSeconds());
	}

	/** 범위를 벗어나도 정의를 버리지 않는다. 숫자 하나 때문에 증강이 통째로 사라지면 손해가 크다. */
	@Test
	void 범위를_벗어나면_자른다() {
		assertEquals(DamageWardEffect.MIN_COOLDOWN_SECONDS,
				ward("{ \"type\": \"damage_ward\", \"cooldown_seconds\": 0 }").cooldownSeconds());
		assertEquals(DamageWardEffect.MIN_COOLDOWN_SECONDS,
				ward("{ \"type\": \"damage_ward\", \"cooldown_seconds\": -99 }").cooldownSeconds());
		assertEquals(DamageWardEffect.MAX_COOLDOWN_SECONDS,
				ward("{ \"type\": \"damage_ward\", \"cooldown_seconds\": 9999 }").cooldownSeconds());
	}

	/** 숫자가 아닌 값은 기본값으로 물러난다. 정의를 버리지는 않는다. */
	@Test
	void 숫자가_아니면_기본값이다() {
		assertEquals(DamageWardEffect.DEFAULT_COOLDOWN_SECONDS,
				ward("{ \"type\": \"damage_ward\", \"cooldown_seconds\": \"열\" }").cooldownSeconds());
	}

	/** 배율에는 손대지 않는다. 이 증강은 피해를 깎는 것이 아니라 한 대를 통째로 없애는 것이다. */
	@Test
	void 피해_배율은_건드리지_않는다() {
		DamageWardEffect effect = new DamageWardEffect(200);

		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
		assertEquals(1.0, effect.damageDealtMultiplier(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 「고른 사람」 가려내기

	@Test
	void 고른_사람만_주인이다() {
		UUID chooser = UUID.randomUUID();
		UUID teammate = UUID.randomUUID();
		TeamState state = teamWith("sharedfate:ward", chooser);

		assertTrue(DamageWardEffect.chosenBy(state, "sharedfate:ward", chooser));
		assertFalse(DamageWardEffect.chosenBy(state, "sharedfate:ward", teammate),
				"같은 팀이라도 고른 사람이 아니면 주인이 아니다");
	}

	/** 덤으로 받은 증강은 주인이 적히지 않는다. 그 경우 아무도 주인이 아니다. */
	@Test
	void 주인이_없는_증강은_아무도_주인이_아니다() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:gift");

		assertFalse(DamageWardEffect.chosenBy(state, "sharedfate:gift", UUID.randomUUID()));
		assertFalse(DamageWardEffect.chosenBy(state, null, UUID.randomUUID()));
		assertFalse(DamageWardEffect.chosenBy(null, "sharedfate:gift", UUID.randomUUID()));
		assertFalse(DamageWardEffect.chosenBy(state, "sharedfate:gift", null));
	}

	@Test
	void 효과_목록에서_호위를_골라낸다() {
		DamageWardEffect ward = new DamageWardEffect(200);

		assertSame(ward, DamageWardEffect.shortestOf(
				List.of(new DamageTakenBlockingEffect(0.5), ward)));
		assertNull(DamageWardEffect.shortestOf(List.of(new DamageTakenBlockingEffect(0.5))));
		assertNull(DamageWardEffect.shortestOf(List.of()));
		assertNull(DamageWardEffect.shortestOf(null));
	}

	/** 여럿을 골랐으면 짧은 쪽이 이긴다. 긴 쪽이 이기면 나중에 고른 증강이 앞의 것을 깎아 먹는다. */
	@Test
	void 여럿이면_쿨타임이_짧은_쪽이_이긴다() {
		DamageWardEffect quick = new DamageWardEffect(100);
		DamageWardEffect slow = new DamageWardEffect(2400);

		assertSame(quick, DamageWardEffect.shortestOf(List.of(slow, quick)));
		assertSame(quick, DamageWardEffect.shortestOf(List.of(quick, slow)));
	}

	// ------------------------------------------------------------------ 팀 조회

	/**
	 * 훑을 값어치가 없는 팀은 곧바로 빠져나간다.
	 *
	 * <p>실제로 「호위」를 가진 팀까지 훑는 길은 증강 풀에 {@code damage_ward} 정의가 들어와야
	 * 볼 수 있다. 그쪽은 {@code DefaultPerkPoolValuesTest} 가 「기본 풀이 그대로 읽히는가」로
	 * 잡으므로, 여기서는 훑기 전에 물러나는 조건만 못박는다.
	 */
	@Test
	void 훑을_것이_없으면_곧바로_없다() {
		UUID chooser = UUID.randomUUID();
		TeamState disabled = teamWith("sharedfate:ward", chooser);
		disabled.perksEnabled = false;

		assertNull(DamageWardEffect.wardFor(null, chooser));
		assertNull(DamageWardEffect.wardFor(teamWith("sharedfate:ward", chooser), null));
		assertNull(DamageWardEffect.wardFor(disabled, chooser));
		assertNull(DamageWardEffect.wardFor(TeamState.fresh(20.0F), chooser),
				"증강이 하나도 없는 팀은 훑을 것도 없다");
	}

	/** 풀에서 사라진 id 는 조용히 건너뛴다. 저장에만 남은 id 는 언제든 생긴다. */
	@Test
	void 정의가_없는_id는_건너뛴다() {
		UUID chooser = UUID.randomUUID();

		assertNull(DamageWardEffect.wardFor(teamWith("sharedfate:missing", chooser), chooser));
	}

	// ------------------------------------------------------------------ 쿨타임

	@Test
	void 처음에는_언제나_막을_수_있다() {
		assertTrue(DamageWardTracker.tryConsume(UUID.randomUUID(), 0L, 200));
		assertTrue(DamageWardTracker.tryConsume(UUID.randomUUID(), 123456L, 200));
	}

	@Test
	void 막고_나면_쿨타임_동안_다시_막지_못한다() {
		UUID player = UUID.randomUUID();

		assertTrue(DamageWardTracker.tryConsume(player, 1000L, 200));
		assertFalse(DamageWardTracker.tryConsume(player, 1000L, 200));
		assertFalse(DamageWardTracker.tryConsume(player, 1199L, 200), "1틱이 모자라면 아직이다");
		assertTrue(DamageWardTracker.tryConsume(player, 1200L, 200), "딱 맞으면 다시 막는다");
	}

	@Test
	void 남은_쿨타임을_셀_수_있다() {
		UUID player = UUID.randomUUID();

		assertEquals(0L, DamageWardTracker.remainingTicks(player, 1000L, 200));
		assertTrue(DamageWardTracker.tryConsume(player, 1000L, 200));
		assertEquals(200L, DamageWardTracker.remainingTicks(player, 1000L, 200));
		assertEquals(1L, DamageWardTracker.remainingTicks(player, 1199L, 200));
		assertEquals(0L, DamageWardTracker.remainingTicks(player, 1200L, 200));
	}

	/** 사람마다 따로 센다. 한 사람이 막았다고 다른 사람 것이 잠기면 안 된다. */
	@Test
	void 쿨타임은_사람마다_따로다() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		assertTrue(DamageWardTracker.tryConsume(first, 0L, 200));
		assertTrue(DamageWardTracker.tryConsume(second, 0L, 200));
		assertFalse(DamageWardTracker.tryConsume(first, 10L, 200));
	}

	/** 회차마다 월드를 새로 만드는 모드라 게임 시간이 과거로 돌아가는 일이 실제로 생긴다. */
	@Test
	void 시각이_되감기면_곧바로_막을_수_있다() {
		UUID player = UUID.randomUUID();

		assertTrue(DamageWardTracker.tryConsume(player, 100000L, 200));
		assertTrue(DamageWardTracker.tryConsume(player, 5L, 200),
				"되감긴 만큼 기다리게 하면 몇 시간씩 잠긴다");
	}

	@Test
	void 기록을_지우면_다시_막을_수_있다() {
		UUID player = UUID.randomUUID();

		assertTrue(DamageWardTracker.tryConsume(player, 0L, 200));
		assertFalse(DamageWardTracker.tryConsume(player, 10L, 200));
		DamageWardTracker.forget(player);
		assertTrue(DamageWardTracker.tryConsume(player, 10L, 200));
	}

	@Test
	void 이름이_없으면_아무_일도_없다() {
		assertFalse(DamageWardTracker.tryConsume(null, 0L, 200));
		assertEquals(0L, DamageWardTracker.remainingTicks(null, 0L, 200));
	}

	// ------------------------------------------------------------------ 피해 진입점

	/**
	 * 읽을 것이 없으면 아무것도 막지 않는다.
	 *
	 * <p>{@code hurtServer} 진입점은 팀도 증강도 없는 서버에서도 매 피해마다 이 자리를 지난다.
	 * 어떤 인자가 비어 있어도 예외 없이 거짓이어야 한다.
	 */
	@Test
	void 읽을_것이_없으면_막지_않는다() {
		assertFalse(PerkDamage.blocksMobDamage(null, null, null, 1.0F));
	}

	// ------------------------------------------------------------------ 도우미

	private static DamageWardEffect ward(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		PerkEffect effect = DamageWardEffect.fromJson("sharedfate:테스트", 0, parsed);
		assertNotNull(effect, "damage_ward 는 값이 틀려도 정의를 버리지 않는다");
		return assertInstanceOf(DamageWardEffect.class, effect);
	}

	private static TeamState teamWith(String perkId, UUID chooser) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		state.perkOwners.put(perkId, chooser);
		return state;
	}
}
