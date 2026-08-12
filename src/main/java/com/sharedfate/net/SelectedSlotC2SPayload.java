package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectedSlotC2SPayload(int slot) implements CustomPacketPayload {
	public static final Type<SelectedSlotC2SPayload> TYPE = new Type<>(SharedFateMod.id("selected_slot_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectedSlotC2SPayload> CODEC =
			ByteBufCodecs.VAR_INT.map(SelectedSlotC2SPayload::new, SelectedSlotC2SPayload::slot).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
