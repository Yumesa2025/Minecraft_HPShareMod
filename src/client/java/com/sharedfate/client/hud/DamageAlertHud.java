package com.sharedfate.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class DamageAlertHud implements HudElement {
	private static final Map<String, Integer> ALERTS = new LinkedHashMap<>();

	public static void show(String playerName, int durationTicks) {
		if (durationTicks > 0) {
			ALERTS.put(playerName, durationTicks);
		}
	}

	public static void tick() {
		Iterator<Map.Entry<String, Integer>> iterator = ALERTS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Integer> entry = iterator.next();
			int next = entry.getValue() - 1;
			if (next <= 0) {
				iterator.remove();
			} else {
				entry.setValue(next);
			}
		}
	}

	public static void clear() {
		ALERTS.clear();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (ALERTS.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null) {
			return;
		}

		// 팀 레벨 표시가 바닥을 차지하므로 그 위에서 시작한다. 자세한 것은 BottomLeftStack.
		int x = BottomLeftStack.left(graphics);
		int y = BottomLeftStack.baseline(player, graphics.guiHeight())
				- BottomLeftStack.teamLevelLines() * BottomLeftStack.LINE_HEIGHT;
		for (String name : ALERTS.keySet()) {
			graphics.text(client.font, name + " 피격", x, y, 0xFFFF5555);
			y -= BottomLeftStack.LINE_HEIGHT;
		}
	}
}
