package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.PeriodicEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * {@code periodic} 증강의 주기를 돌리는 곳.
 *
 * <p>{@link PeriodicEffect} 는 "어느 구간에 무엇이 걸린다"만 알고, "지금 몇 시인가"와 "누구에게
 * 물어볼 것인가"는 여기서 정한다.
 *
 * <h2>기준 시각</h2>
 * <p>오버월드의 게임 시간({@code ServerLevel#getGameTime()})을 쓴다. 세 가지가 모두 필요해서다.
 *
 * <ul>
 *   <li><b>팀 단위</b> — 사람마다 다른 값이면 팀원끼리 구간이 어긋난다. 게임 시간은 월드에
 *       하나뿐이라 같은 틱에 접속해 있는 모두가 같은 값을 본다. 다른 차원에 있어도 마찬가지다.</li>
 *   <li><b>재시작 후에도 이어짐</b> — {@code MinecraftServer#getTickCount()} 는 서버를 켤 때마다
 *       0부터 다시 센다. 그 값을 쓰면 재시작할 때마다 주기가 처음으로 되감긴다. 게임 시간은
 *       {@code level.dat} 에 저장돼 이어진다.</li>
 *   <li><b>저장할 것이 없음</b> — 증강을 얻은 시각을 기준으로 삼으려면 그 시각을 팀 상태에
 *       저장해야 하고, 저장 형식이 바뀌면 기존 월드와의 호환을 따져야 한다. 게임 시간을 쓰면
 *       주기가 순수한 계산이 되어 저장할 상태가 하나도 없다. 대신 증강을 얻은 순간이 주기의
 *       처음이 아닐 수 있는데, "30초마다 돌아오는" 효과에서는 문제가 되지 않는다.</li>
 * </ul>
 *
 * <p>서버가 멈춰 있는 동안에는 게임 시간도 멈춘다. 아무도 접속하지 않아 월드가 흐르지 않는
 * 시간에 주기만 혼자 도는 일이 없으므로 이 편이 자연스럽다.
 *
 * <h2>비용</h2>
 * <p>매 틱 돌지만, 실제로 무언가를 붙였다 떼는 것은 구간이 바뀐 틱뿐이다. 나머지 틱에는
 * 팀 → 보유 증강 → 효과를 훑으며 구간 번호를 비교하고 끝난다. 보유 증강이 없거나 증강 풀이
 * 비어 있으면 첫 줄에서 되돌아 나온다.
 */
public final class PeriodicPerkManager {
	/** 접속을 끊은 사람의 기억을 정리하는 주기. 자주 할 이유가 없다. */
	private static final int CLEANUP_INTERVAL_TICKS = 600;

	/**
	 * 마지막으로 읽은 오버월드 게임 시간.
	 *
	 * <p>피해 배율 조회({@link PeriodicEffect#damageDealtMultiplier})에는 플레이어도 서버도
	 * 넘어오지 않는다. 기준 시각이 월드에 하나뿐이라 이렇게 들고 있어도 어긋나지 않는다.
	 */
	private static volatile long currentTick;

	private static int cleanupCounter;
	private static boolean warned;

	private PeriodicPerkManager() {
	}

	/** 마지막으로 읽은 게임 시간. 아직 한 번도 틱이 돌지 않았으면 0. */
	public static long currentTick() {
		return currentTick;
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드의 시각을 물려받지 않기 위해서다. */
	public static void reset() {
		currentTick = 0;
		cleanupCounter = 0;
		warned = false;
	}

	/**
	 * 팀마다 지금 구간을 판단해 접속 중인 팀원에게 반영한다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		try {
			currentTick = server.overworld().getGameTime();
			boolean cleanup = ++cleanupCounter >= CLEANUP_INTERVAL_TICKS;
			if (cleanup) {
				cleanupCounter = 0;
			}

			TeamManager manager = TeamManager.get(server);
			for (ShareTeam team : manager.allTeams()) {
				TeamState state = manager.stateByTeamId(team.teamId());
				if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
					continue;
				}
				tickTeam(server, team, state, currentTick, cleanup);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void tickTeam(MinecraftServer server, ShareTeam team, TeamState state,
			long time, boolean cleanup) {
		for (PerkStack stack : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(stack.perkId()).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (!(effect instanceof PeriodicEffect periodic)) {
					continue;
				}
				for (UUID member : team.members()) {
					ServerPlayer online = server.getPlayerList().getPlayer(member);
					if (online != null) {
						periodic.tick(online, stack.count(), time);
					}
				}
				if (cleanup) {
					periodic.forgetIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
				}
			}
		}
	}

	/** 테스트가 시각을 정해 두고 배율을 확인할 때 쓴다. */
	static void setCurrentTickForTesting(long time) {
		currentTick = time;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"주기 증강을 처리하지 못해 이번 틱은 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}
}
