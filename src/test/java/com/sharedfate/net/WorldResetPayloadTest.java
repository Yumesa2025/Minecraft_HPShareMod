package com.sharedfate.net;

import com.sharedfate.TestBootstrap;
import com.sharedfate.ui.GameOverCountdown;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서버 종료 예고 묶음이 <b>왜 내려가는지</b>까지 싣는가.
 *
 * <p>여태 이 묶음에는 회차와 남은 틱만 있었다. 그래서 클라이언트는 카운트다운이 도는 이유를
 * 알 수 없었고, 살아 있는 사람의 화면에도 언제나 「게임 오버」를 그렸다 — 운영자가
 * {@code /shareteam reset} 을 쳤을 뿐인데 팀원 전원이 전멸한 줄 아는 사고다. 칸이 하나 늘어난
 * 것이 그 사고의 고침이므로, 이 시험은 <b>그 칸이 실제로 왕복하는지</b>를 붙든다.
 */
class WorldResetPayloadTest {
	/**
	 * 이 칸이 들어간 규약 번호.
	 *
	 * <p>칸이 늘면 옛 클라이언트는 이 묶음을 못 읽는다. 막을 수단이 악수뿐이라 번호를 함께
	 * 올려야 하고, 그 「함께」를 사람의 기억에 맡기지 않으려고 여기 적어 둔다.
	 */
	private static final int PROTOCOL_VERSION_WITH_REASON = 29;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static WorldResetPayload roundTrip(WorldResetPayload payload) {
		RegistryFriendlyByteBuf buffer =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		WorldResetPayload.CODEC.encode(buffer, payload);
		WorldResetPayload decoded = WorldResetPayload.CODEC.decode(buffer);
		assertEquals(0, buffer.readableBytes(), "읽고 남은 바이트가 있으면 칸이 어긋난 것이다");
		return decoded;
	}

	@Test
	void 전멸_예고가_그대로_왕복한다() {
		WorldResetPayload decoded = roundTrip(
				new WorldResetPayload(7, 100, GameOverCountdown.Reason.TEAM_WIPE));

		assertEquals(7, decoded.runNumber());
		assertEquals(100, decoded.delayTicks());
		assertEquals(GameOverCountdown.Reason.TEAM_WIPE, decoded.reason());
	}

	/**
	 * 초기화 예고도 <b>초기화인 채로</b> 도착해야 한다.
	 *
	 * <p>이 한 줄이 무너지면 화면은 다시 「게임 오버」로 돌아간다. 클라이언트에는 이 칸 말고
	 * 두 경로를 가를 근거가 없다 — 회차도 남은 틱도 양쪽이 똑같이 채운다.
	 */
	@Test
	void 초기화_예고도_초기화인_채로_도착한다() {
		WorldResetPayload decoded = roundTrip(
				new WorldResetPayload(7, 100, GameOverCountdown.Reason.RUN_RESET));

		assertEquals(GameOverCountdown.Reason.RUN_RESET, decoded.reason());
	}

	/** 경로가 몇 개가 되든 전부 자기 자신으로 돌아와야 한다. */
	@Test
	void 어떤_경로든_자기_자신으로_돌아온다() {
		for (GameOverCountdown.Reason reason : GameOverCountdown.Reason.values()) {
			assertEquals(reason, roundTrip(new WorldResetPayload(1, 20, reason)).reason(),
					reason.name());
		}
	}

	/** 선 위에 실리는 것은 상수 차례가 아니라 이름이다. */
	@Test
	void 선_위에는_경로_이름이_실린다() {
		assertEquals("run_reset",
				new WorldResetPayload(1, 20, GameOverCountdown.Reason.RUN_RESET).reasonId());
	}

	/**
	 * 빈 칸으로 만들어도 묶음이 터지지 않는다.
	 *
	 * <p>{@code null} 을 그대로 들고 있으면 {@link WorldResetPayload#reasonId} 가 인코딩 도중에
	 * 터진다. 보내는 쪽에서 터지면 <b>받는 사람 전원이 예고를 못 받고</b> 서버만 5초 뒤에
	 * 조용히 내려간다.
	 */
	@Test
	void 빈_경로는_전멸로_눕힌다() {
		assertEquals(GameOverCountdown.Reason.TEAM_WIPE,
				new WorldResetPayload(1, 20, null).reason());
	}

	/**
	 * <b>칸 수와 규약 번호는 함께 움직인다.</b>
	 *
	 * <p>이 묶음의 칸을 늘리거나 줄이면 옛 클라이언트는 읽지 못한다. 그런데 형식이 바뀐 것은
	 * 로그에도 화면에도 안 보이고, 번호를 안 올리면 <b>못 읽는 클라이언트가 그대로 접속한다.</b>
	 * 그래서 칸 수를 여기에 못박아 둔다 — 다음에 칸을 건드리는 사람은 이 시험이 깨지는 것으로
	 * 「{@code PROTOCOL_VERSION} 도 올려라」는 말을 듣게 된다.
	 */
	@Test
	void 칸을_건드렸으면_규약_번호도_올려야_한다() {
		assertEquals(3, WorldResetPayload.class.getRecordComponents().length,
				"칸 수가 바뀌었다. SharedFateNetworking.PROTOCOL_VERSION 도 함께 올리고"
						+ " 그 위 주석에 왜 올렸는지 적은 뒤 이 숫자를 고쳐라");
		assertTrue(SharedFateNetworking.PROTOCOL_VERSION >= PROTOCOL_VERSION_WITH_REASON,
				"이유 칸은 규약 " + PROTOCOL_VERSION_WITH_REASON + " 부터다. 지금 번호: "
						+ SharedFateNetworking.PROTOCOL_VERSION);
	}
}
