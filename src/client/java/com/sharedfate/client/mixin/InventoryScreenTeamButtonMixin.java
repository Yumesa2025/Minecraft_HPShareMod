package com.sharedfate.client.mixin;

import com.sharedfate.client.ClientStatRows;
import com.sharedfate.client.team.TeamScreen;
import com.sharedfate.ui.InventoryStatPanel;
import com.sharedfate.ui.InventoryTeamButton;
import com.sharedfate.ui.StatRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

import java.util.List;

/**
 * 인벤토리 화면(E) 왼쪽에 SharedFate 화면을 여는 단추와 <b>능력치</b>를 붙입니다.
 *
 * <p>{@code /st} 를 칠 일이 잦은데 창을 열려면 채팅을 거쳐야 했고, 능력치는 그 창의 탭까지
 * 들어가야 보였습니다. 이제 인벤토리를 여는 순간 단추 아래에 세로로 늘어섭니다. 단추 이름과
 * 자리, 자리가 모자랄 때 어떻게 물러나는지는 {@link InventoryTeamButton} 과
 * {@link InventoryStatPanel} 에 적어 두었습니다.
 *
 * <h2>팀이 없어도 보입니다</h2>
 * <p>팀을 <b>만드는</b> 것도 그 화면에서 하는 일이므로, 팀이 없을 때 단추를 감추면 정작 필요한
 * 사람에게 없는 단추가 됩니다. 능력치도 마찬가지로 팀이 없어도 그립니다 — 증강이 붙기 전의
 * 값을 봐 두어야 나중에 무엇이 달라졌는지 압니다.
 *
 * <h2>왜 매 프레임 자리를 다시 잡는가</h2>
 * <p>조합법 책을 펼치면 바닐라는 창을 오른쪽으로 밀면서 <b>조합법 책 단추만</b> 새 자리로
 * 옮깁니다({@code AbstractRecipeBookScreen} 의 단추 콜백). 화면을 다시 만들지 않으므로 여기서
 * {@code init} 때 잡아 둔 자리는 그대로 남아 펼쳐진 책에 깔립니다. 그림을 뽑기 직전에 다시
 * 재면 한 프레임도 어긋나지 않습니다. 능력치 값도 같은 자리에서 다시 읽으므로, 무기를 바꾸면
 * 창을 닫지 않아도 숫자가 따라옵니다.
 *
 * <p>{@code TAIL} 로 넣는 이유는 {@code InventoryScreen.init} 에 <b>return 이 둘</b>이기
 * 때문입니다. 앞의 것은 크리에이티브 모드라 화면을 크리에이티브 인벤토리로 갈아 끼우고 곧바로
 * 빠져나가는 길이고, 거기에 단추를 얹으면 곧 버려지는 화면에 붙습니다.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenTeamButtonMixin {
	/**
	 * 파고들 메서드를 <b>서술자까지</b> 적습니다.
	 *
	 * <p>{@code InventoryScreen} 에는 이름이 같은 메서드가 <b>둘</b> 있습니다 — 화면을 그리는
	 * 이것과, 인벤토리 속 플레이어 인형을 그리려고 쓰는
	 * {@code private static EntityRenderState extractRenderState(LivingEntity)} 입니다.
	 * 이름만 적으면 믹스인이 둘 다 고르고, 서명이 맞지 않는 쪽에서 화면을 여는 순간
	 * 터집니다. {@code ScreenStatSourceTest} 가 이 둘이 그대로 있는지 붙들어 둡니다.
	 */
	@Unique
	private static final String EXTRACT_RENDER_STATE =
			"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";

	@Unique
	private Button sharedfate$teamButton;

	/** 이번 프레임에 그릴 능력치 줄들. {@code extractRenderState} 머리에서 다시 잽니다. */
	@Unique
	private InventoryStatPanel.Layout sharedfate$stats;
	@Unique
	private int sharedfate$statLeft;
	@Unique
	private int sharedfate$statTop;

	@Inject(method = "init", at = @At("TAIL"))
	private void sharedfate$addTeamButton(CallbackInfo ci) {
		sharedfate$teamButton = Button.builder(
						Component.literal(InventoryTeamButton.LABEL),
						button -> sharedfate$openTeamScreen())
				.bounds(0, 0, InventoryTeamButton.MIN_WIDTH, InventoryTeamButton.HEIGHT)
				.tooltip(Tooltip.create(Component.literal(InventoryTeamButton.TOOLTIP)))
				.build();
		sharedfate$layOut();
		((ScreenAccessor) this).sharedfate$addRenderableWidget(sharedfate$teamButton);
	}

	@Inject(method = EXTRACT_RENDER_STATE, at = @At("HEAD"))
	private void sharedfate$followWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		sharedfate$layOut();
	}

	/**
	 * 능력치를 그립니다. 창과 칸이 모두 그려진 <b>뒤</b>입니다.
	 *
	 * <p>줄들은 창 바깥 왼쪽에만 있어 무엇과도 겹치지 않지만, 머리에서 그리면 이 모드가 창
	 * 아래에 덧그리는 추가 27칸 판보다 먼저 나와 겹칠 여지가 생깁니다. 꼬리에서 그리면 그런
	 * 순서 문제가 아예 없습니다.
	 */
	@Inject(method = EXTRACT_RENDER_STATE, at = @At("TAIL"))
	private void sharedfate$drawStats(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		if (sharedfate$stats == null || !sharedfate$stats.visible()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		for (InventoryStatPanel.Line line : sharedfate$stats.lines()) {
			graphics.text(font, line.text(),
					sharedfate$statLeft, sharedfate$statTop + line.y(), line.color());
		}
	}

	/**
	 * 단추 폭·자리와 능력치 줄을 다시 잽니다.
	 *
	 * <p>단추와 줄들을 <b>한 덩어리</b>로 보고 그 오른쪽 끝을 창(또는 펼친 조합법 책)에
	 * 붙입니다. 덩어리 폭은 둘 중 넓은 쪽이라, 줄이 단추보다 길어도 창을 덮지 않습니다.
	 */
	@Unique
	private void sharedfate$layOut() {
		if (sharedfate$teamButton == null) {
			return;
		}
		Screen self = (Screen) (Object) this;
		AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) this;
		Font font = Minecraft.getInstance().font;

		int available = InventoryTeamButton.available(
				self.width, window.sharedfate$getImageWidth(), window.sharedfate$getLeftPos());
		int buttonWidth = InventoryTeamButton.buttonWidth(
				font.width(InventoryTeamButton.LABEL), available);

		List<List<StatRow>> groups = ClientStatRows.groups(Minecraft.getInstance().player);
		sharedfate$stats = InventoryStatPanel.layout(groups, available,
				InventoryTeamButton.statHeight(self.height, window.sharedfate$getTopPos()),
				font::width);

		int blockWidth = Math.max(buttonWidth, sharedfate$stats.width());
		int left = InventoryTeamButton.blockLeft(available, blockWidth);

		sharedfate$teamButton.setWidth(buttonWidth);
		sharedfate$teamButton.setPosition(
				left, InventoryTeamButton.y(window.sharedfate$getTopPos()));
		sharedfate$statLeft = left;
		sharedfate$statTop = InventoryTeamButton.statTop(window.sharedfate$getTopPos());
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
