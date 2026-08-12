package com.sharedfate.client;

import com.sharedfate.net.SelectedSlotC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class SelectedSlotReporter {
	private static int lastSent = -1;

	private SelectedSlotReporter() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null) {
			reset();
			return;
		}
		int current = client.player.getInventory().getSelectedSlot();
		if (current == lastSent) {
			return;
		}
		if (ClientPlayNetworking.canSend(SelectedSlotC2SPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectedSlotC2SPayload(current));
			lastSent = current;
		}
	}

	public static void forceResend() {
		lastSent = -1;
	}

	public static void reset() {
		lastSent = -1;
	}
}
