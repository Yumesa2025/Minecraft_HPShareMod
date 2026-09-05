package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 증강 카드의 <b>세트 유형 줄</b>을 다루는 계산 — 어디에 놓이고, 마우스가 그 위에 있는지,
 * 그리고 <b>화면용 이름을 유형 id 로 되돌리는 일</b>.
 *
 * <h2>왜 이름을 id 로 되돌려야 하는가</h2>
 * <p>{@code PerkOfferPayload.PerkOption.setTypes()} 는 서버가 <b>화면용 이름</b>을
 * {@code ·} 로 이어 붙인 한 줄이다({@code "무기·화력"}). 그런데 툴팁을 물어보는 자리
 * ({@code ClientPerkSets.tooltip}) 는 <b>id</b>({@code mining}·{@code power})를 받는다.
 * 그래서 이름을 그대로 넘기면 언제나 빈 툴팁이 나온다.
 *
 * <p>여기서는 세트 동기화 패킷이 실어 준 이름표({@link PerkSetLines.Entry}) 를 뒤져 이름을
 * id 로 되돌린다. <b>임시 다리다.</b> 이름이 겹치는 유형이 생기거나 세트 패킷이 아직 안 온
 * 순간에는 되돌릴 수 없다. 페이로드가 id 를 함께 실어 주면 이 되돌리기는 통째로 버려야 한다.
 */
public final class PerkCardSetTypes {

	private PerkCardSetTypes() {
	}

	/**
	 * 이어 붙은 한 줄을 유형 이름들로 가른다.
	 *
	 * <p>빈 조각은 버린다 — 유형이 하나뿐인 증강에 이음쇠가 잘못 붙어 와도 빈 이름으로
	 * 툴팁을 묻는 일이 없어야 한다.
	 */
	public static List<String> split(String joined, String joiner) {
		if (joined == null || joined.isEmpty() || joiner == null || joiner.isEmpty()) {
			return joined == null || joined.isBlank() ? List.of() : List.of(joined.trim());
		}
		List<String> names = new ArrayList<>();
		for (String piece : joined.split(java.util.regex.Pattern.quote(joiner))) {
			String trimmed = piece.trim();
			if (!trimmed.isEmpty()) {
				names.add(trimmed);
			}
		}
		return List.copyOf(names);
	}

	/**
	 * 화면용 이름에 맞는 유형 id. 못 찾으면 빈 문자열.
	 *
	 * <p>못 찾는 것은 정상이다 — 세트 패킷이 아직 안 왔거나 서버가 이 유형을 안 쓰는 경우다.
	 * 그때는 툴팁을 아예 띄우지 않는다.
	 */
	public static String typeId(List<PerkSetLines.Entry> entries, String displayName) {
		if (entries == null || displayName == null || displayName.isEmpty()) {
			return "";
		}
		String wanted = displayName.trim();
		for (PerkSetLines.Entry entry : entries) {
			if (entry != null && wanted.equals(entry.displayName())) {
				return entry.typeId();
			}
		}
		return "";
	}

	/**
	 * 이어 붙은 한 줄을 유형 id 들로 되돌린다. 못 되돌린 이름은 빠진다.
	 *
	 * @param joiner {@code PerkOfferPayload.PerkOption.SET_TYPE_JOINER}
	 */
	public static List<String> typeIds(String joined, String joiner,
			List<PerkSetLines.Entry> entries) {
		List<String> ids = new ArrayList<>();
		for (String name : split(joined, joiner)) {
			String id = typeId(entries, name);
			if (!id.isEmpty() && !ids.contains(id)) {
				ids.add(id);
			}
		}
		return List.copyOf(ids);
	}

	/**
	 * 카드 윗변에서 <b>세트 유형 첫 줄</b>까지의 거리.
	 *
	 * <p>{@code PerkOfferScreen.renderCard} 가 쌓는 차례를 그대로 따라간다 — 등급 띠 → 아이콘 →
	 * 이름 → 유형 앞 틈. <b>그리는 차례를 바꾸면 여기도 함께 바꿔야 한다.</b> 안 그러면 마우스를
	 * 받는 자리와 글자가 있는 자리가 어긋나, 유형 줄 위에 올려도 아무것도 안 뜬다.
	 *
	 * <p>치수는 {@link PerkCardMetrics} 에서 받는다. 카드 높이를 재는 것과 <b>같은 값</b>을
	 * 써야 두 계산이 갈라지지 않는다.
	 */
	public static int rowsTop(PerkCardMetrics metrics, int iconSize, int nameLines) {
		if (metrics == null) {
			return 0;
		}
		int iconBlock = iconSize > 0 ? iconSize + metrics.iconGapBottom() : 0;
		return metrics.bandHeight() + metrics.iconGapTop() + iconBlock
				+ Math.max(0, nameLines) * metrics.lineHeight() + metrics.setTypeGap();
	}

	/**
	 * 마우스가 세트 유형 줄 위에 있는가.
	 *
	 * <p>가로는 <b>글자 폭</b>만 받는다. 카드 폭 전체가 받으면 이름과 설명 사이 빈자리에
	 * 마우스를 두어도 툴팁이 떠, 정작 읽으려던 설명을 툴팁이 덮는다.
	 *
	 * @param centerX   유형 줄이 가운데를 맞추는 x. 카드 한가운데다
	 * @param textWidth 가장 긴 유형 줄의 글자 폭
	 * @param top       유형 첫 줄의 윗변(화면 좌표)
	 */
	public static boolean hovered(double mouseX, double mouseY, int centerX, int textWidth,
			int top, int lineHeight, int lineCount) {
		if (textWidth <= 0 || lineHeight <= 0 || lineCount <= 0) {
			return false;
		}
		int left = centerX - textWidth / 2;
		return mouseX >= left && mouseX < left + textWidth
				&& mouseY >= top && mouseY < top + lineCount * lineHeight;
	}
}
