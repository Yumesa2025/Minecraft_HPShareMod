package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.SharedFateMod;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.MobSpeedEffect;
import com.sharedfate.sync.DifficultyEscalation;
import net.minecraft.world.entity.EntityTypes;
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
 * {@code mob_speed} 의 정의 읽기와 대상 한정을 본다.
 *
 * <p>{@code mob_health} 를 그대로 본떴으므로 대상 한정 규칙 자체는
 * {@link MobPerkEffectTest} 가 이미 덮고 있다. 여기서는 이 타입이 같은 규칙을 실제로 물려받는지와,
 * 배율 범위·수정자 식별자처럼 이 타입만의 것을 본다. 몹에게 속성 수정자를 붙이는 부분은 살아 있는
 * 서버와 월드가 있어야 하므로 여기서 다루지 않는다.
 */
class MobSpeedEffectTest {
	private static final boolean HOSTILE = true;
	private static final boolean PEACEFUL = false;

	@BeforeAll
	static void setUp() {
		// 엔티티 레지스트리를 봐야 하므로 최소한의 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 배율만_적으면_적대적_몹_전체가_대상이다() {
		MobSpeedEffect effect = speed("{ \"type\": \"mob_speed\", \"multiplier\": 1.15 }");

		assertEquals(1.15, effect.multiplier());
		assertFalse(effect.targets().hasTargetList());
		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.COW, PEACEFUL), "소에는 걸리면 안 된다");
	}

	@Test
	void excludes_는_적대적_몹_전체에서_그것만_뺀다() {
		MobSpeedEffect effect = speed("""
				{ "type": "mob_speed", "multiplier": 1.15,
				  "excludes": ["minecraft:ender_dragon"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertTrue(effect.appliesTo(EntityTypes.SKELETON, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));
	}

	@Test
	void targets_는_적힌_것에만_걸린다() {
		MobSpeedEffect effect = speed("""
				{ "type": "mob_speed", "multiplier": 0.8, "targets": ["minecraft:creeper"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.CREEPER, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
	}

	@Test
	void 플레이어는_targets_에_직접_적어도_걸리지_않는다() {
		MobSpeedEffect effect = speed("""
				{ "type": "mob_speed", "multiplier": 0.5, "targets": ["minecraft:player"] }
				""");

		assertFalse(effect.appliesTo(EntityTypes.PLAYER, HOSTILE));
	}

	@Test
	void 배율이_없거나_범위를_벗어나면_효과를_만들지_않는다() {
		assertNull(MobSpeedEffect.fromJson("p", 0, json("{ \"type\": \"mob_speed\" }")));
		assertNull(MobSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_speed\", \"multiplier\": \"빠르게\" }")));
		assertNull(MobSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_speed\", \"multiplier\": 0.0 }")),
				"이동 속도 0 은 몹을 제자리에 굳힌다");
		assertNull(MobSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_speed\", \"multiplier\": 64.0 }")),
				"이동 속도는 체력과 달리 조금만 커져도 판이 성립하지 않는다");
		assertNull(MobSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_speed\", \"multiplier\": -1.0 }")));
	}

	@Test
	void 배율은_정의에_적힌_값_그대로다() {
		MobSpeedEffect effect = speed("{ \"type\": \"mob_speed\", \"multiplier\": 1.15 }");

		assertEquals(1.15, effect.multiplierFor(), 1.0e-9);
		assertEquals(1.15, effect.multiplierFor(), 1.0e-9, "몇 번을 물어도 같은 값이다");
	}

	@Test
	void 팀원에게는_아무것도_하지_않는다() {
		MobSpeedEffect effect = speed("{ \"type\": \"mob_speed\", \"multiplier\": 1.15 }");

		effect.apply(null);
		effect.remove(null);
		assertEquals(1.0, effect.damageDealtMultiplier());
		assertEquals(1.0, effect.damageTakenMultiplier());
	}

	// ------------------------------------------------------------------ 수정자 식별자

	@Test
	void 수정자_식별자가_체력이나_난이도와_겹치지_않는다() {
		// 같은 식별자를 쓰면 나중에 붙는 쪽이 앞의 것을 조용히 덮어쓴다.
		assertEquals(SharedFateMod.id("perk/mob_speed"), MobPerkModifiers.SPEED_MODIFIER_ID);
		assertNotEquals(MobPerkModifiers.HEALTH_MODIFIER_ID, MobPerkModifiers.SPEED_MODIFIER_ID);
		assertNotEquals(DifficultyEscalation.HEALTH_MODIFIER_ID,
				MobPerkModifiers.SPEED_MODIFIER_ID);
	}

	// ------------------------------------------------------------------ 증강 정의 파일

	@Test
	void 증강_파일에서_읽는다(@TempDir Path dir) throws IOException {
		try {
			Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
					{
					  "perks": [
					    { "id": "sharedfate:swift_horde", "name": "빠른 무리",
					      "description": "엔더 드래곤을 제외한 모든 몹의 이동 속도가 15% 빨라집니다",
					      "rarity": "common",
					      "effects": [ { "type": "mob_speed", "multiplier": 1.15,
					                     "excludes": ["minecraft:ender_dragon"] } ] },
					    { "id": "sharedfate:bad_speed", "rarity": "common",
					      "effects": [ { "type": "mob_speed", "multiplier": 0.0 } ] }
					  ]
					}
					""", StandardCharsets.UTF_8);

			PerkRegistry.load(dir);

			MobSpeedEffect swift = assertInstanceOf(MobSpeedEffect.class,
					PerkRegistry.byId("sharedfate:swift_horde").orElseThrow().effects().get(0));
			assertEquals(1.15, swift.multiplier());
			assertTrue(swift.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
			assertFalse(swift.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));

			assertTrue(PerkRegistry.byId("sharedfate:bad_speed").isEmpty(),
					"배율이 잘못된 효과가 있으면 그 증강만 버린다");
		} finally {
			PerkRegistry.clear();
		}
	}

	// ------------------------------------------------------------------ 도우미

	private static MobSpeedEffect speed(String raw) {
		return assertInstanceOf(MobSpeedEffect.class,
				MobSpeedEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}
}
