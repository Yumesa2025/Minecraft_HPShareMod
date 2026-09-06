package com.sharedfate.client.perk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 카드 줄이 왼쪽 세트 판에게 자리를 내주는가.
 *
 * <p>이 시험이 지키는 것은 셋이다.
 *
 * <ul>
 *   <li>판이 설 폭이 실제로 비는가 — 이것이 안 되면 판이 사라지고, 그러면 세트를 볼 수 있는
 *       곳은 흐려진 HUD 뿐이다.</li>
 *   <li>카드가 화면 밖으로 나가지 않는가 — 밀린 만큼 오른쪽이 잘리면 안 된다.</li>
 *   <li>넓은 화면에서 카드가 이유 없이 치우치지 않는가.</li>
 * </ul>
 *
 * <p>치수는 {@code PerkOfferScreen} 이 쓰는 값 그대로다. 화면 폭 480 은 1080p 의 기본 GUI
 * 배율(4배)이고, 640 은 3배, 320 은 마인크래프트가 허용하는 가장 좁은 자리다.
 */
class PerkOfferCardRowTest {

	private static final PerkOfferCardRow.Limits LIMITS =
			new PerkOfferCardRow.Limits(8, 8, 56, 116, 88);
	private static final int COUNT = 3;
	/** 「지금 켜진 세트」 머리글 폭 62 + 안쪽 여백 5×2 + 카드와의 틈 10. */
	private static final int RESERVE = 82;

	/** 카드가 화면 안에 있고 왼쪽에 판 자리가 남았는지 한 번에 본다. */
	private static void assertRoom(PerkOfferCardRow row, int screenWidth, int count, int reserve) {
		int total = row.totalWidth(count, LIMITS.gap());
		assertTrue(row.firstCardLeft() >= LIMITS.margin() + reserve,
				"판 자리가 모자라다: " + row.firstCardLeft());
		assertTrue(row.firstCardLeft() + total <= screenWidth - LIMITS.margin(),
				"카드가 화면 오른쪽으로 넘쳤다: " + (row.firstCardLeft() + total));
	}

	/**
	 * 1080p 의 기본 배율. 여기서 판이 사라지던 것이 이 고침의 이유다.
	 *
	 * <p>가운데에 놓으면 첫 카드가 58 이라 왼쪽에 50 밖에 안 남고, 판은 72 가 필요하다.
	 */
	@Test
	void 기본_배율에서_판_자리가_난다() {
		PerkOfferCardRow centered = PerkOfferCardRow.fit(LIMITS, 480, COUNT, 0);
		assertEquals(58, centered.firstCardLeft());

		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 480, COUNT, RESERVE);
		assertEquals(90, row.firstCardLeft());
		// 카드는 한 픽셀도 안 줄었다. 오른쪽으로 밀렸을 뿐이다.
		assertEquals(116, row.cardWidth());
		assertRoom(row, 480, COUNT, RESERVE);
	}

	/** 넓은 화면에서는 가운데 자리가 이미 충분히 오른쪽이라 아무것도 안 바뀐다. */
	@Test
	void 넓은_화면에서는_가운데_그대로다() {
		PerkOfferCardRow centered = PerkOfferCardRow.fit(LIMITS, 640, COUNT, 0);
		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 640, COUNT, RESERVE);
		assertEquals(centered.firstCardLeft(), row.firstCardLeft());
		assertEquals(138, row.firstCardLeft());
		assertEquals(116, row.cardWidth());
	}

	/**
	 * 가장 좁은 화면에서는 판을 포기한다.
	 *
	 * <p>자리를 떼면 카드가 68 까지 줄어 {@code panelFloor} 88 을 밑돈다. 그때는 카드를 지킨다 —
	 * 세트를 보자고 무엇을 고르는지 못 읽게 만들 수는 없다.
	 */
	@Test
	void 아주_좁으면_카드를_지킨다() {
		PerkOfferCardRow centered = PerkOfferCardRow.fit(LIMITS, 320, COUNT, 0);
		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 320, COUNT, RESERVE);
		assertEquals(centered.cardWidth(), row.cardWidth());
		assertEquals(centered.firstCardLeft(), row.firstCardLeft());
		assertRoom(row, 320, COUNT, 0);
	}

	/** 720p 의 최대 배율(3배)에서도 판이 선다. */
	@Test
	void 칠백이십피_최대_배율에서도_판이_선다() {
		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 426, COUNT, RESERVE);
		assertTrue(row.cardWidth() >= LIMITS.panelFloor(),
				"카드가 바닥을 밑돌았다: " + row.cardWidth());
		assertEquals(90, row.firstCardLeft());
		assertRoom(row, 426, COUNT, RESERVE);
	}

	/** 세트가 하나도 없으면 뗄 것도 없다. 카드는 늘 그렇듯 가운데다. */
	@Test
	void 세트가_없으면_자리를_안_뗀다() {
		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 480, COUNT, 0);
		assertEquals(58, row.firstCardLeft());
		assertEquals(116, row.cardWidth());
	}

	/** 후보가 한 장뿐인 회차에서도 판 자리는 그대로 난다. */
	@Test
	void 카드가_한_장이어도_판_자리가_난다() {
		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 480, 1, RESERVE);
		assertEquals(116, row.cardWidth());
		assertRoom(row, 480, 1, RESERVE);
	}

	/** 판이 아무리 넓어도 카드를 화면 밖으로 밀어내지 않는다. */
	@Test
	void 판이_지나치게_넓어도_카드는_화면_안에_있다() {
		PerkOfferCardRow row = PerkOfferCardRow.fit(LIMITS, 480, COUNT, 400);
		assertRoom(row, 480, COUNT, 0);
	}
}
