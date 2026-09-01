package com.sharedfate.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
	@Invoker("moveItemStackTo")
	boolean sharedfate$invokeMoveItemStackTo(
			ItemStack stack, int startIndex, int endIndex, boolean reverseDirection);

	/** 창을 닫을 때 그릇을 비워 플레이어에게 돌려줍니다. */
	@Invoker("clearContainer")
	void sharedfate$invokeClearContainer(Player player, Container container);
}
