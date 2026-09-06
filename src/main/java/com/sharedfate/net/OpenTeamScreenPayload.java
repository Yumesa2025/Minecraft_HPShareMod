package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — {@code /shareteam} 를 인자 없이 쳤을 때 팀 화면을 열라는 신호.
 *
 * <p>모드가 없는 클라이언트에게는 보내지 않는다.
 */
public record OpenTeamScreenPayload() implements CustomPacketPayload {
	public static final OpenTeamScreenPayload INSTANCE = new OpenTeamScreenPayload();

	public static final Type<OpenTeamScreenPayload> TYPE =
			new Type<>(SharedFateMod.id("open_team_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenTeamScreenPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
