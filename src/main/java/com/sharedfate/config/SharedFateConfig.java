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
	/** 승리 연출 각 단계의 지연 범위(틱). 0이면 바로 다음 틱에 넘어간다. */
	public static final int MIN_VICTORY_STAGE_TICKS = 0;
	public static final int MAX_VICTORY_STAGE_TICKS = 1200;
	/** 위치 교환 카운트다운 최대 길이(초). */
	public static final int MAX_POSITION_SWAP_COUNTDOWN_SECONDS = 30;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int maxTeamSize = 4;
	/**
	 * 서버에 팀을 하나만 두도록 제한한다.
	 * 켜져 있으면 이미 팀이 있을 때 /shareteam create 가 거부된다.
	 * 이미 만들어져 있는 팀은 건드리지 않고, 새로 만드는 것만 막는다.
	 * 몹 증강처럼 월드 전체에 걸리는 효과가 팀끼리 충돌하는 것을 막기 위한 기본값이다.
	 */
	public boolean singleTeamOnly = true;
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
	/**
	 * 예전 엔딩 크레딧 연출용 값. 지금은 크레딧을 띄우지 않으므로 쓰이지 않는다.
	 * 기존 설정 파일과의 호환을 위해 필드만 남겨 둔다.
	 */
	public int victoryCreditsDelayTicks = 100;
	/** 드래곤 처치 후 "엔더드래곤 토벌" 타이틀이 뜰 때까지의 지연(틱). 100틱 = 5초. */
	public int victoryTitleDelayTicks = 100;
	/** 타이틀이 뜬 뒤 팀원 위치에 폭죽이 터질 때까지의 지연(틱). 100틱 = 5초. */
	public int victoryFireworkDelayTicks = 100;
	/** 위치 교환 몇 초 전부터 화면에 카운트다운을 띄울지. 0이면 카운트다운을 띄우지 않는다. */
	public int positionSwapCountdownSeconds = 5;

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
		if (victoryTitleDelayTicks < MIN_VICTORY_STAGE_TICKS
				|| victoryTitleDelayTicks > MAX_VICTORY_STAGE_TICKS) {
			victoryTitleDelayTicks = 100;
			changed = true;
		}
		if (victoryFireworkDelayTicks < MIN_VICTORY_STAGE_TICKS
				|| victoryFireworkDelayTicks > MAX_VICTORY_STAGE_TICKS) {
			victoryFireworkDelayTicks = 100;
			changed = true;
		}
		if (positionSwapCountdownSeconds < 0
				|| positionSwapCountdownSeconds > MAX_POSITION_SWAP_COUNTDOWN_SECONDS) {
			positionSwapCountdownSeconds = 5;
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
