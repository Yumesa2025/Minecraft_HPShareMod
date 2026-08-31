package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.RarityRerollEffect;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rarity_reroll}(프리즘 「환골탈태」)의 정의 읽기와 즉시 개편을 본다.
 *
 * <p>{@code rarity_grant}(숨은 재능·하늘의 은총)와 정반대다 — 더하는 대신 지금 가진 것
 * 전부(자기 자신 포함)를 지우고 같은 수만큼 지정 등급으로 다시 채운다. 다만 이 증강 자신의
 * id는 "무엇을 골랐었는가"의 기록으로 {@code ownedPerks}에 그대로 남는다.
 */
class RarityRerollEffectTest {

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
	void 등급을_읽는다() {
		RarityRerollEffect effect = assertInstanceOf(RarityRerollEffect.class,
				create("{ \"type\": \"rarity_reroll\", \"rarity\": \"gold\" }"));

		assertEquals(PerkRarity.GOLD, effect.rarity());
	}

	@Test
	void 알_수_없는_등급이면_버린다() {
		assertNull(create("{ \"type\": \"rarity_reroll\", \"rarity\": \"legendary\" }"));
		assertNull(create("{ \"type\": \"rarity_reroll\" }"));
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.RARITY_REROLL, PerkEffectType.fromId("rarity_reroll"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		RarityRerollEffect effect = assertInstanceOf(RarityRerollEffect.class,
				create("{ \"type\": \"rarity_reroll\", \"rarity\": \"gold\" }"));

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	// ------------------------------------------------------------------ 즉시 개편

	@Test
	void 가진_증강_전부가_지정_등급_같은_수로_바뀐다(@TempDir Path dir) throws IOException {
		// old_gold 는 owned 로 넣지 않는다 — 재추첨 풀은 owned 를 안 가리므로(전부 지운 뒤
		// 다시 뽑는 것이라) 원래 안 가졌던 골드도 그대로 후보가 되고, 그걸 없다고 단정하면
		// 시험이 우연히 실패할 수 있다. 여기서는 "골드가 아닌 것은 절대 다시 안 나온다"만 본다.
		loadPool(dir, 4);
		Perk reroll = PerkRegistry.byId("sharedfate:golden_rebirth").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:old_silver");
		state.ownedPerks.add("sharedfate:old_prism2");
		state.ownedPerks.add(reroll.id());
		// commit 이 이미 넣어 둔 상태와 같다: 자기 자신 포함 3개.

		int granted = PerkRarityReroll.rerollOnChoice(
				null, null, state, reroll, RandomSource.create(1L));

		assertEquals(3, granted, "가진 3개(자기 자신 포함)만큼 새로 채워야 한다");
		assertEquals(4, state.ownedPerks.size(), "환골탈태 자신(기록) + 새로 받은 골드 3개");
		assertTrue(state.ownedPerks.contains(reroll.id()), "자기 자신은 기록으로 남는다");
		assertFalse(state.ownedPerks.contains("sharedfate:old_silver"),
				"실버는 골드 풀에 없으니 다시 나올 수 없다");
		assertFalse(state.ownedPerks.contains("sharedfate:old_prism2"),
				"프리즘도 골드 풀에 없으니 다시 나올 수 없다");

		Set<String> newGolds = new HashSet<>(state.ownedPerks);
		newGolds.remove(reroll.id());
		assertEquals(3, newGolds.size(), "중복 없이 세 개여야 한다");
		for (String id : newGolds) {
			Perk perk = PerkRegistry.byId(id).orElseThrow();
			assertEquals(PerkRarity.GOLD, perk.rarity(), "새로 받은 것은 전부 골드여야 한다");
		}
	}

	@Test
	void 자기_자신은_기록으로만_남고_재적용해도_안전하다(@TempDir Path dir) throws IOException {
		loadPool(dir, 4);
		Perk reroll = PerkRegistry.byId("sharedfate:golden_rebirth").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(reroll.id());

		PerkRarityReroll.rerollOnChoice(null, null, state, reroll, RandomSource.create(2L));

		assertTrue(state.ownedPerks.contains(reroll.id()));
		// PerkManager.refreshPlayer 가 목록을 훑으며 매번 apply 를 다시 부르는 것과 같은 상황.
		// RarityRerollEffect 는 apply/remove 를 재정의하지 않으므로 아무 일도 하지 않는다.
		for (var effect : reroll.effects()) {
			assertDoesNotThrow(() -> effect.apply(null));
		}
	}

	@Test
	void 목표_등급_후보가_가진_것보다_적으면_있는_만큼만_준다(@TempDir Path dir) throws IOException {
		// 골드 후보를 "old_gold" 하나 + gold_0 하나 = 2개로 제한한다(loadPool(dir, 1)).
		loadPool(dir, 1);
		Perk reroll = PerkRegistry.byId("sharedfate:golden_rebirth").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:old_silver");
		state.ownedPerks.add("sharedfate:old_prism2");
		state.ownedPerks.add(reroll.id());
		// 가진 것 3개(자기 자신 포함)인데 골드 후보는 2개뿐이다.

		int granted = PerkRarityReroll.rerollOnChoice(
				null, null, state, reroll, RandomSource.create(3L));

		assertEquals(2, granted, "골드 후보가 2개뿐이니 그만큼만 준다(중복 없이 뽑을 수 있는 상한)");
		assertEquals(3, state.ownedPerks.size(), "환골탈태 자신 + 골드 2개");
	}

	@Test
	void 뽑을_후보가_없으면_아무것도_손대지_않는다(@TempDir Path dir) throws IOException {
		write(dir, """
				{ "perks": [
				  { "id": "sharedfate:golden_rebirth", "rarity": "prism", "name": "환골탈태",
				    "effects": [ { "type": "rarity_reroll", "rarity": "gold" } ] }
				] }
				""");
		PerkRegistry.load(dir);
		Perk reroll = PerkRegistry.byId("sharedfate:golden_rebirth").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(reroll.id());

		int granted = PerkRarityReroll.rerollOnChoice(
				null, null, state, reroll, RandomSource.create(1L));

		assertEquals(0, granted);
		assertEquals(List.of(reroll.id()), state.ownedPerks, "골드 후보가 없으면 목록을 그대로 둔다");
	}

	@Test
	void rarity_reroll_이_없는_증강은_아무것도_바꾸지_않는다(@TempDir Path dir) throws IOException {
		loadPool(dir, 4);
		Perk notReroll = PerkRegistry.byId("sharedfate:old_gold").orElseThrow();
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(notReroll.id());

		int granted = PerkRarityReroll.rerollOnChoice(
				null, null, state, notReroll, RandomSource.create(1L));

		assertEquals(0, granted);
		assertEquals(List.of(notReroll.id()), state.ownedPerks);
	}

	@Test
	void 인자가_비어도_터지지_않는다() {
		assertEquals(0, PerkRarityReroll.rerollOnChoice(null, null, null, null, RandomSource.create(1L)));
		assertEquals(0, PerkRarityReroll.rerollOnChoice(
				null, null, TeamState.fresh(20.0F), null, RandomSource.create(1L)));
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect create(String json) {
		com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.RARITY_REROLL.create("sharedfate:테스트", 0, parsed);
	}

	/** 「환골탈태」+ 옛 증강 셋(실버·골드·프리즘 하나씩) + 골드 후보 {@code goldCount}개. */
	private static void loadPool(Path dir, int goldCount) throws IOException {
		StringBuilder golds = new StringBuilder();
		for (int i = 0; i < goldCount; i++) {
			golds.append("""
					,
					    { "id": "sharedfate:gold_%d", "rarity": "gold", "name": "골드%d",
					      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
					""".formatted(i, i));
		}
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:golden_rebirth", "rarity": "prism", "name": "환골탈태",
				      "min_level": 30,
				      "effects": [ { "type": "rarity_reroll", "rarity": "gold" } ] },
				    { "id": "sharedfate:old_silver", "rarity": "silver", "name": "옛실버",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:old_gold", "rarity": "gold", "name": "옛골드",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:old_prism2", "rarity": "prism", "name": "옛프리즘",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				    %s
				  ]
				}
				""".formatted(golds));
		PerkRegistry.load(dir);
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
