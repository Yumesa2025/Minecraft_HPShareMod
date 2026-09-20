package com.sharedfate.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 회차를 1로 되돌리라는 표식.
 *
 * <p>이 표식이 <b>잘못 읽히면 남의 회차 기록이 사라진다.</b> 루프 스크립트가 월드를 지운 뒤
 * {@code runNumber++} 를 하므로 초기화 경로는 반드시 다음 기동에서 회차를 1로 눌러야 하는데,
 * 그 판단의 근거가 이 파일 하나뿐이다. 그래서 {@code WorldResetCoordinator} 의 월드 표식과
 * 같은 수준으로 <b>형식과 경로를 둘 다</b> 본다.
 */
class RunResetMarkerTest {

	@Test
	void 표식에는_계약_버전과_정규화된_서버_경로가_들어간다(@TempDir Path server) {
		String marker = RunResetMarker.markerContents(server);

		assertTrue(marker.startsWith(RunResetMarker.MARKER_HEADER + System.lineSeparator()));
		assertTrue(marker.contains(server.toAbsolutePath().normalize().toString()));
	}

	@Test
	void 쓴_표식은_다음_기동에서_한_번_읽히고_사라진다(@TempDir Path server) throws IOException {
		RunResetMarker.write(server);

		assertTrue(Files.isRegularFile(server.resolve(RunResetMarker.MARKER_FILE_NAME)));
		assertTrue(RunResetMarker.consume(server), "형식이 맞으면 회차를 되돌린다");
		assertFalse(Files.exists(server.resolve(RunResetMarker.MARKER_FILE_NAME)),
				"지우지 않으면 켤 때마다 회차가 1로 눌린다");
		assertFalse(RunResetMarker.consume(server), "두 번 읽히면 안 된다");
	}

	/** 표식이 아예 없는 것은 보통의 기동이다. 조용해야 한다. */
	@Test
	void 표식이_없으면_아무_일도_하지_않는다(@TempDir Path server) {
		assertFalse(RunResetMarker.consume(server));
	}

	/**
	 * 형식이 다르면 <b>회차를 건드리지 않고 파일도 남긴다.</b>
	 *
	 * <p>지워 버리면 무엇이 잘못됐는지 사람이 볼 것이 사라진다. 다음 기동에서 또 로그가 남는
	 * 쪽이 조용히 사라지는 쪽보다 낫다.
	 */
	@Test
	void 머리글이_다르면_회차를_건드리지_않는다(@TempDir Path server) throws IOException {
		Path marker = server.resolve(RunResetMarker.MARKER_FILE_NAME);
		Files.writeString(marker, "sharedfate-run-reset-v99" + System.lineSeparator()
				+ server.toAbsolutePath().normalize() + System.lineSeparator(),
				StandardCharsets.UTF_8);

		assertFalse(RunResetMarker.consume(server));
		assertTrue(Files.exists(marker), "손상된 표식은 사람이 보도록 남긴다");
	}

	/**
	 * 남의 서버 폴더에서 복사해 온 표식은 듣지 않는다.
	 *
	 * <p>배포본을 통째로 복사해 쓰는 사람들이 있다. 초기화 직후의 폴더를 복사하면 표식까지
	 * 따라가는데, 그것이 새 서버의 회차를 1로 눌러서는 안 된다.
	 */
	@Test
	void 다른_서버의_경로가_적혀_있으면_듣지_않는다(@TempDir Path server, @TempDir Path other)
			throws IOException {
		Files.writeString(server.resolve(RunResetMarker.MARKER_FILE_NAME),
				RunResetMarker.markerContents(other), StandardCharsets.UTF_8);

		assertFalse(RunResetMarker.consume(server));
	}

	@Test
	void 줄_수가_다르거나_상대_경로면_듣지_않는다(@TempDir Path server) {
		String root = server.toAbsolutePath().normalize().toString();

		assertFalse(RunResetMarker.matches(RunResetMarker.MARKER_HEADER, server),
				"경로 줄이 없으면 안 된다");
		assertFalse(RunResetMarker.matches(
				RunResetMarker.MARKER_HEADER + "\n" + root + "\n덤", server),
				"줄이 더 붙어 있으면 안 된다");
		assertFalse(RunResetMarker.matches(RunResetMarker.MARKER_HEADER + "\nworld", server),
				"상대 경로는 안 된다");
		assertFalse(RunResetMarker.matches("", server));
		assertFalse(RunResetMarker.matches(null, server));
		assertTrue(RunResetMarker.matches(RunResetMarker.MARKER_HEADER + "\n" + root, server),
				"줄바꿈이 \\n 이든 \\r\\n 이든 같은 표식이다");
	}

	/**
	 * 월드 표식과 <b>다른 파일 이름</b>이어야 한다.
	 *
	 * <p>루프 스크립트는 월드 표식만 알고 이 파일은 모른다. 이름이 겹치면 스크립트가 우리
	 * 표식을 월드 삭제 지시로 읽는다.
	 */
	@Test
	void 월드_표식과_이름이_겹치지_않는다() {
		assertFalse(RunResetMarker.MARKER_FILE_NAME.equals(WorldResetCoordinator.MARKER_FILE_NAME));
		assertFalse(RunResetMarker.MARKER_HEADER.equals(WorldResetCoordinator.MARKER_HEADER));
	}
}
