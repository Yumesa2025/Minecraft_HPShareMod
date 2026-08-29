package com.sharedfate.perk;

import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.MaxHealthLockEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code max_health_lock} 의 정의 읽기와 고정값 계산을 본다.
 *
 * <p>속성 수정자를 실제로 거는 자리는 {@code MaxHealthAttribute} 이고 그건 살아 있는 플레이어가
 * 있어야 확인할 수 있다. 여기서는 {@link PerkHealthRules#lockedMaxHealth} 가 무엇을 고정값으로
 * 고르는지만 확인한다.
 */
class MaxHealthLockEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 고정값을_읽는다() {
		PerkEffect effect = PerkEffectType.MAX_HEALTH_LOCK.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"max_health_lock\", \"value\": 10.0 }")
						.getAsJsonObject());

		assertInstanceOf(MaxHealthLockEffect.class, effect);
		assertEquals(10.0F, ((MaxHealthLockEffect) effect).value());
	}

	@Test
	void 값이_없거나_범위를_벗어나면_버린다() {
		assertNull(PerkEffectType.MAX_HEALTH_LOCK.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"max_health_lock\" }").getAsJsonObject()));
		assertNull(PerkEffectType.MAX_HEALTH_LOCK.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"max_health_lock\", \"value\": 0.0 }")
						.getAsJsonObject()));
		assertNull(PerkEffectType.MAX_HEALTH_LOCK.create("sharedfate:테스트", 0,
				JsonParser.parseString("{ \"type\": \"max_health_lock\", \"value\": 99999.0 }")
						.getAsJsonObject()));
	}

	@Test
	void 증강이_없으면_고정값도_없다() {
		assertTrue(PerkHealthRules.lockedMaxHealth(null).isEmpty());
		assertTrue(PerkHealthRules.lockedMaxHealth(TeamState.fresh(20.0F)).isEmpty());
	}

	@Test
	void 고정_증강이_있으면_그_값이다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:고행자");

		assertEquals(OptionalDouble.of(10.0), PerkHealthRules.lockedMaxHealth(state));
	}

	@Test
	void 여러_개면_가장_작은_값이_이긴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:반쪽");
		state.ownedPerks.add("sharedfate:고행자");

		assertEquals(OptionalDouble.of(10.0), PerkHealthRules.lockedMaxHealth(state),
				"고정은 전부 대가라, 더 후한 쪽이 이기면 대가를 지우는 조합이 생긴다");
	}

	@Test
	void 최대_체력을_올리는_증강만_있으면_고정값이_없다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:피통");

		assertTrue(PerkHealthRules.lockedMaxHealth(state).isEmpty());
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:사라진것");

		assertTrue(PerkHealthRules.lockedMaxHealth(state).isEmpty());
	}

	@Test
	void 증강을_잃으면_고정도_사라진다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:고행자");
		assertFalse(PerkHealthRules.lockedMaxHealth(state).isEmpty());

		// 회차 리셋은 팀 상태를 통째로 새로 만든다. 그때 보유 증강도 최대 체력도 함께 돌아온다.
		TeamState reborn = TeamState.fresh(20.0F);
		assertTrue(PerkHealthRules.lockedMaxHealth(reborn).isEmpty());
		assertEquals(20.0F, reborn.maxHealth);
	}

	/** 고정 둘과, 최대 체력을 올리는 증강 하나를 담은 풀. */
	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:고행자", "rarity": "prism", "name": "고행자",
				      "effects": [
				        { "type": "no_hunger_drain" },
				        { "type": "max_health_lock", "value": 10.0 }
				      ] },
				    { "id": "sharedfate:반쪽", "rarity": "gold", "name": "반쪽",
				      "effects": [ { "type": "max_health_lock", "value": 14.0 } ] },
				    { "id": "sharedfate:피통", "rarity": "silver", "name": "뚝배기 대신 피통",
				      "effects": [
				        { "type": "attribute", "attribute": "minecraft:max_health",
				          "operation": "add_value", "amount": 6.0 }
				      ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
