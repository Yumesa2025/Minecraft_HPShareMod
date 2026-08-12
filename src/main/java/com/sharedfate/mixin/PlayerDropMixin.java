package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.sync.DeathHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerDropMixin {
	@WrapOperation(method = "dropEquipment",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V"))
	private void sharedfate$dropOnlyOnce(Inventory inventory, Operation<Void> original) {
		if (DeathHandler.shouldDrop((Player) (Object) this)) {
			original.call(inventory);
		}
	}
}
