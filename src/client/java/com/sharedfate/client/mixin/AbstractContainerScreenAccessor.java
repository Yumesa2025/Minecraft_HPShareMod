package com.sharedfate.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("imageHeight")
	int sharedfate$getImageHeight();

	@Accessor("imageHeight")
	@Mutable
	void sharedfate$setImageHeight(int imageHeight);

	@Accessor("leftPos")
	int sharedfate$getLeftPos();

	@Accessor("leftPos")
	void sharedfate$setLeftPos(int leftPos);

	@Accessor("topPos")
	int sharedfate$getTopPos();
}
