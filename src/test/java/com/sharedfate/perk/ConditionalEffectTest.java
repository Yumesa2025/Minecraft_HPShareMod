package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.ConditionalEffect;
import com.sharedfate.perk.effect.DamageDealtEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import com.sharedfate.team.TeamState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 조건부 효과의 정의 읽기와 판정 규칙을 검증한다.
 *
 * <p>실제로 플레이어에게 붙였다 떼는 부분은 서버와 접속한 플레이어가 있어야 하므로 여기서는
 * 다루지 않는다. 대신 그 판단의 근거가 되는 두 가지, 곧 "정의를 어떻게 읽는가"
 * ({@link ConditionalEffect#fromJson})와 "팀 상태를 어떻게 판정하는가"
 * ({@link ConditionalEffect#matches})를 직접 시험한다.
 */
class ConditionalEffectTest {

	@BeforeAll
	static void setUp() {
		// 속성 operation 이나 TeamState 처럼 마인크래프트 쪽을 건드리므로 최소 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 도우미

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	private static ConditionalEffect parse(String raw) {
		return assertInstanceOf(ConditionalEffect.class,
				ConditionalEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	/** 최대 체력 20, 현재 체력·허기 가득 찬 상태. */
	private static TeamState state(float health, int foodLevel) {
		TeamState state = TeamState.fresh(20.0F);
		state.health = health;
		state.foodLevel = foodLevel;
		return state;
	}

	private static final String PREDATOR = """
			{
			  "type": "conditional",
			  "condition": "hunger_full",
			  "when_true": [
			    { "type": "attribute", "attribute": "minecraft:attack_damage",
			      "operation": "add_multiplied_base", "amount": 0.15 }
			  ],
			  "when_false": [
			    { "type": "attribute", "attribute": "minecraft:movement_speed",
			      "operation": "add_multiplied_base", "amount": -0.2 },
			    { "type": "attribute", "attribute": "minecraft:attack_damage",
			      "operation": "add_multiplied_base", "amount": -0.3 }
			  ]
			}
			""";

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 포식자_정의를_읽는다() {
		ConditionalEffect effect = parse(PREDATOR);

		assertEquals(ConditionalEffect.Condition.HUNGER_FULL, effect.condition());
		assertEquals(1, effect.whenTrue().size());
		assertEquals(2, effect.whenFalse().size(), "거짓 쪽 효과는 두 개다");
	}

	@Test
	void 벼랑끝_정의를_읽는다() {
		ConditionalEffect effect = parse("""
				{
				  "type": "conditional",
				  "condition": "health_below",
				  "threshold": 0.6,
				  "when_true": [ { "type": "status_effect", "effect": "minecraft:strength", "amplifier": 1 } ],
				  "when_false": [ { "type": "status_effect", "effect": "minecraft:weakness", "amplifier": 0 } ]
				}
				""");

		assertEquals(ConditionalEffect.Condition.HEALTH_BELOW, effect.condition());
		assertEquals(0.6, effect.threshold(), 1.0e-9);
		assertEquals(1, assertInstanceOf(StatusEffectPerk.class, effect.whenTrue().getFirst()).amplifier(),
				"힘 II 는 amplifier 1 이다");
		assertEquals(0, assertInstanceOf(StatusEffectPerk.class, effect.whenFalse().getFirst()).amplifier());
	}

	@Test
	void 묶음이_없으면_빈_목록으로_본다() {
		ConditionalEffect effect = parse("""
				{ "type": "conditional", "condition": "health_full",
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				""");

		assertEquals(1, effect.whenTrue().size());
		assertTrue(effect.whenFalse().isEmpty(), "when_false 를 안 적으면 빈 목록이다");
	}

	@Test
	void 조건이_없거나_모르는_값이면_버린다() {
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("""
						{ "type": "conditional",
						  "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
						""")));
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("""
						{ "type": "conditional", "condition": "달빛이_밝으면",
						  "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
						""")));
	}

	@Test
	void 기준값이_없거나_범위를_벗어나면_버린다() {
		String template = """
				{ "type": "conditional", "condition": "health_below", %s
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				""";

		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0, json(template.formatted(""))),
				"health_below 에는 threshold 가 반드시 있어야 한다");
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json(template.formatted("\"threshold\": 1.5,"))));
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json(template.formatted("\"threshold\": -0.1,"))));
	}

	@Test
	void 만복_조건은_기준값이_없어도_된다() {
		assertEquals(0.0, parse("""
				{ "type": "conditional", "condition": "hunger_full",
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				""").threshold(), 1.0e-9);
	}

	@Test
	void 양쪽_모두_비어_있으면_버린다() {
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("{ \"type\": \"conditional\", \"condition\": \"health_full\" }")),
				"아무 효과도 없는 조건부 증강은 의미가 없다");
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("""
						{ "type": "conditional", "condition": "health_full",
						  "when_true": [], "when_false": [] }
						""")));
	}

	@Test
	void 하위_효과가_잘못되면_전체를_버린다() {
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("""
						{ "type": "conditional", "condition": "health_full",
						  "when_true": [ { "type": "attribute", "operation": "add_value" } ] }
						""")),
				"attribute 필드가 빠진 하위 효과 하나 때문에 조건부 효과 전체가 버려져야 한다");
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("""
						{ "type": "conditional", "condition": "health_full",
						  "when_true": [ { "type": "그런_건_없다" } ] }
						""")),
				"알 수 없는 하위 type 도 마찬가지다");
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0,
				json("""
						{ "type": "conditional", "condition": "health_full",
						  "when_true": "속성" }
						""")),
				"배열이 아니면 버린다");
	}

	@Test
	void 중첩은_깊이_제한에_걸린다() {
		// 한 겹 중첩까지는 읽는다.
		ConditionalEffect outer = parse("""
				{ "type": "conditional", "condition": "hunger_full",
				  "when_true": [
				    { "type": "conditional", "condition": "health_full",
				      "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ] }
				""");
		assertInstanceOf(ConditionalEffect.class, outer.whenTrue().getFirst());

		// 두 겹부터는 버린다. 버리면 바깥까지 통째로 없어진다.
		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "conditional", "condition": "hunger_full",
				  "when_true": [
				    { "type": "conditional", "condition": "health_full",
				      "when_true": [
				        { "type": "conditional", "condition": "hunger_full",
				          "when_true": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				      ] }
				  ] }
				""")), "무한 중첩은 깊이 제한에서 막혀야 한다");

		// 제한에 걸린 뒤에도 깊이가 원래대로 돌아와 있어야 다음 정의를 읽을 수 있다.
		assertInstanceOf(ConditionalEffect.class,
				ConditionalEffect.fromJson("sharedfate:test", 0, json(PREDATOR)));
	}

	// ------------------------------------------------------------------ 하위 순번

	@Test
	void 하위_효과의_순번은_서로_겹치지_않는다() {
		ConditionalEffect effect = parse(PREDATOR);

		AttributeEffect trueSide = assertInstanceOf(AttributeEffect.class, effect.whenTrue().getFirst());
		AttributeEffect falseFirst = assertInstanceOf(AttributeEffect.class, effect.whenFalse().get(0));
		AttributeEffect falseSecond = assertInstanceOf(AttributeEffect.class, effect.whenFalse().get(1));

		assertNotEquals(trueSide.modifierId(), falseFirst.modifierId(),
				"참 쪽과 거짓 쪽이 같은 식별자를 쓰면 서로를 덮어쓴다");
		assertNotEquals(falseFirst.modifierId(), falseSecond.modifierId(),
				"같은 묶음 안에서도 서로 달라야 한다");

		assertEquals(AttributeEffect.modifierId("sharedfate:test", ConditionalEffect.childIndex(0, 0)),
				trueSide.modifierId());
		assertEquals(AttributeEffect.modifierId("sharedfate:test", ConditionalEffect.childIndex(0, 50)),
				falseFirst.modifierId(), "거짓 쪽은 50 부터 센다");
	}

	@Test
	void 하위_순번은_최상위_순번과_겹치지_않는다() {
		// 최상위 효과는 0,1,2,… 를 쓴다. 하위 순번이 그 범위로 내려오면 안 된다.
		for (int parent = 0; parent < 4; parent++) {
			for (int ordinal = 0; ordinal < 100; ordinal++) {
				assertTrue(ConditionalEffect.childIndex(parent, ordinal) >= 100,
						"하위 순번은 언제나 100 이상이어야 한다");
			}
		}
		assertNotEquals(ConditionalEffect.childIndex(0, 99), ConditionalEffect.childIndex(1, 0),
				"부모가 다르면 하위 순번도 달라야 한다");
		assertEquals(100, ConditionalEffect.childIndex(0, 0));
		assertEquals(150, ConditionalEffect.childIndex(0, 50));
		assertEquals(200, ConditionalEffect.childIndex(1, 0));
	}

	@Test
	void 한_묶음에_효과가_너무_많으면_버린다() {
		StringBuilder builder = new StringBuilder(
				"{ \"type\": \"conditional\", \"condition\": \"health_full\", \"when_true\": [");
		for (int i = 0; i < 51; i++) {
			builder.append(i == 0 ? "" : ",").append("{ \"type\": \"damage_dealt\", \"multiplier\": 1.1 }");
		}
		builder.append("] }");

		assertNull(ConditionalEffect.fromJson("sharedfate:test", 0, json(builder.toString())),
				"순번 파생 규칙이 깨지는 개수는 받지 않는다");
	}

	// ------------------------------------------------------------------ 판정

	@Test
	void 허기_만복_판정() {
		ConditionalEffect effect = parse(PREDATOR);

		assertTrue(effect.matches(state(20.0F, 20)));
		assertFalse(effect.matches(state(20.0F, 19)));
		assertFalse(effect.matches(state(20.0F, 0)));
	}

	@Test
	void 체력_만피_판정() {
		ConditionalEffect effect = parse("""
				{ "type": "conditional", "condition": "health_full",
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.15 } ] }
				""");

		assertTrue(effect.matches(state(20.0F, 20)));
		assertFalse(effect.matches(state(19.9F, 20)));
		assertFalse(effect.matches(state(0.0F, 20)));
	}

	@Test
	void 체력_비율_판정은_경계에서_한쪽만_참이다() {
		ConditionalEffect below = parse("""
				{ "type": "conditional", "condition": "health_below", "threshold": 0.6,
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				""");
		ConditionalEffect above = parse("""
				{ "type": "conditional", "condition": "health_above", "threshold": 0.6,
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				""");

		TeamState exact = state(12.0F, 20);
		assertTrue(below.matches(exact), "60% 정확히는 이하에 든다");
		assertFalse(above.matches(exact));

		TeamState higher = state(13.0F, 20);
		assertFalse(below.matches(higher));
		assertTrue(above.matches(higher));

		TeamState lower = state(4.0F, 20);
		assertTrue(below.matches(lower));
		assertFalse(above.matches(lower));
	}

	@Test
	void 팀_상태를_모르면_거짓으로_본다() {
		assertFalse(parse(PREDATOR).matches(null));
	}

	@Test
	void 최대_체력이_이상하면_비율을_0으로_본다() {
		ConditionalEffect full = parse("""
				{ "type": "conditional", "condition": "health_full",
				  "when_true": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				""");

		TeamState broken = state(20.0F, 20);
		broken.maxHealth = 0.0F;

		assertFalse(full.matches(broken), "최대 체력이 0 이면 가득 찼다고 보지 않는다");
	}

	// ------------------------------------------------------------------ 피해 배율

	@Test
	void 피해_배율은_지금_붙어_있는_묶음만_센다() {
		ConditionalEffect effect = new ConditionalEffect(
				ConditionalEffect.Condition.HUNGER_FULL, 0.0,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageDealtEffect(0.7), new DamageTakenEffect(1.2)));

		assertEquals(1.5, effect.damageDealtMultiplier(true), 1.0e-9);
		assertEquals(1.0, effect.damageTakenMultiplier(true), 1.0e-9,
				"참 쪽에는 받는 피해 효과가 없다");

		assertEquals(0.7, effect.damageDealtMultiplier(false), 1.0e-9);
		assertEquals(1.2, effect.damageTakenMultiplier(false), 1.0e-9);
	}

	@Test
	void 피해_배율은_같은_묶음_안에서_곱해진다() {
		ConditionalEffect effect = new ConditionalEffect(
				ConditionalEffect.Condition.HEALTH_FULL, 0.0,
				List.of(new DamageDealtEffect(1.2), new DamageDealtEffect(1.5)),
				List.of());

		assertEquals(1.8, effect.damageDealtMultiplier(true), 1.0e-9);
		assertEquals(1.0, effect.damageDealtMultiplier(false), 1.0e-9, "빈 묶음은 1.0");
	}

	@Test
	void 대상을_모르면_배율에_관여하지_않는다() {
		ConditionalPerkManager.beginMultiplierLookup(null);
		ConditionalEffect effect = new ConditionalEffect(
				ConditionalEffect.Condition.HUNGER_FULL, 0.0,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageDealtEffect(0.7)));

		assertEquals(1.0, effect.damageDealtMultiplier(), 1.0e-9,
				"누구를 위한 조회인지 모르고 기억해 둔 판정도 없으면 1.0 이다");
		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
	}

	@Test
	void 서버가_없으면_주기_평가가_아무_일도_하지_않는다() {
		ConditionalPerkManager.beginMultiplierLookup(null);
		ConditionalPerkManager.tick(null);
		ConditionalPerkManager.refreshPlayer(null);

		assertNull(ConditionalPerkManager.multiplierContext());
	}

	// ------------------------------------------------------------------ 하위 효과 훑기

	@Test
	void 하위_효과는_양쪽_모두_모인다() {
		ConditionalEffect effect = parse(PREDATOR);

		assertEquals(3, effect.children().size(), "참 1개 + 거짓 2개");
		assertTrue(effect.children().containsAll(effect.whenTrue()));
		assertTrue(effect.children().containsAll(effect.whenFalse()));
	}

	@Test
	void 안에_든_상태이상도_증강분으로_잡힌다(@TempDir Path dir) throws IOException {
		// 벼랑 끝처럼 상태이상이 conditional 안에 들어 있으면, 겉만 봐서는 증강분인 줄 모른다.
		// 그러면 팀 공유 상태이상으로 새어 나가 증강을 잃은 뒤에도 되살아난다.
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    {
				      "id": "sharedfate:brink",
				      "name": "벼랑 끝",
				      "rarity": "epic",
				      "effects": [
				        {
				          "type": "conditional",
				          "condition": "health_below",
				          "threshold": 0.6,
				          "when_true": [
				            { "type": "status_effect", "effect": "minecraft:strength", "amplifier": 1 }
				          ],
				          "when_false": [
				            { "type": "status_effect", "effect": "minecraft:weakness", "amplifier": 0 }
				          ]
				        }
				      ]
				    }
				  ]
				}
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		TeamState state = state(20.0F, 20);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:brink");

		PerkStatusEffects granted = PerkStatusEffects.of(state);
		assertTrue(granted.covers(BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("minecraft:strength"))
						.orElseThrow()),
				"참 쪽 상태이상도 증강분으로 잡혀야 한다");
		assertTrue(granted.covers(BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("minecraft:weakness"))
						.orElseThrow()),
				"거짓 쪽 상태이상도 마찬가지다");

		PerkRegistry.clear();
	}

	@Test
	void 조건_이름은_대소문자와_공백을_가리지_않는다() {
		assertSame(ConditionalEffect.Condition.HEALTH_BELOW,
				ConditionalEffect.Condition.fromId("  Health_Below "));
		assertNull(ConditionalEffect.Condition.fromId("health below"));
		assertNull(ConditionalEffect.Condition.fromId(null));
	}
}
