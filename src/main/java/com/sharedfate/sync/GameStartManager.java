package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 「게임 시작」 — 회차가 실제로 시작되는 한 순간.
 *
 * <h2>왜 팀 생성이 아니라 따로 눌러야 하는가</h2>
 * <p>예전에는 회차가 <b>월드에 붙어</b> 있었다. {@code RunProgressManager} 가 서버가 뜰 때
 * {@code sharedfate-run-state.json} 을 읽고 팀이 있는지도 보지 않은 채 「1회차 진행 중」으로
 * 시작했다. 그래서 팀을 만들기 전에 돌아다닌 시간도, 팀원을 기다리며 서 있던 시간도 전부
 * 회차에 들어갔다. 회차의 시작점을 사람이 정하는 한 순간으로 옮긴 것이 이 클래스다.
 *
 * <h2>누르는 것은 <b>1회차 전 한 번뿐</b>이다</h2>
 * <p>기다림이 필요한 자리는 <b>팀을 만들고 팀원을 모으는 동안</b>뿐이다. 전멸해서 회차가
 * 넘어갈 때는 이미 다 모여 있고 새 월드도 비어 있으므로, 거기서 또 단추를 요구하면 회차마다
 * 아무 뜻 없는 확인을 한 번씩 더 하는 것이 된다. 그래서 <b>2회차부터는 언제나
 * 「진행 중」</b>이다({@link #syncRunStart}).
 *
 * <p>자동 시작이 「게임 시작」과 같은 일을 다시 하지는 않는다. 새 월드는 시각이 이미 0 이고
 * 팀 상태도 {@code TeamState.fresh} 로 새로 만들어져 아이템·경험치·증강 구간이 전부 비어
 * 있으며, 접속하는 사람은 어차피 월드 스폰에 떨어진다. <b>남는 일은 둘</b>이다 —
 * 「유산」이 넘긴 장비를 인벤토리에 넣는 것과, 위치 교환의 남은 시간을 주기 그대로 채우는 것.
 * 둘 다 {@link #syncRunStart} 가 한다.
 *
 * <h2>누르면 무엇이 일어나는가</h2>
 * <ol>
 *   <li>{@link TeamState#runStarted} 가 참이 된다 — 「대기」에서 「진행 중」으로.</li>
 *   <li><b>시간이 0 이 된다.</b> 오버월드 시계의 누적 틱을 0(첫날 아침)으로 맞추고,
 *       회차 경과 시간({@code difficultyElapsedTicks})도 0 에서 다시 센다.</li>
 *   <li><b>접속 중인 팀원 전원을 월드 스폰으로 옮긴다.</b></li>
 *   <li><b>모든 아이템을 없앤다.</b> 공유 인벤토리·추가 3줄·방어구·공유 엔더상자까지
 *       전부 비운다. 드랍하지 않고 <b>지운다</b> — 시작하자마자 발밑에서 다시 주울 수 있으면
 *       맨몸으로 시작한다는 뜻이 없다.</li>
 *   <li>체력·허기·경험치·상태이상·증강 구간·다시 뽑기 횟수가 전부 회차 처음의 값이 된다.</li>
 *   <li>「유산」이 지난 회차에서 몰수해 둔 장비만은 이 청소가 끝난 <b>뒤에</b> 돌려준다.</li>
 * </ol>
 *
 * <h2>되돌릴 수 없으므로 실수로 도는 길이 없어야 한다</h2>
 * <p>{@link #start} 로 들어오는 길은 {@code /shareteam start confirm} 하나뿐이다. 팀 화면의
 * 단추도 결국 그 명령을 보낸다({@code TeamScreen}). 확인 낱말 없이 {@code /shareteam start}
 * 만 치면 무엇이 사라지는지 안내만 하고 아무것도 하지 않는다. {@code disband confirm} 과 같은
 * 방식이다. 게다가 여기서 <b>리더 여부와 이미 시작했는지를 다시 확인</b>하므로, 화면이 단추를
 * 잘못 그려도 서버에서 걸린다.
 *
 * <h2>시작 전에는 무엇이 멈춰 있는가</h2>
 * <ul>
 *   <li><b>증강 구간</b> — {@code PerkManager.tick} 이 시작하지 않은 팀을 건너뛴다. 시작하면서
 *       레벨과 지나온 구간을 0 으로 되돌리므로, 대기 중에 올린 레벨로 증강을 받는 길도 없다.</li>
 *   <li><b>위치 교환</b> — {@code PositionSwapManager.tick} 이 건너뛴다. 시작하는 순간 남은
 *       시간이 주기 그대로 채워져 첫 교환은 시작으로부터 한 주기 뒤다.</li>
 *   <li><b>난이도 상승</b> — {@code DifficultyEscalation} 이 시간을 세지 않는다.</li>
 *   <li><b>전멸 판정</b> — {@link #blocksDamage} 로 <b>아예 죽지 않게</b> 했다. 까닭은 그
 *       메서드 문서에 있다.</li>
 *   <li><b>승리 판정</b> — {@code RunProgressManager.onDeath} 가 시작하지 않은 팀의 드래곤
 *       처치를 회차 승리로 세지 않는다.</li>
 * </ul>
 */
public final class GameStartManager {
	/** 「게임 시작!」 타이틀이 화면에 남는 시간과 사라지는 시간(틱). */
	private static final int TITLE_STAY_TICKS = 50;
	private static final int TITLE_FADE_OUT_TICKS = 10;

	private GameStartManager() {
	}

	/** {@code /shareteam start} 의 결과. 명령과 화면이 안내 문구를 고르는 데 쓴다. */
	public enum StartResult {
		STARTED,
		NO_TEAM,
		NOT_LEADER,
		ALREADY_STARTED
	}

	// ------------------------------------------------------------------ 판정

	/**
	 * 이 팀 상태가 「시작 대기」인가.
	 *
	 * <p>{@code null}(팀이 없음)은 대기가 아니다. 팀에 속하지 않은 사람에게는 회차라는 것이
	 * 아예 없으므로, 여기서 참을 돌려주면 그 사람까지 무적이 되고 아무 이득도 없다.
	 */
	public static boolean waiting(@Nullable TeamState state) {
		return state != null && !state.runStarted;
	}

	/** 이 팀 상태의 회차가 진행 중인가. 팀이 없으면 참으로 본다 — 막을 것이 없다는 뜻이다. */
	public static boolean started(@Nullable TeamState state) {
		return state == null || state.runStarted;
	}

	/**
	 * 시작을 기다리는 동안 이 대상의 피해를 통째로 버리는가.
	 *
	 * <p>{@code LivingEntityPerkDamageMixin} 의 {@code hurtServer} HEAD 에서 부른다.
	 * {@code PerkChoiceSession.blocksDamage} 와 같은 자리, 같은 방식이다.
	 *
	 * <h2>왜 「죽으면 되살아난다」가 아니라 「죽지 않는다」인가</h2>
	 * <p>이 모드는 체력을 공유하므로 <b>한 명이 죽으면 팀이 전멸</b>한다. 시작 전에 그 판정이
	 * 돌면 둘 중 하나가 된다.
	 * <ul>
	 *   <li>전멸을 그대로 인정한다 — 아직 시작도 안 한 회차 때문에 월드가 지워지고 회차 번호가
	 *       오른다. 사람이 하려던 일과 정반대다.</li>
	 *   <li>전멸만 막는다 — 이 서버는 <b>하드코어</b>다. 죽은 사람은 관전자가 되고, 관전에서
	 *       빠져나오는 길은 월드 초기화뿐이다. 초기화를 막으면 <b>영원히 관전자로 갇힌다.</b></li>
	 * </ul>
	 * <p>그래서 죽는 일 자체를 없앴다. 시작 전은 팀을 모으고 설정을 확인하는 자리이지 게임이
	 * 아니므로, 그 사이의 낙하·굶주림·몹은 회차와 아무 관계가 없다. 넉백이나 산소 게이지처럼
	 * 피해가 아닌 것은 그대로 도는데, 시작하는 순간 체력·허기가 전부 회차 처음 값으로
	 * 되돌아가므로 남는 것이 없다.
	 *
	 * <p>팀에 속하지 않은 사람과 몹은 첫 줄에서 곧바로 빠져나간다.
	 *
	 * <h2>게임 오버 카운트다운 5초도 여기서 막는다</h2>
	 * <p>전멸이 확정되고 서버가 종료되기까지의 5초 동안은 <b>사람이든 몹이든</b> 피해를 전부
	 * 버린다. 이미 예약된 「폭발 교환」이 종료 직전에 터지거나, 관전자가 아닌 다른 접속자가
	 * 그 사이에 죽어 또 하나의 전멸 처리가 도는 일을 없애기 위해서다. 회차는 이미 끝났으므로
	 * 그 5초에 일어나는 피해에는 아무 뜻이 없다. 자세한 것은
	 * {@link WorldResetCoordinator#countingDown()} 에 적어 뒀다.
	 */
	public static boolean blocksDamage(@Nullable Entity entity) {
		if (WorldResetCoordinator.countingDown()) {
			return true;
		}
		if (!(entity instanceof ServerPlayer player)) {
			return false;
		}
		return waiting(TeamLookup.stateOf(player.getUUID()));
	}

	/**
	 * 이 서버에서 회차가 실제로 굴러가고 있는가.
	 *
	 * <p>팀이 하나도 없으면 거짓이다 — 아무도 시작하지 않았다는 뜻이 맞다. 팀이 여럿이면
	 * 하나라도 시작했으면 참으로 본다. 보스바 문구를 고르는 데만 쓴다.
	 */
	public static boolean anyTeamStarted(@Nullable MinecraftServer server) {
		if (server == null) {
			return false;
		}
		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state != null && state.runStarted) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 자동 시작

	/** 단추 없이 저절로 진행 중이 되는 첫 회차. 1회차만이 「게임 시작」을 기다린다. */
	private static final int FIRST_AUTO_START_RUN = 2;

	/**
	 * 이 회차는 <b>단추 없이 저절로 진행 중</b>인가.
	 *
	 * <p>판단 기준은 오직 <b>회차 번호</b>다. 서버가 어떤 길로 떴는지도, 저장에 무엇이 적혀
	 * 있는지도 보지 않는다.
	 *
	 * <h2>왜 「복원하는 순간」이 아니라 「회차 번호」인가</h2>
	 * <p>예전에는 {@code TeamRosterStore} 가 <b>명단을 복원하는 순간</b>에만 회차를 켰다. 그
	 * 자리는 월드에 팀이 하나도 없을 때만 지나므로, <b>이미 팀이 살아 있는 월드에서 서버만 다시
	 * 켜면 그 길을 타지 않는다.</b> 그러면 5회차를 굴리던 팀이 「시작 대기」에 영영 갇힌다 —
	 * 실제로 그렇게 갇혔고, 그 상태에서 「게임 시작」을 누르면 5회차 동안 모은 것이 전부
	 * 사라진다. 회차가 시작되었는지는 <b>서버가 뜬 길과 아무 상관이 없는 사실</b>이므로, 그
	 * 사실을 그대로 들고 있는 값 하나 — 회차 번호 — 로만 판단한다.
	 */
	public static boolean autoStarts(int runNumber) {
		return runNumber >= FIRST_AUTO_START_RUN;
	}

	/**
	 * 서버가 어떤 월드를 열었는가. 자동 시작이 <b>무엇까지 해도 되는지</b>가 여기서 갈린다.
	 *
	 * <p>둘 중 어느 쪽도 <b>아이템을 지우거나 사람을 옮기거나 시각을 되돌리지 않는다.</b> 그것은
	 * 사람이 「게임 시작」을 누를 때만 하는 일이고({@link #start}), 자동 시작이 그 일까지 하면
	 * 서버를 다시 켤 때마다 진행 중이던 팀의 물건이 사라진다.
	 */
	public enum WorldOrigin {
		/**
		 * 전멸로 월드가 지워지고 <b>새로 열린</b> 월드. 명단만 복원했고 팀 상태는
		 * {@code TeamState.fresh} 라 아이템·경험치·증강 구간이 전부 비어 있다.
		 */
		FRESH_WORLD,
		/**
		 * <b>이미 굴러가던</b> 월드가 그대로 다시 열렸다. 아이템도 경험치도 살아 있고, 위치
		 * 교환의 남은 시간도 세다 만 값이다. 여기서는 상태를 「진행 중」으로 맞추는 것 말고는
		 * 진행 상황을 <b>하나도 건드리지 않는다.</b>
		 */
		ONGOING_WORLD
	}

	/**
	 * 회차 번호에 맞춰 팀의 「시작했는가」를 맞춘다. <b>2회차 이상이면 언제나 진행 중이다.</b>
	 *
	 * <p>부르는 곳은 셋이다 — 서버가 뜰 때의 두 갈래({@code TeamRosterStore.onServerStarted} 의
	 * 새 월드 갈래와 기존 월드 갈래)와 팀을 새로 만들 때({@code ShareTeamCommand}). 어느 길로
	 * 들어와도 답이 같아야 하므로 판단은 {@link #autoStarts} 한 곳에만 있다.
	 *
	 * <h2>여기서 하는 일 — 그리고 <b>하지 않는 일</b></h2>
	 * <p>「시작 대기」인 팀에만 손대고, 이미 진행 중인 팀은 통째로 건너뛴다. 손대는 팀에 하는
	 * 일은 셋뿐이다.
	 * <ol>
	 *   <li>{@link TeamState#runStarted} 를 올린다.</li>
	 *   <li><b>「유산」이 넘긴 장비를 인벤토리에 넣는다.</b> 회차 경계를 넘겨 지키기로 한 유일한
	 *       물건이라 회차가 시작되는 자리에서 반드시 들어가야 한다. 시작하지 않은 팀은 증강을
	 *       고를 수 없으므로({@code PerkManager.tick} 가 건너뛴다) 이 목록에 들어 있는 것은
	 *       언제나 <b>지난 회차에서 넘어온 것</b>뿐이고, 이번 회차에 몰수된 것이 섞일 길이 없다.</li>
	 *   <li>위치 교환의 <b>남은 시간</b>을 채운다. 새 월드에서는 주기 그대로 — 첫 교환은 한 주기
	 *       뒤여야 한다. 이미 굴러가던 월드에서는 <b>세다 만 값이 있으면 그대로 둔다.</b>
	 *       0 일 때만 채우는데, 그 0 은 「곧 교환할 때가 됐다」가 아니라 「대기 중이라 한 번도
	 *       세지 않았다」는 뜻이라({@code PositionSwapManager.tick} 가 대기 중인 팀을 건너뛴다)
	 *       그대로 두면 시작하는 순간 첫 교환이 터진다.</li>
	 * </ol>
	 * <p><b>아이템을 지우지 않고, 사람을 스폰으로 옮기지 않고, 시각을 되돌리지 않고, 경험치와
	 * 증강 구간도 건드리지 않는다.</b> 그 다섯은 {@link #start} 만 한다.
	 *
	 * @param runNumber {@code RunProgressManager.runNumber()} 가 들고 있는 지금 회차
	 * @return 실제로 시작시킨 팀의 수
	 */
	public static int syncRunStart(@Nullable TeamManager manager, int runNumber, WorldOrigin origin) {
		if (manager == null || !autoStarts(runNumber)) {
			return 0;
		}
		int started = 0;
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || state.runStarted) {
				continue;
			}
			state.runStarted = true;
			if (origin == WorldOrigin.FRESH_WORLD || state.positionSwapRemainingTicks <= 0) {
				state.positionSwapRemainingTicks = state.positionSwapIntervalTicks;
			}
			restoreLegacyGear(state);
			started++;
		}
		if (started > 0) {
			manager.setDirty();
			SharedFateMod.LOGGER.info(
					"[RUN] {}회차라 회차를 자동으로 시작했습니다: teams={}, 월드={}",
					runNumber, started,
					origin == WorldOrigin.FRESH_WORLD ? "새 월드" : "이미 굴러가던 월드");
		}
		return started;
	}

	// ------------------------------------------------------------------ 시작

	/**
	 * 회차를 시작한다. <b>되돌릴 수 없다.</b>
	 *
	 * <p>부르는 곳은 {@code /shareteam start confirm} 한 곳뿐이다. 그래도 여기서 세 가지를 다시
	 * 확인한다 — 팀이 있는가, 리더인가, 아직 시작하지 않았는가. 확인에 걸리면 아무것도 건드리지
	 * 않고 이유만 돌려준다.
	 */
	public static StartResult start(@Nullable MinecraftServer server, @Nullable ServerPlayer requester) {
		if (server == null || requester == null) {
			return StartResult.NO_TEAM;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(requester.getUUID());
		TeamState state = manager.stateOf(requester.getUUID());
		if (team == null || state == null) {
			return StartResult.NO_TEAM;
		}
		if (!requester.getUUID().equals(team.leader())) {
			return StartResult.NOT_LEADER;
		}
		if (state.runStarted) {
			return StartResult.ALREADY_STARTED;
		}

		List<ServerPlayer> online = onlineMembers(server, team);
		state.runStarted = true;

		resetWorldClock(server);
		wipeItems(state, online);
		restoreLegacyGear(state);
		resetRunProgress(state);
		teleportToSpawn(server, online);
		resyncPlayers(server, team, state, online);

		manager.setDirty();
		TeamBroadcaster.broadcast(server, team);
		announce(team, state, online, requester);
		SharedFateMod.LOGGER.info(
				"[RUN] 게임 시작: team={} leader={} online={}/{}",
				team.name(), requester.getPlainTextName(), online.size(), team.size());
		return StartResult.STARTED;
	}

	/**
	 * 오버월드 시계를 첫날 아침(누적 0틱)으로 되돌린다.
	 *
	 * <p>26.2 의 시계는 하루 안의 시각이 아니라 <b>누적 틱</b>이고 달 위상도 그 값에서 나온다.
	 * 회차의 시작이란 곧 1일차 아침이므로 여기서만은 누적 틱을 통째로 0 으로 덮어쓴다.
	 * ({@code time_lock} 증강이 날짜를 보존하는 것과 반대인데, 그쪽은 1초마다 도는 자리라
	 * 날짜를 지우면 영영 0 에 붙박이기 때문이다.)
	 *
	 * <p>네더는 시계 자체가 없고 엔드는 {@code minecraft:the_end} 를 쓰므로 손대지 않는다.
	 *
	 * <p><b>시계는 서버에 하나뿐이다.</b> 팀이 여럿이면 나중에 시작한 팀이 먼저 시작한 팀의
	 * 시각까지 되돌린다. 이 모드는 {@code singleTeamOnly} 로 팀을 하나만 만들게 하므로
	 * ({@code TeamManager.canCreateNewTeam}) 실제 운영에서는 일어나지 않고, 그 설정을 끈
	 * 서버에서는 {@code time_lock} 증강이 이미 같은 성질을 갖고 있다.
	 */
	private static void resetWorldClock(MinecraftServer server) {
		try {
			Holder<WorldClock> clock = server.registryAccess().get(WorldClocks.OVERWORLD).orElse(null);
			if (clock == null) {
				SharedFateMod.LOGGER.warn("오버월드 시계를 찾지 못해 시각을 0으로 맞추지 못했습니다.");
				return;
			}
			server.clockManager().setTotalTicks(clock, 0L);
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("회차 시작 시 월드 시각을 0으로 맞추지 못했습니다.", error);
		}
	}

	/**
	 * 공유 아이템을 전부 <b>지운다.</b>
	 *
	 * <p>{@code InventorySwapper.drainSharedItems} 가 훑는 자리가 곧 이 팀이 가진 전부다 —
	 * 공유 인벤토리 36칸, 추가 27칸, 방어구·오프핸드, 넘침 목록, 공유 엔더상자 27칸.
	 * 팀 해체가 같은 메서드로 아이템을 <b>드랍</b>하는 것과 달리 여기서는 받는 쪽이 아무것도
	 * 하지 않는다. 그것이 「없앤다」와 「내려놓는다」의 차이다.
	 *
	 * <p>창을 먼저 닫는다. 상자나 조합대를 연 채로 밑바탕이 비면 클라이언트 쪽 칸이 실제와
	 * 어긋난 채로 남고, 손에 쥐고 있던 스택은 아예 이 청소를 지나가지 않는다.
	 */
	private static void wipeItems(TeamState state, List<ServerPlayer> online) {
		for (ServerPlayer player : online) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			player.closeContainer();
		}
		InventorySwapper.drainSharedItems(state, stack -> {
		});
	}

	/**
	 * 「유산」이 지난 회차에서 몰수해 둔 장비를 돌려준다. <b>청소가 끝난 뒤여야 한다.</b>
	 *
	 * <p>{@code TeamManager.restoreFreshRoster} 는 회차 경계를 넘어온 목록을
	 * {@link TeamState#legacyGear} 에 담아만 두고 인벤토리에 꽂지 않는다. 회차가 시작되는
	 * 자리에서 처음으로 인벤토리에 들어가야 하기 때문이다 — 1회차 전이라면 「게임 시작」이
	 * 인벤토리를 통째로 비운 <b>뒤에</b>({@link #start}), 2회차부터라면 회차 번호를 보고 저절로
	 * 시작할 때({@link #syncRunStart}). 앞의 경우에 미리 꽂아 두면 그 청소에 함께 쓸려 나가
	 * 「유산」이 아무 뜻도 없는 증강이 된다.
	 *
	 * <p>자리가 모자라면 {@code overflowItems} 에 남아 칸이 비는 대로 자동으로 들어온다
	 * ({@code PerkItemGrants} 가 즉시 지급을 넣을 때와 같은 경로).
	 */
	private static void restoreLegacyGear(TeamState state) {
		if (state.legacyGear.isEmpty()) {
			return;
		}
		List<ItemStack> inherited = new ArrayList<>(state.legacyGear);
		state.legacyGear.clear();
		state.overflowItems.addAll(inherited);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		SharedFateMod.LOGGER.info("[PERK] 「유산」으로 넘어온 장비 {}개를 회차 시작 인벤토리에 넣었습니다.",
				inherited.size());
	}

	/**
	 * 회차마다 0 에서 다시 세는 값들을 전부 되돌린다.
	 *
	 * <p><b>경험치를 지우는 이유</b>가 특히 중요하다. 증강은 팀 공유 레벨의 3·6·9… 구간에서
	 * 나오는데, 대기 중에 올린 레벨을 그대로 들고 시작하면 시작하자마자 증강 선택창이 몇 개나
	 * 터진다. 아이템을 전부 지우면서 그 아이템으로 올린 레벨만 남겨 두는 것도 앞뒤가 맞지
	 * 않는다. 「회차의 0점」이라는 말이 아이템에만 걸리고 레벨에는 안 걸릴 이유가 없다.
	 *
	 * <p>{@code ownedPerks} 는 건드리지 않는다. 시작 전에는 증강 구간 자체가 돌지 않으므로
	 * 정상적인 길로는 언제나 비어 있고, 여기 무언가 들어 있다면 운영자가 시험 명령으로 넣어 둔
	 * 것뿐이라 지울 이유가 없다.
	 */
	private static void resetRunProgress(TeamState state) {
		state.health = state.maxHealth;
		state.absorption = 0.0F;
		state.foodLevel = 20;
		state.saturation = 5.0F;
		state.xpLevel = 0;
		state.xpProgress = 0.0F;
		state.totalExperience = 0;
		state.effects.clear();
		state.lastPerkMilestone = 0;
		state.pending.clear();
		state.rerollsRemaining = state.rerollAllowance;
		state.difficultyElapsedTicks = 0;
		// 첫 교환은 시작으로부터 한 주기 뒤다. 대기하는 동안 흘러 있던 남은 시간을 그대로 두면
		// 시작하자마자 자리가 뒤바뀐다.
		state.positionSwapRemainingTicks = state.positionSwapIntervalTicks;
	}

	/**
	 * 접속 중인 팀원을 월드 스폰으로 옮긴다.
	 *
	 * <p>접속하지 않은 팀원은 옮길 수 없다. 그 사람은 로그아웃한 자리에서 다시 시작하게 되는데,
	 * 인벤토리는 공유라 이미 비어 있다. 시작 안내에 접속 인원을 함께 적는 이유가 이것이다.
	 *
	 * <p>옮기지 못한 사람이 있어도 회차는 그대로 시작한다. 여기서 되돌리면 「아이템은 사라졌는데
	 * 회차는 시작되지 않은」 상태가 되어 훨씬 나쁘다.
	 */
	private static void teleportToSpawn(MinecraftServer server, List<ServerPlayer> online) {
		try {
			ServerLevel overworld = server.overworld();
			LevelData.RespawnData spawn = overworld.getRespawnData();
			BlockPos pos = spawn.pos();
			double x = pos.getX() + 0.5;
			double y = pos.getY();
			double z = pos.getZ() + 0.5;
			for (ServerPlayer player : online) {
				if (!player.teleportTo(overworld, x, y, z, Set.<Relative>of(),
						spawn.yaw(), spawn.pitch(), true)) {
					SharedFateMod.LOGGER.warn("회차 시작 시 {} 를 스폰으로 옮기지 못했습니다.",
							player.getPlainTextName());
				}
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("회차 시작 시 팀원을 스폰으로 옮기지 못했습니다.", error);
		}
	}

	/** 비운 결과를 실제 플레이어에게 반영한다. 여기를 빠뜨리면 화면에만 옛 값이 남는다. */
	private static void resyncPlayers(MinecraftServer server, ShareTeam team, TeamState state,
			List<ServerPlayer> online) {
		EffectSync.clearTeamEffects(server, team, state);
		// 다음 틱의 델타 계산이 「방금 지운 만큼 잃었다」로 읽히면 안 된다. 기억을 버리고 나서
		// 새 값을 써 넣어야 그 값이 곧 다음 기준이 된다.
		StatMirror.forgetTeam(team);
		for (ServerPlayer player : online) {
			MaxHealthAttribute.apply(player, state.maxHealth);
			StatMirror.setTotalExperience(player, 0);
			StatMirror.syncPlayerNow(team.teamId(), state, player);
			EffectSync.refreshPlayer(player);
			player.containerMenu.broadcastChanges();
		}
	}

	private static void announce(ShareTeam team, TeamState state, List<ServerPlayer> online,
			ServerPlayer requester) {
		TitleMessenger.showTitle(online,
				Component.literal("게임 시작!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal("'" + team.name() + "' 팀 · 1일차 아침").withStyle(ChatFormatting.WHITE),
				0, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS);
		Component message = Component.literal(
				"[SharedFate] " + requester.getPlainTextName() + "님이 게임을 시작했습니다."
						+ "\n시각을 1일차 아침으로 맞추고, 모든 아이템을 없애고, 접속 중인 팀원 "
						+ online.size() + "/" + team.size() + "명을 스폰으로 옮겼습니다."
						+ (state.difficultyEscalationEnabled
								? "\n난이도 상승은 지금부터 셉니다." : "")
						+ (state.positionSwapEnabled()
								? "\n첫 위치 교환은 " + state.positionSwapIntervalMinutes()
										+ "분 뒤입니다." : ""));
		for (ServerPlayer player : online) {
			player.sendSystemMessage(message);
		}
	}

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> result = new ArrayList<>();
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null && !player.isRemoved()) {
				result.add(player);
			}
		}
		return result;
	}
}
