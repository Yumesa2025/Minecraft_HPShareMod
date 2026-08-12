package com.sharedfate.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sharedfate.SharedFateMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SharedFateConfig {
	public static final int NETWORK_MAX_TEAM_SIZE = 16;
	public static final double MIN_SHARED_MAX_HEALTH = 1.0;
	public static final double MAX_SHARED_MAX_HEALTH = 1024.0;
	public static final int MIN_WORLD_RESET_DELAY_TICKS = 40;
	public static final int MAX_WORLD_RESET_DELAY_TICKS = 1200;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int maxTeamSize = 4;
	public double sharedMaxHealth = 20.0;
	public int mainInventoryRows = 6;
	public boolean shareEnderChest = true;
	public boolean shareExperience = true;
	public boolean shareStatusEffects = true;
	public int damageAlertDurationTicks = 30;
	public boolean requireClientMod = true;
	public boolean resetWorldOnTeamDeath = true;
	public int worldResetDelayTicks = 160;
	public boolean showRunBossBar = true;
	public boolean dragonKillEndsRun = true;
	public int victoryCreditsDelayTicks = 100;

	public static SharedFateConfig loadOrCreate(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				SharedFateConfig loaded = GSON.fromJson(reader, SharedFateConfig.class);
				if (loaded != null) {
					if (loaded.sanitize()) {
						loaded.save(file);
					}
					return loaded;
				}
				SharedFateMod.LOGGER.warn("설정 파일이 비어 있어 기본값을 사용합니다: {}", file);
			} catch (Exception e) {
				SharedFateMod.LOGGER.warn("설정 파일을 읽지 못해 기본값을 사용합니다: {}", file, e);
			}
		}

		SharedFateConfig config = new SharedFateConfig();
		try {
			config.save(file);
		} catch (IOException e) {
			SharedFateMod.LOGGER.warn("설정 파일을 쓰지 못했습니다: {}", file, e);
		}
		return config;
	}

	private boolean sanitize() {
		boolean changed = false;
		if (maxTeamSize < 1) {
			maxTeamSize = 4;
			changed = true;
		} else if (maxTeamSize > NETWORK_MAX_TEAM_SIZE) {
			maxTeamSize = NETWORK_MAX_TEAM_SIZE;
			changed = true;
		}
		if (!Double.isFinite(sharedMaxHealth)
				|| sharedMaxHealth < MIN_SHARED_MAX_HEALTH
				|| sharedMaxHealth > MAX_SHARED_MAX_HEALTH) {
			sharedMaxHealth = 20.0;
			changed = true;
		}
		if (mainInventoryRows != 3 && mainInventoryRows != 6) {
			mainInventoryRows = 6;
			changed = true;
		}
		if (damageAlertDurationTicks < 0) {
			damageAlertDurationTicks = 30;
			changed = true;
		}
		if (worldResetDelayTicks < MIN_WORLD_RESET_DELAY_TICKS
				|| worldResetDelayTicks > MAX_WORLD_RESET_DELAY_TICKS) {
			worldResetDelayTicks = 160;
			changed = true;
		}
		if (victoryCreditsDelayTicks < 20 || victoryCreditsDelayTicks > 1200) {
			victoryCreditsDelayTicks = 100;
			changed = true;
		}
		return changed;
	}

	public void save(Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
	}
}
