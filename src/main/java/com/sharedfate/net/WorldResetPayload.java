package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record WorldResetPayload(int runNumber, int delayTicks) implements CustomPacketPayload {
	public static final Type<WorldResetPayload> TYPE = new Type<>(SharedFateMod.id("world_reset"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldResetPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, WorldResetPayload::runNumber,
					ByteBufCodecs.VAR_INT, WorldResetPayload::delayTicks,
					WorldResetPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
