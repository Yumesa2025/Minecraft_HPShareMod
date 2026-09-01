package com.sharedfate.ui;

/**
 * 플레이어 인벤토리 화면 왼쪽에 붙는 「팀」 단추의 자리와 글자.
 *
 * <p>{@link GameStartButton}·{@link PanelScroll} 과 같은 이유로 여기 있다 — 화면
 * ({@code InventoryScreenTeamButtonMixin})은 {@code src/client} 에 있어 시험 소스셋이 볼 수
 * 없으므로 <b>순수 계산만</b> 공용 소스셋으로 내려 둔다.
 *
 * <h2>왜 글자 단추인가</h2>
 * <p>이 모드가 가진 그림은 {@code assets/sharedfate/icon.png} 하나뿐이다. 아이콘을 쓰려면
 * 눌림·올려놓음 세 가지 상태의 스프라이트를 새로 그려 넣어야 하는데, 팀 화면·증강 화면이 모두
 * 바닐라 {@code Button} 에 한글을 얹은 모습이라 <b>아이콘 하나만 이 모드에서 튄다.</b>
 * 대신 크기를 {@value #WIDTH}×{@value #HEIGHT} 로 잡아 바닐라 조합법 책 단추(20×18)와 거의
 * 같게 두었다 — 두 단추가 같은 급의 형제로 읽힌다. 무엇을 여는 단추인지는 툴팁이 적는다.
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
	/** 단추 크기. 바닐라 조합법 책 단추(20×18)와 나란히 놓아도 어색하지 않은 값이다. */
	public static final int WIDTH = 20;
	public static final int HEIGHT = 20;
	/** 단추와 창(또는 조합법 책) 사이의 틈. */
	public static final int GAP = 4;

	/** 단추에 적는 글자. 한 글자라야 20px 안에 여백을 남기고 들어간다. */
	public static final String LABEL = "팀";
	/** 올려놓으면 뜨는 설명. 글자 한 자로는 무엇을 여는지 알 수 없으므로 여기에 다 적는다. */
	public static final String TOOLTIP = "SharedFate 팀 화면을 엽니다 (/shareteam)";

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
	 * 단추의 x. 화면 밖으로는 나가지 않는다.
	 *
	 * <p>0 으로 잘리는 것은 창이 화면을 거의 가득 채우는 아주 좁은 화면뿐이고, 그때도 단추는
	 * 창 왼쪽 가장자리에 걸릴 뿐 칸을 덮지 않는다.
	 */
	public static int x(int screenWidth, int imageWidth, int leftPos) {
		return Math.max(0, anchorLeft(screenWidth, imageWidth, leftPos) - GAP - WIDTH);
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
}
