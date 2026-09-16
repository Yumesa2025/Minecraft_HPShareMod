package com.sharedfate.mixin;

import com.sharedfate.storage.TeamStorage;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 창고 칸에는 <b>넣을 수 없다.</b> 꺼내기만 된다.
 *
 * <p>창고는 인벤토리가 꽉 차 못 받은 물건이 쌓이는 곳이지 <b>보관하는 곳이 아니다.</b>
 * 넣게 두면 「인벤토리가 두 개」가 되어 공유 인벤토리의 뜻이 흐려진다.
 *
 * <h2>왜 여기에는 믹스인이 먹히는가</h2>
 *
 * <p>추가 27칸에서는 같은 방식이 죽어 있었다 — {@code ExpandedInventorySlot} 이
 * {@code mayPlace} 를 <b>재정의</b>해서 이 주입이 한 번도 실행되지 않았다. 창고는 바닐라
 * {@code ChestMenu} 가 만드는 <b>평범한 {@code Slot}</b> 을 쓰므로 그 문제가 없다.
 *
 * <p>그 대신 이 믹스인은 <b>창고 화면을 바닐라 상자로 여는 한에서만</b> 유효하다. 나중에
 * 전용 메뉴를 만들어 슬롯을 갈아 끼우게 되면 여기도 함께 손봐야 한다.
 *
 * <h2>쉬프트 클릭도 함께 막힌다</h2>
 * <p>{@code AbstractContainerMenu.moveItemStackTo} 가 빈 칸을 고를 때 {@code mayPlace} 를
 * 보므로, 손으로 옮기는 길과 쉬프트 클릭이 한 곳에서 같이 막힌다.
 */
@Mixin(Slot.class)
public abstract class SlotTeamStorageMixin {

	@Inject(
			method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$storageIsTakeOnly(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (((Slot) (Object) this).container instanceof TeamStorage.Container) {
			cir.setReturnValue(false);
		}
	}
}
