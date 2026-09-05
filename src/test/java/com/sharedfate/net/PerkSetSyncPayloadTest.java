package com.sharedfate.net;

import com.sharedfate.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 세트 패킷이 그대로 왕복하는지, 그리고 <b>상한을 넘는 목록이 접속을 끊지 않는지</b> 본다.
 *
 * <p>{@code ByteBufCodecs.list(max)} 는 상한을 넘으면 예외를 던지는데, 그 예외는 패킷 한 장이
 * 아니라 접속 자체를 끊는다. 유형이 늘거나 증강 풀이 커졌을 때 <b>화면이 한 줄 덜 뜨는 것</b>과
 * <b>모두가 튕기는 것</b>은 전혀 다른 일이다.
 *
 * <p>"달라졌을 때만 다시 보낸다"의 근거인 {@code equals} 도 함께 본다. 같은 내용인데 다르게
 * 나오면 패킷이 쉬지 않고 나가고, 다른 내용인데 같게 나오면 세트가 켜져도 화면이 그대로다.
 */
class PerkSetSyncPayloadTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static PerkSetSyncPayload roundTrip(PerkSetSyncPayload payload) {
		RegistryFriendlyByteBuf buffer =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		PerkSetSyncPayload.CODEC.encode(buffer, payload);
		return PerkSetSyncPayload.CODEC.decode(buffer);
	}

	private static PerkSetSyncPayload sample() {
		return new PerkSetSyncPayload(
				List.of(new PerkSetSyncPayload.SetLine("mining", "채굴", 3, 4, 3),
						new PerkSetSyncPayload.SetLine("defense", "방어", 1, 2, 0)),
				List.of(new PerkSetSyncPayload.CatalogEntry("mining", "굴착기", "gold", true),
						new PerkSetSyncPayload.CatalogEntry("mining", "종결곡", "prism", false)));
	}

	@Test
	void 세트_줄과_이름표가_직렬화를_그대로_통과한다() {
		PerkSetSyncPayload decoded = roundTrip(sample());

		assertEquals(2, decoded.sets().size());
		PerkSetSyncPayload.SetLine mining = decoded.sets().getFirst();
		assertEquals("mining", mining.typeId());
		assertEquals("채굴", mining.displayName());
		assertEquals(3, mining.owned());
		assertEquals(4, mining.nextThreshold());
		assertEquals(3, mining.activeTier());
		assertTrue(mining.active());
		assertFalse(decoded.sets().get(1).active());

		assertEquals(2, decoded.catalog().size());
		assertTrue(decoded.catalog().getFirst().owned());
		assertEquals("종결곡", decoded.catalog().get(1).perkName());
		assertEquals("prism", decoded.catalog().get(1).rarity());
		assertFalse(decoded.catalog().get(1).owned());
	}

	@Test
	void 빈_상태도_왕복한다() {
		assertEquals(PerkSetSyncPayload.EMPTY, roundTrip(PerkSetSyncPayload.EMPTY));
		assertTrue(PerkSetSyncPayload.EMPTY.sets().isEmpty());
		assertTrue(PerkSetSyncPayload.EMPTY.catalog().isEmpty());
	}

	// ------------------------------------------------------------------ 상한

	@Test
	void 상한을_넘는_세트_줄은_잘려서_담긴다() {
		List<PerkSetSyncPayload.SetLine> tooMany = new ArrayList<>();
		for (int index = 0; index < PerkSetSyncPayload.MAX_SETS + 5; index++) {
			tooMany.add(new PerkSetSyncPayload.SetLine("type" + index, "유형", 1, 2, 0));
		}

		PerkSetSyncPayload payload = new PerkSetSyncPayload(tooMany, List.of());

		assertEquals(PerkSetSyncPayload.MAX_SETS, payload.sets().size());
		// 잘린 뒤에는 코덱도 아무 일 없이 지나간다. 여기서 터지면 접속이 끊긴다.
		assertEquals(PerkSetSyncPayload.MAX_SETS, roundTrip(payload).sets().size());
	}

	@Test
	void 상한을_넘는_이름표는_뒤에서부터_버린다() {
		List<PerkSetSyncPayload.CatalogEntry> tooMany = new ArrayList<>();
		for (int index = 0; index < PerkSetSyncPayload.MAX_CATALOG + 40; index++) {
			tooMany.add(new PerkSetSyncPayload.CatalogEntry("mining", "증강" + index, "silver",
					false));
		}

		PerkSetSyncPayload payload = new PerkSetSyncPayload(List.of(), tooMany);

		assertEquals(PerkSetSyncPayload.MAX_CATALOG, payload.catalog().size());
		// 앞에서부터 남는다 — 그래서 서버는 유형별로 모아 실어야 한다.
		assertEquals("증강0", payload.catalog().getFirst().perkName());
		assertEquals(PerkSetSyncPayload.MAX_CATALOG, roundTrip(payload).catalog().size());
	}

	@Test
	void 상한과_꼭_같은_길이는_자르지_않는다() {
		List<PerkSetSyncPayload.SetLine> exact = new ArrayList<>();
		for (int index = 0; index < PerkSetSyncPayload.MAX_SETS; index++) {
			exact.add(new PerkSetSyncPayload.SetLine("type" + index, "유형", 1, 2, 0));
		}

		assertEquals(PerkSetSyncPayload.MAX_SETS,
				roundTrip(new PerkSetSyncPayload(exact, List.of())).sets().size());
	}

	// ------------------------------------------------------------------ 어긋난 값

	@Test
	void 음수가_와도_패킷이_터지지_않는다() {
		// VAR_INT 는 음수를 싣지 못한다. 판정이 어긋나도 접속이 끊기면 안 된다.
		PerkSetSyncPayload.SetLine line =
				new PerkSetSyncPayload.SetLine("mining", "채굴", -1, -3, -2);

		assertEquals(0, line.owned());
		assertEquals(0, line.nextThreshold());
		assertEquals(0, line.activeTier());
		assertFalse(line.active());
	}

	@Test
	void null_문자열은_빈_문자열이_된다() {
		// 서버 쪽 null 하나로 인코딩이 터지면 아무도 접속하지 못한다.
		PerkSetSyncPayload.SetLine line = new PerkSetSyncPayload.SetLine(null, null, 1, 2, 0);
		PerkSetSyncPayload.CatalogEntry entry =
				new PerkSetSyncPayload.CatalogEntry(null, null, null, false);

		assertEquals("", line.typeId());
		assertEquals("", line.displayName());
		assertEquals("", entry.typeId());
		assertEquals("", entry.perkName());
		assertEquals("", entry.rarity());
	}

	@Test
	void null_목록은_빈_목록이_된다() {
		PerkSetSyncPayload payload = new PerkSetSyncPayload(null, null);

		assertTrue(payload.sets().isEmpty());
		assertTrue(payload.catalog().isEmpty());
	}

	// ------------------------------------------------------------------ 다시 보낼지 판단

	@Test
	void 같은_내용이면_같다고_나온다() {
		assertEquals(sample(), sample());
	}

	@Test
	void 진행도만_올라도_다르다고_나온다() {
		// 「채굴 2/3」이 「채굴 3/3」이 되는 것은 줄 수가 그대로다. 여기서 같다고 나오면
		// 세트가 켜져도 화면이 영영 안 바뀐다.
		PerkSetSyncPayload before = new PerkSetSyncPayload(
				List.of(new PerkSetSyncPayload.SetLine("mining", "채굴", 2, 3, 0)), List.of());
		PerkSetSyncPayload after = new PerkSetSyncPayload(
				List.of(new PerkSetSyncPayload.SetLine("mining", "채굴", 3, 4, 3)), List.of());

		assertNotEquals(before, after);
	}

	@Test
	void 이름표의_보유_여부가_바뀌어도_다르다고_나온다() {
		// 툴팁에서 「아직 없는 것」이 사라져야 하는 순간이다.
		PerkSetSyncPayload before = new PerkSetSyncPayload(List.of(),
				List.of(new PerkSetSyncPayload.CatalogEntry("mining", "굴착기", "gold", false)));
		PerkSetSyncPayload after = new PerkSetSyncPayload(List.of(),
				List.of(new PerkSetSyncPayload.CatalogEntry("mining", "굴착기", "gold", true)));

		assertNotEquals(before, after);
	}
}
