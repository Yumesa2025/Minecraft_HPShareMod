package com.sharedfate.ui;

/**
 * 플레이어 인벤토리 화면 왼쪽에 붙는 SharedFate 단추와 그 아래 능력치의 자리.
 *
 * <p>폭은 못 박지 않고 <b>글자를 실제로 재서</b>
 * 정한다({@link #buttonWidth}) — 자원팩이 폰트를 바꾸거나 다른 언어로 옮겨도 글자가 넘치지
 * 않는다.
 *
 * <h2>줄에 서는 단추는 팀이 있느냐에 따라 달라진다</h2>
 * <p>팀이 있으면 {@link #LABEL} 과 {@link #PERK_LABEL} 둘이 나란히 서고, 팀이 없으면
 * {@link #CREATE_LABEL} 하나만 선다. <b>줄 폭을 재는 규칙은 하나다</b> — 맨 앞 단추가
 * {@link #buttonWidth} 로 남은 자리 안에서 제 폭을 잡고, 뒤에 붙는 단추만
 * {@link #perkButtonWidth} 로 남은 것을 넘겨받는다. 하나뿐일 때는 뒤가 없으니 두 번째 계산을
 * 부르지 않는 것이 전부다.
 *
 * <p>창 <b>안</b>에는 빈자리가 없다. 왼쪽 위는 방어구 칸과 플레이어 미리보기, 오른쪽 위는
 * 조합칸, 아래는 인벤토리와 이 모드가 더한 추가 27칸이 전부 차지한다. 창 밖을 눌러도
 * 들고 있던 아이템이 떨어지지는 않는다 — {@code AbstractContainerScreen.mouseClicked} 는
 * 위젯에게 먼저 물어보고, 위젯이 먹은 누름은 「창 밖을 눌렀다」 판정까지 가지 않는다.
 *
 * <p>오른쪽은 쓸 수 없다. 상태이상 목록이 {@code leftPos + imageWidth + 2} 부터 그려진다
 * ({@code EffectsInInventory}). 왼쪽에는 조합법 책만 있고, 그것은 {@link #anchorLeft} 가 피한다.
 *
 * <h2>화면 왼쪽 위는 HUD 가 먼저 쓴다</h2>
 * <p>인벤토리를 열어도 HUD 는 계속 그려진다 — {@code Gui.extractRenderState} 가 HUD 를 먼저
 * 그리고 그 위에 화면을 얹는다. 그래서 좌표·바이옴과 그 아래 세트 줄이 <b>인벤토리 화면 위에도
 * 그대로 남는다.</b> 이 덩어리는 그 아래에서 시작한다({@link #TOP_LIMIT}).
 */
public final class InventoryTeamButton {
	/** 단추 높이. 바닐라 단추와 같다. */
	public static final int HEIGHT = 20;
	/** 단추와 창(또는 조합법 책) 사이의 틈. */
	public static final int GAP = 4;

	/**
	 * HUD 좌표·바이옴 두 줄이 끝나는 자리. {@code client.hud.CoordinateHud.NEXT_LINE_Y} 다.
	 *
	 * <p>그 화면은 {@code src/client} 에 있어 여기서 부를 수 없으므로 값을 옮겨 적는다.
	 * 어긋나면 {@code InventoryTeamButtonTest} 가 먼저 터진다.
	 */
	public static final int HUD_NEXT_LINE_Y = 24;

	/**
	 * 세트 줄 위에 놓이는 구분선 덩어리의 높이.
	 *
	 * <p>바이옴 줄과 선 사이의 틈 3 + 선 자체 1 + 선과 첫 세트 줄 사이의 틈 3 이다.
	 */
	public static final int HUD_SEPARATOR_HEIGHT = 7;

	/** HUD 세트 한 줄의 높이. {@code client.hud.BottomLeftStack.LINE_HEIGHT} 와 같다. */
	public static final int HUD_LINE_HEIGHT = 10;

	/**
	 * 세트 줄이 <b>가장 많이 그려질 때</b> HUD 덩어리의 아래끝.
	 *
	 * <p>세트 줄 수는 0~{@link PerkSetLines#MAX_HUD_LINES} 사이에서 변하지만 여기서는 늘
	 * 최댓값으로 잡는다. 지금 그려지는 줄 수에 맞춰 움직이면 증강 하나를 새 유형에서 뽑는
	 * 순간 능력치 전체가 한 줄만큼 미끄러지고, <b>같은 값이 늘 같은 자리에 있다</b>는 것이
	 * 이 표시의 값어치다. 최댓값으로 잡아 두면 줄이 몇 개든 자리가 흔들리지 않는다.
	 */
	public static final int HUD_BOTTOM = HUD_NEXT_LINE_Y + HUD_SEPARATOR_HEIGHT
			+ PerkSetLines.MAX_HUD_LINES * HUD_LINE_HEIGHT;

	/** HUD 덩어리와 이 덩어리 사이에 두는 틈. */
	public static final int HUD_GAP = 4;

	/**
	 * 덩어리가 올라갈 수 있는 가장 위.
	 *
	 * <p>이보다 위는 HUD 몫이다. 세트 줄은 화면 왼쪽 끝({@code MARGIN} 4)부터 그려지고 이
	 * 덩어리도 창 왼쪽 바깥이라, 둘의 가로 범위는 좁은 화면에서 거의 통째로 겹친다. 겹침을
	 * 가로로 피할 길이 없으므로 세로로 가른다.
	 */
	public static final int TOP_LIMIT = HUD_BOTTOM + HUD_GAP;

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

	/**
	 * 옆에 붙는 두 번째 단추의 글자. 누르면 증강 탭이 펴진 채로 열린다.
	 *
	 * <p>그냥 「증강」이 아니라 <b>「현재 증강」</b>이다. 「증강」만 적으면 증강을 <b>고르는</b>
	 * 창처럼 읽힌다 — 이 단추가 여는 것은 지금 가진 것을 보는 목록이다. 선택 화면의 같은
	 * 단추와도 글자를 맞춘다.
	 */
	public static final String PERK_LABEL = "현재 증강";
	public static final String PERK_TOOLTIP = "지금 가진 증강 목록을 바로 엽니다";

	/**
	 * 팀이 없을 때 위의 둘을 <b>모두 밀어내고</b> 혼자 서는 단추.
	 *
	 * <p>팀이 없으면 위의 둘은 빈 화면을 연다 — 「SharedFate」는 「팀에 속해 있지 않습니다」
	 * 한 줄이고, 「현재 증강」은 가진 것이 없으니 빈 목록이다. 그 자리에 <b>지금 할 수 있는
	 * 단 하나</b>를 놓는다.
	 *
	 * <p>「팀 만들기」가 아니라 <b>「팀 생성」</b>이다. 팀 화면 안의 양식에 붙은 단추가
	 * 「팀 만들기」라서, 같은 글자를 쓰면 이 단추를 누르는 것으로 팀이 만들어지는 줄 안다.
	 * 이것은 <b>양식을 여는</b> 단추다.
	 */
	public static final String CREATE_LABEL = "팀 생성";
	public static final String CREATE_TOOLTIP = "팀을 만드는 화면을 바로 엽니다";

	/** 두 단추 사이의 틈. */
	public static final int BUTTON_GAP = 2;

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
	 * <h2>줄에서 <b>맨 앞</b>에 서는 단추가 쓴다</h2>
	 * <p>팀이 있으면 그것은 {@link #LABEL} 이고, 팀이 없으면 {@link #CREATE_LABEL} 이다.
	 * 둘 다 뒤에 무엇이 오는지와 무관하게 <b>남은 자리 전부를 놓고</b> 잰다 — 뒤 단추 몫을
	 * 떼어 두는 일은 {@link #perkButtonWidth} 한 곳에서만 한다. 그래서 「팀 생성」 하나만
	 * 그릴 때 여기에 따로 손댈 것이 없다.
	 *
	 * <p>이 단추는 <b>0 을 돌려주지 않는다.</b> 자리가 {@link #MIN_WIDTH} 보다 좁아도 그만큼은
	 * 남긴다. 맨 앞 단추까지 사라지면 그 화면에서 팀 화면으로 가는 길이 끊기고, 팀이 없을
	 * 때는 팀을 만들 길까지 끊긴다. 글자는 바닐라가 알아서 잘라 보여 준다.
	 *
	 * @param labelWidth 폰트가 잰 맨 앞 단추 글자의 폭
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
	 * 「증강」 단추의 폭. 자리가 모자라면 <b>0</b> 이고, 그때는 단추를 아예 만들지 않는다.
	 *
	 * <p>두 단추는 한 줄에 나란히 서고 그 줄의 오른쪽 끝이 창에 붙는다. 그래서 먼저 선
	 * {@link #LABEL} 단추가 쓰고 남은 폭 안에 들어가야 한다. 좁은 화면에서 억지로 끼우면
	 * 둘 다 글자가 잘려 무엇을 누르는지 알 수 없게 되므로, 그때는 하나만 남긴다.
	 *
	 * @param labelWidth 폰트가 잰 {@link #PERK_LABEL} 의 폭
	 * @param available  {@link #available} 이 돌려준 값
	 * @param teamWidth  {@link #buttonWidth} 가 돌려준 값
	 */
	public static int perkButtonWidth(int labelWidth, int available, int teamWidth) {
		int wanted = Math.max(MIN_WIDTH, labelWidth + LABEL_PADDING);
		int room = available - teamWidth - BUTTON_GAP;
		if (room < wanted) {
			return 0;
		}
		return wanted;
	}

	/**
	 * 두 단추가 한 줄로 차지하는 폭. 「증강」 단추가 없으면 첫 단추 폭 그대로다.
	 *
	 * @param teamWidth {@link #buttonWidth} 가 돌려준 값
	 * @param perkWidth {@link #perkButtonWidth} 가 돌려준 값. 0이면 단추가 없다
	 */
	public static int buttonRowWidth(int teamWidth, int perkWidth) {
		return perkWidth <= 0 ? teamWidth : teamWidth + BUTTON_GAP + perkWidth;
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
	 * 덩어리 맨 위, 곧 단추의 y. 창 위 끝과 {@link #TOP_LIMIT} 중 <b>아래쪽</b>이다.
	 *
	 * <p>창 위 끝에 맞추는 것이 본디 모양이다. 조합법 책 단추는 {@code height / 2 − 22} — 창
	 * 한가운데 언저리라 위 끝에 두면 세로로도 멀찍이 떨어지고, 이 모드가 창을 아래로 54px
	 * 늘려도({@code ExpandedInventoryManager.EXTRA_PANEL_HEIGHT}) 창 위 끝은 늘 창 안이다.
	 *
	 * <p>다만 GUI 배율이 크면 화면이 짧아져 창이 위로 올라붙고, 그러면 창 위 끝이 HUD 세트
	 * 줄 한가운데에 놓인다. 그때는 {@link #TOP_LIMIT} 까지 내린다. <b>단추도 함께 내린다</b> —
	 * 단추만 남겨 두면 「◆ 채굴 3/3」 위에 겹쳐 앉아 무엇을 누르는지 알 수 없게 되고, 능력치
	 * 줄과 폭·왼쪽을 함께 잡는 한 덩어리라는 계산도 깨진다.
	 *
	 * <p>배율 1~3 처럼 화면이 넉넉하면 창 위 끝이 이미 {@link #TOP_LIMIT} 보다 아래라 아무것도
	 * 달라지지 않는다.
	 */
	public static int y(int topPos) {
		return Math.max(TOP_LIMIT, topPos);
	}

	/**
	 * 능력치 줄들이 쓸 수 있는 세로 높이.
	 *
	 * <p>단추 아래부터 화면 바닥까지다. 인벤토리 창 <b>왼쪽 바깥</b>에는 바닐라가 아무것도
	 * 그리지 않으므로 아래로는 화면 끝까지 쓸 수 있다.
	 *
	 * <p>HUD 를 피해 내려온 만큼 세로가 줄어든다. 화면 세로는 바닐라가 240 아래로 내려가지
	 * 않게 GUI 배율을 스스로 낮추므로 가장 짧은 화면에서도 121px 이 남고, 한 줄짜리 여덟 줄
	 * (84px)은 어느 배율에서도 들어간다. <b>두 줄로 접은 여덟 줄(164px)은 화면 세로가 283
	 * 아래면 못 들어간다</b> — 그때는 {@link InventoryStatPanel.Style#HIDDEN} 이 되어 단추만
	 * 남는다. HUD 가 위쪽 91px 을 쓰는 이상 240px 짜리 화면에 접은 여덟 줄과 세트 줄을 함께
	 * 세울 자리는 없고, 겹쳐 그려 둘 다 못 읽느니 한쪽을 접는 편이 낫다.
	 */
	public static int statHeight(int screenHeight, int topPos) {
		return screenHeight - (y(topPos) + HEIGHT + InventoryStatPanel.BUTTON_GAP);
	}

	/** 능력치 첫 줄의 y. */
	public static int statTop(int topPos) {
		return y(topPos) + HEIGHT + InventoryStatPanel.BUTTON_GAP;
	}
}
