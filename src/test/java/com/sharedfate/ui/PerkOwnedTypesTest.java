package com.sharedfate.ui;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 보유 증강 줄에서 「이 증강은 어느 유형인가」를 이름표로 되짚기.
 *
 * <p>보유 목록이 유형 id 를 안 실어 와서 생긴 우회로다({@link PerkOwnedTypes}). 이 시험이
 * 지키는 것은 둘이다 — <b>유형이 둘인 증강에서 하나도 빠뜨리지 않을 것</b>, 그리고
 * <b>되짚지 못했을 때 조용히 빈손으로 돌아올 것</b>. 못 찾았을 때 아무 유형이나 고르면 엉뚱한
 * 세트의 단계 설명이 뜬다.
 */
class PerkOwnedTypesTest {

	@BeforeAll
	static void 준비() {
		TestBootstrap.ensureInitialized();
	}

	private static List<PerkSetTooltip.Entry> catalog() {
		return List.of(
				new PerkSetTooltip.Entry("mining", "굴착기", "gold", true),
				new PerkSetTooltip.Entry("mining", "광맥 감각", "silver", false),
				// 유형 둘에 걸친 증강. 이름표가 유형마다 한 줄씩 실린다.
				new PerkSetTooltip.Entry("power", "돌주먹", "gold", true),
				new PerkSetTooltip.Entry("defense", "돌주먹", "gold", true));
	}

	@Test
	void 가진_증강의_유형을_이름으로_되짚는다() {
		assertEquals(List.of("mining"), PerkOwnedTypes.typeIdsOf(catalog(), "굴착기"));
	}

	@Test
	void 유형이_둘이면_둘_다_돌려준다() {
		// 하나만 고르면 나머지 유형의 진행도를 영영 못 본다.
		assertEquals(List.of("power", "defense"), PerkOwnedTypes.typeIdsOf(catalog(), "돌주먹"));
	}

	@Test
	void 같은_유형이_두_번_실려도_한_번만_담는다() {
		List<PerkSetTooltip.Entry> doubled = List.of(
				new PerkSetTooltip.Entry("mining", "굴착기", "gold", true),
				new PerkSetTooltip.Entry("mining", "굴착기", "gold", true));

		assertEquals(List.of("mining"), PerkOwnedTypes.typeIdsOf(doubled, "굴착기"));
	}

	@Test
	void 이름표에_없는_증강은_빈손이다() {
		// 세트 유형이 아예 없는 증강이거나, 이름표가 상한에 잘려 빠진 경우다. 그 줄에는
		// 툴팁이 안 뜰 뿐 아무것도 깨지지 않는다.
		assertTrue(PerkOwnedTypes.typeIdsOf(catalog(), "행운의 발").isEmpty());
		assertTrue(PerkOwnedTypes.typeIdsOf(catalog(), "").isEmpty());
		assertTrue(PerkOwnedTypes.typeIdsOf(catalog(), null).isEmpty());
		assertTrue(PerkOwnedTypes.typeIdsOf(null, "굴착기").isEmpty());
	}

	@Test
	void 아직_안_가진_증강도_되짚힌다() {
		// 이름표는 가진 것과 안 가진 것을 함께 싣는다. 되짚기는 그 구별을 보지 않는다.
		assertEquals(List.of("mining"), PerkOwnedTypes.typeIdsOf(catalog(), "광맥 감각"));
	}
}
