package com.sharedfate.client.hud;

import com.sharedfate.client.ClientTeamState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * 핫바 왼쪽 끝 위로 쌓아 올리는 글줄들의 좌표를 한곳에서 계산한다.
 *
 * <p>{@link TeamLevelHud} 와 {@link DamageAlertHud} 가 같은 자리를 쓴다. 각자 좌표를 재면
 * 서로 겹치므로, 바닥선을 여기서 한 번만 구하고 누가 몇 줄을 먹는지도 여기서 정한다.
 *
 * <h2>쌓는 순서</h2>
 * <p>바닥에 <b>팀 레벨</b>이 오고 그 위로 <b>피격 알림</b>이 올라간다. 팀 레벨은 늘 떠 있고
 * 피격 알림은 잠깐 떴다 사라지므로, 항상 있는 것을 고정된 자리에 두어야 눈이 익는다.
 * 반대로 두면 팀 레벨이 피격 때마다 위아래로 흔들린다.
 *
 * <p>{@link PerkProgressHud} 는 화면 가운데에 그리지만 <b>왼쪽 끝이 핫바 왼쪽 끝과 같은
 * x</b> 라 이 글줄들과 세로로 부딪힌다. 그래서 바닥선을 잴 때 그 게이지도 함께 센다.
 */
public final class BottomLeftStack {
	/** 글줄 높이. 바닐라 기본 글꼴 기준이다. */
	public static final int LINE_HEIGHT = 10;
	/** 핫바 왼쪽 끝은 화면 가운데에서 이만큼 왼쪽이다. 바닐라 값과 같아야 한다. */
	private static final int HOTBAR_HALF_WIDTH = 91;
	/** 핫바와 경험치 바가 차지하는 높이. 이 위로 체력 줄이 쌓인다. */
	private static final int BOTTOM_BARS_HEIGHT = 39;
	/** 하트 한 줄의 기본 간격. 줄이 많아지면 바닐라가 이보다 촘촘하게 그린다. */
	private static final int HEART_ROW_SPACING = 10;
	/** 하트가 아무리 촘촘해져도 이보다 좁아지지는 않는다. */
	private static final int MIN_HEART_ROW_SPACING = 3;
	/** 방어구 칸이 차지하는 한 줄. */
	private static final int ARMOR_ROW_HEIGHT = 10;

	private BottomLeftStack() {
	}

	/** 핫바 왼쪽 끝 x. */
	public static int left(GuiGraphicsExtractor graphics) {
		return graphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH;
	}

	/**
	 * 가장 아래 글줄을 그릴 y.
	 *
	 * <p>흡수 체력까지 더해 하트가 몇 줄인지 세고, 방어구를 입고 있으면 한 줄 더 올린다.
	 * 최대 체력이 늘거나 방어구를 갈아입으면 이 값이 따라 움직인다.
	 */
	public static int baseline(Player player, int guiHeight) {
		int heartRows = Math.max(1,
				(int) Math.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 20.0F));
		int rowSpacing = Math.max(HEART_ROW_SPACING - (heartRows - 2), MIN_HEART_ROW_SPACING);
		// 증강 게이지는 핫바 왼쪽 끝과 같은 x 에서 시작하므로 이 글줄들과 세로로 부딪힌다.
		// 게이지가 떠 있으면 그 위에서 쌓기 시작한다.
		int bottom = Math.min(guiHeight - BOTTOM_BARS_HEIGHT,
				PerkProgressHud.clearanceTop(guiHeight));
		int y = bottom - (heartRows - 1) * rowSpacing - LINE_HEIGHT;
		if (player.getArmorValue() > 0) {
			y -= ARMOR_ROW_HEIGHT;
		}
		return y;
	}

	/**
	 * 팀 레벨 표시가 바닥에서 몇 줄을 차지하는가. 팀이 없으면 0줄이다.
	 *
	 * <p>{@link TeamLevelHud} 가 그리는 줄 수와 반드시 같아야 한다. 피격 알림은 이만큼
	 * 위에서 시작한다.
	 */
	public static int teamLevelLines() {
		if (!ClientTeamState.inTeam()) {
			return 0;
		}
		return ClientTeamState.levelsToNextPerk() < 0 ? 1 : 2;
	}
}
