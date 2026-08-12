package com.sharedfate.client;

import com.sharedfate.client.mixin.DeathScreenAccessor;
import com.sharedfate.client.mixin.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

public final class GameOverClientDisplay {
	private static int pendingTicks;
	private static int runNumber;
	private static boolean applied;

	private GameOverClientDisplay() {
	}

	public static void show(int run, int delayTicks) {
		runNumber = Math.max(1, run);
		pendingTicks = Math.max(100, delayTicks + 40);
		applied = false;
	}

	public static void tick(Minecraft client) {
		if (pendingTicks <= 0) {
			return;
		}
		pendingTicks--;
		if (applied || !(client.gui.screen() instanceof DeathScreen deathScreen)) {
			return;
		}

		((DeathScreenAccessor) deathScreen).sharedfate$setHardcore(true);
		((ScreenAccessor) deathScreen).sharedfate$setTitle(
				Component.literal("게임 오버! · " + runNumber + "회차"));
		((ScreenAccessor) deathScreen).sharedfate$rebuildWidgets();
		applied = true;
	}

	public static void clear() {
		pendingTicks = 0;
		runNumber = 0;
		applied = false;
	}
}
