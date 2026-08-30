package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * S2C — 증강 선택권을 누가 가질지 뽑는 연출을 시작하라는 신호.
 *
 * <p>선택자는 <b>서버가 이미 정해 두었다.</b> 이 묶음은 그 결과를 미리 알려 주고, 클라이언트가
 * 정해진 시간 동안 이름을 굴리다 그 사람에서 멈추게 한다. 뽑기를 클라이언트가 하면 사람마다
 * 다른 결과가 나오므로, <b>연출만</b> 클라이언트가 맡는다.
 *
 * <p>연출이 도는 동안에도 서버는 시간을 멈춘 채 무적을 걸어 둔다. 연출이 끝나면 서버가
 * {@link PerkOfferPayload} 를 보내 실제 선택창으로 넘어간다.
 *
 * @param memberNames   굴려서 보여 줄 이름들. 접속 중인 팀원이다
 * @param chooserName   멈춰야 할 이름
 * @param durationTicks 연출을 돌릴 시간(틱)
 */
public record PerkDrawPayload(List<String> memberNames, String chooserName, int durationTicks)
		implements CustomPacketPayload {

	/** 한 팀의 최대 인원. 그보다 많은 이름이 올 일은 없다. */
	public static final int MAX_NAMES = 8;

	public static final Type<PerkDrawPayload> TYPE = new Type<>(SharedFateMod.id("perk_draw"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkDrawPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_NAMES)),
					PerkDrawPayload::memberNames,
					ByteBufCodecs.STRING_UTF8, PerkDrawPayload::chooserName,
					ByteBufCodecs.VAR_INT, PerkDrawPayload::durationTicks,
					PerkDrawPayload::new);

	public PerkDrawPayload {
		memberNames = List.copyOf(memberNames);
		durationTicks = Math.max(0, durationTicks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
