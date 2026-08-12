package com.sharedfate;

import com.sharedfate.team.ShareTeam;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareTeamTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	@Test
	void 생성하면_리더가_유일한_멤버다() {
		ShareTeam team = ShareTeam.create("우리팀", A);

		assertEquals("우리팀", team.name());
		assertEquals(A, team.leader());
		assertEquals(List.of(A), team.members());
	}

	@Test
	void 멤버를_추가하면_순서가_유지된다() {
		ShareTeam team = ShareTeam.create("우리팀", A).withMemberAdded(B).withMemberAdded(C);

		assertEquals(List.of(A, B, C), team.members());
	}

	@Test
	void 같은_멤버를_두_번_추가하면_기존_객체를_유지한다() {
		ShareTeam team = ShareTeam.create("우리팀", A).withMemberAdded(B);

		assertSame(team, team.withMemberAdded(B));
		assertEquals(List.of(A, B), team.members());
	}

	@Test
	void 리더가_나가면_다음_가입자가_리더가_된다() {
		ShareTeam team = ShareTeam.create("우리팀", A).withMemberAdded(B).withMemberAdded(C);

		ShareTeam after = team.withMemberRemoved(A);

		assertEquals(B, after.leader());
		assertEquals(List.of(B, C), after.members());
	}

	@Test
	void 마지막_멤버가_나가면_빈_팀이_된다() {
		ShareTeam after = ShareTeam.create("우리팀", A).withMemberRemoved(A);

		assertTrue(after.members().isEmpty());
		assertTrue(after.isEmpty());
	}

	@Test
	void 리더가_아닌_멤버가_나가도_리더는_그대로다() {
		ShareTeam team = ShareTeam.create("우리팀", A).withMemberAdded(B);

		ShareTeam after = team.withMemberRemoved(B);

		assertEquals(A, after.leader());
		assertEquals(List.of(A), after.members());
	}

	@Test
	void 외부_멤버_리스트를_변경해도_팀은_바뀌지_않는다() {
		List<UUID> source = new ArrayList<>(List.of(A));
		ShareTeam team = new ShareTeam(UUID.randomUUID(), "우리팀", source);

		source.add(B);

		assertEquals(List.of(A), team.members());
		assertThrows(UnsupportedOperationException.class, () -> team.members().add(C));
	}
}
