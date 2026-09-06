package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.SharedFateMod;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.perk.effect.MobSpawnRateEffect;
import com.sharedfate.perk.effect.MobSpeedEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code mob_spawn_rate} 의 정의 읽기와 배율 합성 규칙을 본다.
 *
 * <p>실제로 스폰 경로에 끼어드는 부분은 살아 있는 서버와 월드가 있어야 하므로 여기서 다루지
 * 않는다. 대신 바닐라 쪽 대상이 그대로인지는 {@link NaturalSpawnerTargetTest} 가 붙들어 둔다.
 */
class MobSpawnRateEffectTest {
	private SharedFateConfig previousConfig;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		previousConfig = SharedFateMod.config;
		SharedFateMod.config = new SharedFateConfig();
		MobPerkModifiers.resetForTesting();
	}

	@AfterEach
	void tearDown() {
		SharedFateMod.config = previousConfig;
		MobPerkModifiers.resetForTesting();
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 배율을_적은_대로_읽는다() {
		MobSpawnRateEffect effect = rate("{ \"type\": \"mob_spawn_rate\", \"multiplier\": 1.35 }");

		assertEquals(1.35, effect.multiplier());
		assertEquals(1.35, effect.multiplierFor(), 1.0e-9);
		assertEquals(1.35, effect.multiplierFor(), 1.0e-9, "몇 번을 물어도 같은 값이다");
	}

	@Test
	void 배율이_없거나_범위를_벗어나면_효과를_만들지_않는다() {
		assertNull(MobSpawnRateEffect.fromJson("p", 0, json("{ \"type\": \"mob_spawn_rate\" }")));
		assertNull(MobSpawnRateEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_spawn_rate\", \"multiplier\": \"많이\" }")));
		assertNull(MobSpawnRateEffect.fromJson("p", 0,
						json("{ \"type\": \"mob_spawn_rate\", \"multiplier\": 0.0 }")),
				"적대 몹이 아예 안 나오는 판은 성립하지 않는다");
		assertNull(MobSpawnRateEffect.fromJson("p", 0,
						json("{ \"type\": \"mob_spawn_rate\", \"multiplier\": -1.0 }")));
		assertNull(MobSpawnRateEffect.fromJson("p", 0,
						json("{ \"type\": \"mob_spawn_rate\", \"multiplier\": 8.0 }")),
				"배율이 곧 스폰 경로를 다시 도는 횟수라 위쪽을 열어 둘 수 없다");
	}

	@Test
	void 코드로_직접_만든_이상한_값도_안전한_범위로_자른다() {
		assertEquals(1.0, new MobSpawnRateEffect(Double.NaN).multiplierFor());
		assertEquals(1.0, new MobSpawnRateEffect(-1.0).multiplierFor());
		assertEquals(1.0, new MobSpawnRateEffect(0.0).multiplierFor());
		assertEquals(1.0, new MobSpawnRateEffect(Double.POSITIVE_INFINITY).multiplierFor(),
				"유한하지 않은 값은 자르는 대신 바닐라로 물러난다");
		assertEquals(MobSpawnRateEffect.MAX_MULTIPLIER,
				new MobSpawnRateEffect(1.0e9).multiplierFor());
	}

	@Test
	void 팀원에게는_아무것도_하지_않는다() {
		MobSpawnRateEffect effect = rate("{ \"type\": \"mob_spawn_rate\", \"multiplier\": 1.35 }");

		effect.apply(null);
		effect.remove(null);
		assertEquals(1.0, effect.damageDealtMultiplier());
		assertEquals(1.0, effect.damageTakenMultiplier());
	}

	// ------------------------------------------------------------------ 배율 합성

	/** 한 팀 안에서는 곱한다. 증강 하나 안의 효과들이든 증강 여럿이든 같은 곱셈이다. */
	@Test
	void 한_팀_안의_배율은_모두_곱한다() {
		assertEquals(1.0, MobPerkModifiers.productOfSpawnRates(List.of()));
		assertEquals(1.35, MobPerkModifiers.productOfSpawnRates(
				List.of(new MobSpawnRateEffect(1.35))), 1.0e-9);
		assertEquals(1.32, MobPerkModifiers.productOfSpawnRates(
				List.of(new MobSpawnRateEffect(1.2), new MobSpawnRateEffect(1.1))), 1.0e-9);
		assertEquals(0.6, MobPerkModifiers.productOfSpawnRates(
				List.of(new MobSpawnRateEffect(1.2), new MobSpawnRateEffect(0.5))), 1.0e-9);
	}

	@Test
	void 다른_종류의_효과는_스폰율에_끼어들지_않는다() {
		assertEquals(1.35, MobPerkModifiers.productOfSpawnRates(List.of(
						new MobSpeedEffect(2.0, null),
						new MobSpawnRateEffect(1.35))),
				1.0e-9);
	}

	/** 팀이 여럿이면 1.0 에서 가장 멀리 떨어진 배율 하나만 쓴다. 체력·공격력과 같은 규칙이다. */
	@Test
	void 팀이_여럿이면_1_0_에서_가장_먼_값_하나만_쓴다() {
		assertEquals(1.0, MobPerkModifiers.combineSpawnRates(), "보유한 팀이 없으면 바닐라 그대로");
		assertEquals(1.35, MobPerkModifiers.combineSpawnRates(1.35), 1.0e-9);
		assertEquals(0.5, MobPerkModifiers.combineSpawnRates(1.35, 0.5), 1.0e-9,
				"×0.5 가 ×1.35 보다 1.0 에서 멀다");
		assertEquals(2.0, MobPerkModifiers.combineSpawnRates(1.5, 2.0), 1.0e-9);
		assertEquals(2.0, MobPerkModifiers.combineSpawnRates(2.0, 1.5), 1.0e-9,
				"팀을 훑는 순서가 결과를 바꾸면 안 된다");
		assertEquals(0.5, MobPerkModifiers.combineSpawnRates(2.0, 0.5), 1.0e-9,
				"세기가 같으면 플레이어에게 유리한 쪽을 고른다");
	}

	@Test
	void 합쳐진_배율도_안전한_범위로_자른다() {
		assertEquals(MobPerkModifiers.MAX_SPAWN_RATE_MULTIPLIER,
				MobPerkModifiers.combineSpawnRates(1.0e9));
		assertEquals(1.0, MobPerkModifiers.combineSpawnRates(Double.NaN));
		assertEquals(1.0, MobPerkModifiers.sanitizeSpawnRate(0.0),
				"스폰율 0 은 배율이 아니라 「몹이 없는 판」이다");
		assertEquals(1.0, MobPerkModifiers.sanitizeSpawnRate(-1.0));
	}

	// ------------------------------------------------------------------ 설정으로 끄기

	@Test
	void 설정은_기본으로_켜져_있다() {
		assertTrue(new SharedFateConfig().mobSpawnRatePerks);
		assertTrue(MobPerkModifiers.spawnRatePerksEnabled());
	}

	@Test
	void 설정을_아직_읽지_않았어도_켜진_것으로_본다() {
		SharedFateMod.config = null;

		assertTrue(MobPerkModifiers.spawnRatePerksEnabled());
	}

	@Test
	void 설정을_끄면_배율이_1_0_이다() {
		SharedFateMod.config.mobSpawnRatePerks = false;

		assertEquals(1.0, MobPerkModifiers.combineSpawnRates(1.35),
				"증강을 보유하고 있어도 걸리지 않아야 한다");
		assertEquals(1.0, MobPerkModifiers.spawnRateOf(List.of(List.of("sharedfate:anything"))));
		assertEquals(1.0, MobPerkModifiers.spawnRateMultiplier(null));
	}

	@Test
	void 서버가_없으면_배율이_1_0_이다() {
		assertEquals(1.0, MobPerkModifiers.spawnRateMultiplier(null));
	}

	// ------------------------------------------------------------------ 스폰 횟수로 바꾸기

	/**
	 * 바닐라는 청크마다 정확히 한 번 돈다. 1.35 번 돌 수는 없으므로 소수 부분을 확률로 바꾼다.
	 */
	@Test
	void 소수점_배율은_확률로_바뀐다() {
		assertEquals(1, MobPerkModifiers.spawnPasses(1.35, 0.36), "65% 는 바닐라와 같은 한 번");
		assertEquals(2, MobPerkModifiers.spawnPasses(1.35, 0.34), "35% 는 한 번 더");
		assertEquals(1, MobPerkModifiers.spawnPasses(0.5, 0.49));
		assertEquals(0, MobPerkModifiers.spawnPasses(0.5, 0.51), "절반은 통째로 건너뛴다");
	}

	@Test
	void 배율이_1_0_이면_언제나_한_번이다() {
		for (double roll : new double[] {0.0, 0.5, 0.999}) {
			assertEquals(1, MobPerkModifiers.spawnPasses(1.0, roll));
		}
	}

	@Test
	void 스폰_횟수는_상한을_넘지_않는다() {
		assertEquals(4, MobPerkModifiers.spawnPasses(1.0e9, 0.0),
				"배율이 곧 매 틱 비용이라 상한을 넘겨선 안 된다");
		assertEquals(1, MobPerkModifiers.spawnPasses(Double.NaN, 0.5), "이상한 값은 바닐라로 물러난다");
		assertEquals(1, MobPerkModifiers.spawnPasses(0.0, 0.9));
	}

	// ------------------------------------------------------------------ 증강 정의 파일

	/**
	 * 이 시험은 {@code PerkEffectType} 에 {@code mob_spawn_rate} 가 등록되어 있어야 통과한다.
	 * 등록이 빠져 있으면 효과를 읽지 못해 증강 자체가 버려지므로 여기서 먼저 터진다.
	 */
	@Test
	void 증강_파일에서_읽는다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:restless_night", "name": "잠들지 않는 밤",
				      "description": "적대적 몹이 35% 더 자주 나타납니다",
				      "rarity": "common",
				      "effects": [ { "type": "mob_spawn_rate", "multiplier": 1.35 } ] },
				    { "id": "sharedfate:bad_spawn_rate", "rarity": "common",
				      "effects": [ { "type": "mob_spawn_rate", "multiplier": 0.0 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);

		PerkRegistry.load(dir);

		MobSpawnRateEffect effect = assertInstanceOf(MobSpawnRateEffect.class,
				PerkRegistry.byId("sharedfate:restless_night").orElseThrow().effects().get(0));
		assertEquals(1.35, effect.multiplier());
		assertTrue(PerkRegistry.byId("sharedfate:bad_spawn_rate").isEmpty(),
				"배율이 잘못된 효과가 있으면 그 증강만 버린다");

		assertEquals(1.35,
				MobPerkModifiers.spawnRateOf(List.of(List.of("sharedfate:restless_night"))),
				1.0e-9);
		assertEquals(1.0, MobPerkModifiers.spawnRateOf(List.of(List.of("sharedfate:없는증강"))),
				"모르는 증강 이름은 조용히 넘긴다");
	}

	/**
	 * 세트로 켜진 스폰율도 함께 곱해진다.
	 *
	 * <p>{@code mob_spawn_rate} 를 쓰는 정의는 세트 「화력 4」 하나뿐이라, 세트를 훑는 줄이
	 * 빠지면 이 효과 타입이 통째로 무동작이 되는데 빌드도 서버도 멀쩡하다.
	 */
	@Test
	void 세트로_켜진_스폰율도_함께_곱해진다() {
		assertEquals(1.35, MobPerkModifiers.spawnRateForTeam(List.of(), List.of(rate("{\"multiplier\": 1.35}"))),
				1.0e-9, "증강이 없어도 세트 몫은 걸려야 한다");

		assertEquals(1.0, MobPerkModifiers.spawnRateForTeam(List.of(), List.of()),
				"세트도 증강도 없으면 1.0 이다");
	}

	/** 증강 몫과 세트 몫은 같은 팀 안에서 곱해진다. 팀끼리 1.0 에서 먼 하나를 고르는 것과 다르다. */
	@Test
	void 증강_몫과_세트_몫은_곱해진다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:restless_night", "rarity": "common",
				      "effects": [ { "type": "mob_spawn_rate", "multiplier": 1.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		assertEquals(1.2 * 1.35,
				MobPerkModifiers.spawnRateForTeam(List.of("sharedfate:restless_night"),
						List.of(rate("{\"multiplier\": 1.35}"))),
				1.0e-9);
	}

	// ------------------------------------------------------------------ 도우미

	private static MobSpawnRateEffect rate(String raw) {
		return assertInstanceOf(MobSpawnRateEffect.class,
				MobSpawnRateEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}
}
