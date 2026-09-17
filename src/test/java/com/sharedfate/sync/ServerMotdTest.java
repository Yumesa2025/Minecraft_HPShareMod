package com.sharedfate.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 서버 목록 설명(MOTD)의 「증강 N개」를 실제 개수로 갈아 끼우는 규칙.
 *
 * <p>손으로 적어 둔 개수는 판을 올릴 때마다 낡는다 — 실제로 94개인데 「82개」가 몇 판째
 * 남아 있었다.
 *
 * <p>여기서 지키는 약속은 <b>남의 글을 지어내지 않는 것</b>이다. MOTD 는 서버를 여는 사람이
 * 쓰는 글이고, 모드는 <b>숫자 하나만</b> 고친다. 그 모양이 없으면 아무것도 하지 않는다.
 */
class ServerMotdTest {

	/** 두 줄에 색 코드가 섞인 MOTD 한 벌. 가장 손대기 까다로운 모양을 본떴다. */
	private static final String SAMPLE = "§6§lSharedFate §r§f운명을 나누는 하드코어\n"
			+ "§7체력·허기·경험치 공유 §8· §7증강 82개 §8· §7세트 효과";

	@Test
	void 개수만_바뀌고_나머지는_그대로다() {
		String updated = ServerMotd.withPerkCount(SAMPLE, 94);

		assertEquals("§6§lSharedFate §r§f운명을 나누는 하드코어\n"
				+ "§7체력·허기·경험치 공유 §8· §7증강 94개 §8· §7세트 효과", updated);
	}

	/** 색 코드·줄바꿈·「세트 효과」는 한 글자도 건드리지 않는다. */
	@Test
	void 색과_줄바꿈과_다른_문구는_건드리지_않는다() {
		String updated = ServerMotd.withPerkCount(SAMPLE, 94);

		assertEquals(SAMPLE.indexOf('\n'), updated.indexOf('\n'), "줄 구조가 바뀌면 안 된다");
		assertEquals(SAMPLE.replace("82", "94"), updated);
	}

	/**
	 * 「증강 N개」가 없는 MOTD 는 <b>남이 쓴 글</b>이다. 손대지 않는다.
	 *
	 * <p>여기서 문구를 통째로 만들어 버리면 이 모드를 받아 서버를 연 사람의 MOTD 가 사라진다.
	 */
	@Test
	void 그_모양이_없으면_아무것도_하지_않는다() {
		assertNull(ServerMotd.withPerkCount("어서 오세요", 94));
		assertNull(ServerMotd.withPerkCount("증강이 많습니다", 94));
		assertNull(ServerMotd.withPerkCount("증강 개", 94));
	}

	@Test
	void 이미_맞는_숫자면_같은_문자열이_나온다() {
		String already = SAMPLE.replace("82", "94");

		assertEquals(already, ServerMotd.withPerkCount(already, 94));
	}

	@Test
	void 빈_값이나_이상한_개수는_건드리지_않는다() {
		assertNull(ServerMotd.withPerkCount(null, 94));
		assertNull(ServerMotd.withPerkCount("", 94));
		assertNull(ServerMotd.withPerkCount(SAMPLE, 0), "증강을 못 읽었으면 0개라고 적지 않는다");
		assertNull(ServerMotd.withPerkCount(SAMPLE, -1));
	}

	/** 같은 모양이 두 번 나오면 둘 다 맞춘다. 한쪽만 고치면 더 헷갈린다. */
	@Test
	void 같은_모양이_여럿이면_전부_맞춘다() {
		assertEquals("증강 94개 · 증강 94개",
				ServerMotd.withPerkCount("증강 82개 · 증강 47개", 94));
	}

	/** 자리수가 달라져도 바뀐다. 줄어드는 쪽도 마찬가지다. */
	@Test
	void 세_자리_개수도_맞춘다() {
		assertEquals("증강 160개", ServerMotd.withPerkCount("증강 94개", 160));
		assertEquals("증강 94개", ServerMotd.withPerkCount("증강 160개", 94));
	}
}
