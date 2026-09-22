package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 검의 <b>휩쓸기 피해</b>만 같은 팀 팀원에게 들어가지 않게 하는 표시.
 *
 * <p>정의는 {@code { "type": "no_sweep_friendly_fire" }} 하나뿐이고 필드가 없다. 실버
 * 「휩쓸기」가 쓴다. 휩쓸기 배율을 1.0 올려 놓고 나면 좁은 굴이나 보스방에서 옆에 선 팀원이
 * 덩달아 맞는 일이 잦아지는데, 그 대가를 없애 주는 것이 이 표시다.
 *
 * <h2>「휩쓸기 피해만」이다</h2>
 * <p>팀원을 <b>겨냥해서</b> 때리는 것은 그대로 아프다. 막는 것은 몹을 때렸을 때 옆으로 퍼지는
 * 몫뿐이다. 그래서 피해원(DamageSource)으로는 가릴 수 없다 — 바닐라
 * {@code Player.attack} 은 겨냥한 대상에게 쓴 피해원 <b>그 객체를 그대로</b>
 * {@code doSweepAttack} 에 넘긴다. 둘은 같은 {@code DamageSource} 라서 구분할 수 있는 정보가
 * 애초에 없다. 가를 수 있는 곳은 휩쓸기 고리 안뿐이고, 그래서 판정은
 * {@code PlayerSweepFriendlyFireMixin} 이 그 고리 한가운데서 부른다.
 *
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 휩쓸기 배율 자체는 같은 증강의
 * {@code attribute} 효과가 맡는다. 이 효과는 "이 팀은 휩쓸기로 서로를 때리지 않는다"만
 * 알려 준다.
 */
public final class NoSweepFriendlyFireEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoSweepFriendlyFireEffect INSTANCE = new NoSweepFriendlyFireEffect();

	private NoSweepFriendlyFireEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}

	/**
	 * 이 팀이 이 효과를 가졌는가.
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
				if (effect instanceof NoSweepFriendlyFireEffect) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 지금 휩쓸기에 걸린 이 대상에게 피해를 넣지 말아야 하는가.
	 *
	 * <p>인자는 모두 <b>공격자</b> 쪽 값이다. 공격자의 팀과 공격자의 팀 상태를 보고, 맞을
	 * 사람이 그 팀에 들어 있는지만 묻는다. 살아 있는 서버가 필요 없어 그대로 단위 시험에
	 * 올릴 수 있다 — 서버에서 팀과 상태를 꺼내 오는 일은 믹스인 쪽이 한다.
	 *
	 * <p>팀원인지를 <b>먼저</b> 본다. 휩쓸기에 걸리는 대상은 거의 언제나 몹이고, 그때는
	 * 증강 목록을 훑을 것도 없이 한 번의 목록 조회로 끝난다.
	 *
	 * @param team     공격자가 속한 팀. 팀이 없으면 {@code null}
	 * @param state    공격자 팀의 상태. 효과 보유 판정은 이것으로 한다
	 * @param victimId 휩쓸기에 걸린 대상의 UUID
	 */
	public static boolean blocksSweep(@Nullable ShareTeam team, @Nullable TeamState state,
			@Nullable UUID victimId) {
		if (team == null || victimId == null || !team.contains(victimId)) {
			return false;
		}
		return heldBy(state);
	}
}
