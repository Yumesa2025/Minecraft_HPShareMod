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

	// ------------------------------------------------------------------ 보급 시계

	/** 10분 주기. 「보급 2·3」이 쓰는 값이다. */
	private static final int TEN_MINUTES = 10 * 20 * 60;
	/** 5분 주기. 「보급 4」가 쓰는 값이다. */
	private static final int FIVE_MINUTES = 5 * 20 * 60;

	private static PerkSetLines.Entry supply(int owned, int next, int tier, int intervalTicks) {
		return new PerkSetLines.Entry("supply", "보급", owned, next, tier, intervalTicks);
	}

	/** 시계는 마름모와 이름 사이에 들어간다. 마름모가 앞자리를 지켜야 세로로 훑을 수 있다. */
	@Test
	void 보급_줄에는_마름모와_이름_사이에_남은_시간이_들어간다() {
		// 10분 주기에서 5분 48초가 지난 자리. 남은 것은 4분 12초다.
		long time = TEN_MINUTES * 12L + (5 * 60 + 48) * 20L;

		List<PerkSetLines.Line> lines =
				PerkSetLines.visible(List.of(supply(2, 3, 2, TEN_MINUTES)), 10, time);

		assertEquals("◆ 04:12 보급 2/3", lines.getFirst().text());
	}

	/** 최대 단계(4/4)에서도 계속 보여 준다. 주기가 5분으로 줄었을 뿐 보급은 여전히 온다. */
	@Test
	void 최대_단계에서도_시계는_남는다() {
		long time = FIVE_MINUTES * 3L + 60 * 20L;

		List<PerkSetLines.Line> lines =
				PerkSetLines.visible(List.of(supply(4, 0, 4, FIVE_MINUTES)), 10, time);

		assertEquals("◆ 04:00 보급 4/4", lines.getFirst().text());
	}

	/**
	 * <b>다른 유형에는 절대 붙지 않는다.</b>
	 *
	 * <p>붙일지 말지는 서버가 주기를 실어 주느냐로 정해진다. 화면이 유형 이름을 보고 고르면
	 * 유형 id 가 바뀌는 날 조용히 어긋난다.
	 */
	@Test
	void 주기가_없는_유형에는_시계가_안_붙는다() {
		long time = TEN_MINUTES * 12L + 100L;

		List<PerkSetLines.Line> lines = PerkSetLines.visible(List.of(
				supply(2, 3, 2, TEN_MINUTES),
				entry("mining", "채굴", 3, 4, 3),
				entry("power", "화력", 1, 3, 0)), 10, time);

		// 차례는 정렬이 정하므로 유형으로 찾는다.
		assertEquals("◆ 채굴 3/4", textOf(lines, "mining"));
		assertEquals("◇ 화력 1/3", textOf(lines, "power"));
		assertEquals("◆ 09:55 보급 2/3", textOf(lines, "supply"));
	}

	/** 그 유형의 줄 글자. 없으면 시험을 실패시킨다. */
	private static String textOf(List<PerkSetLines.Line> lines, String typeId) {
		for (PerkSetLines.Line line : lines) {
			if (line.typeId().equals(typeId)) {
				return line.text();
			}
		}
		throw new AssertionError(typeId + " 줄이 없습니다");
	}

	/**
	 * 시간을 모르는 화면은 시계 없는 줄을 받는다.
	 *
	 * <p>팀 화면과 선택창 곁판이 이 길로 들어온다. 그 두 곳의 줄 폭이 매초 흔들리면 마우스가
	 * 어느 줄 위인지 재는 계산까지 함께 흔들린다.
	 */
	@Test
	void 시간을_안_넘기면_시계가_없다() {
		List<PerkSetLines.Line> lines =
				PerkSetLines.visible(List.of(supply(2, 3, 2, TEN_MINUTES)), 10);

		assertEquals("◆ 보급 2/3", lines.getFirst().text());
	}

	/**
	 * 단계가 안 켜져 있어도 주기가 있으면 시계가 뜬다.
	 *
	 * <p>세트가 아니라 증강 하나가 {@code supply_drop} 을 들고 도는 경우다. 실제로 보급이 오는데
	 * 화면에 안 뜨는 것이 그 반대보다 나쁘다.
	 */
	@Test
	void 안_켜진_줄이라도_주기가_있으면_시계가_뜬다() {
		List<PerkSetLines.Line> lines =
				PerkSetLines.visible(List.of(supply(1, 2, 0, TEN_MINUTES)), 10, TEN_MINUTES * 4L);

		assertEquals("◇ 10:00 보급 1/2", lines.getFirst().text());
	}

	/**
	 * <b>한 주기 내내 구분선의 길이가 변하지 않는다.</b>
	 *
	 * <p>{@code SupplyCountdown} 이 분까지 두 자리로 채우는 까닭이 이것이다. 구분선은 가장 긴
	 * 줄에 맞춰 매 프레임 다시 재므로, 시계의 글자 수가 한 번이라도 줄면 그 순간 선이 눈에 띄게
	 * 짧아졌다가 다음 주기에 다시 길어진다. 시계 글자만 보는 시험은
	 * {@code SupplyCountdownTest} 에 있고, 여기서는 <b>실제로 선을 재는 길</b>로 확인한다.
	 *
	 * <p>보급 줄이 가장 긴 줄이 되도록 다른 줄은 짧은 것 하나만 둔다. 그래야 시계가 흔들릴 때
	 * 폭도 함께 흔들려 시험이 실제로 무언가를 잡는다.
	 */
	@Test
	void 한_주기_동안_구분선_길이가_변하지_않는다() {
		List<PerkSetLines.Entry> entries = List.of(
				supply(2, 3, 2, TEN_MINUTES),
				entry("mining", "채굴", 1, 2, 0));

		int first = PerkSetLines.blockWidth(
				PerkSetLines.visible(entries, 10, 0L), FakeFont::width, 0);

		for (long time = 0; time < TEN_MINUTES; time++) {
			int now = PerkSetLines.blockWidth(
					PerkSetLines.visible(entries, 10, time), FakeFont::width, 0);
			assertEquals(first, now, "구분선 길이가 달라졌습니다 (틱 " + time + ")");
		}

		// 보급 줄이 정말 가장 긴 줄이었는지. 아니었다면 위 되풀이가 아무것도 못 잡는다.
		assertTrue(first > FakeFont.width("◇ 채굴 1/2"), "보급 줄이 가장 긴 줄이 아닙니다");
	}

	/** 주기를 안 싣는 옛 서버에 붙으면 옛 모습 그대로다. 다섯 인자 생성자가 그 자리다. */
	@Test
	void 주기를_안_보내는_서버에서는_옛_모습_그대로다() {
		PerkSetLines.Entry old = entry("supply", "보급", 2, 3, 2);

		assertEquals(0, old.intervalTicks());
		assertFalse(old.hasTimer());
		assertEquals("◆ 보급 2/3",
				PerkSetLines.visible(List.of(old), 10, 12345L).getFirst().text());
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
