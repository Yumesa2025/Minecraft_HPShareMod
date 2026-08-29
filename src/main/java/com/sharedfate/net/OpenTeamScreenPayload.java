package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — {@code /shareteam} 를 인자 없이 쳤을 때 팀 화면을 열라는 신호.
 *
 * <p>담을 내용이 없다. 화면이 그릴 값은 이미 {@link TeamSyncPayload} 와
 * {@link PerkSyncPayload} 로 계속 와 있으므로, 여기서는 "열어라"만 보낸다.
 *
 * <p>모드가 없는 클라이언트에게는 보내지 않는다. 그쪽에는 서버가 예전처럼 도움말을
 * 글로 찍어 준다.
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
