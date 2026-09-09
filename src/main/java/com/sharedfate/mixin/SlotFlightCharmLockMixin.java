package com.sharedfate.mixin;

import com.sharedfate.perk.PerkFlightCharm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code flight_charm} 의 핫바 1번 칸 잠금 지점.
 *
 * <p>비행 부적이 놓인 칸은 <b>꺼낼 수도 옮길 수도 버릴 수도 없어야</b> 한다. 26.2 의
 * {@code AbstractContainerMenu.doClick} 은 칸을 건드리는 모든 길에서 아래 둘 중 하나를 반드시
 * 지나므로, 두 곳만 막으면 화면에서 하는 조작이 전부 막힌다.
 *
 * <ul>
 *   <li>{@link Slot#mayPickup(Player)} — 집기({@code PICKUP})·쉬프트 옮기기({@code QUICK_MOVE})·
 *       숫자키 교환({@code SWAP})·같은 것 모으기({@code PICKUP_ALL})가 직접 부르고,
 *       화면을 열어 둔 채 누르는 Q({@code THROW})는 {@code Slot.safeTake} →
 *       {@code Slot.tryRemove} 를 거쳐 부른다. 바이트코드로 확인했다.</li>
 *   <li>{@link Slot#mayPlace(ItemStack)} — 들고 있던 것을 그 칸에 놓거나 바꿔치기하는 길,
 *       그리고 다른 칸에서 쉬프트 옮기기로 흘러드는 길({@code moveItemStackTo})이 부른다.
 *       부적은 개수가 하나뿐이라 놓기를 막지 않아도 웬만해선 겹치지 않지만, 같은 팬텀 막을
 *       그 칸에 합쳐 넣는 길이 열려 있으면 부적을 「합쳐서」 꺼낼 수 있다.</li>
 * </ul>
 *
 * <h2>{@code index} 가 아니라 {@code getContainerSlot()} 이다</h2>
 * <p>{@link Slot#index} 는 생성자가 채우지 않고 {@code AbstractContainerMenu.addSlot} 이
 * <b>메뉴 안의 순번</b>으로 덮어쓰는 칸이다(바이트코드에서 {@code slots.size()} 를 그대로
 * 넣는다). 그래서 상자를 열었는지 화로를 열었는지에 따라 값이 달라진다. 반대로
 * {@code getContainerSlot()} 은 생성자가 받은 <b>인벤토리 안의 칸 번호</b>라 어느 화면에서도
 * 같다. 핫바 맨 왼쪽은 언제나 0 번이다.
 *
 * <h2>Q 키는 여기서 다 막히지 않는다</h2>
 * <p>화면을 <b>닫은 채</b> 누르는 Q 는 칸을 거치지 않는다. 26.2 의
 * {@code ServerGamePacketListenerImpl.handlePlayerAction} 이
 * {@code ServerPlayer.drop(boolean)} 을 부르고, 그것이
 * {@code Inventory.removeFromSelected(boolean)} 로 곧장 묶음을 뽑아 간다. 그 길은
 * {@code com.sharedfate.perk.PerkFlightCharm} 의 주기 점검이 부적을 제자리에 되돌려 막는다.
 *
 * <h2>판정은 여기에 두지 않는다</h2>
 * <p>{@link SlotOffhandLockMixin} 과 같은 규칙이다. mixin 은 「어디서 막는가」만 갖고
 * 「막아야 하는가」는 {@link PerkFlightCharm#isLockedSlot} 에 물어본다. 그쪽이 싼 조건부터
 * 보므로 관계없는 칸은 비교 두 번으로 빠져나간다.
 */
@Mixin(Slot.class)
public abstract class SlotFlightCharmLockMixin {

	@Inject(
			method = "mayPickup(Lnet/minecraft/world/entity/player/Player;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$lockFlightCharmPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (PerkFlightCharm.isLockedSlot((Slot) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(
			method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$lockFlightCharmPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (PerkFlightCharm.isLockedSlot((Slot) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
