package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 위치 교환을 한꺼번에가 아니라 팀원 한 명씩 순서대로 일어나게 한다.
 *
 * <p>정의는 {@code { "type": "staggered_swap" }} 하나뿐이고 필드가 없다. 실버 「시차」가 쓴다.
 *
 * <h2>최종 자리 배정은 그대로다</h2>
 * <p>누가 누구의 자리로 가는지는 평소와 똑같은 무고정점 순열({@code
 * PositionSwapManager.derangedDonors})을 쓴다. 이 효과가 바꾸는 것은 <b>그 이동이 한 틱
 * 안에서 전부 끝나는가, 아니면 몇 초씩 간격을 두고 한 명씩 끝나는가</b>뿐이다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link SwapBlockEffect}와 같은 이유다. {@link PerkEffect#apply}로 팀원에게 붙일 것이
 * 없다. 위치 교환 처리 한가운데서 "이 팀이 순차 이동을 쓰는가"만 물어보면 되므로, 이
 * 클래스는 그 물음에 답하기 위한 표시로만 존재한다.
 *
 * <p>실제로 순서를 나누고 진행시키는 곳은 {@link com.sharedfate.sync.StaggeredSwapManager}이고,
 * 지금 이 팀에 이 효과가 있는지 판단하는 것은 {@link com.sharedfate.perk.PerkSwapRules}다.
 */
public final class StaggeredSwapEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final StaggeredSwapEffect INSTANCE = new StaggeredSwapEffect();

	private StaggeredSwapEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
