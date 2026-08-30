package com.sharedfate.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sharedfate.SharedFateMod;
import com.sharedfate.sync.TeamRosterStore;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class TeamManager extends SavedData {
	private final Map<UUID, ShareTeam> teams = new LinkedHashMap<>();
	private final Map<UUID, TeamState> states = new HashMap<>();
	private final Map<UUID, UUID> playerToTeam = new HashMap<>();
	private final Set<UUID> pendingEffectClears = new HashSet<>();
	private final Set<UUID> pendingExperienceClears = new HashSet<>();

	private record Entry(ShareTeam team, TeamState state) {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ShareTeam.CODEC.fieldOf("team").forGetter(Entry::team),
				TeamState.CODEC.fieldOf("state").forGetter(Entry::state)
		).apply(instance, Entry::new));
	}

	public static final Codec<TeamManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Entry.CODEC.listOf().fieldOf("teams").forGetter(TeamManager::toEntries),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("pendingEffectClears", List.of())
					.forGetter(manager -> List.copyOf(manager.pendingEffectClears)),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("pendingExperienceClears", List.of())
					.forGetter(manager -> List.copyOf(manager.pendingExperienceClears))
	).apply(instance, TeamManager::fromCodec));

	public static final SavedDataType<TeamManager> TYPE = new SavedDataType<>(
			SharedFateMod.id("teams"),
			TeamManager::new,
			CODEC,
			null
	);

	public TeamManager() {
	}

	public static TeamManager get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable ShareTeam teamOf(UUID player) {
		UUID teamId = playerToTeam.get(player);
		return teamId == null ? null : teams.get(teamId);
	}

	public @Nullable TeamState stateOf(UUID player) {
		UUID teamId = playerToTeam.get(player);
		return teamId == null ? null : states.get(teamId);
	}

	public @Nullable ShareTeam teamById(UUID teamId) {
		return teams.get(teamId);
	}

	public @Nullable TeamState stateByTeamId(UUID teamId) {
		return states.get(teamId);
	}

	public @Nullable ShareTeam teamByName(String name) {
		for (ShareTeam team : teams.values()) {
			if (team.name().equalsIgnoreCase(name)) {
				return team;
			}
		}
		return null;
	}

	public Collection<ShareTeam> allTeams() {
		return List.copyOf(teams.values());
	}

	/** 팀이 하나라도 있는지. */
	public boolean hasAnyTeam() {
		return !teams.isEmpty();
	}

	/**
	 * singleTeamOnly 정책상 새 팀을 만들 수 있는지 판단한다.
	 * 이미 있는 팀은 그대로 두고 새로 만드는 것만 막으므로, 팀이 여럿인 서버도 깨지지 않는다.
	 * 회차 리셋 복원은 {@link #restoreFreshRoster}를 쓰므로 이 판단을 거치지 않는다.
	 */
	public boolean canCreateNewTeam(boolean singleTeamOnly) {
		return !singleTeamOnly || teams.isEmpty();
	}

	/**
	 * 새 회차의 빈 월드에 팀 명단과 그 팀이 정해 둔 설정을 되살린다.
	 *
	 * <p>공유 아이템·체력·경험치는 새로 시작하지만 <b>증강 사용 여부·최대 체력·위치 교환
	 * 주기는 이어진다.</b> 이것들은 회차마다 달라지는 진행 상황이 아니라 팀이 한 번 내린
	 * 결정이라, 회차가 넘어갈 때마다 다시 켜라고 하면 매번 같은 명령을 치게 된다.
	 *
	 * <p>보유 증강은 이어지지 않는다. 회차마다 새로 고르는 것이 규칙이다.
	 */
	public int restoreFreshRoster(Collection<TeamRosterStore.RestoredTeam> roster) {
		Objects.requireNonNull(roster, "roster");
		if (!teams.isEmpty()) {
			throw new IllegalStateException("기존 팀이 있는 저장소에는 팀 명단을 복원할 수 없습니다.");
		}

		List<TeamRosterStore.RestoredTeam> entries = List.copyOf(roster);
		List<ShareTeam> snapshot = entries.stream().map(TeamRosterStore.RestoredTeam::team).toList();
		Set<UUID> teamIds = new HashSet<>();
		Set<String> names = new HashSet<>();
		Set<UUID> members = new HashSet<>();
		for (ShareTeam team : snapshot) {
			if (team == null || team.isEmpty()
					|| team.size() > com.sharedfate.config.SharedFateConfig.NETWORK_MAX_TEAM_SIZE
					|| team.name().isBlank() || team.name().length() > 32
					|| !teamIds.add(team.teamId())
					|| !names.add(team.name().toLowerCase(Locale.ROOT))
					|| new HashSet<>(team.members()).size() != team.members().size()) {
				throw new IllegalArgumentException("유효하지 않거나 중복된 팀 명단입니다.");
			}
			for (UUID member : team.members()) {
				if (member == null || !members.add(member)) {
					throw new IllegalArgumentException("한 플레이어가 여러 팀에 중복되어 있습니다.");
				}
			}
		}

		pendingEffectClears.clear();
		pendingExperienceClears.clear();
		for (TeamRosterStore.RestoredTeam entry : entries) {
			ShareTeam team = entry.team();
			TeamState state = TeamState.fresh(sanitizeMaxHealth(entry.maxHealth()));
			state.perksEnabled = entry.perksEnabled();
			state.positionSwapIntervalTicks = Math.max(0, entry.swapIntervalTicks());
			teams.put(team.teamId(), team);
			states.put(team.teamId(), state);
			for (UUID member : team.members()) {
				playerToTeam.put(member, team.teamId());
			}
		}
		if (!snapshot.isEmpty()) {
			setDirty();
		}
		return snapshot.size();
	}

	/** 손상된 저장값이 와도 허용 범위 안으로 맞춘다. */
	private static float sanitizeMaxHealth(float stored) {
		float fallback = SharedFateMod.config == null
				? 20.0F : (float) SharedFateMod.config.sharedMaxHealth;
		if (!Float.isFinite(stored) || stored < 20.0F || stored > 40.0F) {
			return fallback;
		}
		return stored;
	}

	public @Nullable ShareTeam createTeam(String name, UUID leader, float maxHealth) {
		return createTeam(name, leader, TeamState.fresh(maxHealth));
	}

	public @Nullable ShareTeam createTeam(String name, UUID leader, TeamState initialState) {
		if (teamByName(name) != null || playerToTeam.containsKey(leader)) {
			return null;
		}
		ShareTeam team = ShareTeam.create(name, leader);
		teams.put(team.teamId(), team);
		states.put(team.teamId(), initialState);
		playerToTeam.put(leader, team.teamId());
		setDirty();
		return team;
	}

	public boolean addMember(UUID teamId, UUID player, int maxTeamSize) {
		ShareTeam team = teams.get(teamId);
		if (team == null || team.size() >= maxTeamSize || playerToTeam.containsKey(player)) {
			return false;
		}
		teams.put(teamId, team.withMemberAdded(player));
		playerToTeam.put(player, teamId);
		setDirty();
		return true;
	}

	public void removeMember(UUID player) {
		UUID teamId = playerToTeam.get(player);
		if (teamId == null) {
			return;
		}
		ShareTeam team = teams.get(teamId);
		if (team != null) {
			ShareTeam next = team.withMemberRemoved(player);
			if (next.isEmpty()) {
				TeamState state = states.get(teamId);
				if (state != null && state.hasSharedItems()) {
					throw new IllegalStateException("공유 아이템을 정산하지 않고 마지막 멤버를 제거할 수 없습니다.");
				}
				teams.remove(teamId);
				states.remove(teamId);
			} else {
				teams.put(teamId, next);
			}
		}
		playerToTeam.remove(player);
		setDirty();
	}

	public void disband(UUID teamId) {
		TeamState state = states.get(teamId);
		if (state != null && state.hasSharedItems()) {
			throw new IllegalStateException("공유 아이템을 정산하지 않고 팀을 해체할 수 없습니다.");
		}
		ShareTeam team = teams.remove(teamId);
		states.remove(teamId);
		if (team != null) {
			for (UUID member : team.members()) {
				playerToTeam.remove(member);
			}
			setDirty();
		}
	}

	public void markEffectClear(UUID player) {
		if (pendingEffectClears.add(player)) {
			setDirty();
		}
	}

	public boolean consumeEffectClear(UUID player) {
		if (!pendingEffectClears.remove(player)) {
			return false;
		}
		setDirty();
		return true;
	}

	public void markExperienceClear(UUID player) {
		if (pendingExperienceClears.add(player)) {
			setDirty();
		}
	}

	public boolean consumeExperienceClear(UUID player) {
		if (!pendingExperienceClears.remove(player)) {
			return false;
		}
		setDirty();
		return true;
	}

	public void markDirtyIfActive() {
		if (!teams.isEmpty()) {
			states.values().forEach(state -> state.restoreOverflow(
					SharedFateMod.config != null && SharedFateMod.config.mainInventoryRows == 6));
			setDirty();
		}
	}

	private List<Entry> toEntries() {
		List<Entry> entries = new ArrayList<>();
		for (ShareTeam team : teams.values()) {
			TeamState state = states.get(team.teamId());
			if (state != null) {
				entries.add(new Entry(team, state));
			}
		}
		return entries;
	}

	/**
	 * 저장 데이터를 되살린다.
	 *
	 * <p>0.5.1-dev 까지 있던 {@code invites} 항목은 더 읽지 않는다. 초대는 리더가 부르는
	 * 즉시 합류로 바뀌어 대기열 자체가 없어졌다. 예전 저장 파일에 그 항목이 남아 있어도
	 * 코덱이 모르는 항목으로 지나치므로 오류가 나지 않는다.
	 */
	private static TeamManager fromCodec(List<Entry> entries,
			List<UUID> pendingEffectClears, List<UUID> pendingExperienceClears) {
		TeamManager manager = new TeamManager();
		for (Entry entry : entries) {
			ShareTeam team = entry.team();
			if (team.isEmpty()
					|| team.size() > com.sharedfate.config.SharedFateConfig.NETWORK_MAX_TEAM_SIZE
					|| new HashSet<>(team.members()).size() != team.members().size()
					|| team.name().isBlank() || manager.teamByName(team.name()) != null
					|| manager.teams.containsKey(team.teamId())
					|| team.members().stream().anyMatch(manager.playerToTeam::containsKey)) {
				SharedFateMod.LOGGER.warn("손상되거나 중복된 팀 저장 데이터를 건너뜁니다: {}", team.name());
				continue;
			}
			float maximum = SharedFateMod.config == null
					? 20.0F : (float) SharedFateMod.config.sharedMaxHealth;
			entry.state().sanitize(maximum);
			manager.teams.put(team.teamId(), team);
			manager.states.put(team.teamId(), entry.state());
			for (UUID member : team.members()) {
				manager.playerToTeam.put(member, team.teamId());
			}
		}
		manager.pendingEffectClears.addAll(pendingEffectClears);
		manager.pendingExperienceClears.addAll(pendingExperienceClears);
		return manager;
	}
}
