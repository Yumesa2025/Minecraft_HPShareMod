package com.sharedfate.perk;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 증강 후보 추첨.
 *
 * <p>구간마다 등급이 하나로 정해지고 <b>그 등급에서만</b> 후보를 뽑는다. 한 라운드에
 * 나오는 3개는 전부 같은 등급이다. 이미 보유한 증강은 어떤 경우에도 후보에서 빠지고,
 * 한 번의 추첨 안에서 같은 증강이 두 번 나오지 않는다.
 *
 * <p>후보 풀은 호출자가 넘긴다. {@code PerkRegistry}에 직접 붙지 않아야 게임 실행 없이
 * 순수 단위 테스트로 검증할 수 있기 때문이다. 난수도 주입받으므로 고정 시드를 주면
 * 결과가 항상 같다.
 */
public final class PerkDraft {
	/** 한 번에 제시하는 기본 후보 수. */
	public static final int DEFAULT_OPTIONS = 3;

	/**
	 * 프리즘 라운드로 고정된 구간들. 2026-09-01부터 15·30 둘이다.
	 *
	 * <p>예전에는 {@code PRISM_MILESTONE}(단수, {@code int} 하나)이었다. 15만 고정이던 시절의
	 * 이름이라, 구간이 둘 이상이 되면서 집합으로 바꾸고 이름도 복수형으로 바꿨다.
	 */
	public static final Set<Integer> PRISM_MILESTONES = Set.of(15, 30);

	/**
	 * 프리즘가 아닌 구간에서 실버가 나올 확률(퍼센트). 나머지는 골드다.
	 *
	 * <p>밸런스를 보고 조정할 수 있게 상수로 빼 뒀다. 0이면 전부 골드, 100이면 전부 실버다.
	 */
	public static final int SILVER_PERCENT = 50;

	private PerkDraft() {
	}

	/**
	 * 이 구간에 배정할 등급을 정한다.
	 *
	 * <p>{@link #PRISM_MILESTONES}에 속한 구간은 무작위가 아니라 <b>항상</b> 프리즘다.
	 * 나머지 구간은 {@link #SILVER_PERCENT} 확률로 실버, 아니면 골드다.
	 *
	 * @param milestone 레벨 구간 (5, 10, …, 35)
	 * @param random    난수원. 고정 시드를 주면 결과가 결정론적이다
	 */
	public static PerkRarity rarityFor(int milestone, RandomSource random) {
		if (PRISM_MILESTONES.contains(milestone)) {
			return PerkRarity.PRISM;
		}
		if (random == null) {
			// 난수원이 없으면 굴릴 수가 없다. 터뜨리는 대신 가장 낮은 등급으로 둔다.
			return PerkRarity.SILVER;
		}
		return random.nextInt(100) < SILVER_PERCENT ? PerkRarity.SILVER : PerkRarity.GOLD;
	}

	/**
	 * 후보가 모자랄 때 어느 등급에서 채울지의 우선순위.
	 *
	 * <p>실버 부족 → 골드 → 프리즘, 골드 부족 → 실버 → 프리즘, 프리즘 부족 → 골드 → 실버.
	 * 등급 차이가 작은 쪽부터 끌어온다.
	 */
	public static List<PerkRarity> fallbackOrder(PerkRarity rarity) {
		return switch (rarity) {
			case SILVER -> List.of(PerkRarity.SILVER, PerkRarity.GOLD, PerkRarity.PRISM);
			case GOLD -> List.of(PerkRarity.GOLD, PerkRarity.SILVER, PerkRarity.PRISM);
			case PRISM -> List.of(PerkRarity.PRISM, PerkRarity.GOLD, PerkRarity.SILVER);
		};
	}

	/**
	 * 구간에 맞는 등급을 정한 뒤 후보를 최대 {@code count}개 뽑는다.
	 *
	 * <p>정해진 등급에 남은 후보가 모자라면 {@link #fallbackOrder} 순서대로 다른 등급에서
	 * 채운다. 그래도 부족하면 가능한 만큼만 돌려주고, 하나도 못 뽑으면 빈 리스트다.
	 *
	 * @param milestone 이 추첨이 속한 레벨 구간 (5, 10, …, 35)
	 * @param pool      전체 증강 목록
	 * @param owned     팀이 이미 보유한 증강의 id 목록
	 * @param random    난수원. 고정 시드를 주면 결과가 결정론적이다
	 * @param count     뽑을 개수
	 */
	public static List<String> draw(int milestone, List<Perk> pool, List<String> owned,
			RandomSource random, int count) {
		if (pool == null || pool.isEmpty() || random == null || count <= 0) {
			return List.of();
		}
		return draw(rarityFor(milestone, random), milestone, pool, owned, random, count);
	}

	/** 기본 개수(3개)로 뽑는다. */
	public static List<String> draw(int milestone, List<Perk> pool, List<String> owned,
			RandomSource random) {
		return draw(milestone, pool, owned, random, DEFAULT_OPTIONS);
	}

	/**
	 * 등급을 직접 지정해 후보를 최대 {@code count}개 뽑는다. 구간(레벨) 정보가 없으므로
	 * {@link Perk#minLevel} 로 거르지 않는다 — {@code min_level} 이 설정된 증강도 그대로 뽑힐
	 * 수 있다.
	 *
	 * <p>구간 배정을 거치지 않으므로 폴백 동작을 그 자체로 검증할 수 있다. 도박꾼처럼 "이 구간은
	 * 무조건 이 등급"인 경로가 여기로 들어온다. 구간을 안다면 {@link #draw(PerkRarity, int,
	 * List, List, RandomSource, int)} 를 대신 써야 {@code min_level} 이 지켜진다.
	 *
	 * @param rarity 뽑을 등급
	 */
	public static List<String> draw(PerkRarity rarity, List<Perk> pool, List<String> owned,
			RandomSource random, int count) {
		return draw(rarity, PerkMilestones.MAX, pool, owned, random, count);
	}

	/**
	 * 등급과 구간을 함께 지정해 후보를 최대 {@code count}개 뽑는다.
	 *
	 * <p>{@link Perk#minLevel} 이 이 {@code milestone} 보다 큰 증강은 후보에서 빠진다 —
	 * "특정 구간부터만 나오는 증강"(예: 프리즘 「환골탈태」, 30렙부터)을 이 한 곳에서 거른다.
	 * 일반 구간 추첨({@link #draw(int, List, List, RandomSource, int)})과, 구간을 아는 채로
	 * 등급을 고정하는 경로(도박꾼의 20·25 실버 고정 등)가 여기를 함께 쓴다.
	 *
	 * @param rarity    뽑을 등급
	 * @param milestone 이 추첨이 속한 레벨 구간
	 */
	public static List<String> draw(PerkRarity rarity, int milestone, List<Perk> pool,
			List<String> owned, RandomSource random, int count) {
		return draw(rarity, milestone, pool, owned, List.of(), random, count);
	}

	/**
	 * 등급·구간에 더해 <b>이번에는 피하고 싶은 후보</b>까지 지정해 뽑는다. 「다시 뽑기」가 쓴다.
	 *
	 * <p>{@code owned} 와 {@code avoid} 는 성격이 다르다. 보유 증강은 <b>절대</b> 나오면 안 되지만,
	 * 피하고 싶은 후보는 <b>되도록</b> 나오지 않으면 되는 것이다. 후보가 세 장 미만으로 뜨는
	 * 것보다는 한 장 겹치는 편이 낫다.
	 *
	 * <p>그래서 우선순위가 <b>등급이 먼저, 회피가 나중</b>이다. 한 등급 안에서 회피 대상이 아닌
	 * 것을 먼저 다 쓰고, 모자라면 <b>같은 등급의 회피 대상</b>을 꺼낸다. 그것마저 바닥나야
	 * {@link #fallbackOrder} 의 다음 등급으로 내려간다. 반대로 하면 「실버 라운드에서 다시
	 * 뽑았더니 골드가 나왔다」가 되는데, 그건 겹쳐 보이는 것보다 훨씬 나쁘다.
	 *
	 * <p>다시 뽑기는 이 방식으로 <b>직전에 보여 준 3장</b>을 넘긴다. 등급이 실버(30개)라면 거의
	 * 언제나 회피 대상이 아닌 쪽에서 다 채워져 방금 본 카드가 돌아오지 않는다.
	 *
	 * @param avoid 되도록 다시 내보내지 않을 증강 id. null 이나 빈 목록이면 아무것도 피하지 않는다
	 */
	public static List<String> draw(PerkRarity rarity, int milestone, List<Perk> pool,
			List<String> owned, List<String> avoid, RandomSource random, int count) {
		if (rarity == null || pool == null || pool.isEmpty() || random == null || count <= 0) {
			return List.of();
		}

		Map<PerkRarity, List<Perk>> remaining = eligibleByRarity(pool, owned, milestone);
		Map<PerkRarity, List<Perk>> avoided = extract(remaining, idSet(avoid));
		List<String> drawn = new ArrayList<>(count);
		for (PerkRarity bucketRarity : fallbackOrder(rarity)) {
			// 한 등급 안에서 회피 대상이 아닌 것을 먼저 다 쓰고, 모자랄 때만 회피 대상을 꺼낸다.
			// 등급을 내려가기 전에 반드시 이 순서를 지켜야 한다 — 남은 실버가 회피 대상뿐인데
			// 골드를 끌어오면 다시 뽑기가 등급을 바꾸는 셈이 되고, 그건 회피보다 훨씬 나쁘다.
			takeFrom(drawn, remaining.get(bucketRarity), random, count);
			takeFrom(drawn, avoided.get(bucketRarity), random, count);
			if (drawn.size() >= count) {
				break;
			}
		}
		return List.copyOf(drawn);
	}

	/** {@code count} 가 차거나 통이 빌 때까지 그 통에서 무작위로 꺼내 담는다. */
	private static void takeFrom(List<String> drawn, List<Perk> bucket, RandomSource random,
			int count) {
		while (drawn.size() < count && !bucket.isEmpty()) {
			drawn.add(bucket.remove(random.nextInt(bucket.size())).id());
		}
	}

	/**
	 * {@code buckets} 에서 {@code ids} 에 해당하는 것들을 <b>덜어내</b> 같은 모양의 통으로 돌려준다.
	 * 원본은 그만큼 줄어든다. 예비 통이라 순서를 지킬 이유가 없다.
	 */
	private static Map<PerkRarity, List<Perk>> extract(Map<PerkRarity, List<Perk>> buckets,
			Set<String> ids) {
		Map<PerkRarity, List<Perk>> taken = new EnumMap<>(PerkRarity.class);
		for (PerkRarity rarity : PerkRarity.values()) {
			List<Perk> held = new ArrayList<>();
			if (!ids.isEmpty()) {
				Iterator<Perk> cursor = buckets.get(rarity).iterator();
				while (cursor.hasNext()) {
					Perk perk = cursor.next();
					if (ids.contains(perk.id())) {
						held.add(perk);
						cursor.remove();
					}
				}
			}
			taken.put(rarity, held);
		}
		return taken;
	}

	/**
	 * 아직 고르지 않은, 이 구간에 나올 수 있는 증강만 등급별로 모은다.
	 *
	 * <p>증강은 중첩되지 않는다. 한 번 보유하면 그 회차 동안 영원히 후보에서 빠진다.
	 * 풀에 같은 id가 두 번 들어 있어도 한 번만 담는다. {@link Perk#minLevel} 이 {@code milestone}
	 * 보다 큰 증강도 여기서 함께 걸러진다.
	 */
	private static Map<PerkRarity, List<Perk>> eligibleByRarity(List<Perk> pool, List<String> owned,
			int milestone) {
		Set<String> ownedIds = idSet(owned);
		Map<PerkRarity, List<Perk>> byRarity = new EnumMap<>(PerkRarity.class);
		for (PerkRarity rarity : PerkRarity.values()) {
			byRarity.put(rarity, new ArrayList<>());
		}
		Set<String> seen = new HashSet<>();
		for (Perk perk : pool) {
			if (perk == null || perk.id() == null || perk.rarity() == null) {
				continue;
			}
			if (!seen.add(perk.id())) {
				continue;
			}
			if (ownedIds.contains(perk.id())) {
				continue;
			}
			if (perk.minLevel() > milestone) {
				continue;
			}
			byRarity.get(perk.rarity()).add(perk);
		}
		return byRarity;
	}

	/** null 을 빈 집합으로 받아 주는 id 집합 만들기. 보유 목록과 회피 목록이 함께 쓴다. */
	private static Set<String> idSet(List<String> perkIds) {
		Set<String> ids = new HashSet<>();
		if (perkIds == null) {
			return ids;
		}
		for (String perkId : perkIds) {
			if (perkId != null) {
				ids.add(perkId);
			}
		}
		return ids;
	}
}
