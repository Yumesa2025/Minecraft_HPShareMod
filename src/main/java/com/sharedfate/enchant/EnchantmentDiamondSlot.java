package com.sharedfate.enchant;

import com.sharedfate.inventory.SelfPaintedSlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 인챈트 탁자의 <b>다이아몬드 칸</b>입니다. 청금석 칸 바로 아래 줄에 있습니다.
 *
 * <p>빈 칸 아이콘은 26.2 에 이미 들어 있는 {@code container/slot/diamond} 를 씁니다 —
 * 청금석 칸이 {@code container/slot/lapis_lazuli} 를 쓰는 것과 같은 방식이라 그림을 새로
 * 만들 필요가 없습니다. 칸의 <b>네모 바탕</b>만은 인챈트 탁자 그림에 없는 자리라
 * 화면 쪽에서 채워 넣습니다({@link SelfPaintedSlot}).
 */
public final class EnchantmentDiamondSlot extends Slot implements SelfPaintedSlot {
	private static final Identifier EMPTY_SLOT_DIAMOND =
			Identifier.withDefaultNamespace("container/slot/diamond");

	public EnchantmentDiamondSlot(Container container, int slot, int x, int y) {
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
