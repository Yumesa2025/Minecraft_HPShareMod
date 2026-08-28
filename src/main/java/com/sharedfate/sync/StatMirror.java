package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StatMirror {
	private static final Map<UUID, Snapshot> LAST = new HashMap<>();
	private static final Set<UUID> SUPPRESSED_TEAMS = new HashSet<>();
	private static final Set<UUID> DAMAGE_CAPTURED_THIS_TICK = new HashSet<>();

	private record Snapshot(UUID teamId, float health, float absorption, float maxAbsorption,
			int foodLevel, float saturation, int experiencePoints) {
	}

	record StatDelta(float healthLoss, float healthGain, float absorptionLoss, float absorptionGain,
			int foodLevel, float saturation, long totalExperience) {
	}

	/**
	 * 한 팀원이 이번 틱에 만들어 낸 변화량.
	 *
	 * <p>{@link #fold} 가 이걸 팀 단위 {@link StatDelta} 로 접는다. 합산 규칙만 떼어 놔야
	 * 월드 없이 시험할 수 있어서 나눠 뒀다.
	 *
	 * @param health              체력 변화량. 음수면 피해, 양수면 회복
	 * @param absorptionDelta     흡수량의 순변화. 양수면 새 보호막을 받은 것이다
	 * @param absorptionConsumed  피해로 실제 소비된 흡수량. 효과 만료로 사라진 몫은 빠져 있다
	 */
	record PlayerDelta(float health, float absorptionDelta, float absorptionConsumed,
			int foodLevel, float saturation, long experience) {
	}

	record AbsorptionDelta(float loss, float gain) {
	}

	private StatMirror() {
	}

	public static void forget(UUID player) {
		LAST.remove(player);
	}

	public static void forgetTeam(ShareTeam team) {
		team.members().forEach(LAST::remove);
	}

	public static void suppressTeamForCurrentTick(UUID teamId) {
		SUPPRESSED_TEAMS.add(teamId);
	}

	public static void captureExperienceBeforeDeath(MinecraftServer server, ShareTeam team, TeamState state) {
		if (!SharedFateMod.config.shareExperience) {
			return;
		}
		long delta = 0;
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			Snapshot last = LAST.get(member);
			if (player != null && last != null && last.teamId().equals(team.teamId())) {
				delta += currentExperiencePoints(player) - last.experiencePoints();
			}
		}
		state.totalExperience = (int) Math.max(0L,
				Math.min(Integer.MAX_VALUE, state.totalExperience + delta));
	}

	public static void captureDamageBeforeDeath(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			Snapshot last = LAST.get(member);
			if (player != null && last != null && last.teamId().equals(team.teamId())) {
				recordDamage(team, player, last);
			}
		}
		DamageLedger.flushIfDirty();
	}

	public static void tick(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		try {
			for (ShareTeam team : manager.allTeams()) {
				if (SUPPRESSED_TEAMS.contains(team.teamId())) {
					continue;
				}
				TeamState state = manager.stateByTeamId(team.teamId());
				if (state == null) {
					continue;
				}

				List<ServerPlayer> online = aliveOnlineMembers(server, team);
				if (online.isEmpty()) {
					continue;
				}

				StatDelta delta = collectDeltas(team, online);
				int adjustedFoodDelta = FoodOverflowBuffer.get(server).apply(
						team.teamId(), state.foodLevel, delta.foodLevel());
				delta = new StatDelta(delta.healthLoss(), delta.healthGain(),
						delta.absorptionLoss(), delta.absorptionGain(), adjustedFoodDelta,
						delta.saturation(), delta.totalExperience());
				applyDeltas(state, state.maxHealth,
						sharedMaxAbsorption(online),
						delta, SharedFateMod.config.shareExperience);
				if (state.health <= 0.0F) {
					forgetTeam(team);
					DeathHandler.killTeamFromMirror(team, online);
					continue;
				}
				writeBack(team.teamId(), state, online);
			}
		} finally {
			SUPPRESSED_TEAMS.clear();
			DAMAGE_CAPTURED_THIS_TICK.clear();
			DamageLedger.flushIfDue();
		}
	}

	public static void syncPlayerNow(UUID teamId, TeamState state, ServerPlayer player) {
		writeBack(teamId, state, List.of(player));
	}

	private static List<ServerPlayer> aliveOnlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (isSharing(player)) {
				online.add(player);
			}
		}
		return online;
	}

	/** 지금 공유 풀에 참여하고 있는 팀원인가. 접속해 있고 살아 있어야 한다. */
	private static boolean isSharing(@Nullable ServerPlayer player) {
		return player != null && !player.isRemoved() && !player.isDeadOrDying();
	}

	private static StatDelta collectDeltas(ShareTeam team, List<ServerPlayer> online) {
		UUID teamId = team.teamId();
		List<PlayerDelta> deltas = new ArrayList<>(online.size());

		for (ServerPlayer player : online) {
			Snapshot last = LAST.get(player.getUUID());
			if (last == null || !last.teamId().equals(teamId)) {
				continue;
			}
			FoodData foodData = player.getFoodData();
			float playerHealthDelta = player.getHealth() - last.health();
			float playerAbsorptionDelta = player.getAbsorptionAmount() - last.absorption();
			float consumedAbsorption = consumedAbsorption(
					last.absorption(), player.getAbsorptionAmount(), player.getMaxAbsorption());
			recordDamage(team, player, last);
			if (playerHealthDelta < -0.01F || consumedAbsorption > 0.01F) {
				TeamBroadcaster.broadcastDamageAlert(online, player.getPlainTextName());
			}
			deltas.add(new PlayerDelta(
					playerHealthDelta, playerAbsorptionDelta, consumedAbsorption,
					foodData.getFoodLevel() - last.foodLevel(),
					foodData.getSaturationLevel() - last.saturation(),
					currentExperiencePoints(player) - last.experiencePoints()));
		}
		return fold(deltas);
	}

	/**
	 * 팀원별 변화량을 공유 풀 하나의 변화량으로 접는다.
	 *
	 * <p>체력 손실은 그대로 <em>합산</em>한다. 팀원 A 가 좀비에게, B 가 스켈레톤에게 같은 틱에
	 * 맞았다면 팀은 진짜로 두 번 맞은 것이므로 둘 다 세는 게 맞다.
	 *
	 * <p>합산하면 안 되는 경우 — 공유된 상태이상 하나가 팀 전원에게 똑같이 주는 피해 — 는
	 * 여기까지 오지 않는다. {@link SharedEffectDamage} 가 피해가 발생하는 자리에서 대표 한 명
	 * 것만 남기고 나머지를 막으므로, 막힌 팀원의 체력은 애초에 줄지 않아 변화량이 0 이다.
	 * 원인을 아는 자리에서 걸러야 여기서 "이 손실이 같은 원인인지"를 추측하지 않아도 된다.
	 */
	static StatDelta fold(List<PlayerDelta> deltas) {
		float healthLoss = 0.0F;
		float healthGain = 0.0F;
		AbsorptionDelta absorption = new AbsorptionDelta(0.0F, 0.0F);
		int food = 0;
		float saturation = 0.0F;
		long experience = 0;

		for (PlayerDelta delta : deltas) {
			if (delta.health() < 0.0F) {
				healthLoss += delta.health();
			} else {
				healthGain += delta.health();
			}
			if (delta.absorptionDelta() > 0.0F) {
				absorption = mergeAbsorptionDelta(absorption, delta.absorptionDelta());
			} else if (delta.absorptionConsumed() > 0.0F) {
				absorption = mergeAbsorptionDelta(absorption, -delta.absorptionConsumed());
			}
			food += delta.foodLevel();
			saturation += delta.saturation();
			experience += delta.experience();
		}
		return new StatDelta(
				healthLoss, healthGain, absorption.loss(), absorption.gain(),
				food, saturation, experience);
	}

	/**
	 * 팀에서 공유 상태이상 피해를 실제로 받을 한 명.
	 *
	 * <p>{@code team.members()} 순서로 처음 만나는, 접속해 있고 살아 있는 팀원이다. 팀 명단
	 * 순서는 안정적이라 같은 틱 안에서 여러 번 물어도 같은 사람이 나온다. 아무도 없으면
	 * {@code null} 이고, 그때는 {@link SharedEffectDamage} 가 어떤 피해도 막지 않는다.
	 */
	public static @Nullable ServerPlayer damageRepresentative(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (isSharing(player)) {
				return player;
			}
		}
		return null;
	}

	private static void recordDamage(ShareTeam team, ServerPlayer player, Snapshot last) {
		if (!DAMAGE_CAPTURED_THIS_TICK.add(player.getUUID())) {
			return;
		}
		float healthLoss = Math.max(0.0F, last.health() - player.getHealth());
		float absorptionLoss = consumedAbsorption(
				last.absorption(), player.getAbsorptionAmount(), player.getMaxAbsorption());
		DamageLedger.record(team, player, healthLoss + absorptionLoss);
	}

	static float consumedAbsorption(float previousAmount, float currentAmount, float currentMaximum) {
		float observedLoss = Math.max(0.0F, previousAmount - currentAmount);
		float capacityLoss = Math.max(0.0F, previousAmount - currentMaximum);
		return Math.max(0.0F, observedLoss - capacityLoss);
	}

	static AbsorptionDelta mergeAbsorptionDelta(AbsorptionDelta current, float delta) {
		if (delta < 0.0F) {
			return new AbsorptionDelta(current.loss() + delta, current.gain());
		}
		return new AbsorptionDelta(current.loss(), Math.max(current.gain(), delta));
	}

	static void applyDeltas(TeamState state, float maxHealth, float maxAbsorption,
			StatDelta delta, boolean shareExperience) {
		float availableAbsorption = clamp(
				state.absorption + delta.absorptionGain(), 0.0F, maxAbsorption);
		float totalDamage = Math.max(0.0F,
				-delta.healthLoss() - delta.absorptionLoss());
		float absorbed = Math.min(availableAbsorption, totalDamage);
		float overflowDamage = totalDamage - absorbed;
		state.absorption = availableAbsorption - absorbed;
		state.health = clamp(
				state.health + delta.healthGain() - overflowDamage, 0.0F, maxHealth);
		state.foodLevel = Math.round(clamp(state.foodLevel + delta.foodLevel(), 0.0F, 20.0F));
		state.saturation = clamp(state.saturation + delta.saturation(), 0.0F, state.foodLevel);
		if (shareExperience) {
			long experience = (long) state.totalExperience + delta.totalExperience();
			state.totalExperience = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, experience));
		}
	}

	private static void writeBack(UUID teamId, TeamState state, List<ServerPlayer> online) {
		for (ServerPlayer player : online) {
			if (player.getHealth() != state.health) {
				player.setHealth(state.health);
			}
			if (player.getAbsorptionAmount() != state.absorption) {
				player.setAbsorptionAmount(state.absorption);
			}

			FoodData food = player.getFoodData();
			if (food.getFoodLevel() != state.foodLevel) {
				food.setFoodLevel(state.foodLevel);
			}
			if (food.getSaturationLevel() != state.saturation) {
				food.setSaturation(state.saturation);
			}

			if (SharedFateMod.config.shareExperience
					&& currentExperiencePoints(player) != state.totalExperience) {
				applyTotalExperience(player, state.totalExperience);
			}

			LAST.put(player.getUUID(), new Snapshot(
					teamId, player.getHealth(), player.getAbsorptionAmount(), player.getMaxAbsorption(),
					food.getFoodLevel(),
					food.getSaturationLevel(), currentExperiencePoints(player)));
		}

		if (SharedFateMod.config.shareExperience) {
			state.xpLevel = online.getFirst().experienceLevel;
			state.xpProgress = online.getFirst().experienceProgress;
		}
	}

	private static float sharedMaxAbsorption(List<ServerPlayer> online) {
		float maximum = 0.0F;
		for (ServerPlayer player : online) {
			maximum = Math.max(maximum, player.getMaxAbsorption());
		}
		return Math.max(0.0F, maximum);
	}

	private static void applyTotalExperience(ServerPlayer player, int total) {
		int safeTotal = Math.max(0, total);
		int level = levelForExperiencePoints(safeTotal);
		int base = experiencePointsFor(level, 0.0F);
		player.experienceLevel = level;
		player.experienceProgress = (safeTotal - base) / (float) xpNeededForLevel(level);
		player.totalExperience = safeTotal;
	}

	public static int currentExperiencePoints(ServerPlayer player) {
		return experiencePointsFor(player.experienceLevel, player.experienceProgress);
	}

	public static void setTotalExperience(ServerPlayer player, int total) {
		applyTotalExperience(player, total);
	}

	public static void addSharedExperience(TeamState state, int points) {
		long combined = (long) state.totalExperience + Math.max(0, points);
		state.totalExperience = (int) Math.min(Integer.MAX_VALUE, combined);
	}

	static int experiencePointsFor(int level, float progress) {
		int safeLevel = Math.max(0, level);
		long base = baseExperienceForLevel(safeLevel);
		long current = base + Math.round(clamp(progress, 0.0F, 1.0F) * xpNeededForLevel(safeLevel));
		return (int) Math.min(Integer.MAX_VALUE, current);
	}

	private static long baseExperienceForLevel(int safeLevel) {
		if (safeLevel <= 15) {
			return (long) safeLevel * safeLevel + 6L * safeLevel;
		} else if (safeLevel <= 30) {
			long steps = safeLevel - 15L;
			return 315L + 37L * steps + 5L * steps * (steps - 1L) / 2L;
		} else {
			long steps = safeLevel - 30L;
			return 1395L + 112L * steps + 9L * steps * (steps - 1L) / 2L;
		}
	}

	private static int xpNeededForLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		if (level >= 15) {
			return 37 + (level - 15) * 5;
		}
		return 7 + level * 2;
	}

	private static int levelForExperiencePoints(int points) {
		int low = 0;
		int high = 1;
		while (high < 100_000 && baseExperienceForLevel(high) <= points) {
			high *= 2;
		}
		while (low + 1 < high) {
			int middle = low + (high - low) / 2;
			if (baseExperienceForLevel(middle) <= points) {
				low = middle;
			} else {
				high = middle;
			}
		}
		return low;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
