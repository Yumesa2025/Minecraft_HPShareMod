package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 세트 줄에 마우스를 올렸을 때 뜨는 「아직 없는 것」 목록.
 *
 * <p>이 시험이 지키는 것은 둘이다 — <b>이미 가진 것이 목록에 섞이지 않을 것</b>, 그리고
 * <b>잘렸다는 사실을 반드시 말할 것</b>. 채굴에는 증강이 열 개라 말없이 자르면 「채굴은 이게
 * 전부구나」로 읽힌다.
 */
class PerkSetTooltipTest {

	private static List<PerkSetTooltip.Entry> catalog() {
		return List.of(
				new PerkSetTooltip.Entry("mining", "굴착기", "gold", true),
				new PerkSetTooltip.Entry("mining", "종결곡", "prism", false),
				new PerkSetTooltip.Entry("mining", "광맥 감각", "silver", false),
				new PerkSetTooltip.Entry("defense", "철벽", "silver", false));
	}

	// ------------------------------------------------------------------ 고르기

	@Test
	void 그_유형에서_아직_안_가진_것만_고른다() {
		List<PerkSetTooltip.Missing> missing = PerkSetTooltip.missingOf(catalog(), "mining");

		assertEquals(2, missing.size());
		assertEquals("종결곡", missing.getFirst().perkName());
		assertEquals("prism", missing.getFirst().rarity());
		assertEquals("광맥 감각", missing.get(1).perkName());
	}

	@Test
	void 다른_유형은_섞이지_않는다() {
		assertEquals(List.of(new PerkSetTooltip.Missing("철벽", "silver")),
				PerkSetTooltip.missingOf(catalog(), "defense"));
	}

	@Test
	void 모르는_유형이면_빈_목록이다() {
		// 새 서버가 늘린 유형이 옛 클라이언트에 닿는 경우다. 아무 줄에도 안 붙고 끝난다.
		assertTrue(PerkSetTooltip.missingOf(catalog(), "경험치").isEmpty());
		assertTrue(PerkSetTooltip.missingOf(catalog(), "").isEmpty());
		assertTrue(PerkSetTooltip.missingOf(catalog(), null).isEmpty());
		assertTrue(PerkSetTooltip.missingOf(null, "mining").isEmpty());
	}

	// ------------------------------------------------------------------ 자르기

	@Test
	void 상한_안이면_자르지_않는다() {
		PerkSetTooltip.Trimmed trimmed =
				PerkSetTooltip.trim(PerkSetTooltip.missingOf(catalog(), "mining"), 6);

		assertEquals(2, trimmed.shown().size());
		assertEquals(0, trimmed.hidden());
		assertFalse(trimmed.truncated());
		assertEquals("", PerkSetTooltip.overflowLine(trimmed.hidden()));
	}

	@Test
	void 상한을_넘으면_앞에서부터_남기고_나머지를_센다() {
		List<PerkSetTooltip.Missing> ten = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			ten.add(new PerkSetTooltip.Missing("증강" + index, "silver"));
		}

		PerkSetTooltip.Trimmed trimmed = PerkSetTooltip.trim(ten, PerkSetTooltip.MAX_ROWS);

		assertEquals(PerkSetTooltip.MAX_ROWS, trimmed.shown().size());
		assertEquals("증강0", trimmed.shown().getFirst().perkName());
		assertEquals(10 - PerkSetTooltip.MAX_ROWS, trimmed.hidden());
		assertTrue(trimmed.truncated());
		assertEquals("… 외 4개", PerkSetTooltip.overflowLine(trimmed.hidden()));
	}

	@Test
	void 상한과_꼭_같으면_접힌_것이_없다() {
		List<PerkSetTooltip.Missing> six = new ArrayList<>();
		for (int index = 0; index < PerkSetTooltip.MAX_ROWS; index++) {
			six.add(new PerkSetTooltip.Missing("증강" + index, "silver"));
		}

		assertFalse(PerkSetTooltip.trim(six, PerkSetTooltip.MAX_ROWS).truncated());
	}

	@Test
	void 빈_목록은_잘라도_빈_목록이다() {
		assertEquals(0, PerkSetTooltip.trim(List.of(), 6).hidden());
		assertTrue(PerkSetTooltip.trim(null, 6).shown().isEmpty());
	}

	// ------------------------------------------------------------------ 머리글

	@Test
	void 전부_모았으면_다른_말을_한다() {
		// 「아직 없는 것」인데 아무것도 안 뜨면 툴팁이 깨진 것처럼 보인다.
		assertEquals("채굴 — 전부 모았습니다", PerkSetTooltip.header("채굴", 0));
		assertEquals("채굴 — 아직 없는 것", PerkSetTooltip.header("채굴", 3));
	}

	// ------------------------------------------------------------------ 진행도 줄

	/** 채굴은 단계가 2·3·4 다. */
	private static List<PerkSetTooltip.TierEntry> miningTiers() {
		return List.of(
				new PerkSetTooltip.TierEntry("mining", 2, "경험치", true),
				new PerkSetTooltip.TierEntry("mining", 3, "다이아", true),
				new PerkSetTooltip.TierEntry("mining", 4, "같은 블록", true));
	}

	@Test
	void 오를_곳이_남았으면_분모는_다음_단계다() {
		assertEquals("채굴 2/3", PerkSetTooltip.progress("채굴", 2, 3, miningTiers()));
	}

	@Test
	void 전부_켠_뒤_더_모아도_없는_단계를_안_가리킨다() {
		// 열 개를 모아도 10 단계는 없다. 바로 아래 그리는 단계 줄에는 2·3·4 뿐이라
		// 「채굴 10/10」 이면 눈앞에서 어긋난다.
		assertEquals("채굴 10/4", PerkSetTooltip.progress("채굴", 10, 0, miningTiers()));
	}

	@Test
	void 단계가_하나도_없으면_분수를_안_적는다() {
		assertEquals("무기 3", PerkSetTooltip.progress("무기", 3, 0, List.of()));
		assertEquals("무기 3", PerkSetTooltip.progress("무기", 3, 0, null));
	}
}
