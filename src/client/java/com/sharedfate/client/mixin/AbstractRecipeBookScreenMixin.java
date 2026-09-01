package com.sharedfate.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sharedfate.client.ClientTeamState;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 조합법 책 단추가 창을 따라오게 합니다.
 *
 * <p>바닐라는 그 단추를 <b>화면 한가운데 기준</b>(예: {@code height / 2 - 49})으로 놓습니다.
 * 창이 54px 커지면 창 위쪽 좌표가 27px 올라가므로, 화면 기준 자리는 그대로인데 창에서 보면
 * 단추만 27px 내려간 것처럼 보입니다. 그만큼 되돌립니다.
 *
 * <p>제작대·화로·훈연기·용광로·플레이어 인벤토리가 모두 이 클래스를 물려받으므로 한 곳만
 * 고치면 됩니다.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@ModifyExpressionValue(
			method = "initButton",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;"
							+ "getRecipeBookButtonPosition()"
							+ "Lnet/minecraft/client/gui/navigation/ScreenPosition;"
			)
	)
	private ScreenPosition sharedfate$keepButtonWithWindow(ScreenPosition position) {
		if (!sharedfate$expanded()) {
			return position;
		}
		return new ScreenPosition(
				position.x(),
				position.y() - ExpandedInventoryManager.EXTRA_PANEL_HEIGHT / 2);
	}

	@Unique
	private static boolean sharedfate$expanded() {
		return ExpandedInventoryManager.enabled() && ClientTeamState.inTeam();
	}
}
