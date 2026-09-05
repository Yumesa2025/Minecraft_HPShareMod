package com.sharedfate.ui;

/**
 * 증강 선택 화면 <b>왼쪽</b>에 세우는 「지금 켜진 세트」 판의 자리 계산.
 *
 * <p>{@link PerkCardMetrics} 와 같은 이유로 공용 소스셋에 있다 — 이 판을 그리는
 * {@code client/perk/PerkOfferScreen} 은 {@code src/client} 라 시험 소스셋이 보지 못한다.
 *
 * <h2>이 계산이 지키는 것</h2>
 * <p><b>카드를 절대 가리지 않는다.</b> 카드 세 장의 자리는 화면이 먼저 정하고
 * ({@code PerkOfferScreen.init}), 이 계산은 <b>그러고 남은 폭</b>만 받는다. 남은 폭이
 * 모자라면 순서대로 물러난다.
 *
 * <ol>
 *   <li>다 들어가면 머리글까지 그린다.</li>
 *   <li>머리글이 제일 긴 줄이라 넘칠 때는 <b>머리글을 접는다</b> — 세트 줄만 남는다.</li>
 *   <li>세트 줄조차 안 들어가면 <b>판을 통째로 감춘다</b>. 잘린 글자를 보여 주느니 아무것도
 *       안 보여 주는 편이 낫다.</li>
 * </ol>
 *
 * <p>세로도 같다. 아래 안내 문구를 넘길 만큼 줄이 많으면 들어가는 만큼만 남긴다.
 * 줄 차례는 {@link PerkSetLines#visible} 이 이미 <b>켜진 것을 위로</b> 올려 두었으므로,
 * 잘려 나가는 것은 언제나 가장 덜 모은 유형이다.
 *
 * @param left      판의 왼쪽 변
 * @param top       판의 윗변
 * @param width     판의 가로. 테두리 안쪽 여백까지 포함한 값이다
 * @param rowCount  실제로 그릴 세트 줄 수. 0 이면 판을 그리지 않는다
 * @param header    「지금 켜진 세트」 머리글을 그릴지
 * @param lineHeight 글줄 하나의 높이
 * @param padding   판 테두리와 글자 사이 여백
 * @param headerGap 머리글과 첫 세트 줄 사이의 틈
 */
public record PerkSetPanelLayout(int left, int top, int width, int rowCount, boolean header,
		int lineHeight, int padding, int headerGap) {

	/**
	 * 판이 쓸 수 있는 자리와 치수.
	 *
	 * <p>{@code right} 는 <b>카드 왼쪽 변에서 틈만큼 물러난 자리</b>다. 화면이 이 값을 정확히
	 * 넘겨 주면 판은 그 선을 넘지 않는다.
	 *
	 * @param left       판을 세울 왼쪽 변
	 * @param right      판이 넘으면 안 되는 오른쪽 한계
	 * @param top        판의 윗변
	 * @param bottom     판이 넘으면 안 되는 아랫변
	 * @param lineHeight 글줄 하나의 높이
	 * @param padding    판 안쪽 여백
	 * @param headerGap  머리글과 첫 줄 사이의 틈
	 */
	public record Room(int left, int right, int top, int bottom, int lineHeight, int padding,
			int headerGap) {

		/** 판이 쓸 수 있는 가로. 음수면 0. */
		public int availableWidth() {
			return Math.max(0, right - left);
		}

		/** 판이 쓸 수 있는 세로. 음수면 0. */
		public int availableHeight() {
			return Math.max(0, bottom - top);
		}
	}

	/** 아무것도 그리지 않는 판. */
	public static PerkSetPanelLayout hidden() {
		return new PerkSetPanelLayout(0, 0, 0, 0, false, 0, 0, 0);
	}

	/**
	 * 주어진 자리에 들어가는 만큼의 판을 만든다.
	 *
	 * @param rowCount    그리고 싶은 세트 줄 수
	 * @param widestRow   가장 긴 세트 줄의 글자 폭
	 * @param headerWidth 머리글의 글자 폭
	 */
	public static PerkSetPanelLayout fit(Room room, int rowCount, int widestRow, int headerWidth) {
		if (room == null || rowCount <= 0 || room.lineHeight() <= 0) {
			return hidden();
		}
		int padding = Math.max(0, room.padding());
		int compact = Math.max(0, widestRow) + padding * 2;
		int full = Math.max(Math.max(0, widestRow), Math.max(0, headerWidth)) + padding * 2;

		boolean header;
		int width;
		if (room.availableWidth() >= full) {
			header = true;
			width = full;
		} else if (room.availableWidth() >= compact) {
			header = false;
			width = compact;
		} else {
			return hidden();
		}

		int inner = room.availableHeight() - padding * 2;
		int headerGap = Math.max(0, room.headerGap());
		int rows = (inner - (header ? room.lineHeight() + headerGap : 0)) / room.lineHeight();
		if (rows <= 0 && header) {
			// 머리글을 접으면 한 줄이라도 들어가는지 다시 본다. 세로가 빠듯한 화면에서 판이
			// 통째로 사라지는 것보다 켜진 세트 한 줄이라도 남는 편이 낫다.
			header = false;
			width = compact;
			rows = inner / room.lineHeight();
		}
		rows = Math.min(rowCount, rows);
		if (rows <= 0) {
			return hidden();
		}
		return new PerkSetPanelLayout(room.left(), room.top(), width, rows, header,
				room.lineHeight(), padding, headerGap);
	}

	/** 그릴 것이 있는가. */
	public boolean visible() {
		return rowCount > 0 && width > 0;
	}

	/** 판의 오른쪽 변. <b>카드 왼쪽 변보다 작아야 한다.</b> */
	public int right() {
		return left + width;
	}

	/** 판이 차지하는 세로 길이. */
	public int height() {
		if (!visible()) {
			return 0;
		}
		return padding * 2 + headerBlock() + rowCount * lineHeight;
	}

	/** 판의 아랫변. */
	public int bottom() {
		return top + height();
	}

	/** 머리글과 그 아래 틈이 차지하는 세로 길이. 머리글을 접었으면 0. */
	public int headerBlock() {
		return header ? lineHeight + headerGap : 0;
	}

	/** 글자가 시작하는 왼쪽 변. */
	public int contentLeft() {
		return left + padding;
	}

	/** 글자가 쓸 수 있는 가로. */
	public int contentWidth() {
		return Math.max(0, width - padding * 2);
	}

	/** 머리글의 윗변. 머리글을 접었으면 첫 줄과 같은 자리다. */
	public int headerY() {
		return top + padding;
	}

	/** 첫 세트 줄의 윗변. */
	public int rowsTop() {
		return top + padding + headerBlock();
	}

	/** {@code index} 번째 세트 줄의 윗변. */
	public int rowY(int index) {
		return rowsTop() + index * lineHeight;
	}

	/**
	 * 마우스가 올라가 있는 세트 줄의 차례. 어느 줄에도 없으면 −1.
	 *
	 * <p>가로 범위를 글자 자리로 좁히는 이유는 {@link PerkSetLines#rowAt} 에 적힌 것과 같다 —
	 * 판 전체가 마우스를 받으면 왼쪽 어디에 두어도 툴팁이 떠 카드를 읽을 수 없다.
	 */
	public int rowAt(double mouseX, double mouseY) {
		if (!visible()) {
			return -1;
		}
		return PerkSetLines.rowAt(mouseX, mouseY, contentLeft(), contentWidth(), rowsTop(),
				lineHeight, rowCount);
	}
}
