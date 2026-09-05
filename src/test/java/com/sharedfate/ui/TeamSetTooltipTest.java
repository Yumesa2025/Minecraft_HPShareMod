package com.sharedfate.ui;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀 화면이 띄우는 세트 툴팁의 <b>줄과 색</b>.
 *
 * <p>세트 줄과 보유 증강 줄이 같은 것을 띄운다. 그 「같은 것」을 만드는 계산이
 * {@link PerkSetTooltipLines} 이고, 여기서는 <b>팀 화면이 부르는 모양 그대로</b> 불러 본다.
 *
 * <p>지키는 것은 셋이다 — <b>켜진 단계와 안 켜진 단계가 색으로 갈릴 것</b>, <b>세트를 모르면
 * 아무 줄도 만들지 않을 것</b>, 그리고 <b>유형이 둘인 증강에서 이름을 더 줄여도 「외 N개」가
 * 거짓말이 되지 않을 것</b>.
 */
class TeamSetTooltipTest {

	/** 알아보기 쉬운 가짜 색. 실제 값은 팀 화면이 정한다. */
	private static final int PROGRESS = 1;
	private static final int TIER_ACTIVE = 2;
	private static final int TIER_IDLE = 3;
	private static final int MISSING_HEADER = 4;
	private static final int SILVER = 5;
	private static final int GOLD = 6;
	private static final int PRISM = 7;
	private static final int OVERFLOW = 8;

	private static final PerkSetTooltipLines.Palette PALETTE = new PerkSetTooltipLines.Palette(
			PROGRESS, TIER_ACTIVE, TIER_IDLE, MISSING_HEADER, SILVER, GOLD, PRISM, OVERFLOW);

	@BeforeAll
	static void 준비() {
		TestBootstrap.ensureInitialized();
	}

	private static List<PerkSetTooltip.TierEntry> tiers() {
		return List.of(
				new PerkSetTooltip.TierEntry("mining", 2, "광물에서 나오는 경험치가 50% 늘어납니다", true),
				new PerkSetTooltip.TierEntry("mining", 3, "다이아몬드를 캐면 2개씩 더 나옵니다", false),
				new PerkSetTooltip.TierEntry("mining", 4, "바로 옆의 같은 블록이 2개 더 캐집니다", false));
	}

	private static List<PerkSetTooltip.Entry> catalog() {
		return List.of(
				new PerkSetTooltip.Entry("mining", "굴착기", "gold", true),
				new PerkSetTooltip.Entry("mining", "종결곡", "prism", false),
				new PerkSetTooltip.Entry("mining", "광맥 감각", "silver", false));
	}

	/** 팀 화면이 부르는 것과 같은 길. {@code ClientPerkSets.tooltip} 이 이 덩어리를 만든다. */
	private static PerkSetTooltip.Body body() {
		return PerkSetTooltip.describe("mining", "채굴", 2, 3, tiers(), catalog(),
				PerkSetTooltip.MAX_ROWS);
	}

	// ------------------------------------------------------------------ 단계 줄

	@Test
	void 진행도가_맨_윗줄이다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(), "채굴", PerkSetTooltip.MAX_ROWS, PALETTE);

		assertEquals("채굴 2/3", rows.getFirst().text());
		assertEquals(PROGRESS, rows.getFirst().color());
	}

	/**
	 * 이 툴팁의 본론이다. 네 줄이 같은 색으로 늘어서면 「지금 무엇이 켜져 있는가」에 아무 답도
	 * 못 한다.
	 */
	@Test
	void 켜진_단계는_밝고_안_켜진_단계는_흐리다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(), "채굴", PerkSetTooltip.MAX_ROWS, PALETTE);

		assertEquals("2:  광물에서 나오는 경험치가 50% 늘어납니다", rows.get(1).text());
		assertEquals(TIER_ACTIVE, rows.get(1).color());
		assertEquals(TIER_IDLE, rows.get(2).color());
		assertEquals(TIER_IDLE, rows.get(3).color());
	}

	@Test
	void 단계는_개수_오름차순이다() {
		// 정의 파일에 거꾸로 적힌 서버에서도 툴팁은 2·3·4 차례여야 한다.
		List<PerkSetTooltip.TierEntry> shuffled = new ArrayList<>(tiers());
		java.util.Collections.reverse(shuffled);
		PerkSetTooltip.Body body = PerkSetTooltip.describe("mining", "채굴", 2, 3, shuffled,
				catalog(), PerkSetTooltip.MAX_ROWS);

		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body, "채굴", PerkSetTooltip.MAX_ROWS, PALETTE);

		assertTrue(rows.get(1).text().startsWith("2:"));
		assertTrue(rows.get(2).text().startsWith("3:"));
		assertTrue(rows.get(3).text().startsWith("4:"));
	}

	@Test
	void 단계와_이름_목록_사이에_빈_줄이_있다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(), "채굴", PerkSetTooltip.MAX_ROWS, PALETTE);

		assertTrue(rows.get(4).blank());
		assertEquals("채굴 — 아직 없는 것", rows.get(5).text());
		assertEquals(MISSING_HEADER, rows.get(5).color());
	}

	// ------------------------------------------------------------------ 이름 목록

	@Test
	void 아직_없는_것은_등급색으로_적힌다() {
		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body(), "채굴", PerkSetTooltip.MAX_ROWS, PALETTE);

		assertEquals("· 종결곡", rows.get(6).text());
		assertEquals(PRISM, rows.get(6).color());
		assertEquals("· 광맥 감각", rows.get(7).text());
		assertEquals(SILVER, rows.get(7).color());
		// 이미 가진 「굴착기」는 목록에 없다.
		assertEquals(8, rows.size());
	}

	/**
	 * 증강 하나가 유형을 둘 가지면 툴팁이 두 덩어리가 된다. 그때 이름 목록을 더 줄이는데,
	 * <b>접힌 개수는 이미 접혀 있던 것까지 더해야</b> 「외 N개」가 진실이 된다.
	 */
	@Test
	void 이름을_더_줄여도_외_N개가_전부를_센다() {
		List<PerkSetTooltip.Missing> ten = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			ten.add(new PerkSetTooltip.Missing("증강" + index, "silver"));
		}
		PerkSetTooltip.Body body = new PerkSetTooltip.Body("채굴 2/3", tiers(),
				PerkSetTooltip.trim(ten, PerkSetTooltip.MAX_ROWS));

		List<PerkSetTooltipLines.Row> rows = PerkSetTooltipLines.build(body, "채굴", 3, PALETTE);

		assertEquals("… 외 7개", rows.getLast().text());
		assertEquals(OVERFLOW, rows.getLast().color());
		assertEquals("· 증강2", rows.get(rows.size() - 2).text());
	}

	@Test
	void 전부_모았으면_머리글이_다른_말을_한다() {
		PerkSetTooltip.Body body = PerkSetTooltip.describe("mining", "채굴", 3, 0, tiers(),
				List.of(new PerkSetTooltip.Entry("mining", "굴착기", "gold", true)),
				PerkSetTooltip.MAX_ROWS);

		List<PerkSetTooltipLines.Row> rows =
				PerkSetTooltipLines.build(body, "채굴", PerkSetTooltip.MAX_ROWS, PALETTE);

		assertEquals("채굴 — 전부 모았습니다", rows.getLast().text());
	}

	// ------------------------------------------------------------------ 세트가 없을 때

	/**
	 * 세트 패킷이 아직 안 왔거나 모르는 유형 id 로 물었을 때다.
	 * {@code ClientPerkSets.tooltip} 이 빈 덩어리를 주고, 팀 화면은 줄이 없으면 툴팁 자체를
	 * 띄우지 않는다. 「— 전부 모았습니다」 한 줄짜리 상자가 뜨면 새빨간 거짓말이 된다.
	 */
	@Test
	void 세트를_모르면_줄이_하나도_없다() {
		PerkSetTooltip.Body empty =
				new PerkSetTooltip.Body("", List.of(), new PerkSetTooltip.Trimmed(List.of(), 0));

		assertTrue(PerkSetTooltipLines.build(empty, "", PerkSetTooltip.MAX_ROWS, PALETTE).isEmpty());
		assertTrue(PerkSetTooltipLines.build(null, "채굴", PerkSetTooltip.MAX_ROWS, PALETTE)
				.isEmpty());
	}
}
