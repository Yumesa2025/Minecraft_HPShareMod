package com.sharedfate.ui;

/**
 * 증강이 정해진 뒤 <b>고른 카드가 가운데로 옮겨 오는</b> 움직임의 계산.
 *
 * <p>{@link PerkCardDismiss} 와 짝이다. 그쪽이 탈락한 카드를 내려보내는 동안 이쪽은 남은 한
 * 장을 화면 가운데로 데려온다. 같은 이유로 여기 있다 — 실제로 그리는 일은 {@code src/client}
 * 의 {@code PerkOfferScreen} 이 하지만 시험 소스셋이 그쪽을 보지 못하므로 <b>시간에 따른
 * 가로 위치와 강조 세기만</b> 공용 소스셋으로 내려 두었다.
 *
 * <h2>왜 필요한가</h2>
 * <p>예전에는 결과가 정해지는 순간 고른 카드를 <b>가운데 좌표로 바로 옮겨</b> 그렸다. 왼쪽이나
 * 오른쪽 카드가 골라졌을 때 그 카드가 한 프레임 만에 순간이동을 하니, 옆 두 장이 사라지는 것과
 * 겹쳐 "화면이 통째로 다른 것으로 바뀌었다"로 읽혔다. 눈이 카드를 <b>따라가지 못하면</b> 무엇이
 * 정해졌는지 다시 읽어야 하고, 그 시간만큼 결과를 보는 시간이 깎인다.
 *
 * <p>움직여서 데려오면 눈이 카드를 놓치지 않는다. 가운데 도착했을 때 이미 그 카드를 보고
 * 있으므로 남은 시간은 전부 이름과 설명을 읽는 데 쓰인다.
 *
 * <h2>곡선</h2>
 * <p>{@link PerkCardDismiss} 와 <b>반대로</b> ease-out 을 쓴다. 떠나는 카드는 가속해야
 * "떨어진다"로 읽히지만, 도착하는 카드는 감속해야 "자리를 잡았다"로 읽힌다. 둘이 같은 곡선을
 * 쓰면 세 장이 한 방향으로 밀려나는 것처럼 보인다.
 *
 * <h2>길이</h2>
 * <p>카드가 다 내려가는 데 걸리는 시간({@link PerkCardDismiss#SLIDE_MILLIS})보다 짧게 잡았다.
 * 옆 카드가 아직 내려가는 중에 고른 카드가 먼저 자리를 잡아야, 마지막에 남는 그림이
 * <b>가운데 카드 한 장</b>으로 또렷하게 정리된다.
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
