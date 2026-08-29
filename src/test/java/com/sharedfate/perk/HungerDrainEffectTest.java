package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.HungerDrainEffect;
import com.sharedfate.perk.effect.NoHungerDrainEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code hunger_drain} 과 {@code no_hunger_drain} 의 정의 읽기와 배율 계산을 본다.
 *
 * <p>배율을 실제로 곱하는 자리는 {@code PlayerSharedExhaustionMixin} 이고 그건 살아 있는
 * 서버가 있어야 확인할 수 있다. 여기서는 그 mixin 이 물어보는 질문
 * ({@link PerkFoodRules#exhaustionMultiplier})과 곱셈 규칙만 확인한다.
 */
class HungerDrainEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 배율을_읽는다() {
		JsonObject json = JsonParser.parseString(
				"{ \"type\": \"hunger_drain\", \"multiplier\": 2.0 }").getAsJsonObject();

		PerkEffect effect = PerkEffectType.HUNGER_DRAIN.create("sharedfate:테스트", 0, json);

		assertInstanceOf(HungerDrainEffect.class, effect);
		assertEquals(2.0, ((HungerDrainEffect) effect).multiplier());
	}

	@Test
	void 배율이_없거나_범위를_벗어나면_버린다() {
		assertNull(PerkEffectType.HUNGER_DRAIN.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"hunger_drain\" }").getAsJsonObject()));
		assertNull(PerkEffectType.HUNGER_DRAIN.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"hunger_drain\", \"multiplier\": -1.0 }")
						.getAsJsonObject()));
		assertNull(PerkEffectType.HUNGER_DRAIN.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"hunger_drain\", \"multiplier\": 999.0 }")
						.getAsJsonObject()));
	}

	@Test
	void 배율_1_은_아무것도_하지_않으므로_버린다() {
		assertNull(PerkEffectType.HUNGER_DRAIN.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"hunger_drain\", \"multiplier\": 1.0 }")
						.getAsJsonObject()));
	}

	@Test
	void 소모없음은_필드가_없어도_읽힌다() {
		PerkEffect effect = PerkEffectType.NO_HUNGER_DRAIN.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"no_hunger_drain\" }").getAsJsonObject());

		assertInstanceOf(NoHungerDrainEffect.class, effect);
		assertSame(NoHungerDrainEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 증강이_없으면_배율이_1_이다() {
		assertEquals(1.0, PerkFoodRules.exhaustionMultiplier(null));
		assertEquals(1.0, PerkFoodRules.exhaustionMultiplier(TeamState.fresh(20.0F)));
	}

	@Test
	void 가진_배율을_모두_곱한다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:식비");
		assertEquals(2.0, PerkFoodRules.exhaustionMultiplier(state));

		state.ownedPerks.add("sharedfate:절약");
		assertEquals(1.0, PerkFoodRules.exhaustionMultiplier(state), "2.0 × 0.5");
	}

	@Test
	void 소모없음이_있으면_다른_배율과_무관하게_0_이다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:식비");
		state.ownedPerks.add("sharedfate:고행자");

		assertEquals(0.0, PerkFoodRules.exhaustionMultiplier(state));
	}

	@Test
	void 다른_증강만_있으면_배율이_1_이다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:무관");

		assertEquals(1.0, PerkFoodRules.exhaustionMultiplier(state));
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:사라진것");

		assertEquals(1.0, PerkFoodRules.exhaustionMultiplier(state));
	}

	@Test
	void 배율_1_이면_소모도가_비트_하나도_달라지지_않는다() {
		float exhaustion = 0.30000001F;

		assertEquals(exhaustion, PerkFoodRules.applyExhaustionMultiplier(1.0, exhaustion));
	}

	@Test
	void 배율을_소모도에_곱한다() {
		assertEquals(0.1F, PerkFoodRules.applyExhaustionMultiplier(2.0, 0.05F), 1.0e-6F);
		assertEquals(0.0F, PerkFoodRules.applyExhaustionMultiplier(0.0, 0.05F));
	}

	/** 허기 소모를 건드리는 증강 셋과, 건드리지 않는 증강 하나를 담은 풀. */
	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:식비", "rarity": "silver", "name": "무한 식비",
				      "effects": [ { "type": "hunger_drain", "multiplier": 2.0 } ] },
				    { "id": "sharedfate:절약", "rarity": "silver", "name": "절약",
				      "effects": [ { "type": "hunger_drain", "multiplier": 0.5 } ] },
				    { "id": "sharedfate:고행자", "rarity": "prism", "name": "고행자",
				      "effects": [ { "type": "no_hunger_drain" } ] },
				    { "id": "sharedfate:무관", "rarity": "silver", "name": "무관",
				      "effects": [ { "type": "damage_taken", "multiplier": 1.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
