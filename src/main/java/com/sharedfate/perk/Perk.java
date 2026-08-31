package com.sharedfate.perk;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

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
 * @param icon        선택 화면 카드에 그릴 아이템. 없으면 {@code null} 이고, 그때는
 *                    클라이언트가 등급별 기본 아이콘을 대신 쓴다
 * @param minLevel    이 증강이 후보로 나올 수 있는 최소 팀 공유 레벨({@code state.xpLevel}).
 *                    0(기본값)이면 제한이 없다. {@link PerkDraft} 의 추첨이 이 값을
 *                    구간과 비교해 거른다 — "특정 구간에서만 나오는 증강"이 필요할 때 이
 *                    필드 하나로 표현한다. 예: 프리즘 「환골탈태」는 30(15렙에는 안 나옴)
 * @param effects     이 증강이 가진 효과들
 */
public record Perk(
		String id,
		String name,
		String description,
		PerkRarity rarity,
		@Nullable Identifier icon,
		int minLevel,
		List<PerkEffect> effects) {

	public Perk {
		effects = List.copyOf(effects);
	}

	/** 아이콘·최소 레벨을 따로 정하지 않은 증강. 화면은 등급별 기본 아이콘을 쓰고 언제나 나올 수 있다. */
	public Perk(String id, String name, String description, PerkRarity rarity,
			List<PerkEffect> effects) {
		this(id, name, description, rarity, null, 0, effects);
	}

	/** 최소 레벨은 따로 정하지 않고 아이콘만 지정한다. {@link PerkRegistry} 가 예전부터 쓰던 자리다. */
	public Perk(String id, String name, String description, PerkRarity rarity,
			@Nullable Identifier icon, List<PerkEffect> effects) {
		this(id, name, description, rarity, icon, 0, effects);
	}
}
