package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.ConditionalEffect;
import com.sharedfate.perk.effect.DamageDealtEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀원 한 명에게만 효과를 몰아 주는 {@code holder} 타입을 검증한다.
 *
 * <p>실제로 플레이어에게 붙였다 떼는 부분은 살아 있는 서버와 접속한 플레이어가 있어야 하므로
 * 여기서 다루지 않는다. 대신 그 코드가 내리는 판단을 전부 여기서 확인한다. "정의를 어떻게
 * 읽는가", "다음 보유자를 누구로 고르는가", "언제 넘길 수 있는가", "누가 어느 배율을 받는가"
 * 네 가지가 정해지면 {@code assign} 은 그 결정대로 두 묶음을 갈아 끼우는 일만 한다.
 *
 * <p>보유자 선정과 이전 판정은 {@link PerkHolderManager} 에 마인크래프트 타입을 하나도 쓰지 않는
 * 순수 함수로 떼어 두었다. 이 시험의 절반이 그 세 함수를 향한다.
 */
class HolderEffectTest {

	@BeforeAll
	static void setUp() {
		// 속성 operation 과 상태이상 레지스트리를 건드리므로 최소 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		// 보유자는 런타임 상태다. 시험끼리 새어 나가지 않게 매번 비운다.
		PerkHolderManager.reset();
		ConditionalPerkManager.beginMultiplierLookupForTesting(null);
	}

	// ------------------------------------------------------------------ 도우미

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	private static HolderEffect parse(String raw) {
		return assertInstanceOf(HolderEffect.class,
				HolderEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	/** 「골드 버프 돌리기」의 완성 정의. */
	private static final String ROTATING_BUFF = """
			{
			  "type": "holder",
			  "rotate_ticks": 1200,
			  "min_hold_ticks": 200,
			  "pass_on_hurt": true,
			  "on_holder": [
			    { "type": "damage_dealt", "multiplier": 1.5 },
			    { "type": "status_effect", "effect": "minecraft:haste", "amplifier": 0 }
			  ],
			  "on_others": [],
			  "on_pass": [
			    { "type": "status_effect", "effect": "minecraft:weakness", "amplifier": 0, "duration": 5 },
			    { "type": "status_effect", "effect": "minecraft:blindness", "amplifier": 0, "duration": 5 },
			    { "type": "status_effect", "effect": "minecraft:mining_fatigue", "amplifier": 0, "duration": 5 }
			  ]
			}
			""";

	/** 「프리즘 제왕과 신하」의 완성 정의. 시간으로는 바뀌지 않는다. */
	private static final String KING_AND_SUBJECTS = """
			{
			  "type": "holder",
			  "rotate_ticks": 0,
			  "pass_on_hurt": false,
			  "on_holder": [
			    { "type": "status_effect", "effect": "minecraft:strength", "amplifier": 1 },
			    { "type": "status_effect", "effect": "minecraft:resistance", "amplifier": 0 }
			  ],
			  "on_others": [
			    { "type": "attribute", "attribute": "minecraft:attack_damage",
			      "operation": "add_multiplied_total", "amount": -0.3 }
			  ]
			}
			""";

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 버프_돌리기_정의를_읽는다() {
		HolderEffect effect = parse(ROTATING_BUFF);

		assertEquals(1200, effect.rotateTicks(), "60초마다 돈다");
		assertEquals(200, effect.minHoldTicks(), "받고 10초 안에는 넘어가지 않는다");
		assertTrue(effect.passOnHurt());
		assertEquals(2, effect.onHolder().size());
		assertTrue(effect.onOthers().isEmpty(), "나머지 팀원은 아무 일도 없다");
		assertEquals(3, effect.onPass().size());
		assertEquals(100, effect.onPass().getFirst().durationTicks(), "duration 5 는 5초 = 100틱이다");
	}

	@Test
	void 제왕과_신하_정의를_읽는다() {
		HolderEffect effect = parse(KING_AND_SUBJECTS);

		assertEquals(0, effect.rotateTicks(), "리더는 시간으로 바뀌지 않는다");
		assertFalse(effect.passOnHurt());
		assertEquals(1, assertInstanceOf(StatusEffectPerk.class, effect.onHolder().getFirst()).amplifier(),
				"힘 II 는 amplifier 1 이다");
		assertEquals(-0.3, assertInstanceOf(AttributeEffect.class, effect.onOthers().getFirst()).amount(),
				1.0e-9);
		assertTrue(effect.onPass().isEmpty());
	}

	@Test
	void 순환_주기와_최소_유지_시간은_기본이_0이다() {
		HolderEffect effect = parse("""
				{ "type": "holder",
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""");

		assertEquals(0, effect.rotateTicks());
		assertEquals(0, effect.minHoldTicks());
		assertFalse(effect.passOnHurt(), "pass_on_hurt 를 안 적으면 거짓이다");
	}

	@Test
	void 순환_주기가_범위를_벗어나면_버린다() {
		String template = """
				{ "type": "holder", "rotate_ticks": %d,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""";

		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(template.formatted(-1))));
		assertNull(HolderEffect.fromJson("sharedfate:test", 0,
				json(template.formatted(HolderEffect.MAX_ROTATE_TICKS + 1))));
		assertInstanceOf(HolderEffect.class, HolderEffect.fromJson("sharedfate:test", 0,
				json(template.formatted(HolderEffect.MAX_ROTATE_TICKS))), "상한 자체는 받는다");
	}

	@Test
	void 최소_유지_시간이_범위를_벗어나면_버린다() {
		String template = """
				{ "type": "holder", "min_hold_ticks": %d, "pass_on_hurt": true,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""";

		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(template.formatted(-1))));
		assertNull(HolderEffect.fromJson("sharedfate:test", 0,
				json(template.formatted(HolderEffect.MAX_MIN_HOLD_TICKS + 1))));
	}

	@Test
	void 참거짓이_아닌_pass_on_hurt_는_버린다() {
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "pass_on_hurt": "네",
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""")), "오타를 조용히 거짓으로 넘기면 증강이 설명과 다르게 동작한다");
	}

	@Test
	void 양쪽_묶음이_모두_비면_버린다() {
		assertNull(HolderEffect.fromJson("sharedfate:test", 0,
				json("{ \"type\": \"holder\", \"rotate_ticks\": 200 }")),
				"아무에게도 아무 일도 없는 보유자 증강은 의미가 없다");
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "on_holder": [], "on_others": [] }
				""")));
	}

	@Test
	void 하위_효과가_잘못되면_전체를_버린다() {
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder",
				  "on_holder": [ { "type": "attribute", "operation": "add_value" } ] }
				""")), "attribute 필드가 빠진 하위 효과 하나 때문에 통째로 버려져야 한다");
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder",
				  "on_others": [ { "type": "그런_건_없다" } ] }
				""")), "알 수 없는 하위 type 도 마찬가지다");
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "on_holder": "강함" }
				""")), "배열이 아니면 버린다");
	}

	@Test
	void 보유자_안에_보유자를_넣을_수_없다() {
		// 안쪽 보유자는 아무도 뽑아 주지 않아 영영 정해지지 않는다. 조용히 죽어 있느니 버린다.
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "rotate_ticks": 200,
				  "on_holder": [
				    { "type": "holder", "rotate_ticks": 100,
				      "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				  ] }
				""")));
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "rotate_ticks": 200, "pass_on_hurt": true,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_pass": [
				    { "type": "holder",
				      "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				  ] }
				""")), "on_pass 안에서도 마찬가지다");
	}

	@Test
	void 최상위가_아니면_버린다() {
		// 순번이 100 이상이라는 것은 다른 효과의 하위로 들어갔다는 뜻이다. 그렇게 들어간
		// 보유자는 PerkHolderManager 가 훑지 않아 순환이 영영 돌지 않는다.
		assertNull(HolderEffect.fromJson("sharedfate:test", ConditionalEffect.childIndex(0, 0), json("""
				{ "type": "holder", "rotate_ticks": 200,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""")));
		assertInstanceOf(HolderEffect.class, HolderEffect.fromJson("sharedfate:test", 99, json("""
				{ "type": "holder", "rotate_ticks": 200,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""")), "최상위 순번 99 까지는 받는다");
	}

	// ------------------------------------------------------------------ on_pass

	@Test
	void on_pass_의_지속시간은_안_적으면_기본값이다() {
		HolderEffect effect = parse("""
				{ "type": "holder", "rotate_ticks": 600,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_pass": [ { "type": "status_effect", "effect": "minecraft:weakness" } ] }
				""");

		assertEquals((int) (HolderEffect.DEFAULT_PASS_DURATION_SECONDS * HolderEffect.TICKS_PER_SECOND),
				effect.onPass().getFirst().durationTicks());
	}

	@Test
	void on_pass_의_지속시간이_범위를_벗어나면_버린다() {
		String template = """
				{ "type": "holder", "rotate_ticks": 600,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_pass": [ { "type": "status_effect", "effect": "minecraft:weakness", "duration": %s } ] }
				""";

		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(template.formatted("0"))));
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(template.formatted("-1"))));
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(template.formatted("601"))));
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(template.formatted("\"오초\""))),
				"숫자가 아닌 duration 은 조용히 기본값으로 넘기지 않는다");
	}

	@Test
	void 넘어갈_길이_없는데_on_pass_만_적으면_버린다() {
		// rotate_ticks 0 이고 pass_on_hurt 도 거짓이면 on_pass 는 영원히 발동하지 않는다.
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "rotate_ticks": 0, "pass_on_hurt": false,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_pass": [ { "type": "status_effect", "effect": "minecraft:weakness" } ] }
				""")));
	}

	// ------------------------------------------------------------------ 하위 순번

	@Test
	void 두_묶음의_하위_순번은_conditional_규칙을_그대로_쓴다() {
		HolderEffect effect = parse("""
				{ "type": "holder", "rotate_ticks": 600,
				  "on_holder": [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": 0.5 } ],
				  "on_others": [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": -0.3 } ] }
				""");

		AttributeEffect holderSide = assertInstanceOf(AttributeEffect.class, effect.onHolder().getFirst());
		AttributeEffect otherSide = assertInstanceOf(AttributeEffect.class, effect.onOthers().getFirst());

		assertEquals(AttributeEffect.modifierId("sharedfate:test", ConditionalEffect.childIndex(0, 0)),
				holderSide.modifierId());
		assertEquals(AttributeEffect.modifierId("sharedfate:test", ConditionalEffect.childIndex(0, 50)),
				otherSide.modifierId(), "on_others 는 when_false 와 똑같이 50 부터 센다");
		assertNotEquals(holderSide.modifierId(), otherSide.modifierId(),
				"같은 식별자를 쓰면 두 묶음이 서로를 덮어쓴다");
	}

	@Test
	void 한_묶음에_효과가_너무_많으면_버린다() {
		StringBuilder builder = new StringBuilder("{ \"type\": \"holder\", \"on_holder\": [");
		for (int i = 0; i < 51; i++) {
			builder.append(i == 0 ? "" : ",").append("{ \"type\": \"damage_dealt\", \"multiplier\": 1.1 }");
		}
		builder.append("] }");

		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(builder.toString())),
				"순번 파생 규칙이 깨지는 개수는 받지 않는다");
	}

	@Test
	void on_pass_의_순번은_두_묶음과_겹치지_않는다() {
		HolderEffect effect = parse("""
				{ "type": "holder", "rotate_ticks": 600,
				  "on_holder": [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": 0.5 } ],
				  "on_others": [ { "type": "attribute", "attribute": "minecraft:movement_speed",
				                   "operation": "add_multiplied_total", "amount": -0.2 } ],
				  "on_pass":   [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": -0.5, "duration": 5 } ] }
				""");

		Set<String> ids = new HashSet<>();
		List<AttributeEffect> all = new ArrayList<>();
		all.add(assertInstanceOf(AttributeEffect.class, effect.onHolder().getFirst()));
		all.add(assertInstanceOf(AttributeEffect.class, effect.onOthers().getFirst()));
		all.add(assertInstanceOf(AttributeEffect.class, effect.onPass().getFirst().effect()));
		for (AttributeEffect attribute : all) {
			assertTrue(ids.add(attribute.modifierId().toString()),
					"세 자리의 수정자 식별자가 하나라도 겹치면 서로를 덮어쓴다");
		}

		assertEquals(AttributeEffect.modifierId("sharedfate:test", OnKillEffect.nestedIndex(0, 0)),
				all.get(2).modifierId(), "on_pass 는 on_kill 과 같은 구간을 쓴다");
	}

	// ------------------------------------------------------------------ 피해 배율

	@Test
	void 배율은_보유자와_나머지가_따로다() {
		HolderEffect effect = new HolderEffect(600, 0, false,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageDealtEffect(0.7), new DamageTakenEffect(1.2)),
				List.of());

		assertEquals(1.5, effect.damageDealtMultiplier(true), 1.0e-9);
		assertEquals(1.0, effect.damageTakenMultiplier(true), 1.0e-9,
				"보유자 쪽에는 받는 피해 효과가 없다");

		assertEquals(0.7, effect.damageDealtMultiplier(false), 1.0e-9);
		assertEquals(1.2, effect.damageTakenMultiplier(false), 1.0e-9);
	}

	@Test
	void 배율은_같은_묶음_안에서_곱해진다() {
		HolderEffect effect = new HolderEffect(0, 0, false,
				List.of(new DamageDealtEffect(1.2), new DamageDealtEffect(1.5)),
				List.of(), List.of());

		assertEquals(1.8, effect.damageDealtMultiplier(true), 1.0e-9);
		assertEquals(1.0, effect.damageDealtMultiplier(false), 1.0e-9, "빈 묶음은 1.0");
	}

	@Test
	void 보유자만_보유자_배율을_받는다() {
		// 이 시험이 이 타입의 핵심이다. 여기가 깨지면 팀 전원이 보유자 배율을 받아
		// 「버프 돌리기」가 그냥 "팀 전체 피해 1.5배" 증강이 되어 버린다.
		UUID team = UUID.randomUUID();
		UUID king = UUID.randomUUID();
		UUID subject = UUID.randomUUID();
		HolderEffect effect = new HolderEffect(1200, 200, true,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageDealtEffect(0.7)),
				List.of());
		PerkHolderManager.setHolderForTesting(effect, team, king);

		ConditionalPerkManager.beginMultiplierLookupForTesting(king);
		assertEquals(1.5, effect.damageDealtMultiplier(), 1.0e-9, "보유자는 보유자 배율을 받는다");

		ConditionalPerkManager.beginMultiplierLookupForTesting(subject);
		assertEquals(0.7, effect.damageDealtMultiplier(), 1.0e-9, "나머지 팀원은 반대쪽 배율을 받는다");
	}

	@Test
	void 대상을_모르면_배율에_관여하지_않는다() {
		UUID team = UUID.randomUUID();
		UUID king = UUID.randomUUID();
		HolderEffect effect = new HolderEffect(1200, 0, false,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageTakenEffect(1.3)),
				List.of());
		PerkHolderManager.setHolderForTesting(effect, team, king);

		ConditionalPerkManager.beginMultiplierLookupForTesting(null);
		assertEquals(1.0, effect.damageDealtMultiplier(), 1.0e-9,
				"누구를 위한 조회인지 모르면 짐작하지 않는다. 짐작하면 팀 전원이 보유자 배율을 받는다");
		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 보유자 선정

	/** 언제나 {@code index} 번째를 고르는 난수. */
	private static IntUnaryOperator fixed(int index) {
		return bound -> index;
	}

	@Test
	void 후보가_없으면_보유자를_비운다() {
		assertNull(PerkHolderManager.chooseNextHolder(null, List.of(), fixed(0)));
		assertNull(PerkHolderManager.chooseNextHolder(UUID.randomUUID(), List.of(), fixed(0)));
		assertNull(PerkHolderManager.chooseNextHolder(null, null, fixed(0)));
	}

	@Test
	void 보유자가_없으면_후보_중에서_뽑는다() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		List<UUID> online = List.of(first, second, third);

		assertSame(first, PerkHolderManager.chooseNextHolder(null, online, fixed(0)));
		assertSame(second, PerkHolderManager.chooseNextHolder(null, online, fixed(1)));
		assertSame(third, PerkHolderManager.chooseNextHolder(null, online, fixed(2)));
	}

	@Test
	void 같은_사람이_연달아_뽑히지_않는다() {
		UUID holder = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		List<UUID> online = List.of(holder, other);

		// 난수가 어떤 값을 내놓아도 지금 보유자는 후보에서 빠져 있다.
		for (int roll = 0; roll < 5; roll++) {
			assertSame(other, PerkHolderManager.chooseNextHolder(holder, online, fixed(roll)));
		}
	}

	@Test
	void 팀원이_한_명뿐이면_그대로_유지한다() {
		UUID alone = UUID.randomUUID();

		assertSame(alone, PerkHolderManager.chooseNextHolder(alone, List.of(alone), fixed(0)),
				"넘길 곳이 없으면 버프가 사라지는 것이 아니라 그대로 남는다");
	}

	@Test
	void 나간_보유자는_다른_사람에게_넘어간다() {
		UUID gone = UUID.randomUUID();
		UUID staying = UUID.randomUUID();

		// 접속 중인 팀원 목록에는 나간 사람이 없다.
		assertSame(staying, PerkHolderManager.chooseNextHolder(gone, List.of(staying), fixed(0)));
		assertNull(PerkHolderManager.chooseNextHolder(gone, List.of(), fixed(0)),
				"넘길 사람도 없으면 보유자를 비운다");
	}

	@Test
	void 난수가_범위를_벗어나도_후보_안에_떨어진다() {
		UUID holder = UUID.randomUUID();
		List<UUID> online = List.of(holder, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

		for (int roll : new int[] {-7, -1, 0, 3, 99}) {
			UUID next = PerkHolderManager.chooseNextHolder(holder, online, fixed(roll));
			assertTrue(online.contains(next), "밖에서 들어온 난수를 그대로 믿지 않는다");
			assertNotEquals(holder, next);
		}
	}

	@Test
	void 난수가_없으면_첫_후보를_고른다() {
		UUID holder = UUID.randomUUID();
		UUID first = UUID.randomUUID();
		List<UUID> online = List.of(holder, first, UUID.randomUUID());

		assertSame(first, PerkHolderManager.chooseNextHolder(holder, online, null));
	}

	// ------------------------------------------------------------------ 이전 판정

	@Test
	void 최소_유지_시간_안에는_넘기지_않는다() {
		assertFalse(PerkHolderManager.holdSatisfied(100L, 100L, 200));
		assertFalse(PerkHolderManager.holdSatisfied(100L, 299L, 200));
		assertTrue(PerkHolderManager.holdSatisfied(100L, 300L, 200), "정확히 채운 순간부터 넘길 수 있다");
		assertTrue(PerkHolderManager.holdSatisfied(100L, 100L, 0),
				"최소 유지 시간이 0 이면 언제든 넘길 수 있다");
	}

	@Test
	void 순환_주기가_0이면_시간으로_바뀌지_않는다() {
		assertFalse(PerkHolderManager.rotationDue(0L, 1_000_000L, 0),
				"제왕과 신하의 리더는 아무리 오래 지나도 시간으로는 바뀌지 않는다");
		assertFalse(PerkHolderManager.rotationDue(0L, 1199L, 1200));
		assertTrue(PerkHolderManager.rotationDue(0L, 1200L, 1200));
		assertTrue(PerkHolderManager.rotationDue(0L, 5000L, 1200));
	}

	// ------------------------------------------------------------------ 런타임 상태

	@Test
	void 보유자를_기억하고_비운다() {
		UUID team = UUID.randomUUID();
		UUID holder = UUID.randomUUID();
		HolderEffect effect = new HolderEffect(1200, 0, false,
				List.of(new DamageDealtEffect(1.5)), List.of(), List.of());

		assertNull(PerkHolderManager.holderOf(effect, team), "처음에는 보유자가 없다");
		assertFalse(PerkHolderManager.isHolder(effect, holder));

		PerkHolderManager.setHolderForTesting(effect, team, holder);
		assertSame(holder, PerkHolderManager.holderOf(effect, team));
		assertTrue(PerkHolderManager.isHolder(effect, holder));
		assertFalse(PerkHolderManager.isHolder(effect, UUID.randomUUID()));

		// 서버가 멈출 때 반드시 비워야 다음 월드로 새어 나가지 않는다.
		PerkHolderManager.reset();
		assertNull(PerkHolderManager.holderOf(effect, team));
		assertFalse(PerkHolderManager.isHolder(effect, holder));
		assertEquals(0L, PerkHolderManager.currentTick());
	}

	@Test
	void 다른_증강의_보유자를_제_것으로_보지_않는다() {
		UUID team = UUID.randomUUID();
		UUID holder = UUID.randomUUID();
		HolderEffect mine = new HolderEffect(1200, 0, false,
				List.of(new DamageDealtEffect(1.5)), List.of(), List.of());
		HolderEffect other = new HolderEffect(1200, 0, false,
				List.of(new DamageDealtEffect(2.0)), List.of(), List.of());

		PerkHolderManager.setHolderForTesting(other, team, holder);

		assertTrue(PerkHolderManager.isHolder(other, holder));
		assertFalse(PerkHolderManager.isHolder(mine, holder),
				"한 팀이 보유자형 증강을 둘 보유해도 보유자는 증강마다 따로다");
	}

	@Test
	void 서버가_없으면_아무_일도_하지_않는다() {
		PerkHolderManager.tick(null);
		PerkHolderManager.onPlayerLeave(null);

		assertEquals(0L, PerkHolderManager.currentTick(), "서버가 없으면 시계도 돌지 않는다");
	}

	// ------------------------------------------------------------------ 하위 효과 훑기

	@Test
	void 하위_효과는_양쪽_묶음이_모두_모인다() {
		HolderEffect effect = parse(KING_AND_SUBJECTS);

		assertEquals(3, effect.children().size(), "보유자 2개 + 나머지 1개");
		assertTrue(effect.children().containsAll(effect.onHolder()));
		assertTrue(effect.children().containsAll(effect.onOthers()));
	}

	@Test
	void 한쪽_묶음이_비어도_하위_효과를_모은다() {
		HolderEffect effect = parse(ROTATING_BUFF);

		assertEquals(2, effect.children().size(), "on_others 가 비어 있어도 on_holder 는 모인다");
		assertTrue(effect.children().containsAll(effect.onHolder()));
	}
}
