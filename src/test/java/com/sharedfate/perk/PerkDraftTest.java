package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
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

	/** 등급이 고정되지 않아 확률표로 굴리는 일곱 구간. */
	private static final int[] RANDOM_MILESTONES = {5, 10, 20, 25, 30, 35, 40};

	@BeforeAll
	static void bootstrap() {
		// 확률표 자체는 순수 계산이라 부트스트랩이 필요 없지만, 「회차가 넘어가면 카운터가
		// 0 으로 돌아간다」만은 그 값이 사는 자리(TeamState)를 실제로 만들어 봐야 뜻이 있다.
		TestBootstrap.ensureInitialized();
	}

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
	void 삼십렙은_더는_고정이_아니라_확률표를_따른다() {
		// 30 에서 프리즘가 사라진 것은 아니다. 시드를 바꿔 가며 굴리면 세 등급이 모두 나와야
		// 한다 — 하나라도 안 나오면 30 이 다른 값으로 고정됐다는 뜻이다.
		assertFalse(PerkDraft.PRISM_MILESTONES.contains(30), "30 은 고정 프리즘 구간이 아니다");
		assertEquals(1, PerkDraft.PRISM_MILESTONES.size(), "고정 프리즘 구간은 15 하나뿐이다");

		RandomSource random = RandomSource.create(SEED);
		Set<PerkRarity> seen = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			seen.add(PerkDraft.rarityFor(30, 0, false, false, random));
		}

		assertEquals(3, seen.size(), "30렙에서 세 등급이 모두 나와야 한다: " + seen);
	}

	@Test
	void 확률표는_세_값을_더하면_언제나_백이다() {
		assertEquals(PerkDraft.MAX_EXTRA_PRISM + 1, PerkDraft.ODDS_BY_EXTRA_PRISM.size(),
				"표의 줄 수와 프리즘 한도가 어긋나면 한도에 닿은 줄을 짚을 수 없다");
		for (int extra = -1; extra <= PerkDraft.MAX_EXTRA_PRISM + 2; extra++) {
			for (boolean boost : new boolean[] {false, true}) {
				for (boolean blocked : new boolean[] {false, true}) {
					PerkDraft.RarityOdds odds = PerkDraft.oddsFor(extra, boost, blocked);
					assertEquals(100, odds.silver() + odds.gold() + odds.prism(),
							"합이 100이 아니다: extra=" + extra + " boost=" + boost
									+ " blocked=" + blocked + " → " + odds);
					assertTrue(odds.silver() >= 0 && odds.gold() >= 0 && odds.prism() >= 0,
							"음수 확률이 나왔다: " + odds);
				}
			}
		}
	}

	@Test
	void 추가_프리즘이_한도에_닿으면_일곱_구간에서_프리즘가_절대_안_나온다() {
		RandomSource random = RandomSource.create(SEED);

		// 한도를 넘긴 값(손상된 저장)도 한도와 똑같이 다뤄야 한다. 「원정 준비물」 보너스가
		// 한도를 뚫고 들어오는 일도 없어야 한다.
		for (int extra : new int[] {PerkDraft.MAX_EXTRA_PRISM, PerkDraft.MAX_EXTRA_PRISM + 5}) {
			for (int round = 0; round < 2000; round++) {
				for (int milestone : RANDOM_MILESTONES) {
					assertNotEquals(PerkRarity.PRISM,
							PerkDraft.rarityFor(milestone, extra, false, false, random),
							milestone + "렙에서 한도를 넘긴 프리즘가 나왔다");
					assertNotEquals(PerkRarity.PRISM,
							PerkDraft.rarityFor(milestone, extra, false, true, random),
							milestone + "렙에서 보너스가 프리즘 한도를 뚫었다");
				}
			}
		}
	}

	@Test
	void 실버가_막혀_있으면_실버가_절대_안_나온다() {
		RandomSource random = RandomSource.create(SEED);

		for (int extra = 0; extra <= PerkDraft.MAX_EXTRA_PRISM; extra++) {
			for (int round = 0; round < 2000; round++) {
				for (int milestone : RANDOM_MILESTONES) {
					assertNotEquals(PerkRarity.SILVER,
							PerkDraft.rarityFor(milestone, extra, true, false, random),
							milestone + "렙에서 막혀 있어야 할 실버가 나왔다");
					assertNotEquals(PerkRarity.SILVER,
							PerkDraft.rarityFor(milestone, extra, true, true, random),
							milestone + "렙에서 막혀 있어야 할 실버가 나왔다(보너스 있음)");
				}
			}
		}
		// 난수원이 없어 굴리지 못하는 길도 차단을 뚫지 않는다.
		assertEquals(PerkRarity.GOLD, PerkDraft.rarityFor(20, 0, true, false, null));
		assertEquals(PerkRarity.SILVER, PerkDraft.rarityFor(20, 0, false, false, null));
	}

	@Test
	void 프리즘가_없는_상태에서는_열_번에_한_번쯤_프리즘다() {
		RandomSource random = RandomSource.create(SEED);
		int rounds = 10000;

		int prism = 0;
		for (int i = 0; i < rounds; i++) {
			if (PerkDraft.rarityFor(30, 0, false, false, random) == PerkRarity.PRISM) {
				prism++;
			}
		}

		double ratio = prism / (double) rounds;
		double expected = PerkDraft.ODDS_BY_EXTRA_PRISM.getFirst().prism() / 100.0;
		assertTrue(Math.abs(expected - ratio) < 0.02,
				"기대 비율 " + expected + ", 실제 " + ratio);
	}

	@Test
	void 원정_준비물은_프리즘_확률을_골드에서_떼어_올린다() {
		PerkDraft.RarityOdds plain = PerkDraft.oddsFor(0, false, false);
		PerkDraft.RarityOdds boosted = PerkDraft.oddsFor(0, true, false);

		assertEquals(plain.prism() + PerkDraft.PRISM_BOOST_PERCENT, boosted.prism());
		assertEquals(plain.gold() - PerkDraft.PRISM_BOOST_PERCENT, boosted.gold());
		assertEquals(plain.silver(), boosted.silver(), "보너스 몫은 실버가 아니라 골드에서 뗀다");
	}

	@Test
	void 실버가_막히면_실버_몫이_통째로_골드로_간다() {
		PerkDraft.RarityOdds plain = PerkDraft.oddsFor(0, false, false);
		PerkDraft.RarityOdds blocked = PerkDraft.oddsFor(0, false, true);

		assertEquals(0, blocked.silver());
		assertEquals(plain.silver() + plain.gold(), blocked.gold());
		assertEquals(plain.prism(), blocked.prism(), "실버가 막혔다고 프리즘가 늘지는 않는다");
	}

	@Test
	void 회차가_넘어가면_추가_프리즘_카운터가_0으로_돌아간다() {
		TeamState state = TeamState.fresh(20.0F);
		assertEquals(0, state.extraPrismRounds, "갓 만든 팀 상태는 한 번도 안 나온 상태다");

		state.extraPrismRounds = PerkDraft.MAX_EXTRA_PRISM;

		// 회차가 넘어가면 팀 상태를 통째로 새로 만든다(TeamManager.restoreFreshRoster).
		// 월드를 지우지 않는 서버에서도 회차를 시작하는 자리가 이 값을 0 으로 되돌린다.
		assertEquals(0, TeamState.fresh(20.0F).extraPrismRounds,
				"새 회차의 팀 상태에 지난 회차의 프리즘 수가 남았다");

		// 손상된 저장에서 흘러들어온 값은 조용히 한도 안으로 접힌다.
		state.extraPrismRounds = 99;
		state.sanitizePerks();
		assertEquals(PerkDraft.MAX_EXTRA_PRISM, state.extraPrismRounds);
		state.extraPrismRounds = -3;
		state.sanitizePerks();
		assertEquals(0, state.extraPrismRounds);
	}

	@Test
	void 옛_두_인자_시그니처는_아무것도_안_나온_상태로_굴린다() {
		// 회차 상태를 모르는 옛 호출부와 기존 시험이 그대로 돌아가야 한다.
		RandomSource first = RandomSource.create(SEED);
		RandomSource second = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			assertEquals(PerkDraft.rarityFor(20, 0, false, false, first),
					PerkDraft.rarityFor(20, second));
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
	void 실버와_골드는_표에_적힌_비율대로_나온다() {
		RandomSource random = RandomSource.create(SEED);
		int rounds = 40000;

		int silver = 0;
		int gold = 0;
		for (int i = 0; i < rounds; i++) {
			switch (PerkDraft.rarityFor(20, 0, false, false, random)) {
				case SILVER -> silver++;
				case GOLD -> gold++;
				default -> {
				}
			}
		}

		PerkDraft.RarityOdds odds = PerkDraft.ODDS_BY_EXTRA_PRISM.getFirst();
		assertTrue(Math.abs(odds.silver() / 100.0 - silver / (double) rounds) < 0.02,
				"실버 기대 비율 " + odds.silver() + "%, 실제 " + silver * 100.0 / rounds + "%");
		assertTrue(Math.abs(odds.gold() / 100.0 - gold / (double) rounds) < 0.02,
				"골드 기대 비율 " + odds.gold() + "%, 실제 " + gold * 100.0 / rounds + "%");
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
		// 등급이 무엇으로 정해지든 한 라운드에 섞여 나오지는 않는다. 이제 확률 구간에서도
		// 프리즘가 나올 수 있으므로 「프리즘가 아니다」까지 함께 확인하지는 않는다.
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
		}
	}

	@Test
	void 한도에_닿은_라운드는_후보에_프리즘가_섞이지_않는다() {
		// 한도에 닿으면 등급 추첨이 프리즘를 뽑지 않는다. 풀에 실버·골드가 넉넉하면 폴백도
		// 프리즘까지 내려갈 이유가 없으므로 화면에 프리즘 카드가 뜰 길이 아예 없다.
		List<Perk> pool = ninePool();
		Map<String, PerkRarity> index = rarityIndex(pool);
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 500; i++) {
			int milestone = RANDOM_MILESTONES[i % RANDOM_MILESTONES.length];
			PerkRarity rarity = PerkDraft.rarityFor(
					milestone, PerkDraft.MAX_EXTRA_PRISM, false, true, random);
			List<String> drawn = PerkDraft.draw(rarity, milestone, pool, List.of(), random, 3);

			assertEquals(3, drawn.size());
			drawn.forEach(id -> assertNotEquals(PerkRarity.PRISM, index.get(id),
					milestone + "렙에서 한도를 넘긴 프리즘 후보가 떴다: " + drawn));
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
	void 십오렙에서_고른_프리즘은_뒤에_또_프리즘가_떠도_다시_나오지_않는다() {
		// 15렙에서 프리즘 하나를 이미 골랐다고 가정한다. 30렙이 확률로 다시 프리즘 라운드가
		// 되더라도 같은 증강이 후보로 다시 뜨면 안 된다. 폴백(프리즘이 모자라면 골드로 채움)이
		// 결과를 헷갈리게 하지 않도록 프리즘만 네 개 있는 풀로 확인한다 — 이 풀에서는 등급이
		// 무엇으로 정해지든 폴백이 프리즘로 내려온다.
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
		// 여러 구간을 많이 돌리면 세 등급 라운드가 모두 나와야 한다. 한도가 차지 않은 상태로
		// 계속 굴리므로 프리즘도 열 번에 한 번쯤 섞인다.
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
		assertTrue(rounds.getOrDefault(PerkRarity.PRISM, 0) > 0, "프리즘 라운드가 한 번도 없었다");
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
			// 20렙은 고정 구간이 아니라 등급이 매번 갈리므로 조합도 갈린다. 15렙은 언제나
			// 프리즘라 후보 3개짜리 풀에서는 같은 조합 하나뿐이다.
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

	// ------------------------------------------------------------------ 다시 뽑기 회피

	@Test
	void 다시_뽑으면_직전_후보가_나오지_않는다() {
		// 실버가 여섯이라 직전 3개를 빼도 셋이 남는다. 남는 한 세 개가 그대로 나와야 한다.
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("s3", PerkRarity.SILVER),
				once("s4", PerkRarity.SILVER),
				once("s5", PerkRarity.SILVER),
				once("s6", PerkRarity.SILVER));
		List<String> shown = List.of("s1", "s2", "s3");
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 500; i++) {
			List<String> drawn = PerkDraft.draw(
					PerkRarity.SILVER, 20, pool, List.of(), shown, random, 3);

			assertEquals(Set.of("s4", "s5", "s6"), new HashSet<>(drawn),
					"직전에 보여 준 후보가 다시 나왔다: " + drawn);
		}
	}

	@Test
	void 회피하고_나면_모자랄_때는_뺐던_것으로_채운다() {
		// 실버가 넷뿐이라 직전 3개를 빼면 하나밖에 안 남는다. 카드가 한 장만 뜨는 것보다는
		// 뺐던 것에서 두 장을 마저 채우는 편이 낫다.
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("s3", PerkRarity.SILVER),
				once("s4", PerkRarity.SILVER));
		List<String> shown = List.of("s1", "s2", "s3");
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(
					PerkRarity.SILVER, 20, pool, List.of(), shown, random, 3);

			assertEquals(3, drawn.size(), "세 장을 채울 수 있는데 못 채웠다: " + drawn);
			assertEquals(3, new HashSet<>(drawn).size(), "중복 후보가 나왔다: " + drawn);
			assertTrue(drawn.contains("s4"), "회피하지 않은 것이 먼저 나와야 한다: " + drawn);
		}
	}

	@Test
	void 회피보다_보유_제외가_우선이다() {
		// avoid 는 「되도록」이지만 owned 는 「절대」다. 모자라서 뺐던 것을 다시 끌어올 때도
		// 이미 가진 증강은 섞이면 안 된다.
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("s3", PerkRarity.SILVER),
				once("s4", PerkRarity.SILVER));
		RandomSource random = RandomSource.create(SEED);

		for (int i = 0; i < 200; i++) {
			List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, 20, pool,
					List.of("s1"), List.of("s2", "s3", "s4"), random, 3);

			assertFalse(drawn.contains("s1"), "보유 증강이 회피 목록을 뚫고 나왔다: " + drawn);
			assertEquals(Set.of("s2", "s3", "s4"), new HashSet<>(drawn));
		}
	}

	@Test
	void 회피_목록이_비어_있거나_없으면_예전과_똑같다() {
		List<Perk> pool = ninePool();

		List<String> plain = PerkDraft.draw(
				PerkRarity.SILVER, 20, pool, List.of(), RandomSource.create(SEED), 3);
		List<String> empty = PerkDraft.draw(
				PerkRarity.SILVER, 20, pool, List.of(), List.of(), RandomSource.create(SEED), 3);
		List<String> none = PerkDraft.draw(
				PerkRarity.SILVER, 20, pool, List.of(), null, RandomSource.create(SEED), 3);

		assertEquals(plain, empty, "빈 회피 목록이 결과를 바꿨다");
		assertEquals(plain, none, "null 회피 목록이 결과를 바꿨다");
	}

	@Test
	void 회피는_등급을_넘지_않는다() {
		// 실버를 전부 피해도 골드로 새지 않는다 — 다시 뽑아도 등급은 그대로여야 한다.
		List<Perk> pool = List.of(
				once("s1", PerkRarity.SILVER),
				once("s2", PerkRarity.SILVER),
				once("s3", PerkRarity.SILVER),
				once("g1", PerkRarity.GOLD),
				once("g2", PerkRarity.GOLD),
				once("g3", PerkRarity.GOLD));
		RandomSource random = RandomSource.create(SEED);

		List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, 20, pool,
				List.of(), List.of("s1", "s2", "s3"), random, 3);

		assertEquals(Set.of("s1", "s2", "s3"), new HashSet<>(drawn),
				"실버가 셋 다 있는데 골드를 끌어왔다: " + drawn);
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
