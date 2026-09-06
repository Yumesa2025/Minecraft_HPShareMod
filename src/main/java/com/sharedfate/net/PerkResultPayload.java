package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — 무엇이 골라졌는지 알리고, 그 카드를 잠깐 보여 준 뒤 창을 닫으라는 신호.
 *
 * <p>이 시간 동안에도 서버는 시간을 멈춘 채 무적을 유지한다. 화면을 보는 사이에 얻어맞으면
 * 창을 띄운 의미가 없다.
 *
 * @param perkId      골라진 증강 식별자
 * @param chooserName 고른 사람 이름. 시간이 다 되어 자동으로 정해졌으면 빈 문자열
 * @param holdTicks   이 화면을 유지할 시간(틱)
 */
public record PerkResultPayload(String perkId, String chooserName, int holdTicks)
		implements CustomPacketPayload {

	public static final Type<PerkResultPayload> TYPE = new Type<>(SharedFateMod.id("perk_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkResultPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, PerkResultPayload::perkId,
					ByteBufCodecs.STRING_UTF8, PerkResultPayload::chooserName,
					ByteBufCodecs.VAR_INT, PerkResultPayload::holdTicks,
					PerkResultPayload::new);

	public PerkResultPayload {
		holdTicks = Math.max(0, holdTicks);
	}

	/** 시간이 다 되어 서버가 대신 골랐는가. */
	public boolean automatic() {
		return chooserName.isEmpty();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
