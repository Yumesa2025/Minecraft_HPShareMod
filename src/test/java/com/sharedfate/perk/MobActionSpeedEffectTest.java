package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.MobActionSpeedEffect;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code mob_action_speed} 의 정의 읽기와, 배율을 <b>「틱을 몇 번 더 주는가」로 바꾸는 셈</b>을
 * 본다.
 *
 * <p>대상 한정 규칙 자체는 {@link MobPerkEffectTest} 가 이미 덮고 있으므로, 여기서는 이 타입이
 * 같은 규칙을 물려받는지와 이 타입만의 것 — 좁은 배율 범위와 비율 변환 — 을 본다. 실제로 틱을
 * 주고 거르는 일은 살아 있는 서버가 있어야 하므로 {@code MobTickRateTest} 가 셈만 따로 본다.
 */
class MobActionSpeedEffectTest {
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
		MobActionSpeedEffect effect = action("{ \"type\": \"mob_action_speed\", \"multiplier\": 1.2 }");

		assertEquals(1.2, effect.multiplier());
		assertFalse(effect.targets().hasTargetList());
		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.COW, PEACEFUL), "소에는 걸리면 안 된다");
	}

	@Test
	void excludes_는_적대적_몹_전체에서_그것만_뺀다() {
		MobActionSpeedEffect effect = action("""
				{ "type": "mob_action_speed", "multiplier": 1.2,
				  "excludes": ["minecraft:ender_dragon"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertTrue(effect.appliesTo(EntityTypes.CREEPER, HOSTILE), "크리퍼가 빠지면 이 효과의 절반이 없다");
		assertTrue(effect.appliesTo(EntityTypes.SKELETON, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE),
				"드래곤은 회차를 끝내는 조건이라 어느 쪽으로도 건드리지 않는다");
	}

	@Test
	void targets_는_적힌_것에만_걸린다() {
		MobActionSpeedEffect effect = action("""
				{ "type": "mob_action_speed", "multiplier": 0.8, "targets": ["minecraft:creeper"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.CREEPER, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
	}

	@Test
	void 플레이어는_targets_에_직접_적어도_걸리지_않는다() {
		MobActionSpeedEffect effect = action("""
				{ "type": "mob_action_speed", "multiplier": 1.5, "targets": ["minecraft:player"] }
				""");

		assertFalse(effect.appliesTo(EntityTypes.PLAYER, HOSTILE));
	}

	/**
	 * 범위가 다른 몹 효과들보다 훨씬 좁다.
	 *
	 * <p>위쪽은 ×2.0 이다. 실행부가 한 틱에 <b>많아야 한 번</b>만 더 돌리므로 그 위는 표현할
	 * 방법이 없고, 허용하면 정의에 적힌 숫자와 실제가 조용히 달라진다. 아래쪽은 ×0.1 이다 —
	 * 0 이면 그 몹은 틱을 영영 돌지 못해 불에 타지도 디스폰되지도 않고 월드에 굳은 채 쌓인다.
	 */
	@Test
	void 배율이_없거나_범위를_벗어나면_효과를_만들지_않는다() {
		assertNull(MobActionSpeedEffect.fromJson("p", 0, json("{ \"type\": \"mob_action_speed\" }")));
		assertNull(MobActionSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_action_speed\", \"multiplier\": \"빠르게\" }")));
		assertNull(MobActionSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_action_speed\", \"multiplier\": 0.0 }")),
				"0 이면 몹의 시간이 멈춰 불에 타지도 디스폰되지도 않는다");
		assertNull(MobActionSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_action_speed\", \"multiplier\": 3.0 }")),
				"한 틱에 한 번만 더 돌리므로 ×2.0 위는 표현할 수 없다");
		assertNull(MobActionSpeedEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_action_speed\", \"multiplier\": -1.0 }")));
		// 경계값은 살아남는다.
		assertEquals(MobActionSpeedEffect.MAX_MULTIPLIER,
				action("{ \"type\": \"mob_action_speed\", \"multiplier\": 2.0 }").multiplier());
		assertEquals(MobActionSpeedEffect.MIN_MULTIPLIER,
				action("{ \"type\": \"mob_action_speed\", \"multiplier\": 0.1 }").multiplier());
	}

	// ------------------------------------------------------------------ 비율 변환

	/**
	 * <b>이 시험이 「20% 빨라진다」와 실행부를 잇는 다리다.</b>
	 *
	 * <p>×1.2 는 「다섯 틱에 한 번 더 준다」, 곧 비율 0.2 다. 이 변환이 어긋나면 카드에 적힌
	 * 숫자와 실제 몹의 속도가 달라진다.
	 */
	@Test
	void 배율을_틱을_더_주는_비율로_바꾼다() {
		assertEquals(0.2, MobActionSpeedEffect.extraTickRate(1.2), 1.0e-9);
		assertEquals(0.5, MobActionSpeedEffect.extraTickRate(1.5), 1.0e-9);
		assertEquals(1.0, MobActionSpeedEffect.extraTickRate(2.0), 1.0e-9, "×2.0 은 매 틱 한 번 더다");
		assertEquals(0.0, MobActionSpeedEffect.extraTickRate(1.0), "1.0 은 아무 일도 하지 않는다");
		assertEquals(0.0, MobActionSpeedEffect.extraTickRate(0.8), "느려지는 쪽은 여기서 0 이다");
		assertEquals(1.0, MobActionSpeedEffect.extraTickRate(9.0), 1.0e-9,
				"범위를 벗어난 값이 새어 들어와도 매 틱이 상한이다");
	}

	/** 반대쪽. ×0.8 은 다섯 틱에 한 번을 거른다. */
	@Test
	void 배율을_틱을_거르는_비율로_바꾼다() {
		assertEquals(0.2, MobActionSpeedEffect.skipRate(0.8), 1.0e-9);
		assertEquals(0.5, MobActionSpeedEffect.skipRate(0.5), 1.0e-9);
		assertEquals(0.0, MobActionSpeedEffect.skipRate(1.0));
		assertEquals(0.0, MobActionSpeedEffect.skipRate(1.2), "빨라지는 쪽은 여기서 0 이다");
		assertEquals(0.9, MobActionSpeedEffect.skipRate(MobActionSpeedEffect.MIN_MULTIPLIER), 1.0e-9,
				"1.0 이 되면 몹의 시간이 멈춘다. 하한이 그것을 막는다");
		assertEquals(0.9, MobActionSpeedEffect.skipRate(0.0), 1.0e-9,
				"0 이 새어 들어와도 전부를 거르지는 않는다");
	}

	/** 합쳐진 배율이 범위를 넘으면 잘린다. 한 팀이 이 계열 증강을 여럿 가지면 곱해지기 때문이다. */
	@Test
	void 합쳐진_배율도_같은_범위로_자른다() {
		assertEquals(MobActionSpeedEffect.MAX_MULTIPLIER, MobPerkModifiers.sanitizeActionSpeed(4.0));
		assertEquals(MobActionSpeedEffect.MIN_MULTIPLIER, MobPerkModifiers.sanitizeActionSpeed(0.01));
		assertEquals(1.0, MobPerkModifiers.sanitizeActionSpeed(0.0), "이상한 값은 1.0 으로 물러난다");
		assertEquals(1.0, MobPerkModifiers.sanitizeActionSpeed(Double.NaN));
		assertEquals(1.2, MobPerkModifiers.sanitizeActionSpeed(1.2));
	}

	// ------------------------------------------------------------------ 등록 확인

	/**
	 * <b>{@code PerkEffectType} 등록을 빠뜨리면 여기서 터진다.</b>
	 *
	 * <p>등록이 없으면 증강 정의를 읽을 때 「알 수 없는 효과 type」이라 그 증강이 통째로 버려진다.
	 * 빌드는 통과하고 로그 한 줄만 남으므로 이 시험 말고는 알 방법이 없다.
	 */
	@Test
	void 효과_타입에_등록되어_있다() {
		PerkEffectType type = PerkEffectType.fromId("mob_action_speed");
		assertNotNull(type,
				"PerkEffectType 에 MOB_ACTION_SPEED(\"mob_action_speed\", "
						+ "MobActionSpeedEffect::fromJson) 을 등록해야 한다");

		JsonObject json = JsonParser
				.parseString("{ \"type\": \"mob_action_speed\", \"multiplier\": 1.2 }")
				.getAsJsonObject();
		MobActionSpeedEffect effect = assertInstanceOf(MobActionSpeedEffect.class,
				type.create("sharedfate:종결곡", 0, json), "등록은 되어 있는데 다른 팩토리가 물려 있다");
		assertEquals(1.2, effect.multiplier());
	}

	// ------------------------------------------------------------------ 증강 정의 파일

	@Test
	void 증강_파일에서_읽는다(@TempDir Path dir) throws IOException {
		try {
			Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
					{
					  "perks": [
					    { "id": "sharedfate:quickened_horde", "name": "빨라진 무리",
					      "description": "엔더 드래곤을 제외한 모든 몹의 행동이 20% 빨라집니다",
					      "rarity": "common",
					      "effects": [ { "type": "mob_action_speed", "multiplier": 1.2,
					                     "excludes": ["minecraft:ender_dragon"] } ] },
					    { "id": "sharedfate:bad_action_speed", "rarity": "common",
					      "effects": [ { "type": "mob_action_speed", "multiplier": 8.0 } ] }
					  ]
					}
					""", StandardCharsets.UTF_8);

			PerkRegistry.load(dir);

			MobActionSpeedEffect quick = assertInstanceOf(MobActionSpeedEffect.class,
					PerkRegistry.byId("sharedfate:quickened_horde").orElseThrow().effects().get(0));
			assertEquals(1.2, quick.multiplier());
			assertTrue(quick.appliesTo(EntityTypes.CREEPER, HOSTILE));
			assertFalse(quick.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));

			assertTrue(PerkRegistry.byId("sharedfate:bad_action_speed").isEmpty(),
					"배율이 잘못된 효과가 있으면 그 증강만 버린다");
		} finally {
			PerkRegistry.clear();
		}
	}

	// ------------------------------------------------------------------ 도우미

	private static MobActionSpeedEffect action(String raw) {
		return assertInstanceOf(MobActionSpeedEffect.class,
				MobActionSpeedEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}
}
