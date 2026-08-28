package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class PositionSwapManager {
	private static final int TICKS_PER_SECOND = 20;
	/** 교환에 필요한 최소 인원. */
	private static final int MIN_SWAP_MEMBERS = 2;
	/** 카운트다운 기본 길이(초). 설정이 없을 때 쓴다. */
	static final int DEFAULT_COUNTDOWN_SECONDS = 5;
	/** 교환 직후 "위치 교환!" 타이틀이 화면에 남는 시간과 사라지는 시간(틱). */
	private static final int SWAP_TITLE_STAY_TICKS = 30;
	private static final int SWAP_TITLE_FADE_OUT_TICKS = 10;

	private PositionSwapManager() {
	}

	public static void tick(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		int countdownSeconds = configuredCountdownSeconds();
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.positionSwapEnabled()) {
				continue;
			}
			List<ServerPlayer> online = onlineMembers(server, team);
			boolean enoughMembers = online.size() >= MIN_SWAP_MEMBERS;
			if (state.advancePositionSwapTick(enoughMembers)) {
				swapTeamPositions(online, ThreadLocalRandom.current());
				continue;
			}
			if (!enoughMembers) {
				continue;
			}
			int secondsLeft = countdownSecondsToShow(
					state.positionSwapRemainingTicks, countdownSeconds);
			if (secondsLeft > 0) {
				announceCountdown(online, secondsLeft);
			}
		}
	}

	private static int configuredCountdownSeconds() {
		return SharedFateMod.config == null
				? DEFAULT_COUNTDOWN_SECONDS : SharedFateMod.config.positionSwapCountdownSeconds;
	}

	/**
	 * 이번 틱에 보여 줄 카운트다운 숫자. 보여 줄 게 없으면 0.
	 *
	 * <p>{@link TeamState#advancePositionSwapTick(boolean)}이 이미 1틱 깎은 뒤의 남은 틱을 받는다.
	 * 딱 1초 경계(20의 배수)일 때만 값을 돌려주므로 초당 한 번씩만 패킷이 나간다.
	 *
	 * @param remainingTicks   교환까지 남은 틱
	 * @param countdownSeconds 카운트다운 길이(초). 0 이하면 카운트다운을 끈다.
	 */
	static int countdownSecondsToShow(int remainingTicks, int countdownSeconds) {
		if (countdownSeconds <= 0 || remainingTicks <= 0) {
			return 0;
		}
		if (remainingTicks > countdownSeconds * TICKS_PER_SECOND) {
			return 0;
		}
		if (remainingTicks % TICKS_PER_SECOND != 0) {
			return 0;
		}
		return remainingTicks / TICKS_PER_SECOND;
	}

	/**
	 * 남은 초를 화면(액션바)에 띄운다.
	 *
	 * <p>채팅이 아니라 화면이어야 하고, 매초 갱신되므로 타이틀 대신 액션바를 쓴다. 타이틀은 갱신할
	 * 때마다 페이드 애니메이션이 다시 시작돼 깜빡이고, 화면 중앙(조준점)을 가려 전투 중에 위험하다.
	 */
	private static void announceCountdown(List<ServerPlayer> players, int secondsLeft) {
		Component text = Component.literal("위치 교환까지 " + secondsLeft + "초")
				.withStyle(secondsLeft <= 2 ? ChatFormatting.RED : ChatFormatting.GOLD,
						ChatFormatting.BOLD);
		TitleMessenger.showActionBar(players, text);
		for (ServerPlayer player : players) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
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
			ServerPlayer moved = players.get(index);
			moved.sendSystemMessage(Component.literal(
					"위치 교환! " + donorName + "님의 위치로 이동했습니다."));
			// 카운트다운이 0이 된 순간을 화면에서도 확인할 수 있게 짧은 타이틀을 함께 띄운다.
			TitleMessenger.showTitle(moved,
					Component.literal("위치 교환!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
					Component.literal(donorName + "님의 위치").withStyle(ChatFormatting.WHITE),
					0, SWAP_TITLE_STAY_TICKS, SWAP_TITLE_FADE_OUT_TICKS);
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
