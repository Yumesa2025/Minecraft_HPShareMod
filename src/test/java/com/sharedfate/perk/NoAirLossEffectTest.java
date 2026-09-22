package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoAirLossEffect;
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
 * {@code no_air_loss} 의 정의 읽기와 팀 조회를 본다.
 *
 * <p>산소가 실제로 안 줄어드는 자리는 {@code LivingEntityAirSupplyMixin} 이고, 그쪽은 살아
 * 있는 서버 없이는 돌릴 수 없다. 여기서 보는 것은 <b>그 믹스인이 매 틱 물어보는 한 줄</b>인
 * {@link NoAirLossEffect#heldBy(TeamState)} 다. 믹스인이 무는 바닐라 자리는
 * {@link AirSupplyTargetTest} 가 따로 못박는다.
 */
class NoAirLossEffectTest {

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
	void 필드가_없어도_읽힌다() {
		PerkEffect effect = raw("{ \"type\": \"no_air_loss\" }");

		assertInstanceOf(NoAirLossEffect.class, effect);
		assertSame(NoAirLossEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 모르는_필드는_그냥_무시한다() {
		// 표시 효과라 읽을 것이 없다. 남는 필드가 있다고 정의를 버리면 오히려 불편하다.
		assertSame(NoAirLossEffect.INSTANCE,
				raw("{ \"type\": \"no_air_loss\", \"seconds\": 30 }"));
	}

	// ------------------------------------------------------------------ 팀 조회

	@Test
	void 가진_팀만_산소가_버틴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertTrue(NoAirLossEffect.heldBy(teamWith("sharedfate:gills")));
		assertFalse(NoAirLossEffect.heldBy(teamWith("sharedfate:plain")));
	}

	@Test
	void 팀이_없거나_증강이_없으면_그대로_닳는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoAirLossEffect.heldBy(null));

		TeamState empty = TeamState.fresh(20.0F);
		empty.perksEnabled = true;
		assertFalse(NoAirLossEffect.heldBy(empty), "아직 아무 증강도 없다");
	}

	@Test
	void 증강을_끈_팀은_그대로_닳는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = teamWith("sharedfate:gills");
		state.perksEnabled = false;

		assertFalse(NoAirLossEffect.heldBy(state), "증강을 끄면 이득도 함께 멈춰야 한다");
	}

	@Test
	void 풀에_없는_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoAirLossEffect.heldBy(teamWith("sharedfate:missing")));
	}

	/**
	 * 조건 안에 숨어 있는 {@code no_air_loss} 는 세어 주지 않는다.
	 *
	 * <p>{@link NoAirLossEffect#heldBy} 는 증강의 <b>맨 위 효과 목록</b>만 훑는다. 물속에서만
	 * 켜지는 효과처럼 보여서 {@code conditional} 안에 넣고 싶어지지만, 그러면 조회가 못 찾아
	 * 아무 일도 일어나지 않는다. 애초에 산소가 닳는 자리 자체가 물속뿐이라 조건이 필요 없다.
	 */
	@Test
	void 조건_안에_넣으면_찾지_못한다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoAirLossEffect.heldBy(teamWith("sharedfate:nested")),
				"conditional 안에 넣으면 조용히 죽는다 — 맨 위에 두어야 한다");
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.NO_AIR_LOSS.create("sharedfate:테스트", 0, parsed);
	}

	private static TeamState teamWith(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:gills", "rarity": "silver", "name": "아가미",
				      "effects": [ { "type": "no_air_loss" } ] },
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] },
				    { "id": "sharedfate:nested", "rarity": "silver", "name": "조건에 숨긴 증강",
				      "effects": [ { "type": "conditional", "condition": "in_water",
				        "when_true": [ { "type": "no_air_loss" } ] } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
