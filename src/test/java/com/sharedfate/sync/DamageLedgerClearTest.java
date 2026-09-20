package com.sharedfate.sync;

import com.sharedfate.team.ShareTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서버 초기화가 피해 기록을 비우는 방식.
 *
 * <p>초기화 명령은 <b>파일을 지우지 않는다.</b> 지워 봐야 나가는 길에 다시 쓰인다 —
 * {@code flushIfDirty} 가 메모리에 있는 것을 그대로 적기 때문이다. 그래서 <b>메모리를 비우고
 * 「저장할 것이 있다」로 표시해</b> 종료 저장이 빈 파일을 적게 한다. 이 시험이 붙드는 것이
 * 바로 그 순서다.
 */
class DamageLedgerClearTest {
	private static final UUID TEAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID PLAYER = UUID.fromString("55555555-5555-5555-5555-555555555555");

	@AfterEach
	void reset() {
		DamageLedger.clearState();
	}

	@Test
	void 비운_뒤에_저장하면_빈_파일이_남는다(@TempDir Path directory) throws IOException {
		Path path = directory.resolve(DamageLedger.FILE_NAME);
		DamageLedger.loadForTesting(path);
		DamageLedger.record(TEAM_ID, "기록팀", PLAYER, "첫째", 1, 12.0D);
		DamageLedger.noteRunEnd(TEAM_ID, "기록팀", 1, "첫째", List.of("짐꾼"));
		DamageLedger.flushIfDirty();
		assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("기록팀"),
				"먼저 기록이 실제로 적혀 있어야 이 시험에 뜻이 있다");

		DamageLedger.clearAllRecords();
		DamageLedger.flushIfDirty();

		String saved = Files.readString(path, StandardCharsets.UTF_8);
		assertFalse(saved.contains("기록팀"), "팀 기록이 파일에 남아 있으면 안 된다");
		assertFalse(saved.contains("첫째"));
	}

	/**
	 * 비우는 것은 <b>파일을 잊는 것이 아니다.</b>
	 *
	 * <p>{@code clearState} 는 어느 파일에 적을지까지 잊어버려서, 그 뒤의 저장이 아무 데도 가지
	 * 않는다. 초기화 경로가 그것을 쓰면 옛 기록이 담긴 파일이 <b>그대로 살아남는다.</b>
	 */
	@Test
	void 비워도_어느_파일에_적을지는_잊지_않는다(@TempDir Path directory) throws IOException {
		Path path = directory.resolve(DamageLedger.FILE_NAME);
		Files.writeString(path, """
				{
				  "formatVersion": 3,
				  "teams": {
				    "%s": {
				      "name": "옛팀",
				      "players": {
				        "%s": { "name": "첫째", "runs": { "1": 7.5 } }
				      }
				    }
				  }
				}
				""".formatted(TEAM_ID, PLAYER), StandardCharsets.UTF_8);
		DamageLedger.loadForTesting(path);

		DamageLedger.clearAllRecords();
		DamageLedger.flushIfDirty();

		assertFalse(Files.readString(path, StandardCharsets.UTF_8).contains("옛팀"),
				"같은 파일이 빈 내용으로 덮여야 한다");
	}

	@Test
	void 비우면_누적도_마지막_피해도_남지_않는다(@TempDir Path directory) {
		DamageLedger.loadForTesting(directory.resolve(DamageLedger.FILE_NAME));
		DamageLedger.noteSource(PLAYER, "creeper", "크리퍼");
		DamageLedger.record(TEAM_ID, "기록팀", PLAYER, "첫째", 1, 19.0D);

		DamageLedger.clearAllRecords();

		ShareTeam team = new ShareTeam(TEAM_ID, "기록팀", List.of(PLAYER));
		assertNull(DamageLedger.lastHitLine(TEAM_ID, PLAYER, 1));
		assertEquals(0, DamageLedger.summaryFor(team).totalPerks());
		assertFalse(DamageLedger.summaryFor(team).hasDamage());
	}

	/**
	 * 아직 기록에 들어가지 않은 「방금 무엇에 맞았나」도 함께 버린다.
	 *
	 * <p>남겨 두면 초기화 직후의 첫 피해가 <b>지운 회차의 출처</b>를 물고 들어온다.
	 */
	@Test
	void 비우면_적어만_둔_출처도_따라오지_않는다(@TempDir Path directory) {
		DamageLedger.loadForTesting(directory.resolve(DamageLedger.FILE_NAME));
		DamageLedger.noteSource(PLAYER, "lava", "");

		DamageLedger.clearAllRecords();
		DamageLedger.record(TEAM_ID, "새팀", PLAYER, "첫째", 1, 3.0D);

		assertNull(DamageLedger.lastHitLine(TEAM_ID, PLAYER, 1),
				"지우기 전에 적어 둔 출처가 새 기록에 붙으면 안 된다");
	}
}
