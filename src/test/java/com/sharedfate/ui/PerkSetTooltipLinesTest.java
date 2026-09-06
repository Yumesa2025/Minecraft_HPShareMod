package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 세트 툴팁의 줄과 색.
 *
 * <p>이 시험이 지키는 것은 둘이다 — <b>켜진 단계와 안 켜진 단계가 갈릴 것</b>, 그리고
 * <b>줄여서 적었다는 사실을 반드시 말할 것</b>. 네 단계가 같은 색으로 늘어서면 「지금 무엇이
 * 켜져 있는가」에 아무 답도 못 하고, 말없이 자르면 「이게 전부구나」로 읽힌다.
 *
 * <p>색은 밖에서 받으므로 여기서는 알아보기 쉬운 가짜 값을 넣고 <b>어느 줄에 어느 색이
 * 갔는지</b>만 본다.
 */
class PerkSetTooltipLinesTest {
	private static final int PROGRESS = 0x111111;
	private static final int TIER_ACTIVE = 0x222222;
	private static final int TIER_IDLE = 0x333333;
	private static final int MISSING_HEADER = 0x444444;
	private static final int SILVER = 0x555555;
	private static final int GOLD = 0x666666;
	private static final int PRISM = 0x777777;
	private static final int OVERFLOW = 0x888888;

	private static final PerkSetTooltipLines.Palette PALETTE = new PerkSetTooltipLines.Palette(
			PROGRESS, TIER_ACTIVE, TIER_IDLE, MISSING_HEADER, SILVER, GOLD, PRISM, OVERFLOW);

	private static PerkSetTooltip.Body body(PerkSetTooltip.Trimmed missing) {
		return new PerkSetTooltip.Body("채굴 2/3", List.of(
				new PerkSetTooltip.TierEntry("mining", 2, "광물에서 나오는 경험치가 50% 늘어납니다.",
						true),
				new PerkSetTooltip.TierEntry("mining", 3, "다이아몬드 광석을 캘 때 2개가 더 나옵니다.",
						false),
				new PerkSetTooltip.TierEntry("mining", 4, "바로 옆의 같은 블록이 함께 캐집니다.",
						false)),
				missing);
	}

	private static PerkSetTooltip.Trimmed missing(int shown, int hidden) {
		List<PerkSetTooltip.Missing> names = new java.util.ArrayList<>();
		String[] rarities = {"silver", "gold", "prism"};
		for (int index = 0; index < shown; index++) {
			names.add(new PerkSetTooltip.Missing("증강" + index, rarities[index % 3]));
		}
		return new PerkSetTooltip.Trimmed(List.copyOf(names), hidden);
	}

	private static List<String> texts(List<PerkSetTooltipLines.Row> rows) {
		return rows.stream().map(PerkSetTooltipLines.Row::text).toList();
	}

	// ------------------------------------------------------------------ 차례

	@Test
	void 진행도가_맨_위에_온다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(0, 0)), "채굴", 6, PALETTE);

		assertEquals("채굴 2/3", rows.getFirst().text());
		assertEquals(PROGRESS, rows.getFirst().color());
	}

	/** 요청받은 모습 그대로다 — {@code "2:  광물에 …"}. */
	@Test
	void 단계_줄은_개수와_설명을_잇는다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(0, 0)), "채굴", 6, PALETTE);

		assertEquals("2:  광물에서 나오는 경험치가 50% 늘어납니다.", rows.get(1).text());
		assertEquals("3:  다이아몬드 광석을 캘 때 2개가 더 나옵니다.", rows.get(2).text());
		assertEquals("4:  바로 옆의 같은 블록이 함께 캐집니다.", rows.get(3).text());
	}

	/** 켜진 단계와 아직인 단계는 반드시 다른 색으로 나가야 한다. */
	@Test
	void 켜진_단계와_안_켜진_단계가_색으로_갈린다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(0, 0)), "채굴", 6, PALETTE);

		assertEquals(TIER_ACTIVE, rows.get(1).color());
		assertEquals(TIER_IDLE, rows.get(2).color());
		assertEquals(TIER_IDLE, rows.get(3).color());
		assertNotEquals(rows.get(1).color(), rows.get(2).color());
	}

	@Test
	void 단계_설명과_이름_목록_사이에_빈_줄이_들어간다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(2, 0)), "채굴", 6, PALETTE);

		assertTrue(rows.get(4).blank());
		assertEquals("채굴 — 아직 없는 것", rows.get(5).text());
		assertEquals(MISSING_HEADER, rows.get(5).color());
	}

	// ------------------------------------------------------------------ 아직 없는 것

	@Test
	void 아직_없는_것은_등급색으로_적는다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(3, 0)), "채굴", 6, PALETTE);

		assertEquals("· 증강0", rows.get(6).text());
		assertEquals(SILVER, rows.get(6).color());
		assertEquals(GOLD, rows.get(7).color());
		assertEquals(PRISM, rows.get(8).color());
	}

	@Test
	void 모르는_등급은_실버로_적는다() {
		assertEquals(SILVER, PALETTE.rarityColor("무지개"));
		assertEquals(SILVER, PALETTE.rarityColor(null));
	}

	@Test
	void 전부_모았으면_다른_말을_한다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(0, 0)), "채굴", 6, PALETTE);

		assertTrue(texts(rows).contains("채굴 — 전부 모았습니다"));
	}

	/**
	 * 이미 잘려 온 것을 <b>여기서 더</b> 자를 수 있다. 유형이 둘인 증강은 툴팁이 두 덩어리라
	 * 양쪽 다 여섯 줄씩 적으면 카드를 통째로 덮는다.
	 */
	@Test
	void 더_자르면_접힌_개수가_합쳐진다() {
		// 여섯 개를 받아 셋만 적으면, 이미 접혀 있던 넷에 새로 접은 셋이 더해져 일곱이다.
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(6, 4)), "채굴", 3, PALETTE);

		assertTrue(texts(rows).contains("… 외 7개"));
		assertEquals(OVERFLOW, rows.getLast().color());
		// 이름 줄은 셋뿐이다.
		assertEquals(3, texts(rows).stream().filter(text -> text.startsWith("· ")).count());
	}

	/** 「아직 없는 것」의 개수는 <b>자르기 전</b>의 수여야 한다. 자른 뒤 수를 적으면 거짓말이다. */
	@Test
	void 머리글은_자르기_전_개수를_본다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(2, 8)), "채굴", 1, PALETTE);

		assertTrue(texts(rows).contains("채굴 — 아직 없는 것"));
		assertTrue(texts(rows).contains("… 외 9개"));
	}

	@Test
	void 자를_것이_없으면_외_N개를_안_붙인다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(missing(2, 0)), "채굴", 6, PALETTE);

		assertFalse(texts(rows).stream().anyMatch(text -> text.startsWith("… 외")));
	}

	// ------------------------------------------------------------------ 모르는 유형

	/**
	 * 세트 패킷이 아직 안 왔거나 서버가 모르는 유형 id 를 보내면 빈 덩어리가 온다. 그때
	 * 「— 전부 모았습니다」만 뜨면 새빨간 거짓말이 되므로 아예 아무 줄도 만들지 않는다.
	 */
	@Test
	void 아무것도_모르는_유형은_빈_목록이다() {
		PerkSetTooltip.Body empty = new PerkSetTooltip.Body("", List.of(),
				new PerkSetTooltip.Trimmed(List.of(), 0));

		assertTrue(PerkSetTooltipLines.build(empty, "", 6, PALETTE).isEmpty());
	}

	@Test
	void 덩어리가_없어도_터지지_않는다() {
		assertTrue(PerkSetTooltipLines.build(null, "채굴", 6, PALETTE).isEmpty());
		assertTrue(PerkSetTooltipLines.build(body(missing(0, 0)), "채굴", 6, null).isEmpty());
	}

	@Test
	void 단계가_없어도_진행도만_있으면_적는다() {
		PerkSetTooltip.Body onlyProgress = new PerkSetTooltip.Body("무기 1/2", List.of(),
				new PerkSetTooltip.Trimmed(List.of(), 3));
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(onlyProgress, "무기", 6, PALETTE);

		assertEquals("무기 1/2", rows.getFirst().text());
		assertTrue(texts(rows).contains("… 외 3개"));
	}
}
