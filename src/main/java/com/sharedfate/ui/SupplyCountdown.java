package com.sharedfate.ui;

/**
 * 다음 보급까지 <b>얼마나 남았는가</b>를 재고 「04:12」로 적는 계산.
 *
 * <h2>⚠ 남은 시간을 서버가 초마다 보내지 않는다</h2>
 * <p>보급 주기는 {@code PerkSupplyDrops} 가 <b>오버월드의 게임 시간</b>만으로 정한다. 경계는
 * 언제나 주기의 배수라({@code cycleAt} 이 {@code floorDiv} 한 자리) 「다음 보급이 언제인가」는
 * <b>게임 시간과 주기 두 값만 알면 누구나 같은 답을 낸다.</b>
 *
 * <p>그런데 게임 시간은 <b>바닐라가 이미 클라이언트에 보내 주고 있다.</b>
 * {@code MinecraftServer} 가 20틱마다 {@code ClientboundSetTimePacket} 으로 오버월드의 게임
 * 시간을 접속자 전원에게 뿌리고({@code forceGameTimeSynchronization}), 그 사이는
 * {@code ClientLevel} 이 틱마다 1씩 올린다. 차원을 옮겨도 같은 값이다 — 뿌리는 값이 언제나
 * 오버월드의 것이기 때문이다.
 *
 * <p>그래서 <b>새로 보내야 하는 것은 주기 하나뿐</b>이고, 그 값은 세트 단계가 바뀔 때만
 * 달라진다.
 *
 * <h2>표시 형식</h2>
 * <p>{@code 분:초} 이고 <b>분과 초를 모두 두 자리로 채운다</b>({@code 04:12}, {@code 00:07},
 * {@code 10:00}).
 *
 * <p>분을 채우는 까닭은 <b>구분선이 흔들리기 때문</b>이다. 세트 줄 위에 긋는 선의 길이는
 * {@code PerkSetLines.blockWidth} 가 <b>가장 긴 줄에 맞춰 매 프레임 다시 재는데</b>, 분을
 * 채우지 않으면 {@code 10:00} 에서 {@code 9:59} 로 넘어가는 순간 글자 하나가 사라진다. 그러면
 * 선이 눈에 띄게 짧아졌다가 다음 주기가 시작될 때 다시 길어져, 아무것도 안 했는데 화면
 * 왼쪽 위가 주기마다 한 번씩 움찔한다. 두 자리로 채우면 글자 수가 고정되어 그 흔들림이
 * 사라진다.
 *
 * <p><b>분은 두 자리로 자르지 않는다.</b> 주기의 상한이 240분이라 {@code 240:00} 까지 나올 수
 * 있고, 그때는 자연스럽게 세 자리로 늘어난다. 채우는 것이지 맞추는 것이 아니다.
 *
 * <p><b>00:00 은 뜨지 않는다.</b> 남은 초를 <b>올림</b>하므로 마지막 1초 동안에도 {@code 00:01}
 * 이 떠 있다가 곧바로 다음 주기의 큰 값으로 넘어간다. 내림을 쓰면 00:00 이 1초 동안 멈춰 있어
 * 「멈췄나」로 읽힌다.
 */
public final class SupplyCountdown {
	/** 1초는 몇 틱인가. */
	public static final int TICKS_PER_SECOND = 20;

	private SupplyCountdown() {
	}

	/**
	 * 다음 경계까지 남은 틱.
	 *
	 * <p>주기가 0 이하면 0 이다 — 도는 보급이 없다는 뜻이라 화면도 아무것도 그리지 않는다.
	 *
	 * <p>경계에 정확히 서 있는 순간({@code gameTime} 이 주기의 배수)에는 <b>주기 전체</b>를
	 * 돌려준다. 그 틱에 보급이 오고 다음 것은 꼬박 한 주기 뒤이기 때문이다.
	 *
	 * <p>{@code Math.floorMod} 를 쓴다. 게임 시간이 음수가 되는 일은 없지만 {@code %} 는 음수에서
	 * 음수를 내놓아 남은 시간이 주기보다 커진다.
	 */
	public static int remainingTicks(long gameTime, int intervalTicks) {
		if (intervalTicks <= 0) {
			return 0;
		}
		long into = Math.floorMod(gameTime, (long) intervalTicks);
		return (int) (intervalTicks - into);
	}

	/**
	 * 다음 경계까지 남은 초. <b>올림</b>이다.
	 *
	 * <p>남은 틱이 1~20 이면 1초로 읽는다. 그래서 0 이 나오는 것은 도는 보급이 없을 때뿐이고,
	 * 화면에는 {@code 00:00} 대신 {@code 00:01} 이 마지막으로 뜬다.
	 */
	public static int remainingSeconds(long gameTime, int intervalTicks) {
		int ticks = remainingTicks(gameTime, intervalTicks);
		if (ticks <= 0) {
			return 0;
		}
		return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
	}

	/**
	 * 「04:12」 한 토막. 음수는 0 으로 본다.
	 *
	 * <p>분과 초를 <b>둘 다 두 자리로 채운다.</b> 글자 수를 고정해 두어야 구분선이 매 주기
	 * 한 번씩 짧아졌다 길어지지 않는다.
	 *
	 * <p><b>두 자리로 자르지는 않는다.</b> 채우기는 아래에서만 하므로 분이 세 자리가 되면
	 * 그대로 세 자리로 나간다. 주기의 상한이 240분이라 「240:00」까지 나올 수 있다.
	 */
	public static String format(int seconds) {
		int safe = Math.max(0, seconds);
		int minutes = safe / 60;
		int rest = safe % 60;
		return (minutes < 10 ? "0" : "") + minutes + ":" + (rest < 10 ? "0" : "") + rest;
	}

	/**
	 * 그 시각에 화면에 적을 글자. 도는 보급이 없으면 <b>빈 문자열</b>이다.
	 *
	 * <p>빈 문자열을 돌려주는 것이 중요하다 — 부르는 쪽이 「보급인가」를 따로 판단하지 않고
	 * 이 값이 비었는지만 보면 되기 때문이다. 어느 유형에 시계를 붙일지는 서버가 주기를
	 * 실어 주느냐로 이미 정해진다.
	 */
	public static String text(long gameTime, int intervalTicks) {
		if (intervalTicks <= 0) {
			return "";
		}
		return format(remainingSeconds(gameTime, intervalTicks));
	}
}
