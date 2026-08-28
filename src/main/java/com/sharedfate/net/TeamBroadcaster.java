package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkMilestones;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamBroadcaster {
	private static final TeamSyncPayload EMPTY = TeamSyncPayload.EMPTY;
	private static final Map<UUID, Integer> SELECTED_SLOTS = new HashMap<>();
	private static final Map<UUID, Integer> PENDING_SLOTS = new HashMap<>();

	/** 레벨 변화를 확인하는 주기. 매 틱 확인할 필요가 없다. */
	private static final int LEVEL_SCAN_INTERVAL_TICKS = 10;

	/** 팀별로 마지막에 보낸 (레벨, 다음 증강 레벨). 값이 바뀔 때만 다시 보낸다. */
	private static final Map<UUID, Long> LAST_LEVELS = new HashMap<>();
	private static int levelScanCooldown;

	private TeamBroadcaster() {
	}

	public static void sendTo(ServerPlayer player) {
		ShareTeam team = TeamManager.get(player.level().getServer()).teamOf(player.getUUID());
		sendIfSupported(player, team == null ? EMPTY : build(player.level().getServer(), team));
	}

	public static void broadcast(MinecraftServer server, ShareTeam team) {
		TeamSyncPayload payload = build(server, team);
		LAST_LEVELS.put(team.teamId(), packLevels(payload.xpLevel(), payload.nextPerkLevel()));
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				sendIfSupported(player, payload);
			}
		}
	}

	public static void sendEmpty(ServerPlayer player) {
		sendIfSupported(player, EMPTY);
	}

	public static void reportSelectedSlot(MinecraftServer server, ServerPlayer sender, int slot) {
		if (slot < 0 || slot >= 9) {
			return;
		}
		PENDING_SLOTS.put(sender.getUUID(), slot);
	}

	public static void flushSelectedSlots(MinecraftServer server) {
		for (Map.Entry<UUID, Integer> entry : PENDING_SLOTS.entrySet()) {
			UUID senderId = entry.getKey();
			int slot = entry.getValue();
			ServerPlayer sender = server.getPlayerList().getPlayer(senderId);
			if (sender == null || SELECTED_SLOTS.getOrDefault(senderId, -1) == slot) {
				continue;
			}
			SELECTED_SLOTS.put(senderId, slot);
			ShareTeam team = TeamManager.get(server).teamOf(senderId);
			if (team == null) {
				continue;
			}
			SelectedSlotPayload payload = new SelectedSlotPayload(senderId, slot);
			for (UUID member : team.members()) {
				if (member.equals(senderId)) {
					continue;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(member);
				if (player != null) {
					sendIfSupported(player, payload);
				}
			}
		}
		PENDING_SLOTS.clear();
	}

	/**
	 * 팀 공유 레벨이 바뀌었으면 팀 상태를 다시 보낸다.
	 *
	 * <p>{@link TeamSyncPayload} 는 원래 명단이 바뀔 때만 나가므로 레벨만 오르내리면
	 * 클라이언트 HUD 가 옛 값을 그대로 들고 있게 된다. 매 틱 전부 다시 보내지 않고
	 * 값이 실제로 달라졌을 때만 보낸다.
	 */
	public static void flushTeamLevels(MinecraftServer server) {
		if (++levelScanCooldown < LEVEL_SCAN_INTERVAL_TICKS) {
			return;
		}
		levelScanCooldown = 0;

		TeamManager manager = TeamManager.get(server);
		Set<UUID> living = new HashSet<>();
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				continue;
			}
			living.add(team.teamId());
			long levels = packLevels(Math.max(0, state.xpLevel), nextPerkLevel(state));
			Long previous = LAST_LEVELS.get(team.teamId());
			if (previous == null || previous != levels) {
				broadcast(server, team);
			}
		}
		// 해체된 팀의 기록은 버린다.
		LAST_LEVELS.keySet().retainAll(living);
	}

	/**
	 * 다음 증강이 나오는 레벨. 남은 구간이 없거나 증강을 쓰지 않는 팀이면 0.
	 *
	 * <p>이미 지나온 구간은 {@code lastPerkMilestone} 으로 판단한다. 경험치를 써서
	 * 레벨이 내려가도 이미 받은 구간이 다시 다음 목표가 되지는 않는다.
	 */
	static int nextPerkLevel(TeamState state) {
		if (!state.perksEnabled) {
			return 0;
		}
		int cleared = PerkMilestones.clampMilestone(Math.max(0, state.lastPerkMilestone));
		int next = cleared + PerkMilestones.STEP;
		return next > PerkMilestones.MAX ? 0 : next;
	}

	private static long packLevels(int xpLevel, int nextPerkLevel) {
		return ((long) xpLevel << 32) | (nextPerkLevel & 0xFFFFFFFFL);
	}

	public static void onDisconnect(ServerPlayer player) {
		UUID playerId = player.getUUID();
		SELECTED_SLOTS.remove(playerId);
		PENDING_SLOTS.remove(playerId);
		ShareTeam team = TeamManager.get(player.level().getServer()).teamOf(playerId);
		if (team != null) {
			broadcast(player.level().getServer(), team);
		}
	}

	public static void broadcastDamageAlert(List<ServerPlayer> online, String victimName) {
		DamageAlertPayload payload = new DamageAlertPayload(
				victimName, SharedFateMod.config.damageAlertDurationTicks);
		for (ServerPlayer player : online) {
			sendIfSupported(player, payload);
		}
	}

	private static TeamSyncPayload build(MinecraftServer server, ShareTeam team) {
		List<TeamSyncPayload.Member> members = new ArrayList<>();
		for (UUID id : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			String name = player != null ? player.getPlainTextName() : id.toString().substring(0, 8);
			int slot = SELECTED_SLOTS.getOrDefault(id, -1);
			members.add(new TeamSyncPayload.Member(id, name, slot));
		}
		TeamState state = TeamManager.get(server).stateByTeamId(team.teamId());
		int xpLevel = state == null ? 0 : Math.max(0, state.xpLevel);
		int nextPerkLevel = state == null ? 0 : nextPerkLevel(state);
		return new TeamSyncPayload(members, xpLevel, nextPerkLevel);
	}

	private static void sendIfSupported(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
