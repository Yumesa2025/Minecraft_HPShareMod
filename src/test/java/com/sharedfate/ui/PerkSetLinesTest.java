package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD 와 팀 화면이 함께 쓰는 세트 줄 계산.
 *
 * <p>그리는 부분은 살아 있는 클라이언트가 있어야 하지만, <b>몇 줄을 어떤 글자로 어느 차례로
 * 그리는가</b>는 순수 함수라 여기서 확인한다. 이 시험이 지키는 것은 셋이다 — 한 개도 없는
 * 유형이 화면을 덮지 않을 것, 켜진 세트가 맨 위에 있을 것, 줄이 없으면 자리를 아예 차지하지
 * 않을 것.
 */
class PerkSetLinesTest {

	private static PerkSetLines.Entry entry(String id, String name, int owned, int next,
			int tier) {
		return new PerkSetLines.Entry(id, name, owned, next, tier);
	}

	// ------------------------------------------------------------------ 글자

	@Test
	void 켜진_세트는_찬_마름모_진행중은_빈_마름모다() {
		assertEquals("◆ 채굴 3/4", PerkSetLines.label(entry("mining", "채굴", 3, 4, 3)));
		assertEquals("◇ 방어 1/2", PerkSetLines.label(entry("defense", "방어", 1, 2, 0)));
	}

	/**
	 * 더 오를 곳이 없으면 분모에 가진 개수를 놓는다.
	 *
	 * <p>「채굴 3」처럼 분모를 지우면 바로 아래의 「방어 1/2」와 모양이 달라져 두 줄을 견주기
	 * 어렵다.
	 */
	@Test
	void 마지막_단계는_분모가_가진_개수다() {
		assertEquals("◆ 채굴 3/3", PerkSetLines.label(entry("mining", "채굴", 3, 0, 3)));
	}

	// ------------------------------------------------------------------ 고르기와 차례

	@Test
	void 한_개도_없는_유형은_빠진다() {
		// 유형이 열한 개다. 전부 그리면 「기동 0/2」 같은 줄이 화면 왼쪽 위를 통째로 덮는다.
		List<PerkSetLines.Line> lines = PerkSetLines.visible(List.of(
				entry("mining", "채굴", 2, 3, 0),
				entry("mobility", "기동", 0, 2, 0),
				entry("swap", "교환", 0, 2, 0)), 10);

		assertEquals(1, lines.size());
		assertEquals("◇ 채굴 2/3", lines.getFirst().text());
	}

	@Test
	void 켜진_세트가_맨_위로_온다() {
		List<PerkSetLines.Line> lines = PerkSetLines.visible(List.of(
				entry("mining", "채굴", 2, 3, 0),
				entry("defense", "방어", 2, 3, 2)), 10);

		assertEquals("defense", lines.getFirst().typeId());
		assertTrue(lines.getFirst().active());
		assertEquals("mining", lines.get(1).typeId());
		assertFalse(lines.get(1).active());
	}

	@Test
	void 아직인_것끼리는_많이_모은_쪽이_먼저다() {
		List<PerkSetLines.Line> lines = PerkSetLines.visible(List.of(
				entry("swap", "교환", 1, 2, 0),
				entry("mining", "채굴", 2, 3, 0)), 10);

		assertEquals("mining", lines.getFirst().typeId());
	}

	@Test
	void 잘릴_때_없어지는_것은_가장_덜_모은_유형이다() {
		List<PerkSetLines.Line> lines = PerkSetLines.visible(List.of(
				entry("swap", "교환", 1, 2, 0),
				entry("mining", "채굴", 3, 4, 3),
				entry("defense", "방어", 2, 3, 0)), 2);

		assertEquals(2, lines.size());
		assertEquals("mining", lines.getFirst().typeId());
		assertEquals("defense", lines.get(1).typeId());
	}

	@Test
	void 줄_수_상한이_0이거나_목록이_비면_아무것도_안_그린다() {
		assertTrue(PerkSetLines.visible(List.of(entry("mining", "채굴", 3, 4, 3)), 0).isEmpty());
		assertTrue(PerkSetLines.visible(List.of(), 10).isEmpty());
		assertTrue(PerkSetLines.visible(null, 10).isEmpty());
	}

	// ------------------------------------------------------------------ 자리

	/**
	 * 세트가 없으면 높이가 0이어야 한다.
	 *
	 * <p>팀 화면의 {@code perkListTop()} 이 이 값을 더한다. 0이 아니면 세트를 하나도 모으지
	 * 않은 사람의 증강 목록이 이유 없이 아래로 내려간다.
	 */
	@Test
	void 줄이_없으면_자리를_차지하지_않는다() {
		assertEquals(0, PerkSetLines.blockHeight(0, 12, 4));
		assertEquals(0, PerkSetLines.blockHeight(-1, 12, 4));
	}

	@Test
	void 줄이_있으면_줄_높이에_아래_틈을_더한다() {
		assertEquals(12 + 4, PerkSetLines.blockHeight(1, 12, 4));
		assertEquals(36 + 4, PerkSetLines.blockHeight(3, 12, 4));
	}

	/** 구분선 길이. 실제 폰트와 같은 폭으로 재는지 {@link FakeFont} 로 확인한다. */
	@Test
	void 가장_긴_줄의_폭을_잰다() {
		List<PerkSetLines.Line> lines = PerkSetLines.visible(List.of(
				entry("mining", "채굴", 3, 4, 3),
				entry("recovery", "회복", 1, 2, 0)), 10);

		// 두 줄의 글자 길이가 같아 폭도 같다. 최소값보다 크면 잰 값이 그대로 쓰인다.
		int expected = FakeFont.width("◆ 채굴 3/4");
		assertEquals(expected, PerkSetLines.blockWidth(lines, FakeFont::width, 0));
	}

	@Test
	void 줄이_아무리_짧아도_최소_폭은_긋는다() {
		List<PerkSetLines.Line> lines =
				PerkSetLines.visible(List.of(entry("mining", "채굴", 3, 4, 3)), 10);

		assertEquals(400, PerkSetLines.blockWidth(lines, FakeFont::width, 400));
	}

	@Test
	void 줄이_없으면_구분선도_없다() {
		assertEquals(0, PerkSetLines.blockWidth(List.of(), FakeFont::width, 60));
	}

	// ------------------------------------------------------------------ 마우스

	@Test
	void 줄_안에_있으면_그_차례를_돌려준다() {
		assertEquals(0, PerkSetLines.rowAt(20, 100, 10, 80, 100, 12, 3));
		assertEquals(0, PerkSetLines.rowAt(20, 111, 10, 80, 100, 12, 3));
		assertEquals(1, PerkSetLines.rowAt(20, 112, 10, 80, 100, 12, 3));
		assertEquals(2, PerkSetLines.rowAt(20, 135, 10, 80, 100, 12, 3));
	}

	@Test
	void 덩어리_밖이면_아무_줄도_아니다() {
		// 위로 벗어남 · 아래로 벗어남 · 왼쪽으로 벗어남 · 오른쪽으로 벗어남
		assertEquals(-1, PerkSetLines.rowAt(20, 99, 10, 80, 100, 12, 3));
		assertEquals(-1, PerkSetLines.rowAt(20, 136, 10, 80, 100, 12, 3));
		assertEquals(-1, PerkSetLines.rowAt(9, 100, 10, 80, 100, 12, 3));
		assertEquals(-1, PerkSetLines.rowAt(90, 100, 10, 80, 100, 12, 3));
	}

	/**
	 * 판 오른쪽의 빈자리에서는 툴팁이 뜨면 안 된다.
	 *
	 * <p>가로 범위를 안 보면 마우스를 어디에 두어도 무언가 뜨는 화면이 된다.
	 */
	@Test
	void 그릴_줄이_없으면_마우스도_받지_않는다() {
		assertEquals(-1, PerkSetLines.rowAt(20, 100, 10, 80, 100, 12, 0));
		assertEquals(-1, PerkSetLines.rowAt(20, 100, 10, 0, 100, 12, 3));
	}
}
