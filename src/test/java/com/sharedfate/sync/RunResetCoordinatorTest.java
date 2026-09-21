package com.sharedfate.sync;

import com.sharedfate.sync.RunResetCoordinator.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 초기화 명령이 <b>거절할 때</b>의 판정과 그때 사람이 보는 글.
 *
 * <p>{@link RunResetCoordinator#reset} 자체는 살아 있는 {@code MinecraftServer} 가 있어야 해서
 * 단위 시험으로 닿지 않는다. 그래서 <b>닿는 것을 전부 밖으로 뺐다</b> — 사전 검증 결과를 명령이
 * 쓰는 {@link Result} 로 옮기는 판정과, 그 {@link Result} 마다의 문구다.
 *
 * <p>여기가 통째로 비어 있던 동안 「초기화가 반쪽만 실행된다」는 결함이 그대로 들어왔다. 새
 * {@link Result} 를 문구 없이 늘리는 일도 여기서 막는다 — 값만 늘고 문구가 없으면 운영자는
 * <b>아무 말도 없이 실패하는 명령</b>을 보게 된다.
 */
class RunResetCoordinatorTest {

	/** 문구에 끼워 넣을 표식 경로. 실제 파일을 만들 필요가 없다. */
	private static final String MARKER_PATH =
			"C:\\서버\\" + WorldResetCoordinator.MARKER_FILE_NAME;

	/** 사전 검증이 통과하면 막을 것이 없다. */
	@Test
	void 사전_검증이_통과하면_막지_않는다() {
		assertNull(RunResetCoordinator.blockedBy(RunResetPreflight.Outcome.READY));
	}

	/**
	 * 사전 검증이 걸리면 그 이유가 그대로 {@link Result} 로 나온다.
	 *
	 * <p>이 값을 받은 명령은 <b>아무것도 지우지 않고</b> 끝난다. 예전에는 같은 상황이
	 * 카운트다운이 다 끝난 뒤에야 드러났고, 그때는 팀도 피해 기록도 이미 없었다.
	 */
	@Test
	void 사전_검증이_걸리면_같은_이유로_거절한다() {
		assertEquals(Result.WORLD_NOT_UNDER_SERVER_ROOT,
				RunResetCoordinator.blockedBy(RunResetPreflight.Outcome.WORLD_NOT_UNDER_SERVER_ROOT));
		assertEquals(Result.STALE_WORLD_MARKER,
				RunResetCoordinator.blockedBy(RunResetPreflight.Outcome.STALE_WORLD_MARKER));
	}

	/** 시작한 경우만 성공이다. 나머지는 전부 「아무것도 지우지 않았다」이다. */
	@Test
	void 시작한_경우만_성공이다() {
		assertTrue(Result.STARTED.started());
		for (Result result : Result.values()) {
			if (result != Result.STARTED) {
				assertFalse(result.started(), result + " 는 성공이 아니다");
			}
		}
	}

	/**
	 * <b>모든</b> 실패 값에 사람이 읽을 문구가 있어야 한다.
	 *
	 * <p>값만 늘리고 문구를 빠뜨리면 운영자에게는 빈 줄이 나간다. 되돌릴 수 없는 명령이
	 * 아무 말도 없이 실패하는 것이 가장 나쁘다.
	 */
	@Test
	void 실패_값마다_문구가_있다() {
		for (Result result : Result.values()) {
			String text = result.failureText(MARKER_PATH);
			if (result == Result.STARTED) {
				assertTrue(text.isEmpty(), "성공에는 실패 문구가 없다");
				continue;
			}
			assertFalse(text.isBlank(), result + " 에 문구가 있어야 한다");
		}
	}

	/**
	 * 싱글플레이·LAN 에서는 <b>전용 서버에서만 쓸 수 있다</b>는 뜻이 전해져야 한다.
	 *
	 * <p>여기서 「로그를 확인하세요」로 끝나면 운영자는 고칠 수 없는 것을 고치려고 붙들게 된다.
	 * 이 경우는 설정이 아니라 <b>서버를 띄운 방식</b> 때문이다.
	 */
	@Test
	void 전용_서버가_아니면_그렇게_말한다() {
		String text = Result.WORLD_NOT_UNDER_SERVER_ROOT.failureText(MARKER_PATH);

		assertTrue(text.contains("전용 서버"), "무엇에서만 되는지 적어야 한다");
		assertTrue(text.contains("아무것도 지우지 않았습니다"), "지우지 않았다는 사실이 먼저다");
	}

	/**
	 * 낡은 표식 때문에 거절했으면 <b>지워야 할 파일의 경로</b>를 적어 준다.
	 *
	 * <p>이 상태의 서버는 루프 스크립트가 기동 자체를 거부한다. 무엇을 지워야 하는지 말해 주지
	 * 않으면 운영자는 서버가 왜 안 뜨는지 알아낼 길이 없다.
	 */
	@Test
	void 낡은_표식_문구는_지울_파일을_적는다() {
		String text = Result.STALE_WORLD_MARKER.failureText(MARKER_PATH);

		assertTrue(text.contains(MARKER_PATH), "경로를 그대로 적어야 한다");
		assertTrue(text.contains("아무것도 지우지 않았습니다"), "지우지 않았다는 사실이 먼저다");
	}

	/** 표식을 못 써서 그만둔 경우와 이미 카운트다운이 도는 경우도 각자의 문구를 쓴다. */
	@Test
	void 표식_실패와_중복_실행도_각자_다르게_말한다() {
		String markerFailed = Result.MARKER_FAILED.failureText(MARKER_PATH);
		String alreadyRunning = Result.ALREADY_RUNNING.failureText(MARKER_PATH);

		assertFalse(markerFailed.equals(alreadyRunning), "두 실패는 다른 일이다");
		assertTrue(markerFailed.contains("아무것도 지우지 않았습니다"));
		assertTrue(alreadyRunning.contains("카운트다운"));
	}
}
