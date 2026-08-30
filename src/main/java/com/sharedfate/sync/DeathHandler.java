package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DeathHandler {
	private static final Set<UUID> SUPPRESSED_DROPS = new HashSet<>();
	private static final Set<UUID> CASCADING_TEAMS = new HashSet<>();

	private DeathHandler() {
	}

	public static boolean shouldDrop(Player player) {
		return !SUPPRESSED_DROPS.contains(player.getUUID());
	}

	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer dead)) {
			return;
		}

		MinecraftServer server = dead.level().getServer();
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(dead.getUUID());
		TeamState state = manager.stateOf(dead.getUUID());
		if (team == null || state == null) {
			return;
		}
		if (!CASCADING_TEAMS.add(team.teamId())) {
			return;
		}

		// 여기 온 사람이 팀에서 먼저 죽어 전멸을 부른 사람이다. 아래에서 죽이는 나머지
		// 팀원은 CASCADING_TEAMS 에 막혀 이 자리를 지나지 않는다.
		if (state.deathAlertEnabled) {
			TeamBroadcaster.broadcastTeamWipe(server, team, dead.getPlainTextName());
		}

		boolean keepInventory = dead.level().getGameRules().get(GameRules.KEEP_INVENTORY);
		StatMirror.suppressTeamForCurrentTick(team.teamId());
		StatMirror.captureExperienceBeforeDeath(server, team, state);
		StatMirror.captureDamageBeforeDeath(server, team);
		StatMirror.forgetTeam(team);
		try {
			if (!keepInventory) {
				for (UUID member : team.members()) {
					ServerPlayer player = server.getPlayerList().getPlayer(member);
					if (player == null) {
						continue;
					}
					var carried = player.containerMenu.getCarried();
					if (!carried.isEmpty()) {
						player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
						dead.drop(carried, true, false);
					}
				}
				InventorySwapper.drainDeathDrops(state,
						stack -> dead.drop(stack, true, false));
			} else {
				for (UUID member : team.members()) {
					ServerPlayer player = server.getPlayerList().getPlayer(member);
					if (player != null) {
						InventorySwapper.stashCarried(player, state);
					}
				}
			}

			for (UUID member : team.members()) {
				if (member.equals(dead.getUUID())) {
					continue;
				}
				ServerPlayer other = server.getPlayerList().getPlayer(member);
				if (other == null || other.isRemoved() || other.isDeadOrDying()) {
					continue;
				}
				if (SharedFateMod.config.shareExperience) {
					other.skipDropExperience();
				}
				SUPPRESSED_DROPS.add(member);
				try {
					other.setHealth(0.0F);
					other.die(source);
				} finally {
					SUPPRESSED_DROPS.remove(member);
				}
			}

			EffectSync.clearTeamEffects(server, team, state);
			state.resetAfterDeath(state.maxHealth, keepInventory);
			manager.setDirty();
			WorldResetCoordinator.request(server, team);
		} finally {
			SUPPRESSED_DROPS.clear();
			CASCADING_TEAMS.remove(team.teamId());
		}
	}

	public static void killTeamFromMirror(ShareTeam team, List<ServerPlayer> online) {
		if (online.isEmpty() || CASCADING_TEAMS.contains(team.teamId())) {
			return;
		}
		ServerPlayer primary = online.getFirst();
		primary.kill(primary.level());
	}
}
