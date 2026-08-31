package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.ShareTeam;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 다시 뽑기의 <b>회차당 횟수</b>가 팀 명단 파일을 어떻게 넘나드는지.
 *
 * <p>{@code TeamRosterStore.save}/{@code load} 가 패키지 전용이라 이 시험만 여기 있다.
 * 나머지(월드 저장·회차 넘기기·기본값)는 {@code com.sharedfate.team.TeamRerollSettingTest}
 * 쪽에 있다.
 *
 * <p>이 파일이 이어 가는 것은 <b>「회차당 몇 번」이라는 결정</b>뿐이다. 「이번 회차에 몇 번
 * 남았는지」는 회차마다 다시 차는 값이라 여기 담지 않는다.
 */
class TeamRosterRerollTest {
	private static final UUID TEAM = UUID.fromString("30000000-0000-0000-0000-000000000002");
	private static final UUID MEMBER = UUID.fromString("30000000-0000-0000-0000-0000000000b1");

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static ShareTeam team() {
		return new ShareTeam(TEAM, "원정대", List.of(MEMBER));
	}

	@Test
	void 회차당_횟수는_명단_파일을_왕복한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);

		TeamRosterStore.save(file, List.of(new TeamRosterStore.RestoredTeam(
				team(), true, 20.0F, 0, false, false, List.of(), false, 7)));

		assertEquals(7, TeamRosterStore.load(file).getFirst().rerollCount());
	}

	@Test
	void 안_적힌_예전_형식은_회차당_세_번으로_읽힌다(@TempDir Path server) throws Exception {
		// 0.10.0-dev 까지의 형식 5. 설정 묶음은 있는데 rerollCount 만 없다. Gson 이 없는 필드를
		// 0 으로 두므로, 그대로 믿으면 지금 돌아가는 서버의 팀이 다음 회차부터 다시 뽑기를
		// 영영 못 쓰게 된다. Integer 로 받아 「없음」과 「0회」를 가른 이유가 이것이다.
		Path file = server.resolve(TeamRosterStore.FILE_NAME);
		Files.writeString(file, """
				{
				  "formatVersion": 5,
				  "teams": [
				    {
				      "teamId": "%s",
				      "name": "원정대",
				      "members": ["%s"],
				      "settings": {
				        "perksEnabled": true,
				        "maxHealth": 20.0,
				        "swapIntervalTicks": 0,
				        "difficultyEscalationEnabled": true
				      }
				    }
				  ]
				}
				""".formatted(TEAM, MEMBER), StandardCharsets.UTF_8);

		TeamRosterStore.RestoredTeam loaded = TeamRosterStore.load(file).getFirst();
		assertEquals(3, loaded.rerollCount());
		assertTrue(loaded.difficultyEscalationEnabled(), "예전 항목은 그대로 읽혀야 한다");
	}

	@Test
	void 영회로_정한_팀은_그대로_영회로_돌아온다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);

		TeamRosterStore.save(file, List.of(new TeamRosterStore.RestoredTeam(
				team(), true, 20.0F, 0, false, false, List.of(), false, 0)));

		assertEquals(0, TeamRosterStore.load(file).getFirst().rerollCount());
	}

	@Test
	void 다시_뽑기가_없던_생성자로_만든_항목은_기본_3회로_본다() {
		// 예전 형태를 그대로 쓰던 자리(시험 포함)가 조용히 0회가 되면 안 된다.
		assertEquals(3, new TeamRosterStore.RestoredTeam(
				team(), true, 20.0F, 0, false, false).rerollCount());
		assertEquals(3, new TeamRosterStore.RestoredTeam(
				team(), true, 20.0F, 0, false, false, List.of(), true).rerollCount());
	}
}
