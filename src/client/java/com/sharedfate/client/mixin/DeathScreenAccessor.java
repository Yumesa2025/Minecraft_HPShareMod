package com.sharedfate.client.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
	@Accessor("hardcore")
	@Mutable
	void sharedfate$setHardcore(boolean hardcore);
}
