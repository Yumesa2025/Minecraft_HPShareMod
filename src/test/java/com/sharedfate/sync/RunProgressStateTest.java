package com.sharedfate.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunProgressStateTest {

	@Test
	void 첫_실행은_1회차이고_월드_밖_파일에_저장한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(RunProgressManager.STATE_FILE_NAME);

		RunProgressState state = RunProgressState.loadOrCreate(file);

		assertEquals(1, state.runNumber());
		assertFalse(state.isVictory());
		assertTrue(Files.exists(file));
	}

	@Test
	void 전멸_리셋은_회차를_올리고_승리_표시를_지운다(@TempDir Path server) throws Exception {
		Path file = server.resolve(RunProgressManager.STATE_FILE_NAME);
		RunProgressState state = RunProgressState.loadOrCreate(file);
		state.markVictory("용사팀");
		state.advanceToNextRun();
		state.save(file);

		RunProgressState reloaded = RunProgressState.loadOrCreate(file);

		assertEquals(2, reloaded.runNumber());
		assertEquals(RunProgressState.PLAYING, reloaded.status());
		assertEquals("", reloaded.winningTeam());
	}

	@Test
	void 드래곤_승리는_현재_회차와_팀명을_보존한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(RunProgressManager.STATE_FILE_NAME);
		RunProgressState state = RunProgressState.loadOrCreate(file);
		state.advanceToNextRun();
		state.advanceToNextRun();
		state.markVictory("엔더팀");
		state.save(file);

		RunProgressState reloaded = RunProgressState.loadOrCreate(file);

		assertEquals(3, reloaded.runNumber());
		assertTrue(reloaded.isVictory());
		assertEquals("엔더팀", reloaded.winningTeam());
	}

	@Test
	void 손상된_회차는_안전한_1회차로_복구한다(@TempDir Path server) throws Exception {
		Path file = server.resolve(RunProgressManager.STATE_FILE_NAME);
		Files.writeString(file,
				"{\"runNumber\":0,\"status\":\"unknown\",\"winningTeam\":null}",
				StandardCharsets.UTF_8);

		RunProgressState state = RunProgressState.loadOrCreate(file);

		assertEquals(1, state.runNumber());
		assertEquals(RunProgressState.PLAYING, state.status());
		assertEquals("", state.winningTeam());
	}
}
