package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * S2C — 팀 보유 증강 동기화. 클라이언트 HUD와 화면 표시용이다.
 *
 * <p>서버가 이미 표시용 문자열로 풀어서 보낸다. 중첩이 없으므로 한 증강은 한 줄이다.
 *
 * @param ownedLines  보유 증강을 줄 단위로 풀어 쓴 것. 없으면 빈 목록
 * @param pendingCount 아직 고르지 않은 선택권 수
 * @param chooserName 지금 고를 차례인 팀원 이름. 선택자가 없거나 미정이면 빈 문자열
 */
public record PerkSyncPayload(List<String> ownedLines, int pendingCount, String chooserName)
		implements CustomPacketPayload {

	/** 한 패킷에 담을 수 있는 보유 증강 줄 수 상한. */
	public static final int MAX_OWNED_LINES = 64;

	/** 증강이 하나도 없고 대기 중인 선택권도 없는 상태. */
	public static final PerkSyncPayload EMPTY = new PerkSyncPayload(List.of(), 0, "");

	public static final Type<PerkSyncPayload> TYPE = new Type<>(SharedFateMod.id("perk_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkSyncPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_OWNED_LINES)),
					PerkSyncPayload::ownedLines,
					ByteBufCodecs.VAR_INT, PerkSyncPayload::pendingCount,
					ByteBufCodecs.STRING_UTF8, PerkSyncPayload::chooserName,
					PerkSyncPayload::new);

	public PerkSyncPayload {
		ownedLines = List.copyOf(ownedLines);
	}

	/** 고를 차례인 팀원이 정해져 있는지. */
	public boolean hasChooser() {
		return !chooserName.isEmpty();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
