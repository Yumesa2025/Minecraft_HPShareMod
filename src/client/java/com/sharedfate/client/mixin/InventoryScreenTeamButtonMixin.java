package com.sharedfate.client.mixin;

import com.sharedfate.client.team.TeamScreen;
import com.sharedfate.ui.InventoryTeamButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 인벤토리 화면(E) 왼쪽에 팀 화면을 여는 단추를 붙입니다.
 *
 * <p>{@code /shareteam} 을 칠 일이 잦은데 창을 열려면 채팅을 거쳐야 했습니다. 자리와 글자를 정한
 * 까닭은 {@link InventoryTeamButton} 에 적어 두었습니다.
 *
 * <h2>팀이 없어도 보입니다</h2>
 * <p>팀을 <b>만드는</b> 것도 그 화면에서 하는 일이므로, 팀이 없을 때 단추를 감추면 정작 필요한
 * 사람에게 없는 단추가 됩니다.
 *
 * <h2>왜 매 프레임 자리를 다시 잡는가</h2>
 * <p>조합법 책을 펼치면 바닐라는 창을 오른쪽으로 밀면서 <b>조합법 책 단추만</b> 새 자리로
 * 옮깁니다({@code AbstractRecipeBookScreen} 의 단추 콜백). 화면을 다시 만들지 않으므로 여기서
 * {@code init} 때 잡아 둔 자리는 그대로 남아 펼쳐진 책에 깔립니다. 그림을 뽑기 직전에 다시
 * 재면 한 프레임도 어긋나지 않습니다.
 *
 * <p>{@code TAIL} 로 넣는 이유는 {@code InventoryScreen.init} 에 <b>return 이 둘</b>이기
 * 때문입니다. 앞의 것은 크리에이티브 모드라 화면을 크리에이티브 인벤토리로 갈아 끼우고 곧바로
 * 빠져나가는 길이고, 거기에 단추를 얹으면 곧 버려지는 화면에 붙습니다.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenTeamButtonMixin {
	@Unique
	private Button sharedfate$teamButton;

	@Inject(method = "init", at = @At("TAIL"))
	private void sharedfate$addTeamButton(CallbackInfo ci) {
		sharedfate$teamButton = Button.builder(
						Component.literal(InventoryTeamButton.LABEL),
						button -> sharedfate$openTeamScreen())
				.bounds(0, 0, InventoryTeamButton.WIDTH, InventoryTeamButton.HEIGHT)
				.tooltip(Tooltip.create(Component.literal(InventoryTeamButton.TOOLTIP)))
				.build();
		sharedfate$placeTeamButton();
		((ScreenAccessor) this).sharedfate$addRenderableWidget(sharedfate$teamButton);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void sharedfate$followWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		sharedfate$placeTeamButton();
	}

	@Unique
	private void sharedfate$placeTeamButton() {
		if (sharedfate$teamButton == null) {
			return;
		}
		Screen self = (Screen) (Object) this;
		AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) this;
		sharedfate$teamButton.setPosition(
				InventoryTeamButton.x(self.width, window.sharedfate$getImageWidth(),
						window.sharedfate$getLeftPos()),
				InventoryTeamButton.y(window.sharedfate$getTopPos()));
	}

	/**
	 * 팀 화면을 엽니다. {@code /shareteam} 을 친 것과 같은 창입니다.
	 *
	 * <p>명령을 서버로 보내 서버가 {@code OpenTeamScreenPayload} 를 되돌려 주게 해도 되지만,
	 * 그러면 왕복 한 번만큼 늦게 열립니다. 이 화면이 서버에 묻는 것 없이 이미 받아 둔 값만으로
	 * 그려지므로({@code TeamScreen}) 곧바로 열어도 같은 내용입니다.
	 */
	@Unique
	private void sharedfate$openTeamScreen() {
		Minecraft.getInstance().setScreenAndShow(new TeamScreen());
	}
}
