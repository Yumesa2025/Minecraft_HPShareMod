package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * S2C — 팀 명단과 공유 레벨 동기화.
 *
 * <p>레벨 구간 계산은 전부 서버가 한다. 클라이언트는 받은 값을 그대로 보여주기만 하므로
 * 증강 구간 값이 바뀌어도 클라이언트를 고칠 필요가 없다.
 *
 * @param members       팀원 목록. 팀에 속하지 않으면 빈 목록
 * @param xpLevel       팀이 공유하는 경험치 레벨
 * @param nextPerkLevel 다음 증강이 나오는 레벨. 남은 증강이 없거나 증강을 쓰지 않으면 0
 */
public record TeamSyncPayload(List<Member> members, int xpLevel, int nextPerkLevel)
		implements CustomPacketPayload {
	public record Member(UUID id, String name, int selectedSlot) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Member> CODEC =
				StreamCodec.composite(
						UUIDUtil.STREAM_CODEC, Member::id,
						ByteBufCodecs.STRING_UTF8, Member::name,
						ByteBufCodecs.VAR_INT, Member::selectedSlot,
						Member::new);
	}

	/** 팀에 속하지 않은 상태. */
	public static final TeamSyncPayload EMPTY = new TeamSyncPayload(List.of(), 0, 0);

	public static final Type<TeamSyncPayload> TYPE = new Type<>(SharedFateMod.id("team_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TeamSyncPayload> CODEC =
			StreamCodec.composite(
					Member.CODEC.apply(ByteBufCodecs.list(16)), TeamSyncPayload::members,
					ByteBufCodecs.VAR_INT, TeamSyncPayload::xpLevel,
					ByteBufCodecs.VAR_INT, TeamSyncPayload::nextPerkLevel,
					TeamSyncPayload::new);

	public TeamSyncPayload {
		members = List.copyOf(members);
		// VAR_INT 는 음수를 담기에 낭비가 크므로 두 값 모두 0 이상으로 맞춘다.
		xpLevel = Math.max(0, xpLevel);
		nextPerkLevel = Math.max(0, nextPerkLevel);
	}

	/** 다음 증강까지 남은 레벨. 더 이상 받을 증강이 없으면 -1. */
	public int levelsToNextPerk() {
		return nextPerkLevel <= 0 ? -1 : Math.max(0, nextPerkLevel - xpLevel);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
