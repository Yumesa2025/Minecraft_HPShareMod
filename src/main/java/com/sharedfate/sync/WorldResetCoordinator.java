package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.net.WorldResetPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class WorldResetCoordinator {
	public static final String MARKER_FILE_NAME = ".sharedfate-world-reset.pending";
	public static final String MARKER_HEADER = "sharedfate-world-reset-v1";

	private static MinecraftServer pendingServer;
	private static int ticksRemaining;
	private static String pendingTeamName;

	private WorldResetCoordinator() {
	}

	public static void onServerStarted(MinecraftServer server) {
		if (SharedFateMod.config.resetWorldOnTeamDeath && !server.isHardcore()) {
			SharedFateMod.LOGGER.warn(
					"resetWorldOnTeamDeath가 켜져 있지만 server.properties의 hardcore가 false입니다. "
							+ "월드 초기화는 동작하지만 사망 화면 제목은 '게임 오버'가 아닙니다.");
		}
	}

	public static void request(MinecraftServer server, ShareTeam team) {
		if (SharedFateMod.config == null || !SharedFateMod.config.resetWorldOnTeamDeath
				|| RunProgressManager.isVictory()) {
			return;
		}
		request(server, team.name());
	}

	public static void cancelPendingReset() {
		pendingServer = null;
		ticksRemaining = 0;
		pendingTeamName = null;
	}

	private static void request(MinecraftServer server, String teamName) {
		if (pendingServer != null) {
			return;
		}
		pendingServer = server;
		ticksRemaining = SharedFateMod.config.worldResetDelayTicks;
		pendingTeamName = teamName;
		WorldResetPayload payload = new WorldResetPayload(
				RunProgressManager.runNumber(), ticksRemaining);
		for (var player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
		int seconds = (ticksRemaining + 19) / 20;
		server.getPlayerList().broadcastSystemMessage(Component.literal(
				"게임 오버! '" + teamName + "' 팀이 전멸했습니다. "
						+ seconds + "초 후 새 월드로 서버를 다시 엽니다."), false);
		SharedFateMod.LOGGER.warn(
				"팀 전멸로 월드 초기화를 예약했습니다: team={}, delayTicks={}", teamName, ticksRemaining);
	}

	public static void tick(MinecraftServer server) {
		if (pendingServer != server || --ticksRemaining > 0) {
			return;
		}

		try {
			Path serverDirectory = server.getServerDirectory();
			Path worldDirectory = validateWorldDirectory(
					serverDirectory, server.getWorldPath(LevelResource.ROOT));
			TeamRosterStore.saveCurrent(server);
			Path marker = serverDirectory.toAbsolutePath().normalize().resolve(MARKER_FILE_NAME);
			Files.writeString(marker, markerContents(worldDirectory), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			SharedFateMod.LOGGER.warn(
					"월드 초기화 표식을 기록했습니다. 정상 종료 후 재시작 스크립트가 삭제합니다: {}",
					worldDirectory);
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"새 월드를 만들기 위해 서버를 재시작합니다."), false);
			pendingServer = null;
			pendingTeamName = null;
			server.halt(false);
		} catch (IOException | IllegalArgumentException e) {
			SharedFateMod.LOGGER.error(
					"월드 초기화 표식을 만들지 못해 서버를 계속 실행합니다: team={}", pendingTeamName, e);
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"월드 초기화 준비에 실패했습니다. 운영자가 서버 로그를 확인해 주세요."), false);
			pendingServer = null;
			pendingTeamName = null;
		}
	}

	static Path validateWorldDirectory(Path serverDirectory, Path worldDirectory) {
		Path root = serverDirectory.toAbsolutePath().normalize();
		Path world = worldDirectory.toAbsolutePath().normalize();
		if (world.equals(root) || world.getParent() == null || !world.getParent().equals(root)) {
			throw new IllegalArgumentException("월드 폴더는 서버 루트 바로 아래여야 합니다: " + world);
		}
		return world;
	}

	static String markerContents(Path worldDirectory) {
		return MARKER_HEADER + System.lineSeparator()
				+ worldDirectory.toAbsolutePath().normalize() + System.lineSeparator();
	}

	public static void reset() {
		pendingServer = null;
		ticksRemaining = 0;
		pendingTeamName = null;
	}
}
