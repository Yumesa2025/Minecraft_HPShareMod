package com.sharedfate.ui;

/**
 * 다음 보급까지 <b>얼마나 남았는가</b>를 재고 「04:12」로 적는 계산.
 *
 * <h2>⚠ 남은 시간을 서버가 초마다 보내지 않는다</h2>
 * <p>보급 주기는 {@code PerkSupplyDrops} 가 <b>오버월드의 게임 시간</b>으로 정한다. 경계는
 * 「보급이 켜진 시점({@code anchorTick})부터 주기마다」라({@code cycleAt} 이 {@code floorDiv} 한
 * 자리) 「다음 보급이 언제인가」는 <b>게임 시간과 주기와 켜진 시점 세 값만 알면 누구나 같은
 * 답을 낸다.</b>
 *
 * <p>그런데 게임 시간은 <b>바닐라가 이미 클라이언트에 보내 주고 있다.</b>
 * {@code MinecraftServer} 가 20틱마다 {@code ClientboundSetTimePacket} 으로 오버월드의 게임
 * 시간을 접속자 전원에게 뿌리고({@code forceGameTimeSynchronization}), 그 사이는
 * {@code ClientLevel} 이 틱마다 1씩 올린다. 차원을 옮겨도 같은 값이다 — 뿌리는 값이 언제나
 * 오버월드의 것이기 때문이다.
 *
 * <p>그래서 <b>새로 보내야 하는 것은 주기와 켜진 시점 둘뿐</b>이고, 그 둘은 세트 단계가
 * 바뀌거나 세트가 풀렸다 다시 켜질 때만 달라진다. 초마다 달라지는 값은 하나도 싣지 않는다.
 *
 * <h2>켜진 시점을 모르면 0 으로 둔다</h2>
 * <p>{@code anchorTick} 을 안 싣는 옛 서버에 붙으면 0 이 오고, 그러면 경계가 「게임 시간이
 * 주기의 배수인 자리」가 되어 예전과 똑같이 움직인다. 시계가 틀리게 보이는 대신 <b>조용히
 * 예전 규칙으로 떨어진다</b>는 뜻이다.
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
	 * 켜진 시점을 모르는 자리에서 쓰는 짧은 꼴. 경계가 게임 시간의 배수가 된다.
	 *
	 * <p>주기를 싣되 켜진 시점은 안 싣는 옛 서버에 붙었을 때의 모습이다.
	 */
	public static int remainingTicks(long gameTime, int intervalTicks) {
		return remainingTicks(gameTime, intervalTicks, 0L);
	}

	/**
	 * 다음 경계까지 남은 틱.
	 *
	 * <p>주기가 0 이하면 0 이다 — 도는 보급이 없다는 뜻이라 화면도 아무것도 그리지 않는다.
	 *
	 * <p>경계에 정확히 서 있는 순간({@code gameTime - anchorTick} 이 주기의 배수)에는 <b>주기
	 * 전체</b>를 돌려준다. 그 틱에 보급이 오고 다음 것은 꼬박 한 주기 뒤이기 때문이다.
	 *
	 * <p>{@code Math.floorMod} 를 쓴다. 켜진 시점보다 <b>이른</b> 게임 시간이 들어오면
	 * {@code gameTime - anchorTick} 이 음수가 되는데, {@code %} 는 음수에서 음수를 내놓아 남은
	 * 시간이 주기보다 커진다. 서버와 클라이언트의 게임 시간이 한 틱 어긋나는 순간에 실제로
	 * 일어난다.
	 *
	 * @param anchorTick 보급이 켜진 게임 시간. 경계는 이 자리부터 주기마다다.
	 *                   모르면 0 을 넘긴다
	 */
	public static int remainingTicks(long gameTime, int intervalTicks, long anchorTick) {
		if (intervalTicks <= 0) {
			return 0;
		}
		long into = Math.floorMod(gameTime - anchorTick, (long) intervalTicks);
		return (int) (intervalTicks - into);
	}

	/** 켜진 시점을 모르는 자리에서 쓰는 짧은 꼴. */
	public static int remainingSeconds(long gameTime, int intervalTicks) {
		return remainingSeconds(gameTime, intervalTicks, 0L);
	}

	/**
	 * 다음 경계까지 남은 초. <b>올림</b>이다.
	 *
	 * <p>남은 틱이 1~20 이면 1초로 읽는다. 그래서 0 이 나오는 것은 도는 보급이 없을 때뿐이고,
	 * 화면에는 {@code 00:00} 대신 {@code 00:01} 이 마지막으로 뜬다.
	 */
	public static int remainingSeconds(long gameTime, int intervalTicks, long anchorTick) {
		int ticks = remainingTicks(gameTime, intervalTicks, anchorTick);
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
		return text(gameTime, intervalTicks, 0L);
	}

	/** {@link #text(long, int)} 과 같되 <b>켜진 시점부터</b> 센다. */
	public static String text(long gameTime, int intervalTicks, long anchorTick) {
		if (intervalTicks <= 0) {
			return "";
		}
		return format(remainingSeconds(gameTime, intervalTicks, anchorTick));
	}
}
