package com.sharedfate.ui;

/**
 * 플레이어 인벤토리 화면 왼쪽에 붙는 SharedFate 단추와 그 아래 능력치의 자리.
 *
 * <p>{@link GameStartButton}·{@link PanelScroll} 과 같은 이유로 여기 있다 — 화면
 * ({@code InventoryScreenTeamButtonMixin})은 {@code src/client} 에 있어 시험 소스셋이 볼 수
 * 없으므로 <b>순수 계산만</b> 공용 소스셋으로 내려 둔다.
 *
 * <h2>단추에 무엇을 적는가</h2>
 * <p>예전에는 「팀」 한 글자였다. 20×20 안에 들어가는 유일한 길이였지만, <b>한 글자로는 무엇이
 * 열리는지 알 수 없었다.</b> 지금은 모드 이름을 그대로 적는다. 이 단추가 여는 창은 팀만이
 * 아니라 현황·설정·증강·능력치를 모두 담은 <b>이 모드의 창 하나</b>이고, 그 창의 제목이 이미
 * 「SharedFate 팀」이라 단추와 창이 같은 것으로 읽힌다. 「모드 설정」이라고 적지 않은 이유도
 * 같다 — 그 창에서 설정은 다섯 탭 중 하나일 뿐이라 이름이 내용보다 좁다.
 *
 * <p>글자가 길어졌으므로 단추도 넓어져야 한다. 폭은 못 박지 않고 <b>글자를 실제로 재서</b>
 * 정한다({@link #buttonWidth}) — 자원팩이 폰트를 바꾸거나 다른 언어로 옮겨도 글자가 넘치지
 * 않는다.
 *
 * <h2>왜 아이콘이 아니라 글자인가</h2>
 * <p>이 모드가 가진 그림은 {@code assets/sharedfate/icon.png} 하나뿐이다. 아이콘을 쓰려면
 * 눌림·올려놓음 세 가지 상태의 스프라이트를 새로 그려 넣어야 하는데, 팀 화면·증강 화면이 모두
 * 바닐라 {@code Button} 에 한글을 얹은 모습이라 <b>아이콘 하나만 이 모드에서 튄다.</b>
 *
 * <h2>왜 창 바깥 왼쪽인가</h2>
 * <p>창 <b>안</b>에는 빈자리가 없다. 왼쪽 위는 방어구 칸과 플레이어 미리보기, 오른쪽 위는
 * 조합칸, 아래는 인벤토리와 이 모드가 더한 추가 27칸이 전부 차지한다. 창 밖을 눌러도
 * 들고 있던 아이템이 떨어지지는 않는다 — {@code AbstractContainerScreen.mouseClicked} 는
 * 위젯에게 먼저 물어보고, 위젯이 먹은 누름은 「창 밖을 눌렀다」 판정까지 가지 않는다.
 *
 * <p>오른쪽은 쓸 수 없다. 상태이상 목록이 {@code leftPos + imageWidth + 2} 부터 그려진다
 * ({@code EffectsInInventory}). 왼쪽에는 조합법 책만 있고, 그것은 {@link #anchorLeft} 가 피한다.
 */
public final class InventoryTeamButton {
	/** 단추 높이. 바닐라 단추와 같다. */
	public static final int HEIGHT = 20;
	/** 단추와 창(또는 조합법 책) 사이의 틈. */
	public static final int GAP = 4;

	/**
	 * 단추 글자 좌우에 두는 여백. 바닐라 {@code Button} 이 글자를 잘라 내는 여백(2px)의 두
	 * 배씩이라, 테두리와 글자가 붙어 보이지 않는다.
	 */
	public static final int LABEL_PADDING = 8;

	/**
	 * 아무리 좁아도 이만큼은 남긴다. 눌 수 있는 최소한의 크기다.
	 *
	 * <p>여기까지 줄어들면 바닐라 단추가 글자를 잘라 보여 준다
	 * ({@code AbstractWidget.renderScrollingString}). 글자가 삐져나가 창을 덮는 일은 없다.
	 */
	public static final int MIN_WIDTH = 20;

	/** 단추에 적는 글자. */
	public static final String LABEL = "SharedFate";
	/** 올려놓으면 뜨는 설명. 단추 글자가 말하지 않는 「무엇이 들었는지」를 여기서 적는다. */
	public static final String TOOLTIP = "현황 · 팀 · 설정 · 증강 · 능력치 (/st)";

	/** 조합법 책 판의 너비. 바닐라 {@code RecipeBookComponent.IMAGE_WIDTH}. */
	private static final int RECIPE_BOOK_WIDTH = 147;
	/** 조합법 책이 화면 가운데에서 왼쪽으로 물러나는 거리. 바닐라 {@code OFFSET_X_POSITION}. */
	private static final int RECIPE_BOOK_OFFSET_X = 86;

	private InventoryTeamButton() {
	}

	/**
	 * 단추가 피해야 할 왼쪽 끝. 창의 왼쪽이거나, 조합법 책이 펼쳐져 있으면 그 판의 왼쪽이다.
	 *
	 * <h2>조합법 책이 펼쳐졌는지를 {@code leftPos} 로 안다</h2>
	 * <p>바닐라 {@code RecipeBookComponent.updateScreenPosition} 은 <b>책이 보이고 화면이 넓을
	 * 때만</b> 창을 오른쪽으로 밀어 책이 들어갈 자리를 만든다. 그래서 창 좌표가 가운데 정렬
	 * 값보다 오른쪽에 있으면 왼쪽에 책이 펼쳐져 있다는 뜻이다. 책 자체를 들여다볼 필요가 없다.
	 *
	 * <p>화면이 좁아 책이 창을 <b>덮는</b> 경우({@code widthTooNarrow})에는 창이 밀리지 않으므로
	 * 여기서도 창 기준으로 남는데, 그때 책은 {@code (화면폭 − 147) / 2} 부터 오른쪽으로 그려져
	 * 창 왼쪽보다 오른쪽에 있다. 겹치지 않는다.
	 */
	public static int anchorLeft(int screenWidth, int imageWidth, int leftPos) {
		int centered = (screenWidth - imageWidth) / 2;
		if (leftPos <= centered) {
			return leftPos;
		}
		return (screenWidth - RECIPE_BOOK_WIDTH) / 2 - RECIPE_BOOK_OFFSET_X;
	}

	/**
	 * 단추와 능력치가 함께 쓸 수 있는 가로 폭.
	 *
	 * <p>0 이하가 나올 수 있다 — 화면 폭이 379 언저리이면 조합법 책이 화면 가운데를 다 먹어
	 * 왼쪽에 서른 픽셀도 안 남는다. 부르는 쪽이 그 경우를 다뤄야 한다.
	 */
	public static int available(int screenWidth, int imageWidth, int leftPos) {
		return anchorLeft(screenWidth, imageWidth, leftPos) - GAP;
	}

	/**
	 * 글자 폭에 맞춘 단추 폭. 남은 자리보다는 넓어지지 않는다.
	 *
	 * @param labelWidth 폰트가 잰 {@link #LABEL} 의 폭
	 * @param available  {@link #available} 이 돌려준 값
	 */
	public static int buttonWidth(int labelWidth, int available) {
		int wanted = Math.max(MIN_WIDTH, labelWidth + LABEL_PADDING);
		if (available < MIN_WIDTH) {
			return MIN_WIDTH;
		}
		return Math.min(wanted, available);
	}

	/**
	 * 단추와 줄들을 묶은 덩어리의 왼쪽 x.
	 *
	 * <p>덩어리의 <b>오른쪽 끝</b>을 창(또는 펼친 책)에 붙이고 왼쪽으로 펼친다. 화면 밖으로는
	 * 나가지 않는다 — 0 으로 잘리는 것은 왼쪽에 자리가 거의 없는 아주 좁은 화면뿐이고, 그때는
	 * 능력치가 이미 {@link InventoryStatPanel.Style#HIDDEN} 이라 덩어리가 단추 하나뿐이다.
	 *
	 * @param available   {@link #available} 이 돌려준 값
	 * @param blockWidth  단추 폭과 가장 긴 능력치 줄 중 넓은 쪽
	 */
	public static int blockLeft(int available, int blockWidth) {
		return Math.max(0, available - Math.max(0, blockWidth));
	}

	/**
	 * 단추의 y. 창 위 끝에 맞춘다.
	 *
	 * <p>조합법 책 단추는 {@code height / 2 − 22} — 창 한가운데 언저리다. 위 끝에 두면 세로로도
	 * 멀찍이 떨어져 둘을 헷갈릴 일이 없고, 이 모드가 창을 아래로 54px 늘려도
	 * ({@code ExpandedInventoryManager.EXTRA_PANEL_HEIGHT}) 창 위 끝은 늘 창 안이다.
	 */
	public static int y(int topPos) {
		return Math.max(0, topPos);
	}

	/**
	 * 능력치 줄들이 쓸 수 있는 세로 높이.
	 *
	 * <p>단추 아래부터 화면 바닥까지다. 인벤토리 창 <b>왼쪽 바깥</b>에는 바닐라가 아무것도
	 * 그리지 않으므로 아래로는 화면 끝까지 쓸 수 있다. 화면 세로는 바닐라가 240 아래로는
	 * 내려가지 않게 GUI 배율을 스스로 낮추므로, 창(220px)을 빼도 늘 백 픽셀 넘게 남는다.
	 */
	public static int statHeight(int screenHeight, int topPos) {
		return screenHeight - (y(topPos) + HEIGHT + InventoryStatPanel.BUTTON_GAP);
	}

	/** 능력치 첫 줄의 y. */
	public static int statTop(int topPos) {
		return y(topPos) + HEIGHT + InventoryStatPanel.BUTTON_GAP;
	}
}
