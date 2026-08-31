package com.sharedfate.perk;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkDraftTest {
	private static final long SEED = 20260829L;

	/** 프리즘가 아닌, 실버 또는 골드가 배정되는 구간들. */
	private static final int[] RANDOM_MILESTONES = {5, 10, 20, 25, 35};

	private static Perk once(String id, PerkRarity rarity) {
		return new Perk(id, id, "설명 " + id, rarity, List.of());
	}

	private static Perk onceWithMinLevel(String id, PerkRarity rarity, int minLevel) {
		return new Perk(id, id, "설명 " + id, rarity, null, minLevel, List.of());
	}

	/** 실버 3개 / 골드 3개 / 프리즘 3개짜리 표준 풀. */
	private static List<Perk> ninePool() {
		return List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("s3", PerkRarity.SILVER),
				once("g1", PerkRarity.GOLD),
				once("g2", PerkRarity.GOLD),
				once("g3", PerkRarity.GOLD),
				once("p1", PerkRarity.PRISM),
				once("p2", PerkRarity.PRISM),
				once("p3", PerkRarity.PRISM));
	}

	private static Map<String, PerkRarity> rarityIndex(List<Perk> pool) {
		Map<String, PerkRarity> index = new HashMap<>();
		pool.forEach(perk -> index.put(perk.id(), perk.rarity()));
		return index;
	}

	// ------------------------------------------------------------------ 구간 → 등급 배정

	@Test
	void 십오렙은_난수와_무관하게_항상_프리즘다() {
		for (long seed = 0; seed < 500; seed++) {
			assertEquals(PerkRarity.PRISM,
					PerkDraft.rarityFor(15, RandomSource.create(seed)),
					"시드 " + seed + "에서 15렙이 프리즘가 아니었다");
		}
		assertEquals(PerkRarity.PRISM, PerkDraft.rarityFor(15, null),
				"난수원이 없어도 15렙은 프리즘다");
		assertTrue(PerkDraft.PRISM_MILESTONES.contains(15));
	}

	@Test
	void 삼십렙도_난수와_무관하게_항상_프리즘다() {
		for (long seed = 0; seed < 500; seed++) {
			assertEquals(PerkRarity.PRISM,
					PerkDraft.rarityFor(30, RandomSource.create(seed)),
					"시드 " + seed + "에서 30렙이 프리즘가 아니었다");
		}
		assertEquals(PerkRarity.PRISM, PerkDraft.rarityFor(30, null),
				"난수원이 없어도 30렙은 프리즘다");
		assertTrue(PerkDraft.PRISM_MILESTONES.contains(30));
		assertEquals(2, PerkDraft.PRISM_MILESTONES.size(), "프리즘 구간은 15·30 둘뿐이다");
	}

	@Test
	void 나머지_구간에서는_프리즘가_절대_나오지_않는다() {
		RandomSource random = RandomSource.create(SEED);

		for (int round = 0; round < 5000; round++) {
			for (int milestone : RANDOM_MILESTONES) {
				PerkRarity rarity = PerkDraft.rarityFor(milestone, random);

				assertNotEquals(PerkRarity.PRISM, rarity, milestone + "렙에서 프리즘가 나왔다");
				assertTrue(rarity == PerkRarity.SILVER || rarity == PerkRarity.GOLD);
			}
		}
	}

	@Test
	void 같은_시드는_같은_등급_배정을_준다() {
		RandomSource first = RandomSource.create(SEED);
		RandomSource second = RandomSource.create(SEED);

		List<PerkRarity> a = new ArrayList<>();
		List<PerkRarity> b = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			a.add(PerkDraft.rarityFor(10, first));
			b.add(PerkDraft.rarityFor(10, second));
		}

		assertEquals(a, b, "고정 시드면 등급 배정이 완전히 같아야 한다");
		assertTrue(new HashSet<>(a).size() > 1, "고정 시드라도 매번 같은 등급만 나오면 무작위가 아니다");
	}

	@Test
	void 실버와_골드는_대략_반반이다() {
		RandomSource random = RandomSource.create(SEED);
		int rounds = 40000;

		int silver = 0;
		for (int i = 0; i < rounds; i++) {
			if (PerkDraft.rarityFor(20, random) == PerkRarity.SILVER) {
				silver++;
			}
		}

		double ratio = silver / (double) rounds;
		double expected = PerkDraft.SILVER_PERCENT / 100.0;
		assertTrue(Math.abs(expected - ratio) < 0.02,
				"기대 비율 " + expected + ", 실제 " + ratio);
	}

	@Test
	void 폴백_우선순위() {
		assertEquals(List.of(PerkRarity.SILVER, PerkRarity.GOLD, PerkRarity.PRISM),
				PerkDraft.fallbackOrder(PerkRarity.SILVER));
		assertEquals(List.of(PerkRarity.GOLD, PerkRarity.SILVER, PerkRarity.PRISM),
				PerkDraft.fallbackOrder(PerkRarity.GOLD));
		assertEquals(List.of(PerkRarity.PRISM, PerkRarity.GOLD, PerkRarity.SILVER),
				PerkDraft.fallbackOrder(PerkRarity.PRISM));
	}

	// ------------------------------------------------------------------ 추첨

	@Test
	void 한_라운드의_후보_세_개는_전부_같은_등급이다() {
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 500; i++) {
			int milestone = RANDOM_MILESTONES[i % RANDOM_MILESTONES.length];
			List<String> drawn = PerkDraft.draw(milestone, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			Set<PerkRarity> rarities = new HashSet<>();
			drawn.forEach(id -> rarities.add(index.get(id)));
			assertEquals(1, rarities.size(), "등급이 섞여 나왔다: " + drawn);
			assertFalse(rarities.contains(PerkRarity.PRISM),
					milestone + "렙에서 프리즘가 나왔다: " + drawn);
		}
	}

	@Test
	void 십오렙_라운드는_프리즘만_나온다() {
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(15, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			drawn.forEach(id -> assertEquals(PerkRarity.PRISM, index.get(id),
					"15렙 라운드에 프리즘가 아닌 후보가 섞였다: " + drawn));
		}
	}

	@Test
	void 삼십렙_라운드도_프리즘만_나온다() {
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(30, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			drawn.forEach(id -> assertEquals(PerkRarity.PRISM, index.get(id),
					"30렙 라운드에 프리즘가 아닌 후보가 섞였다: " + drawn));
		}
	}

	@Test
	void 십오렙에서_고른_프리즘은_삼십렙에_다시_나오지_않는다() {
		// 도박꾼처럼 15렙에서 프리즘 하나를 이미 골랐다고 가정한다. 30렙도 프리즘 라운드이므로
		// 같은 증강이 후보로 다시 뜨면 안 된다. 폴백(프리즘이 모자라면 골드로 채움)이 결과를
		// 헷갈리게 하지 않도록 프리즘만 네 개 있는 풀로 확인한다.
		List<Perk> pool = List.of(
				once("p1", PerkRarity.PRISM),
				once("p2", PerkRarity.PRISM),
				once("p3", PerkRarity.PRISM),
				once("p4", PerkRarity.PRISM));
		RandomSource random = RandomSource.create(SEED);
		String picked = "p1";

		for (int i = 0; i < 200; i++) {
			List<String> secondRound = PerkDraft.draw(30, pool, List.of(picked), random, 3);

			assertFalse(secondRound.contains(picked),
					"15렙에서 고른 " + picked + " 가 30렙에 다시 나왔다");
			assertEquals(3, secondRound.size(), "남은 프리즘 셋(p2·p3·p4) 중에서 채워야 한다");
			assertEquals(Set.of("p2", "p3", "p4"), new HashSet<>(secondRound));
		}
	}

	@Test
	void 등급별_등장_횟수가_기록된다() {
		// 여러 구간을 많이 돌리면 실버 라운드와 골드 라운드가 모두 나와야 한다
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		Map<PerkRarity, Integer> rounds = new EnumMap<>(PerkRarity.class);
		for (int i = 0; i < 400; i++) {
			List<String> drawn = PerkDraft.draw(25, pool, List.of(), random, 3);
			rounds.merge(index.get(drawn.getFirst()), 1, Integer::sum);
		}

		assertTrue(rounds.getOrDefault(PerkRarity.SILVER, 0) > 0, "실버 라운드가 한 번도 없었다");
		assertTrue(rounds.getOrDefault(PerkRarity.GOLD, 0) > 0, "골드 라운드가 한 번도 없었다");
		assertEquals(0, rounds.getOrDefault(PerkRarity.PRISM, 0).intValue());
	}

	@Test
	void 같은_시드는_항상_같은_결과를_준다() {
		List<Perk> pool = ninePool();

		List<String> first = PerkDraft.draw(20, pool, List.of(), RandomSource.create(SEED), 3);
		List<String> second = PerkDraft.draw(20, pool, List.of(), RandomSource.create(SEED), 3);

		assertEquals(3, first.size());
		assertEquals(first, second, "같은 시드면 후보가 완전히 같아야 한다");
	}

	@Test
	void 시드가_다르면_결과도_갈린다() {
		List<Perk> pool = ninePool();
		RandomSource random = RandomSource.create(SEED);

		Set<List<String>> results = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			// 20렙은 프리즘 라운드가 아니라 실버/골드가 반반씩 섞여 나오므로 조합이 갈린다.
			// 30렙은 이제 프리즘 라운드라 후보 3개짜리 풀에서는 언제나 같은 조합 하나뿐이다.
			results.add(PerkDraft.draw(20, pool, List.of(), random, 3));
		}

		assertTrue(results.size() > 1, "난수를 계속 굴리면 서로 다른 조합이 나와야 한다");
	}

	@Test
	void 같은_추첨_안에서_같은_증강이_두_번_나오지_않는다() {
		List<Perk> pool = ninePool();
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 500; i++) {
			List<String> drawn = PerkDraft.draw(35, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			assertEquals(3, new HashSet<>(drawn).size(), "중복 후보가 나왔다: " + drawn);
		}
	}

	// ------------------------------------------------------------------ 보유 처리

	@Test
	void 한_번_고른_증강은_다시_후보로_나오지_않는다() {
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("g1", PerkRarity.GOLD));
		List<String> owned = List.of("s1");
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, pool, owned, random, 3);

			assertEquals(2, drawn.size(), "남은 후보는 두 개뿐이다");
			assertFalse(drawn.contains("s1"), "이미 보유한 증강이 다시 나왔다");
		}
	}

	@Test
	void 고를수록_풀이_줄어들다_결국_바닥난다() {
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("s3", PerkRarity.SILVER));
		RandomSource random = RandomSource.create(SEED);

		// 한 회차에 실버 구간이 이어지면 고른 만큼 후보가 사라진다.
		assertEquals(3, PerkDraft.draw(PerkRarity.SILVER, pool, List.of(), random, 3).size());
		assertEquals(2, PerkDraft.draw(PerkRarity.SILVER, pool, List.of("s1"), random, 3).size());
		assertEquals(1, PerkDraft.draw(PerkRarity.SILVER, pool, List.of("s1", "s2"), random, 3).size());
		assertTrue(PerkDraft.draw(PerkRarity.SILVER, pool, List.of("s1", "s2", "s3"), random, 3)
				.isEmpty());
	}

	@Test
	void 보유한_증강은_어느_등급에서든_후보에서_빠진다() {
		// 폴백으로 끌어온 등급이라도 이미 가진 것은 다시 나오면 안 된다.
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("g1", PerkRarity.GOLD),
				once("g2", PerkRarity.GOLD),
				once("p1", PerkRarity.PRISM));
		List<String> owned = List.of("s1", "g1");
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, pool, owned, random, 3);

		assertEquals(Set.of("g2", "p1"), new HashSet<>(drawn));
	}

	// ------------------------------------------------------------------ 풀 부족 시 폴백

	@Test
	void 실버가_모자라면_골드로_채우고_그래도_모자라면_프리즘로_채운다() {
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("g1", PerkRarity.GOLD),
				once("p1", PerkRarity.PRISM),
				once("p2", PerkRarity.PRISM));
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, pool, List.of(), random, 3);

			assertEquals(3, drawn.size(), "세 개를 채울 수 있는데 못 채웠다: " + drawn);
			assertEquals(3, new HashSet<>(drawn).size());
			assertTrue(drawn.contains("s1"), "제 등급이 먼저 소진돼야 한다: " + drawn);
			assertTrue(drawn.contains("g1"), "프리즘보다 골드를 먼저 끌어와야 한다: " + drawn);
		}
	}

	@Test
	void 골드가_모자라면_실버_먼저_그다음_프리즘다() {
		List<Perk> pool = List.of(
				once("g1", PerkRarity.GOLD),
				once("s1", PerkRarity.SILVER),
				once("p1", PerkRarity.PRISM),
				once("p2", PerkRarity.PRISM));
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(PerkRarity.GOLD, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			assertTrue(drawn.contains("g1") && drawn.contains("s1"),
					"골드 → 실버 → 프리즘 순서가 아니다: " + drawn);
		}
	}

	@Test
	void 프리즘가_모자라면_골드_먼저_그다음_실버다() {
		List<Perk> pool = List.of(
				once("p1", PerkRarity.PRISM),
				once("g1", PerkRarity.GOLD),
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER));
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(PerkRarity.PRISM, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			assertTrue(drawn.contains("p1") && drawn.contains("g1"),
					"프리즘 → 골드 → 실버 순서가 아니다: " + drawn);
		}
	}

	@Test
	void 십오렙에_프리즘가_비면_골드로_채운다() {
		List<Perk> pool = List.of(
				once("g1", PerkRarity.GOLD),
				once("g2", PerkRarity.GOLD),
				once("g3", PerkRarity.GOLD),
				once("s1", PerkRarity.SILVER));
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(15, pool, List.of(), random, 3);

		assertEquals(3, drawn.size());
		drawn.forEach(id -> assertEquals(PerkRarity.GOLD, index.get(id),
				"골드가 세 개나 있으니 실버까지 내려갈 이유가 없다: " + drawn));
	}

	@Test
	void 후보가_모자라면_가능한_만큼만_준다() {
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("g1", PerkRarity.GOLD));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(10, pool, List.of(), random, 3);

		assertEquals(2, drawn.size());
		assertEquals(Set.of("s1", "g1"), new HashSet<>(drawn));
	}

	@Test
	void 뽑을_후보가_하나도_없으면_빈_리스트다() {
		List<Perk> pool = List.of(once("s1", PerkRarity.SILVER));
		RandomSource random = RandomSource.create(SEED);

		assertTrue(PerkDraft.draw(10, pool, List.of("s1"), random, 3).isEmpty());
		assertTrue(PerkDraft.draw(10, List.of(), List.of(), random, 3).isEmpty());
		assertTrue(PerkDraft.draw(10, pool, List.of(), random, 0).isEmpty());
		assertTrue(PerkDraft.draw(10, pool, List.of(), null, 3).isEmpty());
		assertTrue(PerkDraft.draw((PerkRarity) null, pool, List.of(), random, 3).isEmpty());
	}

	@Test
	void 풀에_같은_id가_두_번_있어도_한_번만_나온다() {
		List<Perk> pool = new ArrayList<>();
		pool.add(once("s1", PerkRarity.SILVER));
		pool.add(once("s1", PerkRarity.SILVER));
		pool.add(once("s2", PerkRarity.SILVER));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, pool, List.of(), random, 3);

		assertEquals(2, drawn.size());
		assertEquals(Set.of("s1", "s2"), new HashSet<>(drawn));
	}

	@Test
	void 보유_목록이_비어_있거나_없어도_동작한다() {
		List<Perk> pool = ninePool();
		RandomSource random = RandomSource.create(SEED);

		assertEquals(3, PerkDraft.draw(10, pool, List.of(), random, 3).size());
		assertEquals(3, PerkDraft.draw(10, pool, null, random, 3).size());
		assertEquals(3, PerkDraft.draw(10, pool, List.of(), random).size(), "기본 개수는 3개다");
		assertEquals(3, PerkDraft.DEFAULT_OPTIONS);
	}

	// ------------------------------------------------------------------ min_level 필터

	@Test
	void min_level_이_구간보다_높으면_후보에서_빠진다() {
		List<Perk> pool = List.of(onceWithMinLevel("p1", PerkRarity.PRISM, 30));
		RandomSource random = RandomSource.create(SEED);

		assertTrue(PerkDraft.draw(15, pool, List.of(), random, 3).isEmpty(),
				"min_level 이 30인 증강은 15렙에 나오면 안 된다");
		assertEquals(List.of("p1"), PerkDraft.draw(30, pool, List.of(), random, 3),
				"30렙에는 나와야 한다");
	}

	@Test
	void min_level_은_구간을_아는_등급_지정_뽑기에도_적용된다() {
		// 도박꾼의 20·25 실버 고정처럼, 등급을 이미 정해 놓고 구간만 함께 넘기는 경로다.
		List<Perk> pool = List.of(onceWithMinLevel("p1", PerkRarity.PRISM, 30));
		RandomSource random = RandomSource.create(SEED);

		assertTrue(PerkDraft.draw(PerkRarity.PRISM, 15, pool, List.of(), random, 3).isEmpty());
		assertEquals(List.of("p1"), PerkDraft.draw(PerkRarity.PRISM, 30, pool, List.of(), random, 3));
	}

	@Test
	void 구간을_모르는_옛_오버로드는_min_level_을_걸지_않는다() {
		// 구간 정보가 없는 호출부(과거 코드·하위 호환)는 min_level 을 검증할 방법이 없으므로
		// 걸지 않는다 — 필터링이 필요하면 milestone 을 함께 받는 오버로드를 써야 한다.
		List<Perk> pool = List.of(onceWithMinLevel("p1", PerkRarity.PRISM, 30));
		RandomSource random = RandomSource.create(SEED);

		assertEquals(List.of("p1"), PerkDraft.draw(PerkRarity.PRISM, pool, List.of(), random, 3));
	}

	@Test
	void min_level_기본값_0은_아무_구간에서나_나온다() {
		List<Perk> pool = List.of(once("p1", PerkRarity.PRISM));
		RandomSource random = RandomSource.create(SEED);

		assertEquals(List.of("p1"), PerkDraft.draw(15, pool, List.of(), random, 3));
		assertEquals(List.of("p1"), PerkDraft.draw(30, pool, List.of(), random, 3));
	}

	@Test
	void 반환된_목록은_수정할_수_없다() {
		List<String> drawn = PerkDraft.draw(10, ninePool(), List.of(), RandomSource.create(SEED), 3);

		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> drawn.add("끼워넣기"));
	}
}
