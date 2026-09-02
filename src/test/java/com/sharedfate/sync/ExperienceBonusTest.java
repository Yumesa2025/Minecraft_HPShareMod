package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.config.SharedFateConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 경험치 획득 배율(상시 규칙)의 계산부.
 *
 * <p>실제로 오브가 떨어지는 자리는 {@code ExperienceOrbAwardMixin} 이라 살아 있는 서버 없이는
 * 닿지 않는다. 대신 그 mixin 이 부르는 계산 하나를 여기서 전부 못박는다 — 배율·반올림·경계값이
 * 조용히 바뀌면 회차의 성장 속도가 통째로 달라진다.
 */
class ExperienceBonusTest {

	@Test
	void 기본_배율은_일점이배다() {
		// 몹 하나 5점 → 6점, 광석 7점 → 8점(반올림 8.4 → 8).
		assertEquals(6, ExperienceBonus.scale(5, 1.2));
		assertEquals(8, ExperienceBonus.scale(7, 1.2));
		assertEquals(12, ExperienceBonus.scale(10, 1.2));
	}

	@Test
	void 가장_가까운_정수로_반올림한다() {
		assertEquals(2, ExperienceBonus.scale(2, 1.2), "2.4 는 2 로 내린다");
		assertEquals(4, ExperienceBonus.scale(3, 1.2), "3.6 은 4 로 올린다");
	}

	@Test
	void 배율이_1이면_손대지_않는다() {
		for (int amount : new int[] {0, 1, 7, 1000}) {
			assertEquals(amount, ExperienceBonus.scale(amount, 1.0));
		}
	}

	@Test
	void 없거나_음수인_양은_그대로_둔다() {
		// 바닐라가 0 을 주는 자리(경험치가 없는 블록 등)에서 1 을 만들어 내면 안 된다.
		assertEquals(0, ExperienceBonus.scale(0, 1.2));
		assertEquals(-5, ExperienceBonus.scale(-5, 1.2));
	}

	@Test
	void 있던_경험치가_배율_때문에_사라지지는_않는다() {
		// 배율을 낮게 잡은 서버에서 작은 오브가 통째로 없어지면 「경험치가 안 나온다」로 보인다.
		assertEquals(1, ExperienceBonus.scale(1, 0.1));
		assertEquals(1, ExperienceBonus.scale(2, 0.1));
	}

	@Test
	void 이상한_배율은_무시한다() {
		assertEquals(5, ExperienceBonus.scale(5, 0.0));
		assertEquals(5, ExperienceBonus.scale(5, -1.0));
		assertEquals(5, ExperienceBonus.scale(5, Double.NaN));
		assertEquals(5, ExperienceBonus.scale(5, Double.POSITIVE_INFINITY));
	}

	@Test
	void 아주_큰_값도_넘치지_않는다() {
		assertEquals(Integer.MAX_VALUE, ExperienceBonus.scale(Integer.MAX_VALUE, 10.0));
	}

	/**
	 * 설정을 아직 읽지 않은 자리(단위 시험, 모드 초기화 전)에서도 안전해야 한다. 여기서
	 * 터지면 경험치를 주는 모든 경로가 함께 터진다.
	 */
	@Test
	void 설정이_없으면_배율이_1이다() {
		SharedFateConfig previous = SharedFateMod.config;
		try {
			SharedFateMod.config = null;
			assertEquals(1.0, ExperienceBonus.multiplier(), 1.0e-9);
			assertEquals(9, ExperienceBonus.scale(9));
		} finally {
			SharedFateMod.config = previous;
		}
	}

	@Test
	void 설정에_적힌_배율을_읽는다() {
		SharedFateConfig previous = SharedFateMod.config;
		try {
			SharedFateConfig config = new SharedFateConfig();
			SharedFateMod.config = config;
			assertEquals(1.2, ExperienceBonus.multiplier(), 1.0e-9, "기본값이 곧 1.2배다");
			assertEquals(6, ExperienceBonus.scale(5));

			config.experienceMultiplier = 2.0;
			assertEquals(10, ExperienceBonus.scale(5));
		} finally {
			SharedFateMod.config = previous;
		}
	}
}
