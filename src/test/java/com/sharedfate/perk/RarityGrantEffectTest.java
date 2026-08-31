package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.RarityGrantEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rarity_grant}(실버 「숨은 재능」, 골드 「하늘의 은총」)의 정의 읽기와 즉시 지급을 본다.
 *
 * <p>{@code gambler}(도박꾼)를 등급 지정으로 일반화한 것이라 {@link GamblerEffectTest}와 같은
 * 방식으로 확인한다. 등급 무관 20·25 구간 고정 같은 부작용이 없으므로 여기서는 다루지 않는다.
 */
class RarityGrantEffectTest {

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
	void 등급과_개수를_읽는다() {
		RarityGrantEffect effect = assertInstanceOf(RarityGrantEffect.class,
				create("{ \"type\": \"rarity_grant\", \"rarity\": \"gold\", \"count\": 1 }"));

		assertEquals(PerkRarity.GOLD, effect.rarity());
		assertEquals(1, effect.count());
	}

	@Test
	void count_을_안_적으면_1_이다() {
		RarityGrantEffect effect = assertInstanceOf(RarityGrantEffect.class,
				create("{ \"type\": \"rarity_grant\", \"rarity\": \"prism\" }"));

		assertEquals(1, effect.count());
	}

	@Test
	void 알_수_없는_등급이면_버린다() {
		assertNull(create("{ \"type\": \"rarity_grant\", \"rarity\": \"legendary\" }"));
		assertNull(create("{ \"type\": \"rarity_grant\" }"));
	}

	@Test
	void count_이_범위를_벗어나면_버린다() {
		assertNull(create("{ \"type\": \"rarity_grant\", \"rarity\": \"gold\", \"count\": 0 }"));
		assertNull(create("{ \"type\": \"rarity_grant\", \"rarity\": \"gold\", \"count\": 4 }"));
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		org.junit.jupiter.api.Assertions.assertSame(
				PerkEffectType.RARITY_GRANT, PerkEffectType.fromId("rarity_grant"));
	}

	// ------------------------------------------------------------------ 즉시 지급

	@Test
	void 지정한_등급에서만_뽑는다(@TempDir Path dir) throws IOException {
		loadPool(dir);
		Perk hiddenTalent = PerkRegistry.byId("sharedfate:talent").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(hiddenTalent.id());

		int granted = PerkRarityGrant.grantOnChoice(
				null, null, state, hiddenTalent, RandomSource.create(1L));

		assertEquals(1, granted);
		assertEquals(2, state.ownedPerks.size(), "숨은 재능 자신 + 새로 받은 골드 1개");
		String newId = state.ownedPerks.get(1);
		Perk newPerk = PerkRegistry.byId(newId).orElseThrow();
		assertEquals(PerkRarity.GOLD, newPerk.rarity(), "골드에서만 뽑혀야 한다");
	}

	@Test
	void 이미_가진_것과_자기_자신은_다시_뽑히지_않는다(@TempDir Path dir) throws IOException {
		loadPool(dir);
		Perk hiddenTalent = PerkRegistry.byId("sharedfate:talent").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(hiddenTalent.id());
		state.ownedPerks.add("sharedfate:gold_a");
		// 남은 골드 후보는 sharedfate:gold_b 하나뿐이다.

		PerkRarityGrant.grantOnChoice(null, null, state, hiddenTalent, RandomSource.create(2L));

		Set<String> owned = new HashSet<>(state.ownedPerks);
		assertEquals(3, owned.size());
		assertTrue(owned.contains("sharedfate:gold_b"));
	}

	@Test
	void 뽑을_후보가_없으면_아무것도_주지_않는다(@TempDir Path dir) throws IOException {
		write(dir, """
				{ "perks": [
				  { "id": "sharedfate:talent", "rarity": "silver", "name": "숨은 재능",
				    "effects": [ { "type": "rarity_grant", "rarity": "gold", "count": 1 } ] }
				] }
				""");
		PerkRegistry.load(dir);
		Perk hiddenTalent = PerkRegistry.byId("sharedfate:talent").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(hiddenTalent.id());

		int granted = PerkRarityGrant.grantOnChoice(
				null, null, state, hiddenTalent, RandomSource.create(1L));

		assertEquals(0, granted);
		assertEquals(1, state.ownedPerks.size());
	}

	@Test
	void rarity_grant_가_없는_증강은_아무것도_더_주지_않는다(@TempDir Path dir) throws IOException {
		loadPool(dir);
		Perk goldA = PerkRegistry.byId("sharedfate:gold_a").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(goldA.id());

		int granted = PerkRarityGrant.grantOnChoice(null, null, state, goldA, RandomSource.create(1L));

		assertEquals(0, granted);
		assertFalse(state.ownedPerks.size() > 1);
	}

	@Test
	void 인자가_비어도_터지지_않는다() {
		assertEquals(0, PerkRarityGrant.grantOnChoice(null, null, null, null, RandomSource.create(1L)));
		assertEquals(0, PerkRarityGrant.grantOnChoice(
				null, null, TeamState.fresh(20.0F), null, RandomSource.create(1L)));
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect create(String json) {
		com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.RARITY_GRANT.create("sharedfate:테스트", 0, parsed);
	}

	/** 「숨은 재능」(실버, 골드 지정) + 골드 후보 둘 + 프리즘 후보 하나. */
	private static void loadPool(Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:talent", "rarity": "silver", "name": "숨은 재능",
				      "effects": [ { "type": "rarity_grant", "rarity": "gold", "count": 1 } ] },
				    { "id": "sharedfate:gold_a", "rarity": "gold", "name": "골드가",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:gold_b", "rarity": "gold", "name": "골드나",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:prism_c", "rarity": "prism", "name": "프리즘다",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
