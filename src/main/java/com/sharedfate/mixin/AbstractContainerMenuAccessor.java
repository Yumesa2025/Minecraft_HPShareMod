package com.sharedfate.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
	@Invoker("addSlot")
	Slot sharedfate$invokeAddSlot(Slot slot);

	/**
	 * 데이터 칸을 하나 더 답니다.
	 *
	 * <p>메뉴가 값 하나를 클라이언트로 계속 내려보내는 통로입니다. 붙이는 <b>순서와 개수가
	 * 양쪽에서 같아야</b> 하므로, 서버에만 붙이거나 조건에 따라 붙이면 안 됩니다.
	 */
	@Invoker("addDataSlot")
	DataSlot sharedfate$invokeAddDataSlot(DataSlot dataSlot);
}
