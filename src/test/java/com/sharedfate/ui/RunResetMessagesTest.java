package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서버 초기화 확인 문구.
 *
 * <p>이 문구는 <b>사람이 되돌릴 수 없는 것을 누르기 직전에 읽는 마지막 글</b>이다. 실제로
 * 지우는 {@code RunResetCoordinator} 는 살아 있는 서버가 있어야 해서 단위 시험으로 닿지
 * 않지만, 「무엇이 사라지는지 빠뜨리고 적는」 사고는 글자만 봐도 잡힌다. 그래서
 * {@link TeamDisbandWarning} 과 같은 방식으로 글자만 떼어 두고 여기서 붙들어 둔다.
 */
class RunResetMessagesTest {

	/**
	 * 지워지는 다섯 가지가 <b>다 있어야 한다.</b>
	 *
	 * <p>하나라도 빠지면 「초기화해도 피해 기록은 남는 줄 알았다」 같은 말이 나오고, 그때는
	 * 이미 서버가 새 월드로 다시 열린 뒤다.
	 */
	@Test
	void 확인_문구는_지워지는_다섯_가지를_모두_적는다() {
		List<String> lines = RunResetMessages.confirmationLines(3, 1, 30);

		for (String item : new String[] {"· 회차", "· 팀", "· 보유 증강", "· 피해 기록", "· 월드"}) {
			assertTrue(lines.stream().anyMatch(line -> line.startsWith(item)),
					item + " 줄이 있어야 한다");
		}
	}

	/** 지금 회차가 1로 돌아간다는 사실을 숫자로 보여 준다. */
	@Test
	void 회차_줄은_지금_회차와_돌아갈_회차를_함께_적는다() {
		String text = RunResetMessages.confirmation(7, 1, 30);

		assertTrue(text.contains("7회차"), "지금 회차가 보여야 한다");
		assertTrue(text.contains("1회차"), "돌아갈 회차가 보여야 한다");
	}

	/** 회차 번호가 손상돼 0 이나 음수로 들어와도 「0회차」 같은 글자는 나오지 않는다. */
	@Test
	void 회차_번호는_최소_일로_접는다() {
		String text = RunResetMessages.confirmation(0, 0, 30);

		assertFalse(text.contains("0회차"), "없는 회차를 적으면 안 된다");
	}

	/** 팀이 없는 서버에서 「0개 팀이 사라집니다」는 거짓말이다. */
	@Test
	void 팀이_없으면_없다고_적는다() {
		assertTrue(RunResetMessages.confirmation(1, 0, 30).contains("지금은 팀이 없습니다"));
		assertTrue(RunResetMessages.confirmation(1, 2, 30).contains("2개"));
	}

	/**
	 * 루프 스크립트 없이 띄운 서버는 <b>꺼진 채 돌아오지 않는다.</b>
	 *
	 * <p>사람이 「막지는 말고 확인 문구에 크게 적는다」고 정했다. 그러므로 이 경고가 빠지는 것은
	 * 문구가 아니라 <b>기능</b>이 빠지는 것이다.
	 */
	@Test
	void 확인_문구는_루프_스크립트_없이_띄운_서버를_경고한다() {
		String text = RunResetMessages.confirmation(1, 1, 30);

		assertTrue(text.contains("java -jar"), "어떻게 띄웠을 때인지 적어야 한다");
		assertTrue(text.contains("sharedfate-server-loop.ps1"), "무엇이 있어야 하는지 적어야 한다");
		assertTrue(text.contains("⚠"), "경고 표시가 있어야 한다");
	}

	/** 30초 안에 무엇을 쳐야 하는지가 문구에 그대로 있어야 한다. */
	@Test
	void 확인_문구는_무엇을_언제까지_쳐야_하는지_적는다() {
		String text = RunResetMessages.confirmation(1, 1, 30);

		assertTrue(text.contains("30초"));
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			assertTrue(text.contains("/" + word), "/" + word + " 가 문구에 있어야 한다");
		}
		assertTrue(text.contains("/shareteam " + RunResetMessages.CONFIRM_WORD_EN),
				"하위 명령으로도 받는다는 사실을 적어야 한다");
	}

	/** 확인 낱말은 둘뿐이고 순서도 정해져 있다. 명령 트리가 이 목록을 그대로 쓴다. */
	@Test
	void 확인_낱말은_yes_와_수락_둘이다() {
		assertEquals(List.of("yes", "수락"), RunResetMessages.CONFIRM_WORDS);
		assertEquals("yes", RunResetMessages.CONFIRM_WORD_EN);
		assertEquals("수락", RunResetMessages.CONFIRM_WORD_KO);
	}

	/** 사람이 정한 대기 시간은 30초다. 문구와 만료 판정이 같은 값을 써야 한다. */
	@Test
	void 대기_시간은_삼십초다() {
		assertEquals(30, RunResetMessages.TIMEOUT_SECONDS);
	}

	@Test
	void 만료_문구는_시간이_지났다고_말한다() {
		String text = RunResetMessages.expired();

		assertTrue(text.contains("시간이 지났습니다"));
		assertTrue(text.contains("/" + RunResetMessages.RESET_COMMAND),
				"다시 하려면 무엇을 쳐야 하는지 적어야 한다");
	}

	/** 남이 친 요청을 가로채려 했을 때. 누가 요청했는지 이름을 적어 준다. */
	@Test
	void 요청자가_아니면_누가_요청했는지_알려_준다() {
		String text = RunResetMessages.notRequester("카이렌");

		assertTrue(text.contains("카이렌"));
		assertTrue(text.contains("요청한 사람만"));
	}

	@Test
	void 대기가_없으면_먼저_요청하라고_알려_준다() {
		assertTrue(RunResetMessages.nothingPending().contains("/" + RunResetMessages.RESET_COMMAND));
	}

	@Test
	void 이미_기다리는_요청이_있으면_누구의_것인지_알려_준다() {
		String text = RunResetMessages.alreadyWaiting("카이렌", 12);

		assertTrue(text.contains("카이렌"));
		assertTrue(text.contains("12초"));
	}

	/** 전원에게 나가는 한 줄. 몇 초 뒤에 무슨 일이 일어나는지가 들어 있어야 한다. */
	@Test
	void 전원_공지는_남은_시간과_무슨_일이_일어나는지_적는다() {
		String text = RunResetMessages.announcement(5);

		assertTrue(text.contains("5초"));
		assertTrue(text.contains("초기화"));
		assertTrue(text.contains("1회차"));
	}

	/** 표식을 못 써서 그만둔 경우. <b>아무것도 지우지 않았다</b>는 사실이 가장 중요하다. */
	@Test
	void 표식_실패_문구는_아무것도_지우지_않았다고_말한다() {
		assertTrue(RunResetMessages.markerFailed().contains("아무것도 지우지 않았습니다"));
	}

	/**
	 * 거절당한 뒤에는 <b>되묻기가 이미 사라진 뒤</b>다.
	 *
	 * <p>{@code RunResetCommand.confirm} 은 수락을 받아들이면서 대기를 비우고 실행한다. 그래서
	 * 여기서 실패한 사람은 {@code /yes} 를 다시 쳐도 「기다리는 요청이 없습니다」만 본다.
	 * 문구가 처음부터 다시 하라고 말해 주지 않으면 그 자리에서 막힌다.
	 */
	@Test
	void 실패_문구는_처음부터_다시_하라고_알려_준다() {
		for (String text : new String[] {
				RunResetMessages.markerFailed(),
				RunResetMessages.staleWorldMarker("C:\\서버\\.sharedfate-world-reset.pending")}) {
			assertTrue(text.contains("/" + RunResetMessages.RESET_COMMAND),
					"다시 무엇을 쳐야 하는지 적어야 한다: " + text);
		}
	}

	/**
	 * 싱글플레이·LAN 에서는 <b>전용 서버에서만 쓸 수 있다</b>고 말한다.
	 *
	 * <p>여기에 「다시 시도하세요」를 적으면 안 된다. 서버를 띄운 방식 때문이라 몇 번을 쳐도
	 * 똑같이 거절당한다.
	 */
	@Test
	void 전용_서버_전용이라는_사실을_말한다() {
		String text = RunResetMessages.worldNotResettable();

		assertTrue(text.contains("전용 서버"), "무엇에서만 되는지 적어야 한다");
		assertTrue(text.contains("아무것도 지우지 않았습니다"), "지우지 않았다는 사실이 먼저다");
		assertFalse(text.contains("/" + RunResetMessages.RESET_COMMAND),
				"다시 쳐도 소용없는 경우에 다시 치라고 하면 안 된다");
	}

	/**
	 * 낡은 월드 표식이 남아 있는 경우.
	 *
	 * <p>이 파일이 있으면 루프 스크립트가 <b>기동 자체를 거부</b>한다. 경로를 적어 주지 않으면
	 * 운영자는 서버가 왜 안 뜨는지 알아낼 길이 없다 — 이름이 점으로 시작하는 숨김 파일이다.
	 */
	@Test
	void 낡은_월드_표식_문구는_경로와_까닭을_적는다() {
		String marker = "C:\\서버\\.sharedfate-world-reset.pending";
		String text = RunResetMessages.staleWorldMarker(marker);

		assertTrue(text.contains(marker), "지울 파일의 경로를 그대로 적어야 한다");
		assertTrue(text.contains("아무것도 지우지 않았습니다"), "지우지 않았다는 사실이 먼저다");
	}
}
