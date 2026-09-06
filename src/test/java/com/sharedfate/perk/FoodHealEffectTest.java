package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.FoodHealEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 회복 2단계 「먹는 게 남는 거다」({@code food_heal})를 본다.
 *
 * <p>실제로 먹는 순간을 잡는 부분({@code FoodPropertiesMixin} 의 {@code onConsume} 훅)은 살아
 * 있는 서버와 월드가 있어야 하므로 여기서는 다루지 않는다. 대신 그 코드가 부르는 계산
 * ({@link PerkFoodRules#foodHealFor}, {@link PerkFoodRules#applyToPool})을 모두 확인한다.
 *
 * <p><b>가장 중요한 시험은 「팀 공유 풀에 한 번만 더해지는가」다.</b>
 */
class FoodHealEffectTest {

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

	/**
	 * {@code food_heal} 이 {@link PerkEffectType} 에 등록돼 있다.
	 *
	 * <p>등록 줄을 빠뜨리면 <b>빌드는 통과하는데</b> 이 타입을 쓴 세트 단계만 조용히 버려진다.
	 */
	@Test
	void 효과_타입으로_등록돼_있다() {
		assertNotNull(PerkEffectType.fromId("food_heal"),
				"PerkEffectType 에 FOOD_HEAL(\"food_heal\", FoodHealEffect::fromJson) 을 등록해야 한다");
	}

	@Test
	void 회복량을_읽는다() {
		FoodHealEffect effect = assertInstanceOf(FoodHealEffect.class,
				create("{ \"type\": \"food_heal\", \"health\": 2.0 }"));

		assertEquals(2.0, effect.health(), 1.0e-9);
		assertEquals(2.0F, effect.healthFor(), 1.0e-6F);
	}

	@Test
	void 회복량이_없거나_범위를_벗어나면_버린다() {
		assertNull(create("{ \"type\": \"food_heal\" }"));
		assertNull(create("{ \"type\": \"food_heal\", \"health\": 0 }"));
		assertNull(create("{ \"type\": \"food_heal\", \"health\": -2 }"));
		assertNull(create("{ \"type\": \"food_heal\", \"health\": 100 }"),
				"한 입에 체력 100 을 채우는 정의는 실수로 본다");
		assertNull(create("{ \"type\": \"food_heal\", \"health\": \"조금\" }"));
	}

	@Test
	void 생성자로_말도_안_되는_값이_들어와도_상한에서_멈춘다() {
		// JSON 경로는 이미 범위를 검사한다. 여기서 보는 것은 Java 쪽에서 직접 만든 경우다.
		assertEquals(20.0F, new FoodHealEffect(999.0).healthFor(), 1.0e-6F);
		assertEquals(0.0F, new FoodHealEffect(-1.0).healthFor(), 1.0e-6F);
		assertEquals(0.0F, new FoodHealEffect(Double.NaN).healthFor(), 1.0e-6F);
	}

	// ------------------------------------------------------------------ 세트와의 연결

	/** 세트 정의가 없으면 아무 일도 일어나지 않는다. 증강만 가진 팀은 예전과 완전히 같다. */
	@Test
	void 세트가_없으면_아무것도_회복하지_않는다(@TempDir Path dir) throws IOException {
		writePerks(dir);
		PerkRegistry.load(dir);

		assertEquals(0.0F, PerkFoodRules.foodHealFor(null));
		assertEquals(0.0F, PerkFoodRules.foodHealFor(TeamState.fresh(20.0F)));
		assertEquals(0.0F, PerkFoodRules.foodHealFor(team("recovery1", "recovery2")),
				"세트 정의가 없으면 회복 증강을 둘 모아도 아무 일도 없다");
	}

	/**
	 * 회복 유형을 둘 모으면 회복 2단계가 켜지고 한 입에 체력 2 를 준다.
	 *
	 * <p>이 시험이 깨지는 가장 흔한 이유는 {@link PerkFoodRules} 가 {@code ownedPerks} 만 훑고
	 * {@link PerkSetEffects#activeEffectsOf} 를 잇지 않은 것이다. 그러면 빌드도 통과하고 로그도
	 * 없는데 이 세트만 완전히 무동작이 된다.
	 */
	@Test
	void 회복_2단계가_켜지면_한_입에_2를_회복한다(@TempDir Path dir) throws IOException {
		load(dir);

		assertEquals(2.0F, PerkFoodRules.foodHealFor(team("recovery1", "recovery2")), 1.0e-6F);
	}

	/** 하나가 모자라면 켜지지 않는다. */
	@Test
	void 회복이_하나_모자라면_켜지지_않는다(@TempDir Path dir) throws IOException {
		load(dir);

		assertEquals(0.0F, PerkFoodRules.foodHealFor(team("recovery1")));
	}

	/** 다른 유형만 모은 팀에는 걸리지 않는다. */
	@Test
	void 다른_유형의_세트는_음식_회복을_주지_않는다(@TempDir Path dir) throws IOException {
		load(dir);

		assertEquals(0.0F, PerkFoodRules.foodHealFor(team("power1", "power2", "power3")));
	}

	// ------------------------------------------------------------------ 공유 풀 반영

	/**
	 * 회복은 팀 공유 값에 <b>정확히 한 번</b> 더해진다.
	 *
	 * <p>여기서 {@code player.heal(2.0F)} 을 부르면 개인 체력이 움직이고, {@code StatMirror} 가
	 * 그 변화량을 관측해 공유 풀에 한 번 더 더한다. 그래서 공유 값만 직접 올린다.
	 * {@link PerkKillRewards} 머리말에 같은 규칙이 자세히 적혀 있다.
	 */
	@Test
	void 회복은_팀_공유_값에_한_번만_더해진다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 10.0F;

		PerkFoodRules.applyToPool(state, 2.0F);

		assertEquals(12.0F, state.health, 1.0e-4F);
	}

	/**
	 * 팀 인원수는 어디에도 곱해지지 않는다.
	 *
	 * <p>회복량은 「한 번 먹는 사건」에 붙는다. 팀원이 몇이든 한 입은 2 다. 두 번 더해지는 경우는
	 * 두 사람이 각각 먹었을 때뿐이고, 그건 진짜로 두 사건이라 4 가 맞다.
	 */
	@Test
	void 인원수가_아니라_먹은_횟수만큼_더해진다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team("recovery1", "recovery2");
		state.health = 10.0F;

		float perBite = PerkFoodRules.foodHealFor(state);
		PerkFoodRules.applyToPool(state, perBite);
		assertEquals(12.0F, state.health, 1.0e-4F, "네 명짜리 팀이어도 한 입은 2 다");

		PerkFoodRules.applyToPool(state, PerkFoodRules.foodHealFor(state));
		assertEquals(14.0F, state.health, 1.0e-4F, "두 번 먹었으면 두 번분이다");
	}

	@Test
	void 팀_최대_체력을_넘지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 19.0F;

		PerkFoodRules.applyToPool(state, 2.0F);

		assertEquals(20.0F, state.health);
	}

	@Test
	void 회복량이_0이면_공유_값을_건드리지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 7.0F;

		PerkFoodRules.applyToPool(state, 0.0F);

		assertEquals(7.0F, state.health);
	}

	/**
	 * 이 효과에는 개인에게 붙는 길이 없다.
	 *
	 * <p>{@code apply}/{@code remove} 를 재정의하지 않는 것이 그 뜻이다. 개인 체력을 건드리는
	 * 순간 {@code StatMirror} 가 그것을 다시 공유 풀에 더해 회복이 두 번 들어간다.
	 */
	@Test
	void 개인에게_붙는_길이_없다() {
		FoodHealEffect effect = new FoodHealEffect(2.0);

		assertDoesNotThrow(() -> {
			effect.apply(null);
			effect.remove(null);
		}, "플레이어를 들여다보지 않으므로 null 이어도 아무 일이 없다");
	}

	// ------------------------------------------------------------------ 도우미

	/** 등록된 타입을 거쳐 만든다. 이름으로 찾으므로 등록을 빠뜨리면 여기서 걸린다. */
	private static PerkEffect create(String json) {
		PerkEffectType type = PerkEffectType.fromId("food_heal");
		assertNotNull(type, "PerkEffectType 에 food_heal 이 등록돼 있지 않다");
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

	/** 증강 풀과 세트 정의를 모두 임시 폴더에 적고 올린다. */
	private static void load(Path dir) throws IOException {
		writePerks(dir);
		writeSets(dir);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);
	}

	/**
	 * 세트 유형만 다른 껍데기 증강들.
	 *
	 * <p>증강 자체의 효과는 세트 판정과 아무 상관이 없지만, {@link PerkRegistry} 가 효과 없는
	 * 증강을 버리므로 상관없는 효과를 하나씩 넣어 둔다.
	 */
	private static void writePerks(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:recovery1", "rarity": "silver", "name": "회복1",
				      "set_types": [ "recovery" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:recovery2", "rarity": "silver", "name": "회복2",
				      "set_types": [ "recovery" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:power1", "rarity": "silver", "name": "화력1",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:power2", "rarity": "silver", "name": "화력2",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] },
				    { "id": "sharedfate:power3", "rarity": "silver", "name": "화력3",
				      "set_types": [ "power" ],
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
	}

	/** 회복 2단계만 값이 들어 있는 최소한의 세트 정의. */
	private static void writeSets(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), """
				{
				  "sets": [
				    { "type": "recovery", "tiers": [
				        { "count": 2, "name": "먹는 게 남는 거다",
				          "effects": [ { "type": "food_heal", "health": 2.0 } ] }
				    ] },
				    { "type": "power", "tiers": [
				        { "count": 3, "name": "때린 만큼 채운다",
				          "effects": [ { "type": "lifesteal", "fraction": 0.05 } ] }
				    ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
	}
}
