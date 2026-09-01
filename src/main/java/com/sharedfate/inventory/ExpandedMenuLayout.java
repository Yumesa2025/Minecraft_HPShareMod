package com.sharedfate.inventory;

/**
 * 확장 27칸을 붙인 메뉴가 <b>제 배치를 스스로 알려 주는 통로</b>입니다.
 *
 * <p>{@code AbstractContainerMenu} 에 Mixin 으로 구현을 얹습니다. 화면 쪽 코드와
 * 쉬프트 클릭 코드가 「플레이어 36칸이 몇 번부터인가」·「추가 27칸이 몇 번부터인가」·
 * 「인벤토리 첫 줄의 y 는 얼마인가」 셋만 알면 나머지는 전부 계산으로 나옵니다.
 *
 * <p>메뉴마다 슬롯 번호가 다르므로 이 셋을 상수로 둘 수 없습니다. 상자는 27줄 뒤,
 * 화로는 3칸 뒤, 인챈트는 2칸 뒤에서 플레이어 인벤토리가 시작합니다.
 */
public interface ExpandedMenuLayout {
	/** 「없음」. 확장 인벤토리를 붙이지 않은 메뉴가 돌려주는 값입니다. */
	int NONE = -1;

	/** 플레이어 인벤토리 27칸이 시작하는 메뉴 번호. 핫바는 여기서 27칸 뒤입니다. */
	default int sharedfate$playerSlotStart() {
		return NONE;
	}

	/** 추가 27칸이 시작하는 메뉴 번호. */
	default int sharedfate$extraSlotStart() {
		return NONE;
	}

	/** 인벤토리 첫 줄의 화면 y. 바닐라 {@code addStandardInventorySlots} 에 넘어간 값입니다. */
	default int sharedfate$inventoryTopY() {
		return NONE;
	}

	/**
	 * 배치를 기록합니다.
	 *
	 * <p>{@code addStandardInventorySlots} 를 거치지 않고 슬롯을 직접 붙이는
	 * {@code InventoryMenu} 처럼 스스로 값을 채울 수 없는 메뉴가 씁니다.
	 */
	default void sharedfate$setExpandedLayout(
			int playerSlotStart, int extraSlotStart, int inventoryTopY) {
	}
}
