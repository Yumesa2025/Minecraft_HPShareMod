package com.sharedfate.team;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TeamLookup {
	private static @Nullable MinecraftServer server;

	private TeamLookup() {
	}

	public static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	public static @Nullable TeamState stateOf(UUID playerId) {
		MinecraftServer current = server;
		return current == null ? null : TeamManager.get(current).stateOf(playerId);
	}

	public static @Nullable TeamState serverStateOf(Player player) {
		if (!(player instanceof ServerPlayer)) {
			return null;
		}
		return stateOf(player.getUUID());
	}
}
