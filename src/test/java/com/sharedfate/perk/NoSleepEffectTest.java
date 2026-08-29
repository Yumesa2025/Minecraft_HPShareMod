package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoSleepEffect;
import com.sharedfate.perk.effect.OnKillEffect;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code no_sleep} 의 정의 읽기와, 잠을 막을지 정하는 판정을 본다.
 *
 * <p>실제로 눕기를 막는 자리는 {@code EntitySleepEvents.ALLOW_SLEEPING} 이고 그건 살아 있는
 * 서버가 있어야 확인할 수 있다. 여기서는 그 이벤트가 물어보는 질문
 * ({@link PerkWorldRules#blocksSleep})만 확인한다.
 */
class NoSleepEffectTest {

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
		JsonObject json = JsonParser.parseString("{ \"type\": \"no_sleep\" }").getAsJsonObject();

		PerkEffect effect = PerkEffectType.NO_SLEEP.create("sharedfate:테스트", 0, json);

		assertInstanceOf(NoSleepEffect.class, effect);
		assertSame(NoSleepEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 하위_효과로는_넣을_수_없다() {
		// 판정부(PerkWorldRules)는 최상위 효과만 훑는다. 안에 넣으면 조용히 아무 일도 하지
		// 않으므로 읽는 시점에 거른다.
		JsonObject json = JsonParser.parseString("{ \"type\": \"no_sleep\" }").getAsJsonObject();

		assertNull(PerkEffectType.NO_SLEEP.create(
				"sharedfate:테스트", OnKillEffect.nestedIndex(0, 0), json));
	}

	// ------------------------------------------------------------------ 판정

	@Test
	void 증강이_없으면_잠을_막지_않는다() {
		assertFalse(PerkWorldRules.blocksSleep(null));
		assertFalse(PerkWorldRules.blocksSleep(TeamState.fresh(20.0F)));
	}

	@Test
	void 이_효과를_가진_증강이_있으면_막는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(watchPool(dir));

		assertTrue(PerkWorldRules.blocksSleep(owning("sharedfate:sleepless_watch")));
	}

	@Test
	void 다른_증강만_있으면_막지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(watchPool(dir));

		assertFalse(PerkWorldRules.blocksSleep(owning("sharedfate:hunter_meal")),
				"mob_damage 만 가진 팀은 평소처럼 잔다");
	}

	@Test
	void 증강이_꺼져_있으면_막지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(watchPool(dir));

		TeamState state = owning("sharedfate:sleepless_watch");
		state.perksEnabled = false;

		assertFalse(PerkWorldRules.blocksSleep(state));
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(watchPool(dir));

		assertFalse(PerkWorldRules.blocksSleep(owning("sharedfate:사라진것")));
	}

	@Test
	void 서버_플레이어가_아니면_통과시킨다() {
		// 클라이언트 쪽 플레이어는 팀 상태를 볼 수 없다. null 은 "막지 않는다"는 뜻이다.
		assertNull(PerkWorldRules.onAllowSleep(null, null));
	}

	// ------------------------------------------------------------------ 도우미

	private static TeamState owning(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	/** 불면의 파수꾼(mob_damage + no_sleep)과 사냥꾼의 식사(mob_damage 만)를 담은 증강 풀. */
	private static Path watchPool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:sleepless_watch", "rarity": "silver", "name": "불면의 파수꾼",
				      "effects": [
				        { "type": "mob_damage", "multiplier": 0.85,
				          "targets": ["minecraft:zombie", "minecraft:skeleton"] },
				        { "type": "no_sleep" }
				      ] },
				    { "id": "sharedfate:hunter_meal", "rarity": "silver", "name": "사냥꾼의 식사",
				      "effects": [
				        { "type": "mob_damage", "multiplier": 1.15 }
				      ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
