package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 고른 즉시 등급 상관없이 무작위 증강 2개를 더 얻게 한다.
 *
 * <p>정의는 {@code { "type": "gambler" }} 하나뿐이고 필드가 없다. 프리즘 「도박꾼」이 쓴다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link SwapBlockEffect}·{@link com.sharedfate.perk.effect.LegacyGearEffect}(의 존재 자체)와
 * 같은 이유다. {@link PerkEffect#apply}로 팀원에게 붙일 것이 없다. "이 증강을 고르는 순간
 * 2개를 더 준다"는 사건 하나만 있으면 되므로, 이 클래스는 그 사건이 있었는지 표시하는
 * 용도로만 존재한다. 상태가 없어 인스턴스를 나눠 써도 안전하다.
 *
 * <h2>실제로 뽑고 주는 곳</h2>
 * <p>{@link com.sharedfate.perk.PerkManager#applyChoice}가 부르는
 * {@link com.sharedfate.perk.PerkGambler#grantOnChoice} 한 곳에서, 증강을 고른 그 순간
 * 딱 한 번 일어난다. {@code item_grant}·{@code legacy_gear}와 같은 시점, 같은 이유다.
 *
 * <h2>20·25 구간 실버 고정과의 관계</h2>
 * <p>이 클래스는 그 규칙 자체를 모른다. "이 팀이 도박꾼을 가졌는가"는
 * {@link com.sharedfate.perk.PerkGambler#forcedRarity}가 {@code state.ownedPerks}를 직접
 * 훑어 판단하고, {@code PerkManager.advanceMilestones}가 그 답에 따라 뽑을 등급을 바꾼다.
 */
public final class GamblerEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final GamblerEffect INSTANCE = new GamblerEffect();

	private GamblerEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
