package com.sharedfate.mixin;

import com.sharedfate.perk.PerkFlightCharm;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 손에 든 것을 그냥 버리는 길을 막는다. 프리즘 「비행 부적」이 핫바 맨 왼쪽 칸을 점유하는
 * 대가를 지키기 위한 것이다.
 *
 * <h2>왜 슬롯 잠금만으로는 모자란가</h2>
 * <p>{@code SlotFlightCharmLockMixin} 은 {@code Slot.mayPickup}/{@code mayPlace} 를 물어 <b>화면을
 * 열고 하는 조작</b>(끌기·쉬프트 클릭·숫자 키 교환·창 안에서의 버리기)을 전부 막는다. 그런데
 * 인벤토리를 열지 않고 버리는 길은 {@code Slot} 을 아예 지나지 않는다 —
 * {@code ServerGamePacketListenerImpl.handlePlayerAction} 이 {@code DROP_ITEM}/{@code DROP_ALL_ITEMS}
 * 를 받으면 {@code Player.drop(boolean)} 을 부르고, 그것이 {@code Inventory.removeFromSelected} 로
 * 곧장 묶음을 뽑아 간다. 그래서 그 진입점을 따로 막는다.
 *
 * <h2>키 설정과 무관하다</h2>
 * <p>버리기 키를 Q 에서 다른 것으로 바꿔도 서버에 오는 패킷은 같다. 서버는 어느 키를 눌렀는지
 * 알지 못한다. 여기서 막는 것은 <b>키가 아니라 「버리는 행동」</b>이라 무엇으로 바꿔 두었든
 * 똑같이 걸린다.
 *
 * <h2>막는 것은 그 부적 하나뿐이다</h2>
 * <p>{@link PerkFlightCharm#blocksDrop} 이 <b>지금 고른 칸이 잠긴 칸이고, 거기 실제로 부적이
 * 있고, 그 팀이 아직 증강을 가지고 있을 때</b>만 참이다.
 *
 * <h2>대상은 {@code ServerPlayer} 다</h2>
 * <p>26.2 에서 {@code drop(boolean)} 은 {@code Player} 가 아니라 <b>{@code ServerPlayer} 에만</b>
 * 있다({@code Player} 에는 {@code drop(ItemStack, boolean)} 만 있다). {@code Player} 를 대상으로
 * 잡으면 mixin 이 붙지 못하고 클래스 변환 자체가 실패한다. 다른 칸을 들고 있으면 그냥 지나가고,
 * 부적을 안 가진 팀은 검사조차 하지 않는다. <b>다른 아이템 버리기는 조금도 달라지지 않는다.</b>
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDropLockMixin {

	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void sharedfate$lockFlightCharmDrop(boolean dropAll, CallbackInfo callback) {
		if (PerkFlightCharm.blocksDrop((ServerPlayer) (Object) this)) {
			callback.cancel();
		}
	}
}
