package com.sharedfate.perk;

import java.util.ArrayList;
import java.util.List;

/**
 * 증강 발동 구간 계산.
 *
 * <p>팀 공유 레벨이 3의 배수에 <b>처음</b> 도달할 때마다 증강 선택권이 하나 생긴다.
 * 구간은 3, 6, 9, …, 36 열두 개뿐이고 36을 넘으면 더 발동하지 않는다.
 *
 * <p>재발동 여부는 현재 레벨이 아니라 {@code lastMilestone}으로 판단한다. 경험치를 써서
 * 레벨이 내려갔다가 다시 올라와도 이미 처리한 구간은 다시 나오지 않는다.
 */
public final class PerkMilestones {
	/** 구간 간격. */
	public static final int STEP = 3;
	/** 마지막 구간. 이 위로는 발동하지 않는다. */
	public static final int MAX = 36;
	/** 전체 구간 수. */
	public static final int COUNT = MAX / STEP;

	private PerkMilestones() {
	}

	/**
	 * {@code lastMilestone} 이후로 {@code currentLevel}이 도달한 구간들을 오름차순으로 돌려준다.
	 *
	 * <p>한 번에 여러 구간을 건너뛰었다면(예: 2렙 → 9렙) 건너뛴 구간을 모두 담는다.
	 * 새로 도달한 구간이 없으면 빈 리스트다.
	 *
	 * @param lastMilestone 마지막으로 처리한 구간. 아직 없으면 0
	 * @param currentLevel  현재 팀 공유 레벨
	 */
	public static List<Integer> newlyReached(int lastMilestone, int currentLevel) {
		int handled = clampMilestone(lastMilestone);
		int reachable = Math.min(currentLevel, MAX);
		if (reachable < STEP || reachable <= handled) {
			return List.of();
		}
		List<Integer> reached = new ArrayList<>();
		for (int milestone = handled + STEP; milestone <= reachable; milestone += STEP) {
			reached.add(milestone);
		}
		return List.copyOf(reached);
	}

	/** 저장값이 손상됐을 때를 대비해 0 이상 MAX 이하의 3의 배수로 맞춘다. */
	public static int clampMilestone(int milestone) {
		if (milestone <= 0) {
			return 0;
		}
		int bounded = Math.min(milestone, MAX);
		return bounded - (bounded % STEP);
	}

	/** 3, 6, …, 36 중 하나인지. */
	public static boolean isMilestone(int level) {
		return level >= STEP && level <= MAX && level % STEP == 0;
	}
}
