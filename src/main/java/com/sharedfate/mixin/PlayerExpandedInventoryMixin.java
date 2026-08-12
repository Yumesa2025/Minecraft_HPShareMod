package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerExpandedInventoryMixin {
	@Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
	private void sharedfate$findProjectileInExtra(
			ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
		Player player = (Player) (Object) this;
		if (!cir.getReturnValue().isEmpty()
				|| !(player instanceof ServerPlayer)
				|| !ExpandedInventoryManager.enabled()
				|| !(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) {
			return;
		}
		var predicate = projectileWeapon.getAllSupportedProjectiles();
		for (ItemStack stack : ExpandedInventoryManager.extraFor(player).getItems()) {
			if (predicate.test(stack)) {
				cir.setReturnValue(stack);
				return;
			}
		}
	}
}
