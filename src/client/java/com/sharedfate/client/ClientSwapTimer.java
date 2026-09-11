package com.sharedfate.client;

import org.jetbrains.annotations.Nullable;

/**
 * 서버가 보내 준 「다음 위치 교환까지 남은 초」를 들고 있는 자리.
 *
 * <p>골드 「폭발 교환」을 가진 팀에게만 1초에 한 번 도착한다
 * ({@code SwapTimerPayload}). 화면 왼쪽 위 세트 줄 바로 위에 한 줄로 그려진다.
 *
 * <h2>스스로 세지 않는다</h2>
 * <p>받은 숫자를 그대로 보여 준다. 클라이언트가 초를 세게 하면 증강 선택 창이 떠 있는 동안,
 * 「시차」가 걸음을 옮기는 동안, 게임 오버 카운트다운 동안 <b>서버에서는 멈춰 있는 주기가
 * 화면에서만 흐른다.</b> 그러면 0 이 되고도 한참 뒤에 교환이 일어나 시계가 거짓말을 한다.
 *
 * <h2>스스로 지운다</h2>
 * <p>증강을 잃거나 팀을 나가면 묶음이 그냥 멎는다. 「그만 그려라」를 따로 받지 않으므로,
 * 마지막으로 받은 시각을 함께 적어 두고 {@value #STALE_TICKS} 틱 동안 새 값이 없으면 지운다.
 * 1초에 한 번씩 오는 값이라 이 여유면 끊긴 것이 확실하다.
 */
public final class ClientSwapTimer {
	/** 이만큼 새 값이 없으면 끊긴 것으로 본다. 60틱 = 3초. */
	private static final long STALE_TICKS = 60L;
	/** 아직 아무것도 못 받았다는 표시. */
	private static final int NONE = -1;

	private static int seconds = NONE;
	private static long receivedAtGameTime = Long.MIN_VALUE;

	private ClientSwapTimer() {
	}

	/** 서버에게서 받은 값을 적어 둔다. */
	public static void set(int remainingSeconds, long gameTime) {
		seconds = Math.max(0, remainingSeconds);
		receivedAtGameTime = gameTime;
	}

	/** 서버를 나가거나 팀이 사라질 때 지운다. */
	public static void clear() {
		seconds = NONE;
		receivedAtGameTime = Long.MIN_VALUE;
	}

	/**
	 * 지금 그릴 한 줄. 그릴 것이 없으면 {@code null}.
	 *
	 * @param gameTime 지금 게임 시간(틱). 값이 낡았는지 재는 데 쓴다
	 */
	public static @Nullable String line(long gameTime) {
		if (seconds < 0 || gameTime - receivedAtGameTime > STALE_TICKS) {
			return null;
		}
		return "위치 교환까지 " + clock(seconds);
	}

	/**
	 * 초를 {@code 분:초} 로. 1분이 안 되면 초만 적는다.
	 *
	 * <p>「3:07」처럼 두 자리로 맞춘다. 「3:7」은 한눈에 안 읽힌다.
	 */
	static String clock(int totalSeconds) {
		int safe = Math.max(0, totalSeconds);
		if (safe < 60) {
			return safe + "초";
		}
		return safe / 60 + ":" + (safe % 60 < 10 ? "0" : "") + safe % 60;
	}
}
