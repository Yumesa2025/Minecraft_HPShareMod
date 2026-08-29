package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.OnCriticalEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.perk.effect.OnTeamHurtEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code on_team_hurt} 와 {@code on_critical} 의 정의 읽기를 본다.
 *
 * <p>실제로 방아쇠가 당겨지는 자리 — {@code AFTER_DAMAGE} 이벤트와
 * {@code ServerPlayerCritMixin} — 는 살아 있는 서버와 월드가 있어야 하므로 여기서는 다루지
 * 않는다. 대신 두 타입이 공유하는 {@link TemporaryPerkGrants} 의 읽기 규칙을 모두 확인한다.
 */
class TriggerPerkEffectTest {

	@BeforeAll
	static void setUp() {
		// 상태이상·속성 레지스트리를 보므로 최소한의 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkTriggers.resetForTesting();
	}

	// ------------------------------------------------------------------ 지속시간

	@Test
	void 지속시간을_초로_읽는다() {
		OnTeamHurtEffect effect = teamHurt("""
				{ "type": "on_team_hurt", "durationSeconds": 2,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:resistance" } ] }
				""");

		assertEquals(2 * TemporaryPerkGrants.TICKS_PER_SECOND, effect.durationTicks());
	}

	@Test
	void snake_case_로_적어도_같다() {
		OnCriticalEffect effect = critical("""
				{ "type": "on_critical", "duration_seconds": 3,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				""");

		assertEquals(3 * TemporaryPerkGrants.TICKS_PER_SECOND, effect.durationTicks());
	}

	@Test
	void 지속시간을_안_적으면_기본값이다() {
		OnCriticalEffect effect = critical("""
				{ "type": "on_critical",
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				""");

		assertEquals(
				(int) Math.round(TemporaryPerkGrants.DEFAULT_DURATION_SECONDS
						* TemporaryPerkGrants.TICKS_PER_SECOND),
				effect.durationTicks());
	}

	@Test
	void 유한하지_않은_지속시간은_버린다() {
		// 무한으로 걸면 PerkStatusEffects 가 증강분으로 오해해 공유에서 빼 버린다.
		assertNull(create(PerkEffectType.ON_CRITICAL, """
				{ "type": "on_critical", "durationSeconds": 0,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				"""));
		assertNull(create(PerkEffectType.ON_CRITICAL, """
				{ "type": "on_critical", "durationSeconds": -1,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				"""));
		assertNull(create(PerkEffectType.ON_CRITICAL, """
				{ "type": "on_critical", "durationSeconds": 9999,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				"""), "너무 길면 '잠깐'이 아니라 상시나 다름없다");
		assertNull(create(PerkEffectType.ON_CRITICAL, """
				{ "type": "on_critical", "durationSeconds": "조금",
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				"""));
	}

	@Test
	void 아주_짧은_지속시간도_최소_한_틱은_남는다() {
		OnCriticalEffect effect = critical("""
				{ "type": "on_critical", "durationSeconds": 0.001,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:strength" } ] }
				""");

		assertTrue(effect.durationTicks() >= 1);
	}

	// ------------------------------------------------------------------ 하위 효과

	@Test
	void 하위_효과를_재귀적으로_읽는다() {
		OnTeamHurtEffect effect = teamHurt("""
				{
				  "type": "on_team_hurt",
				  "durationSeconds": 2,
				  "effects": [
				    { "type": "status_effect", "effect": "minecraft:resistance", "amplifier": 0 },
				    { "type": "attribute", "attribute": "minecraft:attack_damage",
				      "operation": "add_multiplied_total", "amount": -0.2 }
				  ]
				}
				""");

		assertEquals(2, effect.effects().size());
		StatusEffectPerk status = assertInstanceOf(StatusEffectPerk.class, effect.effects().getFirst());
		assertEquals(Identifier.parse("minecraft:resistance"), status.effectId());
		assertInstanceOf(AttributeEffect.class, effect.effects().get(1));
	}

	@Test
	void effects_가_비어_있으면_버린다() {
		assertNull(create(PerkEffectType.ON_TEAM_HURT, "{ \"type\": \"on_team_hurt\" }"));
		assertNull(create(PerkEffectType.ON_TEAM_HURT,
				"{ \"type\": \"on_team_hurt\", \"effects\": [] }"));
		assertNull(create(PerkEffectType.ON_TEAM_HURT,
				"{ \"type\": \"on_team_hurt\", \"effects\": 3 }"));
		assertNull(create(PerkEffectType.ON_TEAM_HURT,
				"{ \"type\": \"on_team_hurt\", \"effects\": [ 3 ] }"));
	}

	@Test
	void 하위_효과가_잘못되면_증강_전체를_버린다() {
		assertNull(create(PerkEffectType.ON_CRITICAL, """
				{ "type": "on_critical", "effects": [ { "type": "없는타입" } ] }
				"""));
		assertNull(create(PerkEffectType.ON_CRITICAL, """
				{ "type": "on_critical",
				  "effects": [ { "type": "attribute", "attribute": "minecraft:attack_damage" } ] }
				"""), "operation·amount 가 빠진 속성 효과는 만들 수 없다");
	}

	@Test
	void 하위_효과의_순번은_최상위_순번과_겹치지_않는다() {
		OnCriticalEffect effect = critical("""
				{ "type": "on_critical", "durationSeconds": 3,
				  "effects": [
				    { "type": "attribute", "attribute": "minecraft:attack_damage",
				      "operation": "add_multiplied_total", "amount": 0.1 }
				  ] }
				""", 1);

		AttributeEffect nested = assertInstanceOf(AttributeEffect.class, effect.effects().getFirst());
		// on_kill 과 같은 규칙을 쓴다. 최상위 순번은 증강 안에서 유일하므로 구간이 겹치지 않는다.
		assertEquals(AttributeEffect.modifierId("sharedfate:테스트", OnKillEffect.nestedIndex(1, 0)),
				nested.modifierId());
		assertNotEquals(AttributeEffect.modifierId("sharedfate:테스트", 1), nested.modifierId());
		assertNotEquals(AttributeEffect.modifierId("sharedfate:테스트", OnKillEffect.nestedIndex(0, 0)),
				nested.modifierId());
	}

	// ------------------------------------------------------------------ includeVictim

	@Test
	void 맞은_본인도_기본으로_포함한다() {
		// 체력을 공유하므로 한 명이 맞으면 팀 전체의 체력이 깎인다. 본인도 겪은 일이다.
		assertTrue(teamHurt("""
				{ "type": "on_team_hurt",
				  "effects": [ { "type": "status_effect", "effect": "minecraft:resistance" } ] }
				""").includesVictim());
	}

	@Test
	void 본인을_뺄_수도_있다() {
		assertFalse(teamHurt("""
				{ "type": "on_team_hurt", "includeVictim": false,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:resistance" } ] }
				""").includesVictim());
	}

	@Test
	void includeVictim_이_참거짓이_아니면_버린다() {
		assertNull(create(PerkEffectType.ON_TEAM_HURT, """
				{ "type": "on_team_hurt", "includeVictim": "네",
				  "effects": [ { "type": "status_effect", "effect": "minecraft:resistance" } ] }
				"""));
	}

	// ------------------------------------------------------------------ 상시 효과가 아니다

	@Test
	void 상시로_거는_배율이_없다() {
		OnTeamHurtEffect effect = teamHurt("""
				{ "type": "on_team_hurt",
				  "effects": [ { "type": "damage_taken", "multiplier": 0.5 } ] }
				""");

		// 방아쇠가 당겨지기 전에는 아무것도 걸려 있지 않아야 한다. 하위 효과의 배율이
		// 상시로 새어 나가면 증강을 얻는 순간부터 늘 걸려 있는 것과 같아진다.
		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
		assertEquals(1.0, effect.damageDealtMultiplier(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 도우미

	private static OnTeamHurtEffect teamHurt(String json) {
		return assertInstanceOf(OnTeamHurtEffect.class,
				create(PerkEffectType.ON_TEAM_HURT, json));
	}

	private static OnCriticalEffect critical(String json) {
		return critical(json, 0);
	}

	private static OnCriticalEffect critical(String json, int index) {
		return assertInstanceOf(OnCriticalEffect.class,
				create(PerkEffectType.ON_CRITICAL, json, index));
	}

	private static PerkEffect create(PerkEffectType type, String json) {
		return create(type, json, 0);
	}

	private static PerkEffect create(PerkEffectType type, String json, int index) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return type.create("sharedfate:테스트", index, parsed);
	}
}
