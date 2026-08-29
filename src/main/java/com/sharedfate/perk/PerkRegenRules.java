package com.sharedfate.perk;

import com.sharedfate.perk.effect.NoNaturalRegenEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * {@code no_natural_regen} 증강의 판정부.
 *
 * <p>{@link com.sharedfate.mixin.FoodDataNaturalRegenMixin} 이 자연 회복 처리 한가운데서 이걸
 * 물어보고, 참이면 회복 갈래를 통째로 건너뛴다. 판정 자체를 여기 떼어 둔 이유는
 * {@link PerkFoodRules} 와 같다. mixin 에는 "어디서 막는가"만 남는 편이 읽기 쉽고, 판정을
 * 월드 없이 시험할 수 있다.
 *
 * <p>보유 증강이 하나도 없으면 팀 상태만 두 번 보고 곧바로 거짓이다. 증강을 쓰지 않는 팀의
 * 자연 회복에는 사실상 아무 부담도 얹히지 않고, 팀에 속하지 않은 플레이어는 언제나 거짓이라
 * 바닐라와 완전히 같다.
 */
public final class PerkRegenRules {
	private PerkRegenRules() {
	}

	/** 이 대상이 지금 허기에 의한 자연 회복을 얻지 못하는가. */
	public static boolean blocksNaturalRegen(@Nullable LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return false;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return false;
		}
		return blocks(state);
	}

	/** 이 팀 상태가 {@code no_natural_regen} 을 갖고 있는가. */
	public static boolean blocks(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return false;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoNaturalRegenEffect) {
					return true;
				}
			}
		}
		return false;
	}
}
