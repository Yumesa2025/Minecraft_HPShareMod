package com.sharedfate.perk;

import java.util.List;

/**
 * 증강 한 개의 정의.
 *
 * @param id          고유 식별자. 예: {@code sharedfate:tough_body}
 * @param name        화면에 보이는 이름
 * @param description 화면에 보이는 설명
 * @param rarity      등급
 * @param stackable   여러 번 고를 수 있는지
 * @param maxStacks   중첩 상한. {@code stackable}이 false면 1
 * @param effects     이 증강이 가진 효과들
 */
public record Perk(
		String id,
		String name,
		String description,
		PerkRarity rarity,
		boolean stackable,
		int maxStacks,
		List<PerkEffect> effects) {

	public Perk {
		effects = List.copyOf(effects);
		if (!stackable) {
			maxStacks = 1;
		}
		maxStacks = Math.max(1, maxStacks);
	}

	/** 현재 보유 수량에서 더 고를 수 있는지. */
	public boolean canTakeMore(int currentStacks) {
		return currentStacks < maxStacks;
	}
}
