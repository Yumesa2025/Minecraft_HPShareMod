package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sharedfate.chat.UnsignedChatRelay;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 로그인 패킷에 「이 서버는 서명을 강제한다」고 적어 보낸다.
 *
 * <h2>왜 이 자리인가</h2>
 * <p>「대화 메시지를 검증할 수 없습니다」 토스트는 26.2 클라이언트의
 * {@code ClientPacketListener.handleLogin} 이 딱 한 번 띄운다. 조건은 셋인데
 * ({@code serverData != null}, 아직 안 본 것, {@code !enforcesSecureChat()}) 우리가 손댈 수
 * 있는 것은 마지막 하나뿐이고, 그 값은 서버가 보낸
 * {@code ClientboundLoginPacket.enforcesSecureChat} 이다.
 *
 * <p>26.2 에서 그 패킷을 만드는 자리는 {@code PlayerList.placeNewPlayer} 하나뿐이고, 거기서
 * {@code MinecraftServer.enforceSecureProfile()} 을 <b>딱 한 번</b> 부른다. 그래서 그 호출의
 * 결괏값만 바꾼다 — 게임룰이나 설정을 건드리지 않으므로 서버에 남는 흔적이 없고,
 * {@code enforceSecureProfile()} 의 다른 쓰임(키 없는 접속을 끊을지 정하는 곳)은 그대로다.
 *
 * <p>참으로 말한 이상 {@code ClientboundPlayerChatPacket} 은 한 장도 나가면 안 된다 —
 * 클라이언트가 그것을 통째로 거부하기 때문이다. 그 짝이
 * {@link DisguisePlayerChatMixin} 이다. <b>둘은 반드시 함께 있어야 한다.</b>
 *
 * <p>{@link com.sharedfate.chat.UnsignedChatRelay#active} 가 거짓인 곳 — 싱글플레이와, 정품
 * 인증으로 서명을 실제로 강제하는 서버 — 에서는 바닐라 값을 그대로 돌려주므로 아무것도
 * 바뀌지 않는다.
 *
 * @see com.sharedfate.chat.UnsignedChatRelay
 */
@Mixin(PlayerList.class)
public abstract class SecureChatClaimMixin {

	@ModifyExpressionValue(method = "placeNewPlayer(Lnet/minecraft/network/Connection;"
			+ "Lnet/minecraft/server/level/ServerPlayer;"
			+ "Lnet/minecraft/server/network/CommonListenerCookie;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/MinecraftServer;enforceSecureProfile()Z"))
	private boolean sharedfate$claimSecureChat(boolean vanilla) {
		PlayerList self = (PlayerList) (Object) this;
		return vanilla || UnsignedChatRelay.active(self.getServer());
	}
}
