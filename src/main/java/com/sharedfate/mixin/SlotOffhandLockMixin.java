package com.sharedfate.mixin;

import com.sharedfate.perk.PerkGearRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code offhand_lock} 의 화면 차단 지점.
 *
 * <p>왼손 칸은 바닐라에서 아무 제한 없는 평범한 {@link Slot} 이라, 방어구 칸과 달리
 * {@code canUseSlot} 이 지나가지 않는다. 그래서 칸 번호로 알아본다. 플레이어 인벤토리를
 * 담고 있고 칸 번호가 {@link Inventory#SLOT_OFFHAND} 인 칸은 왼손 칸뿐이다.
 *
 * <p>이 차단이 없어도 {@link com.sharedfate.perk.PerkGearManager} 가 곧 되돌려 놓지만, 그러면
 * 넣었다 튕겨 나오기를 되풀이하는 그림이 된다. 애초에 놓이지 않게 막는 편이 낫다.
 *
 * <p>{@link Slot#mayPlace} 는 자주 불리는 자리라 판정 전에 값싼 조건부터 본다. 칸 번호 비교와
 * {@code instanceof} 두 번이면 대부분의 칸이 여기서 빠져나간다.
 *
 * <p>클라이언트는 팀의 증강을 모르므로 화면에서는 잠깐 놓이는 것처럼 보일 수 있다. 서버가
 * 거절하면 바닐라의 칸 동기화가 곧바로 되돌린다.
 */
@Mixin(Slot.class)
public abstract class SlotOffhandLockMixin {

	@Inject(
			method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$lockOffhandSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Slot self = (Slot) (Object) this;
		if (self.index != Inventory.SLOT_OFFHAND
				|| !(self.container instanceof Inventory inventory)
				|| !(inventory.player instanceof ServerPlayer owner)) {
			return;
		}
		if (!PerkGearRules.mayPlaceInOffhand(owner, stack)) {
			cir.setReturnValue(false);
		}
	}
}
