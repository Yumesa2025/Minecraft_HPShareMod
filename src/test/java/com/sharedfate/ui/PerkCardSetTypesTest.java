package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 카드의 세트 유형 줄 — 어디에 놓이고, 마우스가 그 위에 있는지, 이름을 id 로 되돌리는 일.
 *
 * <h2>왜 되돌려야 하는가</h2>
 * <p>선택 화면의 카드가 들고 있는 것은 <b>화면용 이름</b>이다({@code "무기·화력"}). 그런데
 * 툴팁은 <b>id</b> 로 묻는다({@code power}). 이름을 그대로 넘기면 언제나 빈 툴팁이 나오는데,
 * 화면에는 아무 오류도 안 뜬다 — 그냥 마우스를 올려도 아무 일이 안 일어난다. 그 사고를 여기서
 * 막는다.
 */
class PerkCardSetTypesTest {
	/** 서버와 화면이 함께 쓰는 이음쇠. {@code PerkOfferPayload.PerkOption.SET_TYPE_JOINER}. */
	private static final String JOINER = "·";

	/** 등급 띠 11 · 띠~아이콘 4 · 아이콘~이름 3 · 이름~유형 2 · 구분선 8 · 아래 여백 6 · 글줄 9. */
	private static final PerkCardMetrics METRICS = new PerkCardMetrics(11, 4, 3, 2, 8, 6, 9);

	private static List<PerkSetLines.Entry> sets() {
		return List.of(
				new PerkSetLines.Entry("mining", "채굴", 2, 3, 0),
				new PerkSetLines.Entry("power", "화력", 1, 2, 0),
				new PerkSetLines.Entry("weapon", "무기", 1, 2, 0));
	}

	// ------------------------------------------------------------------ 가르기

	@Test
	void 가운뎃점으로_가른다() {
		assertEquals(List.of("무기", "화력"), PerkCardSetTypes.split("무기·화력", JOINER));
	}

	@Test
	void 유형이_하나면_그대로_한_개다() {
		assertEquals(List.of("채굴"), PerkCardSetTypes.split("채굴", JOINER));
	}

	@Test
	void 유형이_없으면_빈_목록이다() {
		assertTrue(PerkCardSetTypes.split("", JOINER).isEmpty());
		assertTrue(PerkCardSetTypes.split(null, JOINER).isEmpty());
	}

	/** 이음쇠가 잘못 붙어 와도 빈 이름으로 툴팁을 묻는 일이 없어야 한다. */
	@Test
	void 빈_조각은_버린다() {
		assertEquals(List.of("채굴"), PerkCardSetTypes.split("·채굴·", JOINER));
	}

	// ------------------------------------------------------------------ 되돌리기

	@Test
	void 이름을_유형_id_로_되돌린다() {
		assertEquals("mining", PerkCardSetTypes.typeId(sets(), "채굴"));
		assertEquals(List.of("weapon", "power"),
				PerkCardSetTypes.typeIds("무기·화력", JOINER, sets()));
	}

	/**
	 * 못 되돌리는 것은 정상이다 — 세트 패킷이 아직 안 왔거나 서버가 그 유형을 안 쓰는 때다.
	 * 그때는 빈 문자열이 나오고 화면은 툴팁을 아예 안 띄운다.
	 */
	@Test
	void 모르는_이름은_빠진다() {
		assertEquals("", PerkCardSetTypes.typeId(sets(), "잠수"));
		assertEquals(List.of("mining"), PerkCardSetTypes.typeIds("채굴·잠수", JOINER, sets()));
		assertTrue(PerkCardSetTypes.typeIds("채굴", JOINER, List.of()).isEmpty());
	}

	@Test
	void 같은_id_가_두_번_들어가지_않는다() {
		assertEquals(List.of("mining"), PerkCardSetTypes.typeIds("채굴·채굴", JOINER, sets()));
	}

	// ------------------------------------------------------------------ 자리

	/**
	 * <b>유형 줄은 반드시 카드 안에 있어야 한다.</b>
	 *
	 * <p>카드 안쪽은 {@code enableScissor} 로 잘린다. 마우스를 받는 자리가 카드 밖으로 나가면
	 * 글자가 없는 곳에서 툴팁이 뜬다. {@link PerkCardMetrics#height} 와 같은 치수를 쓰므로 이
	 * 관계는 아이콘이 있든 없든, 이름이 몇 줄이든 성립해야 한다.
	 */
	@Test
	void 유형_줄은_카드_안에_들어간다() {
		for (int icon : new int[] {0, 16, 32}) {
			for (int nameLines = 1; nameLines <= 3; nameLines++) {
				for (int setTypeLines = 1; setTypeLines <= 2; setTypeLines++) {
					int top = PerkCardSetTypes.rowsTop(METRICS, icon, nameLines);
					int bottom = top + setTypeLines * METRICS.lineHeight();
					int height = METRICS.height(icon, nameLines, setTypeLines, 3);

					assertTrue(bottom <= height,
							"아이콘 " + icon + " 이름 " + nameLines + "줄 유형 " + setTypeLines
									+ "줄에서 유형 줄이 카드 밖으로 나갔다");
				}
			}
		}
	}

	@Test
	void 이름이_길어진_만큼_유형_줄도_내려간다() {
		assertEquals(PerkCardSetTypes.rowsTop(METRICS, 16, 1) + 9,
				PerkCardSetTypes.rowsTop(METRICS, 16, 2));
	}

	@Test
	void 아이콘이_없으면_그만큼_올라온다() {
		assertEquals(PerkCardSetTypes.rowsTop(METRICS, 0, 1) + 16 + 3,
				PerkCardSetTypes.rowsTop(METRICS, 16, 1));
	}

	// ------------------------------------------------------------------ 마우스 판정

	@Test
	void 유형_줄_위에_마우스를_두면_잡힌다() {
		// 가운데 100, 글자 폭 40 → 80..119 를 받는다.
		assertTrue(PerkCardSetTypes.hovered(80, 50, 100, 40, 50, 9, 1));
		assertTrue(PerkCardSetTypes.hovered(119, 58, 100, 40, 50, 9, 1));
	}

	/** 글자 폭 밖은 안 받는다. 카드 폭 전체가 받으면 설명을 읽는 동안 툴팁이 그것을 덮는다. */
	@Test
	void 글자_폭_밖은_안_잡힌다() {
		assertFalse(PerkCardSetTypes.hovered(79, 50, 100, 40, 50, 9, 1));
		assertFalse(PerkCardSetTypes.hovered(120, 50, 100, 40, 50, 9, 1));
	}

	@Test
	void 줄_위아래는_안_잡힌다() {
		assertFalse(PerkCardSetTypes.hovered(100, 49, 100, 40, 50, 9, 1));
		assertFalse(PerkCardSetTypes.hovered(100, 59, 100, 40, 50, 9, 1));
	}

	@Test
	void 두_줄이면_두_줄_다_잡힌다() {
		assertTrue(PerkCardSetTypes.hovered(100, 59, 100, 40, 50, 9, 2));
		assertFalse(PerkCardSetTypes.hovered(100, 68, 100, 40, 50, 9, 2));
	}

	@Test
	void 유형_줄이_없으면_아무데서도_안_잡힌다() {
		assertFalse(PerkCardSetTypes.hovered(100, 50, 100, 40, 50, 9, 0));
		assertFalse(PerkCardSetTypes.hovered(100, 50, 100, 0, 50, 9, 1));
	}
}
