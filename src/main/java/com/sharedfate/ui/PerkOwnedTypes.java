package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 「이 증강은 어느 세트 유형인가」를 <b>이름표 목록에서 되짚는</b> 계산.
 *
 * <p>보유 증강 목록({@code net.PerkSyncPayload.Owned})은 <b>이름·설명·등급</b>만 들고 온다.
 * 유형 id 가 없어서 「채굴 2/3」 툴팁을 어느 유형으로 물어야 할지 알 수 없다. 그런데 세트
 * 이름표({@code PerkSetSyncPayload.catalog})는 <b>가진 것까지</b> 유형과 함께 싣고 있으므로,
 * 이름을 열쇠로 유형을 되찾을 수 있다.
 *
 * <p>이름이 같은 증강이 둘 있으면 여기서는 가릴 수 없고, 이름표 목록이 상한
 * ({@code PerkSetSyncPayload.MAX_CATALOG}) 에 잘리면 뒤쪽 유형의 증강은 되짚기에서 빠진다.
 * 되짚지 못하면 그 줄에는 툴팁이 안 뜰 뿐 아무것도 깨지지 않는다.
 *
 * <h2>유형이 여럿일 수 있다</h2>
 * <p>증강 하나가 유형을 여럿 가질 수 있어 이름표도 유형마다 한 줄씩 실린다. 그래서 하나가 아니라
 * <b>목록</b>을 돌려준다. 화면은 유형마다 툴팁 한 덩어리씩을 이어 붙인다.
 */
public final class PerkOwnedTypes {

	private PerkOwnedTypes() {
	}

	/**
	 * 이 이름의 증강이 속한 유형 id 들.
	 *
	 * <p>차례는 이름표가 실려 온 차례 그대로다. 같은 유형이 두 번 나와도 한 번만 담는다 —
	 * 같은 툴팁이 두 번 이어 붙는 것을 막는다.
	 *
	 * @param perkName 보유 증강의 이름. 이름표의 이름과 <b>정확히</b> 같아야 한다
	 * @return 되짚지 못하면 빈 목록
	 */
	public static List<String> typeIdsOf(List<PerkSetTooltip.Entry> catalog, String perkName) {
		if (catalog == null || perkName == null || perkName.isEmpty()) {
			return List.of();
		}
		List<String> found = new ArrayList<>();
		for (PerkSetTooltip.Entry entry : catalog) {
			if (entry == null || !perkName.equals(entry.perkName())) {
				continue;
			}
			String typeId = entry.typeId();
			if (typeId != null && !typeId.isEmpty() && !found.contains(typeId)) {
				found.add(typeId);
			}
		}
		return List.copyOf(found);
	}
}
