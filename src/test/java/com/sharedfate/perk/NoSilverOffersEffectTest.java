package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoSilverOffersEffect;
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
 * {@code no_silver_offers} 의 정의 읽기와 팀 조회를 본다.
 *
 * <p>실제로 실버가 후보에서 빠지는 자리는 {@link PerkDraft} 이고, 그쪽은 호출자가 넘긴 플래그만
 * 본다. 여기서 보는 것은 <b>그 플래그를 만드는 한 줄</b>인
 * {@link NoSilverOffersEffect#heldBy(TeamState)} 다.
 */
class NoSilverOffersEffectTest {

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
		PerkEffect effect = raw("{ \"type\": \"no_silver_offers\" }");

		assertInstanceOf(NoSilverOffersEffect.class, effect);
		assertSame(NoSilverOffersEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 모르는_필드는_그냥_무시한다() {
		// 표시 효과라 읽을 것이 없다. 남는 필드가 있다고 정의를 버리면 오히려 불편하다.
		assertSame(NoSilverOffersEffect.INSTANCE,
				raw("{ \"type\": \"no_silver_offers\", \"rarity\": \"silver\" }"));
	}

	// ------------------------------------------------------------------ 팀 조회

	@Test
	void 가진_팀만_실버가_막힌다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertTrue(NoSilverOffersEffect.heldBy(teamWith("sharedfate:expedition")));
		assertFalse(NoSilverOffersEffect.heldBy(teamWith("sharedfate:plain")));
	}

	@Test
	void 팀이_없거나_증강이_없으면_막히지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSilverOffersEffect.heldBy(null));

		TeamState empty = TeamState.fresh(20.0F);
		empty.perksEnabled = true;
		assertFalse(NoSilverOffersEffect.heldBy(empty), "아직 아무 증강도 없다");
	}

	@Test
	void 증강을_끈_팀은_막히지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = teamWith("sharedfate:expedition");
		state.perksEnabled = false;

		assertFalse(NoSilverOffersEffect.heldBy(state), "증강을 끄면 대가도 함께 멈춰야 한다");
	}

	@Test
	void 풀에_없는_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSilverOffersEffect.heldBy(teamWith("sharedfate:missing")));
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.NO_SILVER_OFFERS.create("sharedfate:테스트", 0, parsed);
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
				    { "id": "sharedfate:expedition", "rarity": "silver", "name": "원정 준비물",
				      "effects": [ { "type": "no_silver_offers" } ] },
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
