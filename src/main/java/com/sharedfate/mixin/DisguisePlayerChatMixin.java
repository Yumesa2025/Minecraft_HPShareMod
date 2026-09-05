package com.sharedfate.mixin;

import com.sharedfate.chat.UnsignedChatRelay;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 플레이어 채팅을 「위장 채팅」으로 바꿔 보낸다.
 *
 * <h2>왜 이 자리인가</h2>
 * <p>{@link SecureChatClaimMixin} 이 로그인 패킷에 「서명을 강제한다」고 적어 보낸 뒤로는
 * 클라이언트가 서명 없는 {@code ClientboundPlayerChatPacket} 을 <b>통째로 거부</b>한다.
 * 그러니 그 패킷을 아예 만들지 말아야 한다.
 *
 * <p>26.2 에서 그 패킷을 만드는 자리는 {@code sendPlayerChatMessage} 하나뿐이다. 채팅도,
 * {@code /say} 도, {@code /me} 도, {@code /msg} 도 모두
 * {@code PlayerList.broadcastChatMessage} → {@code ServerPlayer.sendChatMessage} →
 * {@code OutgoingChatMessage.Player.sendToPlayer} 를 지나 여기로 모인다. 받는 사람마다 한 번씩
 * 불리고, 걸러내기({@code filter})도 이미 끝난 뒤다.
 *
 * <p>대신 보내는 {@code ClientboundDisguisedChatPacket} 은 {@code ChatType.Bound} 를 그대로
 * 싣는다. 클라이언트가 {@code Bound.decorate} 로 꾸미므로 <b>바닐라와 똑같은
 * {@code <이름> 내용}</b> 이 나온다 — 형식을 우리가 만들지 않는다.
 *
 * <h2>무엇이 달라지나</h2>
 * <p>표시는 사실상 같다. 서명 없는 채팅에 붙던 {@code CHAT_NOT_SECURE} 표와 위장 채팅에
 * 붙는 {@code SYSTEM} 표는 아이콘이 둘 다 없고 표시색도 같은 {@code 0xD0D0D0} 이라, 마우스를
 * 올렸을 때 나오는 설명만 바뀐다.
 *
 * <p>대신 <b>사회적 상호작용의 「메시지 숨기기」가 이 채팅에는 듣지 않는다.</b> 바닐라
 * 클라이언트가 {@code Minecraft.isBlocked} 를 플레이어 채팅 갈래에서만 보기 때문이다.
 * 채팅 신고는 어차피 서명이 없어 원래부터 되지 않았다.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DisguisePlayerChatMixin {

	@Inject(method = "sendPlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;"
			+ "Lnet/minecraft/network/chat/ChatType$Bound;)V",
			at = @At("HEAD"), cancellable = true)
	private void sharedfate$sendAsDisguised(PlayerChatMessage message, ChatType.Bound bound,
			CallbackInfo info) {
		ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
		ServerPlayer receiver = self.player;
		if (receiver == null || !UnsignedChatRelay.active(receiver.level().getServer())) {
			return;
		}

		info.cancel();
		Component content = UnsignedChatRelay.visibleContent(message);
		if (content == null) {
			// 통째로 걸러진 메시지. 바닐라도 이때는 아무것도 보내지 않는다.
			return;
		}
		self.sendDisguisedChatMessage(content, bound);
	}
}
