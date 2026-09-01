package com.sharedfate.ui;

/**
 * 증강이 정해진 뒤 <b>안 고른 카드가 아래로 미끄러져 내려가는</b> 움직임의 계산.
 *
 * <p>{@link PanelScroll}·{@link PerkGauge} 와 같은 이유로 여기 있다 — 실제로 그리는 일은
 * {@code src/client} 의 {@code PerkOfferScreen} 이 하지만 시험 소스셋이 그쪽을 보지 못하므로
 * <b>시간에 따른 위치·어둠만</b> 공용 소스셋으로 내려 두었다. 마인크래프트 클래스는 하나도
 * 들어오지 않는다.
 *
 * <h2>왜 그냥 지우지 않는가</h2>
 * <p>예전에는 결과가 정해지는 순간 안 고른 카드를 <b>그리지 않는 것</b>으로 끝냈다. 한 프레임
 * 만에 두 장이 없어지니 무엇이 사라졌는지 눈이 따라가지 못하고, 남은 한 장이 가운데로 옮겨
 * 가는 것과 겹쳐 "화면이 갑자기 바뀌었다"로만 읽혔다. 내려보내면 <b>탈락했다</b>는 뜻이
 * 움직임 자체에 담긴다.
 *
 * <h2>시간</h2>
 * <p>결과를 보여 주는 시간은 전부 5초({@code PerkChoiceSession.RESULT_TICKS})다. 이 움직임은
 * 그 <b>앞머리에서만</b> 끝나야 한다 — 카드가 다 내려간 뒤 고른 카드만 남은 화면을 충분히
 * 볼 시간이 있어야 강조가 뜻을 갖는다. 그래서 {@link #SLIDE_MILLIS} 와 {@link #STAGGER_MILLIS}
 * 를 합쳐도 0.8초를 넘지 않게 잡았고, 남는 4초 이상이 고른 카드를 보는 시간이다.
 *
 * <p>예전에는 한 장에 0.38초, 시차 0.07초였다. 세 장이 0.52초 만에 다 사라지는데 뒤에 붙는
 * 가속까지 겹쳐, 무엇이 떨어져 나갔는지 알아보기 전에 화면에서 없어졌다. 지금은 한 장이
 * 0.52초를 쓰고 시차도 0.11초로 벌려서, 세 장이 <b>한 덩어리로 사라지지 않고</b> 왼쪽부터
 * 차례로 떠나는 것이 눈에 보인다. 결과 시간이 5초라 0.74초를 써도 앞머리에 그대로 들어간다.
 *
 * <h2>곡선</h2>
 * <p>진행도를 <b>제곱</b>해서 쓴다(ease-in). 떨어지는 물체와 같은 가속이라 "내려간다"가 가장
 * 자연스럽게 읽히고, 처음이 느려서 <b>어느 카드가 떠나는지</b>를 눈이 먼저 잡는다. 반대로
 * ease-out 을 쓰면 출발이 빨라 그냥 사라진 것처럼 보이고, 등속은 화면 UI 라기보다 밀려나는
 * 판때기처럼 보인다.
 *
 * <p>왼쪽 카드부터 조금씩 시차를 두고 떠난다. 창이 열릴 때 카드가 왼쪽부터 차례로 올라오므로
 * 나갈 때도 같은 방향이어야 한 화면의 규칙으로 읽힌다.
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
