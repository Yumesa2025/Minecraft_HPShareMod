package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoFoodHungerEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code no_food_hunger} 의 정의 읽기와, 먹기를 막을지 정하는 판정을 본다.
 *
 * <p>실제로 영양 섭취를 건너뛰는 자리는 {@code FoodPropertiesMixin} 이고 그건 살아 있는
 * 서버가 있어야 확인할 수 있다. 여기서는 그 mixin 이 물어보는 질문
 * ({@link PerkFoodRules#blocks})만 확인한다.
 */
class NoFoodHungerEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 필드가_없어도_읽힌다() {
		JsonObject json = JsonParser.parseString("{ \"type\": \"no_food_hunger\" }").getAsJsonObject();

		PerkEffect effect = PerkEffectType.NO_FOOD_HUNGER.create("sharedfate:테스트", 0, json);

		assertInstanceOf(NoFoodHungerEffect.class, effect);
		assertSame(NoFoodHungerEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 증강이_없으면_먹기를_막지_않는다() {
		assertFalse(PerkFoodRules.blocks(null));
		assertFalse(PerkFoodRules.blocks(TeamState.fresh(20.0F)));
	}

	@Test
	void 이_효과를_가진_증강이_있으면_막는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(devourPool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:devour");

		assertTrue(PerkFoodRules.blocks(state));
	}

	@Test
	void 다른_증강만_있으면_막지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(devourPool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:hunter_meal");

		assertFalse(PerkFoodRules.blocks(state), "on_kill 만 가진 팀은 평소처럼 먹는다");
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(devourPool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:사라진것");

		assertFalse(PerkFoodRules.blocks(state));
	}

	/** 포식(on_kill + no_food_hunger)과 사냥꾼의 식사(on_kill 만)를 담은 증강 풀. */
	private static Path devourPool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:devour", "rarity": "prism", "name": "포식",
				      "effects": [
				        { "type": "on_kill", "food": 1, "health": 2.0 },
				        { "type": "no_food_hunger" }
				      ] },
				    { "id": "sharedfate:hunter_meal", "rarity": "silver", "name": "사냥꾼의 식사",
				      "effects": [
				        { "type": "on_kill", "food": 2, "saturation": 1.0 },
				        { "type": "damage_taken", "multiplier": 1.2 }
				      ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
