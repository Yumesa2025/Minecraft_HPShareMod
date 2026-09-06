package com.sharedfate.ui;

/**
 * 증강이 정해진 뒤 <b>안 고른 카드가 아래로 미끄러져 내려가는</b> 움직임의 계산.
 *
 * <p>결과를 보여 주는 시간은 전부 5초({@code PerkChoiceSession.RESULT_TICKS})다. 이 움직임은
 * 그 <b>앞머리에서만</b> 끝나야 한다. {@link #SLIDE_MILLIS} 와 {@link #STAGGER_MILLIS}
 * 를 합쳐도 0.8초를 넘지 않게 잡았고, 남는 4초 이상이 고른 카드를 보는 시간이다.
 *
 * <p>진행도를 <b>제곱</b>해서 쓴다(ease-in).
 *
 * <p>왼쪽 카드부터 조금씩 시차를 두고 떠난다.
 */
public final class PerkCardDismiss {
	/** 카드 한 장이 다 내려가는 데 걸리는 시간(ms). */
	public static final long SLIDE_MILLIS = 520L;
	/** 카드마다 출발을 미루는 간격(ms). 왼쪽 카드가 먼저 떠난다. */
	public static final long STAGGER_MILLIS = 110L;

	/**
	 * 다 내려갔을 때 카드에 덮이는 어둠의 세기.
	 *
	 * <p>카드를 <b>투명하게</b> 만들지 않고 <b>어둡게</b> 만든다. 화면 전체에 이미 어둠이 깔려
	 * 있어서 결과는 거의 같아 보이지만, 이쪽은 아이템 아이콘까지 함께 가라앉는다. 아이콘을
	 * 그리는 통로에는 투명도를 받는 자리가 없어서, 진짜 투명도를 쓰면 배경과 글자만 옅어지고
	 * <b>아이콘만 또렷하게 떠 있는</b> 이상한 그림이 된다.
	 */
	public static final float SHADE_MAX = 0.85F;

	/**
	 * 어둠이 가장 짙어지는 시점(진행도).
	 *
	 * <p>끝까지 끌지 않고 조금 일찍 다 짙어지게 한다. 마지막 구간은 이미 화면 아래끝을 지나
	 * 잘려 나가는 중이라 더 짙게 해도 보이지 않고, 대신 중간쯤에서 확실히 가라앉아야
	 * 고른 카드와 눈에 띄게 갈린다.
	 */
	private static final float SHADE_FULL_AT = 0.8F;

	private PerkCardDismiss() {
	}

	/** {@code order} 번째 카드가 움직이기 시작하는 시각(결과가 정해진 뒤 ms). */
	public static long startMillis(int order) {
		return Math.max(0, order) * STAGGER_MILLIS;
	}

	/**
	 * 이 카드가 얼마나 내려갔는지 0.0~1.0 으로. 곡선을 먹이기 전의 <b>날 진행도</b>다.
	 *
	 * @param elapsedMillis 결과가 정해진 뒤 지난 시간(ms)
	 * @param order         왼쪽부터 센 카드 자리. 시차의 근거다
	 */
	public static float progress(long elapsedMillis, int order) {
		long moved = elapsedMillis - startMillis(order);
		if (moved <= 0L) {
			return 0.0F;
		}
		if (moved >= SLIDE_MILLIS) {
			return 1.0F;
		}
		return (float) moved / (float) SLIDE_MILLIS;
	}

	/**
	 * 지금 카드를 제자리에서 아래로 얼마나 밀어야 하는지(픽셀).
	 *
	 * @param travel 다 내려갔을 때의 거리. 화면 아래끝을 지나도록 부르는 쪽이 정한다
	 */
	public static int offset(long elapsedMillis, int order, int travel) {
		if (travel <= 0) {
			return 0;
		}
		float progress = progress(elapsedMillis, order);
		// ease-in: 처음엔 천천히, 뒤로 갈수록 빠르게. 떨어지는 것과 같은 가속이다.
		return Math.round(travel * progress * progress);
	}

	/** 지금 카드 위에 덮을 어둠의 세기 0.0~{@link #SHADE_MAX}. */
	public static float shade(long elapsedMillis, int order) {
		float progress = progress(elapsedMillis, order);
		return SHADE_MAX * Math.clamp(progress / SHADE_FULL_AT, 0.0F, 1.0F);
	}

	/** 이 카드가 다 내려가 더 그릴 것이 없는지. */
	public static boolean gone(long elapsedMillis, int order) {
		return elapsedMillis >= startMillis(order) + SLIDE_MILLIS;
	}

	/**
	 * 카드 {@code cardCount} 장짜리 창에서 마지막 한 장까지 다 내려가는 데 걸리는 시간(ms).
	 *
	 * <p>고른 카드도 자리는 차지하므로 자리 수를 그대로 넣는다. 결과를 보여 주는 시간과 견줘
	 * 얼마나 앞에서 끝나는지 재는 데 쓴다.
	 */
	public static long totalMillis(int cardCount) {
		return startMillis(Math.max(1, cardCount) - 1) + SLIDE_MILLIS;
	}
}
