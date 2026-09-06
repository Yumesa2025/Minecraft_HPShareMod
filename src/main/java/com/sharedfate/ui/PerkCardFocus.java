package com.sharedfate.ui;

/**
 * 증강이 정해진 뒤 <b>고른 카드가 가운데로 옮겨 오는</b> 움직임의 계산.
 *
 * <p>{@link PerkCardDismiss} 와 짝이다. 그쪽이 탈락한 카드를 내려보내는 동안 이쪽은 남은 한
 * 장을 화면 가운데로 데려온다.
 *
 * <p>{@link PerkCardDismiss} 와 <b>반대로</b> ease-out 을 쓴다.
 *
 * <p>카드가 다 내려가는 데 걸리는 시간({@link PerkCardDismiss#SLIDE_MILLIS})보다 짧게 잡았다.
 */
public final class PerkCardFocus {
	/** 고른 카드가 가운데까지 오는 데 걸리는 시간(ms). */
	public static final long MOVE_MILLIS = 300L;

	private PerkCardFocus() {
	}

	/**
	 * 카드가 얼마나 옮겨 왔는지 0.0~1.0 으로. 곡선을 먹인 <b>뒤</b>의 값이다.
	 *
	 * @param elapsedMillis 결과가 정해진 뒤 지난 시간(ms)
	 */
	public static float progress(long elapsedMillis) {
		if (elapsedMillis <= 0L) {
			return 0.0F;
		}
		if (elapsedMillis >= MOVE_MILLIS) {
			return 1.0F;
		}
		float linear = (float) elapsedMillis / (float) MOVE_MILLIS;
		float remaining = 1.0F - linear;
		// ease-out: 빠르게 출발해 가운데에서 부드럽게 멈춘다.
		return 1.0F - remaining * remaining * remaining;
	}

	/**
	 * 지금 카드를 그릴 가로 위치.
	 *
	 * @param fromLeft 결과가 정해지기 전 이 카드가 서 있던 자리
	 * @param toLeft   가운데 자리
	 */
	public static int left(long elapsedMillis, int fromLeft, int toLeft) {
		if (fromLeft == toLeft) {
			return toLeft;
		}
		return Math.round(fromLeft + (toLeft - fromLeft) * progress(elapsedMillis));
	}
}
