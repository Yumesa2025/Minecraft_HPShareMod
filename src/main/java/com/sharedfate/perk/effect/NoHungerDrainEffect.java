package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 허기가 아예 줄지 않게 한다.
 *
 * <p>정의는 {@code { "type": "no_hunger_drain" }} 하나뿐이고 필드가 없다. 프리즘 1 고행자가
 * 최대 체력 10 고정의 대가로 이 타입을 쓴다.
 *
 * <p>{@link HungerDrainEffect} 와 같은 자리에서 같은 방식으로 동작한다. 소모도가 쌓이는 입구인
 * {@code Player.causeFoodExhaustion} 에서 배율을 0 으로 만들 뿐이다. 배율 0 으로 적어도 결과는
 * 같지만, "떨어지지 않는다"는 정의에 숫자를 쓰게 하면 오타 하나로 조용히 되살아나므로 뜻이
 * 분명한 타입을 따로 뒀다.
 *
 * <h2>자연 회복의 대가는 면제되지 않는다</h2>
 * <p>이 효과가 면제해 주는 것은 <b>플레이어의 행동</b>이 치르는 소모도다. 달리기·점프·수영·
 * 채굴·공격, 그리고 같은 통로를 쓰는 허기 상태이상이 여기에 든다.
 *
 * <p>자연 회복은 다르다. 마인크래프트는 체력을 돌려주는 대가로 그 자리에서 소모도를 치르게
 * 하는데, 그 대가까지 0 이 되면 체력이 공짜로 무한히 차오른다. 그래서 회복의 대가는 그대로
 * 통과시킨다. 26.2 의 {@code FoodData.tick} 은 회복 갈래에서 {@code causeFoodExhaustion} 이
 * 아니라 {@code FoodData.addExhaustion} 을 직접 부르므로 배율이 걸리는 자리를 애초에 지나가지
 * 않고, 그 갈림을 통로에 기대지 않고 못 박아 두는 것이
 * {@link com.sharedfate.mixin.FoodDataRegenExhaustionMixin} 이다.
 *
 * <p>그래서 이 증강을 든 팀도 체력이 자연 회복되는 동안에는 배가 준다. 설명이 "움직임으로는
 * 허기가 떨어지지 않는다"라고 적혀 있는 이유다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 소모도가 쌓이는 한가운데서 "이 팀이
 * 이 효과를 갖고 있는가"만 물어보면 되므로, 이 클래스는 그 물음에 답하기 위한 표시로만
 * 존재한다. {@link NoFoodHungerEffect} 와 같은 구도다. 상태가 없어 인스턴스를 나눠 써도 안전하다.
 */
public final class NoHungerDrainEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoHungerDrainEffect INSTANCE = new NoHungerDrainEffect();

	private NoHungerDrainEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
