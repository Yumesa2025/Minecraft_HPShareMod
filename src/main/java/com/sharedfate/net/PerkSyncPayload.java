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
 * <p>중첩이 없으므로 한 증강은 한 줄이다.
 *
 * <p>이름과 설명을 따로 담는다. {@code /shareteam} 창의 증강 탭이 이름만 보여 주면 무엇을
 * 들고 있는지 알 수 없어서, 고를 때 읽었던 설명을 그대로 다시 볼 수 있어야 한다.
 *
 * @param owned        보유 증강. 없으면 빈 목록
 * @param pendingCount 아직 고르지 않은 선택권 수
 * @param chooserName  지금 고를 차례인 팀원 이름. 선택자가 없거나 미정이면 빈 문자열
 */
public record PerkSyncPayload(List<Owned> owned, int pendingCount, String chooserName)
		implements CustomPacketPayload {

	/**
	 * 보유 증강 하나.
	 *
	 * @param name        증강 이름
	 * @param description 고를 때 보여 준 설명 그대로
	 * @param rarity      등급 이름. 화면에서 색을 고르는 데 쓴다
	 */
	public record Owned(String name, String description, String rarity) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Owned> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, Owned::name,
						ByteBufCodecs.STRING_UTF8, Owned::description,
						ByteBufCodecs.STRING_UTF8, Owned::rarity,
						Owned::new);
	}

	/** 한 패킷에 담을 수 있는 보유 증강 수 상한. */
	public static final int MAX_OWNED = 64;

	/** 증강이 하나도 없고 대기 중인 선택권도 없는 상태. */
	public static final PerkSyncPayload EMPTY = new PerkSyncPayload(List.of(), 0, "");

	public static final Type<PerkSyncPayload> TYPE = new Type<>(SharedFateMod.id("perk_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkSyncPayload> CODEC =
			StreamCodec.composite(
					Owned.CODEC.apply(ByteBufCodecs.list(MAX_OWNED)), PerkSyncPayload::owned,
					ByteBufCodecs.VAR_INT, PerkSyncPayload::pendingCount,
					ByteBufCodecs.STRING_UTF8, PerkSyncPayload::chooserName,
					PerkSyncPayload::new);

	public PerkSyncPayload {
		owned = List.copyOf(owned);
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
