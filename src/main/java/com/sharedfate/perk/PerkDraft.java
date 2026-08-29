package com.sharedfate.perk;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
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

	/** 플레 라운드로 고정된 구간. 한 회차에 단 한 번뿐이다. */
	public static final int PLATINUM_MILESTONE = 15;

	/**
	 * 플레가 아닌 구간에서 실버가 나올 확률(퍼센트). 나머지는 골드다.
	 *
	 * <p>밸런스를 보고 조정할 수 있게 상수로 빼 뒀다. 0이면 전부 골드, 100이면 전부 실버다.
	 */
	public static final int SILVER_PERCENT = 50;

	private PerkDraft() {
	}

	/**
	 * 이 구간에 배정할 등급을 정한다.
	 *
	 * <p>{@link #PLATINUM_MILESTONE}은 무작위가 아니라 <b>항상</b> 플레다. 나머지 구간은
	 * {@link #SILVER_PERCENT} 확률로 실버, 아니면 골드다.
	 *
	 * @param milestone 레벨 구간 (5, 10, …, 35)
	 * @param random    난수원. 고정 시드를 주면 결과가 결정론적이다
	 */
	public static PerkRarity rarityFor(int milestone, RandomSource random) {
		if (milestone == PLATINUM_MILESTONE) {
			return PerkRarity.PLATINUM;
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
	 * <p>실버 부족 → 골드 → 플레, 골드 부족 → 실버 → 플레, 플레 부족 → 골드 → 실버.
	 * 등급 차이가 작은 쪽부터 끌어온다.
	 */
	public static List<PerkRarity> fallbackOrder(PerkRarity rarity) {
		return switch (rarity) {
			case SILVER -> List.of(PerkRarity.SILVER, PerkRarity.GOLD, PerkRarity.PLATINUM);
			case GOLD -> List.of(PerkRarity.GOLD, PerkRarity.SILVER, PerkRarity.PLATINUM);
			case PLATINUM -> List.of(PerkRarity.PLATINUM, PerkRarity.GOLD, PerkRarity.SILVER);
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
		return draw(rarityFor(milestone, random), pool, owned, random, count);
	}

	/** 기본 개수(3개)로 뽑는다. */
	public static List<String> draw(int milestone, List<Perk> pool, List<String> owned,
			RandomSource random) {
		return draw(milestone, pool, owned, random, DEFAULT_OPTIONS);
	}

	/**
	 * 등급을 직접 지정해 후보를 최대 {@code count}개 뽑는다.
	 *
	 * <p>구간 배정을 거치지 않으므로 폴백 동작을 그 자체로 검증할 수 있다.
	 *
	 * @param rarity 뽑을 등급
	 */
	public static List<String> draw(PerkRarity rarity, List<Perk> pool, List<String> owned,
			RandomSource random, int count) {
		if (rarity == null || pool == null || pool.isEmpty() || random == null || count <= 0) {
			return List.of();
		}

		Map<PerkRarity, List<Perk>> remaining = eligibleByRarity(pool, owned);
		List<String> drawn = new ArrayList<>(count);
		for (PerkRarity bucketRarity : fallbackOrder(rarity)) {
			List<Perk> bucket = remaining.get(bucketRarity);
			while (drawn.size() < count && !bucket.isEmpty()) {
				drawn.add(bucket.remove(random.nextInt(bucket.size())).id());
			}
			if (drawn.size() >= count) {
				break;
			}
		}
		return List.copyOf(drawn);
	}

	/**
	 * 아직 고르지 않은 증강만 등급별로 모은다.
	 *
	 * <p>증강은 중첩되지 않는다. 한 번 보유하면 그 회차 동안 영원히 후보에서 빠진다.
	 * 풀에 같은 id가 두 번 들어 있어도 한 번만 담는다.
	 */
	private static Map<PerkRarity, List<Perk>> eligibleByRarity(List<Perk> pool, List<String> owned) {
		Set<String> ownedIds = ownedIds(owned);
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
			byRarity.get(perk.rarity()).add(perk);
		}
		return byRarity;
	}

	private static Set<String> ownedIds(List<String> owned) {
		Set<String> ids = new HashSet<>();
		if (owned == null) {
			return ids;
		}
		for (String perkId : owned) {
			if (perkId != null) {
				ids.add(perkId);
			}
		}
		return ids;
	}
}
