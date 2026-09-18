package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 누가 어떤 판의 클라이언트로 들어와 있는지 기억한다.
 *
 * <h2>왜 기억하는가</h2>
 * <p>{@link ClientVersionPayload} 는 원래 <b>로그에만</b> 적고 버렸다. 그런데 로그는 서버를 켤
 * 수 있는 사람만 볼 수 있고, 정작 「내 판이 몇이냐」가 궁금한 사람은 게임 안에 있다. 접속이
 * 막히지는 않는데 화면 한쪽이 안 뜨거나 값이 이상할 때, 그것이 <b>낡은 클라이언트라서인지</b>
 * 아니면 진짜 버그인지 가르는 것이 언제나 첫 단계였다. 그 물음에 게임 안에서 답할 수 있게
 * {@code /shareteam version} 이 읽어 갈 자리를 둔다.
 *
 * <h2>이 값으로는 아무 판단도 하지 않는다</h2>
 * <p>{@link ClientVersionPayload} 의 규칙 그대로다 — <b>막는 일은 규약 번호가 한다</b>
 * ({@link SharedFateNetworking#PROTOCOL_VERSION}). 여기 적힌 문자열은 밖에서 온 값이라
 * 얼마든지 거짓일 수 있고, 그것을 믿고 무언가를 허용하거나 막으면 그 자리가 곧 구멍이 된다.
 * <b>사람이 읽고 판단하라고 보여 주기만 한다.</b>
 *
 * <h2>모르는 것과 낡은 것은 다르다</h2>
 * <p>버전이 {@code null} 이라고 해서 「모드가 없다」는 뜻이 아니다. 이 패킷은 0.25.4-dev 에서
 * 생겼으므로 <b>그보다 낡은 클라이언트는 애초에 보내지 않는다.</b> 규약이 맞아 접속에 성공한
 * 사람이 여기에 없다면 그 자체가 단서다 — 그래서 「(알 수 없음)」으로 보여 주고 지우지 않는다.
 */
public final class ClientVersionRegistry {
	/** 접속 중인 사람의 UUID → 그가 알려 준 판. 저장하지 않는다 — 접속마다 다시 받는다. */
	private static final Map<UUID, String> VERSIONS = new HashMap<>();

	/** 클라이언트가 자기 판을 알려 주지 않았을 때 대신 보여 줄 문구. */
	public static final String UNKNOWN = "(알 수 없음)";

	private ClientVersionRegistry() {
	}

	/** 클라이언트가 알려 준 판을 적어 둔다. 이미 적힌 값이 있으면 덮어쓴다. */
	public static void remember(UUID playerId, String version) {
		if (playerId == null) {
			return;
		}
		VERSIONS.put(playerId, version);
	}

	/** 이 사람이 알려 준 판. 알려 준 적이 없으면 {@code null}. */
	public static @Nullable String versionOf(UUID playerId) {
		return playerId == null ? null : VERSIONS.get(playerId);
	}

	/** 화면에 그대로 쓸 수 있는 판 문자열. 모르면 {@link #UNKNOWN}. */
	public static String displayVersionOf(@Nullable UUID playerId) {
		String version = playerId == null ? null : VERSIONS.get(playerId);
		return version == null || version.isBlank() ? UNKNOWN : version;
	}

	/** 나갈 때 지운다. 남겨 두면 다시 들어오지 않은 사람의 옛 판이 목록에 계속 뜬다. */
	public static void forget(UUID playerId) {
		if (playerId != null) {
			VERSIONS.remove(playerId);
		}
	}

	/** 서버가 멈출 때 전부 버린다. */
	public static void reset() {
		VERSIONS.clear();
	}

	/**
	 * 이 서버가 돌리고 있는 모드의 판.
	 *
	 * <p>{@code fabric.mod.json} 에 적힌 값을 그대로 읽는다. 어딘가에 문자열로 한 번 더 적어
	 * 두면 판을 올릴 때 한쪽만 고쳐 <b>서버가 자기 판을 잘못 말하는</b> 일이 생긴다.
	 */
	public static String serverModVersion() {
		return FabricLoader.getInstance()
				.getModContainer(SharedFateMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse(UNKNOWN);
	}

}
