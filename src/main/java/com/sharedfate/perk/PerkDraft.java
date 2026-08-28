package com.sharedfate.perk;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 증강 후보 추첨.
 *
 * <p>구간이 오를수록 높은 등급이 나올 확률이 커진다. 이미 보유해서 더 고를 수 없는 증강은
 * 후보에서 빠지고, 한 번의 추첨 안에서 같은 증강이 두 번 나오지 않는다.
 *
 * <p>후보 풀은 호출자가 넘긴다. {@code PerkRegistry}에 직접 붙지 않아야 게임 실행 없이
 * 순수 단위 테스트로 검증할 수 있기 때문이다. 난수도 주입받으므로 고정 시드를 주면
 * 결과가 항상 같다.
 */
public final class PerkDraft {
	/** 한 번에 제시하는 기본 후보 수. */
	public static final int DEFAULT_OPTIONS = 3;

	private PerkDraft() {
	}

	/**
	 * 후보를 최대 {@code count}개 뽑는다.
	 *
	 * <p>해당 등급에 남은 후보가 없으면 다른 등급에서 채운다. 그래도 부족하면 가능한 만큼만
	 * 돌려주고, 하나도 못 뽑으면 빈 리스트다.
	 *
	 * @param milestone 이 추첨이 속한 레벨 구간 (3, 6, …, 36)
	 * @param pool      전체 증강 목록
	 * @param owned     팀이 이미 보유한 증강과 중첩 수
	 * @param random    난수원. 고정 시드를 주면 결과가 결정론적이다
	 * @param count     뽑을 개수
	 */
	public static List<String> draw(int milestone, List<Perk> pool, List<PerkStack> owned,
			RandomSource random, int count) {
		if (pool == null || pool.isEmpty() || random == null || count <= 0) {
			return List.of();
		}

		Map<PerkRarity, List<Perk>> remaining = eligibleByRarity(pool, owned);
		int[] weights = weightsFor(milestone);

		List<String> drawn = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			Perk picked = pickOne(remaining, weights, random);
			if (picked == null) {
				break;
			}
			drawn.add(picked.id());
		}
		return List.copyOf(drawn);
	}

	/** 기본 개수(3개)로 뽑는다. */
	public static List<String> draw(int milestone, List<Perk> pool, List<PerkStack> owned,
			RandomSource random) {
		return draw(milestone, pool, owned, random, DEFAULT_OPTIONS);
	}

	/**
	 * 구간별 등급 가중치를 {@code [COMMON, RARE, EPIC]} 순으로 돌려준다.
	 *
	 * <p>3·6·9·12 → 75/25/0, 15·18·21·24 → 40/50/10, 27·30·33·36 → 15/50/35.
	 */
	public static int[] weightsFor(int milestone) {
		if (milestone <= 12) {
			return new int[] {75, 25, 0};
		}
		if (milestone <= 24) {
			return new int[] {40, 50, 10};
		}
		return new int[] {15, 50, 35};
	}

	/**
	 * 아직 더 고를 수 있는 증강만 등급별로 모은다.
	 *
	 * <p>중첩 불가({@code stackable == false}) 증강은 {@link Perk}의 규칙상 {@code maxStacks}가
	 * 1이므로 한 번 보유하면 자동으로 빠진다. 중첩 가능한 증강도 상한에 닿으면 빠진다.
	 * 풀에 같은 id가 두 번 들어 있어도 한 번만 담는다.
	 */
	private static Map<PerkRarity, List<Perk>> eligibleByRarity(List<Perk> pool, List<PerkStack> owned) {
		Map<String, Integer> stacks = ownedStacks(owned);
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
			if (!perk.canTakeMore(stacks.getOrDefault(perk.id(), 0))) {
				continue;
			}
			byRarity.get(perk.rarity()).add(perk);
		}
		return byRarity;
	}

	private static Map<String, Integer> ownedStacks(List<PerkStack> owned) {
		Map<String, Integer> stacks = new HashMap<>();
		if (owned == null) {
			return stacks;
		}
		for (PerkStack stack : owned) {
			if (stack == null || stack.perkId() == null) {
				continue;
			}
			stacks.merge(stack.perkId(), stack.count(), Integer::sum);
		}
		return stacks;
	}

	/**
	 * 한 개를 뽑고 후보 목록에서 빼낸다.
	 *
	 * <p>먼저 가중치가 있는 등급 중 후보가 남은 것들로 굴린다. 그런 등급이 하나도 없으면
	 * 남은 후보 전체에서 균등하게 하나를 집는다. 이래야 저구간에서도 3개를 최대한 채운다.
	 */
	private static Perk pickOne(Map<PerkRarity, List<Perk>> remaining, int[] weights, RandomSource random) {
		int total = 0;
		for (PerkRarity rarity : PerkRarity.values()) {
			if (!remaining.get(rarity).isEmpty()) {
				total += weights[rarity.ordinal()];
			}
		}
		if (total > 0) {
			int roll = random.nextInt(total);
			for (PerkRarity rarity : PerkRarity.values()) {
				List<Perk> bucket = remaining.get(rarity);
				if (bucket.isEmpty()) {
					continue;
				}
				roll -= weights[rarity.ordinal()];
				if (roll < 0) {
					return bucket.remove(random.nextInt(bucket.size()));
				}
			}
		}

		List<Perk> rest = new ArrayList<>();
		for (PerkRarity rarity : PerkRarity.values()) {
			rest.addAll(remaining.get(rarity));
		}
		if (rest.isEmpty()) {
			return null;
		}
		Perk fallback = rest.get(random.nextInt(rest.size()));
		remaining.get(fallback.rarity()).remove(fallback);
		return fallback;
	}
}
