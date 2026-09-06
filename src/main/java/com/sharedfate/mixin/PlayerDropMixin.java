package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.perk.PerkLegacyGear;
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
		Player self = (Player) (Object) this;
		// 「유산」은 바닐라가 공유 인벤토리를 쏟기 전에 스냅샷을 떠야 한다. 이 자리를 지나면
		// InventoryMixin 이 갈아 끼운 mainItems 36칸이 통째로 비어 도구도 무기도 남지 않는다.
		PerkLegacyGear.captureBeforeDrop(self);
		if (DeathHandler.shouldDrop(self)) {
			original.call(inventory);
		}
	}
}
