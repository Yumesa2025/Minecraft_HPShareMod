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

		int heartRows = Math.max(1,
				(int) Math.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 20.0F));
		int rowSpacing = Math.max(10 - (heartRows - 2), 3);
		int y = graphics.guiHeight() - 39 - (heartRows - 1) * rowSpacing - 10;
		if (player.getArmorValue() > 0) {
			y -= 10;
		}
		int x = graphics.guiWidth() / 2 - 91;
		for (String name : ALERTS.keySet()) {
			graphics.text(client.font, name + " 피격", x, y, 0xFFFF5555);
			y -= 10;
		}
	}
}
