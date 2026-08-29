package com.sharedfate.perk;

import java.util.List;

/**
 * 증강 한 개의 정의.
 *
 * <p>중첩 개념은 없다. 한 번 고른 증강은 그 회차 동안 다시 후보로 나오지 않는다.
 * 예전 정의 파일에 남아 있는 {@code stackable}·{@code maxStacks} 는 조용히 무시한다.
 *
 * @param id          고유 식별자. 예: {@code sharedfate:tough_body}
 * @param name        화면에 보이는 이름
 * @param description 화면에 보이는 설명
 * @param rarity      등급
 * @param effects     이 증강이 가진 효과들
 */
public record Perk(
		String id,
		String name,
		String description,
		PerkRarity rarity,
		List<PerkEffect> effects) {

	public Perk {
		effects = List.copyOf(effects);
	}
}
