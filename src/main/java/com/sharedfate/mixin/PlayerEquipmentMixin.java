package com.sharedfate.mixin;

import com.sharedfate.team.TeamAwareEquipment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEquipmentMixin {
	@Inject(method = "createEquipment", at = @At("HEAD"), cancellable = true)
	private void sharedfate$installTeamAwareEquipment(CallbackInfoReturnable<EntityEquipment> cir) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer) {
			cir.setReturnValue(new TeamAwareEquipment(self));
		}
	}
}
