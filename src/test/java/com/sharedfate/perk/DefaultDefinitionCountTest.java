package com.sharedfate.perk;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultDefinitionCount#isStale} 판정만 떼어 내 시험한다.
 *
 * <p>실제 로깅과 번들 리소스를 읽는 파일 IO 는 {@link PerkRegistry}·{@link PerkSetRegistry}
 * 안에 있어 여기서는 닿지 않는다 — 여기서는 "다르면 경고, 같으면 조용히, 모르면 조용히"
 * 라는 판정 규칙만 본다.
 */
class DefaultDefinitionCountTest {

	@Test
	void 기대와_실제가_같으면_경고하지_않는다() {
		assertFalse(DefaultDefinitionCount.isStale(OptionalInt.of(94), 94));
	}

	@Test
	void 기대와_실제가_다르면_경고한다() {
		assertTrue(DefaultDefinitionCount.isStale(OptionalInt.of(94), 47));
	}

	@Test
	void 실제가_기대보다_많아도_경고한다() {
		// 판을 내렸다가 다시 올린 경우처럼 실제 값이 더 큰 쪽도 "다르다" 라는 사실은
		// 똑같이 알려야 한다 — 어느 방향이든 옛 파일을 의심할 이유가 된다.
		assertTrue(DefaultDefinitionCount.isStale(OptionalInt.of(47), 94));
	}

	@Test
	void 번들을_못_읽어_기대를_모르면_경고하지_않는다() {
		// 번들 리소스를 못 읽거나 파싱에 실패했을 때다. 모르는 걸 안다고 우겨서
		// 새 오류를 만들면 안 된다.
		assertFalse(DefaultDefinitionCount.isStale(OptionalInt.empty(), 0));
		assertFalse(DefaultDefinitionCount.isStale(OptionalInt.empty(), 94));
	}
}
