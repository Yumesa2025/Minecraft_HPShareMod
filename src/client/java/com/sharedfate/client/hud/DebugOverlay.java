package com.sharedfate.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;

/**
 * F3 판이 <b>지금 화면을 덮고 있는가</b>.
 *
 * <p>이 모드의 HUD 셋({@link CoordinateHud}·{@link PerkProgressHud}·{@link TeamLevelHud})이
 * 같은 질문을 한다. 덮고 있으면 우리 글자를 접는다 — 겹쳐 놓으면 둘 다 못 읽기 때문이다.
 *
 * <h2>⚠ {@code DebugScreenOverlay.showDebugScreen()} 을 쓰면 안 된다</h2>
 *
 * <p>이름과 달리 「F3 화면이 떠 있다」가 아니다. 26.2 의 바이트코드를 풀면 이렇다.
 *
 * <pre>{@code
 * if (!entries.isOverlayVisible() && entries.getCurrentlyEnabled().isEmpty()) return false;
 * if (!hud.isHidden()) return true;
 * return gui.screen() != null;
 * }</pre>
 *
 * <p>그리고 {@code currentlyEnabled} 에 무엇이 들어가는지가 함정이다.
 *
 * <pre>{@code
 * if (status == ALWAYS_ON || (isOverlayVisible && status == IN_OVERLAY)) { ... 추가 ... }
 * }</pre>
 *
 * <p>즉 <b>디버그 항목 하나라도 「항상 켜기」로 두면 F3 를 켜지 않아도 언제나 참</b>이다.
 * FPS 를 화면에 박아 두는 흔한 설정이 그것이고, 그 설정은 {@code debug-profile.json} 에
 * 저장돼 게임을 껐다 켜도 남는다.
 *
 * <p>그래서 실제로 이런 일이 있었다 — <b>F3 글자는 하나도 안 보이는데 모드의 왼쪽 위 표시만
 * 영영 사라진 사람들이 생겼다.</b> 핫바도 체력바도 멀쩡해서 F1 도 아니었고, 본인은 아무것도
 * 건드린 적이 없다고 했다. 원인은 예전에 FPS 를 박아 둔 것 하나였다.
 *
 * <h2>그래서 물어야 할 것은 하나뿐이다</h2>
 *
 * <p>우리가 알고 싶은 것은 「F3 판이 화면을 덮고 있는가」이지 「디버그 항목이 하나라도 켜져
 * 있는가」가 아니다. 그것을 그대로 묻는 자리가
 * {@link DebugScreenEntryList#isOverlayVisible()} 이다.
 *
 * <p>「항상 켜기」로 박아 둔 항목과는 겹칠 수 있다. 그건 그 사람이 스스로 고른 것이고,
 * <b>FPS 한 줄 때문에 모드 HUD 가 통째로 사라지는 것보다 낫다.</b>
 *
 * <p>{@code Minecraft.debugEntries} 는 {@code public} 이라 접근자 믹스인이 필요 없다.
 */
public final class DebugOverlay {

	private DebugOverlay() {
	}

	/** F3 판이 화면을 덮고 있으면 참. 클라이언트가 아직 없으면 거짓이다. */
	public static boolean coversScreen(Minecraft client) {
		if (client == null) {
			return false;
		}
		DebugScreenEntryList entries = client.debugEntries;
		return entries != null && entries.isOverlayVisible();
	}
}
