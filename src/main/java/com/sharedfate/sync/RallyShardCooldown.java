package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkRallyShard;
import com.sharedfate.perk.effect.RallyShardEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「소집의 조각」의 쿨타임을 <b>팀이 함께</b> 쓰게 만든다.
 *
 * <h2>왜 필요한가</h2>
 * <p>바닐라 {@code ItemCooldowns} 는 사람마다 따로 돈다. 그런데 조각은 팀 공유 인벤토리에
 * 들어 있어 <b>누구나 집어 쓸 수 있다.</b> 사람마다 따로 돌면 네 명이 번갈아 누르는 것으로
 * 4분 쿨타임이 사실상 1분이 된다. 팀 전원을 한 자리로 끌어오는 힘에 비해 너무 헐겁다.
 *
 * <p>그래서 쿨타임 하나를 <b>팀에</b> 매달고, 그 값을 접속한 팀원 전원의 바닐라 쿨타임에도
 * 그대로 적어 준다. 화면에 도는 게이지와 {@code PerkItemCooldownDisplay} 의 남은 시간이
 * 바닐라 경로 그대로 나오게 하려는 것이다 — 그리는 쪽을 새로 만들지 않는다.
 *
 * <h2>진짜 판정은 팀 쪽이다</h2>
 * <p>바닐라 쿨타임은 <b>보여 주기 위한 사본</b>이다. 실제로 「쓸 수 있는가」는
 * {@link #onCooldown} 이 답한다. 사본이 어긋날 수 있는 자리가 둘 있기 때문이다.
 *
 * <ul>
 *   <li>쿨타임이 도는 동안 접속한 사람 — 들어오는 순간 {@link #onPlayerJoin} 이 남은 만큼
 *       다시 걸어 준다.</li>
 *   <li>죽었다 살아난 사람 — 부활은 바닐라 쿨타임을 그대로 두지만, 팀 전멸이 회차를 끝내면
 *       {@link #forget} 이 팀 쿨타임을 아예 지운다.</li>
 * </ul>
 *
 * <h2>저장하지 않는다</h2>
 * <p>서버를 껐다 켜면 쿨타임이 풀린다. 회차 자체가 서버와 함께 가는 것이 아니고, 남겨 두면
 * 「분명 안 썼는데 못 쓴다」가 되어 더 나쁘다.
 */
public final class RallyShardCooldown {
	private static final Map<UUID, Integer> REMAINING = new ConcurrentHashMap<>();

	private RallyShardCooldown() {
	}

	/** 서버가 멈출 때 전부 지운다. */
	public static void reset() {
		REMAINING.clear();
	}

	/** 팀이 전멸·해체될 때 그 팀의 쿨타임만 지운다. */
	public static void forget(@Nullable UUID teamId) {
		if (teamId != null) {
			REMAINING.remove(teamId);
		}
	}

	/** 이 팀이 지금 쿨타임 중인가. 조각을 쓸 수 있는지 가리는 <b>유일한</b> 판정이다. */
	public static boolean onCooldown(@Nullable UUID teamId) {
		return remainingTicks(teamId) > 0;
	}

	/** 남은 틱. 없으면 0. */
	public static int remainingTicks(@Nullable UUID teamId) {
		if (teamId == null) {
			return 0;
		}
		Integer left = REMAINING.get(teamId);
		return left == null ? 0 : Math.max(0, left);
	}

	/**
	 * 쿨타임을 팀에 건다. 조각을 실제로 쓴 직후에 부른다.
	 *
	 * @param server 접속한 팀원에게 사본을 걸기 위해 필요하다. 없으면 팀 쿨타임만 건다
	 */
	public static void begin(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable RallyShardEffect effect) {
		if (team == null || effect == null || effect.cooldownTicks() <= 0) {
			return;
		}
		REMAINING.put(team.teamId(), effect.cooldownTicks());
		applyToOnline(server, team, effect, effect.cooldownTicks());
	}

	/** 한 틱 줄인다. 다 된 팀은 목록에서 뺀다. */
	public static void tick(MinecraftServer server) {
		if (REMAINING.isEmpty()) {
			return;
		}
		for (UUID teamId : List.copyOf(REMAINING.keySet())) {
			REMAINING.computeIfPresent(teamId, (ignored, left) -> left <= 1 ? null : left - 1);
		}
	}

	/**
	 * 쿨타임이 도는 팀에 뒤늦게 들어온 사람에게 남은 만큼 사본을 걸어 준다.
	 *
	 * <p>이것이 없으면 게이지가 비어 있는데 눌러도 안 되는 상태가 된다.
	 */
	public static void onPlayerJoin(@Nullable ServerPlayer player) {
		if (player == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		if (team == null) {
			return;
		}
		int left = remainingTicks(team.teamId());
		if (left <= 0) {
			return;
		}
		RallyShardEffect effect = PerkRallyShard.effectOf(manager.stateByTeamId(team.teamId()));
		applyCooldown(player, effect, left);
	}

	private static void applyToOnline(@Nullable MinecraftServer server, ShareTeam team,
			RallyShardEffect effect, int ticks) {
		if (server == null) {
			return;
		}
		for (UUID memberId : team.members()) {
			applyCooldown(server.getPlayerList().getPlayer(memberId), effect, ticks);
		}
	}

	/**
	 * 한 사람의 바닐라 쿨타임에 사본을 적는다.
	 *
	 * <p>어떤 조각이든 {@code minecraft:use_cooldown} 에 같은 묶음 이름을 달고 있으므로, 견본
	 * 하나로 걸어도 그 사람이 든 조각 전부에 걸린다({@link RallyShardEffect#COOLDOWN_GROUP}).
	 */
	private static void applyCooldown(@Nullable ServerPlayer player,
			@Nullable RallyShardEffect effect, int ticks) {
		if (player == null || effect == null || ticks <= 0) {
			return;
		}
		try {
			ItemStack template = effect.createItem();
			if (template != null && !template.isEmpty()) {
				player.getCooldowns().addCooldown(template, ticks);
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("소집의 조각 쿨타임을 {} 에게 걸지 못했습니다.",
					player.getPlainTextName(), error);
		}
	}

	/** 시험이 상태를 격리할 때 쓴다. */
	static void clearForTesting() {
		REMAINING.clear();
	}

	/** 시험이 살아 있는 월드 없이 쿨타임을 걸 때 쓴다. */
	static void beginForTesting(UUID teamId, int ticks) {
		if (teamId != null && ticks > 0) {
			REMAINING.put(teamId, ticks);
		}
	}
}
