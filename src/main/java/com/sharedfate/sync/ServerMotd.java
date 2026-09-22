package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkRegistry;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 서버 목록에 뜨는 설명(MOTD)의 <b>증강 개수만</b> 실제 값으로 갈아 끼운다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>개수를 손으로 적어 두면 판을 올릴 때마다 낡는다. 실제로 증강이 94개인데 MOTD 에는
 * 「증강 82개」가 몇 판째 남아 있었다. 배포 절차에 「MOTD 고치기」를 한 줄 더 넣는 것은
 * <b>지금 문제의 원인과 똑같은 종류의 실패</b>다 — 사람이 기억해야 하는 단계는 언젠가 빠진다.
 *
 * <h2>사람이 쓴 글은 건드리지 않는다. 안 쓴 서버에만 채운다</h2>
 *
 * <p>MOTD 는 서버를 여는 사람이 정하는 글이다. 색·줄바꿈·문안을 모드가 통째로 만들어 버리면
 * 그 사람이 쓴 글이 사라진다. 그래서 갈래를 <b>둘</b>로 둔다.
 *
 * <ul>
 *   <li><b>사람이 쓴 글이 있으면</b> — 「증강 N개」라는 모양만 찾아 N 을 갈아 끼운다. 그 모양이
 *       없으면 아무것도 하지 않는다. 남의 글을 지어내지 않는다.</li>
 *   <li><b>아무도 안 썼으면</b>(바닐라 기본값 {@value #VANILLA_DEFAULT} 이거나 비어 있으면) —
 *       {@link #defaultMotd} 를 넣는다. 받아서 자기 서버를 여는 사람이 <b>아무것도 안 해도</b>
 *       무슨 서버인지 목록에 뜬다.</li>
 * </ul>
 *
 * <p>둘째 갈래가 0.29.1-dev 에 생겼다. 그전에는 문구를 적어 둔 서버에서만 동작해서, 받아서 연
 * 서버에는 「A Minecraft Server」가 그대로 떴다.
 *
 * <h2>⚠ 이 호출은 파일을 쓴다</h2>
 *
 * <p>{@code DedicatedServer.setMotd} 는 {@code DedicatedServerSettings.update} 를 거쳐
 * {@code server.properties} 를 <b>그 자리에서 저장한다.</b> 그래서 기본값이 꺼짐이고
 * ({@code SharedFateConfig.overrideServerMotd}), 값이 이미 맞으면 부르지 않는다 — 회차마다
 * 재시작하는 서버에서 매번 파일을 다시 쓸 이유가 없다.
 *
 * <p>저장은 바닐라의 UTF-8 writer 가 하므로 한글과 {@code §} 가 깨지지 않는다. 사람이
 * 인코딩을 신경 쓸 일이 없는 것이 스크립트로 고치는 길보다 나은 가장 큰 이유다.
 *
 * <h2>싱글플레이는 건드리지 않는다</h2>
 *
 * <p>통합 서버에는 서버 목록도 {@code server.properties} 도 없다.
 */
public final class ServerMotd {

	/** 「증강 82개」처럼 <b>숫자만</b> 집어내는 자리. 앞뒤 글자는 그대로 둔다. */
	private static final Pattern PERK_COUNT = Pattern.compile("증강 \\d+개");

	/**
	 * 바닐라가 새 서버에 적어 두는 문구.
	 *
	 * <p>이 값이면 「아무도 안 썼다」로 본다. 마인크래프트는 판이 바뀌어도 이 문자열을 그대로
	 * 쓰고 번역하지도 않는다.
	 */
	static final String VANILLA_DEFAULT = "A Minecraft Server";

	/** 아무도 안 쓴 서버에 넣을 문구의 앞부분. 뒤에 「증강 N개」가 붙는다. */
	private static final String DEFAULT_PREFIX = "SharedFate · 체력·허기·인벤 공유 · ";

	private ServerMotd() {
	}

	/**
	 * 아무도 MOTD 를 안 쓴 서버에 넣을 문구.
	 *
	 * <p>한 줄이다. 두 줄로 만들면 서버 목록에서 줄이 잘리는 클라이언트가 있고, 무엇보다
	 * <b>이 글의 목적은 「무슨 서버인지 한눈에」</b>라 길어질 이유가 없다.
	 *
	 * <p>끝을 「증강 N개」로 맞춰 두는 것이 중요하다. 그래야 다음 판에서 증강이 늘었을 때
	 * {@link #PERK_COUNT} 가 <b>자기가 쓴 글도</b> 알아보고 숫자를 따라간다.
	 */
	static String defaultMotd(int perkCount) {
		return DEFAULT_PREFIX + "증강 " + perkCount + "개";
	}

	/** {@code ServerLifecycleEvents.SERVER_STARTED} 에서 부른다. */
	public static void onServerStarted(@Nullable MinecraftServer server) {
		if (server == null || SharedFateMod.config == null
				|| !SharedFateMod.config.overrideServerMotd
				|| !server.isDedicatedServer()) {
			return;
		}
		String current = server.getMotd();
		String updated = withPerkCount(current, PerkRegistry.all().size());
		if (updated == null || updated.equals(current)) {
			return;
		}
		server.setMotd(updated);
		// 첫 상태 묶음이 이미 만들어졌더라도 다음 핑에서 새 값을 쓰게 한다.
		server.invalidateStatus();
		SharedFateMod.LOGGER.info("서버 설명(MOTD)을 맞췄습니다: {}", updated);
	}

	/**
	 * 이 서버가 지금 띄워야 할 MOTD.
	 *
	 * <p>마인크래프트를 하나도 쓰지 않는 순수 함수라 서버 없이 그대로 시험할 수 있다.
	 *
	 * <ul>
	 *   <li>아무도 안 썼으면 → {@link #defaultMotd}</li>
	 *   <li>사람이 썼고 「증강 N개」가 있으면 → 그 숫자만 갈아 끼운 글</li>
	 *   <li>사람이 썼는데 그 모양이 없으면 → {@code null}(아무것도 하지 않는다)</li>
	 * </ul>
	 *
	 * @return 띄울 문자열. 건드릴 것이 없으면 {@code null}
	 */
	static @Nullable String withPerkCount(@Nullable String motd, int perkCount) {
		if (perkCount <= 0) {
			// 증강을 하나도 못 읽은 서버다. 「증강 0개」를 광고하느니 가만히 있는다.
			return null;
		}
		if (motd == null || motd.isBlank() || VANILLA_DEFAULT.equals(motd.strip())) {
			return defaultMotd(perkCount);
		}
		Matcher matcher = PERK_COUNT.matcher(motd);
		if (!matcher.find()) {
			// 서버를 여는 사람이 제 문구를 쓴 것이다. 남의 글을 지어내지 않는다.
			return null;
		}
		return matcher.replaceAll(Matcher.quoteReplacement("증강 " + perkCount + "개"));
	}
}
