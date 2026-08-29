package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 음식을 먹어도 허기와 포만감이 오르지 않게 한다.
 *
 * <p>정의는 {@code { "type": "no_food_hunger" }} 하나뿐이고 필드가 없다. 프리즘 13 포식이
 * {@code on_kill} 과 짝지어 쓰는 대가다.
 *
 * <h2>먹기의 나머지는 그대로다</h2>
 * <p>막는 것은 <b>영양 섭취 한 자리</b>뿐이다. 아이템은 그대로 줄어들고, 먹는 소리와 트림 소리도
 * 나고, 수상한 스튜의 상태이상이나 우유의 해독 같은 {@code consume_effects} 도 모두 그대로
 * 일어난다. 실제로 막는 지점은 {@link com.sharedfate.mixin.FoodPropertiesMixin} 이고,
 * 지금 이 팀에 이 효과가 있는지 판단하는 것은 {@link com.sharedfate.perk.PerkFoodRules} 다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 먹기 처리 한가운데서 "이 팀이 이
 * 효과를 갖고 있는가"만 물어보면 되므로, 이 클래스는 그 물음에 답하기 위한 표시로만 존재한다.
 * 상태가 없어 인스턴스를 나눠 써도 안전하다.
 */
public final class NoFoodHungerEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoFoodHungerEffect INSTANCE = new NoFoodHungerEffect();

	private NoFoodHungerEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
