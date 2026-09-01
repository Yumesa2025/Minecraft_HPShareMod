package com.sharedfate.enchant;

import net.minecraft.world.Container;

/**
 * 인챈트 메뉴가 <b>제 다이아몬드 칸</b>을 알려 주는 통로입니다.
 *
 * <p>{@code EnchantmentMenu} 에 Mixin 으로 구현을 얹습니다. 화면·검사·차감이 모두 이
 * 통로 하나만 보므로, 칸을 옮기거나 개수를 늘려도 고칠 곳이 한 군데입니다.
 */
public interface EnchantmentDiamondAccess {
	int NO_SLOT = -1;

	/** 다이아몬드 칸 하나짜리 그릇. 아직 만들어지지 않았으면 {@code null}. */
	default Container sharedfate$diamondContainer() {
		return null;
	}

	/** 다이아몬드 칸의 메뉴 번호. 없으면 {@link #NO_SLOT}. */
	default int sharedfate$diamondMenuSlot() {
		return NO_SLOT;
	}
}
