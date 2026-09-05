package com.sharedfate.chat;

import com.sharedfate.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「대화 메시지를 검증할 수 없습니다」 경고를 끄는 두 믹스인이 <b>무는 자리</b>를 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상이 틀려도 <b>빌드가 그냥
 * 통과</b>하고 사람이 접속하거나 말을 거는 순간에야 터진다. 그래서 서술자만이라도 여기서
 * 붙들어 둔다.
 *
 * <p>대상 둘은 <b>짝</b>이다. {@code SecureChatClaimMixin} 이 로그인 패킷에 「서명을
 * 강제한다」고 적어 보내는 순간부터 클라이언트는 서명 없는 플레이어 채팅 패킷을 통째로
 * 거부하므로, {@code DisguisePlayerChatMixin} 이 그 패킷을 하나도 못 나가게 막아야 한다.
 * 한쪽이 빠지면 채팅이 아예 안 보이거나 접속이 끊긴다.
 */
class UnsignedChatRelayTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- 믹스인이 무는 자리

	/**
	 * {@code SecureChatClaimMixin} 이 파고드는 메서드와, 그 안에서 값을 가로챌 호출.
	 *
	 * <p>{@code placeNewPlayer} 는 26.2 에서 {@code ClientboundLoginPacket} 을 만드는 유일한
	 * 자리이고, 거기서 {@code enforceSecureProfile()} 을 딱 한 번 불러 그 결과를 패킷의 마지막
	 * 칸에 넣는다. <b>서술자가 바뀌면 사람이 접속하는 순간 믹스인이 못 붙는다.</b>
	 */
	@Test
	void 로그인_패킷을_만드는_자리가_그대로_있다() {
		method(PlayerList.class, "placeNewPlayer",
				Connection.class, ServerPlayer.class, CommonListenerCookie.class);

		Method enforce = method(MinecraftServer.class, "enforceSecureProfile");
		assertEquals(boolean.class, enforce.getReturnType(),
				"반환형이 boolean 이 아니면 @ModifyExpressionValue 의 서명이 맞지 않는다");
	}

	/**
	 * 우리가 뒤집는 값이 실제로 클라이언트까지 실려 가는 칸인지.
	 *
	 * <p>클라이언트의 {@code ClientPacketListener.handleLogin} 은 이 값이 거짓일 때만 토스트를
	 * 띄운다. 이 접근자가 사라지면 경고가 뜨는 근거 자체가 바뀐 것이므로 조사를 다시 해야
	 * 한다.
	 */
	@Test
	void 로그인_패킷에_서명_강제_칸이_남아_있다() {
		assertEquals(boolean.class,
				method(ClientboundLoginPacket.class, "enforcesSecureChat").getReturnType());
	}

	/**
	 * {@code DisguisePlayerChatMixin} 이 막는 자리와, 대신 부르는 자리.
	 *
	 * <p>{@code sendPlayerChatMessage} 는 26.2 에서 {@code ClientboundPlayerChatPacket} 을
	 * 만드는 <b>유일한</b> 자리다(나머지 참조는 패킷 등록표와 클라이언트뿐). 여기가 갈라지면
	 * 서명 없는 채팅이 새 나가고, 「서명을 강제한다」고 말해 둔 클라이언트가 그것을 거부해
	 * 채팅이 통째로 사라진다.
	 */
	@Test
	void 플레이어_채팅을_내보내는_자리가_하나로_모여_있다() {
		method(ServerGamePacketListenerImpl.class, "sendPlayerChatMessage",
				PlayerChatMessage.class, ChatType.Bound.class);
		method(ServerGamePacketListenerImpl.class, "sendDisguisedChatMessage",
				Component.class, ChatType.Bound.class);
	}

	/** 믹스인이 {@code this} 를 형변환해 바로 읽는 밭. 공개가 아니게 되면 컴파일이 깨진다. */
	@Test
	void 받는_사람을_가리키는_밭이_공개로_남아_있다() {
		Field player = field(ServerGamePacketListenerImpl.class, "player");
		assertTrue(Modifier.isPublic(player.getModifiers()),
				"공개가 아니면 @Shadow 를 따로 붙여야 한다");
		assertEquals(ServerPlayer.class, player.getType());
	}

	// -------------------------------------------------- 무엇을 실어 보내는가

	/** 서명 없는 채팅은 서명된 원문이 곧 알맹이다. */
	@Test
	void 꾸민_내용이_없으면_원문을_그대로_싣는다() {
		PlayerChatMessage message = PlayerChatMessage.unsigned(UUID.randomUUID(), "안녕하세요");

		assertEquals("안녕하세요", UnsignedChatRelay.visibleContent(message).getString());
	}

	/**
	 * 서버가 꾸민 내용이 있으면 그쪽이 이긴다.
	 *
	 * <p>지금 이 모드는 채팅을 꾸미지 않지만, 나중에 팀 이름 같은 것을 붙이더라도 위장 채팅에
	 * 그 결과가 그대로 실려야 한다.
	 */
	@Test
	void 꾸민_내용이_있으면_그것을_싣는다() {
		PlayerChatMessage message = PlayerChatMessage.unsigned(UUID.randomUUID(), "안녕하세요")
				.withUnsignedContent(Component.literal("[붉은팀] 안녕하세요"));

		assertEquals("[붉은팀] 안녕하세요", UnsignedChatRelay.visibleContent(message).getString());
	}

	/** 통째로 걸러진 메시지는 보낼 것이 없다 — 바닐라도 이때는 패킷을 만들지 않는다. */
	@Test
	void 통째로_걸러진_메시지는_실을_것이_없다() {
		PlayerChatMessage message = PlayerChatMessage.unsigned(UUID.randomUUID(), "안녕하세요")
				.filter(FilterMask.FULLY_FILTERED);

		assertNull(UnsignedChatRelay.visibleContent(message),
				"null 이 아니면 걸러진 말이 그대로 나간다");
	}

	/**
	 * <b>이 시험이 서버 쪽 길을 고른 까닭이다.</b>
	 *
	 * <p>위장 채팅 패킷은 {@code ChatType.Bound} 를 그대로 싣고 클라이언트가 그것으로 꾸민다.
	 * 그래서 {@code <이름> 내용} 이라는 바닐라 꼴을 우리가 손으로 만들 필요가 없다. 이 꼴이
	 * 바뀌면 채팅이 바닐라와 달라 보이기 시작한다.
	 */
	@Test
	void 위장_채팅도_바닐라와_똑같은_이름_꼴로_보인다() {
		Holder<ChatType> chat = TestBootstrap.registries()
				.lookupOrThrow(Registries.CHAT_TYPE)
				.getOrThrow(ChatType.CHAT);
		ChatType.Bound bound =
				new ChatType.Bound(chat, Component.literal("철수"), Optional.empty());

		assertEquals("<철수> 안녕하세요",
				bound.decorate(Component.literal("안녕하세요")).getString());
	}

	// -------------------------------------------------- 언제 손대는가

	/**
	 * 전용 서버가 아니면 아무것도 하지 않는다 — 싱글플레이는 그대로다.
	 *
	 * <p>토스트는 서버 목록으로 접속했을 때만 뜨므로 싱글플레이에서는 고칠 것이 없고, 굳이
	 * 고치면 제 채팅에 회색 표시줄만 하나 더 생긴다. 나머지 절반(「서명을 실제로 강제하는
	 * 서버에서는 손대지 않는다」)은 살아 있는 {@code MinecraftServer} 없이 확인할 수 없으므로
	 * {@code DedicatedServer.enforceSecureProfile()} 한 줄
	 * ({@code enforceSecureProfile && onlineMode && canValidateProfileKeys}) 에 그대로 맡긴다.
	 */
	@Test
	void 전용_서버가_아니면_손대지_않는다() {
		assertFalse(UnsignedChatRelay.active(null));
	}

	// -------------------------------------------------- 도구

	private static Method method(Class<?> owner, String name, Class<?>... parameters) {
		try {
			return owner.getDeclaredMethod(name, parameters);
		} catch (NoSuchMethodException error) {
			throw new AssertionError(
					owner.getSimpleName() + "." + name + " 의 서술자가 바뀌었다", error);
		}
	}

	private static Field field(Class<?> owner, String name) {
		try {
			return owner.getDeclaredField(name);
		} catch (NoSuchFieldException error) {
			throw new AssertionError(owner.getSimpleName() + "." + name + " 이 사라졌다", error);
		}
	}
}
