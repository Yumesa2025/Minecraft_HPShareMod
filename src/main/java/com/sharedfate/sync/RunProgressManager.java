package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class RunProgressManager {
	public static final String STATE_FILE_NAME = "sharedfate-run-state.json";
	private static final UUID BOSS_EVENT_ID = UUID.nameUUIDFromBytes(
			"sharedfate:run-progress".getBytes(StandardCharsets.UTF_8));

	private static RunProgressState state;
	private static Path stateFile;
	private static ServerBossEvent bossBar;

	private RunProgressManager() {
	}

	public static void onServerStarted(MinecraftServer server) {
		DamageLedger.onServerStarted(server);
		stateFile = server.getServerDirectory().toAbsolutePath().normalize().resolve(STATE_FILE_NAME);
		try {
			state = RunProgressState.loadOrCreate(stateFile);
		} catch (IOException e) {
			state = RunProgressState.firstRun();
			SharedFateMod.LOGGER.error("회차 파일을 읽지 못해 메모리에서 1회차로 시작합니다: {}", stateFile, e);
		}
		if (SharedFateMod.config.showRunBossBar) {
			bossBar = new ServerBossEvent(BOSS_EVENT_ID, title(), color(), BossEvent.BossBarOverlay.PROGRESS);
			bossBar.setProgress(1.0F);
			bossBar.setVisible(true);
		}
		SharedFateMod.LOGGER.info(
				"[RUN] runNumber={} status={} winningTeam={}",
				state.runNumber(), state.status(), state.winningTeam());
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (bossBar != null) {
			bossBar.addPlayer(player);
		}
	}

	public static void onPlayerLeave(ServerPlayer player) {
		if (bossBar != null) {
			bossBar.removePlayer(player);
		}
	}

	public static boolean isVictory() {
		return state != null && state.isVictory();
	}

	public static int runNumber() {
		return state == null ? 1 : state.runNumber();
	}

	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof EnderDragon dragon)
				|| SharedFateMod.config == null
				|| !SharedFateMod.config.dragonKillEndsRun
				|| isVictory()) {
			return;
		}

		MinecraftServer server = dragon.level().getServer();
		Player killer = dragon.getLastHurtByPlayer();
		ShareTeam winningTeam = killer == null ? null : TeamManager.get(server).teamOf(killer.getUUID());
		String winningName = winningTeam != null
				? winningTeam.name()
				: killer != null ? killer.getPlainTextName() : "모험가";
		declareVictory(server, winningTeam, killer, winningName);
	}

	private static void declareVictory(
			MinecraftServer server, ShareTeam winningTeam, Player killer, String winningName) {
		state.markVictory(winningName);
		try {
			state.save(stateFile);
		} catch (IOException e) {
			SharedFateMod.LOGGER.error("승리 회차 상태를 저장하지 못했습니다: {}", stateFile, e);
		}
		WorldResetCoordinator.cancelPendingReset();
		DamageLedger.giveVictoryBooks(server, winningTeam, state.runNumber());
		refreshBossBar();
		server.getPlayerList().broadcastSystemMessage(Component.literal(
				"승리! '" + winningName + "' 팀이 " + state.runNumber() + "회차에서 엔더 드래곤을 처치했습니다!"),
				false);

		// 엔딩 크레딧은 띄우지 않는다. 대신 타이틀 → 폭죽 연출을 예약한다.
		Set<UUID> audience = celebrationAudience(server, winningTeam, killer);
		VictoryCelebration.start(audience, state.runNumber(), winningName,
				SharedFateMod.config.victoryTitleDelayTicks,
				SharedFateMod.config.victoryFireworkDelayTicks);
		SharedFateMod.LOGGER.info(
				"[RUN] victory runNumber={} team={} celebrationPlayers={}",
				state.runNumber(), winningName, audience.size());
	}

	/** 승리 연출을 볼 사람들. 승리 팀이 있으면 그 팀, 없으면 처치자, 그마저 없으면 접속자 전원. */
	private static Set<UUID> celebrationAudience(
			MinecraftServer server, ShareTeam winningTeam, Player killer) {
		Set<UUID> audience = new LinkedHashSet<>();
		if (winningTeam != null) {
			audience.addAll(winningTeam.members());
		} else if (killer != null) {
			audience.add(killer.getUUID());
		} else {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				audience.add(player.getUUID());
			}
		}
		return audience;
	}

	public static void tick(MinecraftServer server) {
		VictoryCelebration.tick(server);
	}

	private static void refreshBossBar() {
		if (bossBar == null) {
			return;
		}
		bossBar.setName(title());
		bossBar.setColor(color());
	}

	private static Component title() {
		if (state != null && state.isVictory()) {
			return Component.literal("SharedFate · " + state.runNumber() + "회차 · "
					+ state.winningTeam() + " 승리!");
		}
		return Component.literal("SharedFate · " + (state == null ? 1 : state.runNumber()) + "회차");
	}

	private static BossEvent.BossBarColor color() {
		return isVictory() ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.BLUE;
	}

	public static void reset() {
		if (bossBar != null) {
			bossBar.removeAllPlayers();
		}
		bossBar = null;
		state = null;
		stateFile = null;
		VictoryCelebration.reset();
		FoodOverflowBuffer.resetRuntime();
		DamageLedger.resetRuntime();
	}
}
