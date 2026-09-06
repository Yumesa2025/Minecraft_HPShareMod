package com.sharedfate.ui;

/**
 * 선택자를 뽑는 굴림 연출의 <b>진행 상태</b>. 이름을 언제 넘기고 언제 결과를 알릴지 정한다.
 *
 * <h2>굴림과 결과 표시를 나눈다</h2>
 * <p>총 길이는 서버가 정한다({@code PerkChoiceSession.DRAW_TICKS}). 그 시간이 다 지나야 서버가
 * 선택창을 보내므로 <b>총 길이는 늘릴 수도 줄일 수도 없다.</b> 대신 그 안을 둘로 나눈다.
 *
 * <ul>
 *   <li><b>굴림</b> {@link #rollTicks(int)} — 이름이 빠르게 바뀌다 점점 느려진다.</li>
 *   <li><b>결과 표시</b> {@link #REVEAL_TICKS} — 뽑힌 이름을 붙잡아 두는 마지막 몇 틱.</li>
 * </ul>
 *
 * <p>간격은
 * {@link #FIRST_STEP_TICKS} 에서 {@link #LAST_STEP_TICKS} 까지 <b>굴림 길이에 대한 비율</b>로
 * 늘어나므로, 길이를 바꿔도 굴리는 횟수와 리듬은 그대로고 속도만 달라진다.
 */
public final class PerkDrawRoll {
	/**
	 * 처음 간격(틱)과 마지막 간격(틱). 뒤로 갈수록 이 사이를 오간다.
	 *
	 * <p>처음 간격은 2틱이다. 초당 10번은 이미 사람이 글자를 읽어낼 수 있는 한계라, 1틱으로
	 * 줄이면 빨라지는 것이 아니라 그냥 뭉개진다.
	 */
	public static final int FIRST_STEP_TICKS = 2;
	public static final int LAST_STEP_TICKS = 8;

	/**
	 * 뽑힌 이름을 붙잡아 두는 시간(틱). 0.6초.
	 *
	 * <p>더 늘리지 않는 이유는 이 시간이 <b>굴림에서 떼어 오는</b> 것이기 때문이다. 총 길이는
	 * 서버가 쥐고 있어 늘릴 수 없다. 0.75초를 넘기면 70틱짜리 연출의 굴림이 55틱 아래로
	 * 내려가고, 그때부터는 마지막 간격 8틱 하나가 굴림의 15%를 차지해 끝에서만 늘어진다.
	 */
	public static final int REVEAL_TICKS = 12;

	/** 한 틱에 일어난 일. 셋은 서로 배타적이다 — 특히 이름 교체와 확정은 절대 겹치지 않는다. */
	public enum Event {
		/** 아무 일도 없다. */
		NOTHING,
		/** 이름을 다음 것으로 넘길 차례다. 굴림 소리를 낸다. */
		NEXT_NAME,
		/**
		 * 굴림이 끝났다. 뽑힌 이름을 보여 주고 확정 소리를 낸다.
		 *
		 * <p><b>연출 하나에 정확히 한 번만</b> 나온다. 총 길이가 얼마든 반드시 한 번은 나온다.
		 */
		REVEAL
	}

	private final int totalTicks;
	private final int rollTicks;

	private int elapsedTicks;
	private int ticksUntilNextName = FIRST_STEP_TICKS;
	/** 남은 굴림 시간이 다음 간격보다 짧아 마지막 이름을 붙잡고 있는 중. */
	private boolean holdingLastName;
	/** 결과를 이미 알렸는지. 두 번 알리지 않게 하는 유일한 장치다. */
	private boolean revealAnnounced;

	public PerkDrawRoll(int totalTicks) {
		this.totalTicks = Math.max(1, totalTicks);
		this.rollTicks = rollTicks(this.totalTicks);
	}

	/**
	 * 총 길이 가운데 <b>굴리는 데</b> 쓰는 틱. 나머지가 결과 표시에 쓰인다.
	 *
	 * <p>총 길이가 짧을 때도 최소한 절반은 굴린다. 굴림이 결과 표시보다 짧아지면 굴린 것으로
	 * 보이지 않고 그냥 이름 하나가 떴다 사라지는 화면이 된다. 그래서 결과 표시가 총 길이의
	 * 절반을 넘게 가져가지 못한다.
	 *
	 * <p>어떤 값을 넣어도 1 이상이고 총 길이 이하다. 이 두 가지가 {@link Event#REVEAL} 이
	 * <b>반드시 한 번은</b> 나온다는 보장의 근거다.
	 */
	public static int rollTicks(int totalTicks) {
		int total = Math.max(1, totalTicks);
		return Math.max((total + 1) / 2, total - REVEAL_TICKS);
	}

	/**
	 * 지금 시점의 이름 교체 간격. 끝으로 갈수록 길어진다.
	 *
	 * <p>분모가 총 길이가 아니라 <b>굴림 길이</b>다. 그래야 굴림이 끝나는 지점에서 간격이
	 * 정확히 {@link #LAST_STEP_TICKS} 에 닿아, 길이를 바꿔도 감속의 모양이 같다.
	 */
	public static int stepTicks(int elapsedTicks, int rollTicks) {
		float progress = rollTicks <= 0
				? 1.0F : Math.clamp((float) elapsedTicks / rollTicks, 0.0F, 1.0F);
		return Math.max(1, Math.round(
				FIRST_STEP_TICKS + (LAST_STEP_TICKS - FIRST_STEP_TICKS) * progress));
	}

	/**
	 * 한 틱 진행시키고 이번 틱에 일어난 일을 돌려준다.
	 *
	 * <p>결과 알림을 <b>이름 교체보다 먼저</b> 가른다. 어떤 길이로 돌려도
	 * 굴림이 끝나는 그 틱에 반드시 한 번 나온다.
	 *
	 * <p>먼저 가르는 덕에 굴림 소리와 확정 소리가 한 틱에 겹칠 일도 없다.
	 */
	public Event tick() {
		if (elapsedTicks >= totalTicks) {
			return Event.NOTHING;
		}
		elapsedTicks++;

		if (elapsedTicks >= rollTicks) {
			if (revealAnnounced) {
				return Event.NOTHING;
			}
			revealAnnounced = true;
			return Event.REVEAL;
		}

		if (holdingLastName || --ticksUntilNextName > 0) {
			return Event.NOTHING;
		}
		int step = stepTicks(elapsedTicks, rollTicks);
		if (elapsedTicks + step > rollTicks) {
			// 다음 이름이 제 간격을 다 채우지 못하고 결과에 잘린다. 넘기지 않고 지금 이름을
			// 결과가 나올 때까지 붙잡아 둔다.
			holdingLastName = true;
			return Event.NOTHING;
		}
		ticksUntilNextName = step;
		return Event.NEXT_NAME;
	}

	/** 굴림이 끝나 뽑힌 이름을 보여 줄 때인지. */
	public boolean revealed() {
		return elapsedTicks >= rollTicks;
	}

	/** 굴림이 얼마나 진행됐는지 0.0~1.0 으로. 굴림 소리의 음을 올리는 데 쓴다. */
	public float progress() {
		if (rollTicks <= 0) {
			return 1.0F;
		}
		return Math.clamp((float) elapsedTicks / rollTicks, 0.0F, 1.0F);
	}
}
