package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 「다음에 뜰 때 회차를 1로 되돌려라」는 표식.
 *
 * <h2>왜 이런 것이 필요한가</h2>
 * <p>재시작 루프 스크립트의 {@code Invoke-ValidatedWorldReset} 은 월드를 지운 뒤
 * <b>{@code runNumber++}</b> 를 한다. 초기화 명령은 전멸과 같은 길로 서버를 내리므로, 그냥
 * 두면 <b>초기화했는데 회차가 하나 올라간다.</b> 스크립트는 {@code runNumber < 1} 을 보면
 * 예외를 던지고 죽으므로 0 을 심어 두는 길도 막혀 있다.
 *
 * <p>그래서 모드만 아는 표식을 하나 더 쓴다. 스크립트는 이 파일을 <b>모르므로 건드리지
 * 않고</b>, 월드를 지우고 회차를 올리고 서버를 다시 연다. 새로 뜬 모드가 표식을 보고 회차를
 * 1 로 눌러 버린 뒤 표식을 지운다.
 *
 * <p><b>스크립트를 고치는 길은 버렸다.</b> 사용자들이 각자 사본을 들고 있어, 옛 스크립트에서는
 * 반쪽만 초기화되는데 오류도 안 난다. 표식은 jar 안에만 있으니 판이 섞여도 똑같이 돈다.
 *
 * <h2>검증</h2>
 * <p>{@link WorldResetCoordinator} 의 월드 표식과 같은 수준으로 본다 — <b>머리글과 경로 둘
 * 다</b>. 배포본을 통째로 복사해 쓰는 사람들이 있어서, 초기화 직후의 폴더가 복사되면 표식까지
 * 따라간다. 그것이 남의 서버 회차를 1 로 눌러서는 안 된다.
 *
 * <p>형식이 다르면 <b>회차를 건드리지 않고 파일도 남긴다.</b> 지우면 무엇이 잘못됐는지 사람이
 * 볼 것이 사라진다. 다음 기동에서 또 로그가 남는 쪽이 조용히 없어지는 쪽보다 낫다.
 */
public final class RunResetMarker {
	/** 월드 표식({@code .sharedfate-world-reset.pending})과 <b>반드시 다른 이름</b>이어야 한다. */
	public static final String MARKER_FILE_NAME = ".sharedfate-run-reset.pending";
	public static final String MARKER_HEADER = "sharedfate-run-reset-v1";

	private RunResetMarker() {
	}

	static Path markerFile(Path serverDirectory) {
		return serverDirectory.toAbsolutePath().normalize().resolve(MARKER_FILE_NAME);
	}

	/**
	 * 표식에 적는 두 줄.
	 *
	 * <p>월드 표식과 같은 모양이다 — 첫 줄이 계약 버전, 둘째 줄이 정규화된 절대 경로. 다만
	 * 여기 적는 것은 지울 월드가 아니라 <b>이 표식이 누구 것인지</b>를 밝히는 서버 루트다.
	 */
	static String markerContents(Path serverDirectory) {
		return MARKER_HEADER + System.lineSeparator()
				+ serverDirectory.toAbsolutePath().normalize() + System.lineSeparator();
	}

	/**
	 * 표식을 쓴다.
	 *
	 * <p>{@link WorldResetCoordinator} 는 {@code CREATE_NEW} 로 써서 이미 있으면 실패하지만
	 * 여기서는 덮어쓴다. 내용이 언제나 같은 두 줄이라 덮어쓰기가 손해를 낳지 않고, 거절하면
	 * 어쩌다 남은 표식 하나 때문에 <b>그 서버는 영영 초기화할 수 없게</b> 된다 — 숨김 파일을
	 * 손으로 지우라고 시키는 것이 이 명령이 없애려던 바로 그 일이다.
	 */
	public static void write(Path serverDirectory) throws IOException {
		Files.writeString(markerFile(serverDirectory), markerContents(serverDirectory),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}

	/**
	 * 표식이 내 것으로 확인되는가.
	 *
	 * <p>줄바꿈이 {@code \n} 이든 {@code \r\n} 이든 같은 표식으로 본다. 쓴 판과 읽는 판의
	 * 운영체제가 다를 수 있다.
	 */
	static boolean matches(@Nullable String contents, Path serverDirectory) {
		if (contents == null) {
			return false;
		}
		List<String> lines = contents.lines().toList();
		if (lines.size() != 2 || !MARKER_HEADER.equals(lines.getFirst())) {
			return false;
		}
		Path recorded;
		try {
			recorded = Path.of(lines.get(1).trim());
		} catch (RuntimeException notAPath) {
			return false;
		}
		if (!recorded.isAbsolute()) {
			return false;
		}
		return recorded.normalize().equals(serverDirectory.toAbsolutePath().normalize());
	}

	/**
	 * 표식이 있으면 <b>지우고</b> 참을 돌려준다. 부르는 쪽은 그때 회차를 1 로 누른다.
	 *
	 * <p><b>지운 뒤에 참을 돌려주는 차례가 중요하다.</b> 반대로 하면 삭제가 실패했을 때 켤
	 * 때마다 회차가 1 로 눌려, 남의 진행이 조용히 계속 사라진다. 지우지 못했으면 회차를
	 * 건드리지 않고 시끄럽게 남긴다.
	 */
	public static boolean consume(Path serverDirectory) {
		Path marker = markerFile(serverDirectory);
		if (!Files.isRegularFile(marker)) {
			return false;
		}
		String contents;
		try {
			contents = Files.readString(marker, StandardCharsets.UTF_8);
		} catch (IOException e) {
			SharedFateMod.LOGGER.error(
					"회차 되돌림 표식을 읽지 못해 회차를 건드리지 않습니다: {}", marker, e);
			return false;
		}
		if (!matches(contents, serverDirectory)) {
			SharedFateMod.LOGGER.error(
					"회차 되돌림 표식의 형식이나 경로가 맞지 않아 회차를 건드리지 않습니다."
							+ " 손으로 지워 주세요: {}", marker);
			return false;
		}
		try {
			Files.delete(marker);
		} catch (IOException e) {
			SharedFateMod.LOGGER.error(
					"회차 되돌림 표식을 지우지 못해 회차를 건드리지 않습니다."
							+ " 그대로 두면 켤 때마다 회차가 1로 눌립니다: {}", marker, e);
			return false;
		}
		return true;
	}
}
