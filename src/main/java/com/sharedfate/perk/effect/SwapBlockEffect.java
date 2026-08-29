package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 팀원 위치 교환이 일어나도 실제로 자리를 바꾸지 않게 한다.
 *
 * <p>정의는 {@code { "type": "swap_block" }} 하나뿐이고 필드가 없다. 골드 「뿌리내린 발」이
 * {@code on_swap} 과 짝지어 쓴다.
 *
 * <h2>막는 것은 순간이동뿐이다</h2>
 * <p>주기 타이머는 그대로 돌고, 카운트다운도 그대로 나오고, 같은 시점에 걸리는
 * {@link OnSwapEffect} 도 그대로 발동한다. 막히는 것은 <b>자리를 바꾸는 한 자리</b>뿐이다.
 * 「뿌리내린 발」의 "원래 바뀔 시점마다 실명과 구속"이라는 대가가 성립하려면 그 시점이
 * 계속 찾아와야 하기 때문이다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 교환 처리 한가운데서 "이 팀이 이
 * 효과를 갖고 있는가"만 물어보면 되므로, 이 클래스는 그 물음에 답하기 위한 표시로만 존재한다.
 * 상태가 없어 인스턴스를 나눠 써도 안전하다. {@link NoFoodHungerEffect} 와 같은 꼴이다.
 *
 * <p>실제로 막는 지점은 {@link com.sharedfate.sync.PositionSwapManager} 이고, 지금 이 팀에 이
 * 효과가 있는지 판단하는 것은 {@link com.sharedfate.perk.PerkSwapRules} 다.
 */
public final class SwapBlockEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final SwapBlockEffect INSTANCE = new SwapBlockEffect();

	private SwapBlockEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
