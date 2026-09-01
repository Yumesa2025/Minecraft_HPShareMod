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
import org.jetbrains.annotations.Nullable;

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

	/** 보스바 문구를 다시 재는 주기. 사람이 단추를 누를 때만 바뀌는 값이라 1초면 충분하다. */
	private static final int BOSS_BAR_SCAN_INTERVAL_TICKS = 20;

	private static RunProgressState state;
	private static Path stateFile;
	private static ServerBossEvent bossBar;
	/** 마지막으로 보스바에 써 넣은 문구. 같으면 팀을 훑지도 패킷을 보내지도 않는다. */
	private static Component lastBossBarTitle;
	private static int bossBarCooldown;

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
			lastBossBarTitle = title(server);
			bossBar = new ServerBossEvent(BOSS_EVENT_ID, lastBossBarTitle, color(server),
					BossEvent.BossBarOverlay.PROGRESS);
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
		// 시작하지 않은 팀이 드래곤을 잡아도 회차 승리가 아니다. 시작 전에는 회차 자체가 없고,
		// 여기서 승리로 세면 아무도 시작하지 않은 회차가 끝나 버린다.
		if (winningTeam != null
				&& GameStartManager.waiting(
						TeamManager.get(server).stateByTeamId(winningTeam.teamId()))) {
			SharedFateMod.LOGGER.info(
					"[RUN] 아직 시작하지 않은 팀 '{}' 의 드래곤 처치라 승리로 세지 않습니다.",
					winningTeam.name());
			return;
		}
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
		refreshBossBar(server);
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
		if (++bossBarCooldown >= BOSS_BAR_SCAN_INTERVAL_TICKS) {
			bossBarCooldown = 0;
			refreshBossBar(server);
		}
	}

	/**
	 * 보스바 문구를 지금 있어야 할 모습으로 맞춘다.
	 *
	 * <p>「시작 대기」와 「진행 중」을 오가는 값은 사람이 단추를 누를 때 바뀌므로, 승리처럼 한
	 * 지점에서 알려 주는 대신 {@value #BOSS_BAR_SCAN_INTERVAL_TICKS} 틱마다 다시 잰다. 값이
	 * 그대로면 아무것도 보내지 않는다 — {@code ServerBossEvent.setName} 은 이름이 실제로
	 * 달라졌을 때만 패킷을 뿌리지만, 팀을 훑는 비용은 여기서 미리 아낀다.
	 */
	private static void refreshBossBar(@Nullable MinecraftServer server) {
		if (bossBar == null) {
			return;
		}
		Component next = title(server);
		if (next.equals(lastBossBarTitle)) {
			return;
		}
		lastBossBarTitle = next;
		bossBar.setName(next);
		bossBar.setColor(color(server));
	}

	private static Component title(@Nullable MinecraftServer server) {
		return Component.literal(label(runNumber(),
				state != null && state.isVictory(),
				state == null ? "" : state.winningTeam(),
				GameStartManager.anyTeamStarted(server)));
	}

	/**
	 * 보스바에 적을 한 줄. 월드 없이 시험할 수 있게 산술만 떼어 뒀다.
	 *
	 * <p>「N회차」라고만 적으면 팀도 없고 아무도 시작하지 않은 상태에서 이미 회차가 굴러가는
	 * 것처럼 읽힌다. 실제로 그렇게 읽혀서 이 기능이 필요해졌으므로, 시작 전에는 <b>「시작 대기」</b>
	 * 라고 분명히 적는다.
	 */
	static String label(int runNumber, boolean victory, @Nullable String winningTeam,
			boolean anyTeamStarted) {
		if (victory) {
			return "SharedFate · " + runNumber + "회차 · "
					+ (winningTeam == null ? "" : winningTeam) + " 승리!";
		}
		return anyTeamStarted
				? "SharedFate · " + runNumber + "회차 진행 중"
				: "SharedFate · " + runNumber + "회차 · 시작 대기";
	}

	private static BossEvent.BossBarColor color(@Nullable MinecraftServer server) {
		if (isVictory()) {
			return BossEvent.BossBarColor.GREEN;
		}
		// 아직 아무도 시작하지 않았다는 사실이 색으로도 보여야 한다.
		return GameStartManager.anyTeamStarted(server)
				? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.YELLOW;
	}

	public static void reset() {
		if (bossBar != null) {
			bossBar.removeAllPlayers();
		}
		bossBar = null;
		lastBossBarTitle = null;
		bossBarCooldown = 0;
		state = null;
		stateFile = null;
		VictoryCelebration.reset();
		FoodOverflowBuffer.resetRuntime();
		DamageLedger.resetRuntime();
	}
}
