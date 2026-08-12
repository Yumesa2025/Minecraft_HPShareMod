package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;

public final class ClientModGate {
	private ClientModGate() {
	}

	public static void register() {
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (!ServerConfigurationNetworking.canSend(handler, HandshakePayload.TYPE)) {
				if (SharedFateMod.config.requireClientMod
						|| SharedFateMod.config.mainInventoryRows == 6) {
					handler.disconnect(Component.literal(
							"이 서버는 SharedFate 모드가 필요합니다.\n"
									+ "클라이언트에 모드를 설치한 뒤 다시 접속해 주세요."));
				}
				return;
			}
			ServerConfigurationNetworking.send(handler, HandshakePayload.current());
		});
	}
}
