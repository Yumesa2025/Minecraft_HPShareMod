package com.sharedfate.perk;

import net.minecraft.server.level.ServerPlayer;

/**
 * 증강 하나가 가진 효과의 원시 단위.
 *
 * <p>효과는 두 가지 방식으로 동작한다. 속성이나 상태이상처럼 플레이어에게 붙였다 떼는 것은
 * {@link #apply}/{@link #remove}로 처리하고, 피해 배율처럼 그때그때 계산에 끼어드는 것은
 * 배율 조회 메서드로 처리한다. 구현체는 자신에게 해당하는 쪽만 재정의하면 된다.
 *
 * <p>{@code stacks}는 팀이 이 증강을 몇 개 쌓았는지다. 중첩 불가 증강은 항상 1이다.
 */
public interface PerkEffect {
	/** 팀원에게 효과를 적용한다. 이미 적용돼 있으면 stacks 기준으로 다시 맞춘다. */
	default void apply(ServerPlayer player, int stacks) {
	}

	/** 팀원에게서 효과를 걷어낸다. */
	default void remove(ServerPlayer player) {
	}

	/** 이 효과가 주는 피해에 곱할 배율. 해당 없으면 1.0. */
	default double damageDealtMultiplier(int stacks) {
		return 1.0;
	}

	/** 이 효과가 받는 피해에 곱할 배율. 해당 없으면 1.0. */
	default double damageTakenMultiplier(int stacks) {
		return 1.0;
	}
}
