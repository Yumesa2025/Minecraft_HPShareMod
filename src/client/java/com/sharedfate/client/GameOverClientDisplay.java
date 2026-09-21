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
 *
 * <h2>같은 예고가 두 가지 이유로 온다</h2>
 * <p>운영자가 {@code /shareteam reset} 을 쳐도 똑같은 묶음이 온다. 그때는 <b>아무도 죽지
 * 않았으므로</b> 「게임 오버」라고 적으면 안 된다. {@code WorldResetPayload} 가 이유를 함께
 * 실어 오고, 이쪽은 그것을 {@link #reason} 에 들고 있다가 {@code GameOverHud} 와 아래
 * 사망 화면 칠하기가 함께 읽는다.
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
	private static GameOverCountdown.Reason reason = GameOverCountdown.Reason.TEAM_WIPE;
	private static String victimName = "";
	private static boolean applied;

	private GameOverClientDisplay() {
	}

	/**
	 * 서버 종료 예고. 화면의 제목을 바꾸고 카운트다운을 시작한다.
	 *
	 * @param why 전멸인가 운영자 초기화인가. 화면에 적을 글자를 여기서만 가른다
	 */
	public static void show(int run, int delayTicks, GameOverCountdown.Reason why) {
		runNumber = Math.max(1, run);
		reason = why == null ? GameOverCountdown.Reason.TEAM_WIPE : why;
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

	/**
	 * 지금 도는 카운트다운이 <b>왜</b> 도는가. {@code GameOverHud} 가 그릴 글자를 고르는 근거다.
	 *
	 * <p>예고가 오기 전에는 뜻이 없는 값이다. 실제로 읽는 자리는 {@link #countdownSeconds} 가
	 * 0 보다 클 때뿐이고, 그때는 {@link #show} 가 반드시 이 값을 함께 채운 뒤다.
	 */
	public static GameOverCountdown.Reason reason() {
		return reason;
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
			// 하드코어로 바꾸는 것은 부활 단추를 지우는 일이라 전멸에만 한다. 초기화 때
			// 사망 화면에 앉아 있는 사람은 **그 죽음과 상관없이** 부활할 수 있어야 한다 —
			// 그 사람이 죽은 것과 운영자가 서버를 내리는 것은 서로 남남이다.
			if (reason == GameOverCountdown.Reason.TEAM_WIPE) {
				((DeathScreenAccessor) deathScreen).sharedfate$setHardcore(true);
			}
			((ScreenAccessor) deathScreen).sharedfate$setTitle(
					Component.literal(GameOverCountdown.screenTitle(reason, runNumber)));
		}
		if (!victimName.isEmpty()) {
			((DeathScreenAccessor) deathScreen).sharedfate$setCauseOfDeath(
					Component.literal(victimName + " 님의 죽음으로 끝났습니다"));
		}
		((ScreenAccessor) deathScreen).sharedfate$rebuildWidgets();
		applied = true;
	}

	/**
	 * 연결이 끊길 때 전부 비운다.
	 *
	 * <p>이 값들은 {@code static} 이라 <b>게임을 끄기 전까지 살아 있다.</b> 초기화로 내려간
	 * 서버에 다시 들어가 이번에는 전멸하는 것이 바로 다음에 일어날 일인데, 지난번 이유가
	 * 남아 있으면 전멸 화면에 「서버 초기화」가 뜬다.
	 */
	public static void clear() {
		pendingTicks = 0;
		countdownTicks = 0;
		runNumber = 0;
		reason = GameOverCountdown.Reason.TEAM_WIPE;
		victimName = "";
		applied = false;
	}
}
