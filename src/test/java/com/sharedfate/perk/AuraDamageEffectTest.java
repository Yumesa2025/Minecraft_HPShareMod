package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AuraDamageEffect;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 프리즘 「살기」({@code aura_damage})의 정의 읽기와 중첩 셈을 본다.
 *
 * <p>몹을 찾아 때리는 일은 살아 있는 서버와 월드가 있어야 하므로 여기서 시험하지 않는다.
 * 대신 월드 없이 답이 정해지는 세 가지만 붙들어 둔다.
 *
 * <ul>
 *   <li>JSON 을 읽는 규칙과 범위를 벗어난 값을 자르는 규칙</li>
 *   <li>{@code excluded_dimensions} 가 어떤 차원을 빼는가 — 빠뜨리면 「네더·엔드에서는 무효」가
 *       조용히 사라진다</li>
 *   <li>{@link AuraDamageEffect#stackedDamage(int)} 의 중첩 셈 — 이 값이 틀리면 「팀원이 모이면
 *       겹친다」는 설계가 통째로 죽는다</li>
 * </ul>
 */
class AuraDamageEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 반경과_초당_피해와_제외_차원을_읽는다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage", "radius": 10, "damage_per_second": 2,
				  "excluded_dimensions": ["minecraft:the_nether", "minecraft:the_end"] }
				""");

		assertEquals(10, effect.radius());
		assertEquals(2, effect.damagePerSecond());
		assertEquals(Set.of(Level.NETHER, Level.END), effect.excludedDimensions());
	}

	@Test
	void 아무것도_안_적으면_반경_10_에_초당_2_다() {
		AuraDamageEffect effect = create("{ \"type\": \"aura_damage\" }");

		assertEquals(AuraDamageEffect.DEFAULT_RADIUS, effect.radius());
		assertEquals(AuraDamageEffect.DEFAULT_DAMAGE_PER_SECOND, effect.damagePerSecond());
		assertTrue(effect.excludedDimensions().isEmpty(), "생략하면 어디서나 작동한다");
	}

	@Test
	void 카멜케이스로_적어도_읽는다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage", "radius": 6, "damagePerSecond": 5,
				  "excludedDimensions": ["minecraft:the_end"] }
				""");

		assertEquals(6, effect.radius());
		assertEquals(5, effect.damagePerSecond());
		assertEquals(Set.of(Level.END), effect.excludedDimensions());
	}

	// ------------------------------------------------------------------ 자르기

	/** 범위를 벗어나도 정의를 버리지 않는다. 값 하나 때문에 프리즘 증강이 통째로 사라지면 안 된다. */
	@Test
	void 반경이_범위를_벗어나면_잘라_쓴다() {
		assertEquals(AuraDamageEffect.MIN_RADIUS, radiusOf(1));
		assertEquals(AuraDamageEffect.MIN_RADIUS, radiusOf(-100));
		assertEquals(AuraDamageEffect.MAX_RADIUS, radiusOf(33));
		assertEquals(AuraDamageEffect.MAX_RADIUS, radiusOf(4096));
		// 경계값은 그대로 남는다.
		assertEquals(2, radiusOf(2));
		assertEquals(32, radiusOf(32));
	}

	@Test
	void 초당_피해가_범위를_벗어나면_잘라_쓴다() {
		assertEquals(AuraDamageEffect.MIN_DAMAGE_PER_SECOND, damageOf(0));
		assertEquals(AuraDamageEffect.MIN_DAMAGE_PER_SECOND, damageOf(-7));
		assertEquals(AuraDamageEffect.MAX_DAMAGE_PER_SECOND, damageOf(11));
		assertEquals(AuraDamageEffect.MAX_DAMAGE_PER_SECOND, damageOf(9999));
		assertEquals(1, damageOf(1));
		assertEquals(10, damageOf(10));
	}

	/** 숫자가 아닌 값은 없는 것으로 보고 기본값을 쓴다. {@code readInt} 의 규칙 그대로다. */
	@Test
	void 숫자가_아니면_기본값이다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage", "radius": "열 칸", "damage_per_second": "둘" }
				""");

		assertEquals(AuraDamageEffect.DEFAULT_RADIUS, effect.radius());
		assertEquals(AuraDamageEffect.DEFAULT_DAMAGE_PER_SECOND, effect.damagePerSecond());
	}

	// ------------------------------------------------------------------ 제외 차원

	@Test
	void 적어_둔_차원만_빠진다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage",
				  "excluded_dimensions": ["minecraft:the_nether", "minecraft:the_end"] }
				""");

		assertTrue(effect.excludes(Level.NETHER));
		assertTrue(effect.excludes(Level.END));
		assertFalse(effect.excludes(Level.OVERWORLD), "오버월드에서는 그대로 작동한다");
		assertFalse(effect.excludes(null));
	}

	@Test
	void 생략하면_어디서나_작동한다() {
		AuraDamageEffect effect = create("{ \"type\": \"aura_damage\", \"radius\": 10 }");

		assertFalse(effect.excludes(Level.OVERWORLD));
		assertFalse(effect.excludes(Level.NETHER));
		assertFalse(effect.excludes(Level.END));
	}

	/** 읽을 수 없는 이름은 그 항목만 버린다. 오타 하나로 나머지 제외까지 풀리면 안 된다. */
	@Test
	void 이름이_틀린_항목만_버린다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage",
				  "excluded_dimensions": ["대문자 안 됨", "minecraft:the_end"] }
				""");

		assertEquals(Set.of(Level.END), effect.excludedDimensions());
	}

	/** 배열이 아니면 빈 것으로 본다. 이때도 정의를 버리지 않는다. */
	@Test
	void 배열이_아니면_비어_있는_것으로_본다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage", "excluded_dimensions": "minecraft:the_nether" }
				""");

		assertTrue(effect.excludedDimensions().isEmpty());
		assertFalse(effect.excludes(Level.NETHER));
	}

	// ------------------------------------------------------------------ 중첩 셈

	/**
	 * <b>이 시험이 이 증강의 심장이다.</b>
	 *
	 * <p>바닐라는 피격 뒤 무적 시간이 있어서 네 명이 각자 2씩 때리면 8이 아니라 2만 들어간다.
	 * 그래서 실행부는 인원을 먼저 세고 여기서 구한 합계를 <b>한 번에</b> 넣는다. 이 셈이 인원에
	 * 비례하지 않게 되면 「팀원이 모이면 겹친다」는 설계가 통째로 죽는다.
	 */
	@Test
	void 겹친_인원만큼_곱해진다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage", "radius": 10, "damage_per_second": 2 }
				""");

		assertEquals(2.0F, effect.stackedDamage(1));
		assertEquals(4.0F, effect.stackedDamage(2));
		assertEquals(8.0F, effect.stackedDamage(4), "네 명이 겹치면 2가 아니라 8이다");
	}

	@Test
	void 초당_피해가_다르면_그_값으로_곱해진다() {
		AuraDamageEffect effect = create("""
				{ "type": "aura_damage", "damage_per_second": 3 }
				""");

		assertEquals(3.0F, effect.stackedDamage(1));
		assertEquals(12.0F, effect.stackedDamage(4));
	}

	/** 반경 안에 아무도 없으면 때릴 것이 없다. 음수는 셈이 어긋난 것이므로 0으로 본다. */
	@Test
	void 아무도_없으면_0_이다() {
		AuraDamageEffect effect = create("{ \"type\": \"aura_damage\" }");

		assertEquals(0.0F, effect.stackedDamage(0));
		assertEquals(0.0F, effect.stackedDamage(-3));
	}

	/** 거리 비교에 쓰는 값. 제곱근을 뽑지 않으려고 미리 내어 둔다. */
	@Test
	void 반경의_제곱을_내어_준다() {
		assertEquals(100.0, create("{ \"type\": \"aura_damage\", \"radius\": 10 }").radiusSquared());
		assertEquals(1024.0, create("{ \"type\": \"aura_damage\", \"radius\": 32 }").radiusSquared());
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
		PerkEffectType type = PerkEffectType.fromId("aura_damage");
		assertNotNull(type,
				"PerkEffectType 에 AURA_DAMAGE(\"aura_damage\", AuraDamageEffect::fromJson) 을 "
						+ "등록해야 한다");

		JsonObject json = JsonParser
				.parseString("{ \"type\": \"aura_damage\", \"radius\": 10, "
						+ "\"damage_per_second\": 2 }")
				.getAsJsonObject();
		AuraDamageEffect effect = assertInstanceOf(AuraDamageEffect.class,
				type.create("sharedfate:살기", 0, json),
				"등록은 되어 있는데 다른 팩토리가 물려 있다");
		assertEquals(10, effect.radius());
		assertEquals(2, effect.damagePerSecond());
	}

	// ------------------------------------------------------------------ 도우미

	private static int radiusOf(int radius) {
		return create("{ \"type\": \"aura_damage\", \"radius\": " + radius + " }").radius();
	}

	private static int damageOf(int damage) {
		return create("{ \"type\": \"aura_damage\", \"damage_per_second\": " + damage + " }")
				.damagePerSecond();
	}

	private static AuraDamageEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(AuraDamageEffect.class,
				AuraDamageEffect.fromJson("sharedfate:살기", 0, parsed));
	}
}
