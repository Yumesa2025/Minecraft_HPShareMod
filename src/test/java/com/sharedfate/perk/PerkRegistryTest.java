package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.CustomEffect;
import com.sharedfate.perk.effect.DamageDealtEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 정의를 읽는 과정만 검증한다. 효과를 실제 플레이어에게 붙이는 부분은
 * 서버가 필요하므로 여기서는 다루지 않는다.
 */
class PerkRegistryTest {

	@BeforeAll
	static void setUp() {
		// 속성 operation 같은 마인크래프트 enum 을 건드리므로 최소한의 초기화만 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 파일이_없으면_기본_풀을_꺼내_놓는다(@TempDir Path dir) {
		PerkRegistry.load(dir);

		assertTrue(PerkRegistry.isLoaded(), "파일이 없어도 로드는 끝난 것으로 본다");
		assertTrue(Files.exists(dir.resolve(PerkRegistry.FILE_NAME)),
				"모드에 들어 있는 기본 풀이 설정 폴더로 나와야 한다");
		assertFalse(PerkRegistry.all().isEmpty(), "기본 풀에는 증강이 들어 있다");
		assertTrue(PerkRegistry.byId("sharedfate:없는것").isEmpty());
	}

	@Test
	void 꺼내_놓은_기본_풀을_그대로_다시_읽는다(@TempDir Path dir) {
		PerkRegistry.load(dir);
		int first = PerkRegistry.all().size();

		// 두 번째 로드는 파일이 이미 있으므로 덮어쓰지 않고 그대로 읽어야 한다.
		PerkRegistry.load(dir);

		assertEquals(first, PerkRegistry.all().size());
	}

	@Test
	void 다섯_가지_효과_타입을_모두_읽는다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    {
				      "id": "sharedfate:tough_body",
				      "name": "강골",
				      "description": "팀 최대 체력 +2",
				      "rarity": "common",
				      "stackable": true,
				      "maxStacks": 3,
				      "effects": [
				        { "type": "attribute", "attribute": "minecraft:max_health",
				          "operation": "add_value", "amount": 2.0 }
				      ]
				    },
				    {
				      "id": "sharedfate:glass_cannon",
				      "rarity": "rare",
				      "effects": [
				        { "type": "damage_dealt", "multiplier": 1.25 },
				        { "type": "damage_taken", "multiplier": 1.35 }
				      ]
				    },
				    {
				      "id": "sharedfate:blessed_pace",
				      "rarity": "epic",
				      "effects": [
				        { "type": "status_effect", "effect": "minecraft:speed", "amplifier": 1 },
				        { "type": "custom", "handler": "sharedfate:example_handler" }
				      ]
				    }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		List<Perk> all = PerkRegistry.all();
		assertEquals(3, all.size());
		assertEquals("sharedfate:tough_body", all.get(0).id(), "파일에 적힌 순서를 지켜야 한다");

		Perk tough = PerkRegistry.byId("sharedfate:tough_body").orElseThrow();
		assertEquals("강골", tough.name());
		assertEquals("팀 최대 체력 +2", tough.description());
		assertEquals(PerkRarity.SILVER, tough.rarity());
		AttributeEffect attribute = assertInstanceOf(AttributeEffect.class, tough.effects().get(0));
		assertEquals("minecraft:max_health", attribute.attributeId().toString());
		assertEquals(AttributeModifier.Operation.ADD_VALUE, attribute.operation());
		assertEquals(2.0, attribute.amount());

		Perk cannon = PerkRegistry.byId("sharedfate:glass_cannon").orElseThrow();
		assertEquals("sharedfate:glass_cannon", cannon.name(), "이름이 없으면 id 를 쓴다");
		assertEquals(PerkRarity.GOLD, cannon.rarity());
		assertEquals(1.25, assertInstanceOf(DamageDealtEffect.class, cannon.effects().get(0)).multiplier());
		assertEquals(1.35, assertInstanceOf(DamageTakenEffect.class, cannon.effects().get(1)).multiplier());

		Perk pace = PerkRegistry.byId("sharedfate:blessed_pace").orElseThrow();
		assertEquals(PerkRarity.PLATINUM, pace.rarity());
		StatusEffectPerk status = assertInstanceOf(StatusEffectPerk.class, pace.effects().get(0));
		assertEquals("minecraft:speed", status.effectId().toString());
		assertEquals(1, status.amplifier());
		assertEquals("sharedfate:example_handler",
				assertInstanceOf(CustomEffect.class, pace.effects().get(1)).handlerId());
	}

	@Test
	void 잘못된_항목만_건너뛴다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    "이건 객체가 아니다",
				    { "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:bad_rarity", "rarity": "전설",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:no_effects", "rarity": "common", "effects": [] },
				    { "id": "sharedfate:bad_attribute", "rarity": "common",
				      "effects": [ { "type": "attribute", "attribute": "minecraft:max_health",
				                     "operation": "곱하기", "amount": 2.0 } ] },
				    { "id": "sharedfate:bad_multiplier", "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": "많이" } ] },
				    { "id": "sharedfate:good", "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		assertEquals(List.of("sharedfate:good"), PerkRegistry.all().stream().map(Perk::id).toList());
	}

	@Test
	void 알_수_없는_type_은_그_증강만_버린다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:unknown_type", "rarity": "common",
				      "effects": [ { "type": "텔레포트", "amount": 1 } ] },
				    { "id": "sharedfate:partly_unknown", "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 },
				                   { "type": "텔레포트" } ] },
				    { "id": "sharedfate:ok", "rarity": "common",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		assertEquals(List.of("sharedfate:ok"), PerkRegistry.all().stream().map(Perk::id).toList());
		assertTrue(PerkRegistry.byId("sharedfate:partly_unknown").isEmpty(),
				"효과 하나라도 잘못되면 증강 전체를 버린다");
	}

	@Test
	void 등록되지_않은_handler_는_무시된다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:custom", "rarity": "epic",
				      "effects": [ { "type": "custom", "handler": "sharedfate:없는핸들러" } ] },
				    { "id": "sharedfate:no_handler_field", "rarity": "epic",
				      "effects": [ { "type": "custom" } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		Perk perk = PerkRegistry.byId("sharedfate:custom").orElseThrow();
		CustomEffect effect = assertInstanceOf(CustomEffect.class, perk.effects().get(0));
		assertFalse(effect.isResolved());
		assertEquals(1.0, effect.damageDealtMultiplier(), "핸들러가 없으면 배율은 그대로 1.0");
		assertEquals(1.0, effect.damageTakenMultiplier());
		effect.apply(null);
		effect.remove(null);

		assertTrue(PerkRegistry.byId("sharedfate:no_handler_field").isEmpty(),
				"handler 필드 자체가 없으면 정의가 잘못된 것이다");
	}

	@Test
	void 나중에_등록한_handler_에_위임한다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:custom", "rarity": "epic",
				      "effects": [ { "type": "custom", "handler": "sharedfate:double_damage" } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);

		PerkRegistry.registerCustom("sharedfate:double_damage", new PerkEffect() {
			@Override
			public double damageDealtMultiplier() {
				return 2.0;
			}
		});

		CustomEffect effect = assertInstanceOf(CustomEffect.class,
				PerkRegistry.byId("sharedfate:custom").orElseThrow().effects().get(0));
		assertTrue(effect.isResolved());
		assertEquals(2.0, effect.damageDealtMultiplier(), "핸들러가 돌려준 값이 그대로 나와야 한다");
	}

	@Test
	void 핸들러가_이상한_값을_돌려줘도_피해_계산은_안전하다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:custom", "rarity": "epic",
				      "effects": [ { "type": "custom", "handler": "sharedfate:broken" } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);
		PerkRegistry.registerCustom("sharedfate:broken", new PerkEffect() {
			@Override
			public double damageDealtMultiplier() {
				return Double.NaN;
			}

			@Override
			public double damageTakenMultiplier() {
				throw new IllegalStateException("일부러 터뜨린다");
			}
		});

		CustomEffect effect = assertInstanceOf(CustomEffect.class,
				PerkRegistry.byId("sharedfate:custom").orElseThrow().effects().get(0));
		assertEquals(1.0, effect.damageDealtMultiplier());
		assertEquals(1.0, effect.damageTakenMultiplier());
	}

	@Test
	void 깨진_파일이면_빈_풀로_시작한다(@TempDir Path dir) throws IOException {
		write(dir, "{ 이건 JSON이 아니다");

		PerkRegistry.load(dir);

		assertTrue(PerkRegistry.isLoaded());
		assertTrue(PerkRegistry.all().isEmpty());
	}

	@Test
	void perks_배열이_없으면_빈_풀로_시작한다(@TempDir Path dir) throws IOException {
		write(dir, "{ \"목록\": [] }");

		PerkRegistry.load(dir);

		assertTrue(PerkRegistry.isLoaded());
		assertTrue(PerkRegistry.all().isEmpty());
	}

	@Test
	void 중복_id_는_처음_것만_남긴다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:dup", "name": "먼저", "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:dup", "name": "나중", "rarity": "epic",
				      "effects": [ { "type": "damage_dealt", "multiplier": 9.0 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		assertEquals(1, PerkRegistry.all().size());
		assertEquals("먼저", PerkRegistry.byId("sharedfate:dup").orElseThrow().name());
	}

	@Test
	void 피해_배율은_정의에_적힌_값_그대로다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:trade_off", "rarity": "rare",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 },
				                   { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		List<PerkEffect> effects = PerkRegistry.byId("sharedfate:trade_off").orElseThrow().effects();
		assertEquals(1.2, effects.get(0).damageDealtMultiplier(), 1.0e-9);
		assertEquals(1.0, effects.get(0).damageTakenMultiplier(), "받는 피해에는 관여하지 않는다");
		assertEquals(0.9, effects.get(1).damageTakenMultiplier(), 1.0e-9);
		assertEquals(1.0, effects.get(1).damageDealtMultiplier(), "주는 피해에는 관여하지 않는다");
	}

	@Test
	void 상태이상_등급은_정의에_적힌_값_그대로다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:haste", "rarity": "rare",
				      "effects": [ { "type": "status_effect", "effect": "haste", "amplifier": 1 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		StatusEffectPerk effect = assertInstanceOf(StatusEffectPerk.class,
				PerkRegistry.byId("sharedfate:haste").orElseThrow().effects().get(0));
		assertEquals("minecraft:haste", effect.effectId().toString(), "이름공간을 생략하면 minecraft 로 본다");
		assertEquals(1, effect.amplifier());
	}

	@Test
	void 예전_형식의_stackable_과_maxStacks_는_오류_없이_무시된다(@TempDir Path dir) throws IOException {
		// 중첩이 있던 시절에 적어 둔 파일이 서버에 그대로 남아 있을 수 있다. 그 파일도 열려야 한다.
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:legacy", "name": "옛 형식", "rarity": "silver",
				      "stackable": true, "maxStacks": 7,
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				    { "id": "sharedfate:legacy_bad_type", "rarity": "silver",
				      "stackable": "예", "maxStacks": "많이",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		assertEquals(2, PerkRegistry.all().size(), "옛 필드가 있어도 증강은 그대로 읽힌다");
		Perk legacy = PerkRegistry.byId("sharedfate:legacy").orElseThrow();
		assertEquals("옛 형식", legacy.name());
		assertEquals(PerkRarity.SILVER, legacy.rarity());
		assertEquals(1.1, assertInstanceOf(DamageDealtEffect.class, legacy.effects().getFirst())
				.multiplier(), 1.0e-9);
	}

	@Test
	void 속성_수정자_식별자는_증강마다_다르다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:one", "rarity": "common",
				      "effects": [ { "type": "attribute", "attribute": "minecraft:max_health",
				                     "operation": "add_value", "amount": 2.0 },
				                   { "type": "attribute", "attribute": "minecraft:armor",
				                     "operation": "add_value", "amount": 1.0 } ] },
				    { "id": "sharedfate:two", "rarity": "common",
				      "effects": [ { "type": "attribute", "attribute": "minecraft:max_health",
				                     "operation": "add_value", "amount": 4.0 } ] }
				  ]
				}
				""");

		PerkRegistry.load(dir);

		AttributeEffect first = assertInstanceOf(AttributeEffect.class,
				PerkRegistry.byId("sharedfate:one").orElseThrow().effects().get(0));
		AttributeEffect second = assertInstanceOf(AttributeEffect.class,
				PerkRegistry.byId("sharedfate:one").orElseThrow().effects().get(1));
		AttributeEffect other = assertInstanceOf(AttributeEffect.class,
				PerkRegistry.byId("sharedfate:two").orElseThrow().effects().get(0));

		assertEquals("sharedfate:perk/sharedfate_one/0", first.modifierId().toString());
		assertNotEquals(first.modifierId(), second.modifierId(), "같은 증강 안에서도 효과마다 달라야 한다");
		assertNotEquals(first.modifierId(), other.modifierId(), "증강이 다르면 달라야 한다");
	}

	@Test
	void 다시_읽으면_이전_목록을_대체한다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:first", "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);
		assertEquals(1, PerkRegistry.all().size());

		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:second", "rarity": "rare",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);

		assertEquals(List.of("sharedfate:second"), PerkRegistry.all().stream().map(Perk::id).toList());
	}

	@Test
	void clear_는_상태를_비운다(@TempDir Path dir) throws IOException {
		write(dir, """
				{
				  "perks": [
				    { "id": "sharedfate:one", "rarity": "common",
				      "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				  ]
				}
				""");
		PerkRegistry.load(dir);
		PerkRegistry.registerCustom("sharedfate:handler", new PerkEffect() {
		});

		PerkRegistry.clear();

		assertFalse(PerkRegistry.isLoaded());
		assertTrue(PerkRegistry.all().isEmpty());
		assertTrue(PerkRegistry.customHandler("sharedfate:handler").isEmpty());
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
