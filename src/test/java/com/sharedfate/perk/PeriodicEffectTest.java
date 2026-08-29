package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.PeriodicEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code periodic} 효과의 정의 읽기, 구간 판정, base 와 구간이 겹칠 때의 규칙을 본다.
 *
 * <p>실제로 플레이어에게 붙였다 떼는 부분은 살아 있는 서버와 월드가 있어야 하므로 여기서
 * 다루지 않는다. 대신 그 코드가 내리는 판단을 모두 여기서 확인한다. "지금 몇 번째 구간인가",
 * "그 구간에 어떤 효과가 걸려 있어야 하는가", "하위 효과의 순번이 겹치지 않는가" 세 가지가
 * 정해지면 {@code reconcile} 은 그 목록을 그대로 붙였다 떼는 일만 한다.
 */
class PeriodicEffectTest {
	/** 증강 작성표의 「실버 4 오버클럭」. 30초 주기, 신속 10초 뒤 구속 5초. base 없음. */
	private static final String OVERCLOCK = """
			{
			  "type": "periodic",
			  "period_ticks": 600,
			  "phases": [
			    { "ticks": 200, "effects": [
			        { "type": "status_effect", "effect": "minecraft:speed", "amplifier": 0 } ] },
			    { "ticks": 100, "effects": [
			        { "type": "status_effect", "effect": "minecraft:slowness", "amplifier": 0 } ] }
			  ]
			}
			""";

	/** 증강 작성표의 「플레 15 과부하」. 힘·신속 상시, 30초마다 5초간 나약함·구속. */
	private static final String OVERLOAD = """
			{
			  "type": "periodic",
			  "period_ticks": 600,
			  "phases": [
			    { "ticks": 100, "effects": [
			        { "type": "status_effect", "effect": "minecraft:weakness", "amplifier": 2 },
			        { "type": "status_effect", "effect": "minecraft:slowness", "amplifier": 2 } ] }
			  ],
			  "base": [
			    { "type": "status_effect", "effect": "minecraft:strength", "amplifier": 1 },
			    { "type": "status_effect", "effect": "minecraft:speed", "amplifier": 1 }
			  ]
			}
			""";

	@BeforeAll
	static void setUp() {
		// 상태이상·속성 레지스트리를 봐야 하므로 최소한의 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PeriodicPerkManager.reset();
	}

	// ------------------------------------------------------------------ 구간 판정

	@Test
	void 구간은_정의한_순서대로_돈다() {
		PeriodicEffect effect = periodic(OVERCLOCK);

		assertEquals(0, effect.phaseAt(0), "주기의 처음은 첫 구간이다");
		assertEquals(0, effect.phaseAt(199));
		assertEquals(1, effect.phaseAt(200), "첫 구간이 끝나는 틱에 곧바로 다음 구간이다");
		assertEquals(1, effect.phaseAt(299));
		assertEquals(PeriodicEffect.NO_PHASE, effect.phaseAt(300),
				"구간 길이의 합이 주기보다 짧으면 남는 시간은 어느 구간도 아니다");
		assertEquals(PeriodicEffect.NO_PHASE, effect.phaseAt(599));
	}

	@Test
	void 주기가_지나면_처음부터_다시_돈다() {
		PeriodicEffect effect = periodic(OVERCLOCK);

		for (long time = 0; time < 1_800; time++) {
			assertEquals(effect.phaseAt(time), effect.phaseAt(time + 600),
					"같은 시각에서 한 주기 떨어진 곳은 같은 구간이어야 한다");
		}
	}

	@Test
	void 기준_시각이_먼_미래여도_구간이_흔들리지_않는다() {
		PeriodicEffect effect = periodic(OVERCLOCK);
		// 게임 시간은 월드에 저장돼 재시작 후에도 계속 커진다. 큰 값에서도 계산이 성립해야 한다.
		long farFuture = 20L * 60 * 60 * 24 * 365;

		assertEquals(effect.phaseAt(farFuture % 600), effect.phaseAt(farFuture));
		assertEquals(0, effect.phaseAt(farFuture - (farFuture % 600)));
	}

	@Test
	void 음수_시각도_구간_밖으로_새지_않는다() {
		PeriodicEffect effect = periodic(OVERCLOCK);

		assertEquals(PeriodicEffect.NO_PHASE, effect.phaseAt(-1), "-1 은 주기의 마지막 틱이다");
		assertEquals(0, effect.phaseAt(-600));
	}

	// ------------------------------------------------------------------ 구간별 효과

	@Test
	void 각_구간에는_그_구간의_효과만_걸린다() {
		PeriodicEffect effect = periodic(OVERCLOCK);

		assertEquals(List.of("minecraft:speed"), statusIds(effect.activeAt(0)));
		assertEquals(List.of("minecraft:slowness"), statusIds(effect.activeAt(200)));
		assertTrue(effect.activeAt(300).isEmpty(), "base 가 없으면 구간 밖에는 아무것도 걸리지 않는다");
	}

	@Test
	void base_는_구간_밖에서_계속_걸린다() {
		PeriodicEffect effect = periodic(OVERLOAD);

		assertEquals(List.of("minecraft:strength", "minecraft:speed"),
				statusIds(effect.activeAt(100)));
		assertEquals(List.of("minecraft:strength", "minecraft:speed"),
				statusIds(effect.activeAt(599)));
	}

	@Test
	void 구간이_상태이상을_걸면_base_의_상태이상은_그동안_꺼진다() {
		PeriodicEffect effect = periodic(OVERLOAD);

		// 「30초마다 힘·신속이 둘 다 사라지고 5초간 나약함 III·구속 III」이 그대로 나와야 한다.
		assertEquals(List.of("minecraft:weakness", "minecraft:slowness"),
				statusIds(effect.activeAt(0)));
		assertEquals(List.of("minecraft:weakness", "minecraft:slowness"),
				statusIds(effect.activeAt(99)));
	}

	@Test
	void 구간의_상태이상은_base_의_속성까지_밀어내지는_않는다() {
		PeriodicEffect effect = periodic("""
				{
				  "type": "periodic", "period_ticks": 200,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "status_effect", "effect": "minecraft:slowness", "amplifier": 0 } ] } ],
				  "base": [
				    { "type": "status_effect", "effect": "minecraft:speed", "amplifier": 0 },
				    { "type": "attribute", "attribute": "minecraft:max_health",
				      "operation": "add_value", "amount": 4.0 }
				  ]
				}
				""");

		List<PerkEffect> active = effect.activeAt(0);

		assertEquals(List.of("minecraft:slowness"), statusIds(active), "상태이상은 구간 것만 남는다");
		assertEquals(1, attributes(active).size(), "갈래가 다른 속성 효과는 계속 걸려 있어야 한다");
		assertEquals(2, active.size());
	}

	@Test
	void 같은_속성을_건드리면_구간이_이긴다() {
		PeriodicEffect effect = periodic("""
				{
				  "type": "periodic", "period_ticks": 200,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "attribute", "attribute": "minecraft:movement_speed",
				        "operation": "add_multiplied_base", "amount": -0.5 } ] } ],
				  "base": [
				    { "type": "attribute", "attribute": "minecraft:movement_speed",
				      "operation": "add_multiplied_base", "amount": 0.2 },
				    { "type": "attribute", "attribute": "minecraft:max_health",
				      "operation": "add_value", "amount": 4.0 }
				  ]
				}
				""");

		List<AttributeEffect> active = attributes(effect.activeAt(0));

		assertEquals(2, active.size());
		assertEquals(-0.5, amountOf(active, "minecraft:movement_speed"), "이동 속도는 구간 값이 이긴다");
		assertEquals(4.0, amountOf(active, "minecraft:max_health"), "다른 속성은 그대로 남는다");
		assertEquals(2, attributes(effect.activeAt(100)).size(),
				"구간 밖에서는 base 두 개가 모두 걸린다");
		assertEquals(0.2, amountOf(attributes(effect.activeAt(100)), "minecraft:movement_speed"));
	}

	// ------------------------------------------------------------------ 하위 효과 순번

	@Test
	void 하위_효과의_수정자_식별자는_서로도_형제와도_겹치지_않는다() {
		PeriodicEffect effect = assertInstanceOf(PeriodicEffect.class,
				PeriodicEffect.fromJson("sharedfate:test", 2, json("""
						{
						  "type": "periodic", "period_ticks": 300,
						  "phases": [
						    { "ticks": 100, "effects": [
						        { "type": "attribute", "attribute": "minecraft:attack_damage",
						          "operation": "add_value", "amount": 1.0 } ] },
						    { "ticks": 100, "effects": [
						        { "type": "attribute", "attribute": "minecraft:attack_damage",
						          "operation": "add_value", "amount": 2.0 } ] }
						  ],
						  "base": [
						    { "type": "attribute", "attribute": "minecraft:armor",
						      "operation": "add_value", "amount": 3.0 } ]
						}
						""")));

		Set<Identifier> ids = new HashSet<>();
		for (AttributeEffect attribute : attributes(effect.base())) {
			assertTrue(ids.add(attribute.modifierId()));
		}
		for (PeriodicEffect.Phase phase : effect.phases()) {
			for (AttributeEffect attribute : attributes(phase.effects())) {
				assertTrue(ids.add(attribute.modifierId()), "구간끼리도 순번을 나눠 써야 한다");
			}
		}
		assertEquals(3, ids.size());

		// 형제 최상위 효과(0,1,2,…)의 식별자와도 달라야 한다. 겹치면 서로를 덮어쓴다.
		for (int sibling = 0; sibling < 10; sibling++) {
			assertFalse(ids.contains(AttributeEffect.modifierId("sharedfate:test", sibling)),
					"최상위 효과의 식별자를 하위 효과가 가로채면 안 된다");
		}
		// 규칙은 (부모순번 + 1) * 100 + 순번. base 가 0번, 구간들이 그다음이다.
		assertTrue(ids.contains(AttributeEffect.modifierId("sharedfate:test", 300)));
		assertTrue(ids.contains(AttributeEffect.modifierId("sharedfate:test", 301)));
		assertTrue(ids.contains(AttributeEffect.modifierId("sharedfate:test", 302)));
	}

	@Test
	void 다른_효과의_하위로는_들어갈_수_없다() {
		// 하위 순번(100 이상)으로 만들려 하면 거절한다. 안쪽 주기는 아무도 돌려 주지 않는다.
		assertNull(PeriodicEffect.fromJson("sharedfate:test", 100, json(OVERCLOCK)));
	}

	@Test
	void periodic_안에_periodic_은_넣을_수_없다() {
		assertNull(PeriodicEffect.fromJson("sharedfate:test", 0, json("""
				{
				  "type": "periodic", "period_ticks": 200,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "periodic", "period_ticks": 40,
				        "phases": [ { "ticks": 20, "effects": [
				            { "type": "status_effect", "effect": "minecraft:speed" } ] } ] } ] } ]
				}
				""")));
	}

	// ------------------------------------------------------------------ 잘못된 정의

	@Test
	void 주기가_없거나_범위를_벗어나면_만들지_않는다() {
		assertNull(fromJson("{ \"type\": \"periodic\", \"phases\": [] }"));
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 0,
				  "phases": [ { "ticks": 20, "effects": [
				      { "type": "status_effect", "effect": "minecraft:speed" } ] } ] }
				"""));
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": -600,
				  "phases": [ { "ticks": 20, "effects": [
				      { "type": "status_effect", "effect": "minecraft:speed" } ] } ] }
				"""));
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 999999,
				  "phases": [ { "ticks": 20, "effects": [
				      { "type": "status_effect", "effect": "minecraft:speed" } ] } ] }
				"""));
	}

	@Test
	void 구간이_없으면_만들지_않는다() {
		assertNull(fromJson("{ \"type\": \"periodic\", \"period_ticks\": 600 }"));
		assertNull(fromJson("{ \"type\": \"periodic\", \"period_ticks\": 600, \"phases\": [] }"));
		assertNull(fromJson(
				"{ \"type\": \"periodic\", \"period_ticks\": 600, \"phases\": \"신속\" }"));
	}

	@Test
	void 구간_길이가_없거나_합이_주기를_넘으면_만들지_않는다() {
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [ { "effects": [
				      { "type": "status_effect", "effect": "minecraft:speed" } ] } ] }
				"""), "ticks 가 없는 구간은 언제 끝나는지 알 수 없다");
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [
				    { "ticks": 400, "effects": [
				        { "type": "status_effect", "effect": "minecraft:speed" } ] },
				    { "ticks": 300, "effects": [
				        { "type": "status_effect", "effect": "minecraft:slowness" } ] } ] }
				"""), "합이 주기를 넘으면 뒤 구간이 잘려 정의와 다르게 돈다");
	}

	@Test
	void 구간의_효과가_비었거나_잘못되면_만들지_않는다() {
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [ { "ticks": 100, "effects": [] } ] }
				"""));
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "없는타입" } ] } ] }
				"""));
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "status_effect" } ] } ] }
				"""), "하위 효과 하나가 잘못되면 주기 전체를 버린다");
	}

	@Test
	void base_가_배열이_아니거나_잘못되면_만들지_않는다() {
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "status_effect", "effect": "minecraft:speed" } ] } ],
				  "base": { "type": "status_effect", "effect": "minecraft:speed" } }
				"""));
		assertNull(fromJson("""
				{ "type": "periodic", "period_ticks": 600,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "status_effect", "effect": "minecraft:speed" } ] } ],
				  "base": [ { "type": "attribute", "attribute": "minecraft:max_health" } ] }
				"""));
	}

	// ------------------------------------------------------------------ 피해 배율

	@Test
	void 피해_배율은_그_구간에만_걸린다() {
		PeriodicEffect effect = periodic("""
				{
				  "type": "periodic", "period_ticks": 400,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "damage_dealt", "multiplier": 1.5 } ] } ]
				}
				""");

		assertEquals(1.5, effect.multiplierAt(0, true), 1.0e-9);
		assertEquals(1.5, effect.multiplierAt(99, true), 1.0e-9);
		assertEquals(1.0, effect.multiplierAt(100, true), 1.0e-9, "구간 밖에서는 배율이 없다");
		assertEquals(1.0, effect.multiplierAt(0, false), "받는 피해에는 관여하지 않는다");
	}

	@Test
	void 배율_조회는_매니저가_읽어_둔_시각을_본다() {
		PeriodicEffect effect = periodic("""
				{
				  "type": "periodic", "period_ticks": 400,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "damage_taken", "multiplier": 0.5 } ] } ]
				}
				""");

		PeriodicPerkManager.setCurrentTickForTesting(50);
		assertEquals(0.5, effect.damageTakenMultiplier(), 1.0e-9);

		PeriodicPerkManager.setCurrentTickForTesting(150);
		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
	}

	@Test
	void 구간의_배율은_base_의_배율을_대신한다() {
		PeriodicEffect effect = periodic("""
				{
				  "type": "periodic", "period_ticks": 400,
				  "phases": [ { "ticks": 100, "effects": [
				      { "type": "damage_dealt", "multiplier": 0.5 } ] } ],
				  "base": [ { "type": "damage_dealt", "multiplier": 1.2 } ]
				}
				""");

		assertEquals(0.5, effect.multiplierAt(0, true), 1.0e-9, "구간 안에서는 구간 값만 쓴다");
		assertEquals(1.2, effect.multiplierAt(100, true), 1.0e-9);
	}

	// ------------------------------------------------------------------ 상태이상 공유

	@Test
	void 품고_있는_상태이상을_모두_알려준다() {
		PeriodicEffect effect = periodic(OVERLOAD);

		List<StatusEffectPerk> statuses = effect.statusEffects();

		assertEquals(4, statuses.size(), "base 와 모든 구간의 상태이상이 다 들어와야 한다");
	}

	@Test
	void 주기_증강이_건_상태이상은_팀_공유에서_빠진다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:overload", "name": "과부하", "rarity": "platinum",
				      "effects": [ %s ] }
				  ]
				}
				""".formatted(OVERLOAD), StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:overload");

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		assertFalse(perkEffects.isEmpty());
		assertTrue(perkEffects.covers(MobEffects.STRENGTH), "base 의 상태이상도 증강분이다");
		assertTrue(perkEffects.covers(MobEffects.WEAKNESS), "구간의 상태이상도 증강분이다");
		assertTrue(perkEffects.grants(new MobEffectInstance(MobEffects.SLOWNESS,
				MobEffectInstance.INFINITE_DURATION, 2, false, false, true)));
		assertTrue(perkEffects.shareable(List.of(new MobEffectInstance(MobEffects.STRENGTH,
				MobEffectInstance.INFINITE_DURATION, 1, false, false, true))).isEmpty(),
				"팀 상태에 저장되면 증강을 잃은 뒤에도 되살아난다");
		assertEquals(1, perkEffects.shareable(List.of(
				new MobEffectInstance(MobEffects.STRENGTH, 3 * 60 * 20, 1))).size(),
				"같은 종류의 포션은 지속시간이 유한하므로 그대로 공유된다");
	}

	// ------------------------------------------------------------------ 증강 정의 파일

	@Test
	void 증강_파일에서_주기_증강을_읽는다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:overclock", "name": "오버클럭", "rarity": "silver",
				      "description": "30초마다 신속 I 을 10초간 얻습니다",
				      "effects": [ %s ] },
				    { "id": "sharedfate:broken", "name": "고장난 주기", "rarity": "silver",
				      "effects": [ { "type": "periodic", "period_ticks": 600 } ] }
				  ]
				}
				""".formatted(OVERCLOCK), StandardCharsets.UTF_8);

		PerkRegistry.load(dir);

		Perk perk = PerkRegistry.byId("sharedfate:overclock").orElseThrow();
		PeriodicEffect effect = assertInstanceOf(PeriodicEffect.class, perk.effects().get(0));
		assertEquals(600, effect.periodTicks());
		assertEquals(2, effect.phases().size());
		assertEquals(200, effect.phases().get(0).ticks());
		assertEquals(List.of("minecraft:speed"), statusIds(effect.activeAt(0)));

		assertTrue(PerkRegistry.byId("sharedfate:broken").isEmpty(),
				"주기 정의가 잘못된 증강은 통째로 버린다");
	}

	@Test
	void 증강_풀이_비어_있으면_매니저는_아무_일도_하지_않는다() {
		// 서버가 없으면 읽을 시각도 없다. 조용히 되돌아 나와야 한다.
		PeriodicPerkManager.tick(null);

		assertEquals(0, PeriodicPerkManager.currentTick());
	}

	// ------------------------------------------------------------------ 도우미

	private static PeriodicEffect periodic(String raw) {
		return assertInstanceOf(PeriodicEffect.class, fromJson(raw));
	}

	private static PerkEffect fromJson(String raw) {
		return PeriodicEffect.fromJson("sharedfate:test", 0, json(raw));
	}

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	/** 목록에 든 상태이상 효과의 이름들. 순서까지 그대로 본다. */
	private static List<String> statusIds(List<PerkEffect> effects) {
		List<String> ids = new ArrayList<>();
		for (PerkEffect effect : effects) {
			if (effect instanceof StatusEffectPerk status) {
				ids.add(status.effectId().toString());
			}
		}
		return ids;
	}

	/** 그 속성을 건드리는 효과의 amount. 없으면 실패한다. */
	private static double amountOf(List<AttributeEffect> effects, String attributeId) {
		for (AttributeEffect effect : effects) {
			if (effect.attributeId().toString().equals(attributeId)) {
				return effect.amount();
			}
		}
		throw new AssertionError("속성 효과를 찾지 못했습니다: " + attributeId);
	}

	private static List<AttributeEffect> attributes(List<PerkEffect> effects) {
		List<AttributeEffect> found = new ArrayList<>();
		for (PerkEffect effect : effects) {
			if (effect instanceof AttributeEffect attribute) {
				found.add(attribute);
			}
		}
		return found;
	}
}
