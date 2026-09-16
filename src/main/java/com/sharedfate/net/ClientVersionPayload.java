package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — 접속한 클라이언트가 <b>자기 모드 판</b>을 한 번 알린다. 서버는 로그에만 적는다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>규약 번호는 <b>맞는가 틀리는가</b>만 말해 준다. 같은 규약 안에서 판이 여럿일 수 있고
 * ({@code 0.25.0}~{@code 0.25.2} 가 전부 규약 24였다), 그러면 <b>서버 로그만 봐서는 누가 어떤
 * 클라이언트를 쓰는지 알 방법이 없다.</b>
 *
 * <p>한 사람만 화면 왼쪽 위가 안 보이는 일이 있었는데, 원인을 좁히려면 그 사람의 클라이언트
 * 판을 알아야 했고 결국 <b>로그 파일을 직접 받아서야</b> 알 수 있었다. 이제 접속 한 줄로
 * 끝난다.
 *
 * <pre>
 * [CLIENT] 플레이어1 — sharedfate 0.25.3-dev
 * </pre>
 *
 * <h2>이 값으로 아무 판단도 하지 않는다</h2>
 *
 * <p>막는 일은 규약 번호가 한다({@link SharedFateNetworking#PROTOCOL_VERSION}). 이것은
 * <b>사람이 읽는 기록</b>일 뿐이라, 클라이언트가 거짓을 보내도 잃을 것이 없다. 판단에 쓰기
 * 시작하면 그 순간 믿을 수 없는 값으로 분기하는 것이 된다.
 *
 * <p>플레이 단계에서 보낸다. 악수는 설정 단계라 아직 플레이어가 없어 이름을 적을 수 없다.
 */
public record ClientVersionPayload(String version) implements CustomPacketPayload {
	/** 로그 한 줄에 들어갈 만한 길이로 자른다. 밖에서 온 문자열을 그대로 믿지 않는다. */
	public static final int MAX_LENGTH = 64;

	public static final Type<ClientVersionPayload> TYPE =
			new Type<>(SharedFateMod.id("client_version"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientVersionPayload> CODEC =
			ByteBufCodecs.stringUtf8(MAX_LENGTH)
					.map(ClientVersionPayload::new, ClientVersionPayload::version)
					.cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
