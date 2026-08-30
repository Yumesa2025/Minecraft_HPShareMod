package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — 무엇이 골라졌는지 알리고, 그 카드를 잠깐 보여 준 뒤 창을 닫으라는 신호.
 *
 * <p>고른 사람은 자기가 뭘 눌렀는지 알지만 <b>나머지 팀원은 창이 그냥 사라질 뿐</b>이라,
 * 무엇이 정해졌는지 모른 채 게임으로 돌아가게 된다. 그래서 고른 카드 하나만 남겨 잠깐
 * 보여 준다.
 *
 * <p>{@code perkId} 만 보내는 이유는 클라이언트가 이미 {@link PerkOfferPayload} 로 후보 셋의
 * 이름·설명·아이콘을 들고 있기 때문이다. 같은 내용을 두 번 보낼 이유가 없다.
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
