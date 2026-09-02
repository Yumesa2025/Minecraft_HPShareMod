package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.ShieldFallImmunityEffect;
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
 * {@code shield_fall_immunity} 의 정의 읽기와 면역 판정을 본다.
 *
 * <p>실제 피해원({@code DamageSource})과 방패 자세({@code isBlocking})는 살아 있는 월드가 있어야
 * 읽을 수 있다. 그래서 그 둘을 읽는 일은 {@link PerkDamage#blocksFallDamage} 에 두고, 여기서는
 * 읽어 온 값으로 답을 내는 순수 판정({@link ShieldFallImmunityEffect#blocks})을 본다.
 */
class ShieldFallImmunityEffectTest {

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
		PerkEffect effect = raw("{ \"type\": \"shield_fall_immunity\" }");

		assertInstanceOf(ShieldFallImmunityEffect.class, effect);
		assertSame(ShieldFallImmunityEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	// ------------------------------------------------------------------ 팀 조회

	@Test
	void 가진_팀만_면역이다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertTrue(ShieldFallImmunityEffect.heldBy(teamWith("sharedfate:guard")));
		assertFalse(ShieldFallImmunityEffect.heldBy(teamWith("sharedfate:plain")));
		assertFalse(ShieldFallImmunityEffect.heldBy(teamWith("sharedfate:missing")));
		assertFalse(ShieldFallImmunityEffect.heldBy(null));
	}

	@Test
	void 증강을_끈_팀은_면역이_아니다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = teamWith("sharedfate:guard");
		state.perksEnabled = false;

		assertFalse(ShieldFallImmunityEffect.heldBy(state));
	}

	// ------------------------------------------------------------------ 면역 판정

	@Test
	void 낙하_피해를_방패로_막는_중일_때만_버린다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));
		TeamState state = teamWith("sharedfate:guard");

		assertTrue(ShieldFallImmunityEffect.blocks(state, true, true));
		assertFalse(ShieldFallImmunityEffect.blocks(state, true, false),
				"방패를 들고만 있어서는 안 되고 막는 중이어야 한다");
		assertFalse(ShieldFallImmunityEffect.blocks(state, false, true),
				"낙하가 아닌 피해까지 막으면 무적이 된다");
		assertFalse(ShieldFallImmunityEffect.blocks(state, false, false));
	}

	@Test
	void 증강이_없으면_막는_중이어도_그대로_맞는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(ShieldFallImmunityEffect.blocks(teamWith("sharedfate:plain"), true, true));
		assertFalse(ShieldFallImmunityEffect.blocks(null, true, true));
	}

	@Test
	void 피해원이나_대상이_없으면_아무것도_막지_않는다() {
		assertFalse(PerkDamage.blocksFallDamage(null, null));
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.SHIELD_FALL_IMMUNITY.create("sharedfate:테스트", 0, parsed);
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
				    { "id": "sharedfate:guard", "rarity": "silver", "name": "버티는 방패",
				      "effects": [ { "type": "shield_fall_immunity" } ] },
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
