package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 서버를 <b>처음 상태</b>로 되돌린다 — 회차도 팀도 피해 기록도 없는 자리로.
 *
 * <h2>파일을 지우지 않는다. 이용한다</h2>
 * <p>여기서 {@code sharedfate-team-roster.json} 을 지워 봐야 소용이 없다.
 * {@link TeamRosterStore#onServerStopping} 이 <b>나가는 길에 지금 팀 명단을 다시 쓰기</b>
 * 때문이다. 막지 않고 이용한다 — 메모리의 팀·피해 기록을 비우면, 종료 저장 훅이 그 <b>빈
 * 상태를 그대로 적는다.</b> 같은 이유로 {@link DamageLedger} 도 파일이 아니라 메모리를 비우고
 * {@code dirty} 로 표시하면 그다음 저장이 빈 파일을 남긴다.
 *
 * <p>회차 파일({@code sharedfate-run-state.json})만은 <b>지금 건드리지 않는다.</b> 루프
 * 스크립트가 월드를 지운 뒤 {@code runNumber++} 를 하므로 지금 1 로 적어 두면 다음 회차가
 * 2 가 된다. 대신 {@link RunResetMarker} 를 남겨 <b>다음에 뜰 때</b> 1 로 누른다.
 *
 * <h2>차례</h2>
 * <ol>
 *   <li><b>표식을 먼저 쓴다.</b> 못 쓰면 아무것도 지우지 않고 그만둔다 — 여기서 실패하면
 *       「팀은 사라졌는데 회차는 올라간」 가장 나쁜 상태가 남는다.</li>
 *   <li>팀을 해체한다. 반드시 {@link InventorySwapper#disbandTeam} 으로 — 팀 상태를 버리기
 *       전에 증강 자국을 사람에게서 걷어내는 검증된 길이 그것뿐이다.</li>
 *   <li>피해 기록을 비운다.</li>
 *   <li>전멸 때 쓰는 카운트다운을 그대로 걸어 서버를 내린다. 월드 삭제와 재기동은 표식과
 *       루프 스크립트가 지금 그대로 한다.</li>
 * </ol>
 *
 * <h2>왜 {@code WorldResetCoordinator.request} 를 안 쓰는가</h2>
 * <p>그 진입점은 {@code config.resetWorldOnTeamDeath} 가 꺼져 있거나 이미 승리 상태면 아무
 * 일도 하지 않는다. 그 둘은 <b>「전멸로 월드를 갈아엎을 것인가」</b>에 대한 답이지 「운영자가
 * 서버를 초기화할 수 있는가」에 대한 답이 아니다. 그래서 같은 연출을 쓰되 진입점만
 * {@code requestRunReset} 으로 따로 둔다.
 */
public final class RunResetCoordinator {

	/** 초기화를 시도한 결과. */
	public enum Result {
		/** 지웠고 카운트다운이 걸렸다. */
		STARTED,
		/** 이미 종료 카운트다운이 돌고 있다. <b>아무것도 지우지 않았다.</b> */
		ALREADY_RUNNING,
		/** 표식을 쓰지 못했다. <b>아무것도 지우지 않았다.</b> */
		MARKER_FAILED
	}

	private RunResetCoordinator() {
	}

	/**
	 * 실제로 비우고 종료를 예약한다.
	 *
	 * @param server 지금 서버
	 * @param actor  명령을 친 사람. {@link InventorySwapper#disbandTeam} 이 서버를 찾고 바닥에
	 *               떨어진 아이템을 훑는 데 쓴다. <b>팀원이 아니어도 된다</b>
	 */
	public static Result reset(MinecraftServer server, ServerPlayer actor) {
		if (WorldResetCoordinator.countingDown()) {
			return Result.ALREADY_RUNNING;
		}

		Path serverDirectory = server.getServerDirectory().toAbsolutePath().normalize();
		try {
			RunResetMarker.write(serverDirectory);
		} catch (IOException | RuntimeException e) {
			SharedFateMod.LOGGER.error(
					"회차 되돌림 표식을 쓰지 못해 초기화를 그만둡니다. 아무것도 지우지 않았습니다: {}",
					serverDirectory, e);
			return Result.MARKER_FAILED;
		}

		int runNumber = RunProgressManager.runNumber();
		int disbanded = disbandAllTeams(server, actor);
		DamageLedger.clearAllRecords();

		int delayTicks = WorldResetCoordinator.requestRunReset(server);
		if (delayTicks <= 0) {
			// 위에서 countingDown() 을 이미 봤으므로 여기 오는 길은 없다. 그래도 조용히
			// 넘어가면 팀만 사라진 채 서버가 계속 도는 상태가 되므로 시끄럽게 남긴다.
			SharedFateMod.LOGGER.error(
					"초기화 카운트다운을 걸지 못했습니다. 서버를 손으로 내려 주세요.");
			return Result.ALREADY_RUNNING;
		}

		SharedFateMod.LOGGER.warn(
				"[RUN] 운영자 초기화: player={}, teams={}, runNumber={} → 다음 기동에서 1",
				actor.getPlainTextName(), disbanded, runNumber);
		return Result.STARTED;
	}

	/**
	 * 팀을 전부 해체한다.
	 *
	 * <p><b>{@code TeamManager.disband} 를 직접 부르지 않는다.</b>
	 * {@link InventorySwapper#disbandTeam} 만이 팀 상태를 버리기 전에
	 * {@code PerkManager.detach} 로 증강 자국을 사람에게서 걷어낸다. 걷어낼 효과를 찾으려면
	 * 보유 목록이 필요한데, 상태를 통째로 지우고 나면 무엇이 붙어 있었는지 알 길이 없다 —
	 * 그러면 속성과 상태이상이 사람 몸에 그대로 남는다. 이 코드베이스가 같은 자리에서 여러 번
	 * 틀린 적이 있다.
	 *
	 * @return 해체한 팀 수
	 */
	private static int disbandAllTeams(MinecraftServer server, ServerPlayer actor) {
		TeamManager manager = TeamManager.get(server);
		int disbanded = 0;
		// allTeams() 가 사본을 돌려주므로 도는 중에 해체해도 안전하다.
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				SharedFateMod.LOGGER.error(
						"상태 없는 팀은 해체하지 않고 둡니다: team={}", team.name());
				continue;
			}
			InventorySwapper.disbandTeam(actor, team, state, manager);
			disbanded++;
		}
		return disbanded;
	}
}
