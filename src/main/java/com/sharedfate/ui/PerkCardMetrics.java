package com.sharedfate.ui;

/**
 * 증강 선택 카드 한 장의 <b>세로 길이</b> 계산.
 *
 * <p>{@link PerkCardFocus}·{@link PerkCardDismiss} 와 같은 이유로 공용 소스셋에 있다 —
 * 카드를 그리는 {@code client/perk/PerkOfferScreen} 은 {@code src/client} 라 시험 소스셋이
 * 보지 못한다.
 *
 * <h2>이 계산이 틀리면 무엇이 보이는가</h2>
 * <p>카드 안쪽은 {@code enableScissor} 로 잘린다. 그래서 <b>덜 세면 아무 오류 없이 설명 마지막
 * 줄이 사라진다.</b> 카드가 짧아졌다는 것도, 무언가 잘렸다는 것도 화면에 아무 표시가 없다.
 * 그리는 쪽에 줄을 하나 더하면서 여기를 함께 고치지 않는 것이 이 화면에서 가장 흔한 사고라,
 * 계산을 밖으로 빼서 <b>줄이 늘면 높이도 는다</b>를 시험으로 못 박아 둔다.
 *
 * <p>세로로 쌓이는 차례는 그리는 차례와 같다.
 *
 * <pre>
 * 등급 띠            bandHeight
 *                   iconGapTop
 * 아이콘             iconSize + iconGapBottom   (아이콘을 안 그리면 통째로 0)
 * 이름               nameLines × lineHeight
 *                   setTypeGap                 (세트 유형이 있을 때만)
 * 세트 유형           setTypeLines × lineHeight
 * 구분선             separatorBlock
 * 설명               descriptionLines × lineHeight
 *                   padding
 * </pre>
 *
 * @param bandHeight     카드 맨 위 등급 띠의 높이
 * @param iconGapTop     등급 띠와 아이콘 사이 여백. 아이콘이 없어도 남는다
 * @param iconGapBottom  아이콘과 이름 사이 여백
 * @param setTypeGap     이름과 세트 유형 줄 사이의 틈. 유형이 없으면 쓰지 않는다
 * @param separatorBlock 이름 묶음과 설명 사이 구분선이 차지하는 세로 공간
 * @param padding        카드 맨 아래에 남길 여백
 * @param lineHeight     글줄 하나의 높이
 */
public record PerkCardMetrics(int bandHeight, int iconGapTop, int iconGapBottom, int setTypeGap,
		int separatorBlock, int padding, int lineHeight) {

	/**
	 * 이 카드가 필요로 하는 세로 길이.
	 *
	 * @param iconSize         아이콘 한 변. 0 이면 아이콘 자리를 아예 쓰지 않는다
	 * @param nameLines        접어 둔 이름 줄 수
	 * @param setTypeLines     접어 둔 세트 유형 줄 수. 0 이면 위의 틈도 없다
	 * @param descriptionLines 접어 둔 설명 줄 수
	 */
	public int height(int iconSize, int nameLines, int setTypeLines, int descriptionLines) {
		int lines = Math.max(0, nameLines) + Math.max(0, setTypeLines)
				+ Math.max(0, descriptionLines);
		int iconBlock = iconSize > 0 ? iconSize + iconGapBottom : 0;
		int setTypeBlock = setTypeLines > 0 ? setTypeGap : 0;
		return bandHeight + iconGapTop + iconBlock + setTypeBlock
				+ lines * lineHeight + separatorBlock + padding;
	}
}
