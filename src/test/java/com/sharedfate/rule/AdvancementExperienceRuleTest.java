package com.sharedfate.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 순수 계산 하나뿐이다 — 무엇을 넣어도 0이어야 한다. */
class AdvancementExperienceRuleTest {
	@Test
	void 언제나_0을_돌려준다() {
		assertEquals(0, AdvancementExperienceRule.strip(0));
		assertEquals(0, AdvancementExperienceRule.strip(1));
		assertEquals(0, AdvancementExperienceRule.strip(100));
		assertEquals(0, AdvancementExperienceRule.strip(Integer.MAX_VALUE));
	}
}
