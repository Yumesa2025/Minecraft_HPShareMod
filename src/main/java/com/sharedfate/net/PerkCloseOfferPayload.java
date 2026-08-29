package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — 강제로 띄운 증강 선택창을 닫으라는 지시.
 *
 * <p>강제 오픈된 창은 ESC 로 닫을 수 없으므로 닫는 책임이 서버에 있다. 고른 사람에게도,
 * 옆에서 지켜보던 팀원에게도 똑같이 보내야 관전 화면이 남아 있지 않는다.
 *
 * <p>{@code milestone} 을 함께 싣는 이유는 늦게 도착한 닫기 지시가 그 사이에 열린 다음 구간의
 * 창을 닫아버리는 일을 막기 위해서다. 클라이언트는 지금 열려 있는 창의 구간과 같을 때만 닫는다.
 *
 * @param milestone 닫아야 할 창이 다루던 레벨 구간. {@link #ANY} 면 열려 있는 증강 창을 무조건 닫는다
 */
public record PerkCloseOfferPayload(int milestone) implements CustomPacketPayload {
	/** 구간을 가리지 않고 닫으라는 값. */
	public static final int ANY = -1;

	/** 열려 있는 증강 창을 구간과 무관하게 닫는 지시. */
	public static final PerkCloseOfferPayload ALL = new PerkCloseOfferPayload(ANY);

	public static final Type<PerkCloseOfferPayload> TYPE =
			new Type<>(SharedFateMod.id("perk_close_offer"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkCloseOfferPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.INT, PerkCloseOfferPayload::milestone,
					PerkCloseOfferPayload::new);

	/** 지금 열려 있는 창(구간 {@code openMilestone})을 이 지시로 닫아야 하는지. */
	public boolean matches(int openMilestone) {
		return milestone == ANY || milestone == openMilestone;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
