package com.sharedfate.ui;

import java.util.Locale;

/**
 * 팀 만들기 화면의 숫자 설정 셋을 「누를 때마다 다음 값」으로 굴리는 계산.
 *
 * <p>{@link PanelScroll}·{@link PerkGauge} 와 같은 이유로 여기 있다 — 단추를 그리는 일은
 * 클라이언트 소스셋의 {@code TeamScreen} 이 하지만, 시험 소스셋이 그쪽을 보지 못하므로
 * <b>순수 계산만</b> 공용 소스셋으로 내려 두었다.
 *
 * <h2>왜 −/+ 두 단추가 아니라 굴림인가</h2>
 * <p>팀 만들기 탭에 정할 것이 일곱 가지로 늘었다. 숫자마다 −/+ 를 두면 단추만 열넷이라
 * 창 높이를 넘긴다. 한 단추가 값을 품고 굴러가면 줄 수가 그대로다.
 *
 * <h2>여기서 정한 값은 결국 명령 한 줄이 된다</h2>
 * <p>{@link #createCommand} 가 그 한 줄을 만든다. 서버의 {@code /shareteam create} 가 읽는
 * 순서와 낱말이 <b>정확히</b> 같아야 하므로, 그 형식을 화면 코드가 아니라 시험할 수 있는
 * 이 자리에 둔다.
 */
public final class TeamCreationCycle {
	/** 위치 교환 「끔」. 서버의 {@code TeamCreationSettings.SWAP_DISABLED} 와 같은 뜻이다. */
	public static final int SWAP_OFF = 0;

	/**
	 * 위치 교환 주기가 굴러가는 자리들(분).
	 *
	 * <p>1~120 을 1분씩 굴리면 120번을 눌러야 한다. 실제로 쓰는 값만 골라 뒀고, 명령은
	 * 여전히 1~120 아무 값이나 받으므로 여기 없는 주기를 원하면 명령으로 적으면 된다.
	 */
	private static final int[] SWAP_STEPS = {SWAP_OFF, 1, 5, 10, 15, 20, 30, 45, 60, 90, 120};

	private TeamCreationCycle() {
	}

	/**
	 * 최대 체력을 한 칸 올린다. 위 끝을 넘으면 아래 끝으로 돌아온다.
	 *
	 * @param current 지금 값
	 * @param min     아래 끝(20)
	 * @param max     위 끝(40)
	 * @param step    한 번에 올릴 양(2). 0 이하면 1로 본다
	 */
	public static int nextMaxHealth(int current, int min, int max, int step) {
		int safeStep = Math.max(1, step);
		int clamped = Math.max(min, Math.min(max, current));
		int next = clamped + safeStep;
		return next > max ? min : next;
	}

	/** 위치 교환 주기를 다음 자리로 굴린다. 마지막(120분) 다음은 「끔」이다. */
	public static int nextSwapMinutes(int current) {
		for (int index = 0; index < SWAP_STEPS.length; index++) {
			if (SWAP_STEPS[index] == current) {
				return SWAP_STEPS[(index + 1) % SWAP_STEPS.length];
			}
		}
		// 명령으로 적은 어중간한 값에서 굴리기 시작한 경우다. 바로 위의 자리로 올린다.
		for (int step : SWAP_STEPS) {
			if (step > current) {
				return step;
			}
		}
		return SWAP_OFF;
	}

	/** 다시 뽑기 횟수를 하나 올린다. 위 끝을 넘으면 아래 끝으로 돌아온다. */
	public static int nextRerollCount(int current, int min, int max) {
		int clamped = Math.max(min, Math.min(max, current));
		return clamped >= max ? min : clamped + 1;
	}

	/**
	 * 위치 교환 값을 단추에 적을 글자로. 끔이면 「끔」, 아니면 「5분」.
	 *
	 * <p>「5분 주기」로 길게 적지 않는 이유는 자리 때문이다. 이 단추는 팀 만들기 탭에서 판
	 * 절반(148px)만 쓰므로, 「위치 교환 — 120분 주기」까지 가면 글자가 단추를 넘친다.
	 */
	public static String swapLabel(int minutes) {
		return minutes == SWAP_OFF ? "끔" : minutes + "분";
	}

	/** 명령에 적을 위치 교환 값. 끔이면 {@code off}, 아니면 분 숫자 그대로. */
	public static String swapArgument(int minutes) {
		return minutes == SWAP_OFF ? "off" : String.valueOf(minutes);
	}

	/**
	 * 화면이 정한 값들을 {@code /shareteam} 뒤에 붙일 한 줄로 만든다.
	 *
	 * <p><b>일곱 가지를 하나도 빼지 않고 적는다.</b> 안 적은 항목은 서버가 기본값으로 두는데,
	 * 화면에는 이미 다른 값이 보이고 있을 수 있어 눈에 보이는 것과 실제가 어긋난다.
	 *
	 * <p>낱말 순서는 {@code ShareTeamCommand.createNode} 가 쌓아 둔 순서와 같아야 한다 —
	 * 이름이 greedyString 이라 <b>모든 설정이 이름 앞</b>에 정해진 차례로 와야 하기 때문이다.
	 *
	 * @param name 팀 이름. 앞뒤 공백은 부르는 쪽에서 이미 다듬어 넘긴다
	 */
	public static String createCommand(boolean perks, boolean damageAlert, boolean deathAlert,
			boolean difficulty, int maxHealth, int swapMinutes, int rerollCount, String name) {
		return "create perks " + onOff(perks)
				+ " damagealert " + onOff(damageAlert)
				+ " deathalert " + onOff(deathAlert)
				+ " difficulty " + onOff(difficulty)
				+ " health " + maxHealth
				+ " swap " + swapArgument(swapMinutes)
				+ " reroll " + rerollCount
				+ " " + name;
	}

	/** 명령이 읽는 켜고 끄기 낱말. 화면에 보이는 한국어와 달리 {@code on}/{@code off} 다. */
	public static String onOff(boolean value) {
		return value ? "on" : "off";
	}

	/** 20.0 처럼 소수점이 의미 없는 값을 "20" 으로 보여 준다. */
	public static String trimZero(float value) {
		return value == Math.rint(value)
				? String.valueOf((long) value)
				: String.format(Locale.ROOT, "%.1f", value);
	}
}
