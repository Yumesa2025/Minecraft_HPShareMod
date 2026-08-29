package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.PerkSwapRules;
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
		// 강제 증강 선택 중에는 시간이 멈춰 있고 팀원은 창에 갇혀 아무것도 할 수 없다. 그
		// 사이에 카운트다운이 흐르거나 자리가 뒤바뀌면 창을 닫자마자 낯선 곳에 서 있게 된다.
		// 세션이 사는 동안에는 주기 자체를 세우고 지나간다.
		if (PerkChoiceSession.isActive()) {
			return;
		}
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
				swapMoment(online, state);
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

	/**
	 * 자리가 바뀔 시점이 왔다. 증강이 끼어드는 자리는 여기 세 곳뿐이다.
	 *
	 * <p>순서가 중요하다. {@code swap_block} 이 막는 것은 <b>순간이동 한 자리</b>뿐이고,
	 * {@code on_swap} 은 막혔든 아니든 그대로 발동한다. 그래야 「뿌리내린 발」의 "원래 바뀔
	 * 시점마다 실명과 구속"이 성립한다. 주기 배율도 마찬가지로 막힘과 무관하게 먹인다.
	 *
	 * <p>{@code TeamState.advancePositionSwapTick} 이 이미 남은 틱을 주기 그대로 채워 넣은
	 * 뒤라, 마지막에 덮어쓰는 것으로 배율이 걸린다. 배율이 없으면 같은 값을 다시 쓰는 셈이라
	 * 아무 일도 일어나지 않는다.
	 */
	private static void swapMoment(List<ServerPlayer> online, TeamState state) {
		if (PerkSwapRules.blocksSwap(state)) {
			announceBlockedSwap(online);
		} else {
			swapTeamPositions(online, ThreadLocalRandom.current());
		}
		PerkSwapRules.grantOnSwap(state, online);
		state.positionSwapRemainingTicks = PerkSwapRules.nextRemainingTicks(state);
	}

	/**
	 * 자리가 바뀌지 않았음을 알린다.
	 *
	 * <p>카운트다운이 0까지 갔는데 아무 일도 일어나지 않으면 고장으로 보인다. 대가는 곧이어
	 * {@code on_swap} 이 얹으므로 여기서는 "버텼다"는 사실만 짧게 보여 준다.
	 *
	 * <p><b>자막에 증강 이름을 그대로 적는다.</b> 예전에는 「뿌리내린 발」이라는 이름에 맞춰
	 * "발이 땅에 붙어"라고 적었는데, 땅에 서 있는지가 조건인 것처럼 읽혀 공중에 떠서 교환을
	 * 피할 수 있다는 오해를 낳았다. 교환에는 땅 접촉 조건이 없다. 막는 것은
	 * {@link PerkSwapRules#blocksSwap} 이 보는 {@code swap_block} 증강뿐이다.
	 */
	private static void announceBlockedSwap(List<ServerPlayer> players) {
		TitleMessenger.showTitle(players,
				Component.literal("제자리").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
				Component.literal("「뿌리내린 발」이 위치 교환을 막았습니다").withStyle(ChatFormatting.WHITE),
				0, SWAP_TITLE_STAY_TICKS, SWAP_TITLE_FADE_OUT_TICKS);
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

	/**
	 * 옮기다 실패했을 때 이미 옮긴 사람들을 제자리로 되돌린다.
	 *
	 * <p>{@link TeamGathering} 도 같은 정책을 쓰므로 같은 패키지에 열어 뒀다. 아직 움직이지
	 * 않은 사람을 자기 원래 자리로 다시 보내도 결과는 같으므로, 부르는 쪽이 "누가 움직였는지"를
	 * 정확히 가려낼 필요는 없다.
	 */
	static void rollback(List<ServerPlayer> players, List<Position> origins, int lastAttempted) {
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

	/**
	 * 한 사람이 서 있던 자리. 차원까지 들고 있어 차원 간 이동도 그대로 처리된다.
	 *
	 * <p>{@link TeamGathering} 이 같은 방식으로 팀을 한곳에 모으므로 같은 패키지에 열어 뒀다.
	 */
	record Position(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
		static Position capture(ServerPlayer player) {
			return new Position(player.level(), player.getX(), player.getY(), player.getZ(),
					player.getYRot(), player.getXRot());
		}

		/** 이 자리로 옮긴다. 보고 있던 방향까지 원래 주인의 것으로 맞춘다. */
		boolean teleport(ServerPlayer player) {
			return player.teleportTo(level, x, y, z, Set.<Relative>of(), yaw, pitch, true);
		}

		/**
		 * 이 지점으로 옮기되 보고 있는 방향은 그대로 둔다.
		 *
		 * <p>여럿을 한곳에 모을 때 쓴다. 자리를 맞바꾸는 것과 달리 모이는 지점의 방향은 우연히
		 * 기준이 된 한 사람의 것이라, 나머지 전원의 시선을 그쪽으로 돌려 놓을 이유가 없다.
		 */
		boolean gather(ServerPlayer player) {
			return player.teleportTo(level, x, y, z, Set.<Relative>of(),
					player.getYRot(), player.getXRot(), true);
		}
	}
}
