package com.sharedfate.client.hud;

import com.sharedfate.client.ClientTeamState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HotbarHighlight implements HudElement {
	private static final int RED = 0xFFFF3030;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientTeamState.inTeam()) {
			return;
		}
		int center = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - 22;
		for (int slot = 0; slot < 9; slot++) {
			if (ClientTeamState.isAllyUsingHotbarSlot(slot)) {
				graphics.outline(center - 91 + slot * 20 + 1, y, 20, 22, RED);
			}
		}
	}
}
