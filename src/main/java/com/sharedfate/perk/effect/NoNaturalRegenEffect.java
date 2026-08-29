package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 허기가 가득 차 있어도 체력이 자연 회복되지 않게 한다.
 *
 * <p>정의는 {@code { "type": "no_natural_regen" }} 하나뿐이고 필드가 없다. 프리즘 11 흡혈귀가
 * {@code lifesteal} 과 짝지어 쓰는 대가다.
 *
 * <h2>막는 것과 막지 않는 것</h2>
 * <p>막는 것은 {@code FoodData.tick} 의 <b>자연 회복 두 갈래</b>뿐이다. 포만감이 남아 있을 때의
 * 빠른 회복(10틱마다)과 허기 18 이상일 때의 느린 회복(80틱마다)이 그것이다. 그 밖의 회복은
 * 전부 그대로다. 재생 상태이상, 금사과, {@code /heal}, 평화 난이도의 회복, 처치 보상
 * ({@code on_kill})과 흡혈({@code lifesteal})은 아무 영향을 받지 않는다.
 *
 * <p>굶주림도 그대로다. 허기가 0 이 되면 여전히 굶어 죽는다. 실제로 막는 지점은
 * {@link com.sharedfate.mixin.FoodDataNaturalRegenMixin} 이고, 지금 이 팀이 이 효과를 갖고
 * 있는지 판단하는 것은 {@link com.sharedfate.perk.PerkRegenRules} 다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 자연 회복 처리 한가운데서 "이 팀이
 * 이 효과를 갖고 있는가"만 물어보면 되므로, 이 클래스는 그 물음에 답하기 위한 표시로만
 * 존재한다. {@link NoFoodHungerEffect} 와 같은 구도이고, 상태가 없어 인스턴스를 나눠 써도
 * 안전하다.
 */
public final class NoNaturalRegenEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoNaturalRegenEffect INSTANCE = new NoNaturalRegenEffect();

	private NoNaturalRegenEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
