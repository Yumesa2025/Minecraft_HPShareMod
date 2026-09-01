package com.sharedfate.client.hud;

import com.sharedfate.client.GameOverClientDisplay;
import com.sharedfate.ui.GameOverCountdown;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * 게임 오버 화면 한가운데의 <b>빨간 5초 카운트다운.</b>
 *
 * <h2>왜 HUD 이고, 왜 하필 이 자리인가</h2>
 * <p>전멸하면 팀원 전원이 죽으므로 <b>사망 화면이 떠 있다.</b> 26.2 의 그리는 순서는
 * {@code Gui.extractRenderState} 가 <b>HUD 를 먼저 뽑고 그 위에 화면을 얹는</b> 것이라,
 * HUD 에 그린 글자는 사망 화면의 단추·글자에 <b>가려질 수 있다.</b> 그래서 자리를 아무 데나
 * 잡으면 안 된다.
 *
 * <p>사망 화면의 세로 배치는 GUI 크기와 상관없이 <b>화면 위에서부터 고정</b>이다 —
 * 제목 y=30(2배 확대라 48까지), 사인 줄 y=85, 점수 y=100. 반면 단추는 {@code h/4 + 72} 부터라
 * 화면이 낮을수록 위로 올라온다. 그래서 <b>제목과 사인 줄 사이의 빈 자리</b>인
 * {@value #COUNTDOWN_Y} 를 골랐다. 여기는 어떤 GUI 크기에서도 비어 있다.
 *
 * <p>바닐라 타이틀 패킷으로 보내는 길을 먼저 재 봤는데, 바닐라는 부제를 화면 세로 한가운데
 * ({@code h/2 + 10})에 그리고 사망 화면 단추가 바로 그 근처라 흔한 GUI 크기에서 숫자가 단추
 * 뒤로 들어간다. 자세한 것은 {@code WorldResetCoordinator} 에 적어 뒀다.
 *
 * <h2>죽지 않은 사람에게는 앞뒤 줄도 함께 적는다</h2>
 * <p>사망 화면이 떠 있으면 그 화면이 이미 제목 「게임 오버 · N회차」와 사인 줄을 그리므로
 * ({@code GameOverClientDisplay}) 숫자만 있으면 된다. 팀에 속하지 않은 접속자처럼 죽지 않은
 * 사람에게는 그 두 줄이 없으므로, 숫자 위에 「게임 오버」를, 아래에 무엇을 세는 숫자인지를
 * 함께 적는다. 사망 화면에서 이 두 줄을 그리면 화면의 제목·사인 줄과 글자가 겹친다.
 */
public class GameOverHud implements HudElement {
	/** 숫자의 윗변 y. 사망 화면 제목(30~48)과 사인 줄(85) 사이의 빈 자리다. */
	private static final int COUNTDOWN_Y = 52;
	/** 죽지 않은 사람에게만 그리는 「게임 오버」 한 줄의 y. 사망 화면 제목과 같은 자리다. */
	private static final int TITLE_Y = 30;
	/** 죽지 않은 사람에게만 그리는 안내 한 줄의 y. 3배 확대한 숫자(52~79) 바로 아래다. */
	private static final int NOTICE_Y = 84;

	private static final float COUNTDOWN_SCALE = 3.0F;
	private static final float TITLE_SCALE = 2.0F;

	/** 카운트다운 숫자 색. 눈에 박히는 빨강이어야 한다. */
	private static final int COUNTDOWN_COLOR = 0xFFFF3333;
	private static final int TITLE_COLOR = 0xFFFF5555;
	private static final int NOTICE_COLOR = 0xFFCCCCCC;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		int seconds = GameOverClientDisplay.countdownSeconds();
		if (seconds <= 0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.font == null) {
			return;
		}
		int centerX = graphics.guiWidth() / 2;
		Matrix3x2fStack pose = graphics.pose();
		boolean onGameOverScreen = client.gui.screen() instanceof DeathScreen;

		if (!onGameOverScreen) {
			pose.pushMatrix();
			pose.translate(centerX, TITLE_Y);
			pose.scale(TITLE_SCALE, TITLE_SCALE);
			graphics.centeredText(client.font,
					Component.literal(GameOverCountdown.TITLE).withStyle(ChatFormatting.BOLD),
					0, 0, TITLE_COLOR);
			pose.popMatrix();
		}

		// 글자 확대는 pose 에 배율을 쌓아서 한다. 그린 뒤 반드시 popMatrix 로 되돌려야 뒤에
		// 오는 HUD 요소가 함께 커지지 않는다.
		pose.pushMatrix();
		pose.translate(centerX, COUNTDOWN_Y);
		pose.scale(COUNTDOWN_SCALE, COUNTDOWN_SCALE);
		graphics.centeredText(client.font,
				Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.BOLD),
				0, 0, COUNTDOWN_COLOR);
		pose.popMatrix();

		// 숫자가 무엇을 세는지 적는 한 줄. 사망 화면에서는 그리지 않는다 — 그 자리(y=85)는
		// 사망 화면의 사인 줄이 쓰고 있어서 글자가 겹친다.
		if (!onGameOverScreen) {
			graphics.centeredText(client.font,
					Component.literal(GameOverCountdown.shutdownNotice(seconds)),
					centerX, NOTICE_Y, NOTICE_COLOR);
		}
	}
}
