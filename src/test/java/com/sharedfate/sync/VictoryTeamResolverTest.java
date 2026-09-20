package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.sync.VictoryTeamResolver.Candidates;
import com.sharedfate.sync.VictoryTeamResolver.Outcome;
import com.sharedfate.sync.VictoryTeamResolver.Resolution;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 드래곤을 잡은 팀을 찾는 <b>순수 판정</b>.
 *
 * <p>엔티티에서 후보를 뽑는 {@code candidatesOf} 는 살아 있는 월드가 있어야 해서 단위 시험으로
 * 닿지 않는다. 대신 <b>뽑아 온 후보로 무엇을 고르는가</b>를 전부 확인한다 — 사고가 났던 자리가
 * 바로 거기다. 간접 처치로 후보가 하나도 없을 때 예전 코드는 곧바로 포기했고, 그 결과 승리
 * 책과 회차 기록이 통째로 빠졌다.
 */
class VictoryTeamResolverTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
	private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000f9");

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/** 회차를 이미 시작한 팀 하나를 만든다. 실제 운영에서 가장 흔한 모양이다. */
	private static ShareTeam startedTeam(TeamManager manager, String name, UUID leader) {
		ShareTeam team = manager.createTeam(name, leader, 20.0F);
		manager.stateByTeamId(team.teamId()).runStarted = true;
		return team;
	}

	/** 아직 「게임 시작」을 누르지 않은 팀. */
	private static ShareTeam waitingTeam(TeamManager manager, String name, UUID leader) {
		ShareTeam team = manager.createTeam(name, leader, 20.0F);
		manager.stateByTeamId(team.teamId()).runStarted = false;
		return team;
	}

	// ------------------------------------------------------------------ 1단계

	@Test
	void 때린_사람이_있으면_그_사람의_팀이다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = startedTeam(manager, "모험가들", ALICE);

		Resolution resolved = VictoryTeamResolver.resolve(manager,
				new Candidates(ALICE, null, null));

		assertSame(team, resolved.team());
		assertEquals(ALICE, resolved.killer());
		assertEquals(Outcome.CAUSING_PLAYER, resolved.outcome());
		assertTrue(resolved.victory());
	}

	// ------------------------------------------------------------------ 2단계

	/**
	 * <b>이 시험이 이 파일의 핵심이다.</b>
	 *
	 * <p>점화한 사람이 {@code causingEntity} 에 실리지 않는 폭발(명령으로 소환한 TNT, 연쇄
	 * 점화로 소유자를 잃은 TNT)이 드래곤을 잡으면 바닐라는 아무도 기억하지 않는다. 직접 원인의
	 * <b>간접 소유자</b>까지 한 단계 풀어야 사람이 나온다.
	 *
	 * <p><b>침대는 여기서도 안 잡힌다</b> — 직접 원인 자체가 없다. 그쪽은 4단계가 받는다.
	 */
	@Test
	void 때린_사람이_없으면_직접_원인의_소유자를_본다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = startedTeam(manager, "모험가들", ALICE);

		Resolution resolved = VictoryTeamResolver.resolve(manager,
				new Candidates(null, ALICE, null));

		assertSame(team, resolved.team());
		assertEquals(ALICE, resolved.killer());
		assertEquals(Outcome.INDIRECT_OWNER, resolved.outcome());
	}

	// ------------------------------------------------------------------ 3단계

	/** 지금까지 유일하게 보던 자리. 여전히 그대로 동작해야 한다. */
	@Test
	void 앞의_둘이_비면_드래곤이_기억하는_마지막_가해자를_본다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = startedTeam(manager, "모험가들", ALICE);

		Resolution resolved = VictoryTeamResolver.resolve(manager,
				new Candidates(null, null, ALICE));

		assertSame(team, resolved.team());
		assertEquals(Outcome.LAST_HURT_BY_PLAYER, resolved.outcome());
	}

	// ------------------------------------------------------------------ 단계 순서

	/** 앞 단계에서 사람을 찾으면 뒤는 보지 않는다. 순서가 뒤집히면 엉뚱한 팀이 이긴다. */
	@Test
	void 앞_단계에서_찾으면_뒤는_보지_않는다() {
		TeamManager manager = new TeamManager();
		ShareTeam first = startedTeam(manager, "앞선팀", ALICE);
		startedTeam(manager, "뒷팀", BOB);
		startedTeam(manager, "기억된팀", CAROL);

		assertSame(first, VictoryTeamResolver.resolve(manager,
				new Candidates(ALICE, BOB, CAROL)).team());
		assertEquals(Outcome.INDIRECT_OWNER, VictoryTeamResolver.resolve(manager,
				new Candidates(null, BOB, CAROL)).outcome());
	}

	// ------------------------------------------------------------------ 4단계

	/**
	 * 아무도 못 찾았을 때의 마지막 수단. 이 모드는 {@code singleTeamOnly} 가 기본값이라 사실상
	 * 언제나 팀이 하나이고, 그 팀이 아니면 드래곤을 잡을 사람이 없다.
	 */
	@Test
	void 아무도_못_찾으면_회차를_시작한_유일한_팀이_이긴다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = startedTeam(manager, "모험가들", ALICE);

		Resolution resolved = VictoryTeamResolver.resolve(manager, Candidates.NONE);

		assertSame(team, resolved.team());
		assertNull(resolved.killer(), "처치자를 찾은 것이 아니라 팀을 유일성으로 고른 것이다");
		assertEquals(Outcome.SOLE_STARTED_TEAM, resolved.outcome());
		assertTrue(resolved.victory());
	}

	/**
	 * <b>실제로 이렇게 잃었다.</b> 엔드에서 침대를 터뜨려 드래곤을 잡는 것은 가장 흔한
	 * 전술인데, 26.3 {@code BedBlock} 은
	 * {@code level.explode(null, damageSources().badRespawnPointExplosion(위치), null, …)}
	 * 로 터뜨린다 — <b>소스 엔티티도 직접 원인도 없다.</b> 그래서 후보 셋이 모두 비고,
	 * 그러면서도 {@code BAD_RESPAWN_POINT} 가 {@code #is_explosion} 에 들어 있어 드래곤에게
	 * 피해는 그대로 들어간다.
	 *
	 * <p>위의 {@code Candidates.NONE} 시험과 입력이 같지만 <b>일부러 따로 둔다.</b> 이 줄이
	 * 지키는 것은 「후보가 비었을 때의 동작」이 아니라 <b>「침대로 잡은 회차가 기록을 잃지
	 * 않는다」</b>는 약속이고, 4단계를 걷어내려는 사람이 가장 먼저 읽어야 할 이유가 그것이다.
	 */
	@Test
	void 침대로_잡아도_유일한_시작_팀이_기록을_받는다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = startedTeam(manager, "1시간반컷", ALICE);

		// 침대 폭발이 남기는 것 — 아무것도 없다.
		Resolution resolved = VictoryTeamResolver.resolve(manager, Candidates.NONE);

		assertSame(team, resolved.team(), "침대로 잡으면 4단계만이 승리 팀을 찾아낸다");
		assertTrue(resolved.victory());
	}

	/** <b>둘 이상이면 절대 찍지 않는다.</b> 남의 승리를 가로채는 것보다 「모험가」가 낫다. */
	@Test
	void 시작한_팀이_둘_이상이면_찍지_않는다() {
		TeamManager manager = new TeamManager();
		startedTeam(manager, "모험가들", ALICE);
		startedTeam(manager, "탐험대", BOB);

		Resolution resolved = VictoryTeamResolver.resolve(manager, Candidates.NONE);

		assertNull(resolved.team());
		assertEquals(Outcome.UNKNOWN, resolved.outcome());
		assertTrue(resolved.victory(), "팀을 못 찾은 것뿐이라 회차는 예전처럼 승리로 끝난다");
	}

	@Test
	void 시작한_팀이_하나도_없으면_찍지_않는다() {
		TeamManager empty = new TeamManager();
		TeamManager waitingOnly = new TeamManager();
		waitingTeam(waitingOnly, "아직안눌렀다", ALICE);

		assertEquals(Outcome.UNKNOWN,
				VictoryTeamResolver.resolve(empty, Candidates.NONE).outcome());
		assertEquals(Outcome.UNKNOWN,
				VictoryTeamResolver.resolve(waitingOnly, Candidates.NONE).outcome());
	}

	/** 시작한 팀이 하나면 대기 중인 팀이 곁에 있어도 그 하나를 고른다. */
	@Test
	void 대기_중인_팀은_유일성을_깨지_않는다() {
		TeamManager manager = new TeamManager();
		ShareTeam running = startedTeam(manager, "진행중", ALICE);
		waitingTeam(manager, "대기중", BOB);

		assertSame(running, VictoryTeamResolver.resolve(manager, Candidates.NONE).team());
	}

	@Test
	void 시작한_팀_세기() {
		TeamManager manager = new TeamManager();
		assertNull(VictoryTeamResolver.soleStartedTeam(null), "서버가 없으면 셀 것도 없다");
		assertNull(VictoryTeamResolver.soleStartedTeam(manager));

		ShareTeam only = startedTeam(manager, "하나뿐", ALICE);
		assertSame(only, VictoryTeamResolver.soleStartedTeam(manager));

		startedTeam(manager, "둘째", BOB);
		assertNull(VictoryTeamResolver.soleStartedTeam(manager));
	}

	// ------------------------------------------------------------------ 시작 전 거부

	/**
	 * 시작하지 않은 팀이 드래곤을 잡아도 회차 승리가 아니다. 예전 {@code onDeath} 가 하던
	 * 검사이고, 판정이 넓어진 지금도 그대로여야 한다 — 시작 전에는 회차 자체가 없고, 여기서
	 * 승리로 세면 아무도 시작하지 않은 회차가 끝나 버린다.
	 */
	@Test
	void 시작하지_않은_팀이_잡으면_승리가_아니다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = waitingTeam(manager, "아직안눌렀다", ALICE);

		Resolution resolved = VictoryTeamResolver.resolve(manager,
				new Candidates(ALICE, null, null));

		assertSame(team, resolved.team());
		assertEquals(Outcome.NOT_STARTED, resolved.outcome());
		assertFalse(resolved.victory());
	}

	/**
	 * <b>거부는 「건너뛰고 다음 단계」가 아니다.</b> 대기 중인 팀이 잡았는데 곁에 시작한 팀이
	 * 하나 있다고 해서 그 팀에게 승리를 넘기면, 남이 잡은 드래곤으로 이기는 길이 열린다.
	 */
	@Test
	void 시작하지_않은_팀이_잡으면_다른_팀에게_승리가_넘어가지_않는다() {
		TeamManager manager = new TeamManager();
		ShareTeam waiting = waitingTeam(manager, "아직안눌렀다", ALICE);
		startedTeam(manager, "진행중", BOB);

		Resolution resolved = VictoryTeamResolver.resolve(manager,
				new Candidates(ALICE, null, null));

		assertSame(waiting, resolved.team(), "잡은 팀은 그대로 잡은 팀이다");
		assertFalse(resolved.victory());
	}

	// ------------------------------------------------------------------ 팀 없는 처치자

	/**
	 * 팀에 속하지 않은 사람이 잡았을 때. 예전과 똑같이 <b>그 사람 이름으로</b> 승리한다.
	 *
	 * <p>4단계로 넘어가지 않는다. 처치자를 이미 알아냈으므로 찍을 일이 없고, 찍으면 운영자가
	 * 시험 삼아 잡은 드래곤이 남의 회차를 끝내 버린다.
	 */
	@Test
	void 팀_없는_사람이_잡으면_팀은_비고_처치자만_남는다() {
		TeamManager manager = new TeamManager();
		startedTeam(manager, "진행중", ALICE);

		Resolution resolved = VictoryTeamResolver.resolve(manager,
				new Candidates(STRANGER, null, null));

		assertNull(resolved.team(), "찾아낸 사람이 팀이 없으면 팀도 없다");
		assertEquals(STRANGER, resolved.killer());
		assertEquals(Outcome.CAUSING_PLAYER, resolved.outcome());
		assertTrue(resolved.victory());
	}

	// ------------------------------------------------------------------ 가장자리

	@Test
	void 팀_명부가_없어도_처치자는_그대로_돌려준다() {
		Resolution resolved = VictoryTeamResolver.resolve(null,
				new Candidates(ALICE, null, null));

		assertNull(resolved.team());
		assertEquals(ALICE, resolved.killer());
		assertEquals(Outcome.CAUSING_PLAYER, resolved.outcome());
	}

	@Test
	void 아무것도_없으면_아무것도_아니다() {
		Resolution resolved = VictoryTeamResolver.resolve(null, Candidates.NONE);

		assertNull(resolved.team());
		assertNull(resolved.killer());
		assertEquals(Outcome.UNKNOWN, resolved.outcome());
		assertTrue(resolved.victory(), "찾지 못한 것과 승리가 아닌 것은 다르다");
	}

	/** 로그에 적을 이름. 팀을 못 찾은 경우에도 줄이 깨지지 않아야 한다. */
	@Test
	void 로그에_적을_팀_이름() {
		TeamManager manager = new TeamManager();
		startedTeam(manager, "모험가들", ALICE);

		assertEquals("모험가들", VictoryTeamResolver.resolve(manager,
				new Candidates(ALICE, null, null)).teamName());
		assertEquals("?", VictoryTeamResolver.resolve(null, Candidates.NONE).teamName());
	}

	/** {@code TeamState} 가 없는 팀은 시작한 팀으로 세지 않는다 — 셀 근거가 없다. */
	@Test
	void 상태를_모르는_팀은_시작한_팀으로_세지_않는다() {
		assertFalse(VictoryTeamResolver.startedTeam(null));

		TeamState waiting = TeamState.fresh(20.0F);
		assertFalse(VictoryTeamResolver.startedTeam(waiting));

		TeamState started = TeamState.fresh(20.0F);
		started.runStarted = true;
		assertTrue(VictoryTeamResolver.startedTeam(started));
	}
}
