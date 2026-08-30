package com.sharedfate.sync;

import com.sharedfate.team.ShareTeam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRosterStoreTest {
	private static final UUID TEAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID A = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID B = UUID.fromString("20000000-0000-0000-0000-000000000002");

	@Test
	void 서버_루트_명단은_팀_식별자와_멤버_순서를_왕복한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);
		ShareTeam team = new ShareTeam(TEAM, "원정대", List.of(A, B));

		TeamRosterStore.save(file, List.of(new TeamRosterStore.RestoredTeam(team, false, 20.0F, 0)));

		assertEquals(List.of(team),
				TeamRosterStore.load(file).stream()
						.map(TeamRosterStore.RestoredTeam::team).toList());
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"formatVersion\": 2"));
	}

	@Test
	void 팀_설정은_회차를_넘겨_그대로_돌아온다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);
		ShareTeam team = new ShareTeam(TEAM, "원정대", List.of(A, B));

		TeamRosterStore.save(file,
				List.of(new TeamRosterStore.RestoredTeam(team, true, 34.0F, 2400)));

		TeamRosterStore.RestoredTeam loaded = TeamRosterStore.load(file).getFirst();
		assertTrue(loaded.perksEnabled());
		assertEquals(34.0F, loaded.maxHealth());
		assertEquals(2400, loaded.swapIntervalTicks());
	}

	@Test
	void 설정이_없던_예전_형식도_읽고_기본값으로_시작한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);
		Files.writeString(file, """
				{"formatVersion":1,"teams":[{"teamId":"10000000-0000-0000-0000-000000000001",
				"name":"원정대","members":["20000000-0000-0000-0000-000000000001"]}]}
				""", StandardCharsets.UTF_8);

		TeamRosterStore.RestoredTeam loaded = TeamRosterStore.load(file).getFirst();
		assertEquals("원정대", loaded.team().name());
		assertFalse(loaded.perksEnabled());
		assertEquals(0, loaded.swapIntervalTicks());
	}

	@Test
	void 손상된_명단은_빈_팀으로_조용히_바꾸지_않고_거부한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);
		Files.writeString(file, "{\"formatVersion\":2,\"teams\":[{\"teamId\":\"bad\"}]}",
				StandardCharsets.UTF_8);

		assertThrows(java.io.IOException.class, () -> TeamRosterStore.load(file));
	}

	@Test
	void 모르는_형식_번호는_거부한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(TeamRosterStore.FILE_NAME);
		Files.writeString(file, "{\"formatVersion\":99,\"teams\":[]}", StandardCharsets.UTF_8);

		assertThrows(java.io.IOException.class, () -> TeamRosterStore.load(file));
	}
}
