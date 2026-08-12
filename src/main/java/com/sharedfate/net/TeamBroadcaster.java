package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeamBroadcaster {
	private static final TeamSyncPayload EMPTY = new TeamSyncPayload(List.of());
	private static final Map<UUID, Integer> SELECTED_SLOTS = new HashMap<>();
	private static final Map<UUID, Integer> PENDING_SLOTS = new HashMap<>();

	private TeamBroadcaster() {
	}

	public static void sendTo(ServerPlayer player) {
		ShareTeam team = TeamManager.get(player.level().getServer()).teamOf(player.getUUID());
		sendIfSupported(player, team == null ? EMPTY : build(player.level().getServer(), team));
	}

	public static void broadcast(MinecraftServer server, ShareTeam team) {
		TeamSyncPayload payload = build(server, team);
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
		return new TeamSyncPayload(members);
	}

	private static void sendIfSupported(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
