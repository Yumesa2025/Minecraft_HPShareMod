package com.sharedfate.net;

import com.sharedfate.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 강제 오픈에 필요한 페이로드 필드가 그대로 왕복하는지 본다.
 *
 * <p>{@code forced} 와 {@code remainingTicks} 가 어긋나면 클라이언트가 ESC 를 막지 못하거나
 * 카운트다운이 엉뚱하게 나오므로 직렬화까지 확인한다.
 */
class PerkOfferPayloadTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static PerkOfferPayload roundTrip(PerkOfferPayload payload) {
		RegistryFriendlyByteBuf buffer =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		PerkOfferPayload.CODEC.encode(buffer, payload);
		return PerkOfferPayload.CODEC.decode(buffer);
	}

	private static List<PerkOfferPayload.PerkOption> sampleOptions() {
		return List.of(
				new PerkOfferPayload.PerkOption("a", "강골", "최대 체력 +2", "silver",
						"minecraft:iron_ingot"),
				new PerkOfferPayload.PerkOption("b", "날렵", "이동 속도 +10%", "gold", ""));
	}

	@Test
	void 직접_연_창은_강제도_아니고_마감도_없다() {
		PerkOfferPayload payload = PerkOfferPayload.manual(15, true, sampleOptions());

		assertFalse(payload.forced());
		assertEquals(PerkOfferPayload.NO_DEADLINE, payload.remainingTicks());
		assertFalse(payload.hasDeadline());
	}

	@Test
	void 강제_오픈은_마감_틱을_함께_싣는다() {
		PerkOfferPayload payload =
				new PerkOfferPayload(20, false, true, 1200, 3, sampleOptions());

		assertTrue(payload.forced());
		assertTrue(payload.hasDeadline());
		assertEquals(1200, payload.remainingTicks());
	}

	@Test
	void 강제_여부와_남은_틱이_직렬화를_그대로_통과한다() {
		PerkOfferPayload decoded =
				roundTrip(new PerkOfferPayload(25, true, true, 640, 2, sampleOptions()));

		assertEquals(25, decoded.milestone());
		assertTrue(decoded.canChoose());
		assertTrue(decoded.forced());
		assertEquals(640, decoded.remainingTicks());
		assertEquals(2, decoded.options().size());
		assertEquals("강골", decoded.options().getFirst().name());
	}

	// ------------------------------------------------------------------ 다시 뽑기 남은 횟수

	@Test
	void 남은_다시_뽑기_횟수도_직렬화를_그대로_통과한다() {
		// 이 값이 어긋나면 단추에 엉뚱한 숫자가 뜨거나, 남아 있는데도 잠긴 채로 보인다.
		assertEquals(3, roundTrip(new PerkOfferPayload(5, true, true, 1200, 3, sampleOptions()))
				.rerollsRemaining());
		assertEquals(0, roundTrip(new PerkOfferPayload(5, true, true, 1200, 0, sampleOptions()))
				.rerollsRemaining());
	}

	@Test
	void 직접_연_창은_다시_뽑기_횟수가_0이다() {
		// 강제 선택 세션 밖에서는 서버가 요청을 버리므로 단추가 뜨면 안 된다.
		assertEquals(0, PerkOfferPayload.manual(15, true, sampleOptions()).rerollsRemaining());
	}

	@Test
	void 음수가_와도_패킷이_터지지_않는다() {
		// VAR_INT 는 음수를 싣지 못한다. 서버 계산이 어긋나도 선택창 자체가 안 열리면 안 된다.
		assertEquals(0, new PerkOfferPayload(5, true, true, 1200, -1, sampleOptions())
				.rerollsRemaining());
	}

	@Test
	void 마감이_없다는_뜻의_음수도_그대로_전달된다() {
		// VAR_INT 로 실으면 -1 이 5바이트로 부풀거나 부호가 뭉개진다. INT 로 싣는 이유다.
		PerkOfferPayload decoded = roundTrip(PerkOfferPayload.manual(5, false, List.of()));

		assertFalse(decoded.forced());
		assertEquals(PerkOfferPayload.NO_DEADLINE, decoded.remainingTicks());
		assertFalse(decoded.hasDeadline());
	}

	@Test
	void 카드_아이콘도_직렬화를_그대로_통과한다() {
		PerkOfferPayload decoded =
				roundTrip(new PerkOfferPayload(15, true, true, 400, 1, sampleOptions()));

		assertEquals("minecraft:iron_ingot", decoded.options().getFirst().icon());
		// 아이콘을 정하지 않은 후보는 빈 문자열로 오고, 화면이 등급별 기본 아이콘을 채운다.
		assertEquals("", decoded.options().get(1).icon());
	}

	@Test
	void 아이콘이_null_이면_빈_문자열로_바뀐다() {
		// 서버 쪽 null 하나로 패킷 인코딩이 터지면 선택창 자체가 안 열린다.
		PerkOfferPayload.PerkOption option =
				new PerkOfferPayload.PerkOption("a", "강골", "최대 체력 +2", "silver", null);

		assertEquals("", option.icon());
		assertEquals("", roundTrip(PerkOfferPayload.manual(5, true, List.of(option)))
				.options().getFirst().icon());
	}

	@Test
	void 닫기_지시는_같은_구간의_창만_닫는다() {
		PerkCloseOfferPayload close = new PerkCloseOfferPayload(15);

		assertTrue(close.matches(15));
		assertFalse(close.matches(20));
	}

	@Test
	void 구간을_가리지_않는_닫기_지시도_있다() {
		assertTrue(PerkCloseOfferPayload.ALL.matches(5));
		assertTrue(PerkCloseOfferPayload.ALL.matches(35));
	}
}
