package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인벤토리 왼쪽의 두 번째 단추(「증강」)가 첫 단추를 밀어내지 않는지.
 *
 * <p>두 단추는 한 줄에 나란히 서고 그 줄의 <b>오른쪽 끝이 창에 붙는다.</b> 줄이 쓸 수 있는
 * 폭을 넘으면 창을 덮으므로, 자리가 모자랄 때는 두 번째 단추를 포기해야 한다.
 */
class InventoryPerkButtonTest {

	/** 「증강」 두 글자의 대략적인 폭. 폰트에 따라 달라지므로 시험에서는 고정값을 쓴다. */
	private static final int PERK_LABEL_WIDTH = 24;

	@Test
	void 자리가_넉넉하면_두_단추가_함께_선다() {
		int available = 200;
		int team = InventoryTeamButton.buttonWidth(60, available);
		int perk = InventoryTeamButton.perkButtonWidth(PERK_LABEL_WIDTH, available, team);

		assertTrue(perk > 0, "자리가 있는데 단추를 포기했다");
		assertTrue(InventoryTeamButton.buttonRowWidth(team, perk) <= available,
				"두 단추가 쓸 수 있는 폭을 넘었다");
	}

	@Test
	void 자리가_모자라면_증강_단추를_포기한다() {
		int available = 70;
		int team = InventoryTeamButton.buttonWidth(60, available);

		assertEquals(0, InventoryTeamButton.perkButtonWidth(PERK_LABEL_WIDTH, available, team),
				"좁은데도 단추를 끼웠다");
		assertEquals(team, InventoryTeamButton.buttonRowWidth(team, 0),
				"단추가 없으면 줄 폭은 첫 단추 그대로여야 한다");
	}

	/** 화면 폭을 넓은 것부터 좁은 것까지 훑어도 줄이 절대 창을 덮지 않아야 한다. */
	@Test
	void 어떤_폭에서도_줄이_창을_덮지_않는다() {
		for (int available = 0; available <= 400; available++) {
			int team = InventoryTeamButton.buttonWidth(60, available);
			int perk = InventoryTeamButton.perkButtonWidth(PERK_LABEL_WIDTH, available, team);
			if (perk == 0) {
				continue;
			}
			assertTrue(InventoryTeamButton.buttonRowWidth(team, perk) <= available,
					"available=" + available + " 에서 줄이 넘쳤다");
		}
	}

	/** 포기 판정은 첫 단추가 실제로 차지한 폭을 기준으로 해야 한다. */
	@Test
	void 첫_단추가_넓어지면_두_번째는_먼저_포기한다() {
		int available = 100;
		int narrow = InventoryTeamButton.buttonWidth(20, available);
		int wide = InventoryTeamButton.buttonWidth(200, available);

		assertTrue(wide > narrow, "시험 전제가 깨졌다");
		assertTrue(InventoryTeamButton.perkButtonWidth(PERK_LABEL_WIDTH, available, narrow)
						>= InventoryTeamButton.perkButtonWidth(PERK_LABEL_WIDTH, available, wide),
				"첫 단추가 넓어졌는데 두 번째가 더 잘 들어갔다");
	}
}
