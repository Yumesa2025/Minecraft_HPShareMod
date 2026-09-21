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
	 * 쓰다 만 표식이 남지 않는다.
	 *
	 * <p>{@code RunProgressState.save}·{@code TeamRosterStore.save} 와 같은 방식으로
	 * 임시 파일에 쓴 뒤 옮긴다. 반쯤 쓰인 표식은 머리글 검사에서 걸려 <b>회차가 되돌아가지
	 * 않는데</b>, 그때는 이미 팀도 월드도 사라진 뒤라 되살릴 길이 없다. 옮기기가 끝나야
	 * 표식이 생기므로 「있으면 온전하다」가 성립한다.
	 */
	@Test
	void 쓰기는_임시_파일을_남기지_않는다(@TempDir Path server) throws IOException {
		RunResetMarker.write(server);

		try (var entries = Files.list(server)) {
			assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
					"임시 파일이 남으면 다음 쓰기가 무엇을 덮는지 알 수 없다");
		}
		assertTrue(RunResetMarker.matches(
				Files.readString(server.resolve(RunResetMarker.MARKER_FILE_NAME),
						StandardCharsets.UTF_8), server));
	}

	/**
	 * 이미 있는 표식 위에 다시 써도 된다.
	 *
	 * <p>월드 표식과 반대다. 거절하면 어쩌다 남은 표식 하나 때문에 그 서버는 영영 초기화할 수
	 * 없게 된다 — 숨김 파일을 손으로 지우라고 시키는 것이 이 명령이 없애려던 바로 그 일이다.
	 */
	@Test
	void 이미_있는_표식_위에_다시_쓸_수_있다(@TempDir Path server) throws IOException {
		Files.writeString(server.resolve(RunResetMarker.MARKER_FILE_NAME),
				"찌꺼기" + System.lineSeparator(), StandardCharsets.UTF_8);

		RunResetMarker.write(server);

		assertTrue(RunResetMarker.consume(server), "덮어쓴 표식은 온전해야 한다");
	}

	/**
	 * 월드 표식을 쓰지 못했을 때 <b>회차 표식을 되돌린다.</b>
	 *
	 * <p>초기화는 회차 표식을 먼저 쓰고 5초 뒤에 월드 표식을 쓴다. 뒤쪽이 실패하면 서버는
	 * 내려가지 않는데, 회차 표식을 그대로 두면 <b>다음에 켤 때 멀쩡한 회차가 1로 눌린다.</b>
	 * 초기화는 일어나지도 않았는데 회차만 사라지는 것이다.
	 */
	@Test
	void 되돌림은_내가_쓴_표식을_지운다(@TempDir Path server) throws IOException {
		RunResetMarker.write(server);

		assertTrue(RunResetMarker.rollback(server));
		assertFalse(Files.exists(server.resolve(RunResetMarker.MARKER_FILE_NAME)));
		assertFalse(RunResetMarker.consume(server), "되돌린 뒤에는 회차를 건드리지 않는다");
	}

	/** 지울 것이 없으면 조용히 거짓. 전멸 경로에는 회차 표식이 아예 없다. */
	@Test
	void 표식이_없으면_되돌릴_것도_없다(@TempDir Path server) {
		assertFalse(RunResetMarker.rollback(server));
	}

	/**
	 * 내 것이 아닌 표식은 되돌림도 건드리지 않는다.
	 *
	 * <p>되돌림은 <b>방금 내가 쓴 표식</b>을 거두는 일이다. 형식이나 경로가 다른 파일은 내가 쓴
	 * 것이 아니므로, 지우면 사람이 볼 증거가 사라진다. {@link RunResetMarker#consume} 과 같은
	 * 기준으로 본다.
	 */
	@Test
	void 남의_표식은_되돌림이_건드리지_않는다(@TempDir Path server, @TempDir Path other)
			throws IOException {
		Path marker = server.resolve(RunResetMarker.MARKER_FILE_NAME);
		Files.writeString(marker, RunResetMarker.markerContents(other), StandardCharsets.UTF_8);

		assertFalse(RunResetMarker.rollback(server));
		assertTrue(Files.exists(marker), "남의 표식은 사람이 보도록 남긴다");
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
