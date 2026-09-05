package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 스크롤되는 목록에서 <b>마우스가 어느 항목 위에 있는가</b>를 재는 계산.
 *
 * <p>팀 화면의 보유 증강 목록이 쓴다. 그 목록은 항목 하나가 여러 줄이고(이름 한 줄 + 접힌 설명
 * 몇 줄), 창보다 길어지면 스크롤된다. 그래서 화면 좌표를 그대로 줄 번호로 나눌 수 없다.
 *
 * <h2>화면 좌표와 목록 좌표</h2>
 * <p>둘을 헷갈리면 <b>스크롤한 뒤에 엉뚱한 증강의 툴팁이 뜬다.</b> 여기서는 이렇게 나눈다.
 *
 * <ul>
 *   <li><b>화면 좌표</b> — 마우스가 오는 값. 창의 윗변이 {@code viewTop} 이다.</li>
 *   <li><b>목록 좌표</b> — 항목의 자리({@link Span})가 사는 값. 맨 위 항목이 0 이고
 *       스크롤과 무관하다.</li>
 * </ul>
 *
 * <p>둘 사이는 {@code 목록 = 화면 − viewTop + scroll} 이다. 그리는 쪽도 같은 식을 쓴다
 * ({@code TeamScreen} 의 {@code lineY = top − perkScroll}).
 *
 * <h2>창 밖은 반드시 먼저 떨어뜨린다</h2>
 * <p>스크롤된 목록은 창 위아래로 <b>이미 그려지지 않는 부분</b>을 가지고 있다(그리는 쪽은
 * {@code enableScissor} 로 자른다). 그 부분까지 마우스를 받으면 <b>안 보이는 증강의 툴팁이
 * 뜬다</b> — 심지어 머리글이나 아래 단추 위에서 뜬다. 그래서 목록 좌표로 옮기기 <b>전에</b>
 * 창 범위를 먼저 본다.
 */
public final class PerkListHover {

	private PerkListHover() {
	}

	/**
	 * 항목 하나가 목록 좌표에서 차지하는 자리.
	 *
	 * @param top    항목의 윗변. 맨 위 항목이 0
	 * @param height 항목의 세로 길이. 이름 줄과 설명 줄을 모두 더한 값이다
	 */
	public record Span(int top, int height) {

		public int bottom() {
			return top + height;
		}

		/** 목록 좌표 {@code y} 가 이 항목 안인가. 높이가 0 인 항목은 아무것도 받지 않는다. */
		public boolean contains(int y) {
			return height > 0 && y >= top && y < bottom();
		}
	}

	/**
	 * 높이 목록을 위에서부터 쌓아 자리로 바꾼다.
	 *
	 * <p>목록을 접을 때 항목마다 몇 px 을 썼는지 세어 두었다가 그대로 넘기면 된다. <b>그리기와
	 * 마우스 판정이 같은 숫자를 봐야</b> 하므로, 여기에 넣는 높이는 그릴 때 더한 높이와 한
	 * 글자도 다르면 안 된다.
	 */
	public static List<Span> stack(List<Integer> heights) {
		if (heights == null || heights.isEmpty()) {
			return List.of();
		}
		List<Span> spans = new ArrayList<>(heights.size());
		int top = 0;
		for (Integer height : heights) {
			int value = height == null ? 0 : Math.max(0, height);
			spans.add(new Span(top, value));
			top += value;
		}
		return List.copyOf(spans);
	}

	/**
	 * 마우스가 올라가 있는 항목의 차례. 어느 항목에도 없으면 −1.
	 *
	 * @param left       목록이 마우스를 받는 왼쪽 끝
	 * @param width      목록이 마우스를 받는 가로 길이
	 * @param viewTop    목록 창의 윗변(화면 좌표)
	 * @param viewHeight 목록 창의 세로 길이. 이 아래는 잘려서 안 보인다
	 * @param scroll     목록을 위로 밀어 올린 거리. 0 이면 맨 위
	 */
	public static int indexAt(List<Span> spans, double mouseX, double mouseY, int left, int width,
			int viewTop, int viewHeight, int scroll) {
		if (spans == null || spans.isEmpty() || width <= 0 || viewHeight <= 0) {
			return -1;
		}
		if (mouseX < left || mouseX >= left + width) {
			return -1;
		}
		// 창 밖은 그려지지도 않는다. 목록 좌표로 옮기기 전에 떨어뜨려야 한다.
		if (mouseY < viewTop || mouseY >= viewTop + viewHeight) {
			return -1;
		}
		int listY = (int) Math.floor(mouseY - viewTop) + scroll;
		for (int index = 0; index < spans.size(); index++) {
			Span span = spans.get(index);
			if (span != null && span.contains(listY)) {
				return index;
			}
		}
		return -1;
	}
}
