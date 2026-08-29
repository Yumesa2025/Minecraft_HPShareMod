package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.LifestealEffect;
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

/**
 * {@code lifesteal} 의 정의 읽기와, 준 피해가 팀 공유 체력을 얼마나 채우는지를 본다.
 *
 * <p>실제로 피해가 들어가는 순간을 잡는 부분({@code AFTER_DAMAGE} 등록, 가해자·피해자 판별)은
 * 살아 있는 서버와 월드가 있어야 하므로 여기서는 다루지 않는다. 대신 그 코드가 부르는 계산
 * ({@link PerkLifesteal#healingFor}, {@link PerkLifesteal#applyToPool})을 모두 확인한다.
 */
class LifestealEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkLifesteal.resetForTesting();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 비율을_읽는다() {
		LifestealEffect effect = assertInstanceOf(LifestealEffect.class,
				create("{ \"type\": \"lifesteal\", \"fraction\": 0.15 }"));

		assertEquals(0.15, effect.fraction(), 1.0e-9);
		assertEquals(0.15, effect.fractionFor(), 1.0e-9);
	}

	@Test
	void 비율이_없거나_범위를_벗어나면_버린다() {
		assertNull(create("{ \"type\": \"lifesteal\" }"));
		assertNull(create("{ \"type\": \"lifesteal\", \"fraction\": 0 }"));
		assertNull(create("{ \"type\": \"lifesteal\", \"fraction\": -0.2 }"));
		assertNull(create("{ \"type\": \"lifesteal\", \"fraction\": 1.5 }"),
				"준 피해보다 많이 돌려받을 수는 없다");
		assertNull(create("{ \"type\": \"lifesteal\", \"fraction\": \"조금\" }"));
	}

	@Test
	void 생성자로_말도_안_되는_값이_들어와도_상한에서_멈춘다() {
		// JSON 경로는 이미 범위를 검사한다. 여기서 보는 것은 Java 쪽에서 직접 만든 경우다.
		assertEquals(1.0, new LifestealEffect(99.0).fractionFor(), 1.0e-9);
		assertEquals(0.0, new LifestealEffect(-1.0).fractionFor(), 1.0e-9);
		assertEquals(0.0, new LifestealEffect(Double.NaN).fractionFor(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 회복량 계산

	@Test
	void 증강이_없으면_아무것도_돌려주지_않는다() {
		assertEquals(0.0F, PerkLifesteal.healingFor(null, 10.0F));
		assertEquals(0.0F, PerkLifesteal.healingFor(TeamState.fresh(20.0F), 10.0F));
	}

	@Test
	void 준_피해의_비율만큼_돌려준다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(vampirePool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:vampire");

		assertEquals(1.5F, PerkLifesteal.healingFor(state, 10.0F), 1.0e-4F);
	}

	@Test
	void 흡혈_증강을_여러_개_가지면_비율을_더한다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(vampirePool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:vampire");
		state.ownedPerks.add("sharedfate:leech");
		state.ownedPerks.add("sharedfate:unrelated");

		assertEquals(3.5F, PerkLifesteal.healingFor(state, 10.0F), 1.0e-4F);
	}

	@Test
	void 피해가_0_이하면_아무것도_돌려주지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(vampirePool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:vampire");

		assertEquals(0.0F, PerkLifesteal.healingFor(state, 0.0F));
		assertEquals(0.0F, PerkLifesteal.healingFor(state, -5.0F));
		assertEquals(0.0F, PerkLifesteal.healingFor(state, Float.NaN));
	}

	@Test
	void 한_번에_돌려받는_양에는_상한이_있다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(vampirePool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:vampire");

		assertEquals(PerkLifesteal.MAX_HEAL_PER_HIT,
				PerkLifesteal.healingFor(state, Float.MAX_VALUE));
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), "{ \"perks\": [] }",
				StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:사라진것");

		assertEquals(0.0F, PerkLifesteal.healingFor(state, 10.0F));
	}

	// ------------------------------------------------------------------ 공유 풀 반영

	@Test
	void 회복은_팀_공유_값에_한_번만_더해진다() {
		// 개인 체력을 올리면 StatMirror 가 그 변화를 관측해 공유 풀에 한 번 더 더한다.
		// 그래서 공유 값만 직접 올린다.
		TeamState state = TeamState.fresh(20.0F);
		state.health = 10.0F;

		PerkLifesteal.applyToPool(state, 1.5F);

		assertEquals(11.5F, state.health, 1.0e-4F);
	}

	@Test
	void 팀_최대_체력을_넘지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 19.0F;

		PerkLifesteal.applyToPool(state, 10.0F);

		assertEquals(20.0F, state.health);
	}

	@Test
	void 회복량이_0이면_공유_값을_건드리지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 7.0F;

		PerkLifesteal.applyToPool(state, 0.0F);

		assertEquals(7.0F, state.health);
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.LIFESTEAL.create("sharedfate:테스트", 0, parsed);
	}

	/** 흡혈귀(lifesteal 0.15 + no_natural_regen), 거머리(0.2), 상관없는 증강 하나. */
	private static Path vampirePool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:vampire", "rarity": "prism", "name": "흡혈귀",
				      "effects": [
				        { "type": "lifesteal", "fraction": 0.15 },
				        { "type": "no_natural_regen" }
				      ] },
				    { "id": "sharedfate:leech", "rarity": "gold", "name": "거머리",
				      "effects": [ { "type": "lifesteal", "fraction": 0.2 } ] },
				    { "id": "sharedfate:unrelated", "rarity": "silver", "name": "상관없음",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
