package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

/**
 * 물속에서 산소 막대가 줄지 않게 하는 표시. 「아가미」가 쓴다.
 *
 * <p>정의는 {@code { "type": "no_air_loss" }} 하나뿐이고 필드가 없다.
 *
 * <h2>왜 물속 호흡 상태이상을 쓰지 않는가</h2>
 * <p>{@code status_effect} 로 {@code minecraft:water_breathing} 을 붙이면 세 가지가 딸려 온다.
 * 상태이상 아이콘이 화면에 남고, 몸에서 입자가 피어오르고, 이 모드는 상태이상을 팀 전체가
 * 나눠 가지므로 <b>물에 들어가지도 않은 팀원 전원</b>에게 함께 걸린다. 원하는 것은 그 셋이
 * 아니라 "산소가 닳지 않는다" 하나뿐이라, 상태이상을 통째로 건너뛰고 산소가 줄어드는 자리
 * 한 곳만 막는다.
 *
 * <p>그래서 {@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 산소를 한 칸 깎으려는
 * 한가운데서 "이 사람의 팀이 이걸 가졌는가"만 물어본다.
 *
 * <h2>실제로 산소를 지키는 곳</h2>
 * <p>{@link com.sharedfate.mixin.LivingEntityAirSupplyMixin} 이다. 26.3 의
 * {@code LivingEntity.decreaseAirSupply(int)} 는 받은 산소량에서 1 을 뺀 값을 돌려주는데,
 * 그 믹스인이 <b>받은 값을 그대로</b> 돌려주게 한다. 바닐라가 숨 참기(OXYGEN_BONUS) 속성이
 * 걸렸을 때 쓰는 갈래와 완전히 같은 모양이라, 물 밖 회복·거품 기둥·익사 판정은 하나도
 * 건드리지 않는다.
 */
public final class NoAirLossEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoAirLossEffect INSTANCE = new NoAirLossEffect();

	private NoAirLossEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}

	/**
	 * 이 팀이 이 효과를 가졌는가. 가졌으면 팀원의 산소가 물속에서 줄지 않는다.
	 *
	 * <p>팀이 없거나, 증강을 껐거나, 아직 아무 증강도 없으면 두 번만 보고 곧바로 거짓이다.
	 * 산소 감소는 물에 눈이 잠긴 동안 매 틱 지나는 자리라 이 이른 탈출이 특히 중요하다.
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
				if (effect instanceof NoAirLossEffect) {
					return true;
				}
			}
		}
		return false;
	}
}
