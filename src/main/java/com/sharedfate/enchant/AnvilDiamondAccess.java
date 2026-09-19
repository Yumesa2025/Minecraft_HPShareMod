package com.sharedfate.enchant;

import net.minecraft.world.Container;

/**
 * 모루 메뉴가 <b>제 다이아몬드 칸</b>을 알려 주는 통로다.
 *
 * <p>{@code AnvilMenu} 에 Mixin 으로 구현을 얹는다. {@link EnchantmentDiamondAccess} 와
 * 같은 모양이다 — 화면·검사·차감이 모두 이 통로 하나만 본다.
 */
public interface AnvilDiamondAccess {
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
