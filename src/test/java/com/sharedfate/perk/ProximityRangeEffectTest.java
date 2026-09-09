package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.ProximityRangeEffect;
import com.sharedfate.sync.TeamGathering;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 거리를 넓히는 {@code proximity_range} 를 본다.
 *
 * <p>여기서 지키려는 것은 셋이다.
 *
 * <ul>
 *   <li><b>{@code proximity} 와 {@code gather} 가 함께 넓어진다.</b> 「운명 공동체」는 둘을 같은
 *       거리로 짝지어 쓴다. 보상 거리만 64로 넓히고 집합을 32로 두면 33칸에서 끌려 모여 넓힌
 *       보상을 영영 못 본다.</li>
 *   <li><b>여러 개면 전부 곱한다.</b> 2.0 둘이면 4.0 이고, 증강과 세트에서 하나씩 와도 같다.</li>
 *   <li><b>값이 이상해도 증강을 버리지 않는다.</b> 범위 밖이면 경고만 남기고 자른다.</li>
 * </ul>
 *
 * <p>거리 곱셈은 월드가 필요 없는 순수 함수({@link ProximityRangeEffect#multiplierOf}·
 * {@link ProximityRangeEffect#scale})라 여기서 전부 확인할 수 있다.
 */
class ProximityRangeEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	/** 등록 줄을 빠뜨리면 이 타입을 쓴 증강이 조용히 버려진다. */
	@Test
	void 효과_타입으로_등록돼_있다() {
		assertNotNull(PerkEffectType.fromId("proximity_range"),
				"PerkEffectType 에 PROXIMITY_RANGE(\"proximity_range\", "
						+ "ProximityRangeEffect::fromJson) 을 등록해야 한다");
	}

	@Test
	void 배율을_읽는다() {
		ProximityRangeEffect effect = assertInstanceOf(ProximityRangeEffect.class,
				ProximityRangeEffect.fromJson("sharedfate:테스트", 0,
						json("{ \"type\": \"proximity_range\", \"multiplier\": 2.0 }")));

		assertEquals(2.0, effect.multiplier(), 1.0e-9);
	}

	/** 안 적으면 두 배다. */
	@Test
	void 배율을_안_적으면_기본값이다() {
		ProximityRangeEffect effect = assertInstanceOf(ProximityRangeEffect.class,
				ProximityRangeEffect.fromJson("sharedfate:테스트", 0,
						json("{ \"type\": \"proximity_range\" }")));

		assertEquals(ProximityRangeEffect.DEFAULT_MULTIPLIER, effect.multiplier(), 1.0e-9);
		assertEquals(2.0, ProximityRangeEffect.DEFAULT_MULTIPLIER, 1.0e-9);
	}

	/**
	 * 범위를 벗어나도 정의를 버리지 않는다.
	 *
	 * <p>거리 배율 하나가 틀렸다고 증강 전체를 잃으면 손해가 더 크다. 「소집의 조각」과 같은
	 * 정책이라 경고만 남기고 자른다.
	 */
	@Test
	void 범위를_벗어나면_자른다() {
		assertEquals(4.0, multiplierFromJson("{ \"multiplier\": 99 }"), 1.0e-9);
		assertEquals(1.0, multiplierFromJson("{ \"multiplier\": 0.5 }"), 1.0e-9);
		assertEquals(1.0, multiplierFromJson("{ \"multiplier\": -3 }"), 1.0e-9);
	}

	/** 숫자가 아니면 기본값으로 본다. 여기서도 정의는 살아남는다. */
	@Test
	void 숫자가_아니면_기본값으로_본다() {
		assertEquals(2.0, multiplierFromJson("{ \"multiplier\": \"두 배\" }"), 1.0e-9);
		assertEquals(2.0, multiplierFromJson("{ \"multiplier\": null }"), 1.0e-9);
	}

	/** 생성자로 말도 안 되는 값이 들어와도 성한 배율만 들고 있는다. */
	@Test
	void 생성자도_한_번_더_자른다() {
		assertEquals(2.0, new ProximityRangeEffect(Double.NaN).multiplier(), 1.0e-9);
		assertEquals(4.0, new ProximityRangeEffect(Double.POSITIVE_INFINITY).multiplier(), 1.0e-9,
				"무한대는 크고 작음을 따질 수 있으므로 상한으로 잘린다");
		assertEquals(1.0, new ProximityRangeEffect(-10.0).multiplier(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 배율 누적

	@Test
	void 하나도_없으면_아무것도_곱하지_않는다() {
		assertEquals(1.0, ProximityRangeEffect.multiplierOf(List.of()), 1.0e-9);
		assertEquals(1.0, ProximityRangeEffect.multiplierOf(null), 1.0e-9);
	}

	@Test
	void 여러_개면_전부_곱한다() {
		assertEquals(4.0, ProximityRangeEffect.multiplierOf(List.of(
				new ProximityRangeEffect(2.0), new ProximityRangeEffect(2.0))), 1.0e-9);
		assertEquals(6.0, ProximityRangeEffect.multiplierOf(List.of(
				new ProximityRangeEffect(2.0), new ProximityRangeEffect(3.0))), 1.0e-9);
	}

	/** 상한은 정의 하나하나에 걸리는 것이지 모아 얻은 값에는 걸지 않는다. */
	@Test
	void 모아서_얻은_값에는_상한이_없다() {
		assertTrue(ProximityRangeEffect.multiplierOf(List.of(
				new ProximityRangeEffect(4.0), new ProximityRangeEffect(4.0)))
				> ProximityRangeEffect.MAX_MULTIPLIER);
	}

	// ------------------------------------------------------------------ 거리 곱셈

	@Test
	void 거리에_배율을_먹인다() {
		assertEquals(64.0, ProximityRangeEffect.scale(32.0, 2.0), 1.0e-9);
		assertEquals(32.0, ProximityRangeEffect.scale(32.0, 1.0), 1.0e-9);
	}

	/**
	 * 배율이 성한 수가 아니면 원래 거리를 그대로 쓴다.
	 *
	 * <p>0 이면 아무리 붙어 있어도 흩어진 것이 되고, 무한대면 영영 모이지 않는다.
	 */
	@Test
	void 배율이_이상하면_원래_거리를_쓴다() {
		assertEquals(32.0, ProximityRangeEffect.scale(32.0, 0.0), 1.0e-9);
		assertEquals(32.0, ProximityRangeEffect.scale(32.0, -2.0), 1.0e-9);
		assertEquals(32.0, ProximityRangeEffect.scale(32.0, Double.NaN), 1.0e-9);
		assertEquals(32.0, ProximityRangeEffect.scale(32.0, Double.POSITIVE_INFINITY), 1.0e-9);
	}

	// ------------------------------------------------------------------ 팀 단위

	/**
	 * <b>같은 배율이 {@code proximity} 와 {@code gather} 두 판정에 함께 들어간다.</b>
	 *
	 * <p>둘 중 하나만 넓히면 「운명 공동체」가 깨진다. 32칸짜리 정의에 2.0 을 걸었는데 집합만
	 * 32로 남으면, 33칸에서 강제로 끌려 모여 64칸 보상을 영영 받지 못한다.
	 */
	@Test
	void 버프_거리와_집합_거리가_함께_늘어난다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = team("운명공동체", "넓은보폭");

		double range = TeamGathering.rangeMultiplier(state);
		double proximity = ProximityRangeEffect.scale(
				PerkSwapRules.proximities(state).getFirst().distance(), range);
		double gather = ProximityRangeEffect.scale(
				PerkSwapRules.gathers(state).getFirst().distance(), range);

		assertEquals(2.0, range, 1.0e-9);
		assertEquals(64.0, proximity, 1.0e-9);
		assertEquals(64.0, gather, 1.0e-9,
				"집합만 32로 남으면 33칸에서 끌려 모여 64칸 보상을 영영 못 본다");
		assertEquals(proximity, gather, 1.0e-9);
	}

	/** 증강이 없으면 거리는 정의에 적힌 그대로다. */
	@Test
	void 거리를_넓히는_증강이_없으면_거리가_그대로다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = team("운명공동체");

		double range = TeamGathering.rangeMultiplier(state);

		assertEquals(1.0, range, 1.0e-9);
		assertEquals(32.0, ProximityRangeEffect.scale(
				PerkSwapRules.proximities(state).getFirst().distance(), range), 1.0e-9);
		assertEquals(32.0, ProximityRangeEffect.scale(
				PerkSwapRules.gathers(state).getFirst().distance(), range), 1.0e-9);
	}

	@Test
	void 증강을_둘_가지면_배율도_곱해진다(@TempDir Path dir) throws IOException {
		loadPerks(dir);

		assertEquals(4.0, TeamGathering.rangeMultiplier(team("운명공동체", "넓은보폭", "더넓은보폭")),
				1.0e-9);
	}

	/**
	 * 세트로 얻은 배율도 함께 곱해진다.
	 *
	 * <p>보유 증강만 훑고 켜진 세트를 빠뜨리면 세트 쪽 배율이 조용히 사라진다. 빌드도 통과하고
	 * 로그도 남지 않는 종류의 누락이라 시험으로 붙잡는다.
	 */
	@Test
	void 세트로_얻은_배율도_곱해진다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		writeSets(dir);
		PerkSetRegistry.load(dir);

		assertEquals(1.5, TeamGathering.rangeMultiplier(team("발1", "발2", "발3")), 1.0e-9);
		assertEquals(3.0, TeamGathering.rangeMultiplier(team("발1", "발2", "발3", "넓은보폭")), 1.0e-9,
				"증강 2.0 × 세트 1.5 = 3.0. 2.0 이 나오면 세트 효과를 훑지 않은 것이다");
	}

	/** 증강을 꺼 둔 팀에게는 아무 배율도 없다. */
	@Test
	void 증강을_꺼_두면_배율이_없다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = team("넓은보폭");
		state.perksEnabled = false;

		assertEquals(1.0, TeamGathering.rangeMultiplier(state), 1.0e-9);
		assertEquals(1.0, TeamGathering.rangeMultiplier(null), 1.0e-9);
	}

	// ------------------------------------------------------------------ 도우미

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static double multiplierFromJson(String text) {
		ProximityRangeEffect effect = assertInstanceOf(ProximityRangeEffect.class,
				ProximityRangeEffect.fromJson("sharedfate:테스트", 0, json(text)));
		return effect.multiplier();
	}

	private static TeamState team(String... perkIds) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (String perkId : perkIds) {
			state.ownedPerks.add("sharedfate:" + perkId);
		}
		return state;
	}

	private static void loadPerks(Path dir) throws IOException {
		writePerks(dir);
		PerkRegistry.load(dir);
	}

	/**
	 * 「운명 공동체」를 닮은 증강 하나와 거리를 넓히는 증강 둘, 그리고 세트용 껍데기 셋.
	 *
	 * <p>거리를 32로 둔 것은 배율 2.0 을 먹였을 때 64가 나와 눈으로 확인하기 쉬워서다.
	 */
	private static void writePerks(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:운명공동체", "rarity": "prism", "name": "운명 공동체",
				      "effects": [
				        { "type": "proximity", "distance": 32,
				          "effects": [ { "type": "status_effect",
				                         "effect": "minecraft:regeneration", "duration": 3 } ] },
				        { "type": "gather", "distance": 32, "cooldown_ticks": 200 }
				      ] },
				    { "id": "sharedfate:넓은보폭", "rarity": "gold", "name": "넓은 보폭",
				      "effects": [ { "type": "proximity_range", "multiplier": 2.0 } ] },
				    { "id": "sharedfate:더넓은보폭", "rarity": "gold", "name": "더 넓은 보폭",
				      "effects": [ { "type": "proximity_range", "multiplier": 2.0 } ] },
				    { "id": "sharedfate:발1", "rarity": "silver", "name": "발1",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:발2", "rarity": "silver", "name": "발2",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:발3", "rarity": "silver", "name": "발3",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
	}

	/** 세트 단계 하나가 거리 배율을 준다. 값은 여기서만 쓰는 것이라 기본 정의와 무관하다. */
	private static void writeSets(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), """
				{
				  "sets": [
				    { "type": "power", "tiers": [
				        { "count": 3, "name": "멀리 떨어져도 된다",
				          "effects": [ { "type": "proximity_range", "multiplier": 1.5 } ] }
				    ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
	}
}
