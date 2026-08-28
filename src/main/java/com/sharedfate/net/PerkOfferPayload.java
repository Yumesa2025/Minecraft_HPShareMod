package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * S2C — 증강 후보 제시.
 *
 * <p>서버가 {@code /shareteam perk} 실행에 응답해 보낸다. 클라이언트는 이걸 받아
 * 선택 화면을 연다.
 *
 * @param milestone 이 선택권을 만든 레벨 구간 (3, 6, …, 36)
 * @param canChoose 실제로 고를 수 있는지. false면 다른 팀원이 고르는 걸 지켜보는 관전 모드
 * @param options   제시된 후보. 최대 {@link #MAX_OPTIONS}개이며, 풀이 모자라면 더 적을 수 있다
 */
public record PerkOfferPayload(int milestone, boolean canChoose, List<PerkOption> options)
		implements CustomPacketPayload {

	/** 한 번에 제시할 수 있는 후보 수 상한. */
	public static final int MAX_OPTIONS = 3;

	/**
	 * 화면에 그릴 후보 하나. 서버가 이미 표시용 문자열로 풀어서 보내므로
	 * 클라이언트는 증강 정의를 알 필요가 없다.
	 *
	 * @param id          증강 식별자. 선택 전송 시 그대로 되돌려 보낸다
	 * @param name        화면에 보이는 이름
	 * @param description 화면에 보이는 설명
	 * @param rarity      등급 문자열 ({@code common} / {@code rare} / {@code epic})
	 */
	public record PerkOption(String id, String name, String description, String rarity) {
		public static final StreamCodec<RegistryFriendlyByteBuf, PerkOption> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, PerkOption::id,
						ByteBufCodecs.STRING_UTF8, PerkOption::name,
						ByteBufCodecs.STRING_UTF8, PerkOption::description,
						ByteBufCodecs.STRING_UTF8, PerkOption::rarity,
						PerkOption::new);
	}

	public static final Type<PerkOfferPayload> TYPE = new Type<>(SharedFateMod.id("perk_offer"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkOfferPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, PerkOfferPayload::milestone,
					ByteBufCodecs.BOOL, PerkOfferPayload::canChoose,
					PerkOption.CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS)), PerkOfferPayload::options,
					PerkOfferPayload::new);

	public PerkOfferPayload {
		options = List.copyOf(options);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
