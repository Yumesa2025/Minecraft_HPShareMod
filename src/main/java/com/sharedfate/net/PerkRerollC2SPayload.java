package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — 증강 후보 다시 뽑기 요청.
 *
 * <p>{@link PerkChoiceC2SPayload} 를 본떴다. <b>클라이언트는 "다시 뽑기를 눌렀다"만 보내고
 * 판단은 전부 서버가 한다.</b> 남은 횟수도, 지금 다시 뽑아도 되는 상태인지도 서버가 다시
 * 따진다 — 클라이언트가 보낸 남은 횟수를 믿으면 창을 조작해 무한히 다시 뽑을 수 있다.
 * 그래서 이 페이로드에는 <b>어느 창에서 눌렀는지</b>를 가리는 {@code milestone} 밖에 없다.
 *
 * <p>서버가 검사하는 것은 {@code PerkManager.applyReroll} 에 적어 두었다. 진행 중인 강제
 * 선택 세션이 아니거나, 보낸 사람이 선택자가 아니거나, 남은 횟수가 0이면 <b>조용히 무시</b>
 * 한다. 지연·재전송된 패킷과 조작된 패킷을 같은 길로 버리기 위해서다.
 *
 * @param milestone 다시 뽑으려는 선택권의 레벨 구간
 */
public record PerkRerollC2SPayload(int milestone) implements CustomPacketPayload {
	public static final Type<PerkRerollC2SPayload> TYPE =
			new Type<>(SharedFateMod.id("perk_reroll_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkRerollC2SPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, PerkRerollC2SPayload::milestone,
					PerkRerollC2SPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
