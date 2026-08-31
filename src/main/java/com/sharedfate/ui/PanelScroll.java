package com.sharedfate.ui;

/**
 * 세로로 긴 목록 하나를 창 안에 가둘 때 쓰는 스크롤 계산.
 *
 * <p>화면 코드는 {@code src/client} 에 있는데 시험 소스셋은 그쪽을 보지 못한다. 그래서
 * <b>그리기와 무관한 순수 계산만</b> 공용 소스셋인 여기로 내려 두었다. 이 파일에는
 * 마인크래프트 클래스가 하나도 들어오지 않으므로 서버에서도 그냥 죽은 코드로 남는다.
 *
 * <p>단위는 전부 화면 픽셀(GUI 좌표)이다. {@code offset} 은 "목록을 위로 얼마나 밀어
 * 올렸는가"이고 0이면 맨 위, {@link #maxOffset} 이면 맨 아래다.
 */
public final class PanelScroll {
	private PanelScroll() {
	}

	/**
	 * 밀어 올릴 수 있는 최대 거리.
	 *
	 * <p>내용이 창보다 짧으면 0이다. 그래서 짧은 목록은 스크롤 자체가 성립하지 않는다.
	 */
	public static int maxOffset(int contentHeight, int viewHeight) {
		return Math.max(0, contentHeight - Math.max(0, viewHeight));
	}

	/** 내용이 창을 넘쳐 스크롤이 필요한지. */
	public static boolean overflows(int contentHeight, int viewHeight) {
		return maxOffset(contentHeight, viewHeight) > 0;
	}

	/**
	 * 스크롤 값을 유효 범위로 자른다.
	 *
	 * <p>목록이 줄거나 창이 커져 스크롤이 범위를 벗어나면 <b>빈 화면</b>이 보인다. 값을 바꾼
	 * 뒤든 배치를 다시 잰 뒤든 언제나 이 함수를 한 번 거치게 해서 그 상태를 만들지 않는다.
	 */
	public static int clamp(int offset, int contentHeight, int viewHeight) {
		return Math.max(0, Math.min(offset, maxOffset(contentHeight, viewHeight)));
	}

	/**
	 * 스크롤 막대 손잡이의 길이.
	 *
	 * <p>보이는 만큼의 비율로 잡되 {@code minimum} 아래로는 줄이지 않는다. 목록이 아주 길면
	 * 비율대로는 1픽셀이 되어 손잡이가 사라져 버린다.
	 */
	public static int thumbHeight(int contentHeight, int viewHeight, int minimum) {
		if (!overflows(contentHeight, viewHeight)) {
			return Math.max(0, viewHeight);
		}
		int proportional = (int) ((long) viewHeight * viewHeight / contentHeight);
		return Math.max(Math.min(minimum, viewHeight), Math.min(viewHeight, proportional));
	}

	/**
	 * 스크롤 막대 손잡이의 윗변 좌표.
	 *
	 * <p>{@code offset} 이 0이면 홈 맨 위, 최대면 홈 맨 아래에 딱 맞는다. 넘칠 것이 없으면
	 * 홈 맨 위를 돌려준다.
	 */
	public static int thumbTop(int trackTop, int viewHeight, int thumbHeight,
			int offset, int contentHeight) {
		int max = maxOffset(contentHeight, viewHeight);
		if (max <= 0) {
			return trackTop;
		}
		int room = Math.max(0, viewHeight - thumbHeight);
		int clamped = clamp(offset, contentHeight, viewHeight);
		return trackTop + (int) ((long) room * clamped / max);
	}
}
