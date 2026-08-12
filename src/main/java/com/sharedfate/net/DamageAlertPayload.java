package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DamageAlertPayload(String playerName, int durationTicks) implements CustomPacketPayload {
	public static final Type<DamageAlertPayload> TYPE = new Type<>(SharedFateMod.id("damage_alert"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DamageAlertPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, DamageAlertPayload::playerName,
					ByteBufCodecs.VAR_INT, DamageAlertPayload::durationTicks,
					DamageAlertPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
