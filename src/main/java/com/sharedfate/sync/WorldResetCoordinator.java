package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.net.WorldResetPayload;
import com.sharedfate.ui.GameOverCountdown;
import com.sharedfate.ui.RunResetMessages;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 팀이 전멸했을 때의 게임 오버 연출과 서버 종료.
 *
 * <h2>흐름</h2>
 * <ol>
 *   <li>{@code DeathHandler} 가 팀을 전부 죽이고 {@link #request} 를 부른다.</li>
 *   <li>화면에 <b>「게임 오버 · N회차」</b>가 크게 뜨고({@code GameOverClientDisplay}) 그 아래에서
 *       <b>빨간 숫자</b>가 5부터 1까지 내려간다({@code GameOverHud}).</li>
 *   <li>숫자가 다 내려가면 <b>표식 파일만 남기고 서버를 정상 종료</b>한다.</li>
 *   <li>월드를 지우고 서버를 다시 여는 일은 <b>재시작 루프 스크립트</b>가 한다
 *       ({@code sharedfate-server-loop.ps1}). 이 모드는 월드를 직접 지우지 않는다.</li>
 * </ol>
 *
 * <p>5초가 지나면 표식을 쓰고 {@code halt} 한다. 그래야 다음 회차가 정상적으로 열린다.
 *
 * <p>카운트다운 숫자는 <b>클라이언트가 자기 자리에 직접 그린다</b> — {@code GameOverHud} 가
 * 사망 화면의 제목(y=30)과 사인 줄(y=85) 사이의 빈 자리에 그리므로 어떤 GUI 크기에서도
 * 가려지지 않는다. 서버는 남은 길이를 {@code WorldResetPayload} 로 한 번 보내고 클라이언트가
 * 그 길이를 스스로 세어 내려간다.
 *
 * <h2>카운트다운 동안 멈추는 것</h2>
 * <ul>
 *   <li><b>피해</b> — {@link GameStartManager#blocksDamage} 가 {@link #countingDown()} 을 보고
 *       모든 피해를 버린다. 이미 예약된 「폭발 교환」이 종료 직전에 터져도 아무 일도 없다.</li>
 *   <li><b>위치 교환·시차·정거장</b> — 각 {@code tick} 이 건너뛴다. 종료 직전에 자리가 뒤바뀌면
 *       월드를 지우는 서버에서는 뜻이 없고, 지우지 않는 서버에서는 벽 속에 박힌 채 저장된다.</li>
 *   <li><b>난이도 상승 시간</b> — 세지 않는다. 회차는 이미 끝났다.</li>
 * </ul>
 * <p><b>이동은 막지 않는다.</b> 전멸한 팀원은 이미 사망 화면에 갇혀 움직일 수 없다.
 */
public final class WorldResetCoordinator {
	public static final String MARKER_FILE_NAME = ".sharedfate-world-reset.pending";
	public static final String MARKER_HEADER = "sharedfate-world-reset-v1";
	/** 초기화 경로가 로그에 쓰는 이름. 전멸 경로의 팀 이름 자리에 들어간다. */
	private static final String RUN_RESET_LABEL = "운영자 초기화";

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

	/**
	 * 게임 오버 카운트다운이 도는 중인가.
	 *
	 * <p>이 값이 참인 동안 피해와 위치 교환과 난이도 시간이 멈춘다. 부르는 곳은 클래스 문서의
	 * 「카운트다운 동안 멈추는 것」에 적어 뒀다.
	 */
	public static boolean countingDown() {
		return pendingServer != null && ticksRemaining > 0;
	}

	/**
	 * 운영자 초기화 명령이 쓰는 진입점.
	 *
	 * <p>{@link #request(MinecraftServer, ShareTeam)} 와 달리 {@code resetWorldOnTeamDeath}
	 * 설정도 승리 여부도 보지 않는다. 그 둘은 「전멸로 월드를 갈아엎을 것인가」에 대한 답이지
	 * 「운영자가 서버를 초기화할 수 있는가」에 대한 답이 아니다. 연출과 종료 절차는 전멸 경로와
	 * <b>똑같은 것을 그대로</b> 쓴다 — 카운트다운이 끝나면 {@link #tick} 이 명단을 저장하고
	 * 표식을 남기고 서버를 내린다.
	 *
	 * @return 걸린 카운트다운 길이(틱). 이미 예약된 종료가 있어 걸지 못했으면 0
	 */
	public static int requestRunReset(MinecraftServer server) {
		if (pendingServer != null) {
			return 0;
		}
		// 설정을 아직 못 읽었어도 초기화는 돌아야 한다. 그때는 모드 기본값 5초로 센다.
		int delayTicks = SharedFateMod.config == null
				? GameOverCountdown.DEFAULT_SECONDS * GameOverCountdown.TICKS_PER_SECOND
				: SharedFateMod.config.worldResetDelayTicks;
		arm(server, RUN_RESET_LABEL, delayTicks,
				RunResetMessages.announcement(GameOverCountdown.secondsRemaining(delayTicks)));
		SharedFateMod.LOGGER.warn(
				"운영자 초기화로 서버 종료를 예약했습니다: delayTicks={}", delayTicks);
		return delayTicks;
	}

	private static void request(MinecraftServer server, String teamName) {
		if (pendingServer != null) {
			return;
		}
		int delayTicks = SharedFateMod.config.worldResetDelayTicks;
		int seconds = GameOverCountdown.secondsRemaining(delayTicks);
		arm(server, teamName, delayTicks, GameOverCountdown.wipeAnnouncement(teamName, seconds));
		SharedFateMod.LOGGER.warn(
				"팀 전멸로 월드 초기화를 예약했습니다: team={}, delayTicks={}", teamName, delayTicks);
	}

	/**
	 * 카운트다운을 실제로 건다. 전멸 경로와 초기화 경로가 <b>같은 절차</b>를 쓰게 하는 자리다.
	 *
	 * <p>여기가 갈라지면 한쪽만 고쳤을 때 다른 쪽이 조용히 다르게 돈다. 클라이언트는
	 * {@code WorldResetPayload} 로 받은 길이를 스스로 세어 내려간다.
	 */
	private static void arm(MinecraftServer server, String label, int delayTicks,
			String announcement) {
		pendingServer = server;
		ticksRemaining = delayTicks;
		pendingTeamName = label;
		WorldResetPayload payload = new WorldResetPayload(
				RunProgressManager.runNumber(), ticksRemaining);
		for (var player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
		server.getPlayerList().broadcastSystemMessage(Component.literal(announcement), false);
	}

	public static void tick(MinecraftServer server) {
		if (pendingServer != server) {
			return;
		}
		if (--ticksRemaining > 0) {
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
