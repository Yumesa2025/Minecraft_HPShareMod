package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /shareteam version} 이 보여 주는 줄들을 못박는다.
 *
 * <p>명령 자체는 {@code ServerPlayer} 와 {@code CommandSourceStack} 없이는 한 줄도 돌릴 수
 * 없어 시험이 닿지 않는다. 그래서 「무엇을 어떻게 보여 주느냐」만 {@link VersionLines} 로
 * 떼어 냈고, 여기서는 그 판단을 본다.
 */
class VersionLinesTest {
	private static final String SERVER = "0.26.2-dev";
	private static final int PROTOCOL = 26;

	@Test
	void 판이_같으면_같다고_말한다() {
		List<String> lines = VersionLines.self(SERVER, PROTOCOL, "0.26.2-dev");

		assertTrue(lines.get(0).contains("26"), "규약 번호가 첫 줄에 있다");
		assertTrue(lines.stream().anyMatch(line -> line.contains("서버: 0.26.2-dev")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("내 클라이언트: 0.26.2-dev")));
		assertTrue(lines.stream().anyMatch(line -> line.contains(VersionLines.MATCH)));
	}

	@Test
	void 판이_다르면_무엇을_해야_하는지까지_말한다() {
		List<String> lines = VersionLines.self(SERVER, PROTOCOL, "0.25.0-dev");

		assertTrue(lines.stream().anyMatch(line -> line.contains(VersionLines.MISMATCH)),
				"다르다는 말만 하고 끝내면 무엇을 해야 하는지 모른다");
	}

	/**
	 * 판을 모를 때 「같다」고 답하면 안 된다.
	 *
	 * <p>이 명령을 만든 이유가 낡은 클라이언트를 쓰는 사람을 찾는 것인데, 모르는 것을 같다고
	 * 답하면 정작 그 사람에게 「문제 없음」이라고 말하게 된다.
	 */
	@Test
	void 판을_모르면_같다고_하지_않는다() {
		assertFalse(VersionLines.matches(null, SERVER));
		assertFalse(VersionLines.matches("", SERVER));
		assertFalse(VersionLines.matches("   ", SERVER));
		assertTrue(VersionLines.matches(SERVER, SERVER));

		List<String> lines = VersionLines.self(SERVER, PROTOCOL, null);
		assertTrue(lines.stream().anyMatch(line -> line.contains(VersionLines.UNKNOWN_CLIENT)));
		assertTrue(lines.stream().noneMatch(line -> line.contains(VersionLines.MATCH)));
	}

	/** 판이 어긋난 사람을 <b>먼저</b> 적는다 — 목록이 길어도 찾을 것이 맨 위에 있어야 한다. */
	@Test
	void 어긋난_사람을_먼저_적는다() {
		List<String> lines = VersionLines.all(SERVER, PROTOCOL, List.of(
				new VersionLines.Entry("맞는사람", SERVER),
				new VersionLines.Entry("낡은사람", "0.25.0-dev"),
				new VersionLines.Entry("또맞는사람", SERVER)));

		int mismatchIndex = indexOfContaining(lines, "낡은사람");
		int matchIndex = indexOfContaining(lines, "맞는사람");
		assertTrue(mismatchIndex < matchIndex, "어긋난 사람이 위에 있어야 한다");
		assertTrue(lines.stream().anyMatch(line -> line.contains("1명이 다른 판")));
	}

	@Test
	void 전원이_같으면_그렇게_말한다() {
		List<String> lines = VersionLines.all(SERVER, PROTOCOL, List.of(
				new VersionLines.Entry("갑", SERVER),
				new VersionLines.Entry("을", SERVER)));

		assertTrue(lines.stream().anyMatch(line -> line.contains("전원이 같은 판")));
	}

	/** 판을 안 알린 사람도 「다름」 쪽에 들어가야 한다. 그 사람이 바로 찾으려던 사람이다. */
	@Test
	void 판을_안_알린_사람도_어긋난_쪽이다() {
		List<String> lines = VersionLines.all(SERVER, PROTOCOL, List.of(
				new VersionLines.Entry("맞는사람", SERVER),
				new VersionLines.Entry("조용한사람", null)));

		assertTrue(indexOfContaining(lines, "조용한사람") < indexOfContaining(lines, "맞는사람"));
		assertTrue(lines.stream().anyMatch(line ->
				line.contains("조용한사람") && line.contains("(알 수 없음)")));
		assertTrue(lines.stream().anyMatch(line -> line.contains("1명이 다른 판")));
	}

	@Test
	void 아무도_없으면_빈_목록이라고_말한다() {
		List<String> lines = VersionLines.all(SERVER, PROTOCOL, List.of());

		assertEquals(2, lines.size());
		assertTrue(lines.get(1).contains("접속 중인 사람이 없습니다"));
	}

	private static int indexOfContaining(List<String> lines, String needle) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).contains(needle)) {
				return i;
			}
		}
		return Integer.MAX_VALUE;
	}
}
