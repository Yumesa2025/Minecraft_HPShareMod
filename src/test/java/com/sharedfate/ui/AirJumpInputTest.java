package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirJumpInputTest {

	/** 발판 위. */
	private static final boolean GROUND = true;
	/** 공중. */
	private static final boolean AIR = false;
	/** 점프 키가 눌려 있다. */
	private static final boolean DOWN = true;
	/** 점프 키를 뗐다. */
	private static final boolean UP = false;
	/** 공중 점프가 성립하는 상태(증강 보유·관전 아님 등). */
	private static final boolean OK = true;

	// ------------------------------------------------------------------ 기본 흐름

	@Test
	void 땅에서는_아무리_눌러도_뛰지_않는다() {
		AirJumpInput input = new AirJumpInput();

		assertFalse(input.tick(GROUND, DOWN, OK));
		assertFalse(input.tick(GROUND, UP, OK));
		assertFalse(input.tick(GROUND, DOWN, OK));
	}

	/**
	 * 이 시험이 이 파일의 존재 이유다.
	 *
	 * <p>바닐라는 땅에서 점프한 <b>그 틱 안에서</b> 이미 땅을 떠난다. 그래서 클라이언트 틱이
	 * 끝날 때 "방금 눌림"과 "지금 공중"이 동시에 참이 되고, 그 둘만 보면 스페이스 한 번이
	 * 땅 점프와 공중 점프를 함께 써 버린다. 두 점프가 같은 틱에 겹치므로 두 번째 점프는
	 * 눈에 보이지 않는다 — 예전에 "더블 점프한 느낌이 없다"던 것이 바로 이 자리였다.
	 */
	@Test
	void 땅에서_뛴_그_틱은_공중_점프로_치지_않는다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);

		// 스페이스를 누른 틱. 바닐라가 이미 몸을 띄워 두어 공중으로 보인다.
		assertFalse(input.tick(AIR, DOWN, OK));
		// 계속 누르고 있어도 마찬가지다.
		assertFalse(input.tick(AIR, DOWN, OK));
		assertFalse(input.tick(AIR, DOWN, OK));
		assertFalse(input.used());
	}

	@Test
	void 공중에서_뗐다_다시_누르면_뛴다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);
		input.tick(AIR, DOWN, OK);

		// 뗀다. 아직 뛰지는 않지만 다음 누름을 받을 준비가 된다.
		assertFalse(input.tick(AIR, UP, OK));
		assertTrue(input.armed());

		assertTrue(input.tick(AIR, DOWN, OK));
		assertTrue(input.used());
	}

	@Test
	void 착지하기_전이면_높이도_방향도_따지지_않는다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);
		input.tick(AIR, DOWN, OK);
		input.tick(AIR, UP, OK);

		// 한참 떠 있다가 눌러도 그대로 받아들인다.
		for (int i = 0; i < 40; i++) {
			assertFalse(input.tick(AIR, UP, OK));
		}
		assertTrue(input.tick(AIR, DOWN, OK));
	}

	@Test
	void 절벽에서_걸어_나가면_첫_누름이_공중_점프다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);

		// 키를 누르지 않은 채 땅이 사라졌다. 그 틱에 이미 준비가 된다.
		assertFalse(input.tick(AIR, UP, OK));
		assertTrue(input.tick(AIR, DOWN, OK));
	}

	// ------------------------------------------------------------------ 한 번 뜨면 한 번

	@Test
	void 한_번_뜬_동안_두_번은_못_쓴다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);
		input.tick(AIR, UP, OK);
		assertTrue(input.tick(AIR, DOWN, OK));

		input.tick(AIR, UP, OK);
		assertFalse(input.tick(AIR, DOWN, OK));
		input.tick(AIR, UP, OK);
		assertFalse(input.tick(AIR, DOWN, OK));
	}

	@Test
	void 발판에_닿으면_다시_한_번_쓸_수_있다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);
		input.tick(AIR, UP, OK);
		assertTrue(input.tick(AIR, DOWN, OK));

		// 물이든 사다리든 발판으로 넘겨받는다. 여기서는 참/거짓 하나로만 다룬다.
		input.tick(GROUND, UP, OK);
		assertFalse(input.used());

		input.tick(AIR, UP, OK);
		assertTrue(input.tick(AIR, DOWN, OK));
	}

	@Test
	void 계속_누른_채_땅에_닿았다_떠도_저절로_뛰지_않는다() {
		AirJumpInput input = new AirJumpInput();
		// 스페이스를 붙잡고 통통 뛰는 상황. 키를 뗀 적이 없으므로 공중 점프는 없다.
		for (int i = 0; i < 10; i++) {
			assertFalse(input.tick(GROUND, DOWN, OK));
			assertFalse(input.tick(AIR, DOWN, OK));
			assertFalse(input.tick(AIR, DOWN, OK));
		}
	}

	// ------------------------------------------------------------------ 못 쓰는 상태

	@Test
	void 쓸_수_없는_상태면_한_번을_소비하지도_않는다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);
		input.tick(AIR, UP, OK);

		// 증강이 없거나 관전 중이라 지금은 안 된다.
		assertFalse(input.tick(AIR, DOWN, false));
		assertFalse(input.used());

		// 조건이 풀린 뒤 다시 누르면 그때는 된다.
		input.tick(AIR, UP, OK);
		assertTrue(input.tick(AIR, DOWN, OK));
	}

	@Test
	void 못_쓰는_동안_눌러_둔_키는_조건이_풀려도_터지지_않는다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, false);
		input.tick(AIR, UP, false);
		// 못 쓰는 동안 눌렀다. 여기서 눌린 것으로 기억해 두어야 한다.
		assertFalse(input.tick(AIR, DOWN, false));

		// 누른 채로 조건이 풀렸다. 새로 누른 것이 아니므로 뛰지 않는다.
		assertFalse(input.tick(AIR, DOWN, OK));
	}

	// ------------------------------------------------------------------ 되돌리기

	@Test
	void 비우면_처음_상태로_돌아간다() {
		AirJumpInput input = new AirJumpInput();
		input.tick(GROUND, UP, OK);
		input.tick(AIR, UP, OK);
		assertTrue(input.tick(AIR, DOWN, OK));

		input.reset();
		assertFalse(input.armed());
		assertFalse(input.used());

		// 비운 직후 공중에서 누르고 있어도, 뗀 적이 없으므로 바로 뛰지는 않는다.
		assertFalse(input.tick(AIR, DOWN, OK));
		input.tick(AIR, UP, OK);
		assertTrue(input.tick(AIR, DOWN, OK));
	}
}
