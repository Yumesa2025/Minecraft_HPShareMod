package com.sharedfate.mixin;

import com.sharedfate.inventory.CreativeInventoryLayout;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeModeSlotPacketMixin {
	@Shadow
	@Final
	private ServerPlayer player;

	@ModifyConstant(
			method = "handleSetCreativeModeSlot",
			constant = @Constant(intValue = 45)
	)
	private int sharedfate$acceptExpandedCreativeInventorySlots(int vanillaMaximum) {
		boolean expandedActive = ExpandedInventoryManager.enabled()
				&& player.inventoryMenu.slots.size()
						>= ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE
				&& ExpandedInventoryManager.extraFor(player).active();
		return CreativeInventoryLayout.maximumServerSlot(expandedActive);
	}
}
