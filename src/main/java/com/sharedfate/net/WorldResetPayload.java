package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.ui.GameOverCountdown;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 서버가 곧 내려간다는 예고. 남은 틱과 <b>왜 내려가는지</b>를 함께 보낸다.
 *
 * <p>이유를 싣기 전에는 클라이언트가 카운트다운이 도는 까닭을 알 수 없어 살아 있는 사람의
 * 화면에도 언제나 「게임 오버」를 그렸다. 운영자가 {@code /shareteam reset} 을 쳤을 뿐인데
 * 팀원 전원이 <b>전멸한 줄 아는</b> 사고다. 회차도 남은 틱도 두 경로가 똑같이 채우므로,
 * 화면이 글자를 가를 근거는 이 칸밖에 없다.
 */
public record WorldResetPayload(int runNumber, int delayTicks, GameOverCountdown.Reason reason)
		implements CustomPacketPayload {
	public static final Type<WorldResetPayload> TYPE = new Type<>(SharedFateMod.id("world_reset"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldResetPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, WorldResetPayload::runNumber,
					ByteBufCodecs.VAR_INT, WorldResetPayload::delayTicks,
					ByteBufCodecs.STRING_UTF8, WorldResetPayload::reasonId,
					WorldResetPayload::decoded);

	/**
	 * 빈 이유는 전멸로 눕힌다.
	 *
	 * <p>{@code null} 을 그대로 들고 있으면 {@link #reasonId} 가 <b>보내는 쪽에서</b> 터진다.
	 * 그러면 받는 사람 전원이 예고를 못 받고 서버만 5초 뒤에 조용히 내려간다 — 화면에 아무
	 * 설명도 없이 접속이 끊기는 것이 가장 나쁜 결과다.
	 */
	public WorldResetPayload {
		reason = reason == null ? GameOverCountdown.Reason.TEAM_WIPE : reason;
	}

	/** 선 위에 실리는 이유의 이름. 상수 차례가 아니라 이름을 싣는 까닭은 열거형 쪽에 적어 뒀다. */
	public String reasonId() {
		return reason.id();
	}

	private static WorldResetPayload decoded(int runNumber, int delayTicks, String reasonId) {
		return new WorldResetPayload(
				runNumber, delayTicks, GameOverCountdown.Reason.fromId(reasonId));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
