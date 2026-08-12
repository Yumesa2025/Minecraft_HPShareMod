package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class PositionSwapManager {
	private PositionSwapManager() {
	}

	public static void tick(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.positionSwapEnabled()) {
				continue;
			}
			List<ServerPlayer> online = onlineMembers(server, team);
			if (state.advancePositionSwapTick(online.size() >= 2)) {
				swapTeamPositions(online, ThreadLocalRandom.current());
			}
		}
	}

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> result = new ArrayList<>();
		for (var memberId : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(memberId);
			if (player != null && !player.isRemoved() && !player.isDeadOrDying()) {
				result.add(player);
			}
		}
		return result;
	}

	static boolean swapTeamPositions(List<ServerPlayer> players, RandomGenerator random) {
		if (players.size() < 2) {
			return false;
		}
		List<Position> origins = players.stream().map(Position::capture).toList();
		int[] donors = derangedDonors(players.size(), random);

		for (int index = 0; index < players.size(); index++) {
			Position destination = origins.get(donors[index]);
			if (!destination.teleport(players.get(index))) {
				rollback(players, origins, index);
				Component failure = Component.literal("위치 교환에 실패해 원래 위치로 되돌렸습니다.");
				players.forEach(player -> player.sendSystemMessage(failure));
				SharedFateMod.LOGGER.warn("팀 위치 교환 중 {} 이동이 실패했습니다.",
						players.get(index).getPlainTextName());
				return false;
			}
		}

		for (int index = 0; index < players.size(); index++) {
			String donorName = players.get(donors[index]).getPlainTextName();
			players.get(index).sendSystemMessage(Component.literal(
					"위치 교환! " + donorName + "님의 위치로 이동했습니다."));
		}
		return true;
	}

	private static void rollback(List<ServerPlayer> players, List<Position> origins, int lastAttempted) {
		for (int index = 0; index <= lastAttempted; index++) {
			if (!origins.get(index).teleport(players.get(index))) {
				SharedFateMod.LOGGER.error("위치 교환 롤백에 실패했습니다: {}",
						players.get(index).getPlainTextName());
			}
		}
	}

	static int[] derangedDonors(int size, RandomGenerator random) {
		if (size < 2) {
			throw new IllegalArgumentException("위치 교환에는 두 명 이상이 필요합니다.");
		}
		int[] order = new int[size];
		for (int index = 0; index < size; index++) {
			order[index] = index;
		}
		for (int index = size - 1; index > 0; index--) {
			int other = random.nextInt(index + 1);
			int temporary = order[index];
			order[index] = order[other];
			order[other] = temporary;
		}

		int shift = random.nextInt(1, size);
		int[] donors = new int[size];
		for (int position = 0; position < size; position++) {
			donors[order[position]] = order[(position + shift) % size];
		}
		return donors;
	}

	private record Position(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
		private static Position capture(ServerPlayer player) {
			return new Position(player.level(), player.getX(), player.getY(), player.getZ(),
					player.getYRot(), player.getXRot());
		}

		private boolean teleport(ServerPlayer player) {
			return player.teleportTo(level, x, y, z, Set.<Relative>of(), yaw, pitch, true);
		}
	}
}
