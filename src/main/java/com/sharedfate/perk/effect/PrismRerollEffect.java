package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

/**
 * 다시 뽑으면 <b>프리즘 등급만</b> 나오게 하는 표시. 세트 「도박 3단계 — 어차피 프리즘」이 쓴다.
 *
 * <p>정의는 {@code { "type": "prism_reroll" }} 하나뿐이고 필드가 없다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link NoSilverOffersEffect} 와 같은 이유다. {@link PerkEffect#apply} 로 팀원에게 붙일 것이
 * 없다. 다시 뽑기 한가운데서 「이 팀은 프리즘만 보는가」만 물어보면 되므로, 이 클래스는 그 물음에
 * 답하기 위한 표시로만 존재한다. 「효과를 붙인다」가 아니라 「규칙을 바꾼다」인 보상은 전부 이
 * 모양이 된다.
 *
 * <h2>실제로 등급을 바꾸는 곳</h2>
 * <p>{@code PerkManager.applyReroll} 이다. 그 자리는 원래 {@code offerRarity(offer)} 로 「다시
 * 뽑아도 등급은 그대로」를 하드코딩하고 있었는데, 이 표시를 가진 팀에서만 {@code PerkRarity.PRISM}
 * 으로 바꾼다. 판정을 {@code PerkDraft} 가 아니라 {@code PerkManager} 에서 하는 까닭도
 * {@code no_silver_offers} 와 같다 — 추첨기가 {@code PerkRegistry} 에 손을 뻗는 순간 게임을
 * 띄우지 않고는 확률표를 검증할 수 없게 된다.
 *
 * <h2>프리즘이 모자라면 「적게」다 — 골드를 섞지 않는다</h2>
 * <p>{@code PerkDraft.fallbackOrder(PRISM)} 는 프리즘 → 골드 → 실버 순이라, 아직 안 가진 프리즘이
 * 3장 미만이면 골드가 섞여 들어온다. 「프리즘만 나옵니다」라고 적어 놓고 골드를 보여 주면 그건
 * 설명이 거짓이 되는 것이라, {@code applyReroll} 이 뽑아 온 후보에서 <b>프리즘이 아닌 것을
 * 걸러낸다.</b> 남은 프리즘이 두 장이면 카드도 두 장이고, 한 장도 없으면 다시 뽑기 자체가
 * 실패한다(그때는 <b>횟수도 깎지 않는다</b> — 등급 통이 비었을 때의 기존 처리와 같다).
 *
 * <p>카드가 세 장 미만으로 뜨는 것 자체는 이미 있는 일이다. 일반 추첨도 「그래도 부족하면
 * 가능한 만큼만 돌려준다」로 되어 있다.
 */
public final class PrismRerollEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final PrismRerollEffect INSTANCE = new PrismRerollEffect();

	private PrismRerollEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}

	/**
	 * 이 팀이 이 효과를 가졌는가. 가졌으면 다음 다시 뽑기부터 프리즘만 나온다.
	 *
	 * <p>팀이 없거나 증강을 껐으면 곧바로 거짓이다. 보유 증강과 켜진 세트를 <b>둘 다</b> 본다 —
	 * 지금 이 형을 태우는 것은 세트뿐이지만, 훑는 자리가 한쪽만 보면 나중에 증강에 붙였을 때
	 * 빌드도 통과하고 로그도 없이 무동작이 된다.
	 */
	public static boolean heldBy(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled) {
			return false;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof PrismRerollEffect) {
					return true;
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof PrismRerollEffect) {
				return true;
			}
		}
		return false;
	}
}
