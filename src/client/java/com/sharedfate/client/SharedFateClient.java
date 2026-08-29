package com.sharedfate.client;

import com.sharedfate.SharedFateMod;
import com.sharedfate.client.hud.DamageAlertHud;
import com.sharedfate.client.hud.HotbarHighlight;
import com.sharedfate.client.hud.TeamLevelHud;
import com.sharedfate.client.perk.PerkClientState;
import com.sharedfate.client.perk.PerkOfferScreen;
import com.sharedfate.net.DamageAlertPayload;
import com.sharedfate.net.HandshakePayload;
import com.sharedfate.net.PerkCloseOfferPayload;
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
import net.minecraft.client.gui.screens.DeathScreen;
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
			ClientTeamState.setTeam(payload, localPlayer);
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
						() -> openOfferScreen(context.client(), payload)));
		// 강제로 띄운 창은 ESC 로 닫을 수 없으므로 닫는 책임이 서버에 있다.
		// 고른 사람과 관전하던 팀원의 화면이 함께 닫혀야 한다.
		ClientPlayNetworking.registerGlobalReceiver(PerkCloseOfferPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> closeOfferScreen(context.client(), payload)));
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
		// 상태이상 아이콘 바로 뒤에 붙인다. 아이콘 위에 겹쳐 그려지고, F1 로 HUD 를 끄면
		// 바닐라가 이 구간 자체를 건너뛰므로 같이 사라진다.
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MOB_EFFECTS,
				SharedFateMod.id("team_level"),
				new TeamLevelHud());
	}

	/**
	 * 증강 선택창을 연다.
	 *
	 * <p>사망 화면만은 밀어내지 않는다. 밀어내면 부활 버튼이 사라져 아무것도 못 하게 된다.
	 * 창을 못 봐도 서버는 제한시간이 지나면 알아서 무작위로 골라 주므로 진행이 막히지 않는다.
	 */
	private static void openOfferScreen(Minecraft client, PerkOfferPayload payload) {
		if (client.gui.screen() instanceof DeathScreen) {
			return;
		}
		client.setScreenAndShow(new PerkOfferScreen(payload));
	}

	/**
	 * 서버의 지시로 증강 선택창을 닫는다.
	 *
	 * <p>다른 화면이 떠 있으면 아무것도 하지 않는다. 늦게 도착한 지시가 그 사이에 열린 다음
	 * 구간의 창을 닫아버리지 않도록 구간까지 맞춰 본다.
	 */
	private static void closeOfferScreen(Minecraft client, PerkCloseOfferPayload payload) {
		if (!(client.gui.screen() instanceof PerkOfferScreen offer)) {
			return;
		}
		if (!payload.matches(offer.milestone())) {
			return;
		}
		offer.closeFromServer();
	}
}
