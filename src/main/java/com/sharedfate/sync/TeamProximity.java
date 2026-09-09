package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.ProximityRangeEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「지금 이 팀이 얼마나 벌어져 있는가」를 한 곳에서 재고 나눠 쓴다.
 *
 * <h2>왜 따로 있는가</h2>
 * <p>「결속」 유형의 증강들은 저마다 다른 거리를 조건으로 쓴다 — 발맞춰 걷기 15칸, 방패벽·파문
 * 10칸, 운명 공동체·성역 20칸. 이들이 각자 팀원 좌표를 훑으면 <b>같은 계산을 한 틱에 다섯 번</b>
 * 하게 된다. 벌어진 정도(가장 먼 두 사람 사이의 거리)를 한 번만 재 두면 어떤 거리 조건이든
 * 숫자 비교 하나로 끝난다.
 *
 * <p>{@link TeamGathering} 이 이미 같은 계산을 하고 있지만 그쪽은 <b>정해진 거리를 넘었는가</b>만
 * 답하고 값을 남기지 않는다. 여기가 값을 들고 있는 자리다.
 *
 * <h2>차원이 다르면 무한대다</h2>
 * <p>좌표만 보면 네더의 (0,0) 과 오버월드의 (0,0) 이 붙어 있는 것으로 보이지만 서로 닿을 수 없다.
 * {@link TeamGathering#anyPairTooFar} 와 같은 규칙이다.
 *
 * <h2>혼자면 0 이다</h2>
 * <p>비교할 상대가 없으면 「뭉쳐 있다」로 본다. 한 명짜리 팀에서 결속 증강이 통째로 죽어 버리면
 * 혼자 시험해 볼 수가 없고, 「전원이 가깝다」는 말 그대로 읽어도 참이다.
 *
 * <h2>접속 안 한 사람은 세지 않는다</h2>
 * <p>세면 팀원 하나가 자는 동안 결속이 영영 안 켜진다. 거리를 재는 다른 자리
 * ({@code TeamGathering})도 접속자만 본다.
 */
public final class TeamProximity {

	/** 몇 틱마다 다시 재는가. 1초다. {@link TeamGathering#CHECK_INTERVAL_TICKS} 와 같은 결이다. */
	private static final int REFRESH_INTERVAL_TICKS = 20;

	/** 차원이 다르거나 잴 수 없을 때의 값. 어떤 조건과 비교해도 「벌어져 있다」가 된다. */
	public static final double APART = Double.MAX_VALUE;

	/** 팀별 마지막 측정값(블록). */
	private static final Map<UUID, Double> SPREAD = new ConcurrentHashMap<>();

	/** 팀별 거리 배율. 세트 「결속 3」이 켜지면 1.5 가 된다. */
	private static final Map<UUID, Double> RANGE = new ConcurrentHashMap<>();

	private static long now;
	private static boolean warned;

	private TeamProximity() {
	}

	/** {@code SharedFateMod} 가 서버 틱마다 부른다. */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++now % REFRESH_INTERVAL_TICKS != 0) {
			return;
		}
		try {
			refresh(server);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void refresh(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		SPREAD.clear();
		RANGE.clear();
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled) {
				continue;
			}
			SPREAD.put(team.teamId(), measure(server, team));
			RANGE.put(team.teamId(), TeamGathering.rangeMultiplier(state));
		}
	}

	/** 이 팀에서 가장 먼 두 사람 사이의 거리(블록). 혼자면 0, 차원이 다르면 {@link #APART}. */
	private static double measure(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				online.add(player);
			}
		}
		if (online.size() < 2) {
			return 0.0;
		}
		double worst = 0.0;
		for (int first = 0; first < online.size(); first++) {
			for (int second = first + 1; second < online.size(); second++) {
				ServerPlayer one = online.get(first);
				ServerPlayer other = online.get(second);
				if (one.level() != other.level()) {
					return APART;
				}
				Vec3 a = one.position();
				Vec3 b = other.position();
				worst = Math.max(worst, a.distanceTo(b));
			}
		}
		return worst;
	}

	/**
	 * 이 팀이 지금 이 거리 안에 뭉쳐 있는가.
	 *
	 * <p>적어 낸 거리에 <b>세트 「결속 3」의 배율이 저절로 먹는다.</b> 부르는 쪽은 정의에 적힌
	 * 값을 그대로 넘기면 된다.
	 *
	 * @param teamId   팀
	 * @param distance 정의에 적힌 거리(블록)
	 */
	public static boolean together(@Nullable UUID teamId, double distance) {
		if (teamId == null || !(distance > 0.0)) {
			return false;
		}
		Double spread = SPREAD.get(teamId);
		if (spread == null) {
			// 아직 한 번도 재지 않았다. 켜지지 않은 것으로 본다 — 없는 효과가 잠깐 켜지는 것보다
			// 있는 효과가 1초 늦게 켜지는 쪽이 안전하다.
			return false;
		}
		return spread <= scaled(teamId, distance);
	}

	/** 이 팀에서 이 거리에 배율을 먹인 값. 반경을 쓰는 효과들이 함께 쓴다. */
	public static double scaled(@Nullable UUID teamId, double distance) {
		if (teamId == null) {
			return distance;
		}
		Double range = RANGE.get(teamId);
		return ProximityRangeEffect.scale(distance, range == null ? 1.0 : range);
	}

	/** 마지막으로 잰 벌어진 정도(블록). 잰 적이 없으면 {@link #APART}. 시험과 진단용이다. */
	public static double spreadOf(@Nullable UUID teamId) {
		if (teamId == null) {
			return APART;
		}
		Double spread = SPREAD.get(teamId);
		return spread == null ? APART : spread;
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드로 넘어가지 않게 한다. */
	public static void reset() {
		SPREAD.clear();
		RANGE.clear();
		now = 0;
		warned = false;
	}

	/** 시험이 값을 직접 넣어 둘 때 쓴다. */
	static void putForTest(UUID teamId, double spread, double range) {
		SPREAD.put(teamId, spread);
		RANGE.put(teamId, range);
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn("팀이 벌어진 정도를 재지 못했습니다. 이 경고는 한 번만 남습니다.",
				error);
	}
}
