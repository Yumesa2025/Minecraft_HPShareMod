package com.sharedfate.ui;

/**
 * 증강 선택 화면 위에 덮는 <b>「보유 증강」 모달</b>의 자리 계산.
 *
 * <h2>왜 화면 가운데인가</h2>
 * <p>모달이다. 뒤의 화면을 가라앉히고 그 위에 한 장 띄운다 — 지금 할 일은 <b>목록을 읽는
 * 것뿐</b>이고, 카드는 그동안 못 고른다. 왼쪽 열에 세우면 곁판처럼 보여서 「읽는 동안에는
 * 아무것도 못 한다」가 전해지지 않는다.
 *
 * <h2>왜 화면을 갈아타지 않는가</h2>
 * <p>선택 화면은 <b>강제 선택 세션</b>이 띄운 것이다. 다른 화면으로 갔다가 돌아오는 길이
 * 하나라도 어긋나면 카드를 영영 못 고른다. 그래서 같은 화면 위에 덮는다.
 *
 * <h2>지키는 것</h2>
 * <ol>
 *   <li><b>화면을 벗어나지 않는다.</b> 가로·세로 모두 주어진 자리 안으로 자른다.</li>
 *   <li><b>너무 작으면 열지 않는다.</b> 잘린 글자를 보여 주느니 단추를 감추는 편이 낫다 —
 *       {@link PerkSetPanelLayout} 이 판을 통째로 감추는 것과 같은 규칙이다.</li>
 *   <li><b>가운데 선다.</b> 카드가 몇 장이든 자리가 흔들리지 않는다.</li>
 * </ol>
 *
 * @param left       모달의 왼쪽 변
 * @param top        모달의 윗변
 * @param width      모달의 가로
 * @param height     모달의 세로
 * @param lineHeight 글줄 하나의 높이
 * @param padding    테두리와 글자 사이 여백
 */
public record OwnedPerkPanelLayout(int left, int top, int width, int height, int lineHeight,
		int padding) {

	/** 이만큼도 안 되면 열지 않는다. 머리글 한 줄과 목록 두 줄은 들어가야 뜻이 있다. */
	public static final int MIN_WIDTH = 120;

	/** 세로 최소. 머리글 + 목록 두 줄 + 위아래 여백. */
	public static final int MIN_HEIGHT = 60;

	/** 화면 가로에서 차지할 몫. 남은 폭이 넉넉해도 이보다 넓히지 않는다. */
	private static final double WIDTH_RATIO = 0.6D;

	/** 화면 세로에서 차지할 몫. */
	private static final double HEIGHT_RATIO = 0.7D;

	/** 이보다 넓으면 읽기 힘들다. 글줄이 길어지면 눈이 다음 줄을 못 찾는다. */
	private static final int MAX_WIDTH = 320;

	/** 아무것도 그리지 않는 모달. */
	public static OwnedPerkPanelLayout hidden() {
		return new OwnedPerkPanelLayout(0, 0, 0, 0, 0, 0);
	}

	/**
	 * 화면 한가운데 들어가는 만큼의 모달을 만든다.
	 *
	 * @param screenWidth  화면 가로
	 * @param screenHeight 화면 세로
	 * @param margin       화면 가장자리에 반드시 남길 여백
	 * @param lineHeight   글줄 하나의 높이
	 * @param padding      테두리와 글자 사이 여백
	 */
	public static OwnedPerkPanelLayout fit(int screenWidth, int screenHeight, int margin,
			int lineHeight, int padding) {
		int roomWidth = screenWidth - margin * 2;
		int roomHeight = screenHeight - margin * 2;
		if (roomWidth < MIN_WIDTH || roomHeight < MIN_HEIGHT) {
			return hidden();
		}
		int width = Math.min(Math.min(MAX_WIDTH, roomWidth), (int) (screenWidth * WIDTH_RATIO));
		int height = Math.min(roomHeight, (int) (screenHeight * HEIGHT_RATIO));
		// 비율로 줄인 값이 최소에 못 미칠 수 있다. 자리가 있으면 최소까지는 늘린다.
		width = Math.max(MIN_WIDTH, Math.min(width, roomWidth));
		height = Math.max(MIN_HEIGHT, Math.min(height, roomHeight));
		if (width > roomWidth || height > roomHeight) {
			return hidden();
		}
		return new OwnedPerkPanelLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
				width, height, lineHeight, padding);
	}

	/** 그릴 것이 있는가. */
	public boolean visible() {
		return width > 0 && height > 0;
	}

	public int right() {
		return left + width;
	}

	public int bottom() {
		return top + height;
	}

	/** 글자가 시작하는 왼쪽 변. */
	public int contentLeft() {
		return left + padding;
	}

	/** 글자가 넘으면 안 되는 오른쪽 변. */
	public int contentRight() {
		return right() - padding;
	}

	/** 글줄을 접을 때 쓸 폭. 오른쪽에 스크롤 막대 자리를 남긴다. */
	public int wrapWidth(int scrollbarWidth) {
		return Math.max(8, contentRight() - contentLeft() - scrollbarWidth);
	}

	/** 머리글을 그릴 y. */
	public int headerY() {
		return top + padding;
	}

	/** 목록이 시작하는 y. 머리글 아래 구분선까지 지난 자리다. */
	public int listTop() {
		return headerY() + lineHeight + 4;
	}

	/** 목록이 넘으면 안 되는 아랫변. */
	public int listBottom() {
		return bottom() - padding;
	}

	/** 목록이 보이는 창의 세로. */
	public int viewHeight() {
		return Math.max(0, listBottom() - listTop());
	}
}
