package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoDamageBoostEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code no_damage_boost}(프리즘 「삽질의 대가」의 대가)의 정의 읽기와
 * {@link PerkGearRules#hasNoDamageBoost}를 본다.
 *
 * <p>실제 상쇄 계산은 {@link PerkDamageBoostBanTest}가 다룬다. 여기서는 이 정의가 어떻게 읽히고
 * 팀이 가졌는지 판정이 맞는지만 확인한다.
 */
class NoDamageBoostEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 필드가_없고_인스턴스를_돌려쓴다() {
		PerkEffect first = create();
		PerkEffect second = create();

		assertSame(NoDamageBoostEffect.INSTANCE, first);
		assertSame(first, second);
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.NO_DAMAGE_BOOST, PerkEffectType.fromId("no_damage_boost"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		NoDamageBoostEffect effect = assertInstanceOf(NoDamageBoostEffect.class, create());

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	@Test
	void 증강이_없으면_판정이_거짓이다() {
		assertFalse(PerkGearRules.hasNoDamageBoost((com.sharedfate.team.TeamState) null));
		assertFalse(PerkGearRules.hasNoDamageBoost(com.sharedfate.team.TeamState.fresh(20.0F)));
	}

	@Test
	void 가지면_판정이_참이다(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws java.io.IOException {
		java.nio.file.Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{ "perks": [
				  { "id": "sharedfate:shovel", "rarity": "prism", "name": "삽질의 대가",
				    "effects": [
				      { "type": "weapon_damage", "tags": ["minecraft:shovels"], "multiplier": 3.0 },
				      { "type": "no_damage_boost" }
				    ] }
				] }
				""", java.nio.charset.StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		com.sharedfate.team.TeamState state = com.sharedfate.team.TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:shovel");

		assertTrue(PerkGearRules.hasNoDamageBoost(state));
	}

	private static PerkEffect create() {
		com.google.gson.JsonObject parsed = com.google.gson.JsonParser
				.parseString("{ \"type\": \"no_damage_boost\" }").getAsJsonObject();
		return PerkEffectType.NO_DAMAGE_BOOST.create("sharedfate:테스트", 0, parsed);
	}
}
