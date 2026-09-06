package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.inventory.ExpandedQuickMoveFallback;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 추가 27칸의 쉬프트 클릭을 <b>모든 화면에서</b> 살리는 공통 자리.
 *
 * <h2>왜 여기인가</h2>
 *
 * <p>{@code AbstractContainerMenu.quickMoveStack} 은 {@code abstract} 라 주입할 수 없다.
 * 그런데 <b>그것을 부르는 자리</b>인 {@code doClick} 은 concrete 이고 모든 화면이 지난다.
 * 그 호출을 감싸면 Mixin 하나가 모루·인챈트대·연마석·대장장이 작업대·양조대·직조기·
 * 지도 제작대·석재 절단기·말 인벤토리·용광로·훈연기·신호기·호퍼·발사기·셜커 상자·통까지
 * 한 번에 덮는다.
 *
 * <p>{@code doClick} 의 {@code QUICK_MOVE} 갈래에는 {@code quickMoveStack} 호출이 <b>두
 * 군데</b> 있다 — 첫 호출과, 같은 아이템이 남아 있으면 되풀이하는 반복문 안쪽이다.
 * {@code ordinal} 을 적지 않았으므로 둘 다 감싼다. 되풀이될 때도 뒤처리가 그대로
 * 이어져야 여러 빈칸에 나눠 담긴다.
 *
 * <p>판정과 이동은 전부 {@link ExpandedQuickMoveFallback} 에 있다. 시험이
 * {@code menu::quickMoveStack} 을 넘겨 <b>여기와 똑같은 순서</b>를 지날 수 있다.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ExpandedQuickMoveFallbackMixin {
	@WrapOperation(
			method = "doClick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
							+ "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)"
							+ "Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack sharedfate$quickMoveFallback(
			AbstractContainerMenu menu, Player player, int index, Operation<ItemStack> original) {
		return ExpandedQuickMoveFallback.quickMove(
				menu, player, index, (owner, slot) -> original.call(menu, owner, slot));
	}
}
