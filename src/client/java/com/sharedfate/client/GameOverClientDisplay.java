package com.sharedfate.client;

import com.sharedfate.client.mixin.DeathScreenAccessor;
import com.sharedfate.client.mixin.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

/**
 * 사망 화면을 이 모드의 게임 오버 화면으로 바꾼다.
 *
 * <p>고칠 것이 둘이고 <b>서로 다른 묶음으로 따로 온다.</b>
 *
 * <ul>
 *   <li>제목 「게임 오버! · N회차」 — {@code WorldResetPayload} 가 온 뒤에만. 월드 초기화를
 *       끈 서버에서는 회차라는 것이 없다.</li>
 *   <li>사인 줄 「OOO 님의 죽음으로 끝났습니다」 — {@code TeamWipePayload} 가 온 뒤에만.
 *       사망 알림을 켠 팀에서만 온다.</li>
 * </ul>
 *
 * <p>둘은 같은 순간에 오지만 도착 순서는 정해져 있지 않다. 그래서 하나가 도착할 때마다
 * {@link #applied} 를 내려 다시 칠하게 한다. 나중에 온 쪽이 먼저 칠한 것을 지우지 않는다.
 */
public final class GameOverClientDisplay {
	/** 사망 화면이 뜨기를 기다리는 최소 시간(틱). 5초면 충분히 넉넉하다. */
	private static final int MIN_WINDOW_TICKS = 100;

	private static int pendingTicks;
	private static int runNumber;
	private static String victimName = "";
	private static boolean applied;

	private GameOverClientDisplay() {
	}

	/** 월드 초기화 예고. 제목을 회차가 적힌 게임 오버로 바꾼다. */
	public static void show(int run, int delayTicks) {
		runNumber = Math.max(1, run);
		openWindow(delayTicks + 40);
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
					Component.literal("게임 오버! · " + runNumber + "회차"));
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
		runNumber = 0;
		victimName = "";
		applied = false;
	}
}
