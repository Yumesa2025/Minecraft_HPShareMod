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
	// 15: 「게임 시작」 — 회차가 팀에 붙었다. TeamSyncPayload 의 Options 묶음에
	//     runStarted 를 더해 클라이언트가 「시작 대기」인지 알 수 있게 했다. 팀 화면의
	//     「게임 시작」 단추를 그릴지 정하는 값이라 이것 없이는 화면을 만들 수 없다.
	//     TeamSyncPayload 의 형식 자체가 바뀌었으므로 예전 클라이언트는 팀 동기화를
	//     아예 읽지 못한다. 이 판도 클라이언트를 함께 배포한다.
	// 16: 인챈트 다이아몬드 칸 — EnchantmentMenu 에 칸이 하나 늘었고, 확장 27칸이 창
	//     오른쪽 바깥에서 플레이어 인벤토리 아래로 옮겨졌다. 묶음 형식은 그대로지만
	//     서버와 클라이언트의 슬롯 수가 다르면 클라이언트가
	//     IndexOutOfBoundsException 으로 죽는다. 막을 수단이 악수뿐이라 번호를 올린다.
	// 17: 팀 화면 「능력치」 탭의 공격력 — AttackDamagePayload(S2C) 를 신설했다.
	//     바닐라가 minecraft:attack_damage 만은 클라이언트에 동기화하지 않아
	//     (Attributes 에서 이 속성만 setSyncable(true) 없이 등록된다) 서버가 따로 보낸다.
	//     기존 페이로드의 형식은 한 바이트도 바뀌지 않았지만, 이 패킷을 모르는 클라이언트는
	//     능력치 탭에서 공격력 줄만 조용히 빠진 화면을 보게 된다. 「값이 안 보인다」는
	//     「모드가 안 맞는다」보다 알아채기 어려우므로 악수 단계에서 걸러지게 한다.
	// 18: 능력치가 여덟 줄이 되었다 — AttackDamagePayload 가 StatSnapshotPayload 로 바뀌면서
	//     받는 피해 배율과 몹 최대 체력·공격력 배율 셋이 더 실린다(4바이트 → 20바이트).
	//     셋 다 서버만 아는 값이다. 증강이 만드는 배율은 바닐라 속성이 아니고, 몹 배율은
	//     사람이 아니라 몹에게 붙어 클라이언트에 흔적이 없다. 형식 자체가 바뀌었으므로
	//     예전 클라이언트는 이 묶음을 읽지 못한다.
	//     공격 속도(minecraft:attack_speed)는 여기 없다 — 그 속성만은 공격력과 달리
	//     setSyncable(true) 로 등록되어 수정자까지 클라이언트에 그대로 온다.
	public static final int PROTOCOL_VERSION = 18;

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
		PayloadTypeRegistry.clientboundPlay().register(
				StatSnapshotPayload.TYPE, StatSnapshotPayload.CODEC);
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
		// 서버만 아는 능력치(공격력·받는 피해 배율·몹 배율). 팀에 속하지 않은 사람도
		// 능력치 표시에서 이 줄들을 보므로 팀 경로가 아니라 여기에 있다.
		ServerTickEvents.END_SERVER_TICK.register(StatSnapshotBroadcaster::flush);
		// 클라이언트가 있어야 하는 증강(double_jump / hide_hud)의 동기화·접지 판정 지점.
		// SharedFateMod 가 아니라 여기서 거는 이유는 이 기능이 네트워크 경로 하나로만
		// 성립하기 때문이다. 패킷 등록과 같은 자리에 두면 한쪽만 빠뜨릴 수 없다.
		ServerTickEvents.END_SERVER_TICK.register(PerkClientRules::tick);
		// 다시 접속했을 때 "이미 보냈다"고 착각하지 않도록 나갈 때 기록을 버린다.
		// 클라이언트는 월드에서 나가며 받은 값을 모두 버리므로, 서버가 기억을 들고 있으면
		// 상태가 그대로인 사람은 다시 들어와도 값을 영영 받지 못한다.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PerkClientRules.forget(handler.player.getUUID());
			StatSnapshotBroadcaster.forget(handler.player.getUUID());
		});
		ClientModGate.register();
	}
}
