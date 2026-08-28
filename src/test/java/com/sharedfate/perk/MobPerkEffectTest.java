package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.MobDamageEffect;
import com.sharedfate.perk.effect.MobHealthEffect;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code mob_health} / {@code mob_damage} 의 정의 읽기와 대상 한정, 그리고 여러 팀의 배율을
 * 하나로 합치는 규칙을 본다.
 *
 * <p>실제로 몹에게 수정자를 붙이는 부분은 살아 있는 서버와 월드가 있어야 하므로 여기서는
 * 다루지 않는다. 대신 그 코드가 쓰는 판단들(어떤 몹이 대상인지, 배율이 얼마인지)을 모두
 * 여기서 확인한다.
 */
class MobPerkEffectTest {
	/** 적대적 몹인지 판단하는 기준. 실제 코드도 {@code mob instanceof Enemy} 를 쓴다. */
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
		MobHealthEffect effect = health("{ \"type\": \"mob_health\", \"multiplier\": 0.5 }");

		assertEquals(0.5, effect.multiplier());
		assertFalse(effect.targets().hasTargetList());
		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertTrue(effect.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.COW, PEACEFUL), "소에는 걸리면 안 된다");
		assertFalse(effect.appliesTo(EntityTypes.VILLAGER, PEACEFUL), "주민에는 걸리면 안 된다");
	}

	@Test
	void excludes_는_적대적_몹_전체에서_그것만_뺀다() {
		MobHealthEffect effect = health("""
				{ "type": "mob_health", "multiplier": 0.5,
				  "excludes": ["minecraft:ender_dragon"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertTrue(effect.appliesTo(EntityTypes.SKELETON, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.COW, PEACEFUL), "제외 목록을 적어도 소는 여전히 대상이 아니다");
	}

	@Test
	void targets_는_적힌_것에만_걸린다() {
		MobDamageEffect effect = damage("""
				{ "type": "mob_damage", "multiplier": 0.85,
				  "targets": ["minecraft:zombie", "minecraft:skeleton"] }
				""");

		assertEquals(0.85, effect.multiplier());
		assertTrue(effect.targets().hasTargetList());
		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertTrue(effect.appliesTo(EntityTypes.SKELETON, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.CREEPER, HOSTILE), "적히지 않은 몹은 대상이 아니다");
	}

	@Test
	void targets_에_적으면_적대적이지_않은_몹도_대상이_된다() {
		MobHealthEffect effect = health("""
				{ "type": "mob_health", "multiplier": 2.0, "targets": ["minecraft:cow"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.COW, PEACEFUL));
		assertFalse(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
	}

	@Test
	void 플레이어는_targets_에_직접_적어도_걸리지_않는다() {
		MobHealthEffect effect = health("""
				{ "type": "mob_health", "multiplier": 0.1, "targets": ["minecraft:player"] }
				""");

		assertFalse(effect.appliesTo(EntityTypes.PLAYER, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.PLAYER, PEACEFUL));
	}

	@Test
	void 알_수_없는_엔티티는_그것만_버리고_나머지는_살린다() {
		MobDamageEffect effect = damage("""
				{ "type": "mob_damage", "multiplier": 0.5,
				  "targets": ["minecraft:zombie", "minecraft:없는몹", "이건:이름이_아니다", 42] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertEquals(1, effect.targets().targets().size(), "쓸 수 있는 이름만 남아야 한다");
	}

	@Test
	void targets_가_전부_알_수_없으면_아무에게도_걸리지_않는다() {
		MobDamageEffect effect = damage("""
				{ "type": "mob_damage", "multiplier": 0.5, "targets": ["minecraft:없는몹"] }
				""");

		assertFalse(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE),
				"targets 를 적었는데 남은 게 없으면 적대적 몹 전체로 넓어지면 안 된다");
		assertFalse(effect.appliesTo(EntityTypes.COW, PEACEFUL));
	}

	@Test
	void excludes_의_알_수_없는_엔티티도_그것만_버린다() {
		MobHealthEffect effect = health("""
				{ "type": "mob_health", "multiplier": 0.5,
				  "excludes": ["minecraft:없는몹", "minecraft:ender_dragon"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertFalse(effect.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));
	}

	@Test
	void 이름_모양은_맞지만_레지스트리에_없는_엔티티도_버린다() {
		MobDamageEffect effect = damage("""
				{ "type": "mob_damage", "multiplier": 0.5,
				  "targets": ["minecraft:zombie", "minecraft:definitely_not_a_mob"] }
				""");

		assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
		assertEquals(1, effect.targets().targets().size(),
				"레지스트리에 없는 이름은 처음 쓸 때 걸러진다");
	}

	@Test
	void targets_가_배열이_아니면_아무에게도_걸리지_않는다() {
		MobDamageEffect effect = damage("""
				{ "type": "mob_damage", "multiplier": 0.5, "targets": "minecraft:zombie" }
				""");

		assertTrue(effect.targets().hasTargetList());
		assertFalse(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
	}

	// ------------------------------------------------------------------ 배율

	@Test
	void 배율이_없거나_범위를_벗어나면_효과를_만들지_않는다() {
		assertNull(MobHealthEffect.fromJson("p", 0, json("{ \"type\": \"mob_health\" }")));
		assertNull(MobHealthEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_health\", \"multiplier\": \"절반\" }")));
		assertNull(MobHealthEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_health\", \"multiplier\": 0.0 }")),
				"최대 체력이 0인 몹은 존재할 수 없다");
		assertNull(MobHealthEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_health\", \"multiplier\": 1000.0 }")));
		assertNull(MobDamageEffect.fromJson("p", 0,
				json("{ \"type\": \"mob_damage\", \"multiplier\": -1.0 }")));
	}

	@Test
	void 피해_배율은_0을_허용한다() {
		MobDamageEffect effect = damage("{ \"type\": \"mob_damage\", \"multiplier\": 0.0 }");

		assertEquals(0.0, effect.multiplierFor(1));
	}

	@Test
	void 중첩은_거듭제곱으로_쌓인다() {
		MobHealthEffect effect = health("{ \"type\": \"mob_health\", \"multiplier\": 0.8 }");

		assertEquals(0.8, effect.multiplierFor(1), 1.0e-9);
		assertEquals(0.64, effect.multiplierFor(2), 1.0e-9);
		assertEquals(0.8, effect.multiplierFor(0), 1.0e-9, "중첩 0도 1중첩으로 본다");
	}

	@Test
	void 몹_효과는_팀원의_피해_배율에_관여하지_않는다() {
		MobHealthEffect healthEffect = health("{ \"type\": \"mob_health\", \"multiplier\": 0.5 }");
		MobDamageEffect damageEffect = damage("{ \"type\": \"mob_damage\", \"multiplier\": 0.5 }");

		assertEquals(1.0, healthEffect.damageDealtMultiplier(3));
		assertEquals(1.0, healthEffect.damageTakenMultiplier(3));
		assertEquals(1.0, damageEffect.damageDealtMultiplier(3),
				"몹의 피해는 PerkDamage 가 따로 조회한다. 팀원의 주는 피해와 섞이면 안 된다");
		assertEquals(1.0, damageEffect.damageTakenMultiplier(3));
	}

	@Test
	void 몹_효과는_팀원에게_아무것도_붙이지_않는다() {
		// apply/remove 를 불러도 터지지 않고 아무 일도 하지 않아야 한다.
		health("{ \"type\": \"mob_health\", \"multiplier\": 0.5 }").apply(null, 1);
		health("{ \"type\": \"mob_health\", \"multiplier\": 0.5 }").remove(null);
		damage("{ \"type\": \"mob_damage\", \"multiplier\": 0.5 }").apply(null, 1);
		damage("{ \"type\": \"mob_damage\", \"multiplier\": 0.5 }").remove(null);
	}

	// ------------------------------------------------------------------ 여러 팀 합치기

	@Test
	void 팀이_여럿이면_1에서_가장_먼_배율_하나만_고른다() {
		assertEquals(0.5, MobPerkModifiers.stronger(1.0, 0.5));
		assertEquals(0.5, MobPerkModifiers.stronger(0.5, 1.0));
		assertEquals(0.25, MobPerkModifiers.stronger(0.5, 0.25));
		assertEquals(2.0, MobPerkModifiers.stronger(1.5, 2.0),
				"1보다 큰 쪽(몹 강화)도 같은 기준으로 다룬다");
	}

	@Test
	void 배율을_고르는_순서는_결과를_바꾸지_않는다() {
		double[] values = {1.0, 0.5, 1.5, 0.9};

		double forward = 1.0;
		for (int i = 0; i < values.length; i++) {
			forward = MobPerkModifiers.stronger(forward, values[i]);
		}
		double backward = 1.0;
		for (int i = values.length - 1; i >= 0; i--) {
			backward = MobPerkModifiers.stronger(backward, values[i]);
		}

		assertEquals(forward, backward, "팀 목록을 훑는 순서에 결과가 좌우되면 안 된다");
		assertEquals(0.5, forward);
	}

	@Test
	void 세기가_같으면_플레이어에게_유리한_쪽을_고른다() {
		// 2배와 0.5배는 로그 눈금에서 거리가 같다. 몹이 약해지는 쪽을 고른다.
		assertEquals(0.5, MobPerkModifiers.stronger(2.0, 0.5));
		assertEquals(0.5, MobPerkModifiers.stronger(0.5, 2.0));
	}

	@Test
	void 팀이_배율을_안_가지면_1이_남는다() {
		assertEquals(1.0, MobPerkModifiers.stronger(1.0, 1.0));
	}

	@Test
	void 이상한_배율은_1로_물러난다() {
		assertEquals(1.0, MobPerkModifiers.sanitizeHealth(Double.NaN));
		assertEquals(1.0, MobPerkModifiers.sanitizeHealth(Double.POSITIVE_INFINITY));
		assertEquals(1.0, MobPerkModifiers.sanitizeHealth(0.0), "최대 체력 배율은 0이 될 수 없다");
		assertEquals(1.0, MobPerkModifiers.sanitizeHealth(-1.0));
		assertEquals(1.0, MobPerkModifiers.sanitizeDamage(Double.NaN));
		assertEquals(1.0, MobPerkModifiers.sanitizeDamage(-1.0));
		assertEquals(0.0, MobPerkModifiers.sanitizeDamage(0.0), "피해 배율 0은 정상이다");
		assertEquals(MobPerkModifiers.MAX_MULTIPLIER, MobPerkModifiers.sanitizeHealth(1.0e30));
		assertEquals(MobPerkModifiers.MIN_HEALTH_MULTIPLIER,
				MobPerkModifiers.sanitizeHealth(1.0e-30));
	}

	@Test
	void 몹이_아닌_가해자에게는_배율을_묻지_않는다() {
		assertEquals(1.0, MobPerkModifiers.damageMultiplier(null));
	}

	// ------------------------------------------------------------------ 적대 판정

	@Test
	void 적대적_몹_판정은_Enemy_구현_여부다() {
		assertTrue(Enemy.class.isAssignableFrom(Zombie.class));
		assertTrue(Enemy.class.isAssignableFrom(Skeleton.class));
		assertTrue(Enemy.class.isAssignableFrom(EnderDragon.class),
				"엔더 드래곤을 excludes 로 뺄 수 있으려면 기본 대상에 들어 있어야 한다");
		assertFalse(Enemy.class.isAssignableFrom(Cow.class));
		assertFalse(Enemy.class.isAssignableFrom(Villager.class));
	}

	// ------------------------------------------------------------------ 증강 정의 파일

	@Test
	void 증강_파일에서_두_타입을_읽는다(@TempDir Path dir) throws IOException {
		try {
			Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
					{
					  "perks": [
					    { "id": "sharedfate:frail_horde", "name": "약골 무리",
					      "description": "엔더 드래곤을 제외한 모든 몹의 체력이 절반이 됩니다",
					      "rarity": "common",
					      "effects": [ { "type": "mob_health", "multiplier": 0.5,
					                     "excludes": ["minecraft:ender_dragon"] } ] },
					    { "id": "sharedfate:blunt_undead", "rarity": "common",
					      "description": "좀비와 스켈레톤의 공격력이 15% 감소합니다",
					      "effects": [ { "type": "mob_damage", "multiplier": 0.85,
					                     "targets": ["minecraft:zombie", "minecraft:skeleton"] } ] },
					    { "id": "sharedfate:bad_multiplier", "rarity": "common",
					      "effects": [ { "type": "mob_health", "multiplier": 0.0 } ] }
					  ]
					}
					""", StandardCharsets.UTF_8);

			PerkRegistry.load(dir);

			MobHealthEffect frail = assertInstanceOf(MobHealthEffect.class,
					PerkRegistry.byId("sharedfate:frail_horde").orElseThrow().effects().get(0));
			assertEquals(0.5, frail.multiplier());
			assertTrue(frail.appliesTo(EntityTypes.ZOMBIE, HOSTILE));
			assertFalse(frail.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE));

			MobDamageEffect blunt = assertInstanceOf(MobDamageEffect.class,
					PerkRegistry.byId("sharedfate:blunt_undead").orElseThrow().effects().get(0));
			assertEquals(0.85, blunt.multiplier());
			assertTrue(blunt.appliesTo(EntityTypes.SKELETON, HOSTILE));
			assertFalse(blunt.appliesTo(EntityTypes.CREEPER, HOSTILE));

			assertTrue(PerkRegistry.byId("sharedfate:bad_multiplier").isEmpty(),
					"배율이 잘못된 효과가 있으면 그 증강만 버린다");
		} finally {
			PerkRegistry.clear();
		}
	}

	@Test
	void 알_수_없는_엔티티가_섞여도_증강은_살아남는다(@TempDir Path dir) throws IOException {
		try {
			Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
					{
					  "perks": [
					    { "id": "sharedfate:typo", "rarity": "common",
					      "effects": [ { "type": "mob_health", "multiplier": 0.5,
					                     "excludes": ["minecraft:엔더드래곤"] } ] }
					  ]
					}
					""", StandardCharsets.UTF_8);

			PerkRegistry.load(dir);

			MobHealthEffect effect = assertInstanceOf(MobHealthEffect.class,
					PerkRegistry.byId("sharedfate:typo").orElseThrow().effects().get(0));
			assertTrue(effect.appliesTo(EntityTypes.ZOMBIE, HOSTILE),
					"오타 하나 때문에 증강 전체를 버리면 안 된다");
			assertTrue(effect.appliesTo(EntityTypes.ENDER_DRAGON, HOSTILE),
					"걸러진 이름은 제외 목록에서 빠진다");
		} finally {
			PerkRegistry.clear();
		}
	}

	// ------------------------------------------------------------------ 도우미

	private static MobHealthEffect health(String raw) {
		return assertInstanceOf(MobHealthEffect.class,
				MobHealthEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	private static MobDamageEffect damage(String raw) {
		return assertInstanceOf(MobDamageEffect.class,
				MobDamageEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}
}
