package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 아직 열리지 않은 추가 칸에 물건이 들어가지 못하게 막는다.
 *
 * <p>추가 칸은 언제나 {@link ExpandedInventoryManager#EXTRA_SIZE} 개가 만들어져 있고, 팀이
 * 연 만큼만 화면에 보인다. 화면 밖으로 치운 칸은 <b>눌리지는 않지만 프로그램이 고르는 대상에는
 * 그대로 남는다</b> — 쉬프트 클릭이 목적지를 고를 때 좌표를 보지 않기 때문이다. 그대로 두면
 * 물건이 안 보이는 칸으로 빨려 들어가 사라진 것처럼 보인다.
 *
 * <h2>꺼내는 것은 막지 않는다</h2>
 * <p>{@code mayPlace} 만 막고 {@code mayPickup} 은 건드리지 않는다. 잠긴 칸에 이미 물건이
 * 들어 있다면 <b>꺼낼 수 있어야</b> 한다. 실제로는 그런 칸이 열려 있도록
 * {@code PerkInventorySlots} 가 「물건이 든 마지막 칸까지는 반드시 연다」를 보장하지만,
 * 여기서도 한 겹 더 안전한 쪽을 고른다.
 *
 * <h2>{@code index} 가 아니라 {@code getContainerSlot()} 이다</h2>
 * <p>{@code Slot.index} 는 화면마다 달라지는 메뉴 순번이고, 우리가 알고 싶은 것은 추가
 * 컨테이너 안의 자리다.
 */
@Mixin(Slot.class)
public abstract class SlotExpandedLockMixin {

	@Inject(
			method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$lockUnopenedExtraSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Slot self = (Slot) (Object) this;
		if (!(self.container instanceof ExpandedInventoryContainer extra)) {
			return;
		}
		Player owner = extra.owner();
		if (owner == null) {
			return;
		}
		if (self.getContainerSlot() >= ExpandedInventoryManager.unlockedFor(owner)) {
			cir.setReturnValue(false);
		}
	}
}
