package com.sharedfate;

import com.sharedfate.config.SharedFateConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

	@Test
	void 파일이_없으면_기본값을_쓰고_파일을_만든다(@TempDir Path dir) {
		Path file = dir.resolve("sharedfate.json");

		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);

		assertEquals(4, config.maxTeamSize);
		assertEquals(20.0, config.sharedMaxHealth);
		assertEquals(6, config.mainInventoryRows);
		assertTrue(config.shareEnderChest);
		assertTrue(config.shareExperience);
		assertTrue(config.shareStatusEffects);
		assertEquals(30, config.damageAlertDurationTicks);
		assertTrue(config.requireClientMod);
		assertTrue(config.resetWorldOnTeamDeath);
		assertEquals(160, config.worldResetDelayTicks);
		assertTrue(config.showRunBossBar);
		assertTrue(config.dragonKillEndsRun);
		assertEquals(100, config.victoryCreditsDelayTicks);
		assertTrue(Files.exists(file), "설정 파일이 생성되어야 한다");
	}

	@Test
	void 저장한_값을_다시_읽는다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);
		config.maxTeamSize = 8;
		config.sharedMaxHealth = 60.0;
		config.save(file);

		SharedFateConfig reloaded = SharedFateConfig.loadOrCreate(file);

		assertEquals(8, reloaded.maxTeamSize);
		assertEquals(60.0, reloaded.sharedMaxHealth);
	}

	@Test
	void 누락된_키는_기본값을_유지한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		Files.writeString(file, "{\"maxTeamSize\": 6}", StandardCharsets.UTF_8);

		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);

		assertEquals(6, config.maxTeamSize);
		assertEquals(20.0, config.sharedMaxHealth);
		assertEquals(6, config.mainInventoryRows);
		assertTrue(config.shareEnderChest);
		assertEquals(30, config.damageAlertDurationTicks);
	}

	@Test
	void 깨진_파일이면_기본값으로_되돌리고_복구한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		Files.writeString(file, "{ 이건 JSON이 아니다", StandardCharsets.UTF_8);

		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);
		SharedFateConfig recovered = SharedFateConfig.loadOrCreate(file);

		assertEquals(4, config.maxTeamSize);
		assertEquals(4, recovered.maxTeamSize);
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("maxTeamSize"));
	}

	@Test
	void 게임을_깨뜨리는_설정값은_기본값으로_복구한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		Files.writeString(file, """
				{
				  "maxTeamSize": 0,
				  "sharedMaxHealth": -1,
				  "damageAlertDurationTicks": -20,
				  "worldResetDelayTicks": 10,
				  "victoryCreditsDelayTicks": 0
				}
				""", StandardCharsets.UTF_8);

		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);

		assertEquals(4, config.maxTeamSize);
		assertEquals(20.0, config.sharedMaxHealth);
		assertEquals(30, config.damageAlertDurationTicks);
		assertEquals(160, config.worldResetDelayTicks);
		assertEquals(100, config.victoryCreditsDelayTicks);
	}

	@Test
	void 네트워크_목록보다_큰_팀_정원은_16으로_제한한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		Files.writeString(file, "{\"maxTeamSize\": 100}", StandardCharsets.UTF_8);

		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);

		assertEquals(16, config.maxTeamSize);
	}

	@Test
	void 인벤토리_줄_수는_3줄이나_6줄만_허용한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		Files.writeString(file, "{\"mainInventoryRows\": 5}", StandardCharsets.UTF_8);

		SharedFateConfig config = SharedFateConfig.loadOrCreate(file);

		assertEquals(6, config.mainInventoryRows);
	}

	@Test
	void 공유_최대_체력은_실제_속성_범위만_허용한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("sharedfate.json");
		for (String invalid : new String[] {"0.5", "1025", "1e308"}) {
			Files.writeString(file, "{\"sharedMaxHealth\":" + invalid + "}", StandardCharsets.UTF_8);
			assertEquals(20.0, SharedFateConfig.loadOrCreate(file).sharedMaxHealth);
		}

		Files.writeString(file, "{\"sharedMaxHealth\":1024}", StandardCharsets.UTF_8);
		assertEquals(1024.0, SharedFateConfig.loadOrCreate(file).sharedMaxHealth);
	}
}
