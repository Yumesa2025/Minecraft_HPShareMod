package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.SanctuaryEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 프리즘 「성역」({@code sanctuary})의 정의 읽기와 확률 셈을 본다.
 *
 * <p>몹을 고르고 틱을 건너뛰는 일은 살아 있는 서버와 월드가 있어야 하므로 여기서 시험하지
 * 않는다. 대신 월드 없이 답이 정해지는 것만 붙들어 둔다.
 *
 * <ul>
 *   <li>JSON 을 읽는 규칙과 범위를 벗어난 값을 <b>버리지 않고</b> 자르는 규칙</li>
 *   <li>{@code slow} 가 1.0 까지 가지 못하게 막는가 — 1.0 이면 몹의 시간이 아예 멈춰 불에 타지도
 *       디스폰되지도 않는 몹이 월드에 쌓인다</li>
 *   <li>{@link SanctuaryEffect#skipsTick(double)} 과
 *       {@link SanctuaryEffect#runsExtraTick(double)} 의 확률 셈 — 이 값이 틀리면 감속·가속의
 *       세기가 통째로 어긋난다</li>
 * </ul>
 */
class SanctuaryEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 거리와_반경과_감속과_가속을_읽는다() {
		SanctuaryEffect effect = create("""
				{ "type": "sanctuary", "distance": 20, "radius": 10,
				  "slow": 0.4, "haste_when_apart": 0.15 }
				""");

		assertEquals(20.0, effect.distance());
		assertEquals(10.0, effect.radius());
		assertEquals(0.4, effect.slow());
		assertEquals(0.15, effect.hasteWhenApart());
	}

	@Test
	void 아무것도_안_적으면_20칸_10칸_40퍼센트_15퍼센트_다() {
		SanctuaryEffect effect = create("{ \"type\": \"sanctuary\" }");

		assertEquals(SanctuaryEffect.DEFAULT_DISTANCE, effect.distance());
		assertEquals(SanctuaryEffect.DEFAULT_RADIUS, effect.radius());
		assertEquals(SanctuaryEffect.DEFAULT_SLOW, effect.slow());
		assertEquals(SanctuaryEffect.DEFAULT_HASTE_WHEN_APART, effect.hasteWhenApart());
	}

	@Test
	void 카멜케이스로_적어도_읽는다() {
		SanctuaryEffect effect = create("""
				{ "type": "sanctuary", "distance": 24, "radius": 8,
				  "slow": 0.3, "hasteWhenApart": 0.2 }
				""");

		assertEquals(24.0, effect.distance());
		assertEquals(8.0, effect.radius());
		assertEquals(0.3, effect.slow());
		assertEquals(0.2, effect.hasteWhenApart());
	}

	/** 숫자가 아닌 값은 없는 것으로 보고 기본값을 쓴다. {@code readDouble} 의 규칙 그대로다. */
	@Test
	void 숫자가_아니면_기본값이다() {
		SanctuaryEffect effect = create("""
				{ "type": "sanctuary", "distance": "스무 칸", "radius": true,
				  "slow": "사십 퍼센트", "haste_when_apart": null }
				""");

		assertEquals(SanctuaryEffect.DEFAULT_DISTANCE, effect.distance());
		assertEquals(SanctuaryEffect.DEFAULT_RADIUS, effect.radius());
		assertEquals(SanctuaryEffect.DEFAULT_SLOW, effect.slow());
		assertEquals(SanctuaryEffect.DEFAULT_HASTE_WHEN_APART, effect.hasteWhenApart());
	}

	// ------------------------------------------------------------------ 자르기

	/**
	 * 범위를 벗어나도 정의를 버리지 않는다.
	 *
	 * <p>값 하나가 틀렸다고 프리즘 증강이 통째로 사라지면, 뽑기 표에서 조용히 한 칸이 비고
	 * 아무도 이유를 모른다. 경고만 남기고 잘라 쓴다.
	 */
	@Test
	void 거리가_범위를_벗어나면_잘라_쓴다() {
		assertEquals(SanctuaryEffect.MIN_DISTANCE, distanceOf(0.5));
		assertEquals(SanctuaryEffect.MIN_DISTANCE, distanceOf(-100));
		assertEquals(SanctuaryEffect.MAX_DISTANCE, distanceOf(9999));
		// 경계값은 그대로 남는다.
		assertEquals(SanctuaryEffect.MIN_DISTANCE, distanceOf(SanctuaryEffect.MIN_DISTANCE));
		assertEquals(SanctuaryEffect.MAX_DISTANCE, distanceOf(SanctuaryEffect.MAX_DISTANCE));
	}

	@Test
	void 반경이_범위를_벗어나면_잘라_쓴다() {
		assertEquals(SanctuaryEffect.MIN_RADIUS, radiusOf(0));
		assertEquals(SanctuaryEffect.MIN_RADIUS, radiusOf(-4));
		assertEquals(SanctuaryEffect.MAX_RADIUS, radiusOf(4096));
		assertEquals(3.0, radiusOf(3));
	}

	/**
	 * <b>{@code slow} 가 1.0 이 되면 안 된다.</b>
	 *
	 * <p>1.0 은 「틱을 100% 건너뛴다」이고 그 몹은 불에 타지도, 죽어 사라지지도, 디스폰되지도
	 * 않는다. 느려지는 것이 아니라 굳어 버려 월드에 쌓인다.
	 */
	@Test
	void 감속이_범위를_벗어나면_잘라_쓴다() {
		assertEquals(SanctuaryEffect.MAX_SLOW, slowOf(1.0), "1.0 이면 몹의 시간이 멈춘다");
		assertEquals(SanctuaryEffect.MAX_SLOW, slowOf(37));
		assertEquals(SanctuaryEffect.MIN_SLOW, slowOf(-0.2));
		assertEquals(0.4, slowOf(0.4));
	}

	@Test
	void 가속이_범위를_벗어나면_잘라_쓴다() {
		assertEquals(SanctuaryEffect.MAX_HASTE_WHEN_APART, hasteOf(2.5),
				"틱은 많아야 한 번만 더 돈다. 그 위는 표현할 방법이 없다");
		assertEquals(SanctuaryEffect.MIN_HASTE_WHEN_APART, hasteOf(-1));
		assertEquals(0.15, hasteOf(0.15));
	}

	// ------------------------------------------------------------------ 확률 셈

	/**
	 * <b>이 시험이 이 증강의 심장이다.</b>
	 *
	 * <p>실행부는 매 틱 0 이상 1 미만의 난수를 뽑아 이 셈에 넣는다. 「40% 감속」은 곧 「난수가
	 * 0.4 보다 작으면 그 틱을 건너뛴다」이므로, 경계가 한 칸만 어긋나도 감속이 41% 나 39% 가
	 * 된다. 이동·공격 간격·크리퍼 부풀기·활 쏘기가 전부 이 한 줄에 걸려 있다.
	 */
	@Test
	void 난수가_감속보다_작을_때만_건너뛴다() {
		SanctuaryEffect effect = create("{ \"type\": \"sanctuary\", \"slow\": 0.4 }");

		assertTrue(effect.skipsTick(0.0));
		assertTrue(effect.skipsTick(0.399));
		assertFalse(effect.skipsTick(0.4), "경계는 건너뛰지 않는다. 그래야 정확히 40% 다");
		assertFalse(effect.skipsTick(0.99));
	}

	/** 감속이 0 이면 어떤 난수에도 건너뛰지 않는다. 0 은 「이 몫은 없다」는 뜻이다. */
	@Test
	void 감속이_0_이면_한_틱도_건너뛰지_않는다() {
		SanctuaryEffect effect = create("{ \"type\": \"sanctuary\", \"slow\": 0 }");

		assertFalse(effect.skipsTick(0.0));
		assertFalse(effect.skipsTick(0.5));
	}

	@Test
	void 난수가_가속보다_작을_때만_한_번_더_돈다() {
		SanctuaryEffect effect = create("""
				{ "type": "sanctuary", "haste_when_apart": 0.15 }
				""");

		assertTrue(effect.runsExtraTick(0.0));
		assertTrue(effect.runsExtraTick(0.149));
		assertFalse(effect.runsExtraTick(0.15), "경계는 돌지 않는다. 그래야 정확히 15% 다");
		assertFalse(effect.runsExtraTick(0.9));
	}

	@Test
	void 가속이_0_이면_한_번도_더_돌지_않는다() {
		SanctuaryEffect effect = create("""
				{ "type": "sanctuary", "haste_when_apart": 0 }
				""");

		assertFalse(effect.runsExtraTick(0.0));
		assertFalse(effect.runsExtraTick(0.5));
	}

	/** 거리 비교에 쓰는 값. 제곱근을 뽑지 않으려고 미리 내어 둔다. */
	@Test
	void 반경의_제곱을_내어_준다() {
		assertEquals(100.0, create("{ \"type\": \"sanctuary\", \"radius\": 10 }").radiusSquared());
		assertEquals(64.0, create("{ \"type\": \"sanctuary\", \"radius\": 8 }").radiusSquared());
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
		PerkEffectType type = PerkEffectType.fromId("sanctuary");
		assertNotNull(type,
				"PerkEffectType 에 SANCTUARY(\"sanctuary\", SanctuaryEffect::fromJson) 을 "
						+ "등록해야 한다");

		JsonObject json = JsonParser
				.parseString("{ \"type\": \"sanctuary\", \"distance\": 20, \"radius\": 10, "
						+ "\"slow\": 0.4, \"haste_when_apart\": 0.15 }")
				.getAsJsonObject();
		SanctuaryEffect effect = assertInstanceOf(SanctuaryEffect.class,
				type.create("sharedfate:성역", 0, json),
				"등록은 되어 있는데 다른 팩토리가 물려 있다");
		assertEquals(20.0, effect.distance());
		assertEquals(0.4, effect.slow());
	}

	// ------------------------------------------------------------------ 도우미

	private static double distanceOf(double distance) {
		return create("{ \"type\": \"sanctuary\", \"distance\": " + distance + " }").distance();
	}

	private static double radiusOf(double radius) {
		return create("{ \"type\": \"sanctuary\", \"radius\": " + radius + " }").radius();
	}

	private static double slowOf(double slow) {
		return create("{ \"type\": \"sanctuary\", \"slow\": " + slow + " }").slow();
	}

	private static double hasteOf(double haste) {
		return create("{ \"type\": \"sanctuary\", \"haste_when_apart\": " + haste + " }")
				.hasteWhenApart();
	}

	private static SanctuaryEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(SanctuaryEffect.class,
				SanctuaryEffect.fromJson("sharedfate:성역", 0, parsed));
	}
}
