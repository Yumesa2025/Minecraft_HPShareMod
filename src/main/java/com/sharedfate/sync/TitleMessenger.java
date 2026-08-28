package com.sharedfate.sync;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * 화면 중앙 타이틀과 액션바를 바닐라 패킷으로 직접 보낸다.
 *
 * <p>타이틀·액션바는 바닐라가 이미 갖고 있는 기능이라 커스텀 페이로드와 클라이언트 화면 코드가
 * 필요 없다. 서버에서 패킷만 보내면 바닐라 클라이언트도, 이 모드를 깐 클라이언트도 똑같이 본다.
 */
public final class TitleMessenger {
	private TitleMessenger() {
	}

	/**
	 * 화면 중앙에 큰 글씨(타이틀)와 그 아래 작은 글씨(부제)를 띄운다.
	 *
	 * <p>바닐라 {@code /title} 과 같은 순서로 보낸다. 애니메이션 → 부제 → 타이틀 순이어야
	 * 마지막 타이틀 패킷을 받은 시점에 지정한 페이드 값으로 함께 표시된다.
	 */
	public static void showTitle(ServerPlayer player, Component title, Component subtitle,
			int fadeInTicks, int stayTicks, int fadeOutTicks) {
		if (!canReceive(player)) {
			return;
		}
		player.connection.send(new ClientboundSetTitlesAnimationPacket(
				Math.max(0, fadeInTicks), Math.max(0, stayTicks), Math.max(0, fadeOutTicks)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				subtitle == null ? Component.empty() : subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** 여러 명에게 같은 타이틀을 띄운다. */
	public static void showTitle(Collection<ServerPlayer> players, Component title, Component subtitle,
			int fadeInTicks, int stayTicks, int fadeOutTicks) {
		for (ServerPlayer player : players) {
			showTitle(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
		}
	}

	/**
	 * 단축바 위(액션바)에 한 줄을 띄운다.
	 *
	 * <p>매초 갱신되는 카운트다운처럼 자주 바뀌는 문구에 쓴다. 타이틀과 달리 페이드 애니메이션이
	 * 다시 시작되지 않아 깜빡이지 않고, 화면 중앙(조준점)을 가리지도 않는다.
	 */
	public static void showActionBar(ServerPlayer player, Component text) {
		if (!canReceive(player)) {
			return;
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(text));
	}

	/** 여러 명에게 같은 액션바 문구를 띄운다. */
	public static void showActionBar(Collection<ServerPlayer> players, Component text) {
		for (ServerPlayer player : players) {
			showActionBar(player, text);
		}
	}

	private static boolean canReceive(ServerPlayer player) {
		return player != null && player.connection != null && !player.isRemoved();
	}
}
