package com.sharedfate.ui;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀 화면 「증강」 탭에서 <b>세트 툴팁이 뜨는 자리</b>.
 *
 * <p>그 탭은 위에서부터 머리글 → 세트 줄 → 보유 증강 목록이다. 세트 툴팁은 <b>세트 줄에서만</b>
 * 뜬다. 아래쪽 보유 증강 줄은 이름 밑에 설명을 이미 펼쳐 적고 있어서, 읽으려고 마우스를 얹는
 * 순간 상자가 뜨면 읽으려던 그 설명을 덮는다.
 *
 * <p>그래서 이 시험이 지키는 것은 <b>세트 판정이 세트 줄 밖으로 새지 않는 것</b>이다. 세는 식이
 * 어긋나 판정이 아래로 흘러내리면, 화면 쪽에서 무엇을 지우든 목록 자리에 다시 상자가 뜬다.
 *
 * <p>시험 소스셋은 {@code src/client} 의 화면 코드를 보지 못하므로, 화면이 쓰는 자리 계산을
 * <b>같은 상수와 같은 함수로</b> 여기서 다시 세운다. 상수가 화면과 어긋나면 여기서 지키는 경계도
 * 실제 화면과 다른 자리가 되므로, 화면의 값을 고칠 때는 이 값들도 함께 고쳐야 한다.
 */
class TeamScreenSetTooltipRegionTest {

	/** 판이 시작하는 y. 화면의 {@code PANEL_TOP} 과 같다. */
	private static final int PANEL_TOP = 40;
	/** 한 줄의 높이. 화면의 {@code ROW_HEIGHT} 와 같다. */
	private static final int ROW_HEIGHT = 12;
	/** 세트 덩어리와 그 아래 증강 목록 사이의 틈. 화면의 {@code SET_BLOCK_GAP} 과 같다. */
	private static final int SET_BLOCK_GAP = 4;
	/** 판의 왼쪽 끝. 창 폭에 따라 달라지는 값이라 아무 자리나 골랐다. */
	private static final int LEFT = 20;

	@BeforeAll
	static void 준비() {
		TestBootstrap.ensureInitialized();
	}

	/** 세트 셋. 켜진 것 하나와 아직인 것 둘이다. */
	private static final List<PerkSetLines.Entry> ENTRIES = List.of(
			new PerkSetLines.Entry("mining", "채굴", 3, 0, 1),
			new PerkSetLines.Entry("defense", "방어", 2, 3, 0),
			new PerkSetLines.Entry("power", "공격", 1, 2, 0));

	/** 화면이 부르는 것과 같은 길. {@code ClientPerkSets.lines} 가 이 목록을 만든다. */
	private static List<PerkSetLines.Line> lines(int count) {
		return PerkSetLines.visible(ENTRIES.subList(0, count), count);
	}

	/** 세트 덩어리가 시작하는 y. 화면의 {@code setBlockTop()} 과 같은 식이다. */
	private static int setBlockTop() {
		return PANEL_TOP + ROW_HEIGHT + 2;
	}

	/** 보유 증강 목록이 시작하는 y. 화면의 {@code perkListTop()} 과 같은 식이다. */
	private static int perkListTop(int lineCount) {
		return setBlockTop() + PerkSetLines.blockHeight(lineCount, ROW_HEIGHT, SET_BLOCK_GAP);
	}

	/** 화면의 {@code setBlockTooltip} 이 하는 판정 그대로. 어느 줄에도 없으면 −1 이다. */
	private static int rowAt(List<PerkSetLines.Line> lines, double mouseX, double mouseY) {
		int width = PerkSetLines.blockWidth(lines, FakeFont::width, 0);
		return PerkSetLines.rowAt(mouseX, mouseY, LEFT, width, setBlockTop(), ROW_HEIGHT,
				lines.size());
	}

	// ------------------------------------------------------------------ 세트 줄

	@Test
	void 세트_줄_위에서는_그_줄이_집힌다() {
		List<PerkSetLines.Line> lines = lines(3);

		assertEquals(3, lines.size());
		assertEquals(0, rowAt(lines, LEFT + 5, setBlockTop()));
		assertEquals(0, rowAt(lines, LEFT + 5, setBlockTop() + ROW_HEIGHT - 1));
		assertEquals(1, rowAt(lines, LEFT + 5, setBlockTop() + ROW_HEIGHT));
		assertEquals(2, rowAt(lines, LEFT + 5, setBlockTop() + ROW_HEIGHT * 3 - 1));
	}

	/**
	 * 세트 줄은 글자 폭만큼만 마우스를 받는다.
	 *
	 * <p>판 오른쪽 빈자리까지 받으면 마우스를 어디에 두어도 상자가 떠 다른 것을 읽을 수 없다.
	 */
	@Test
	void 줄_오른쪽_빈자리는_안_집힌다() {
		List<PerkSetLines.Line> lines = lines(3);
		int width = PerkSetLines.blockWidth(lines, FakeFont::width, 0);

		assertTrue(width > 0, "글자 폭을 못 재면 세트 줄 위에서도 툴팁이 안 뜬다");
		assertEquals(-1, rowAt(lines, LEFT + width, setBlockTop()));
		assertEquals(-1, rowAt(lines, LEFT - 1, setBlockTop()));
	}

	// ------------------------------------------------------------------ 보유 증강 목록 자리

	/**
	 * 목록 자리에서는 세트 줄이 하나도 집히지 않는다.
	 *
	 * <p>목록은 스크롤되고 창 아래까지 길게 이어지므로, 판정이 조금이라도 아래로 흘러내리면
	 * 목록 전체에서 상자가 뜬다.
	 */
	@Test
	void 보유_증강_목록_자리에서는_아무_세트_줄도_안_집힌다() {
		List<PerkSetLines.Line> lines = lines(3);
		int listTop = perkListTop(3);

		for (int offset : new int[] {0, 1, 12, 40, 120, 240}) {
			assertEquals(-1, rowAt(lines, LEFT + 5, listTop + offset),
					"목록 자리 " + offset + "px 아래에서 세트 줄이 집혔다");
		}
	}

	@Test
	void 세트_덩어리와_목록_사이의_틈도_비어_있다() {
		List<PerkSetLines.Line> lines = lines(3);

		for (int y = setBlockTop() + ROW_HEIGHT * 3; y < perkListTop(3); y++) {
			assertEquals(-1, rowAt(lines, LEFT + 5, y), "틈에서 세트 줄이 집혔다");
		}
	}

	/**
	 * 두 자리는 세트 줄이 몇 줄이든 겹치지 않는다.
	 *
	 * <p>세트가 한 줄 늘 때마다 목록이 그만큼 내려간다. 그 두 값이 같은 식을 보지 않으면 어느
	 * 줄 수에서만 겹치는 화면이 나온다.
	 */
	@Test
	void 세트_자리와_목록_자리는_줄_수가_늘어도_겹치지_않는다() {
		for (int count = 1; count <= 6; count++) {
			assertTrue(setBlockTop() + count * ROW_HEIGHT <= perkListTop(count),
					"세트 " + count + "줄에서 목록 자리가 세트 줄을 파고든다");
		}
	}

	@Test
	void 세트_줄이_하나도_없으면_어디서도_안_집힌다() {
		// 세트를 하나도 모으지 않은 사람의 화면이다. 목록이 머리글 바로 아래까지 올라온다.
		assertEquals(setBlockTop(), perkListTop(0));
		assertEquals(-1, rowAt(List.of(), LEFT + 5, setBlockTop()));
		assertEquals(-1, rowAt(List.of(), LEFT + 5, setBlockTop() + 100));
	}
}
