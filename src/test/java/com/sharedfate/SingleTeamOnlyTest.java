package com.sharedfate;

import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서버에 팀을 하나만 두는 제한(singleTeamOnly) 검증. */
class SingleTeamOnlyTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
	private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

	private TeamManager manager;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		manager = new TeamManager();
	}

	@Test
	void 팀이_없으면_새_팀을_만들_수_있다() {
		assertFalse(manager.hasAnyTeam());
		assertTrue(manager.canCreateNewTeam(true));
	}

	@Test
	void 팀이_하나_있으면_새_팀_생성을_막는다() {
		assertNotNull(manager.createTeam("첫팀", A, 40.0F));

		assertTrue(manager.hasAnyTeam());
		assertFalse(manager.canCreateNewTeam(true));
	}

	@Test
	void 제한을_끄면_팀이_있어도_새_팀을_만들_수_있다() {
		manager.createTeam("첫팀", A, 40.0F);

		assertTrue(manager.canCreateNewTeam(false));
		assertNotNull(manager.createTeam("둘째팀", B, 40.0F));
		assertEquals(2, manager.allTeams().size());
	}

	@Test
	void 팀을_해체하면_다시_만들_수_있다() {
		ShareTeam team = manager.createTeam("첫팀", A, 40.0F);
		assertFalse(manager.canCreateNewTeam(true));

		manager.disband(team.teamId());

		assertFalse(manager.hasAnyTeam());
		assertTrue(manager.canCreateNewTeam(true));
		assertNotNull(manager.createTeam("새팀", A, 40.0F));
	}

	@Test
	void 마지막_멤버가_나가_팀이_사라지면_다시_만들_수_있다() {
		ShareTeam team = manager.createTeam("첫팀", A, 40.0F);
		manager.addMember(team.teamId(), B, 4);

		manager.removeMember(A);
		assertFalse(manager.canCreateNewTeam(true), "아직 B가 남아 있으면 여전히 막혀야 한다");

		manager.removeMember(B);
		assertTrue(manager.canCreateNewTeam(true));
	}

	@Test
	void 이미_여러_팀이_있는_서버의_기존_팀은_그대로_둔다() {
		// 제한이 없던 시절에 만들어진 상태를 흉내낸다.
		manager.createTeam("첫팀", A, 40.0F);
		manager.createTeam("둘째팀", B, 40.0F);

		// 새로 만드는 것만 막히고, 기존 팀과 소속은 그대로 남는다.
		assertFalse(manager.canCreateNewTeam(true));
		assertEquals(2, manager.allTeams().size());
		assertNotNull(manager.teamOf(A));
		assertNotNull(manager.teamOf(B));
	}

	@Test
	void 회차_리셋_복원은_팀_하나_제한에_막히지_않는다() {
		TeamManager source = new TeamManager();
		ShareTeam first = source.createTeam("첫팀", A, 40.0F);
		source.addMember(first.teamId(), B, 4);
		source.createTeam("둘째팀", C, 40.0F);

		// 새 월드의 빈 TeamManager 로 명단을 되살린다. createTeam 을 거치지 않는 경로다.
		TeamManager fresh = new TeamManager();
		int restored = fresh.restoreFreshRoster(source.allTeams(), 40.0F);

		assertEquals(2, restored);
		assertEquals(2, fresh.allTeams().size());
		assertNotNull(fresh.teamOf(A));
		assertNotNull(fresh.teamOf(B));
		assertNotNull(fresh.teamOf(C));
	}

	@Test
	void 복원된_뒤에는_새_팀_생성이_막힌다() {
		TeamManager source = new TeamManager();
		source.createTeam("첫팀", A, 40.0F);

		TeamManager fresh = new TeamManager();
		fresh.restoreFreshRoster(List.copyOf(source.allTeams()), 40.0F);

		assertFalse(fresh.canCreateNewTeam(true));
	}
}
