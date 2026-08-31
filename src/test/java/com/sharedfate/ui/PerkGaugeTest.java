package com.sharedfate.ui;

import com.sharedfate.perk.PerkMilestones;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkGaugeTest {

	private static final float EPSILON = 0.0001F;

	@Test
	void 구간을_막_지나면_비어_있다() {
		assertEquals(0.0F, PerkGauge.fraction(5, 5), EPSILON);
	}

	@Test
	void 구간에_닿으면_가득_찬다() {
		assertEquals(1.0F, PerkGauge.fraction(0, 5), EPSILON);
	}

	@Test
	void 한_칸_안에서는_고르게_찬다() {
		assertEquals(0.2F, PerkGauge.fraction(4, 5), EPSILON);
		assertEquals(0.4F, PerkGauge.fraction(3, 5), EPSILON);
		assertEquals(0.6F, PerkGauge.fraction(2, 5), EPSILON);
		assertEquals(0.8F, PerkGauge.fraction(1, 5), EPSILON);
	}

	@Test
	void 레벨을_써서_이전_구간_아래로_내려가면_빈_막대다() {
		// 인챈트로 레벨을 쏟아부어 남은 레벨이 한 칸을 넘어간 경우.
		assertEquals(0.0F, PerkGauge.fraction(9, 5), EPSILON);
		assertEquals(0.0F, PerkGauge.fraction(40, 5), EPSILON);
	}

	@Test
	void 남은_레벨이_음수여도_범위를_벗어나지_않는다() {
		assertEquals(1.0F, PerkGauge.fraction(-1, 5), EPSILON);
	}

	@Test
	void 칸_길이가_0이어도_나눗셈이_깨지지_않는다() {
		float value = PerkGauge.fraction(0, 0);
		assertTrue(value >= 0.0F && value <= 1.0F, "0~1 밖으로 나갔다: " + value);
	}

	@Test
	void 실제_구간_간격으로도_0에서_1_사이다() {
		// 구간 만렙이 35에서 40으로 바뀌어도 STEP 을 그대로 쓰므로 이 시험은 그대로 통과한다.
		for (int remaining = 0; remaining <= PerkMilestones.MAX; remaining++) {
			float value = PerkGauge.fraction(remaining, PerkMilestones.STEP);
			assertTrue(value >= 0.0F && value <= 1.0F, remaining + " 남았을 때 " + value);
		}
		assertEquals(1.0F, PerkGauge.fraction(0, PerkMilestones.STEP), EPSILON);
		assertEquals(0.0F, PerkGauge.fraction(PerkMilestones.STEP, PerkMilestones.STEP), EPSILON);
	}
}
