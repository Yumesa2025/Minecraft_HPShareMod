package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.HungerOnDamageEffect;
import com.sharedfate.team.TeamState;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 생존 3단계 「맞으면 배부르다」({@code hunger_on_damage})를 살아 있는 게임 없이 시험한다.
 *
 * <p>여기서 지키는 것이 셋이다.
 *
 * <ul>
 *   <li>허기 계산의 <b>기준</b> — 감산 뒤 실제로 들어간 피해다({@link PerkTriggers#damageBasis}).</li>
 *   <li>「피해 1당 0.4」가 정수 칸으로 바뀌는 동안에도 <b>비율이 지켜지는가</b>.</li>
 *   <li>한 번의 피격에 공유 풀이 <b>한 번만</b> 움직이는가 — 팀 인원수만큼 곱해지지 않는가.</li>
 * </ul>
 *
 * <p>정의 읽기는 {@code PerkEffectType} 을 거치지 않고 팩토리를 직접 부른다. 그 열거형에 새 줄을
 * 넣는 일은 다른 사람 몫이라, 여기서 열거형 상수를 참조하면 순서에 따라 빌드가 깨진다.
 */
class HungerOnDamageEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	/** 다른 시험이 올려 둔 정의와 넘겨받은 나머지가 남아 있으면 답이 흔들린다. */
	@BeforeEach
	void 준비() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
		PerkTriggers.resetForTesting();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
		PerkTriggers.resetForTesting();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 피해당_허기를_적으면_읽힌다() {
		HungerOnDamageEffect effect = assertInstanceOf(HungerOnDamageEffect.class,
				raw(0, "{ \"type\": \"hunger_on_damage\", \"per_damage\": 0.4 }"));

		assertEquals(0.4, effect.perDamage(), 1.0e-9);
	}

	@Test
	void per_damage_가_없거나_숫자가_아니면_버린다() {
		assertNull(raw(0, "{ \"type\": \"hunger_on_damage\" }"));
		assertNull(raw(0, "{ \"type\": \"hunger_on_damage\", \"per_damage\": \"0.4\" }"));
	}

	@Test
	void per_damage_가_범위를_벗어나면_버린다() {
		assertNull(raw(0, "{ \"type\": \"hunger_on_damage\", \"per_damage\": 0 }"));
		assertNull(raw(0, "{ \"type\": \"hunger_on_damage\", \"per_damage\": -0.4 }"));
		assertNull(raw(0, "{ \"type\": \"hunger_on_damage\", \"per_damage\": 100 }"));
	}

	/** {@link PerkTriggers} 는 최상위 효과만 훑는다. 하위에 넣으면 조용히 무동작이 된다. */
	@Test
	void 하위_효과로_들어가면_버린다() {
		assertNull(raw(100, "{ \"type\": \"hunger_on_damage\", \"per_damage\": 0.4 }"));
	}

	// ------------------------------------------------------------------ 계산의 기준

	/**
	 * 허기는 <b>감산 뒤 실제로 들어간 피해</b>를 기준으로 찬다.
	 *
	 * <p>감산 전의 원래 피해가 아니다. 방어구와 저항을 두르면 실제로 깎이는 체력은 적은데 허기는
	 * 원래 피해만큼 찬다면 방어 증강과 겹칠 때 값이 터무니없어진다.
	 */
	@Test
	void 감산_뒤_실제_피해가_기준이다() {
		assertEquals(3.0F, PerkTriggers.damageBasis(10.0F, 3.0F), 0.0F,
				"감산 전(10)이 아니라 실제로 들어간 피해(3)를 센다");
		assertEquals(0.0F, PerkTriggers.damageBasis(10.0F, 0.0F), 0.0F,
				"방어구가 다 막아 실제 피해가 0 이면 허기도 안 찬다");
	}

	@Test
	void 피해_1당_0점4가_그대로_걸린다() {
		HungerOnDamageEffect effect = new HungerOnDamageEffect(0.4);

		assertEquals(4.0F, effect.hungerFor(10.0F), 1.0e-5F);
		assertEquals(1.2F, effect.hungerFor(3.0F), 1.0e-5F);
	}

	@Test
	void 피해가_0이하거나_이상하면_아무것도_안_찬다() {
		HungerOnDamageEffect effect = new HungerOnDamageEffect(0.4);

		assertEquals(0.0F, effect.hungerFor(0.0F), 0.0F);
		assertEquals(0.0F, effect.hungerFor(-5.0F), 0.0F);
		assertEquals(0.0F, effect.hungerFor(Float.NaN), 0.0F);
	}

	@Test
	void 한_번에_채울_수_있는_양은_잘린다() {
		assertEquals(20.0F, new HungerOnDamageEffect(0.4).hungerFor(1.0e9F), 1.0e-3F);
	}

	@Test
	void 효과_목록에서는_전부_더한다() {
		List<PerkEffect> effects =
				List.of(new HungerOnDamageEffect(0.4), new HungerOnDamageEffect(0.1));

		assertEquals(5.0F, PerkTriggers.hungerGainOf(effects, 10.0F), 1.0e-5F);
		assertEquals(0.0F, PerkTriggers.hungerGainOf(effects, 0.0F), 0.0F);
		assertEquals(0.0F, PerkTriggers.hungerGainOf(null, 10.0F), 0.0F);
		assertEquals(0.0F, PerkTriggers.hungerGainOf(List.of(), 10.0F), 0.0F);
	}

	// ------------------------------------------------------------------ 소수 나머지

	/**
	 * 정수 칸으로 바뀌어도 「피해 1당 0.4」는 긴 눈으로 지켜진다.
	 *
	 * <p>피해 3 은 1.2 칸이다. 매번 버리면 다섯 번에 5 칸, 매번 올림하면 10 칸이 되는데 정답은
	 * 6 칸이다. 남는 소수를 팀별로 들고 가는 방식만 그 값을 낸다.
	 */
	@Test
	void 남는_소수는_다음_피격으로_넘어간다() {
		UUID team = UUID.randomUUID();
		int total = 0;
		for (int i = 0; i < 5; i++) {
			total += PerkTriggers.takeWholeHunger(team, 1.2F);
		}

		assertEquals(6, total, "피해 3 을 다섯 번이면 15 × 0.4 = 6 칸이다");
	}

	/** 한 칸에 못 미치는 피격은 당장은 아무 일도 없지만 사라지지도 않는다. */
	@Test
	void 한_칸에_못_미치면_모아_두었다가_준다() {
		UUID team = UUID.randomUUID();

		assertEquals(0, PerkTriggers.takeWholeHunger(team, 0.4F));
		assertEquals(0, PerkTriggers.takeWholeHunger(team, 0.4F));
		assertEquals(1, PerkTriggers.takeWholeHunger(team, 0.4F), "0.4 셋이면 1.2 라 한 칸이다");
	}

	/** 나머지는 팀별로 따로 쌓인다. 다른 팀의 피격이 우리 허기를 채우면 안 된다. */
	@Test
	void 나머지는_팀별로_따로_쌓인다() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		assertEquals(0, PerkTriggers.takeWholeHunger(first, 0.6F));
		assertEquals(0, PerkTriggers.takeWholeHunger(second, 0.6F));
		assertEquals(1, PerkTriggers.takeWholeHunger(first, 0.6F));
	}

	// ------------------------------------------------------------------ 공유 풀

	/**
	 * 허기는 팀 공유 풀에 <b>한 번만</b> 더해진다.
	 *
	 * <p>이 저장소의 대표적인 사고가 「관측이 팀 인원수만큼 곱해지는」 것이다. 한 번의 피격은
	 * 한 번의 사건이고 {@code TeamState} 는 팀에 하나뿐이므로, 팀원을 도는 고리 없이 풀을 한 번만
	 * 건드리면 인원수와 무관하게 언제나 1인분이다.
	 */
	@Test
	void 허기는_공유_풀에_한_번만_더해진다() {
		List<UUID> members =
				List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		TeamState state = TeamState.fresh(20.0F);
		state.foodLevel = 0;

		int food = PerkTriggers.takeWholeHunger(UUID.randomUUID(),
				PerkTriggers.hungerGainOf(List.of(new HungerOnDamageEffect(0.4)), 5.0F));
		PerkTriggers.applyHungerToPool(state, food);

		assertEquals(2, state.foodLevel, "피해 5 × 0.4 = 2 칸");
		assertNotEquals(2 * members.size(), state.foodLevel,
				"팀원마다 한 번씩 더하면 인원수만큼 곱해진다");
	}

	@Test
	void 허기는_20에서_멈춘다() {
		TeamState state = TeamState.fresh(20.0F);
		state.foodLevel = 19;

		PerkTriggers.applyHungerToPool(state, 8);

		assertEquals(20, state.foodLevel);
	}

	@Test
	void 채울_것이_없으면_풀을_건드리지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.foodLevel = 7;

		PerkTriggers.applyHungerToPool(state, 0);
		PerkTriggers.applyHungerToPool(state, -3);

		assertEquals(7, state.foodLevel);
	}

	// ------------------------------------------------------------------ 팀 조회

	/** 증강도 세트도 이 효과를 갖고 있지 않으면 아무 일도 일어나지 않는다. */
	@Test
	void 세트도_증강도_없으면_아무_일도_없다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));
		PerkSetRegistry.clear();

		assertTrue(PerkSetRegistry.isEmpty());
		assertEquals(0.0F, PerkTriggers.hungerGainFor(teamWith("sharedfate:plain"), 10.0F), 0.0F);
		assertEquals(0.0F, PerkTriggers.hungerGainFor(teamWith("sharedfate:missing"), 10.0F), 0.0F);
		assertEquals(0.0F, PerkTriggers.hungerGainFor(TeamState.fresh(20.0F), 10.0F), 0.0F);
		assertEquals(0.0F, PerkTriggers.hungerGainFor(null, 10.0F), 0.0F);
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect raw(int index, String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return HungerOnDamageEffect.fromJson("sharedfate:테스트", index, parsed);
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
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
