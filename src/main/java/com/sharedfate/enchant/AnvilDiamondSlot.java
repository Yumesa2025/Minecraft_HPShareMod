package com.sharedfate.enchant;

import com.sharedfate.inventory.SelfPaintedSlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 모루의 <b>다이아몬드 칸</b>이다. 왼쪽 재료 칸(27,47) 바로 아래 줄에 있다.
 *
 * <p>{@link EnchantmentDiamondSlot} 과 같은 모양이다 — 빈 칸 아이콘도 같은
 * {@code container/slot/diamond} 를 쓰고, 네모 바탕도 마찬가지로 화면 쪽에서 채워 넣는다
 * ({@link SelfPaintedSlot}).
 */
public final class AnvilDiamondSlot extends Slot implements SelfPaintedSlot {
	private static final Identifier EMPTY_SLOT_DIAMOND =
			Identifier.withDefaultNamespace("container/slot/diamond");

	public AnvilDiamondSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return stack.is(Items.DIAMOND);
	}

	@Override
	public Identifier getNoItemIcon() {
		return EMPTY_SLOT_DIAMOND;
	}
}
