package com.sharedfate.net;

import com.sharedfate.perk.PerkClientRules;
import com.sharedfate.perk.PerkManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class SharedFateNetworking {
	// 6: 증강(Perk) 페이로드 3종 추가
	// 7: TeamSyncPayload 에 공유 레벨·다음 증강 레벨 추가
	// 8: 강제 증강 선택 — PerkOfferPayload 에 forced·remainingTicks 추가,
	//    PerkCloseOfferPayload 신설
	// 9: PerkOfferPayload.PerkOption 에 카드 아이콘(아이템 이름) 추가
	// 10: 클라이언트가 있어야 하는 증강 2종(double_jump / hide_hud) —
	//     PerkClientFeaturesPayload(S2C) 와 DoubleJumpPayload(C2S) 신설.
	//     기존 페이로드의 형식은 그대로지만, 이 패킷을 모르는 클라이언트는 공중 점프가
	//     조용히 안 되고 HUD 가림도 걸리지 않는다. 그 상태로 붙어 있는 편이 더 나쁘므로
	//     악수 단계에서 걸러지도록 번호를 올린다.
	// 11: /shareteam 화면 — TeamSyncPayload 에 팀 이름·최대 체력·교환 주기·증강 사용
	//     여부·리더 UUID 를 추가하고 OpenTeamScreenPayload 를 신설했다.
	//     TeamSyncPayload 의 형식 자체가 바뀌었으므로 예전 클라이언트는 읽지 못한다.
	// 12: 증강 선택 연출 — PerkDrawPayload(선택자 뽑기)·PerkResultPayload(고른 카드 보여주기)
	//     신설, PerkSyncPayload 가 이름만이 아니라 설명·등급까지 담도록 바뀌었다.
	// 13: 피격·사망 알림을 팀 생성 시 정하는 설정으로 —
	//     TeamSyncPayload 의 perksEnabled 자리가 Options(perks/damageAlert/deathAlert)
	//     중첩 묶음으로 바뀌었고 TeamWipePayload 를 신설했다. TeamSyncPayload 의 형식
	//     자체가 바뀌었으므로 예전 클라이언트는 읽지 못한다.
	// 14: 증강 후보 다시 뽑기 — PerkRerollC2SPayload(C2S) 를 신설하고 PerkOfferPayload 에
	//     이번 회차에 남은 다시 뽑기 횟수를 실었다. PerkOfferPayload 의 형식 자체가 바뀌었으므로
	//     예전 클라이언트는 선택창을 아예 읽지 못한다. 이 판은 클라이언트를 함께 배포한다.
	public static final int PROTOCOL_VERSION = 14;

	private SharedFateNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DamageAlertPayload.TYPE, DamageAlertPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelectedSlotPayload.TYPE, SelectedSlotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TeamSyncPayload.TYPE, TeamSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WorldResetPayload.TYPE, WorldResetPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TeamWipePayload.TYPE, TeamWipePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PerkOfferPayload.TYPE, PerkOfferPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PerkSyncPayload.TYPE, PerkSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				PerkCloseOfferPayload.TYPE, PerkCloseOfferPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				PerkClientFeaturesPayload.TYPE, PerkClientFeaturesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				OpenTeamScreenPayload.TYPE, OpenTeamScreenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PerkDrawPayload.TYPE, PerkDrawPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				PerkResultPayload.TYPE, PerkResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectedSlotC2SPayload.TYPE, SelectedSlotC2SPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PerkChoiceC2SPayload.TYPE, PerkChoiceC2SPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(
				PerkRerollC2SPayload.TYPE, PerkRerollC2SPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DoubleJumpPayload.TYPE, DoubleJumpPayload.CODEC);
		PayloadTypeRegistry.clientboundConfiguration().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectedSlotC2SPayload.TYPE,
				(payload, context) -> TeamBroadcaster.reportSelectedSlot(
						context.server(), context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(PerkChoiceC2SPayload.TYPE,
				(payload, context) -> PerkManager.applyChoice(
						context.player(), payload.milestone(), payload.perkId()));
		// 다시 뽑기 요청. 남은 횟수 검사도 재추첨도 전부 서버가 한다. 클라이언트는 눌렀다는
		// 사실과 어느 창에서 눌렀는지만 보낸다.
		ServerPlayNetworking.registerGlobalReceiver(PerkRerollC2SPayload.TYPE,
				(payload, context) -> PerkManager.applyReroll(
						context.player(), payload.milestone()));
		// 공중 점프 요청. 세기도 가능 여부도 전부 서버가 다시 따진다.
		ServerPlayNetworking.registerGlobalReceiver(DoubleJumpPayload.TYPE,
				(payload, context) -> PerkClientRules.onDoubleJumpRequest(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(TeamBroadcaster::flushSelectedSlots);
		ServerTickEvents.END_SERVER_TICK.register(TeamBroadcaster::flushTeamLevels);
		// 클라이언트가 있어야 하는 증강(double_jump / hide_hud)의 동기화·접지 판정 지점.
		// SharedFateMod 가 아니라 여기서 거는 이유는 이 기능이 네트워크 경로 하나로만
		// 성립하기 때문이다. 패킷 등록과 같은 자리에 두면 한쪽만 빠뜨릴 수 없다.
		ServerTickEvents.END_SERVER_TICK.register(PerkClientRules::tick);
		// 다시 접속했을 때 "이미 보냈다"고 착각하지 않도록 나갈 때 기록을 버린다.
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> PerkClientRules.forget(handler.player.getUUID()));
		ClientModGate.register();
	}
}
