package com.sharedfate.sync;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 「지금 서버를 초기화해도 되는 상태인가」를 <b>지우기 전에</b> 본다.
 *
 * <h2>왜 필요한가 — 초기화가 반쪽만 실행될 수 있었다</h2>
 * <p>{@link RunResetCoordinator#reset} 은 회차 표식을 쓰고, 팀을 해체하고, 피해 기록을 비운 뒤
 * 카운트다운을 건다. 그런데 월드를 실제로 지우게 만드는 <b>월드 표식은 5초 뒤</b>
 * {@link WorldResetCoordinator#tick} 에서 쓰인다. 거기서 실패하면 서버는 내려가지 않고, 그때는
 * 이미 팀도 피해 기록도 없다 — <b>「팀만 사라지고 월드는 그대로」</b>가 남는다. 되돌릴 수 없다고
 * 경고까지 하고 실행한 명령이 그 상태를 만든다.
 *
 * <p>뒤에서 실패하는 길이 <b>실제로 셋</b> 있었다.
 * <ul>
 *   <li><b>싱글플레이·LAN</b> — 26.3 {@code MinecraftServer.getServerDirectory()} 는 통합
 *       서버에서 언제나 {@code Path.of("")}(프로세스 CWD)를 돌려주고, 월드는
 *       {@code <CWD>/saves/<월드>} 에 있다. 부모가 {@code saves} 라
 *       {@link WorldResetCoordinator#validateWorldDirectory} 가 반드시 예외를 던진다.</li>
 *   <li><b>낡은 표식 잔류</b> — 월드 표식은 {@code CREATE_NEW} 로 쓴다. 파일이 이미 있으면
 *       {@code FileAlreadyExistsException} 이다.</li>
 *   <li><b>루프 삭제 실패 후</b> — 루프 스크립트가 월드 삭제에 실패하면 표식을 남긴 채 죽는다.
 *       운영자가 {@code java -jar} 로 띄우면 그 상태 그대로 서버가 뜬다.</li>
 * </ul>
 *
 * <p>그래서 <b>똑같은 조건을 미리 한 번 본다.</b> 여기서 걸리면 명령은 팀도 기록도 표식도
 * 건드리지 않고 그 자리에서 끝난다. 뒤늦게 터지는 실패를 없애는 것이 아니라, <b>터질 자리를
 * 지우기 전으로 당기는</b> 것이다.
 *
 * <h2>순수 판정이라 시험이 닿는다</h2>
 * <p>{@link #evaluate} 는 살아 있는 서버 없이 도는 순수 함수다 — 경로 둘과 「표식이 이미
 * 있는가」만 받는다. {@link RunResetCoordinator} 는 {@code MinecraftServer} 를 받아 단위 시험이
 * 닿지 않으므로, 이 저장소가 {@code GameOverCountdown}·{@code VictoryTeamResolver.resolve} 에서
 * 쓴 방식 그대로 판정만 떼어 냈다. 파일을 실제로 보는 일은 {@link #inspect} 한 줄이 한다.
 *
 * <h2>검사 차례에도 뜻이 있다</h2>
 * <p>월드 경로를 먼저 본다. 싱글플레이에서는 표식을 지워 봐야 달라지는 것이 없으니, 고칠 수
 * 없는 쪽을 먼저 알려야 운영자가 헛수고를 하지 않는다.
 */
public final class RunResetPreflight {

	/** 사전 검증의 결과. {@link RunResetCoordinator#blockedBy} 가 명령의 결과 값으로 옮긴다. */
	public enum Outcome {
		/** 지워도 된다. */
		READY,
		/**
		 * 월드 폴더가 서버 루트 바로 아래가 아니다 — 싱글플레이·LAN 이 이 모양이다.
		 *
		 * <p>고칠 수 있는 상태가 아니다. 전용 서버로 띄워야 한다.
		 */
		WORLD_NOT_UNDER_SERVER_ROOT,
		/**
		 * 지난 월드 초기화의 표식이 아직 남아 있다.
		 *
		 * <p>이 파일이 있는 동안에는 루프 스크립트가 <b>서버를 켜지도 않는다.</b> 손으로 지우면
		 * 풀린다.
		 */
		STALE_WORLD_MARKER;

		/** 초기화를 시작해도 되는가. */
		public boolean ready() {
			return this == READY;
		}
	}

	private RunResetPreflight() {
	}

	/**
	 * 순수 판정. <b>파일을 보지 않는다.</b>
	 *
	 * <p>월드 경로 규칙은 {@link WorldResetCoordinator#validateWorldDirectory} 를 그대로 부른다.
	 * 같은 규칙을 여기에 베껴 쓰면 한쪽만 고쳤을 때 <b>사전 검증은 통과했는데 5초 뒤에
	 * 터지는</b> 어긋남이 생긴다 — 이 클래스가 막으려던 바로 그 상태다.
	 *
	 * @param serverDirectory   서버 루트
	 * @param worldDirectory    지울 월드 폴더
	 * @param worldMarkerPresent 월드 표식 파일이 이미 있는가
	 */
	public static Outcome evaluate(Path serverDirectory, Path worldDirectory,
			boolean worldMarkerPresent) {
		try {
			WorldResetCoordinator.validateWorldDirectory(serverDirectory, worldDirectory);
		} catch (RuntimeException notResettable) {
			// 경로가 터무니없어 정규화 자체가 실패하는 경우도 여기로 온다. 어느 쪽이든 지울
			// 대상을 고르지 못한다는 뜻이라 결론이 같다.
			return Outcome.WORLD_NOT_UNDER_SERVER_ROOT;
		}
		if (worldMarkerPresent) {
			return Outcome.STALE_WORLD_MARKER;
		}
		return Outcome.READY;
	}

	/**
	 * 파일을 실제로 보고 판정한다. 명령이 부르는 길.
	 *
	 * <p>{@link Files#exists} 로 본다 — 폴더든 읽을 수 없는 파일이든 <b>그 이름이 이미 차 있으면</b>
	 * {@code CREATE_NEW} 는 실패하므로, 「올바른 표식인가」가 아니라 「자리가 비어 있는가」가 맞는
	 * 물음이다.
	 */
	public static Outcome inspect(Path serverDirectory, Path worldDirectory) {
		return evaluate(serverDirectory, worldDirectory,
				Files.exists(WorldResetCoordinator.markerFile(serverDirectory)));
	}
}
