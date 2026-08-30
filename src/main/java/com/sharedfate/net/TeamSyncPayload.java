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
 * <p>팀 설정까지 함께 보내는 이유는 {@code /shareteam} 화면 때문이다. 화면을 열 때마다
 * 서버에 묻는 대신, 이미 오가는 이 묶음에 실어 두면 창이 곧바로 열린다.
 *
 * @param members       팀원 목록. 팀에 속하지 않으면 빈 목록
 * @param teamName      팀 이름. 팀에 속하지 않으면 빈 문자열
 * @param xpLevel       팀이 공유하는 경험치 레벨
 * @param nextPerkLevel 다음 증강이 나오는 레벨. 남은 증강이 없거나 증강을 쓰지 않으면 0
 * @param maxHealth     팀이 정한 공유 최대 체력
 * @param swapIntervalMinutes 위치 교환 주기(분). 꺼져 있으면 0
 * @param options       팀을 만들 때 정한 켜고 끄기 셋
 * @param leaderId      팀 리더의 UUID. 팀이 없으면 0 UUID.
 *                      묶음을 팀 전체에 한 번만 만들어 보내므로, 받는 쪽이 자기 UUID 와
 *                      견주어 리더인지 판단한다
 */
public record TeamSyncPayload(List<Member> members, String teamName, int xpLevel, int nextPerkLevel,
		float maxHealth, int swapIntervalMinutes, Options options, UUID leaderId)
		implements CustomPacketPayload {
	public record Member(UUID id, String name, int selectedSlot) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Member> CODEC =
				StreamCodec.composite(
						UUIDUtil.STREAM_CODEC, Member::id,
						ByteBufCodecs.STRING_UTF8, Member::name,
						ByteBufCodecs.VAR_INT, Member::selectedSlot,
						Member::new);
	}

	/**
	 * 켜고 끄기 셋을 한 칸에 담는 묶음.
	 *
	 * <p>따로 묶은 이유는 자리가 없어서다. 바깥 {@link #CODEC} 의
	 * {@code StreamCodec.composite} 는 항목 <b>8개가 상한</b>이고 이미 다 찼다. 앞으로 켜고
	 * 끄기가 더 늘어도 이 안에 넣으면 바깥은 그대로다.
	 *
	 * @param perks       이 팀이 증강을 쓰는가
	 * @param damageAlert 피격 알림을 보여 주는가
	 * @param deathAlert  사망 알림을 보여 주는가
	 */
	public record Options(boolean perks, boolean damageAlert, boolean deathAlert) {
		/** 셋 다 꺼진 상태. 팀에 속하지 않았을 때의 값이다. */
		public static final Options NONE = new Options(false, false, false);

		public static final StreamCodec<RegistryFriendlyByteBuf, Options> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.BOOL, Options::perks,
						ByteBufCodecs.BOOL, Options::damageAlert,
						ByteBufCodecs.BOOL, Options::deathAlert,
						Options::new);
	}

	/** 팀에 속하지 않은 상태. */
	public static final TeamSyncPayload EMPTY =
			new TeamSyncPayload(List.of(), "", 0, 0, 20.0F, 0, Options.NONE, new UUID(0L, 0L));

	public static final Type<TeamSyncPayload> TYPE = new Type<>(SharedFateMod.id("team_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TeamSyncPayload> CODEC =
			StreamCodec.composite(
					Member.CODEC.apply(ByteBufCodecs.list(16)), TeamSyncPayload::members,
					ByteBufCodecs.STRING_UTF8, TeamSyncPayload::teamName,
					ByteBufCodecs.VAR_INT, TeamSyncPayload::xpLevel,
					ByteBufCodecs.VAR_INT, TeamSyncPayload::nextPerkLevel,
					ByteBufCodecs.FLOAT, TeamSyncPayload::maxHealth,
					ByteBufCodecs.VAR_INT, TeamSyncPayload::swapIntervalMinutes,
					Options.CODEC, TeamSyncPayload::options,
					UUIDUtil.STREAM_CODEC, TeamSyncPayload::leaderId,
					TeamSyncPayload::new);

	public TeamSyncPayload {
		members = List.copyOf(members);
		// VAR_INT 는 음수를 담기에 낭비가 크므로 세 값 모두 0 이상으로 맞춘다.
		xpLevel = Math.max(0, xpLevel);
		nextPerkLevel = Math.max(0, nextPerkLevel);
		swapIntervalMinutes = Math.max(0, swapIntervalMinutes);
	}

	/** 이 팀이 증강을 쓰는가. */
	public boolean perksEnabled() {
		return options.perks();
	}

	/** 피격 알림을 보여 주는 팀인가. */
	public boolean damageAlertEnabled() {
		return options.damageAlert();
	}

	/** 사망 알림을 보여 주는 팀인가. */
	public boolean deathAlertEnabled() {
		return options.deathAlert();
	}

	/** 이 사람이 팀 리더인가. */
	public boolean isLeader(UUID player) {
		return leaderId.equals(player);
	}

	/** 위치 교환이 켜져 있는가. */
	public boolean swapEnabled() {
		return swapIntervalMinutes > 0;
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
