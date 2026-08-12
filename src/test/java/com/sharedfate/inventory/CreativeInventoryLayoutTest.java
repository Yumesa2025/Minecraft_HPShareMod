package com.sharedfate.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreativeInventoryLayoutTest {
	@Test
	void 추가_27칸은_오른쪽에_겹치지_않는_3행으로_배치된다() {
		assertEquals(196, CreativeInventoryLayout.extraSlotX(0));
		assertEquals(340, CreativeInventoryLayout.extraSlotX(8));
		assertEquals(196, CreativeInventoryLayout.extraSlotX(9));
		assertEquals(54, CreativeInventoryLayout.extraSlotY(0));
		assertEquals(72, CreativeInventoryLayout.extraSlotY(9));
		assertEquals(90, CreativeInventoryLayout.extraSlotY(26));
	}

	@Test
	void 전체_357픽셀_패널을_화면_가운데에_놓는다() {
		assertEquals(248, CreativeInventoryLayout.expandedLeft(854));
		assertEquals(35, CreativeInventoryLayout.expandedLeft(427));
		assertEquals(0, CreativeInventoryLayout.expandedLeft(320));
	}

	@Test
	void 팀_추가칸이_활성일_때만_서버_슬롯_상한을_72로_늘린다() {
		assertEquals(45, CreativeInventoryLayout.maximumServerSlot(false));
		assertEquals(72, CreativeInventoryLayout.maximumServerSlot(true));
	}
}
