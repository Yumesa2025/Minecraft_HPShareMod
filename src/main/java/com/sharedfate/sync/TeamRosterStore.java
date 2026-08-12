package com.sharedfate.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TeamRosterStore {
	public static final String FILE_NAME = "sharedfate-team-roster.json";
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_STORED_TEAMS = 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record StoredTeam(String teamId, String name, List<String> members) {
	}

	private record StoredRoster(int formatVersion, List<StoredTeam> teams) {
	}

	private TeamRosterStore() {
	}

	public static void onServerStarted(MinecraftServer server) {
		Path file = rosterFile(server);
		TeamManager manager = TeamManager.get(server);
		try {
			if (!manager.allTeams().isEmpty()) {
				save(file, manager.allTeams());
				SharedFateMod.LOGGER.info(
						"[TEAM-ROSTER] 현재 월드 팀 명단 저장: teams={}", manager.allTeams().size());
				return;
			}
			if (!Files.exists(file)) {
				return;
			}
			int restored = manager.restoreFreshRoster(
					load(file), (float) SharedFateMod.config.sharedMaxHealth);
			SharedFateMod.LOGGER.info(
					"[TEAM-ROSTER] 새 월드 팀 명단 복원·공유 자원 초기화: teams={}", restored);
		} catch (IOException | IllegalArgumentException | IllegalStateException e) {
			SharedFateMod.LOGGER.error(
					"팀 명단 파일을 처리하지 못해 기존 월드 상태를 유지합니다: {}", file, e);
		}
	}

	public static void onServerStopping(MinecraftServer server) {
		try {
			saveCurrent(server);
		} catch (IOException e) {
			SharedFateMod.LOGGER.error("서버 종료 중 팀 명단을 저장하지 못했습니다.", e);
		}
	}

	public static void saveCurrent(MinecraftServer server) throws IOException {
		save(rosterFile(server), TeamManager.get(server).allTeams());
	}

	static void save(Path file, Collection<ShareTeam> teams) throws IOException {
		List<StoredTeam> storedTeams = teams.stream()
				.map(team -> new StoredTeam(
						team.teamId().toString(), team.name(),
						team.members().stream().map(UUID::toString).toList()))
				.toList();
		StoredRoster roster = new StoredRoster(FORMAT_VERSION, storedTeams);
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(roster, writer);
		}
		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	static List<ShareTeam> load(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			throw new IOException("팀 명단이 일반 파일이 아닙니다: " + file);
		}
		StoredRoster stored;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			stored = GSON.fromJson(reader, StoredRoster.class);
		} catch (RuntimeException e) {
			throw new IOException("팀 명단 JSON이 손상되었습니다: " + file, e);
		}
		if (stored == null || stored.formatVersion() != FORMAT_VERSION
				|| stored.teams() == null || stored.teams().size() > MAX_STORED_TEAMS) {
			throw new IOException("지원하지 않거나 손상된 팀 명단입니다: " + file);
		}

		List<ShareTeam> teams = new ArrayList<>();
		try {
			for (StoredTeam team : stored.teams()) {
				if (team == null || team.teamId() == null || team.name() == null
						|| team.members() == null) {
					throw new IllegalArgumentException("필수 팀 필드가 없습니다.");
				}
				List<UUID> members = team.members().stream().map(UUID::fromString).toList();
				teams.add(new ShareTeam(UUID.fromString(team.teamId()), team.name(), members));
			}
		} catch (RuntimeException e) {
			throw new IOException("팀 명단 값이 손상되었습니다: " + file, e);
		}
		return List.copyOf(teams);
	}

	private static Path rosterFile(MinecraftServer server) {
		return server.getServerDirectory().toAbsolutePath().normalize().resolve(FILE_NAME);
	}
}
