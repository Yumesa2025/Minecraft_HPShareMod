package com.sharedfate.perk;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkDraftTest {
	private static final long SEED = 20260828L;

	private static Perk once(String id, PerkRarity rarity) {
		return new Perk(id, id, "설명 " + id, rarity, false, 1, List.of());
	}

	private static Perk stacking(String id, PerkRarity rarity, int maxStacks) {
		return new Perk(id, id, "설명 " + id, rarity, true, maxStacks, List.of());
	}

	/** COMMON 3개 / RARE 3개 / EPIC 3개짜리 표준 풀. */
	private static List<Perk> ninePool() {
		return List.of(
				once("c1", PerkRarity.COMMON),
				once("c2", PerkRarity.COMMON),
				once("c3", PerkRarity.COMMON),
				once("r1", PerkRarity.RARE),
				once("r2", PerkRarity.RARE),
				once("r3", PerkRarity.RARE),
				once("e1", PerkRarity.EPIC),
				once("e2", PerkRarity.EPIC),
				once("e3", PerkRarity.EPIC));
	}

	private static Map<String, PerkRarity> rarityIndex(List<Perk> pool) {
		Map<String, PerkRarity> index = new HashMap<>();
		pool.forEach(perk -> index.put(perk.id(), perk.rarity()));
		return index;
	}

	@Test
	void 같은_시드는_항상_같은_결과를_준다() {
		List<Perk> pool = ninePool();

		List<String> first = PerkDraft.draw(15, pool, List.of(), RandomSource.create(SEED), 3);
		List<String> second = PerkDraft.draw(15, pool, List.of(), RandomSource.create(SEED), 3);

		assertEquals(3, first.size());
		assertEquals(first, second, "같은 시드면 후보가 완전히 같아야 한다");
	}

	@Test
	void 시드가_다르면_결과도_갈린다() {
		List<Perk> pool = ninePool();
		RandomSource random = RandomSource.create(SEED);

		Set<List<String>> results = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			results.add(PerkDraft.draw(21, pool, List.of(), random, 3));
		}

		assertTrue(results.size() > 1, "난수를 계속 굴리면 서로 다른 조합이 나와야 한다");
	}

	@Test
	void 같은_추첨_안에서_같은_증강이_두_번_나오지_않는다() {
		List<Perk> pool = ninePool();
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 500; i++) {
			List<String> drawn = PerkDraft.draw(27, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			assertEquals(3, new HashSet<>(drawn).size(), "중복 후보가 나왔다: " + drawn);
		}
	}

	@Test
	void 저구간에서는_에픽이_절대_나오지_않고_커먼이_대부분이다() {
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		int[] counts = new int[PerkRarity.values().length];
		int rounds = 20000;
		for (int i = 0; i < rounds; i++) {
			List<String> drawn = PerkDraft.draw(3, pool, List.of(), random, 1);
			counts[index.get(drawn.getFirst()).ordinal()]++;
		}

		assertEquals(0, counts[PerkRarity.EPIC.ordinal()], "3~12 구간에서는 에픽 가중치가 0이다");
		assertRatio(0.75, counts[PerkRarity.COMMON.ordinal()] / (double) rounds);
		assertRatio(0.25, counts[PerkRarity.RARE.ordinal()] / (double) rounds);
	}

	@Test
	void 중간_구간_가중치는_40_50_10_이다() {
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		int[] counts = new int[PerkRarity.values().length];
		int rounds = 20000;
		for (int i = 0; i < rounds; i++) {
			counts[index.get(PerkDraft.draw(18, pool, List.of(), random, 1).getFirst()).ordinal()]++;
		}

		assertRatio(0.40, counts[PerkRarity.COMMON.ordinal()] / (double) rounds);
		assertRatio(0.50, counts[PerkRarity.RARE.ordinal()] / (double) rounds);
		assertRatio(0.10, counts[PerkRarity.EPIC.ordinal()] / (double) rounds);
	}

	@Test
	void 고구간_가중치는_15_50_35_이다() {
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		int[] counts = new int[PerkRarity.values().length];
		int rounds = 20000;
		for (int i = 0; i < rounds; i++) {
			counts[index.get(PerkDraft.draw(36, pool, List.of(), random, 1).getFirst()).ordinal()]++;
		}

		assertRatio(0.15, counts[PerkRarity.COMMON.ordinal()] / (double) rounds);
		assertRatio(0.50, counts[PerkRarity.RARE.ordinal()] / (double) rounds);
		assertRatio(0.35, counts[PerkRarity.EPIC.ordinal()] / (double) rounds);
	}

	@Test
	void 구간별_가중치표() {
		for (int milestone : new int[] {3, 6, 9, 12}) {
			org.junit.jupiter.api.Assertions.assertArrayEquals(
					new int[] {75, 25, 0}, PerkDraft.weightsFor(milestone));
		}
		for (int milestone : new int[] {15, 18, 21, 24}) {
			org.junit.jupiter.api.Assertions.assertArrayEquals(
					new int[] {40, 50, 10}, PerkDraft.weightsFor(milestone));
		}
		for (int milestone : new int[] {27, 30, 33, 36}) {
			org.junit.jupiter.api.Assertions.assertArrayEquals(
					new int[] {15, 50, 35}, PerkDraft.weightsFor(milestone));
		}
	}

	@Test
	void 중첩_불가_증강은_한_번_고르면_후보에서_빠진다() {
		List<Perk> pool = List.of(
				once("c1", PerkRarity.COMMON),
				once("c2", PerkRarity.COMMON),
				once("r1", PerkRarity.RARE));
		List<PerkStack> owned = List.of(new PerkStack("c1", 1));
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(9, pool, owned, random, 3);

			assertEquals(2, drawn.size(), "남은 후보는 두 개뿐이다");
			assertFalse(drawn.contains("c1"), "이미 보유한 중첩 불가 증강이 다시 나왔다");
		}
	}

	@Test
	void 중첩_가능_증강은_상한에_닿기_전까지는_계속_나온다() {
		List<Perk> pool = List.of(stacking("s1", PerkRarity.COMMON, 3));
		RandomSource random = RandomSource.create(SEED);

		assertEquals(List.of("s1"), PerkDraft.draw(9, pool, List.of(), random, 3));
		assertEquals(List.of("s1"),
				PerkDraft.draw(9, pool, List.of(new PerkStack("s1", 1)), random, 3));
		assertEquals(List.of("s1"),
				PerkDraft.draw(9, pool, List.of(new PerkStack("s1", 2)), random, 3));
	}

	@Test
	void 최대_중첩에_도달하면_후보에서_빠진다() {
		List<Perk> pool = List.of(
				stacking("s1", PerkRarity.COMMON, 3),
				once("c1", PerkRarity.COMMON));
		List<PerkStack> owned = List.of(new PerkStack("s1", 3));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(9, pool, owned, random, 3);

		assertEquals(List.of("c1"), drawn);
	}

	@Test
	void 후보가_모자라면_가능한_만큼만_준다() {
		List<Perk> pool = List.of(
				once("c1", PerkRarity.COMMON),
				once("r1", PerkRarity.RARE));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(12, pool, List.of(), random, 3);

		assertEquals(2, drawn.size());
		assertEquals(Set.of("c1", "r1"), new HashSet<>(drawn));
	}

	@Test
	void 뽑을_후보가_하나도_없으면_빈_리스트다() {
		List<Perk> pool = List.of(once("c1", PerkRarity.COMMON));
		RandomSource random = RandomSource.create(SEED);

		assertTrue(PerkDraft.draw(9, pool, List.of(new PerkStack("c1", 1)), random, 3).isEmpty());
		assertTrue(PerkDraft.draw(9, List.of(), List.of(), random, 3).isEmpty());
		assertTrue(PerkDraft.draw(9, pool, List.of(), random, 0).isEmpty());
	}

	@Test
	void 가중치가_0인_등급밖에_안_남으면_그_등급에서라도_채운다() {
		// 3 구간은 에픽 가중치가 0이지만, 남은 후보가 에픽뿐이면 빈손으로 두는 것보다 채우는 게 낫다
		List<Perk> pool = List.of(
				once("e1", PerkRarity.EPIC),
				once("e2", PerkRarity.EPIC));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(3, pool, List.of(), random, 3);

		assertEquals(2, drawn.size());
		assertEquals(Set.of("e1", "e2"), new HashSet<>(drawn));
	}

	@Test
	void 등급이_모자라면_다른_등급에서_채워_최대한_세_개를_만든다() {
		// 3 구간: 커먼 1개 + 레어 1개 + 에픽 2개 → 가중치 등급이 바닥나면 에픽까지 끌어온다
		List<Perk> pool = List.of(
				once("c1", PerkRarity.COMMON),
				once("r1", PerkRarity.RARE),
				once("e1", PerkRarity.EPIC),
				once("e2", PerkRarity.EPIC));
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(3, pool, List.of(), random, 3);

			assertEquals(3, drawn.size(), "세 개를 채울 수 있는데 못 채웠다: " + drawn);
			assertEquals(3, new HashSet<>(drawn).size());
			assertTrue(drawn.contains("c1") && drawn.contains("r1"),
					"가중치 있는 등급이 먼저 소진돼야 한다: " + drawn);
		}
	}

	@Test
	void 풀에_같은_id가_두_번_있어도_한_번만_나온다() {
		List<Perk> pool = new ArrayList<>();
		pool.add(once("c1", PerkRarity.COMMON));
		pool.add(once("c1", PerkRarity.COMMON));
		pool.add(once("c2", PerkRarity.COMMON));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(6, pool, List.of(), random, 3);

		assertEquals(2, drawn.size());
		assertEquals(Set.of("c1", "c2"), new HashSet<>(drawn));
	}

	@Test
	void 보유_목록이_비어_있거나_없어도_동작한다() {
		List<Perk> pool = ninePool();
		RandomSource random = RandomSource.create(SEED);

		assertEquals(3, PerkDraft.draw(9, pool, List.of(), random, 3).size());
		assertEquals(3, PerkDraft.draw(9, pool, null, random, 3).size());
		assertEquals(3, PerkDraft.draw(9, pool, List.of(), random).size(),
				"기본 개수는 3개다");
	}

	private static void assertRatio(double expected, double actual) {
		assertTrue(Math.abs(expected - actual) < 0.03,
				"기대 비율 " + expected + ", 실제 " + actual);
	}
}
