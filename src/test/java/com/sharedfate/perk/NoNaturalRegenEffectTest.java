package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoNaturalRegenEffect;
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
 * {@code no_natural_regen} 의 정의 읽기와, 자연 회복을 막을지 정하는 판정을 본다.
 *
 * <p>실제로 회복을 건너뛰는 자리는 {@code FoodDataNaturalRegenMixin} 이고 그건 살아 있는
 * 서버가 있어야 확인할 수 있다. 여기서는 그 mixin 이 물어보는 질문
 * ({@link PerkRegenRules#blocks})만 확인한다. {@code no_food_hunger} 와 완전히 같은 구도다.
 */
class NoNaturalRegenEffectTest {

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
		JsonObject json = JsonParser.parseString("{ \"type\": \"no_natural_regen\" }").getAsJsonObject();

		PerkEffect effect = PerkEffectType.NO_NATURAL_REGEN.create("sharedfate:테스트", 0, json);

		assertInstanceOf(NoNaturalRegenEffect.class, effect);
		assertSame(NoNaturalRegenEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 증강이_없으면_자연_회복을_막지_않는다() {
		// 증강 풀이 비어 있으면 바닐라와 100% 같아야 한다.
		assertFalse(PerkRegenRules.blocks(null));
		assertFalse(PerkRegenRules.blocks(TeamState.fresh(20.0F)));
	}

	@Test
	void 이_효과를_가진_증강이_있으면_막는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:vampire");

		assertTrue(PerkRegenRules.blocks(state));
	}

	@Test
	void 다른_증강만_있으면_막지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:unrelated");

		assertFalse(PerkRegenRules.blocks(state));
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:사라진것");

		assertFalse(PerkRegenRules.blocks(state));
	}

	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:vampire", "rarity": "prism", "name": "흡혈귀",
				      "effects": [
				        { "type": "lifesteal", "fraction": 0.15 },
				        { "type": "no_natural_regen" }
				      ] },
				    { "id": "sharedfate:unrelated", "rarity": "silver", "name": "상관없음",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
