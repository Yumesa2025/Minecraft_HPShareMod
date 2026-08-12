package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record SelectedSlotPayload(UUID player, int slot) implements CustomPacketPayload {
	public static final Type<SelectedSlotPayload> TYPE = new Type<>(SharedFateMod.id("selected_slot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectedSlotPayload> CODEC =
			StreamCodec.composite(
					UUIDUtil.STREAM_CODEC, SelectedSlotPayload::player,
					ByteBufCodecs.VAR_INT, SelectedSlotPayload::slot,
					SelectedSlotPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
