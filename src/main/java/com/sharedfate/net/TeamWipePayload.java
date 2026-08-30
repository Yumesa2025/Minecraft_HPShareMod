package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — 전멸을 부른 사람의 이름.
 *
 * <p>사망 알림을 켠 팀에서만 나간다. 받은 클라이언트는 게임 오버 화면 부제에 한 줄로 적는다.
 *
 * <p>{@link WorldResetPayload} 에 얹지 않은 이유가 있다. 그 묶음은 {@code
 * resetWorldOnTeamDeath} 가 켜져 있을 때만 나가는데 확인용 테스트 서버는 그 값이 꺼져
 * 있다. 얹으면 <b>정작 확인해야 할 곳에서 확인할 수 없다.</b>
 *
 * @param victimName 팀에서 먼저 죽어 전멸을 부른 사람의 이름
 */
public record TeamWipePayload(String victimName) implements CustomPacketPayload {
	public static final Type<TeamWipePayload> TYPE = new Type<>(SharedFateMod.id("team_wipe"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TeamWipePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, TeamWipePayload::victimName,
					TeamWipePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
