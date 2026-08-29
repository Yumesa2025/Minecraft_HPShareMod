package com.sharedfate.perk;

import com.sharedfate.perk.effect.NoFoodHungerEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * {@code no_food_hunger} 증강의 판정부.
 *
 * <p>{@link com.sharedfate.mixin.FoodPropertiesMixin} 이 먹기 처리 한가운데서 이걸 물어보고,
 * 참이면 영양 섭취만 건너뛴다. 판정 자체를 여기 떼어 둔 이유는 두 가지다. mixin 에는 판단이
 * 아니라 "어디서 막는가"만 남는 편이 읽기 쉽고, 판정을 월드 없이 시험할 수 있다.
 *
 * <p>보유 증강이 하나도 없으면 팀 상태만 두 번 보고 곧바로 거짓이다. 증강을 쓰지 않는 팀의
 * 먹기 경로에는 사실상 아무 부담도 얹히지 않는다.
 */
public final class PerkFoodRules {
	private PerkFoodRules() {
	}

	/**
	 * 이 대상이 지금 음식으로 허기를 얻지 못하는가.
	 *
	 * <p>서버의 팀원일 때만 참이 될 수 있다. 클라이언트 쪽 플레이어는 팀 상태를 볼 수 없으므로
	 * 언제나 거짓이고, 그래서 먹은 직후 한 틱 동안은 화면의 허기 막대가 잠깐 올라갔다가
	 * {@link #resyncFoodDisplay} 가 부르는 동기화로 제자리를 찾는다.
	 */
	public static boolean blocksFoodHunger(@Nullable LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return false;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return false;
		}
		return blocks(state);
	}

	/** 이 팀 상태가 {@code no_food_hunger} 를 갖고 있는가. */
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
				if (effect instanceof NoFoodHungerEffect) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 막아 놓고 나서 화면의 허기 막대를 서버 값으로 되돌린다.
	 *
	 * <p>클라이언트는 먹기를 미리 그려 두므로 허기 막대가 혼자 올라가 있다. 그런데 서버 쪽
	 * 허기는 우리가 막아서 <em>바뀌지 않았고</em>, 바닐라는 값이 달라졌을 때만 동기화 꾸러미를
	 * 보내므로 가만히 두면 틀린 막대가 다음 변화까지 그대로 남는다.
	 * {@code resetSentInfo} 로 "마지막으로 보낸 값"을 지워 두면 다음 틱에 반드시 다시 보낸다.
	 */
	public static void resyncFoodDisplay(@Nullable LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			player.resetSentInfo();
		}
	}
}
