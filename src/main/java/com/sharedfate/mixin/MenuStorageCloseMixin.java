package com.sharedfate.mixin;

import com.sharedfate.storage.TeamStorage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 창고 화면을 닫을 때 <b>남은 것을 대기열로 되돌린다.</b>
 *
 * <p>창고를 열 때 대기열 앞쪽을 꺼내 화면에 담는다. 그래서 닫을 때 반드시 되돌려야 한다 —
 * 안 되돌리면 <b>꺼내지 않은 물건이 그대로 사라진다.</b>
 *
 * <h2>커서에 든 것도 창고로 간다</h2>
 *
 * <p>바닐라 {@code removed} 는 커서의 물건을 인벤토리에 넣어 보고 <b>안 되면 바닥에
 * 버린다.</b> 인벤토리가 꽉 차서 창고를 연 상황이므로 「안 되는」 쪽이 보통이고, 그러면
 * 꺼내다 만 물건이 발밑에 떨어져 5분 뒤 사라진다. 그래서 바닐라가 손대기 <b>전에</b>
 * 가로채 창고로 되돌린다.
 *
 * <p>{@code HEAD} 에서 커서를 비우므로 뒤이어 도는 바닐라 코드는 할 일이 없어진다.
 * 취소하지 않는 이유는 {@code removed} 가 그 밖에도 여러 뒷정리를 하기 때문이다.
 *
 * <h2>접속이 끊겨도 같은 자리를 지난다</h2>
 * <p>바닐라는 사람이 나갈 때도 열린 메뉴를 닫으므로 이 주입이 함께 돈다.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuStorageCloseMixin {

	@Inject(method = "removed", at = @At("HEAD"))
	private void sharedfate$returnStorageItems(Player player, CallbackInfo ci) {
		if (!((Object) this instanceof ChestMenu chest)
				|| !(chest.getContainer() instanceof TeamStorage.Container storage)) {
			return;
		}
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			TeamStorage.returnCarried(storage, carried);
			menu.setCarried(ItemStack.EMPTY);
		}
		TeamStorage.writeBack(storage);
	}
}
