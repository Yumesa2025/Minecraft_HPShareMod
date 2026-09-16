package com.sharedfate.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「F3 가 켜져 있는가」를 <b>바닐라의 어느 메서드로 묻는가</b>.
 *
 * <p>이 한 줄을 잘못 고르면 모드의 왼쪽 위 표시가 <b>영구히 사라진다.</b> 실제로 겪었고,
 * 같은 두 줄이 세 군데에 복사돼 있어 셋 다 같이 죽었다.
 *
 * <h2>{@code showDebugScreen()} 을 쓰면 안 된다</h2>
 * <p>이름과 달리 「F3 화면이 떠 있다」가 아니다. 26.2 의 바이트코드를 풀면 이렇다.
 *
 * <pre>{@code
 * if (!entries.isOverlayVisible() && entries.getCurrentlyEnabled().isEmpty()) return false;
 * if (!hud.isHidden()) return true;
 * return gui.screen() != null;
 * }</pre>
 *
 * <p>그리고 {@code currentlyEnabled} 에 들어가는 조건이 문제다.
 *
 * <pre>{@code
 * if (status == ALWAYS_ON || (isOverlayVisible && status == IN_OVERLAY)) { ... 추가 ... }
 * }</pre>
 *
 * <p>즉 <b>항목 하나라도 {@link DebugScreenEntryStatus#ALWAYS_ON} 이면 F3 를 켜지 않아도
 * 언제나 참</b>이다. FPS 를 화면에 박아 두는 흔한 설정이 그것이고, 그 설정은
 * {@code debug-profile.json} 에 저장돼 게임을 껐다 켜도 남는다. 그래서 「어느 날부터 좌표가
 * 안 보이는데 F3 글자도 안 보인다」가 된다.
 *
 * <p>우리가 알고 싶은 것은 <b>F3 판이 화면을 덮고 있는가</b> 하나뿐이다. 그것을 그대로 묻는
 * 자리가 {@link DebugScreenEntryList#isOverlayVisible()} 이다.
 */
class DebugOverlayCheckTest {

	/** 왼쪽 위·경험치 줄에 그리는, 같은 판정을 쓰는 HUD 셋. */
	private static final Class<?>[] HUD_CLASSES = {
			CoordinateHud.class, PerkProgressHud.class, TeamLevelHud.class};

	/**
	 * 어느 하나라도 {@code showDebugScreen} 을 다시 부르면 여기서 걸린다.
	 *
	 * <p>클래스 파일의 상수 풀을 그대로 뒤진다. 살아 있는 클라이언트가 없어도 되고, 같은 실수가
	 * 다시 복사되는 것을 막는 것이 목적이다 — <b>처음 겪었을 때 이미 세 군데에 복사돼 있었다.</b>
	 */
	@Test
	void 아무도_showDebugScreen_을_부르지_않는다() throws IOException {
		for (Class<?> type : new Class<?>[] {
				CoordinateHud.class, PerkProgressHud.class, TeamLevelHud.class,
				DebugOverlay.class}) {
			assertFalse(referencesName(type, "showDebugScreen"),
					type.getSimpleName() + " 가 showDebugScreen 을 부른다. "
							+ "항목을 ALWAYS_ON 으로 둔 사람에게는 이 HUD 가 영영 안 보인다. "
							+ "DebugOverlay.coversScreen 을 쓰라");
		}
	}

	/** 판정은 {@link DebugOverlay} 한 곳에만 있어야 한다. */
	@Test
	void 오버레이_판정은_한_곳에_모여_있다() throws IOException {
		assertTrue(referencesName(DebugOverlay.class, "isOverlayVisible"),
				"DebugOverlay 가 F3 판이 떠 있는지를 묻지 않는다");
		for (Class<?> hud : HUD_CLASSES) {
			assertTrue(referencesName(hud, "DebugOverlay"),
					hud.getSimpleName() + " 가 공통 판정을 안 쓰고 제 나름대로 묻고 있다");
		}
	}

	/**
	 * 문제의 뿌리인 상태값이 실제로 있는지 못박는다.
	 *
	 * <p>이것이 없어지면 위의 규칙도 뜻을 잃으므로, 그때는 이 시험이 먼저 깨져 알려 준다.
	 */
	@Test
	void 항상_켜기_상태가_실제로_있다() {
		assertDoesNotThrow(() -> DebugScreenEntryStatus.valueOf("ALWAYS_ON"),
				"이 상태가 showDebugScreen 을 F3 와 무관하게 참으로 만든다");
		assertDoesNotThrow(() -> DebugScreenEntryStatus.valueOf("IN_OVERLAY"));
		assertDoesNotThrow(() -> DebugScreenEntryStatus.valueOf("NEVER"));
	}

	/**
	 * 우리가 대신 쓰는 길이 열려 있는지 본다.
	 *
	 * <p>{@code Minecraft.debugEntries} 가 {@code public} 이라 믹스인도 접근자도 필요 없다.
	 * 이 접근성이 닫히면 컴파일이 깨지기 전에 여기서 먼저 알려 준다.
	 */
	@Test
	void 오버레이가_떠_있는지_묻는_길이_열려_있다() throws NoSuchFieldException {
		Field entries = Minecraft.class.getField("debugEntries");
		assertTrue(Modifier.isPublic(entries.getModifiers()),
				"public 이 아니면 접근자 믹스인을 따로 만들어야 한다");
		assertTrue(DebugScreenEntryList.class.isAssignableFrom(entries.getType()));
		assertDoesNotThrow(() -> DebugScreenEntryList.class.getMethod("isOverlayVisible"));
	}

	/**
	 * 클래스 파일 안에 이 이름이 적혀 있는가.
	 *
	 * <p>메서드 이름은 상수 풀에 아스키로 들어가므로 바이트를 그대로 훑어도 찾을 수 있다.
	 */
	private static boolean referencesName(Class<?> type, String name) throws IOException {
		String path = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream in = type.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("클래스 파일을 찾지 못했습니다: " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains(name);
		}
	}
}
