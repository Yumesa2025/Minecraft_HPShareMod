package com.sharedfate.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「지금 초기화해도 되는 상태인가」의 <b>사전 검증</b>.
 *
 * <p>이 판정이 없던 동안 {@code /shareteam reset} 은 <b>반쪽만 실행될 수 있었다.</b> 팀과 피해
 * 기록을 먼저 비운 뒤 5초 있다가 월드 표식을 쓰는데, 그 쓰기가 실패하면 서버는 내려가지 않고
 * 팀만 사라진 채로 계속 돈다. 실패하는 길이 실제로 셋이나 있었다 — 싱글플레이·LAN(월드가
 * {@code saves/} 아래에 있다), 낡은 표식 잔류({@code CREATE_NEW} 가 거절한다), 루프 스크립트가
 * 삭제에 실패하고 죽은 뒤.
 *
 * <p>그래서 <b>지우기 전에</b> 같은 조건을 미리 본다. 여기서 거절하면 명령은 아무것도 건드리지
 * 않고 그 자리에서 끝난다.
 *
 * <p>판정을 {@code MinecraftServer} 에서 떼어 <b>경로 둘과 불리언 하나</b>만 받게 해 두었기
 * 때문에 이 시험이 닿는다. {@code VictoryTeamResolver.resolve} 와 같은 모양이다.
 */
class RunResetPreflightTest {

	/** 전용 서버의 보통 모양. 서버 루트 바로 아래에 월드가 있고 남은 표식이 없다. */
	@Test
	void 서버_루트_바로_아래_월드에_표식이_없으면_통과한다(@TempDir Path server) {
		assertEquals(RunResetPreflight.Outcome.READY,
				RunResetPreflight.evaluate(server, server.resolve("world"), false));
		assertTrue(RunResetPreflight.evaluate(server, server.resolve("world"), false).ready());
	}

	/**
	 * 싱글플레이·LAN 모양은 거절한다.
	 *
	 * <p>26.3 {@code MinecraftServer.getServerDirectory()} 는 통합 서버에서 <b>언제나</b>
	 * {@code Path.of("")}(프로세스 CWD)를 돌려주고, 월드는 {@code <CWD>/saves/<월드>} 에 있다.
	 * 부모가 {@code saves} 라 서버 루트 바로 아래가 아니고, 그대로 두면 카운트다운이 다 끝난
	 * 뒤에야 {@code IllegalArgumentException} 으로 터진다 — 팀은 이미 사라진 다음이다.
	 */
	@Test
	void 싱글플레이_모양의_월드는_거절한다(@TempDir Path server) {
		assertEquals(RunResetPreflight.Outcome.WORLD_NOT_UNDER_SERVER_ROOT,
				RunResetPreflight.evaluate(server, server.resolve("saves/새로운 세계"), false));
		assertFalse(RunResetPreflight.evaluate(server, server.resolve("saves/새로운 세계"), false)
				.ready());
	}

	/** 월드 폴더가 서버 루트 자신이거나 루트 밖이어도 마찬가지다. 지울 대상을 못 고른다. */
	@Test
	void 루트_자신이거나_루트_밖이면_거절한다(@TempDir Path server) {
		assertEquals(RunResetPreflight.Outcome.WORLD_NOT_UNDER_SERVER_ROOT,
				RunResetPreflight.evaluate(server, server, false));
		assertEquals(RunResetPreflight.Outcome.WORLD_NOT_UNDER_SERVER_ROOT,
				RunResetPreflight.evaluate(server, server.resolve("../바깥"), false));
	}

	/**
	 * 낡은 월드 표식이 남아 있으면 거절한다.
	 *
	 * <p>표식이 있으면 {@code WorldResetCoordinator.tick} 의 {@code CREATE_NEW} 가 반드시
	 * {@code FileAlreadyExistsException} 을 던진다. 게다가 루프 스크립트는 표식이 있으면
	 * <b>기동 자체를 거부</b>하므로, 이 상태에서 초기화를 시작하면 팀만 잃는다.
	 */
	@Test
	void 낡은_월드_표식이_있으면_거절한다(@TempDir Path server) {
		assertEquals(RunResetPreflight.Outcome.STALE_WORLD_MARKER,
				RunResetPreflight.evaluate(server, server.resolve("world"), true));
	}

	/**
	 * 둘 다 걸리면 <b>월드 경로</b>를 먼저 말한다.
	 *
	 * <p>싱글플레이에서는 표식을 지워 봐야 달라지는 것이 없다. 고칠 수 없는 쪽을 먼저 알려야
	 * 운영자가 헛수고를 하지 않는다.
	 */
	@Test
	void 둘_다_걸리면_월드_경로를_먼저_말한다(@TempDir Path server) {
		assertEquals(RunResetPreflight.Outcome.WORLD_NOT_UNDER_SERVER_ROOT,
				RunResetPreflight.evaluate(server, server.resolve("saves/world"), true));
	}

	/** 표식이 있는지는 약속된 이름의 파일 하나로 본다. 루프 스크립트가 보는 바로 그 파일이다. */
	@Test
	void 표식_경로는_서버_루트_바로_아래의_약속된_이름이다(@TempDir Path server) {
		Path marker = WorldResetCoordinator.markerFile(server);

		assertEquals(WorldResetCoordinator.MARKER_FILE_NAME, marker.getFileName().toString());
		assertEquals(server.toAbsolutePath().normalize(), marker.getParent());
	}

	/** 실제 파일을 보고 판정하는 길. 명령이 부르는 것은 이쪽이다. */
	@Test
	void 실제_파일을_보고_판정한다(@TempDir Path server) throws IOException {
		Path world = server.resolve("world");

		assertEquals(RunResetPreflight.Outcome.READY,
				RunResetPreflight.inspect(server, world));

		Files.writeString(WorldResetCoordinator.markerFile(server),
				WorldResetCoordinator.markerContents(world), StandardCharsets.UTF_8);

		assertEquals(RunResetPreflight.Outcome.STALE_WORLD_MARKER,
				RunResetPreflight.inspect(server, world));
	}
}
