package com.sharedfate.ui;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /shareteam version} 이 보여 줄 줄들을 만든다.
 *
 * <p>화면도 서버도 아닌 <b>순수 계산</b>만 여기 둔다. 명령 쪽은 {@code ServerPlayer} 와
 * {@code CommandSourceStack} 없이는 한 줄도 돌릴 수 없어 시험이 닿지 않는데, 정작 틀리기 쉬운
 * 것은 「무엇을 어떻게 보여 주느냐」다. 그 판단을 여기로 옮겨 놓으면 시험이 붙는다.
 *
 * <h2>답해야 하는 물음은 하나다</h2>
 * <p><b>「지금 이 사람이 낡은 클라이언트를 쓰고 있는가」.</b> 접속이 막히지 않는데 화면 한쪽이
 * 안 뜨거나 값이 이상할 때, 그것이 낡은 판 때문인지 진짜 버그인지 가르는 것이 언제나 첫
 * 단계였다. 그래서 판 두 개를 나란히 적고 <b>같은지 다른지를 글로 못박아</b> 준다 — 숫자 두
 * 줄만 던져 놓고 사람이 눈으로 견주게 하면 그 자리에서 또 틀린다.
 */
public final class VersionLines {
	/** 판이 서로 같을 때. */
	public static final String MATCH = "같은 판입니다.";
	/** 판이 다를 때. 무엇을 해야 하는지까지 적는다. */
	public static final String MISMATCH = "판이 다릅니다 — 클라이언트를 서버와 같은 판으로 바꾸십시오.";
	/**
	 * 클라이언트가 자기 판을 알려 주지 않았을 때.
	 *
	 * <p>모드가 없다는 뜻이 아니다. 자기 판을 알리는 패킷은 0.25.4-dev 에서 생겼으므로
	 * <b>그보다 낡은 클라이언트는 애초에 보내지 않는다.</b> 규약이 맞아 접속에 성공했는데도
	 * 판을 모른다면 그 자체가 단서라, 「모른다」로 끝내지 않고 그 뜻까지 적어 준다.
	 */
	public static final String UNKNOWN_CLIENT =
			"클라이언트가 자기 판을 알리지 않았습니다 — 0.25.4-dev 보다 낡은 판입니다.";

	private VersionLines() {
	}

	/**
	 * 자기 판을 묻는 {@code /shareteam version} 의 줄들.
	 *
	 * @param serverVersion  서버가 돌리는 모드의 판
	 * @param protocolVersion 통신 규약 번호
	 * @param clientVersion  이 사람이 알려 준 판. 알려 준 적이 없으면 {@code null}
	 */
	public static List<String> self(String serverVersion, int protocolVersion,
			@Nullable String clientVersion) {
		List<String> lines = new ArrayList<>(4);
		lines.add("SharedFate — 통신 규약 " + protocolVersion);
		lines.add("· 서버: " + serverVersion);
		lines.add("· 내 클라이언트: " + display(clientVersion));
		lines.add("· " + verdict(serverVersion, clientVersion));
		return lines;
	}

	/**
	 * 접속 중인 전원의 판을 묻는 {@code /shareteam version all} 의 줄들.
	 *
	 * <p>판이 서버와 <b>다른 사람을 먼저</b> 적는다. 사람이 많을수록 찾으려는 것은 언제나
	 * 「어긋난 사람」인데, 이름 순으로 늘어놓으면 그 한 줄을 눈으로 훑어 찾아야 한다.
	 *
	 * @param entries 이름과 그 사람이 알려 준 판(모르면 {@code null})의 쌍
	 */
	public static List<String> all(String serverVersion, int protocolVersion, List<Entry> entries) {
		List<String> lines = new ArrayList<>(entries.size() + 2);
		lines.add("SharedFate " + serverVersion + " — 통신 규약 " + protocolVersion);
		if (entries.isEmpty()) {
			lines.add("· 접속 중인 사람이 없습니다.");
			return lines;
		}
		List<Entry> mismatched = new ArrayList<>();
		List<Entry> matched = new ArrayList<>();
		for (Entry entry : entries) {
			if (matches(entry.version(), serverVersion)) {
				matched.add(entry);
			} else {
				mismatched.add(entry);
			}
		}
		for (Entry entry : mismatched) {
			lines.add("· " + entry.name() + ": " + display(entry.version()) + "  ← 다름");
		}
		for (Entry entry : matched) {
			lines.add("· " + entry.name() + ": " + entry.version());
		}
		if (mismatched.isEmpty()) {
			lines.add("전원이 같은 판입니다.");
		} else {
			lines.add(mismatched.size() + "명이 다른 판을 쓰고 있습니다.");
		}
		return lines;
	}

	/** 한 사람의 이름과 그가 알려 준 판. 판을 모르면 {@code version} 이 {@code null} 이다. */
	public record Entry(String name, @Nullable String version) {
	}

	/**
	 * 두 판이 같은가. <b>모르는 값은 같다고 보지 않는다.</b>
	 *
	 * <p>모르는 것을 「같다」로 답하면 낡은 클라이언트를 쓰는 사람에게 「문제 없음」이라고 말하게
	 * 된다. 이 명령을 만든 이유가 그 사람을 찾는 것이므로, 모를 때는 모른다고 한다.
	 */
	public static boolean matches(@Nullable String clientVersion, String serverVersion) {
		return clientVersion != null && !clientVersion.isBlank() && clientVersion.equals(serverVersion);
	}

	private static String display(@Nullable String version) {
		return version == null || version.isBlank() ? "(알 수 없음)" : version;
	}

	private static String verdict(String serverVersion, @Nullable String clientVersion) {
		if (clientVersion == null || clientVersion.isBlank()) {
			return UNKNOWN_CLIENT;
		}
		return clientVersion.equals(serverVersion) ? MATCH : MISMATCH;
	}
}
