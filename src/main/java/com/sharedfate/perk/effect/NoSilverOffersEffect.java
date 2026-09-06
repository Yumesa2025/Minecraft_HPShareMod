package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

/**
 * 이 증강을 가진 뒤로는 증강 후보에 실버가 더 이상 나오지 않게 하는 표시.
 *
 * <p>정의는 {@code { "type": "no_silver_offers" }} 하나뿐이고 필드가 없다. 실버 「원정 준비물」이
 * 쓴다. 실버 하나를 즉시 지급형 이득으로 받는 대신 <b>남은 회차 내내 실버를 포기한다</b>는
 * 맞바꿈이므로, 실버 안에서 가장 큰 대가를 지불하는 항목이 된다.
 *
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 후보를 뽑는 한가운데서 "이 팀에
 * 실버가 막혀 있는가"만 물어본다.
 *
 * <h2>실제로 실버를 걸러 내는 곳</h2>
 * <p>{@link com.sharedfate.perk.PerkDraft}가 후보를 뽑을 때 쓰는 {@code silverBlocked} 플래그다.
 * 추첨기는 {@code PerkRegistry}에 붙지 않고 <b>호출자가 넘긴 값</b>만 보도록 되어 있어(그래야
 * 게임 없이 순수 단위 시험으로 검증할 수 있다), 팀 상태를 읽어 그 플래그를 만드는 일은 추첨을
 * 시작하는 쪽의 몫이다. 그 한 줄이 {@link #heldBy(TeamState)}다.
 */
public final class NoSilverOffersEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoSilverOffersEffect INSTANCE = new NoSilverOffersEffect();

	private NoSilverOffersEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}

	/**
	 * 이 팀이 이 효과를 가졌는가. 가졌으면 다음 추첨부터 실버가 후보에서 빠진다.
	 *
	 * <p>팀이 없거나, 증강을 껐거나, 아직 아무 증강도 없으면 두 번만 보고 곧바로 거짓이다.
	 * 풀에서 사라진 id 는 건너뛴다 — 정의를 손으로 고칠 수 있는 이상 저장에만 남은 id 는
	 * 언제든 생기고, 정의가 없는 증강은 아무 효과도 없는 것으로 본다.
	 */
	public static boolean heldBy(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return false;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoSilverOffersEffect) {
					return true;
				}
			}
		}
		return false;
	}
}
