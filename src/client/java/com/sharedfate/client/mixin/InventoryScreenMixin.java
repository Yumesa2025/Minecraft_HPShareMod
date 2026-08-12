package com.sharedfate.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.sharedfate.client.ClientTeamState;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@Unique
	private static final int VANILLA_HEIGHT = 166;
	@Unique
	private static final int EXPANDED_HEIGHT = 220;
	@Unique
	private static final int EXTRA_HEIGHT = EXPANDED_HEIGHT - VANILLA_HEIGHT;

	@Unique
	private static final int INVENTORY_TOP_HEIGHT = 137;
	@Unique
	private static final int ROW_SOURCE_Y = 119;
	@Unique
	private static final int ROW_HEIGHT = 18;
	@Unique
	private static final int SEPARATOR_SOURCE_Y = 137;
	@Unique
	private static final int SEPARATOR_HEIGHT = 4;
	@Unique
	private static final int HOTBAR_SOURCE_Y = 141;
	@Unique
	private static final int HOTBAR_HEIGHT = 25;

	@Unique
	private static final int RECIPE_BUTTON_X = 104;
	@Unique
	private static final int RECIPE_BUTTON_Y = 61;

	@Inject(method = "init", at = @At("HEAD"))
	private void sharedfate$selectImageHeight(CallbackInfo ci) {
		sharedfate$screen().sharedfate$setImageHeight(
				sharedfate$expanded() ? EXPANDED_HEIGHT : VANILLA_HEIGHT);
	}

	@Inject(method = "containerTick", at = @At("HEAD"))
	private void sharedfate$refreshDynamicLayout(CallbackInfo ci) {
		int desiredHeight = sharedfate$expanded() ? EXPANDED_HEIGHT : VANILLA_HEIGHT;
		AbstractContainerScreenAccessor screen = sharedfate$screen();
		if (screen.sharedfate$getImageHeight() == desiredHeight) {
			return;
		}
		screen.sharedfate$setImageHeight(desiredHeight);
		Screen self = (Screen) (Object) this;
		self.resize(self.width, self.height);
	}

	@Inject(method = "getRecipeBookButtonPosition", at = @At("HEAD"), cancellable = true)
	private void sharedfate$moveRecipeBookButton(
			CallbackInfoReturnable<ScreenPosition> cir) {
		if (!sharedfate$expanded()) {
			return;
		}
		AbstractContainerScreenAccessor screen = sharedfate$screen();
		cir.setReturnValue(new ScreenPosition(
				screen.sharedfate$getLeftPos() + RECIPE_BUTTON_X,
				screen.sharedfate$getTopPos() + RECIPE_BUTTON_Y));
	}

	@WrapOperation(
			method = "extractBackground",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
			)
	)
	private void sharedfate$drawExpandedBackground(
			GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture,
			int x, int y, float u, float v, int width, int height,
			int textureWidth, int textureHeight, Operation<Void> original) {
		if (!sharedfate$expanded()) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height,
					textureWidth, textureHeight);
			return;
		}

		original.call(graphics, pipeline, texture, x, y, u, v, width,
				INVENTORY_TOP_HEIGHT, textureWidth, textureHeight);
		for (int row = 0; row < 3; row++) {
			original.call(graphics, pipeline, texture,
					x, y + INVENTORY_TOP_HEIGHT + row * ROW_HEIGHT,
					u, (float) ROW_SOURCE_Y, width, ROW_HEIGHT, textureWidth, textureHeight);
		}
		original.call(graphics, pipeline, texture,
				x, y + INVENTORY_TOP_HEIGHT + EXTRA_HEIGHT,
				u, (float) SEPARATOR_SOURCE_Y, width, SEPARATOR_HEIGHT,
				textureWidth, textureHeight);
		original.call(graphics, pipeline, texture,
				x, y + HOTBAR_SOURCE_Y + EXTRA_HEIGHT,
				u, (float) HOTBAR_SOURCE_Y, width, HOTBAR_HEIGHT,
				textureWidth, textureHeight);
	}

	private static boolean sharedfate$expanded() {
		return ExpandedInventoryManager.enabled() && ClientTeamState.inTeam();
	}

	private AbstractContainerScreenAccessor sharedfate$screen() {
		return (AbstractContainerScreenAccessor) this;
	}
}
