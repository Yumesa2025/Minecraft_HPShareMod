package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.LifestealEfficiencyEffect;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 회복 3단계 「더 진하게 빤다」({@code lifesteal_efficiency})와 화력 4단계의 흡혈 합산을 본다.
 *
 * <p>여기서 지키려는 것은 둘이다.
 *
 * <ul>
 *   <li><b>효율은 곱셈이다.</b> 5% 에 1.3 을 곱하면 6.5% 이지 35% 가 아니다. 덧셈으로 근사하면
 *       흡혈률이 바뀌는 순간 어긋난다.</li>
 *   <li><b>세트 단계는 누적이다.</b> 화력 3·4단계가 둘 다 켜지므로 4단계는 3단계를 대체하는
 *       15% 가 아니라 그 위에 얹는 +10% 로 적혀 있어야 하고, 합이 정확히 15% 여야 한다.</li>
 * </ul>
 */
class LifestealEfficiencyEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
		PerkLifesteal.resetForTesting();
	}

	// ------------------------------------------------------------------ 정의 읽기

	/** 등록 줄을 빠뜨리면 이 타입을 쓴 세트 단계가 조용히 버려진다. */
	@Test
	void 효과_타입으로_등록돼_있다() {
		assertNotNull(PerkEffectType.fromId("lifesteal_efficiency"),
				"PerkEffectType 에 LIFESTEAL_EFFICIENCY(\"lifesteal_efficiency\", "
						+ "LifestealEfficiencyEffect::fromJson) 을 등록해야 한다");
	}

	@Test
	void 배율을_읽는다() {
		LifestealEfficiencyEffect effect = assertInstanceOf(LifestealEfficiencyEffect.class,
				create("{ \"type\": \"lifesteal_efficiency\", \"multiplier\": 1.3 }"));

		assertEquals(1.3, effect.multiplier(), 1.0e-9);
		assertEquals(1.3, effect.multiplierFor(), 1.0e-9);
	}

	@Test
	void 배율이_없거나_범위를_벗어나면_버린다() {
		assertNull(create("{ \"type\": \"lifesteal_efficiency\" }"));
		assertNull(create("{ \"type\": \"lifesteal_efficiency\", \"multiplier\": -0.5 }"));
		assertNull(create("{ \"type\": \"lifesteal_efficiency\", \"multiplier\": 100 }"));
		assertNull(create("{ \"type\": \"lifesteal_efficiency\", \"multiplier\": \"조금\" }"));
	}

	/** 값을 못 믿을 때는 <b>1.0</b> 이다. 0 을 돌려주면 흡혈이 통째로 사라진다. */
	@Test
	void 생성자로_말도_안_되는_값이_들어오면_아무것도_곱하지_않는다() {
		assertEquals(1.0, new LifestealEfficiencyEffect(Double.NaN).multiplierFor(), 1.0e-9);
		assertEquals(8.0, new LifestealEfficiencyEffect(999.0).multiplierFor(), 1.0e-9);
		assertEquals(0.0, new LifestealEfficiencyEffect(-1.0).multiplierFor(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 곱셈인가

	/**
	 * <b>효율은 곱셈이다.</b> 화력 3단계(5%)에 회복 3단계(×1.3)면 6.5% 다.
	 *
	 * <p>덧셈으로 근사했다면 0.05 + 0.3 = 35% 가 되어 준 피해 100 에 35 를 돌려준다.
	 * 그 값이 나오면 곱셈 단계가 빠진 것이다.
	 */
	@Test
	void 효율은_흡혈률에_곱해진다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team("power1", "power2", "power3",
				"recovery1", "recovery2", "recovery3");

		assertEquals(0.065, PerkLifesteal.totalFraction(state), 1.0e-9,
				"5% × 1.3 = 6.5% 다. 35% 가 나오면 덧셈으로 근사한 것이다");
		assertEquals(6.5F, PerkLifesteal.healingFor(state, 100.0F), 1.0e-4F);
	}

	/** 흡혈이 하나도 없으면 효율만 켜져도 0 이다. 곱셈 단계라 그것이 옳다. */
	@Test
	void 흡혈이_없으면_효율만으로는_아무것도_안_준다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team("recovery1", "recovery2", "recovery3");

		assertEquals(0.0, PerkLifesteal.totalFraction(state), 1.0e-9);
		assertEquals(0.0F, PerkLifesteal.healingFor(state, 100.0F));
	}

	// ------------------------------------------------------------------ 화력 3 + 4

	/**
	 * 화력 3·4단계가 <b>함께</b> 켜지면 최종 흡혈률이 15% 다.
	 *
	 * <p>세트 단계는 누적이라 넷을 모으면 3단계(5%)와 4단계가 둘 다 켜진다. 그래서 4단계를
	 * 「15%」로 적으면 합이 20% 가 된다. 4단계는 <b>+10%</b> 로 적혀 있어야 한다.
	 */
	@Test
	void 화력_3과_4가_함께_켜지면_흡혈이_15퍼센트다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team("power1", "power2", "power3", "power4");

		assertEquals(0.15, PerkLifesteal.totalFraction(state), 1.0e-9,
				"3단계 5% + 4단계 10% = 15%. 0.2 가 나오면 4단계를 15% 로 적은 것이다");
		assertEquals(15.0F, PerkLifesteal.healingFor(state, 100.0F), 1.0e-4F);
	}

	/** 셋만 모으면 아직 5% 다. */
	@Test
	void 화력이_셋이면_아직_5퍼센트다(@TempDir Path dir) throws IOException {
		load(dir);

		assertEquals(0.05, PerkLifesteal.totalFraction(team("power1", "power2", "power3")), 1.0e-9);
	}

	/** 화력 3·4 와 회복 3 이 모두 켜지면 15% × 1.3 = 19.5% 다. */
	@Test
	void 화력_3_4_와_회복_3이_겹치면_19_5퍼센트다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team("power1", "power2", "power3", "power4",
				"recovery1", "recovery2", "recovery3");

		assertEquals(0.195, PerkLifesteal.totalFraction(state), 1.0e-9);
		assertEquals(19.5F, PerkLifesteal.healingFor(state, 100.0F), 1.0e-3F);
	}

	// ------------------------------------------------------------------ 세트가 없을 때

	/** 세트 정의가 없으면 흡혈 계산은 증강만 보던 예전과 완전히 같다. */
	@Test
	void 세트가_없으면_아무_일도_일어나지_않는다(@TempDir Path dir) throws IOException {
		writePerks(dir);
		PerkRegistry.load(dir);

		assertEquals(0.0, PerkLifesteal.totalFraction(team("power1", "power2", "power3", "power4")),
				1.0e-9);
		assertEquals(0.0F,
				PerkLifesteal.healingFor(team("recovery1", "recovery2", "recovery3"), 100.0F));
	}

	/** 증강이 준 흡혈에도 세트 효율이 그대로 곱해진다. */
	@Test
	void 증강_흡혈에도_세트_효율이_곱해진다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team("vampire", "recovery1", "recovery2", "recovery3");

		assertEquals(0.26, PerkLifesteal.totalFraction(state), 1.0e-9, "20% × 1.3 = 26%");
	}

	// ------------------------------------------------------------------ 도우미

	/** 등록된 타입을 거쳐 만든다. 이름으로 찾으므로 등록을 빠뜨리면 여기서 걸린다. */
	private static PerkEffect create(String json) {
		PerkEffectType type = PerkEffectType.fromId("lifesteal_efficiency");
		assertNotNull(type, "PerkEffectType 에 lifesteal_efficiency 가 등록돼 있지 않다");
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return type.create("sharedfate:테스트", 0, parsed);
	}

	private static TeamState team(String... perkIds) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (String perkId : perkIds) {
			state.ownedPerks.add("sharedfate:" + perkId);
		}
		return state;
	}

	private static void load(Path dir) throws IOException {
		writePerks(dir);
		writeSets(dir);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);
	}

	/**
	 * 세트 유형만 다른 껍데기 증강 일곱과, 흡혈 20% 를 직접 가진 증강 하나.
	 *
	 * <p>{@link PerkRegistry} 가 효과 없는 증강을 버리므로 껍데기에도 상관없는 효과를 하나씩
	 * 넣어 둔다. 「흡혈귀」만 진짜 흡혈을 가진다.
	 */
	private static void writePerks(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:power1", "rarity": "silver", "name": "화력1",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:power2", "rarity": "silver", "name": "화력2",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:power3", "rarity": "silver", "name": "화력3",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:power4", "rarity": "silver", "name": "화력4",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:recovery1", "rarity": "silver", "name": "회복1",
				      "set_types": [ "recovery" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:recovery2", "rarity": "silver", "name": "회복2",
				      "set_types": [ "recovery" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:recovery3", "rarity": "silver", "name": "회복3",
				      "set_types": [ "recovery" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:vampire", "rarity": "prism", "name": "흡혈귀",
				      "effects": [ { "type": "lifesteal", "fraction": 0.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
	}

	/** 세트 JSON 에 넣을 값을 그대로 적어 둔 최소한의 정의. */
	private static void writeSets(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), """
				{
				  "sets": [
				    { "type": "power", "tiers": [
				        { "count": 3, "name": "때린 만큼 채운다",
				          "effects": [ { "type": "lifesteal", "fraction": 0.05 } ] },
				        { "count": 4, "name": "피에 굶주렸다",
				          "effects": [ { "type": "lifesteal", "fraction": 0.10 } ] }
				    ] },
				    { "type": "recovery", "tiers": [
				        { "count": 2, "name": "먹는 게 남는 거다",
				          "effects": [ { "type": "food_heal", "health": 2.0 } ] },
				        { "count": 3, "name": "더 진하게 빤다",
				          "effects": [ { "type": "lifesteal_efficiency", "multiplier": 1.3 } ] }
				    ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
	}
}
