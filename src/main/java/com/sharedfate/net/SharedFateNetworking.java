package com.sharedfate.net;

import com.sharedfate.perk.PerkManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class SharedFateNetworking {
	// 6: 증강(Perk) 페이로드 3종 추가
	// 7: TeamSyncPayload 에 공유 레벨·다음 증강 레벨 추가
	// 8: 강제 증강 선택 — PerkOfferPayload 에 forced·remainingTicks 추가,
	//    PerkCloseOfferPayload 신설
	// 9: PerkOfferPayload.PerkOption 에 카드 아이콘(아이템 이름) 추가
	public static final int PROTOCOL_VERSION = 9;

	private SharedFateNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DamageAlertPayload.TYPE, DamageAlertPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelectedSlotPayload.TYPE, SelectedSlotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TeamSyncPayload.TYPE, TeamSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WorldResetPayload.TYPE, WorldResetPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PerkOfferPayload.TYPE, PerkOfferPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PerkSyncPayload.TYPE, PerkSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				PerkCloseOfferPayload.TYPE, PerkCloseOfferPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectedSlotC2SPayload.TYPE, SelectedSlotC2SPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PerkChoiceC2SPayload.TYPE, PerkChoiceC2SPayload.CODEC);
		PayloadTypeRegistry.clientboundConfiguration().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectedSlotC2SPayload.TYPE,
				(payload, context) -> TeamBroadcaster.reportSelectedSlot(
						context.server(), context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(PerkChoiceC2SPayload.TYPE,
				(payload, context) -> PerkManager.applyChoice(
						context.player(), payload.milestone(), payload.perkId()));
		ServerTickEvents.END_SERVER_TICK.register(TeamBroadcaster::flushSelectedSlots);
		ServerTickEvents.END_SERVER_TICK.register(TeamBroadcaster::flushTeamLevels);
		ClientModGate.register();
	}
}
