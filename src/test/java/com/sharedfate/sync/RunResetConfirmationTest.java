package com.sharedfate.sync;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「누가·언제까지」 수락할 수 있는가.
 *
 * <p>실제로 명령을 받는 {@code RunResetCommand} 는 살아 있는 서버가 있어야 해서 단위 시험으로
 * 닿지 않는다. 그래서 대기 상태만 순수 로직으로 떼어 두고, <b>남이 대신 수락하는 사고</b>와
 * <b>영원히 안 끝나는 대기</b>를 여기서 붙들어 둔다.
 */
class RunResetConfirmationTest {
	private static final UUID REQUESTER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test
	void 아무것도_요청하지_않았으면_수락할_것도_없다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();

		assertFalse(confirmation.pending());
		assertNull(confirmation.requester());
		assertEquals(RunResetConfirmation.Answer.NOTHING_PENDING, confirmation.answer(REQUESTER));
	}

	@Test
	void 요청한_사람이_수락하면_받아들이고_대기를_비운다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "카이렌");

		assertTrue(confirmation.pending());
		assertEquals(REQUESTER, confirmation.requester());
		assertEquals("카이렌", confirmation.requesterName());
		assertEquals(RunResetConfirmation.Answer.ACCEPTED, confirmation.answer(REQUESTER));
		assertFalse(confirmation.pending(), "한 번 수락하면 대기가 사라진다");
		assertEquals(RunResetConfirmation.Answer.NOTHING_PENDING, confirmation.answer(REQUESTER),
				"같은 수락을 두 번 먹으면 서버가 두 번 초기화된다");
	}

	/**
	 * <b>친 사람만</b> 수락할 수 있다.
	 *
	 * <p>남이 대신 수락해 버리면 「누가 서버를 날렸는지」를 아무도 모르게 된다. 그리고 남의
	 * 수락으로 대기가 <b>사라지지도</b> 않아야 한다 — 사라지면 옆 사람이 아무 말이나 쳐서
	 * 남의 확인 절차를 조용히 취소시킬 수 있다.
	 */
	@Test
	void 남이_수락하면_거절하고_대기는_그대로_둔다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "카이렌");

		assertEquals(RunResetConfirmation.Answer.NOT_REQUESTER, confirmation.answer(OTHER));
		assertTrue(confirmation.pending(), "남의 수락으로 대기가 사라지면 안 된다");
		assertEquals(RunResetConfirmation.Answer.ACCEPTED, confirmation.answer(REQUESTER));
	}

	/** 30초는 600틱이다. 문구에 적는 초와 실제로 세는 틱이 어긋나면 안 된다. */
	@Test
	void 대기는_삼십초_육백틱이다() {
		assertEquals(30, RunResetConfirmation.TIMEOUT_SECONDS);
		assertEquals(600, RunResetConfirmation.TIMEOUT_TICKS);
	}

	/**
	 * 마지막 한 틱에서 만료되고, 만료는 <b>딱 한 번만</b> 알린다.
	 *
	 * <p>두 번 알리면 「시간이 지났습니다」가 매 틱 쏟아진다.
	 */
	@Test
	void 삼십초가_지나면_한_번만_만료를_알린다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "카이렌");

		for (int tick = 1; tick < RunResetConfirmation.TIMEOUT_TICKS; tick++) {
			assertFalse(confirmation.tick(), tick + "틱에서는 아직 살아 있어야 한다");
		}

		assertTrue(confirmation.tick(), "마지막 틱에서 만료된다");
		assertFalse(confirmation.pending());
		assertFalse(confirmation.tick(), "만료는 한 번만 알린다");
	}

	/** 만료된 뒤의 수락은 아무 일도 하지 않는다. */
	@Test
	void 만료된_뒤에는_요청자도_수락할_수_없다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "카이렌");
		for (int tick = 0; tick < RunResetConfirmation.TIMEOUT_TICKS; tick++) {
			confirmation.tick();
		}

		assertEquals(RunResetConfirmation.Answer.NOTHING_PENDING, confirmation.answer(REQUESTER));
	}

	/** 남은 시간은 올림해서 보여 준다. 1틱이라도 남아 있으면 1초다. */
	@Test
	void 남은_시간은_올림해서_보여_준다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "카이렌");

		assertEquals(30, confirmation.remainingSeconds());
		for (int tick = 0; tick < 599; tick++) {
			confirmation.tick();
		}
		assertEquals(1, confirmation.remainingSeconds(), "마지막 1틱도 1초로 보여 준다");
	}

	/** 다시 요청하면 시간이 처음부터 다시 흐른다. */
	@Test
	void 다시_요청하면_시간이_처음부터_흐른다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "카이렌");
		for (int tick = 0; tick < 500; tick++) {
			confirmation.tick();
		}

		confirmation.request(REQUESTER, "카이렌");

		assertEquals(RunResetConfirmation.TIMEOUT_TICKS, confirmation.remainingTicks());
	}

	/** 이름을 못 얻은 경우에도 빈칸이 화면에 나가면 안 된다. */
	@Test
	void 이름이_비어_있으면_대신_적을_말을_쓴다() {
		RunResetConfirmation confirmation = new RunResetConfirmation();
		confirmation.request(REQUESTER, "  ");

		assertFalse(confirmation.requesterName().isBlank());
	}
}
