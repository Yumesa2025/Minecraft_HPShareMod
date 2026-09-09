package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DamageTakenBlockingEffect;
import com.sharedfate.perk.effect.ProjectileWardEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 골드 「방패벽」({@code projectile_ward})의 정의 읽기와 효과 골라내기를 본다.
 *
 * <p>투사체를 찾아 지우는 일은 살아 있는 서버와 월드가 있어야 하므로 여기서 시험하지 않는다.
 * 대신 월드 없이 답이 정해지는 것만 붙들어 둔다.
 *
 * <ul>
 *   <li>JSON 을 읽는 규칙과 범위를 벗어난 값을 자르는 규칙</li>
 *   <li>{@code distance} 와 {@code radius} 가 서로 섞이지 않는가 — 둘 다 「칸」이라 뒤바뀌어도
 *       빌드는 통과한다. 뒤바뀌면 뭉치지 않아도 켜지거나, 뭉쳐도 아무것도 못 막는다</li>
 *   <li>효과 목록에서 자기 타입만 골라내는가
 *       ({@link ProjectileWardEffect#wardsOf}·{@link ProjectileWardEffect#widestOf})</li>
 * </ul>
 *
 * <p>정의 읽기는 대부분 팩토리를 직접 부르지만, 마지막에 {@code PerkEffectType} 등록까지 한 번
 * 확인한다. 등록을 빠뜨리면 증강이 통째로 버려지는데 빌드는 통과하기 때문이다.
 */
class ProjectileWardEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 뭉침_거리와_반경을_읽는다() {
		ProjectileWardEffect effect = create("""
				{ "type": "projectile_ward", "distance": 10, "radius": 8 }
				""");

		assertEquals(10, effect.distance());
		assertEquals(8, effect.radius());
	}

	@Test
	void 아무것도_안_적으면_거리_10_에_반경_8_이다() {
		ProjectileWardEffect effect = create("{ \"type\": \"projectile_ward\" }");

		assertEquals(ProjectileWardEffect.DEFAULT_DISTANCE, effect.distance());
		assertEquals(ProjectileWardEffect.DEFAULT_RADIUS, effect.radius());
	}

	/**
	 * 둘을 뒤바꿔 읽으면 이 증강이 통째로 뒤집힌다.
	 *
	 * <p>{@code distance} 는 켜지는 조건이고 {@code radius} 는 지우는 범위다. 서로 다른 값을
	 * 넣어 두어 어느 한쪽만 잘못 읽어도 여기서 걸리게 한다.
	 */
	@Test
	void 뭉침_거리와_반경은_서로_섞이지_않는다() {
		ProjectileWardEffect effect = create("""
				{ "type": "projectile_ward", "distance": 20, "radius": 5 }
				""");

		assertEquals(20, effect.distance(), "distance 는 팀이 뭉쳐 있어야 하는 거리다");
		assertEquals(5, effect.radius(), "radius 는 투사체를 지우는 반경이다");
	}

	/** 하나만 적어도 나머지는 기본값으로 채워진다. */
	@Test
	void 한쪽만_적어도_읽힌다() {
		assertEquals(ProjectileWardEffect.DEFAULT_RADIUS,
				create("{ \"type\": \"projectile_ward\", \"distance\": 12 }").radius());
		assertEquals(ProjectileWardEffect.DEFAULT_DISTANCE,
				create("{ \"type\": \"projectile_ward\", \"radius\": 6 }").distance());
	}

	/** 숫자가 아닌 값은 없는 것으로 보고 기본값을 쓴다. {@code readInt} 의 규칙 그대로다. */
	@Test
	void 숫자가_아니면_기본값이다() {
		ProjectileWardEffect effect = create("""
				{ "type": "projectile_ward", "distance": "열 칸", "radius": "여덟" }
				""");

		assertEquals(ProjectileWardEffect.DEFAULT_DISTANCE, effect.distance());
		assertEquals(ProjectileWardEffect.DEFAULT_RADIUS, effect.radius());
	}

	// ------------------------------------------------------------------ 자르기

	/** 범위를 벗어나도 정의를 버리지 않는다. 값 하나 때문에 골드 증강이 통째로 사라지면 안 된다. */
	@Test
	void 뭉침_거리가_범위를_벗어나면_잘라_쓴다() {
		assertEquals(ProjectileWardEffect.MIN_DISTANCE, distanceOf(3));
		assertEquals(ProjectileWardEffect.MIN_DISTANCE, distanceOf(0));
		assertEquals(ProjectileWardEffect.MIN_DISTANCE, distanceOf(-50));
		assertEquals(ProjectileWardEffect.MAX_DISTANCE, distanceOf(65));
		assertEquals(ProjectileWardEffect.MAX_DISTANCE, distanceOf(100000));
		// 경계값은 그대로 남는다.
		assertEquals(ProjectileWardEffect.MIN_DISTANCE,
				distanceOf(ProjectileWardEffect.MIN_DISTANCE));
		assertEquals(ProjectileWardEffect.MAX_DISTANCE,
				distanceOf(ProjectileWardEffect.MAX_DISTANCE));
	}

	@Test
	void 반경이_범위를_벗어나면_잘라_쓴다() {
		assertEquals(ProjectileWardEffect.MIN_RADIUS, radiusOf(1));
		assertEquals(ProjectileWardEffect.MIN_RADIUS, radiusOf(0));
		assertEquals(ProjectileWardEffect.MIN_RADIUS, radiusOf(-7));
		assertEquals(ProjectileWardEffect.MAX_RADIUS, radiusOf(33));
		assertEquals(ProjectileWardEffect.MAX_RADIUS, radiusOf(9999));
		assertEquals(ProjectileWardEffect.MIN_RADIUS, radiusOf(ProjectileWardEffect.MIN_RADIUS));
		assertEquals(ProjectileWardEffect.MAX_RADIUS, radiusOf(ProjectileWardEffect.MAX_RADIUS));
	}

	/** 한쪽이 범위를 벗어나도 다른 쪽은 적은 대로 남는다. 잘라 쓰기가 서로를 건드리면 안 된다. */
	@Test
	void 한쪽만_잘라도_다른_쪽은_그대로다() {
		ProjectileWardEffect effect = create("""
				{ "type": "projectile_ward", "distance": 9999, "radius": 6 }
				""");

		assertEquals(ProjectileWardEffect.MAX_DISTANCE, effect.distance());
		assertEquals(6, effect.radius());
	}

	// ------------------------------------------------------------------ 거리 비교값

	/** 거리 비교에 쓰는 값. 제곱근을 뽑지 않으려고 미리 내어 둔다. */
	@Test
	void 반경의_제곱을_내어_준다() {
		assertEquals(64.0, create("{ \"type\": \"projectile_ward\", \"radius\": 8 }").radiusSquared());
		assertEquals(1024.0,
				create("{ \"type\": \"projectile_ward\", \"radius\": 32 }").radiusSquared());
	}

	// ------------------------------------------------------------------ 효과 골라내기

	@Test
	void 효과_목록에서_방패벽만_골라낸다() {
		ProjectileWardEffect ward = new ProjectileWardEffect(10, 8);

		assertEquals(List.of(ward), ProjectileWardEffect.wardsOf(
				List.of(new DamageTakenBlockingEffect(0.5), ward)));
		assertTrue(ProjectileWardEffect.wardsOf(List.of(new DamageTakenBlockingEffect(0.5)))
				.isEmpty());
		assertTrue(ProjectileWardEffect.wardsOf(List.of()).isEmpty());
		assertTrue(ProjectileWardEffect.wardsOf(null).isEmpty());
	}

	/**
	 * 여럿이면 하나로 합치지 않고 그대로 돌려준다.
	 *
	 * <p>정의마다 뭉침 거리가 달라 어느 것은 켜지고 어느 것은 안 켜질 수 있다. 여기서 합쳐 버리면
	 * 실행부가 정의마다 따로 판정할 방법이 없어진다.
	 */
	@Test
	void 여럿이면_모두_돌려준다() {
		ProjectileWardEffect narrow = new ProjectileWardEffect(10, 8);
		ProjectileWardEffect wide = new ProjectileWardEffect(20, 16);

		List<ProjectileWardEffect> found = ProjectileWardEffect.wardsOf(
				List.of(narrow, new DamageTakenBlockingEffect(0.5), wide));

		assertEquals(2, found.size());
		assertSame(narrow, found.get(0));
		assertSame(wide, found.get(1));
	}

	/** 넓은 쪽이 이긴다. 좁은 쪽이 이기면 증강을 하나 더 얻은 것이 앞의 것을 깎아 먹는다. */
	@Test
	void 여럿이면_반경이_넓은_쪽이_이긴다() {
		ProjectileWardEffect narrow = new ProjectileWardEffect(10, 8);
		ProjectileWardEffect wide = new ProjectileWardEffect(10, 16);

		assertSame(wide, ProjectileWardEffect.widestOf(List.of(narrow, wide)));
		assertSame(wide, ProjectileWardEffect.widestOf(List.of(wide, narrow)));
	}

	@Test
	void 방패벽이_없으면_고를_것도_없다() {
		assertNull(ProjectileWardEffect.widestOf(List.of(new DamageTakenBlockingEffect(0.5))));
		assertNull(ProjectileWardEffect.widestOf(List.of()));
		assertNull(ProjectileWardEffect.widestOf(null));
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
		PerkEffectType type = PerkEffectType.fromId("projectile_ward");
		assertNotNull(type,
				"PerkEffectType 에 PROJECTILE_WARD(\"projectile_ward\", "
						+ "ProjectileWardEffect::fromJson) 을 등록해야 한다");

		JsonObject json = JsonParser
				.parseString("{ \"type\": \"projectile_ward\", \"distance\": 10, \"radius\": 8 }")
				.getAsJsonObject();
		ProjectileWardEffect effect = assertInstanceOf(ProjectileWardEffect.class,
				type.create("sharedfate:방패벽", 0, json),
				"등록은 되어 있는데 다른 팩토리가 물려 있다");
		assertEquals(10, effect.distance());
		assertEquals(8, effect.radius());
	}

	// ------------------------------------------------------------------ 도우미

	private static int distanceOf(int distance) {
		return create("{ \"type\": \"projectile_ward\", \"distance\": " + distance + " }").distance();
	}

	private static int radiusOf(int radius) {
		return create("{ \"type\": \"projectile_ward\", \"radius\": " + radius + " }").radius();
	}

	private static ProjectileWardEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(ProjectileWardEffect.class,
				ProjectileWardEffect.fromJson("sharedfate:방패벽", 0, parsed));
	}
}
