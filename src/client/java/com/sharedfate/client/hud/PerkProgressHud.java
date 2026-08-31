package com.sharedfate.client.hud;

import com.sharedfate.client.ClientTeamState;
import com.sharedfate.perk.PerkMilestones;
import com.sharedfate.ui.PerkGauge;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;

/**
 * 바닐라 경험치 레벨 숫자 <b>위</b>에 다음 증강 구간까지의 진행도를 막대로 그린다.
 *
 * <h2>왜 필요한가</h2>
 * <p>이 모드의 경험치는 팀 공유이고 증강은 정해진 레벨 구간에 <b>처음</b> 닿을 때 나온다.
 * 그런데 인챈트·모루로 레벨을 써 버리면 바닐라 경험치 바의 숫자가 내려가서, 다음 구간까지
 * 얼마나 남았는지 눈으로 알 수 없다. 그 진행도를 따로 그리는 것이 이 요소다.
 *
 * <h2>왜 여기인가</h2>
 * <p>레벨을 확인할 때 눈이 가는 곳은 화면 아래 가운데의 경험치 바다. 그 바로 위, 레벨 숫자
 * 위에 같은 폭·같은 두께로 두면 "경험치 바를 하나 더 얹은 것"으로 읽혀 설명이 필요 없다.
 * 좌표는 바닐라의 {@code ContextualBar}(폭 182, 높이 5, 아래 여백 24)와 레벨 숫자 자리에서
 * 그대로 따왔다.
 *
 * <h2>숫자를 함께 적지 않는 이유</h2>
 * <p>정확히 몇 레벨 남았는지는 {@link TeamLevelHud} 가 좌하단에 「다음 증강까지 N」으로 이미
 * 적고 있다. 같은 숫자를 화면 아래에 두 번 적으면 그만큼 지저분해질 뿐이다. 여기서는
 * <b>얼마나 찼는지</b>만 눈으로 읽게 하고 숫자는 좌하단에 맡긴다. 덕분에 이 요소가 차지하는
 * 자리도 막대 한 줄뿐이라 좌하단 글줄들을 거의 밀어내지 않는다.
 *
 * <h2>구간 수가 바뀌어도 따라간다</h2>
 * <p>한 칸의 길이는 {@link PerkMilestones#STEP} 을 그대로 쓰고, "더 받을 증강이 있는가"는
 * 서버가 {@code TeamSyncPayload.nextPerkLevel} 로 이미 알려 준다(만렙을 넘겼으면 0). 그래서
 * 구간 만렙이 바뀌어도 이 파일은 고칠 것이 없고, <b>통신 규약도 그대로다.</b>
 */
public class PerkProgressHud implements HudElement {
	/** 게이지 폭. 바닐라 경험치 바({@code ContextualBar.WIDTH})와 같다. */
	private static final int WIDTH = 182;
	/** 게이지 높이. 바닐라 경험치 바({@code ContextualBar.HEIGHT})와 같다. */
	private static final int HEIGHT = 5;
	/**
	 * 경험치 레벨 숫자의 윗변이 화면 아래에서 떨어진 거리.
	 *
	 * <p>바닐라 {@code ContextualBar.extractExperienceLevel} 이 쓰는
	 * {@code guiHeight - MARGIN_BOTTOM(24) - 글꼴 높이(9) - 2} 와 같은 값이다.
	 */
	private static final int LEVEL_TEXT_OFFSET = 35;
	/** 게이지와 레벨 숫자 사이 여백. */
	private static final int GAP_TO_LEVEL = 2;
	/** 게이지 위로 남겨 둘 여백. 좌하단 글줄이 이 위에서 시작한다. */
	private static final int MARGIN_TOP = 3;

	/** 아직 안 찬 부분. 바닐라 바의 어두운 바탕과 비슷한 무게로 둔다. */
	private static final int TRACK_COLOR = 0xA0202028;
	/** 찬 부분. {@link TeamLevelHud} 의 「다음 증강까지」와 같은 색이라 둘이 한 쌍으로 읽힌다. */
	private static final int FILL_COLOR = 0xFFFFD24A;
	/** 밝은 배경 위에서도 막대가 뭉개지지 않도록 두르는 테두리. */
	private static final int BORDER_COLOR = 0xC0000000;

	/**
	 * 지금 이 게이지를 그리는가.
	 *
	 * <p>팀이 없거나, 증강을 안 쓰는 팀이거나, 구간 만렙을 넘겨 더 받을 증강이 없으면 그리지
	 * 않는다. 세 경우 모두 {@code levelsToNextPerk()} 가 -1 이다.
	 *
	 * <p><b>다 찬 채로 남겨 두지 않는 이유</b> — 가득 찬 막대는 "곧 무언가 나온다"는 뜻으로
	 * 읽힌다. 더 나올 것이 없는데 그렇게 두면 거짓말이 된다. 좌하단이 「남은 증강 없음」으로
	 * 이미 말해 주므로 여기서는 자리를 비워 주는 편이 낫다.
	 */
	public static boolean visible() {
		return ClientTeamState.inTeam() && ClientTeamState.levelsToNextPerk() >= 0;
	}

	/**
	 * 게이지가 차지하는 자리의 윗변. 좌하단 글줄은 이 위에서 시작해야 겹치지 않는다.
	 *
	 * <p>안 그리는 상황이면 화면 아래끝을 돌려주어 아무것도 밀어내지 않는다.
	 */
	public static int clearanceTop(int guiHeight) {
		return visible() ? barTop(guiHeight) - MARGIN_TOP : guiHeight;
	}

	private static int barTop(int guiHeight) {
		return guiHeight - LEVEL_TEXT_OFFSET - GAP_TO_LEVEL - HEIGHT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!visible()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		// F1 로 HUD 를 껐거나 F3 디버그 화면이 켜져 있으면 그리지 않는다.
		if (client.gui.hud.isHidden() || isDebugScreenOpen(client)) {
			return;
		}

		int left = (graphics.guiWidth() - WIDTH) / 2;
		int top = barTop(graphics.guiHeight());
		int right = left + WIDTH;
		int bottom = top + HEIGHT;

		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
		graphics.fill(left, top, right, bottom, TRACK_COLOR);
		int filled = Math.round(WIDTH
				* PerkGauge.fraction(ClientTeamState.levelsToNextPerk(), PerkMilestones.STEP));
		if (filled > 0) {
			graphics.fill(left, top, left + filled, bottom, FILL_COLOR);
		}
	}

	private static boolean isDebugScreenOpen(Minecraft client) {
		DebugScreenOverlay overlay = client.getDebugOverlay();
		return overlay != null && overlay.showDebugScreen();
	}
}
