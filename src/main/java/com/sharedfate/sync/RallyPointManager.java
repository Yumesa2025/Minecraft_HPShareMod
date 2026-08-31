package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.effect.SwapExplosionEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * 골드 「정거장」의 실행부. 위치 교환 순간 전원이 무작위로 뽑힌 한 명(목적지)의 자리로
 * 모이고, 15초 뒤 목적지가 아니었던 사람들만 각자 원래 자리로 돌아간다.
 *
 * <h2>목적지</h2>
 * <p>매 교환마다 새로 무작위로 뽑는다({@link PositionSwapManager.Position#gather}). 목적지가
 * 된 사람은 애초에 안 움직이므로 복귀 대상도 아니다. {@link TeamGathering}이 "무작위 한 명,
 * 매번 다시 뽑음"을 쓰는 것과 같은 이유다 — 고정하면 그 사람이 늘 유리해지고, 무게중심으로
 * 잡으면 아무도 없던 허공이나 용암 위가 될 수 있다.
 *
 * <h2>복귀에는 안전성 검사가 없다</h2>
 * <p>15초 사이에 원래 자리가 용암·몹·블록으로 막혔거나 폭발로 파여 있어도 그대로 보낸다.
 * 검사하지 않는 것이 확정된 설계다 — 그 위험 자체가 이 증강의 대가다.
 *
 * <h2>복귀에는 폭발이 붙지 않는다</h2>
 * <p>모이는 순간에는 {@code swap_explosion}이 각자의 옛 자리에서 한 번씩 터지지만(폭발
 * 교환과 같은 방식), 흩어지는 순간에는 안 터뜨린다. 전원이 같은 좌표(정거장)에서 동시에
 * 흩어지므로, 거기에도 터뜨리면 같은 자리에 폭발이 여러 발 겹쳐 터지고 한 번의 교환에
 * 폭발이 사실상 두 배가 된다.
 *
 * <h2>주기 타이머를 얼리지 않는다</h2>
 * <p>{@link StaggeredSwapManager}와 달리 복귀를 기다리는 동안에도
 * {@code TeamState.advancePositionSwapTick}은 평소대로 흐른다. 그래서 복귀 대기 중에 다음
 * 교환 주기가 먼저 오면, {@link #beginGather}를 다시 부르는 것만으로 이전 복귀 예약이
 * 새 모임으로 덮여 사라진다({@link #PENDING}의 같은 키에 새로 넣으면 끝이다) — "복귀를
 * 생략하고 곧바로 새 모임을 시작한다"는 규칙이 별도 처리 없이 자연스럽게 성립한다.
 */
public final class RallyPointManager {
	/** 모인 뒤 복귀까지 기다리는 시간. 15초다. */
	static final int RETURN_DELAY_TICKS = 300;

	private static final Map<UUID, PendingReturn> PENDING = new ConcurrentHashMap<>();

	private RallyPointManager() {
	}

	/** 이 팀이 지금 복귀를 기다리는 중인가. 시험·진단용. */
	public static boolean hasPendingReturn(UUID teamId) {
		return PENDING.containsKey(teamId);
	}

	/** 서버가 멈출 때 대기 중이던 복귀를 모두 지운다. */
	public static void reset() {
		PENDING.clear();
	}

	/** 팀이 전멸·해체될 때 그 팀의 대기만 지운다. */
	public static void forget(UUID teamId) {
		PENDING.remove(teamId);
	}

	/**
	 * 전원을 무작위 한 명의 자리로 모으고, 이동한 사람들의 15초 뒤 복귀를 예약한다.
	 *
	 * <p>옮기다 한 명이라도 실패하면 {@code TeamGathering.gatherTeam}과 같은 정책으로 이미
	 * 옮긴 사람을 되돌리고 아무 예약도 남기지 않는다.
	 */
	public static void beginGather(ShareTeam team, List<ServerPlayer> players,
			RandomGenerator random, List<SwapExplosionEffect> explosions) {
		if (players.size() < 2) {
			return;
		}
		int anchor = random.nextInt(players.size());
		List<PositionSwapManager.Position> origins =
				players.stream().map(PositionSwapManager.Position::capture).toList();
		PositionSwapManager.Position destination = origins.get(anchor);

		for (int index = 0; index < players.size(); index++) {
			if (index == anchor || destination.gather(players.get(index))) {
				continue;
			}
			PositionSwapManager.rollback(players, origins, index);
			Component failure = Component.literal("정거장으로 모으지 못해 원래 위치로 되돌렸습니다.");
			players.forEach(player -> player.sendSystemMessage(failure));
			SharedFateMod.LOGGER.warn("정거장 집합 중 {} 이동이 실패했습니다.",
					players.get(index).getPlainTextName());
			return;
		}

		List<UUID> moverIds = new ArrayList<>();
		List<PositionSwapManager.Position> moverOrigins = new ArrayList<>();
		for (int index = 0; index < players.size(); index++) {
			if (index == anchor) {
				continue;
			}
			moverIds.add(players.get(index).getUUID());
			moverOrigins.add(origins.get(index));
			for (SwapExplosionEffect explosion : explosions) {
				PositionSwapManager.triggerSwapExplosion(origins.get(index), players.get(index).getUUID(), explosion);
			}
		}

		announce(players, players.get(anchor).getPlainTextName());
		// 같은 팀 키에 새로 넣으면 이전에 대기 중이던 복귀는 조용히 사라진다.
		PENDING.put(team.teamId(), new PendingReturn(moverIds, moverOrigins, RETURN_DELAY_TICKS));
	}

	/** 대기 중인 복귀들을 한 틱씩 줄이고, 다 된 것은 돌려보낸다. */
	public static void tick(MinecraftServer server) {
		if (server == null || PENDING.isEmpty() || PerkChoiceSession.isActive()) {
			return;
		}
		for (UUID teamId : List.copyOf(PENDING.keySet())) {
			PendingReturn pending = PENDING.get(teamId);
			if (pending == null) {
				continue;
			}
			if (pending.ticksLeft > 0) {
				pending.ticksLeft--;
				continue;
			}
			returnHome(server, pending);
			PENDING.remove(teamId);
		}
	}

	/**
	 * 원래 자리로 그대로 돌려보낸다. <b>안전성 검사는 하지 않는다.</b> 15초 사이에 그 자리가
	 * 용암·구덩이·블록으로 막혔어도 검사 없이 보낸다 — 그 위험 자체가 대가로 확정된 설계다.
	 */
	private static void returnHome(MinecraftServer server, PendingReturn pending) {
		for (int i = 0; i < pending.moverIds.size(); i++) {
			ServerPlayer mover = server.getPlayerList().getPlayer(pending.moverIds.get(i));
			if (mover == null || mover.isRemoved() || mover.isDeadOrDying()) {
				continue;
			}
			PositionSwapManager.Position origin = pending.moverOrigins.get(i);
			if (origin.teleport(mover)) {
				mover.sendSystemMessage(Component.literal("정거장에서 원래 자리로 돌아왔습니다."));
			} else {
				SharedFateMod.LOGGER.warn("정거장 복귀 중 {} 이동이 실패했습니다.", mover.getPlainTextName());
			}
		}
	}

	private static void announce(List<ServerPlayer> players, String anchorName) {
		Component message = Component.literal("정거장으로 모였습니다. (" + anchorName + "님 위치, 15초 뒤 복귀)");
		for (ServerPlayer player : players) {
			player.sendSystemMessage(message);
		}
		TitleMessenger.showTitle(players,
				Component.literal("정거장!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.literal(anchorName + "님의 위치").withStyle(ChatFormatting.WHITE),
				0, 30, 10);
	}

	/** {@code LivingEntityEvents.AFTER_DEATH} 에 붙는 지점. 팀이 전멸하면 대기 중이던 복귀를 지운다. */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		ShareTeam team = TeamManager.get(server).teamOf(player.getUUID());
		if (team != null) {
			forget(team.teamId());
		}
	}

	/** 복귀를 기다리는 팀 하나. 저장하지 않는다 — 서버가 다시 뜨면 처음부터 다시 시작해도 된다. */
	private static final class PendingReturn {
		final List<UUID> moverIds;
		final List<PositionSwapManager.Position> moverOrigins;
		int ticksLeft;

		PendingReturn(List<UUID> moverIds, List<PositionSwapManager.Position> moverOrigins, int ticksLeft) {
			this.moverIds = moverIds;
			this.moverOrigins = moverOrigins;
			this.ticksLeft = ticksLeft;
		}
	}
}
