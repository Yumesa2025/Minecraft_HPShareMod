package com.sharedfate.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * 서명 없는 채팅을 「위장 채팅」으로 바꿔 내보내기 위한 판단 두 가지.
 *
 * <h2>무엇을 고치나</h2>
 * <p>이 서버는 {@code online-mode=false} 로 돈다. 오프라인 계정은 Mojang 이 서명한 프로필
 * 공개키가 없어서 채팅에 서명을 붙이지 못한다. 그러면 접속 직후 클라이언트가
 * 「대화 메시지를 검증할 수 없습니다」({@code multiplayer.unsecureserver.toast.title}) 토스트를
 * 띄운다.
 *
 * <p>그 토스트는 <b>서버가 로그인 패킷에 실어 보낸 한 비트</b>만 보고 뜬다 —
 * {@code ClientboundLoginPacket.enforcesSecureChat()}. 26.2 의 {@code ClientPacketListener}
 * 는 {@code handleLogin} 끝에서 이 값이 거짓이면 토스트를 한 번 띄우고 표시를 남긴다.
 * 그래서 고치는 자리는 「경고를 그리는 곳」이 아니라 <b>그 비트를 정하는 곳</b>이다.
 *
 * <h2>{@code server.properties} 로는 왜 안 되나</h2>
 * <p>{@code DedicatedServer.enforceSecureProfile()} 는
 * {@code enforceSecureProfile && onlineMode && services.canValidateProfileKeys()} 다.
 * {@code online-mode=false} 인 한 {@code enforce-secure-profile} 을 무엇으로 두든 결과는
 * 언제나 거짓이다. 설정만으로는 끌 수 없다.
 *
 * <h2>비트만 뒤집으면 왜 안 되나</h2>
 * <p>클라이언트는 그 비트가 참이면 <b>채팅 세션이 없는 상대의 서명 없는 채팅을 통째로
 * 거부</b>한다({@code PlayerInfo.fallbackMessageValidator} → {@code REJECT_ALL}). 그래서
 * 비트를 참으로 말한 서버는 {@code ClientboundPlayerChatPacket} 을 <b>한 장도 보내면 안
 * 된다.</b> 대신 같은 내용을 {@code ClientboundDisguisedChatPacket} 으로 보낸다. 이 패킷도
 * {@code ChatType.Bound} 를 그대로 싣기 때문에 클라이언트가 {@code <이름> 내용} 꼴로 똑같이
 * 꾸며 준다 — 우리가 형식을 다시 만들 필요가 없다.
 *
 * <p>서버에서 {@code ClientboundPlayerChatPacket} 을 만드는 자리는 26.2 통틀어
 * {@code ServerGamePacketListenerImpl.sendPlayerChatMessage} 하나뿐이다(그 밖의 참조는 패킷
 * 등록표와 클라이언트 쪽뿐). 그 한 자리만 막으면 새는 곳이 없다.
 */
public final class UnsignedChatRelay {

	private UnsignedChatRelay() {
	}

	/**
	 * 지금 이 서버에서 손을 대야 하는가. <b>두 믹스인이 똑같이 이것만 본다</b> — 한쪽만
	 * 켜지면 채팅이 통째로 사라지므로 판단을 나눠 두면 안 된다.
	 *
	 * <p>조건은 둘이다.
	 *
	 * <ul>
	 *   <li><b>전용 서버일 것.</b> 문제의 토스트는 서버 목록으로 접속했을 때만 뜬다
	 *       ({@code ClientPacketListener.handleLogin} 이 {@code serverData != null} 을 함께
	 *       본다). 싱글플레이는 애초에 뜨지 않으므로 건드릴 까닭이 없고, 건드리면 제 채팅에
	 *       회색 표시줄만 하나 더 생긴다.</li>
	 *   <li><b>서버가 서명을 강제하지 못할 것.</b> 강제할 수 있으면(정품 인증 + 서비스 키)
	 *       바닐라가 이미 옳다. 나중에 {@code online-mode=true} 로 바꾸면 이 판단 하나로
	 *       모드가 스스로 물러나고 채팅 신고까지 되살아난다.</li>
	 * </ul>
	 */
	public static boolean active(MinecraftServer server) {
		return server instanceof DedicatedServer && !server.enforceSecureProfile();
	}

	/**
	 * 위장 채팅에 실을 알맹이. 통째로 걸러진 메시지면 {@code null} 이고, 그때는 아무것도
	 * 보내지 않는다.
	 *
	 * <p>바닐라 클라이언트가 {@code ChatListener.showMessageToPlayer} 에서 하는 것과 같은
	 * 갈래다. 걸러진 데가 없으면 꾸며진 내용을 그대로, 있으면 서명된 원문에 가림표를 씌운다.
	 * 지금 서버는 {@code text-filtering-config} 가 비어 있어 언제나 앞쪽으로 가지만, 나중에
	 * 걸러내기를 켜더라도 표시가 어긋나지 않게 두 갈래를 다 둔다.
	 */
	public static Component visibleContent(PlayerChatMessage message) {
		FilterMask mask = message.filterMask();
		if (mask.isEmpty()) {
			return message.decoratedContent();
		}
		return mask.applyWithFormatting(message.signedContent());
	}
}
