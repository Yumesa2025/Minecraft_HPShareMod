package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import com.sharedfate.ui.RunResetMessages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

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
 *   <li><b>월드를 지울 수 있는 자리인지 먼저 본다</b>({@link RunResetPreflight}). 월드 표식은
 *       5초 뒤 {@link WorldResetCoordinator#tick} 에서야 쓰이는데, 거기서 실패하면 서버는
 *       내려가지 않고 그때는 이미 팀도 기록도 없다. 그래서 <b>지우기 전에</b> 같은 조건을
 *       본다.</li>
 *   <li><b>회차 표식을 쓴다.</b> 못 쓰면 아무것도 지우지 않고 그만둔다 — 여기서 실패하면
 *       「팀은 사라졌는데 회차는 올라간」 가장 나쁜 상태가 남는다.</li>
 *   <li>팀을 해체한다. 반드시 {@link InventorySwapper#disbandTeam} 으로 — 팀 상태를 버리기
 *       전에 증강 자국을 사람에게서 걷어내는 검증된 길이 그것뿐이다.</li>
 *   <li>피해 기록을 비운다.</li>
 *   <li>전멸 때 쓰는 카운트다운을 그대로 걸어 서버를 내린다. 월드 삭제와 재기동은 표식과
 *       루프 스크립트가 지금 그대로 한다.</li>
 * </ol>
 *
 * <p>1번을 통과하고도 5초 뒤에 실패할 길은 남아 있다(그 사이에 누가 표식을 만들어 놓는 따위).
 * 그때는 {@link WorldResetCoordinator#tick} 의 실패 처리가 <b>회차 표식을 거둬</b> 적어도
 * 「초기화는 안 됐는데 회차만 1로 눌리는」 상태는 남기지 않는다.
 *
 * <h2>왜 {@code WorldResetCoordinator.request} 를 안 쓰는가</h2>
 * <p>그 진입점은 {@code config.resetWorldOnTeamDeath} 가 꺼져 있거나 이미 승리 상태면 아무
 * 일도 하지 않는다. 그 둘은 <b>「전멸로 월드를 갈아엎을 것인가」</b>에 대한 답이지 「운영자가
 * 서버를 초기화할 수 있는가」에 대한 답이 아니다. 그래서 같은 연출을 쓰되 진입점만
 * {@code requestRunReset} 으로 따로 둔다.
 */
public final class RunResetCoordinator {

	/**
	 * 초기화를 시도한 결과.
	 *
	 * <p>{@link #STARTED} 말고는 <b>전부 아무것도 지우지 않은 상태</b>다. 그 사실을 값마다
	 * 되풀이해 적는 이유는, 이 명령이 「되돌릴 수 없다」고 경고하고 실행되기 때문이다 — 실패를
	 * 본 사람이 가장 먼저 알아야 할 것은 「그래서 지금 뭐가 사라졌나」다.
	 */
	public enum Result {
		/** 지웠고 카운트다운이 걸렸다. */
		STARTED,
		/** 이미 종료 카운트다운이 돌고 있다. <b>아무것도 지우지 않았다.</b> */
		ALREADY_RUNNING,
		/** 회차 표식을 쓰지 못했다. <b>아무것도 지우지 않았다.</b> */
		MARKER_FAILED,
		/**
		 * 월드 폴더가 서버 루트 바로 아래가 아니다 — 싱글플레이·LAN.
		 * <b>아무것도 지우지 않았다.</b>
		 */
		WORLD_NOT_UNDER_SERVER_ROOT,
		/** 지난 월드 초기화의 표식이 아직 남아 있다. <b>아무것도 지우지 않았다.</b> */
		STALE_WORLD_MARKER;

		/** 실제로 초기화가 시작됐는가. */
		public boolean started() {
			return this == STARTED;
		}

		/**
		 * 실패를 사람에게 알리는 글. 성공이면 빈 문자열.
		 *
		 * <p><b>값과 문구를 한자리에 묶어 둔다.</b> 부르는 쪽마다 switch 를 따로 쓰면 값을 늘릴
		 * 때 한 곳을 빠뜨리게 되고, 그러면 되돌릴 수 없는 명령이 <b>아무 말도 없이</b> 실패한다.
		 * 여기서는 switch 식이 모든 값을 덮으므로 값만 늘리면 컴파일이 멈춘다.
		 *
		 * @param worldMarkerPath 월드 표식 파일의 경로. {@link #STALE_WORLD_MARKER} 문구가
		 *                        지울 파일을 짚어 주는 데만 쓴다
		 */
		public String failureText(String worldMarkerPath) {
			return switch (this) {
				case STARTED -> "";
				case ALREADY_RUNNING -> RunResetMessages.alreadyCountingDown();
				case MARKER_FAILED -> RunResetMessages.markerFailed();
				case WORLD_NOT_UNDER_SERVER_ROOT -> RunResetMessages.worldNotResettable();
				case STALE_WORLD_MARKER -> RunResetMessages.staleWorldMarker(worldMarkerPath);
			};
		}
	}

	private RunResetCoordinator() {
	}

	/**
	 * 지금 초기화를 시작할 수 있는가. 못 하면 그 까닭, 할 수 있으면 {@code null}.
	 *
	 * <p><b>되묻기 전에도 이것을 본다.</b> 「되돌릴 수 없습니다」를 읽고 수락까지 한 사람에게
	 * 「사실은 이 서버에서는 안 됩니다」라고 말하는 것보다, 되묻기를 띄우기 전에 막는 편이 낫다.
	 * {@link #reset} 도 같은 것을 한 번 더 본다 — 되묻는 30초 사이에 표식이 생길 수 있다.
	 */
	public static @Nullable Result blocker(MinecraftServer server) {
		if (WorldResetCoordinator.countingDown()) {
			return Result.ALREADY_RUNNING;
		}
		return blockedBy(preflight(server));
	}

	/**
	 * 사전 검증 결과를 명령이 쓰는 {@link Result} 로 옮긴다. 통과면 {@code null}.
	 *
	 * <p>{@link RunResetPreflight} 를 {@code MinecraftServer} 에서 떼어 둔 대가로 이 옮김이
	 * 한 번 필요하다. 그 대신 판정 전체에 단위 시험이 닿는다.
	 */
	public static @Nullable Result blockedBy(RunResetPreflight.Outcome outcome) {
		return switch (outcome) {
			case READY -> null;
			case WORLD_NOT_UNDER_SERVER_ROOT -> Result.WORLD_NOT_UNDER_SERVER_ROOT;
			case STALE_WORLD_MARKER -> Result.STALE_WORLD_MARKER;
		};
	}

	/** 살아 있는 서버에서 사전 검증에 필요한 경로 둘을 뽑아 판정한다. */
	public static RunResetPreflight.Outcome preflight(MinecraftServer server) {
		return RunResetPreflight.inspect(
				server.getServerDirectory(), server.getWorldPath(LevelResource.ROOT));
	}

	/** 실패 문구에 끼워 넣을 월드 표식 경로. 명령이 {@link Result#failureText} 에 넘긴다. */
	public static String worldMarkerPath(MinecraftServer server) {
		return WorldResetCoordinator.markerFile(server.getServerDirectory()).toString();
	}

	/**
	 * 실제로 비우고 종료를 예약한다.
	 *
	 * @param server 지금 서버
	 * @param actor  명령을 친 사람. {@link InventorySwapper#disbandTeam} 이 서버를 찾고 바닥에
	 *               떨어진 아이템을 훑는 데 쓴다. <b>팀원이 아니어도 된다</b>
	 */
	public static Result reset(MinecraftServer server, ServerPlayer actor) {
		// 아무것도 건드리기 전에 본다. 여기서 걸리면 팀도 기록도 표식도 그대로다.
		Result blocked = blocker(server);
		if (blocked != null) {
			SharedFateMod.LOGGER.warn(
					"초기화를 시작하지 않았습니다. 아무것도 지우지 않았습니다: 까닭={}, serverDirectory={}",
					blocked, server.getServerDirectory().toAbsolutePath().normalize());
			return blocked;
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
	 * <h2>한 팀이 터져도 멈추지 않는다</h2>
	 * <p>{@link InventorySwapper#disbandTeam} 은 증강을 걷어내고 인벤토리를 되돌리고 바닥
	 * 아이템을 훑는다 — 남의 코드를 여럿 거치므로 던질 수 있는 자리가 많다. 그것이 그대로
	 * 올라가면 <b>카운트다운을 걸기도 전에</b> 이 메서드가 끝나서, 팀은 절반만 사라지고 서버는
	 * 계속 돈다. 가장 나쁜 결말이다.
	 *
	 * <p>그래서 팀마다 감싸고 <b>계속 돈다.</b> 여기서 되돌리지 않는 이유는 되돌릴 수가 없기
	 * 때문이다 — 해체된 팀은 다시 세울 수 없다. 남은 길은 <b>끝까지 가서 카운트다운을 거는</b>
	 * 것뿐이고, 그러면 월드째 새로 열리면서 반쪽 상태도 함께 사라진다. 실패한 팀 수는 로그에
	 * 남긴다.
	 *
	 * @return 해체한 팀 수
	 */
	private static int disbandAllTeams(MinecraftServer server, ServerPlayer actor) {
		TeamManager manager = TeamManager.get(server);
		int disbanded = 0;
		int failed = 0;
		// allTeams() 가 사본을 돌려주므로 도는 중에 해체해도 안전하다.
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				SharedFateMod.LOGGER.error(
						"상태 없는 팀은 해체하지 않고 둡니다: team={}", team.name());
				failed++;
				continue;
			}
			try {
				InventorySwapper.disbandTeam(actor, team, state, manager);
				disbanded++;
			} catch (RuntimeException e) {
				// 한 팀이 터져도 나머지는 해체하고 카운트다운까지 간다. 멈추면 반쪽 상태로
				// 서버가 계속 돈다.
				SharedFateMod.LOGGER.error(
						"팀 해체가 실패했습니다. 나머지 팀은 그대로 해체합니다: team={}",
						team.name(), e);
				failed++;
			}
		}
		if (failed > 0) {
			SharedFateMod.LOGGER.error(
					"해체하지 못한 팀이 있습니다. 월드가 새로 열리면 함께 사라집니다: 실패={}, 성공={}",
					failed, disbanded);
		}
		return disbanded;
	}
}
