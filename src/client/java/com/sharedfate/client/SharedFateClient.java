package com.sharedfate.client;

import com.sharedfate.SharedFateMod;
import com.sharedfate.client.hud.DamageAlertHud;
import com.sharedfate.client.hud.HotbarHighlight;
import com.sharedfate.client.hud.TeamLevelHud;
import com.sharedfate.client.team.TeamScreen;
import com.sharedfate.client.perk.ClientPerkFeatures;
import com.sharedfate.client.perk.DoubleJumpHandler;
import com.sharedfate.client.perk.PerkClientState;
import com.sharedfate.client.perk.PerkOfferScreen;
import com.sharedfate.net.DamageAlertPayload;
import com.sharedfate.net.HandshakePayload;
import com.sharedfate.net.OpenTeamScreenPayload;
import com.sharedfate.net.PerkClientFeaturesPayload;
import com.sharedfate.net.PerkCloseOfferPayload;
import com.sharedfate.net.PerkOfferPayload;
import com.sharedfate.net.PerkSyncPayload;
import com.sharedfate.net.SelectedSlotPayload;
import com.sharedfate.net.SharedFateNetworking;
import com.sharedfate.net.TeamSyncPayload;
import com.sharedfate.net.WorldResetPayload;
import com.sharedfate.perk.effect.HideHudEffect;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

		// /shareteam 화면. 네트워크 스레드에서 화면을 열 수 없으므로 클라이언트 스레드로 넘긴다.
		// 다른 창이 이미 떠 있으면 열지 않는다. 증강 강제 선택 창을 밀어내면 안 된다.
		ClientPlayNetworking.registerGlobalReceiver(OpenTeamScreenPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					if (context.client().gui.screen() == null) {
						context.client().setScreenAndShow(new TeamScreen());
					}
				}));

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
		// 클라이언트가 스스로 해야 하는 증강 기능. HUD 가 읽는 값이므로 렌더와 같은
		// 스레드(클라이언트 본 스레드)에서 갱신한다.
		ClientPlayNetworking.registerGlobalReceiver(PerkClientFeaturesPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> ClientPerkFeatures.update(payload)));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientTeamState.clear();
			SelectedSlotReporter.reset();
			DamageAlertHud.clear();
			ExpandedInventoryManager.clearNegotiatedClientLayout();
			GameOverClientDisplay.clear();
			PerkClientState.clear();
			ClientPerkFeatures.clear();
			DoubleJumpHandler.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SelectedSlotReporter.tick(client);
			DamageAlertHud.tick();
			GameOverClientDisplay.tick(client);
			DoubleJumpHandler.tick(client);
		});

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				SharedFateMod.id("hotbar_highlight"),
				new HotbarHighlight());
		HudElementRegistry.addLast(
				SharedFateMod.id("damage_alert"),
				new DamageAlertHud());
		// 경험치 레벨 숫자 바로 뒤에 붙인다. 그려지는 자리도 그 옆이라 순서를 맞춰 둔다.
		// F1 로 HUD 를 끄면 바닐라가 이 구간 자체를 건너뛰므로 같이 사라진다.
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.EXPERIENCE_LEVEL,
				SharedFateMod.id("team_level"),
				new TeamLevelHud());

		// 「장님 거인」 처럼 HUD 를 가리는 증강. 바닐라 요소를 지우지 않고 "가려야 할 때만
		// 건너뛰는" 껍데기로 감싼다. removeElement 는 되돌릴 수 없어 증강을 잃어도 영영
		// 안 보이고, Gui/Hud 에 mixin 을 거는 길은 26.2 에서 그리기 메서드가 전부 private
		// extract* 로 바뀌어 버전마다 깨지기 쉽다. 이 길이 둘 다 피한다.
		hideWhenPerkSays(VanillaHudElements.HEALTH_BAR, HideHudEffect.Element.HEALTH);
		hideWhenPerkSays(VanillaHudElements.FOOD_BAR, HideHudEffect.Element.FOOD);
		hideWhenPerkSays(VanillaHudElements.ARMOR_BAR, HideHudEffect.Element.ARMOR);
		hideWhenPerkSays(VanillaHudElements.AIR_BAR, HideHudEffect.Element.AIR);
	}

	/**
	 * 바닐라 HUD 요소 하나를 "가려야 할 때만 건너뛰는" 껍데기로 바꾼다.
	 *
	 * <p>감싸기만 하고 원래 요소를 버리지 않으므로, 증강이 없거나 잃은 뒤에는 바닐라가
	 * 그대로 그린다. 다른 모드가 같은 요소를 이미 바꿔 놓았어도 그쪽 결과를 그대로 감싼다.
	 */
	private static void hideWhenPerkSays(Identifier vanillaElement, HideHudEffect.Element element) {
		HudElementRegistry.replaceElement(vanillaElement, original -> {
			HudElement wrapped = (graphics, deltaTracker) -> {
				if (ClientPerkFeatures.isHidden(element)) {
					return;
				}
				original.extractRenderState(graphics, deltaTracker);
			};
			return wrapped;
		});
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
