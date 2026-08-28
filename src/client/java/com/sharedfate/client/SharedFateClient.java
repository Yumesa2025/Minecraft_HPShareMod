package com.sharedfate.client;

import com.sharedfate.SharedFateMod;
import com.sharedfate.client.hud.DamageAlertHud;
import com.sharedfate.client.hud.HotbarHighlight;
import com.sharedfate.client.perk.PerkClientState;
import com.sharedfate.client.perk.PerkOfferScreen;
import com.sharedfate.net.DamageAlertPayload;
import com.sharedfate.net.HandshakePayload;
import com.sharedfate.net.PerkOfferPayload;
import com.sharedfate.net.PerkSyncPayload;
import com.sharedfate.net.SelectedSlotPayload;
import com.sharedfate.net.SharedFateNetworking;
import com.sharedfate.net.TeamSyncPayload;
import com.sharedfate.net.WorldResetPayload;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import java.util.UUID;

public class SharedFateClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfigurationNetworking.registerGlobalReceiver(HandshakePayload.TYPE,
				(payload, context) -> {
					if (payload.protocolVersion() != SharedFateNetworking.PROTOCOL_VERSION) {
						context.packetContext().orElseThrow(PacketContext.CONNECTION).disconnect(
								Component.literal(
										"SharedFate 모드 버전이 서버와 맞지 않습니다."));
						return;
					}
					ExpandedInventoryManager.applyNegotiatedClientLayout(payload.inventoryLayout());
				});

		ClientPlayNetworking.registerGlobalReceiver(TeamSyncPayload.TYPE, (payload, context) -> {
			Minecraft client = Minecraft.getInstance();
			UUID localPlayer = client.player == null
					? new UUID(0L, 0L) : client.player.getUUID();
			ClientTeamState.setTeam(payload.members(), localPlayer);
			ExpandedInventoryManager.setClientTeamActive(client.player, ClientTeamState.inTeam());
			SelectedSlotReporter.forceResend();
		});
		ClientPlayNetworking.registerGlobalReceiver(SelectedSlotPayload.TYPE,
				(payload, context) -> ClientTeamState.setAllySlot(payload.player(), payload.slot()));
		ClientPlayNetworking.registerGlobalReceiver(DamageAlertPayload.TYPE,
				(payload, context) -> DamageAlertHud.show(
						payload.playerName(), payload.durationTicks()));
		ClientPlayNetworking.registerGlobalReceiver(WorldResetPayload.TYPE,
				(payload, context) -> GameOverClientDisplay.show(
						payload.runNumber(), payload.delayTicks()));

		// 증강 후보 제시 — 네트워크 스레드에서 화면을 열 수 없으므로 클라이언트 스레드로 넘긴다.
		ClientPlayNetworking.registerGlobalReceiver(PerkOfferPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> context.client().setScreenAndShow(new PerkOfferScreen(payload))));
		ClientPlayNetworking.registerGlobalReceiver(PerkSyncPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> PerkClientState.update(payload.ownedLines(),
								payload.pendingCount(), payload.chooserName())));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientTeamState.clear();
			SelectedSlotReporter.reset();
			DamageAlertHud.clear();
			ExpandedInventoryManager.clearNegotiatedClientLayout();
			GameOverClientDisplay.clear();
			PerkClientState.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SelectedSlotReporter.tick(client);
			DamageAlertHud.tick();
			GameOverClientDisplay.tick(client);
		});

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				SharedFateMod.id("hotbar_highlight"),
				new HotbarHighlight());
		HudElementRegistry.addLast(
				SharedFateMod.id("damage_alert"),
				new DamageAlertHud());
	}
}
