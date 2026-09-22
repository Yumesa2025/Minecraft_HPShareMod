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
 * <p>여기서 지키는 약속은 <b>남의 글을 덮어쓰지 않는 것</b>이다. 사람이 쓴 글에서는
 * <b>숫자 하나만</b> 고치고, 그 모양이 없으면 아무것도 하지 않는다.
 *
 * <p>0.29.1-dev 에 갈래가 하나 늘었다 — <b>아무도 안 쓴 서버는 채운다.</b> 그전에는 문구를
 * 적어 둔 서버에서만 동작해서, 모드를 받아 연 서버 목록에는 「A Minecraft Server」가 그대로
 * 떴다. 둘이 어긋나지 않는지가 이 파일의 요점이다 — <b>채운 글도 다음 판에 스스로 따라가야</b>
 * 한다.
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
	void 증강을_못_읽었으면_아무것도_안_한다() {
		assertNull(ServerMotd.withPerkCount(SAMPLE, 0), "증강을 못 읽었으면 0개라고 적지 않는다");
		assertNull(ServerMotd.withPerkCount(SAMPLE, -1));
		assertNull(ServerMotd.withPerkCount(null, 0), "채울 때도 마찬가지다");
	}

	// ------------------------------------------------- 아무도 안 쓴 서버는 채운다

	/**
	 * <b>받아서 자기 서버를 여는 사람이 아무것도 안 해도 뜬다.</b> 0.29.1-dev 에 생긴 갈래다.
	 *
	 * <p>그전에는 문구를 적어 둔 서버에서만 동작해서, 받아서 연 서버 목록에는
	 * 「A Minecraft Server」가 그대로 떴다.
	 */
	@Test
	void 아무도_안_쓴_서버에는_문구를_넣는다() {
		String expected = "SharedFate · 체력·허기·인벤 공유 · 증강 94개";

		assertEquals(expected, ServerMotd.withPerkCount(null, 94), "설정이 아예 없는 서버");
		assertEquals(expected, ServerMotd.withPerkCount("", 94), "비워 둔 서버");
		assertEquals(expected, ServerMotd.withPerkCount("   ", 94), "공백만 남은 서버");
		assertEquals(expected, ServerMotd.withPerkCount(ServerMotd.VANILLA_DEFAULT, 94),
				"바닐라가 새 서버에 적어 두는 그 문구");
		assertEquals(expected, ServerMotd.withPerkCount("  A Minecraft Server  ", 94),
				"앞뒤 공백이 붙어 있어도 알아본다");
	}

	/**
	 * 모드가 넣은 문구도 <b>다음 판에 스스로 따라간다.</b>
	 *
	 * <p>끝을 「증강 N개」로 맞춰 둔 것이 이것 때문이다. 다른 모양으로 적으면 한 번 채운 뒤로는
	 * 영영 낡은 숫자가 남는다 — 이 시험이 그것을 막는다.
	 */
	@Test
	void 넣은_문구는_다음_판에_스스로_따라간다() {
		String first = ServerMotd.withPerkCount(null, 94);

		assertEquals("SharedFate · 체력·허기·인벤 공유 · 증강 100개",
				ServerMotd.withPerkCount(first, 100));
	}

	/** 사람이 제 문구를 써 두었으면 <b>채우지 않는다.</b> 그 글이 사라지면 안 된다. */
	@Test
	void 사람이_쓴_글이_있으면_채우지_않는다() {
		assertNull(ServerMotd.withPerkCount("어서 오세요", 94));
		assertNull(ServerMotd.withPerkCount("A Minecraft Server 입니다", 94),
				"기본값을 품고 있을 뿐 사람이 쓴 글이다");
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
