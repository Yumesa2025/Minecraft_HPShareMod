package com.sharedfate.sync;

import com.sharedfate.team.ShareTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageLedgerTest {
	private static final UUID TEAM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID FIRST = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SECOND = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@AfterEach
	void reset() {
		DamageLedger.clearState();
	}

	@Test
	void recordsEachRunAndTotalPerPlayer() {
		ShareTeam team = new ShareTeam(TEAM_ID, "기록팀", List.of(FIRST, SECOND));
		DamageLedger.record(TEAM_ID, team.name(), FIRST, "첫째", 1, 3.5D);
		DamageLedger.record(TEAM_ID, team.name(), FIRST, "첫째", 1, 0.5D);
		DamageLedger.record(TEAM_ID, team.name(), FIRST, "첫째", 3, 6.0D);

		List<String> pages = DamageLedger.buildPageTexts(team, 3);
		String book = String.join("\n---\n", pages);

		assertTrue(book.contains("1회차: 4.0 / 2.0♥"));
		assertTrue(book.contains("2회차: 0.0 / 0.0♥"));
		assertTrue(book.contains("3회차: 6.0 / 3.0♥"));
		assertTrue(book.contains("총합: 10.0 / 5.0♥"));
		assertTrue(book.contains(SECOND.toString().substring(0, 8)));
	}
}
