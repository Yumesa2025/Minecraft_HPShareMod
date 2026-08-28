package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code on_kill} 의 정의 읽기와, 한 번의 처치가 팀 공유 풀을 얼마나 채우는지를 본다.
 *
 * <p>실제로 몹이 죽는 순간을 잡는 부분({@code AFTER_DEATH} 등록, 가해자 판별)은 살아 있는
 * 서버와 월드가 있어야 하므로 여기서는 다루지 않는다. 대신 그 코드가 부르는 계산
 * ({@link PerkKillRewards#rewardFor}, {@link PerkKillRewards#applyToPool})을 모두 확인한다.
 */
class OnKillEffectTest {

	@BeforeAll
	static void setUp() {
		// 상태이상·속성 레지스트리를 보므로 최소한의 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkKillRewards.resetForTesting();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 회복량_세_가지를_읽는다() {
		OnKillEffect effect = onKill("""
				{ "type": "on_kill", "food": 2, "saturation": 1.0, "health": 1.5 }
				""");

		assertEquals(2, effect.food());
		assertEquals(1.0F, effect.saturation());
		assertEquals(1.5F, effect.health());
		assertTrue(effect.grants().isEmpty());
		assertTrue(effect.restoresStats());
	}

	@Test
	void 적지_않은_회복량은_0_이다() {
		OnKillEffect effect = onKill("{ \"type\": \"on_kill\", \"food\": 3 }");

		assertEquals(3, effect.food());
		assertEquals(0.0F, effect.saturation());
		assertEquals(0.0F, effect.health());
	}

	@Test
	void 아무것도_주지_않는_정의는_버린다() {
		assertNull(create("{ \"type\": \"on_kill\" }"));
	}

	@Test
	void 음수나_지나친_값은_버린다() {
		assertNull(create("{ \"type\": \"on_kill\", \"food\": -1 }"));
		assertNull(create("{ \"type\": \"on_kill\", \"food\": 21 }"));
		assertNull(create("{ \"type\": \"on_kill\", \"health\": -0.5 }"));
		assertNull(create("{ \"type\": \"on_kill\", \"saturation\": 100.0 }"));
	}

	@Test
	void 숫자가_아닌_회복량은_버린다() {
		assertNull(create("{ \"type\": \"on_kill\", \"health\": \"조금\" }"));
	}

	@Test
	void 중첩하면_회복량이_배로_늘고_상한에서_멈춘다() {
		OnKillEffect effect = onKill("""
				{ "type": "on_kill", "food": 8, "saturation": 1.5, "health": 2.0 }
				""");

		assertEquals(8, effect.foodFor(1));
		assertEquals(16, effect.foodFor(2));
		assertEquals(20, effect.foodFor(3), "허기 회복량은 20 을 넘지 않는다");
		assertEquals(3.0F, effect.saturationFor(2));
		assertEquals(4.0F, effect.healthFor(2));
		assertEquals(2.0F, effect.healthFor(0), "중첩 수가 0 이하로 와도 1개로 본다");
	}

	// ------------------------------------------------------------------ 하위 효과

	@Test
	void 하위_효과를_재귀적으로_읽는다() {
		OnKillEffect effect = onKill("""
				{
				  "type": "on_kill",
				  "food": 1,
				  "effects": [
				    { "type": "status_effect", "effect": "minecraft:regeneration", "amplifier": 1,
				      "duration": 3 },
				    { "type": "damage_dealt", "multiplier": 1.2 }
				  ]
				}
				""");

		assertEquals(2, effect.grants().size());
		StatusEffectPerk status = assertInstanceOf(
				StatusEffectPerk.class, effect.grants().getFirst().effect());
		assertEquals(Identifier.parse("minecraft:regeneration"), status.effectId());
		assertEquals(1, status.amplifier());
		assertEquals(3 * OnKillEffect.TICKS_PER_SECOND, effect.grants().getFirst().durationTicks());
	}

	@Test
	void 하위_효과에_duration_을_안_적으면_기본값이다() {
		OnKillEffect effect = onKill("""
				{ "type": "on_kill", "food": 1,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:speed" } ] }
				""");

		assertEquals((int) Math.round(OnKillEffect.DEFAULT_DURATION_SECONDS * OnKillEffect.TICKS_PER_SECOND),
				effect.grants().getFirst().durationTicks());
	}

	@Test
	void 회복량이_없어도_하위_효과만_있으면_살린다() {
		OnKillEffect effect = onKill("""
				{ "type": "on_kill",
				  "effects": [ { "type": "status_effect", "effect": "minecraft:speed" } ] }
				""");

		assertFalse(effect.restoresStats());
		assertEquals(1, effect.grants().size());
	}

	@Test
	void 하위_효과가_잘못되면_증강_전체를_버린다() {
		assertNull(create("""
				{ "type": "on_kill", "food": 1, "effects": [ { "type": "없는타입" } ] }
				"""));
		assertNull(create("""
				{ "type": "on_kill", "food": 1,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:speed", "duration": 0 } ] }
				"""), "지속시간 0 은 '잠깐'이 될 수 없다");
		assertNull(create("""
				{ "type": "on_kill", "food": 1,
				  "effects": [ { "type": "status_effect", "effect": "minecraft:speed", "duration": 9999 } ] }
				"""), "너무 길면 상시나 다름없다");
		assertNull(create("{ \"type\": \"on_kill\", \"food\": 1, \"effects\": 3 }"));
		assertNull(create("{ \"type\": \"on_kill\", \"food\": 1, \"effects\": [ 3 ] }"));
	}

	@Test
	void 하위_효과의_순번은_최상위_순번과_겹치지_않는다() {
		// 순번은 속성 수정자 이름을 만드는 데 쓰인다. 겹치면 뒤에 붙은 수정자가 앞의 것을 덮는다.
		Identifier top0 = AttributeEffect.modifierId("sharedfate:x", 0);
		Identifier top1 = AttributeEffect.modifierId("sharedfate:x", 1);
		Identifier nested00 = AttributeEffect.modifierId(
				"sharedfate:x", OnKillEffect.nestedIndex(0, 0));
		Identifier nested01 = AttributeEffect.modifierId(
				"sharedfate:x", OnKillEffect.nestedIndex(0, 1));
		Identifier nested10 = AttributeEffect.modifierId(
				"sharedfate:x", OnKillEffect.nestedIndex(1, 0));

		assertNotEquals(top0, nested00);
		assertNotEquals(top1, nested00);
		assertNotEquals(nested00, nested01);
		assertNotEquals(nested00, nested10, "부모가 다르면 자식 순번도 달라야 한다");
	}

	@Test
	void 하위_효과에_붙인_순번이_실제로_수정자에_반영된다() {
		OnKillEffect effect = onKill("""
				{ "type": "on_kill", "food": 1,
				  "effects": [
				    { "type": "attribute", "attribute": "minecraft:movement_speed",
				      "operation": "ADD_MULTIPLIED_TOTAL", "amount": 0.2 }
				  ] }
				""", 0);

		AttributeEffect nested = assertInstanceOf(
				AttributeEffect.class, effect.grants().getFirst().effect());
		assertEquals(AttributeEffect.modifierId("sharedfate:테스트", OnKillEffect.nestedIndex(0, 0)),
				nested.modifierId());
		assertNotEquals(AttributeEffect.modifierId("sharedfate:테스트", 0), nested.modifierId());
	}

	// ------------------------------------------------------------------ 팀 보상 계산

	@Test
	void 증강이_없으면_아무것도_주지_않는다() {
		assertTrue(PerkKillRewards.rewardFor(null).isEmpty());
		assertTrue(PerkKillRewards.rewardFor(TeamState.fresh(20.0F)).isEmpty());
	}

	@Test
	void 처치_보상_증강을_여러_개_가지면_모두_더한다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:hunter_meal", "rarity": "silver", "name": "사냥꾼의 식사",
				      "effects": [ { "type": "on_kill", "food": 2, "saturation": 1.0 } ] },
				    { "id": "sharedfate:devour", "rarity": "platinum", "name": "포식",
				      "effects": [
				        { "type": "on_kill", "food": 1, "health": 2.0 },
				        { "type": "no_food_hunger" }
				      ] },
				    { "id": "sharedfate:unrelated", "rarity": "gold", "name": "상관없음",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(new PerkStack("sharedfate:hunter_meal", 1));
		state.ownedPerks.add(new PerkStack("sharedfate:devour", 1));
		state.ownedPerks.add(new PerkStack("sharedfate:unrelated", 1));

		PerkKillRewards.Reward reward = PerkKillRewards.rewardFor(state);

		assertEquals(3, reward.food());
		assertEquals(1.0F, reward.saturation());
		assertEquals(2.0F, reward.health());
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		write(dir, "{ \"perks\": [] }");
		PerkRegistry.load(dir);

		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(new PerkStack("sharedfate:사라진것", 1));

		assertTrue(PerkKillRewards.rewardFor(state).isEmpty());
	}

	// ------------------------------------------------------------------ 공유 풀 반영

	@Test
	void 회복은_팀_공유_값에_한_번만_더해진다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 10.0F;
		state.foodLevel = 8;
		state.saturation = 0.0F;

		PerkKillRewards.applyToPool(state, new PerkKillRewards.Reward(2, 1.0F, 1.5F));

		assertEquals(11.5F, state.health);
		assertEquals(10, state.foodLevel);
		assertEquals(1.0F, state.saturation);
	}

	@Test
	void 최대치를_넘기지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 19.0F;
		state.foodLevel = 19;
		state.saturation = 4.0F;

		PerkKillRewards.applyToPool(state, new PerkKillRewards.Reward(5, 30.0F, 5.0F));

		assertEquals(20.0F, state.health, "팀 최대 체력을 넘지 않는다");
		assertEquals(20, state.foodLevel);
		assertEquals(20.0F, state.saturation, "포만감은 허기를 넘지 못한다");
	}

	@Test
	void 포만감은_늘어난_허기까지만_찬다() {
		TeamState state = TeamState.fresh(20.0F);
		state.foodLevel = 3;
		state.saturation = 0.0F;

		PerkKillRewards.applyToPool(state, new PerkKillRewards.Reward(2, 10.0F, 0.0F));

		assertEquals(5, state.foodLevel);
		assertEquals(5.0F, state.saturation);
	}

	@Test
	void 보상이_없으면_공유_값을_건드리지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 7.0F;
		state.foodLevel = 6;
		state.saturation = 2.0F;

		PerkKillRewards.applyToPool(state, PerkKillRewards.Reward.NONE);

		assertEquals(7.0F, state.health);
		assertEquals(6, state.foodLevel);
		assertEquals(2.0F, state.saturation);
	}

	// ------------------------------------------------------------------ 도우미

	private static OnKillEffect onKill(String json) {
		return onKill(json, 0);
	}

	private static OnKillEffect onKill(String json, int index) {
		return assertInstanceOf(OnKillEffect.class, create(json, index));
	}

	private static PerkEffect create(String json) {
		return create(json, 0);
	}

	private static PerkEffect create(String json, int index) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.ON_KILL.create("sharedfate:테스트", index, parsed);
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
