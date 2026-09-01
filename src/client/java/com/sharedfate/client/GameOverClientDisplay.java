package com.sharedfate.client;

import com.sharedfate.client.mixin.DeathScreenAccessor;
import com.sharedfate.client.mixin.ScreenAccessor;
import com.sharedfate.ui.GameOverCountdown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

/**
 * 사망 화면을 이 모드의 게임 오버 화면으로 바꾼다.
 *
 * <p>고칠 것이 둘이고 <b>서로 다른 묶음으로 따로 온다.</b>
 *
 * <ul>
 *   <li>제목 「게임 오버 · N회차」 — {@code WorldResetPayload} 가 온 뒤에만. 월드 초기화를
 *       끈 서버에서는 회차라는 것이 없다.</li>
 *   <li>사인 줄 「OOO 님의 죽음으로 끝났습니다」 — {@code TeamWipePayload} 가 온 뒤에만.
 *       사망 알림을 켠 팀에서만 온다.</li>
 * </ul>
 *
 * <p>둘은 같은 순간에 오지만 도착 순서는 정해져 있지 않다. 그래서 하나가 도착할 때마다
 * {@link #applied} 를 내려 다시 칠하게 한다. 나중에 온 쪽이 먼저 칠한 것을 지우지 않는다.
 *
 * <h2>서버 종료까지 남은 초</h2>
 * <p>전멸하면 {@code WorldResetPayload} 에 <b>서버가 종료되기까지 남은 틱</b>이 함께 온다.
 * 여기서 그 틱을 받아 매 틱 하나씩 줄이고, 실제로 숫자를 그리는 것은 {@code GameOverHud} 다.
 * 숫자를 <b>클라이언트가 세는</b> 이유는 {@code WorldResetCoordinator} 에 적어 뒀다 — 짧게
 * 말하면 바닐라 타이틀 자리가 사망 화면 단추에 가리기 때문이고, 남은 길이는 이미 이 묶음에
 * 실려 오던 값이라 <b>새로 주고받는 것이 없다.</b>
 */
public final class GameOverClientDisplay {
	/** 사망 화면이 뜨기를 기다리는 최소 시간(틱). 5초면 충분히 넉넉하다. */
	private static final int MIN_WINDOW_TICKS = 100;

	/**
	 * 카운트다운으로 인정할 최대 틱. 서버 설정의 상한({@code MAX_WORLD_RESET_DELAY_TICKS})과
	 * 같은 값이다. 손상된 값이 와도 화면에 「9999」 같은 숫자가 뜨지 않게 여기서 접는다.
	 */
	private static final int MAX_COUNTDOWN_TICKS = 1200;

	private static int pendingTicks;
	private static int countdownTicks;
	private static int runNumber;
	private static String victimName = "";
	private static boolean applied;

	private GameOverClientDisplay() {
	}

	/** 월드 초기화 예고. 제목을 회차가 적힌 게임 오버로 바꾸고 카운트다운을 시작한다. */
	public static void show(int run, int delayTicks) {
		runNumber = Math.max(1, run);
		countdownTicks = GameOverCountdown.sanitizeTicks(delayTicks, MAX_COUNTDOWN_TICKS);
		openWindow(delayTicks + 40);
	}

	/**
	 * 서버가 종료되기까지 남은 초. 0 이면 그릴 것이 없다.
	 *
	 * <p>{@code GameOverHud} 가 매 프레임 읽는다. 월드 초기화를 끈 서버에서는 예고 자체가
	 * 오지 않으므로 언제나 0 이고, 그런 서버에는 카운트다운도 종료도 없다.
	 */
	public static int countdownSeconds() {
		return GameOverCountdown.secondsRemaining(countdownTicks);
	}

	/** 전멸을 부른 사람. 사망 알림을 켠 팀에서만 온다. */
	public static void showVictim(String name) {
		victimName = name == null ? "" : name;
		openWindow(0);
	}

	private static void openWindow(int wantedTicks) {
		pendingTicks = Math.max(pendingTicks, Math.max(MIN_WINDOW_TICKS, wantedTicks));
		applied = false;
	}

	public static void tick(Minecraft client) {
		// 카운트다운은 사망 화면이 떴는지와 상관없이 흐른다. 서버는 어차피 자기 시계로 종료한다.
		if (countdownTicks > 0) {
			countdownTicks--;
		}
		if (pendingTicks <= 0) {
			return;
		}
		pendingTicks--;
		if (applied || !(client.gui.screen() instanceof DeathScreen deathScreen)) {
			return;
		}

		if (runNumber > 0) {
			((DeathScreenAccessor) deathScreen).sharedfate$setHardcore(true);
			((ScreenAccessor) deathScreen).sharedfate$setTitle(
					Component.literal(GameOverCountdown.TITLE + " · " + runNumber + "회차"));
		}
		if (!victimName.isEmpty()) {
			((DeathScreenAccessor) deathScreen).sharedfate$setCauseOfDeath(
					Component.literal(victimName + " 님의 죽음으로 끝났습니다"));
		}
		((ScreenAccessor) deathScreen).sharedfate$rebuildWidgets();
		applied = true;
	}

	public static void clear() {
		pendingTicks = 0;
		countdownTicks = 0;
		runNumber = 0;
		victimName = "";
		applied = false;
	}
}
