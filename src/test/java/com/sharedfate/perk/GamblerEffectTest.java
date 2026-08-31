package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.GamblerEffect;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code gambler}(프리즘 「도박꾼」)의 정의 읽기, 즉시 지급, 20·25 구간 실버 고정을 본다.
 *
 * <p>실제로 화면에 보이는지는 {@code PerkManager.commit}이 이미 부르는
 * {@code broadcastSync}(모든 {@code ownedPerks}를 훑음) 하나로 해결되므로 새 동기화 경로가
 * 없다 — 여기서는 {@link PerkGambler}가 {@code ownedPerks}에 정확히 넣는지만 본다.
 */
class GamblerEffectTest {

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
	void 필드가_없고_인스턴스를_돌려쓴다() {
		PerkEffect first = create("{ \"type\": \"gambler\" }");
		PerkEffect second = create("{ \"type\": \"gambler\" }");

		assertSame(GamblerEffect.INSTANCE, first);
		assertSame(first, second);
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.GAMBLER, PerkEffectType.fromId("gambler"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		GamblerEffect effect = assertInstanceOf(GamblerEffect.class, create("{ \"type\": \"gambler\" }"));

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	// ------------------------------------------------------------------ 20·25 구간 고정

	@Test
	void 도박꾼이_없으면_구간이_고정되지_않는다() {
		TeamState state = TeamState.fresh(20.0F);

		assertNull(PerkGambler.forcedRarity(state, 20));
		assertNull(PerkGambler.forcedRarity(state, 25));
		assertNull(PerkGambler.forcedRarity(null, 20));
	}

	@Test
	void 도박꾼을_가지면_20과_25만_실버로_고정된다(@TempDir Path dir) throws IOException {
		loadGamblerPool(dir);
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:gambler");

		assertEquals(PerkRarity.SILVER, PerkGambler.forcedRarity(state, 20));
		assertEquals(PerkRarity.SILVER, PerkGambler.forcedRarity(state, 25));
		assertNull(PerkGambler.forcedRarity(state, 5), "다른 구간까지 고정하면 안 된다");
		assertNull(PerkGambler.forcedRarity(state, 10));
		assertNull(PerkGambler.forcedRarity(state, 15));
		assertNull(PerkGambler.forcedRarity(state, 30));
		assertNull(PerkGambler.forcedRarity(state, 35));
	}

	@Test
	void 풀에서_사라진_도박꾼_id_는_고정을_걸지_않는다(@TempDir Path dir) throws IOException {
		write(dir, "{ \"perks\": [] }");
		PerkRegistry.load(dir);
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:사라진도박꾼");

		assertNull(PerkGambler.forcedRarity(state, 20));
	}

	// ------------------------------------------------------------------ 즉시 지급

	@Test
	void 등급_상관없이_두_개를_더_준다(@TempDir Path dir) throws IOException {
		loadGamblerPool(dir);
		Perk gambler = PerkRegistry.byId("sharedfate:gambler").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(gambler.id());

		int granted = PerkGambler.grantOnChoice(null, null, state, gambler, RandomSource.create(1L));

		assertEquals(2, granted);
		assertEquals(3, state.ownedPerks.size(), "도박꾼 자신 + 새로 받은 2개");
	}

	@Test
	void 자기_자신과_이미_가진_것은_다시_뽑히지_않는다(@TempDir Path dir) throws IOException {
		loadGamblerPool(dir);
		Perk gambler = PerkRegistry.byId("sharedfate:gambler").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(gambler.id());
		state.ownedPerks.add("sharedfate:a");
		state.ownedPerks.add("sharedfate:b");
		state.ownedPerks.add("sharedfate:c");
		// 이제 남은 후보는 sharedfate:d, sharedfate:e, sharedfate:prism_extra 뿐이다.

		PerkGambler.grantOnChoice(null, null, state, gambler, RandomSource.create(42L));

		Set<String> owned = new HashSet<>(state.ownedPerks);
		assertEquals(6, owned.size(), "중복 없이 정확히 6개(도박꾼+기존 3+신규 2)여야 한다");
		assertEquals(1, state.ownedPerks.stream().filter(id -> id.equals(gambler.id())).count(),
				"도박꾼 자신은 정확히 한 번만 있어야 한다");
	}

	@Test
	void 받을_수_있는_것이_2개_미만이면_있는_만큼만_준다(@TempDir Path dir) throws IOException {
		write(dir, """
				{ "perks": [
				  { "id": "sharedfate:gambler", "rarity": "prism", "name": "도박꾼",
				    "effects": [ { "type": "gambler" } ] },
				  { "id": "sharedfate:only_one", "rarity": "silver", "name": "하나뿐",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				] }
				""");
		PerkRegistry.load(dir);
		Perk gambler = PerkRegistry.byId("sharedfate:gambler").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(gambler.id());

		int granted = PerkGambler.grantOnChoice(null, null, state, gambler, RandomSource.create(7L));

		assertEquals(1, granted);
		assertTrue(state.ownedPerks.contains("sharedfate:only_one"));
	}

	@Test
	void gambler_가_없는_증강은_아무것도_더_주지_않는다(@TempDir Path dir) throws IOException {
		loadGamblerPool(dir);
		Perk notGambler = PerkRegistry.byId("sharedfate:a").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(notGambler.id());

		int granted = PerkGambler.grantOnChoice(null, null, state, notGambler, RandomSource.create(1L));

		assertEquals(0, granted);
		assertEquals(1, state.ownedPerks.size());
	}

	@Test
	void 인자가_비어도_터지지_않는다() {
		assertEquals(0, PerkGambler.grantOnChoice(null, null, null, null, RandomSource.create(1L)));
		assertEquals(0, PerkGambler.grantOnChoice(null, null, TeamState.fresh(20.0F), null, RandomSource.create(1L)));
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect create(String json) {
		com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.GAMBLER.create("sharedfate:테스트", 0, parsed);
	}

	/** 도박꾼 하나 + 뽑힐 수 있는 후보 다섯(프리즘 하나 포함). */
	private static void loadGamblerPool(Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:gambler", "rarity": "prism", "name": "도박꾼",
				      "effects": [ { "type": "gambler" } ] },
				    { "id": "sharedfate:a", "rarity": "silver", "name": "가",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:b", "rarity": "silver", "name": "나",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:c", "rarity": "gold", "name": "다",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:d", "rarity": "gold", "name": "라",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:e", "rarity": "prism", "name": "마",
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
