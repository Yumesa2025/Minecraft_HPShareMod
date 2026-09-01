package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

/**
 * 이 모드가 <b>월드가 열릴 때마다 강제로 맞추는 바닐라 게임 규칙.</b>
 *
 * <h2>왜 서버 설정 파일이 아니라 코드인가</h2>
 * <p>세 가지 이유로 코드여야 한다.
 *
 * <ol>
 *   <li><b>{@code server.properties} 에는 게임 규칙 항목이 아예 없다.</b> 게임 규칙을 파일로
 *       미리 정하는 길은 바닐라 서버에 없고, 명령({@code /gamerule})으로만 바꿀 수 있다.</li>
 *   <li><b>게임 규칙은 월드({@code level.dat})에 새겨진다.</b> 이 모드는 팀이 전멸하면 월드를
 *       통째로 지우고 새로 만든다. 사람이 한 번 {@code /gamerule} 로 꺼 두어도 <b>다음 회차의
 *       새 월드에서는 바닐라 기본값으로 되돌아간다.</b> 회차가 바뀌어도 유지되어야 한다는 것이
 *       이 요구의 핵심이므로, 월드에 한 번 적어 두는 방식으로는 답이 되지 않는다.</li>
 *   <li>서버가 뜰 때마다 코드로 맞추면 <b>새로 만든 월드든 이어 하는 월드든 언제나 꺼져
 *       있다.</b> 회차 수와 상관없이 같은 결과가 된다.</li>
 * </ol>
 *
 * <p>다만 이 모드를 쓰면서도 발전과제 알림은 보고 싶은 서버가 있을 수 있으므로, 끄는 것 자체는
 * {@code config/sharedfate.json} 의 {@code silenceAdvancementMessages} 로 되돌릴 수 있게 했다.
 * 기본값은 <b>끔(알림을 없앤다)</b> 이다.
 *
 * <h2>26.2 에서 규칙 이름이 바뀌었다</h2>
 * <p>예전의 {@code announceAdvancements} 가 26.2 에서 {@code show_advancement_messages}
 * ({@link GameRules#SHOW_ADVANCEMENT_MESSAGES})가 되었다. 발전과제를 달성했을 때 채팅에
 * 「OOO 님이 발전 과제를 달성했습니다」를 뿌릴지 정하는 값으로, {@code PlayerAdvancements} 가
 * 알림을 보내기 직전에 이 값을 본다. 끄면 달성 자체는 그대로이고 채팅 줄만 사라진다.
 */
public final class WorldGameRules {
	private WorldGameRules() {
	}

	/**
	 * 서버가 뜬 직후에 한 번 부른다.
	 *
	 * <p>이미 값이 맞으면 아무것도 하지 않는다. {@code GameRules.set} 은
	 * {@code MinecraftServer.onGameRuleChanged} 를 거쳐 접속자에게 알림이 나가는 규칙도 있으므로,
	 * 바꿀 것이 없을 때 굳이 부르지 않는다.
	 */
	public static void onServerStarted(@Nullable MinecraftServer server) {
		if (server == null || !silenceAdvancementMessages()) {
			return;
		}
		try {
			GameRules rules = server.getGameRules();
			if (!rules.get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
				return;
			}
			rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
			SharedFateMod.LOGGER.info(
					"[RULE] 발전과제 달성 알림을 껐습니다 (show_advancement_messages=false). "
							+ "회차가 바뀌어 월드가 새로 만들어져도 서버가 뜰 때마다 다시 끕니다.");
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("발전과제 달성 알림을 끄지 못했습니다.", error);
		}
	}

	/** 설정을 읽지 못하는 상황(시험 등)에서도 기본값은 「끈다」이다. */
	static boolean silenceAdvancementMessages() {
		return SharedFateMod.config == null || SharedFateMod.config.silenceAdvancementMessages;
	}
}
