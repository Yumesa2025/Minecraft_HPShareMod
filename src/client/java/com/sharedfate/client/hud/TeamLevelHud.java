package com.sharedfate.client.hud;

import com.sharedfate.client.ClientTeamState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.player.Player;

/**
 * 화면 아래 왼쪽, 핫바 왼쪽 끝에 맞춰 팀 공유 레벨과 다음 증강까지 남은 레벨을 표시한다.
 *
 * <p>인챈트 등으로 경험치를 쓰면 바닐라 경험치 바의 숫자가 내려가기 때문에, 다음 증강이
 * 언제 나오는지 알 수 없다. 그 값을 따로 보여주는 것이 이 HUD 의 목적이다.
 *
 * <h2>왜 오른쪽 위에서 내려왔나</h2>
 * <p>처음에는 상태이상 아이콘 아래(오른쪽 위)에 붙였는데, 거기에 있다는 것을 모르면
 * 아무도 보지 않는 자리였다. 레벨을 확인할 때 눈이 가는 곳은 화면 아래 경험치 바다.
 * 그래서 그 옆으로 옮겼다.
 *
 * <p>좌표 계산과 다른 표시와의 겹침 정리는 {@link BottomLeftStack} 이 맡는다.
 */
public class TeamLevelHud implements HudElement {
	/** 경험치 레벨 숫자와 같은 연두색. */
	private static final int LEVEL_COLOR = 0xFF80FF20;
	/** 남은 레벨은 한 단계 눈에 덜 띄는 색으로 둔다. */
	private static final int REMAINING_COLOR = 0xFFFFD24A;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientTeamState.inTeam()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null) {
			return;
		}
		// F1 로 HUD 를 껐거나 F3 디버그 화면이 켜져 있으면 그리지 않는다.
		if (client.gui.hud.isHidden() || isDebugScreenOpen(client)) {
			return;
		}

		Font font = client.font;
		int remaining = ClientTeamState.levelsToNextPerk();
		int left = BottomLeftStack.left(graphics);
		int baseline = BottomLeftStack.baseline(player, graphics.guiHeight());

		// 남은 증강이 없으면(35 구간을 넘겼거나 증강을 쓰지 않는 팀) 팀 레벨 한 줄만 그린다.
		if (remaining < 0) {
			graphics.text(font, "팀 레벨 " + ClientTeamState.teamLevel(), left, baseline, LEVEL_COLOR);
			return;
		}
		graphics.text(font, "팀 레벨 " + ClientTeamState.teamLevel(),
				left, baseline - BottomLeftStack.LINE_HEIGHT, LEVEL_COLOR);
		graphics.text(font, "다음 증강까지 " + remaining, left, baseline, REMAINING_COLOR);
	}

	private static boolean isDebugScreenOpen(Minecraft client) {
		DebugScreenOverlay overlay = client.getDebugOverlay();
		return overlay != null && overlay.showDebugScreen();
	}
}
