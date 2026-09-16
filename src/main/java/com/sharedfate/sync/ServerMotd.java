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
 * <h2>문구 전체를 만들지 않고 숫자만 바꾼다</h2>
 *
 * <p>MOTD 는 서버를 여는 사람이 정하는 글이다. 색·줄바꿈·문안을 모드가 통째로 만들어 버리면
 * 그 사람이 쓴 글이 사라진다. 그래서 <b>「증강 N개」라는 모양만 찾아 N 을 갈아 끼운다.</b>
 * 그 모양이 없으면 아무것도 하지 않는다.
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

	private ServerMotd() {
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
		SharedFateMod.LOGGER.info("서버 설명의 증강 개수를 {}개로 맞췄습니다.", PerkRegistry.all().size());
	}

	/**
	 * MOTD 안의 「증강 N개」를 실제 개수로 갈아 끼운다.
	 *
	 * <p>마인크래프트를 하나도 쓰지 않는 순수 함수라 서버 없이 그대로 시험할 수 있다.
	 *
	 * @return 바꾼 문자열. 바꿀 자리가 없거나 입력이 없으면 {@code null}
	 */
	static @Nullable String withPerkCount(@Nullable String motd, int perkCount) {
		if (motd == null || motd.isEmpty() || perkCount <= 0) {
			return null;
		}
		Matcher matcher = PERK_COUNT.matcher(motd);
		if (!matcher.find()) {
			// 서버를 여는 사람이 제 문구를 쓴 것이다. 남의 글을 지어내지 않는다.
			return null;
		}
		return matcher.replaceAll(Matcher.quoteReplacement("증강 " + perkCount + "개"));
	}
}
