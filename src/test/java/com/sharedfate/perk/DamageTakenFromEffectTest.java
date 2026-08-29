package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DamageTakenFromEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code damage_taken_from} 의 정의 읽기를 본다.
 *
 * <p>실제 피해원({@code DamageSource})은 데이터팩 레지스트리가 있어야 만들 수 있으므로
 * 여기서는 정의가 어떤 종류를 가리키는지까지만 확인한다. 배율이 실제 피해에 곱해지는 자리는
 * {@code LivingEntityPerkDamageMixin} → {@link PerkDamage} 이고, 그 산술은
 * {@code PerkDamageTest} 가 이미 확인한다.
 */
class DamageTakenFromEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 피해_종류_목록을_읽는다() {
		DamageTakenFromEffect effect = create("""
				{ "type": "damage_taken_from", "multiplier": 1.5,
				  "sources": ["minecraft:in_fire", "minecraft:on_fire", "minecraft:lava"] }
				""");

		assertEquals(1.5, effect.multiplier(), 1.0e-9);
		assertEquals(3, effect.types().size());
		assertTrue(effect.tags().isEmpty());
		assertTrue(effect.coversType(Identifier.parse("minecraft:lava")));
		assertFalse(effect.coversType(Identifier.parse("minecraft:fall")));
	}

	@Test
	void 태그도_적을_수_있다() {
		DamageTakenFromEffect effect = create("""
				{ "type": "damage_taken_from", "multiplier": 1.5,
				  "sources": ["#minecraft:is_fire"] }
				""");

		assertEquals(1, effect.tags().size());
		assertTrue(effect.types().isEmpty());
		// 태그는 실제 피해원이 있어야 풀리므로 이름만으로는 걸리지 않는다.
		assertFalse(effect.coversType(Identifier.parse("minecraft:is_fire")));
	}

	@Test
	void sources_가_없거나_비면_버린다() {
		assertNull(raw("{ \"type\": \"damage_taken_from\", \"multiplier\": 1.5 }"));
		assertNull(raw("{ \"type\": \"damage_taken_from\", \"multiplier\": 1.5, \"sources\": [] }"));
		assertNull(raw("{ \"type\": \"damage_taken_from\", \"multiplier\": 1.5, \"sources\": 3 }"));
		assertNull(raw("""
				{ "type": "damage_taken_from", "multiplier": 1.5, "sources": ["대문자 안 됨"] }
				"""));
	}

	@Test
	void multiplier_가_없거나_범위를_벗어나면_버린다() {
		assertNull(raw("{ \"type\": \"damage_taken_from\", \"sources\": [\"minecraft:lava\"] }"));
		assertNull(raw("""
				{ "type": "damage_taken_from", "multiplier": -1, "sources": ["minecraft:lava"] }
				"""));
		assertNull(raw("""
				{ "type": "damage_taken_from", "multiplier": 999, "sources": ["minecraft:lava"] }
				"""));
	}

	@Test
	void 하위_효과로는_넣을_수_없다() {
		// 피해원을 아는 자리(PerkDamage)는 최상위 효과만 훑는다. 안에 넣으면 조용히 아무 일도
		// 하지 않으므로 읽는 시점에 거른다.
		assertNull(raw("""
				{ "type": "damage_taken_from", "multiplier": 1.5, "sources": ["minecraft:lava"] }
				""", OnKillEffect.nestedIndex(0, 0)));
	}

	// ------------------------------------------------------------------ 조건 없는 배율은 없다

	@Test
	void 피해원을_모르는_자리에서는_배율이_걸리지_않는다() {
		DamageTakenFromEffect effect = create("""
				{ "type": "damage_taken_from", "multiplier": 1.5,
				  "sources": ["minecraft:lava"] }
				""");

		// 이 자리에서 1.5 를 돌려주면 낙하·익사 같은 다른 피해에까지 배율이 걸린다.
		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
		assertEquals(1.0, effect.multiplierFor(null), 1.0e-9);
		assertFalse(effect.matches(null));
	}

	@Test
	void 팀이나_피해원이_없으면_배율은_1_이다() {
		assertEquals(1.0, PerkDamage.takenSourceMultiplier(null, null), 1.0e-9);
		assertEquals(1.0, PerkDamage.takenSourceMultiplier(TeamState.fresh(20.0F), null), 1.0e-9);
	}

	// ------------------------------------------------------------------ 도우미

	private static DamageTakenFromEffect create(String json) {
		return assertInstanceOf(DamageTakenFromEffect.class, raw(json));
	}

	private static PerkEffect raw(String json) {
		return raw(json, 0);
	}

	private static PerkEffect raw(String json, int index) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.DAMAGE_TAKEN_FROM.create("sharedfate:테스트", index, parsed);
	}
}
