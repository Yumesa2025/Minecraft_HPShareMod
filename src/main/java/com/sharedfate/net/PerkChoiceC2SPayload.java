package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — 증강 선택 전송.
 *
 * <p>클라이언트를 믿지 않는다. 서버는 보낸 사람이 실제 선택자인지, {@code milestone}이
 * 지금 처리해야 할 대기 건인지, {@code perkId}가 그 후보에 들어 있는지를 모두 다시 확인한다.
 *
 * @param milestone 고르는 선택권의 레벨 구간
 * @param perkId    고른 증강 식별자
 */
public record PerkChoiceC2SPayload(int milestone, String perkId) implements CustomPacketPayload {
	public static final Type<PerkChoiceC2SPayload> TYPE = new Type<>(SharedFateMod.id("perk_choice_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkChoiceC2SPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, PerkChoiceC2SPayload::milestone,
					ByteBufCodecs.STRING_UTF8, PerkChoiceC2SPayload::perkId,
					PerkChoiceC2SPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
