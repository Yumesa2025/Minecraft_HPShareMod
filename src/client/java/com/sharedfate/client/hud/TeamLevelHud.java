package com.sharedfate.client.hud;

import com.sharedfate.client.ClientTeamState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 화면 오른쪽 위에 팀 공유 레벨과 다음 증강까지 남은 레벨을 표시한다.
 *
 * <p>인챈트 등으로 경험치를 쓰면 바닐라 경험치 바의 숫자가 내려가기 때문에, 다음 증강이
 * 언제 나오는지 알 수 없다. 그 값을 따로 보여주는 것이 이 HUD 의 목적이다.
 *
 * <p>표시 위치는 상태이상 아이콘 바로 아래다. 바닐라는 오른쪽 위에 아이콘을 그리는데,
 * 이로운 효과는 y=1..25, 해로운 효과는 y=27..51 을 쓴다({@code Hud.extractEffects}).
 * 그래서 지금 떠 있는 아이콘 줄 수를 보고 그 아래로 내려서 그린다.
 */
public class TeamLevelHud implements HudElement {
	/** 경험치 레벨 숫자와 같은 연두색. */
	private static final int LEVEL_COLOR = 0xFF80FF20;
	/** 남은 레벨은 한 단계 눈에 덜 띄는 색으로 둔다. */
	private static final int REMAINING_COLOR = 0xFFFFD24A;

	/** 화면 오른쪽 끝에서 띄울 여백. 상태이상 아이콘도 오른쪽 끝에 붙는다. */
	private static final int RIGHT_PADDING = 4;
	private static final int LINE_HEIGHT = 10;

	/** 상태이상 아이콘 한 칸의 크기와 줄 간격. 바닐라 값과 같아야 한다. */
	private static final int EFFECT_ICON_SIZE = 24;
	private static final int EFFECT_ROW_SPACING = 26;
	/** 바닐라가 첫 줄 아이콘을 그리기 시작하는 y. */
	private static final int EFFECT_TOP = 1;
	/** 데모 모드에서는 남은 시간 표시 때문에 아이콘이 아래로 밀린다. */
	private static final int DEMO_OFFSET = 15;
	/** 아이콘 아래로 띄울 간격. */
	private static final int GAP_BELOW_EFFECTS = 2;

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
		// F3 의 오른쪽 정보 칸이 바로 이 자리를 쓴다.
		if (client.gui.hud.isHidden() || isDebugScreenOpen(client)) {
			return;
		}

		Font font = client.font;
		String levelLine = "팀 레벨 " + ClientTeamState.teamLevel();
		int remaining = ClientTeamState.levelsToNextPerk();
		// 남은 증강이 없으면(35 구간을 넘겼거나 증강을 쓰지 않는 팀) 둘째 줄은 그리지 않는다.
		String remainingLine = remaining < 0 ? null : "다음 증강까지 " + remaining;

		int lines = remainingLine == null ? 1 : 2;
		int right = graphics.guiWidth() - RIGHT_PADDING;
		int y = clampTop(topBelowEffectIcons(client, player), lines, graphics.guiHeight());

		drawRightAligned(graphics, font, levelLine, right, y, LEVEL_COLOR);
		if (remainingLine != null) {
			drawRightAligned(graphics, font, remainingLine, right, y + LINE_HEIGHT, REMAINING_COLOR);
		}
	}

	private static boolean isDebugScreenOpen(Minecraft client) {
		DebugScreenOverlay overlay = client.getDebugOverlay();
		return overlay != null && overlay.showDebugScreen();
	}

	/** 지금 떠 있는 상태이상 아이콘 줄 수를 보고 그 아래 y 좌표를 구한다. */
	private static int topBelowEffectIcons(Minecraft client, Player player) {
		boolean beneficial = false;
		boolean harmful = false;
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (!effect.showIcon()) {
				continue;
			}
			if (effect.getEffect().value().isBeneficial()) {
				beneficial = true;
			} else {
				harmful = true;
			}
		}

		int top = EFFECT_TOP + (client.isDemo() ? DEMO_OFFSET : 0);
		if (harmful) {
			// 해로운 효과는 둘째 줄에 그려지므로 그 아래까지 내려간다.
			return top + EFFECT_ROW_SPACING + EFFECT_ICON_SIZE + GAP_BELOW_EFFECTS;
		}
		if (beneficial) {
			return top + EFFECT_ICON_SIZE + GAP_BELOW_EFFECTS;
		}
		return top + GAP_BELOW_EFFECTS;
	}

	/** GUI 배율이 크면 화면이 좁아지므로 아래로 넘치지 않게 잡아둔다. */
	private static int clampTop(int y, int lines, int guiHeight) {
		int limit = guiHeight - lines * LINE_HEIGHT - GAP_BELOW_EFFECTS;
		return Math.max(0, Math.min(y, limit));
	}

	private static void drawRightAligned(GuiGraphicsExtractor graphics, Font font, String text,
			int right, int y, int color) {
		// 글자가 화면 폭보다 길어도 왼쪽으로 잘려 나가지 않게 한다.
		int x = Math.max(0, right - font.width(text));
		graphics.text(font, text, x, y, color);
	}
}
