package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — 공중에서 점프 키를 눌렀다는 알림.
 *
 * <p>내용이 없다. 세기도 방향도 클라이언트가 정하지 않는다. 값을 실어 보내면 그 값이
 * 곧 치트 통로가 되기 때문에, 클라이언트는 "눌렀다"는 사실만 알리고 얼마나 밀지는
 * 서버가 증강 정의에서 읽는다.
 *
 * <p>보낸다고 뛰어지는 것도 아니다. 팀이 그 증강을 가졌는지, 정말 공중인지, 이번 공중에서
 * 이미 썼는지를 {@link com.sharedfate.perk.PerkClientRules} 가 모두 다시 확인하고,
 * 하나라도 어긋나면 조용히 버린다.
 */
public record DoubleJumpPayload() implements CustomPacketPayload {
	/** 내용이 없으므로 인스턴스도 하나면 된다. */
	public static final DoubleJumpPayload INSTANCE = new DoubleJumpPayload();

	public static final Type<DoubleJumpPayload> TYPE = new Type<>(SharedFateMod.id("double_jump_c2s"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DoubleJumpPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
