package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandshakePayload(int protocolVersion, int inventoryLayout) implements CustomPacketPayload {
	public static final int THREE_ROW_LAYOUT = 0;
	public static final int SIX_ROW_LAYOUT = 1;
	public static final Type<HandshakePayload> TYPE = new Type<>(SharedFateMod.id("handshake"));
	public static final StreamCodec<FriendlyByteBuf, HandshakePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, HandshakePayload::protocolVersion,
					ByteBufCodecs.VAR_INT, HandshakePayload::inventoryLayout,
					HandshakePayload::new);

	public static HandshakePayload current() {
		return new HandshakePayload(
				SharedFateNetworking.PROTOCOL_VERSION,
				ExpandedInventoryManager.enabled() ? SIX_ROW_LAYOUT : THREE_ROW_LAYOUT);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
