package com.sharedfate.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("title")
	@Mutable
	void sharedfate$setTitle(Component title);

	@Invoker("rebuildWidgets")
	void sharedfate$rebuildWidgets();
}
