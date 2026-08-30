package com.sharedfate.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
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
	/**
	 * 2: 팀 설정(증강 사용 여부·최대 체력·위치 교환 주기)을 함께 적기 시작했다.
	 * 3: 피격 알림·사망 알림 표시 여부를 함께 적기 시작했다.
	 *
	 * <p>2·1로 적힌 예전 파일도 그대로 읽는다. 설정 항목이 없으면 기본값으로 시작하고,
	 * 두 알림의 기본값은 꺼짐이다.
	 */
	private static final int FORMAT_VERSION = 3;
	private static final List<Integer> READABLE_FORMAT_VERSIONS = List.of(3, 2, 1);
	private static final int MAX_STORED_TEAMS = 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * 회차를 넘겨 이어 갈 팀 설정.
	 *
	 * <p>보유 증강은 여기 담지 않는다. 증강은 회차마다 새로 고르는 것이 규칙이라 이어지면
	 * 안 되고, 이어져야 하는 것은 <b>"이 팀은 증강을 쓰기로 했다"</b> 는 결정뿐이다.
	 * 최대 체력과 위치 교환 주기도 같은 이유로 결정에 해당한다.
	 *
	 * @param perksEnabled        증강을 쓰기로 한 팀인가
	 * @param maxHealth           팀이 정한 공유 최대 체력. 증강 보너스가 아닌 기본값이다
	 * @param swapIntervalTicks   위치 교환 주기(틱). 꺼져 있으면 0
	 * @param damageAlertEnabled  피격 알림을 켜기로 한 팀인가
	 * @param deathAlertEnabled   사망 알림을 켜기로 한 팀인가
	 */
	private record StoredSettings(boolean perksEnabled, float maxHealth, int swapIntervalTicks,
			boolean damageAlertEnabled, boolean deathAlertEnabled) {
	}

	private record StoredTeam(String teamId, String name, List<String> members,
			StoredSettings settings) {
	}

	/** 명단과 그 팀이 이어 갈 설정을 함께 들고 다니는 짝. */
	public record RestoredTeam(ShareTeam team, boolean perksEnabled, float maxHealth,
			int swapIntervalTicks, boolean damageAlertEnabled, boolean deathAlertEnabled) {
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
				save(file, snapshot(manager));
				SharedFateMod.LOGGER.info(
						"[TEAM-ROSTER] 현재 월드 팀 명단 저장: teams={}", manager.allTeams().size());
				return;
			}
			if (!Files.exists(file)) {
				return;
			}
			int restored = manager.restoreFreshRoster(load(file));
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
		save(rosterFile(server), snapshot(TeamManager.get(server)));
	}

	/** 지금 팀들의 명단과 이어 갈 설정을 함께 뜬다. */
	private static List<RestoredTeam> snapshot(TeamManager manager) {
		List<RestoredTeam> result = new ArrayList<>();
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				continue;
			}
			result.add(new RestoredTeam(team, state.perksEnabled, state.baseMaxHealth,
					state.positionSwapIntervalTicks,
					state.damageAlertEnabled, state.deathAlertEnabled));
		}
		return result;
	}

	static void save(Path file, Collection<RestoredTeam> teams) throws IOException {
		List<StoredTeam> storedTeams = teams.stream()
				.map(entry -> new StoredTeam(
						entry.team().teamId().toString(), entry.team().name(),
						entry.team().members().stream().map(UUID::toString).toList(),
						new StoredSettings(entry.perksEnabled(), entry.maxHealth(),
								entry.swapIntervalTicks(),
								entry.damageAlertEnabled(), entry.deathAlertEnabled())))
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

	static List<RestoredTeam> load(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			throw new IOException("팀 명단이 일반 파일이 아닙니다: " + file);
		}
		StoredRoster stored;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			stored = GSON.fromJson(reader, StoredRoster.class);
		} catch (RuntimeException e) {
			throw new IOException("팀 명단 JSON이 손상되었습니다: " + file, e);
		}
		boolean known = stored != null
				&& READABLE_FORMAT_VERSIONS.contains(stored.formatVersion());
		if (!known || stored.teams() == null || stored.teams().size() > MAX_STORED_TEAMS) {
			throw new IOException("지원하지 않거나 손상된 팀 명단입니다: " + file);
		}

		List<RestoredTeam> teams = new ArrayList<>();
		try {
			for (StoredTeam team : stored.teams()) {
				if (team == null || team.teamId() == null || team.name() == null
						|| team.members() == null) {
					throw new IllegalArgumentException("필수 팀 필드가 없습니다.");
				}
				List<UUID> members = team.members().stream().map(UUID::fromString).toList();
				ShareTeam share = new ShareTeam(UUID.fromString(team.teamId()), team.name(), members);
				// 형식 1 에는 설정이 없다. 그때는 기본값으로 시작한다. 형식 2 에는 알림
				// 항목이 없는데, Gson 이 없는 boolean 을 false 로 두므로 저절로 꺼짐이 된다.
				StoredSettings settings = team.settings();
				teams.add(settings == null
						? new RestoredTeam(share, false, defaultMaxHealth(), 0, false, false)
						: new RestoredTeam(share, settings.perksEnabled(),
								settings.maxHealth(), settings.swapIntervalTicks(),
								settings.damageAlertEnabled(), settings.deathAlertEnabled()));
			}
		} catch (RuntimeException e) {
			throw new IOException("팀 명단 값이 손상되었습니다: " + file, e);
		}
		return List.copyOf(teams);
	}

	/**
	 * 설정이 없는 예전 명단을 읽을 때 쓸 최대 체력.
	 *
	 * <p>{@code SharedFateMod.config} 는 서버가 뜨기 전이나 단위 시험에서는 null 이다.
	 * 그때는 모드 기본값 20 으로 둔다.
	 */
	private static float defaultMaxHealth() {
		return SharedFateMod.config == null
				? 20.0F : (float) SharedFateMod.config.sharedMaxHealth;
	}

	private static Path rosterFile(MinecraftServer server) {
		return server.getServerDirectory().toAbsolutePath().normalize().resolve(FILE_NAME);
	}
}
