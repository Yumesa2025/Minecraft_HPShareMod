package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

public record TeamSyncPayload(List<Member> members) implements CustomPacketPayload {
	public record Member(UUID id, String name, int selectedSlot) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Member> CODEC =
				StreamCodec.composite(
						UUIDUtil.STREAM_CODEC, Member::id,
						ByteBufCodecs.STRING_UTF8, Member::name,
						ByteBufCodecs.VAR_INT, Member::selectedSlot,
						Member::new);
	}

	public static final Type<TeamSyncPayload> TYPE = new Type<>(SharedFateMod.id("team_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TeamSyncPayload> CODEC =
			Member.CODEC.apply(ByteBufCodecs.list(16))
					.map(TeamSyncPayload::new, TeamSyncPayload::members);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
