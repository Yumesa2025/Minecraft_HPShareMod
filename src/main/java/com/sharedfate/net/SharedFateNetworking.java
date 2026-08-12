package com.sharedfate.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class SharedFateNetworking {
	public static final int PROTOCOL_VERSION = 5;

	private SharedFateNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DamageAlertPayload.TYPE, DamageAlertPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelectedSlotPayload.TYPE, SelectedSlotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TeamSyncPayload.TYPE, TeamSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WorldResetPayload.TYPE, WorldResetPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectedSlotC2SPayload.TYPE, SelectedSlotC2SPayload.CODEC);
		PayloadTypeRegistry.clientboundConfiguration().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectedSlotC2SPayload.TYPE,
				(payload, context) -> TeamBroadcaster.reportSelectedSlot(
						context.server(), context.player(), payload.slot()));
		ServerTickEvents.END_SERVER_TICK.register(TeamBroadcaster::flushSelectedSlots);
		ClientModGate.register();
	}
}
