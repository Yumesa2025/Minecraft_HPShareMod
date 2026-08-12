package com.sharedfate.mixin;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import com.sharedfate.sync.TeamEnderChestView;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEnderChestMixin {
	@Unique
	private PlayerEnderChestContainer sharedfate$enderChestView;

	@Inject(method = "getEnderChestInventory", at = @At("HEAD"), cancellable = true)
	private void sharedfate$shareEnderChest(CallbackInfoReturnable<PlayerEnderChestContainer> cir) {
		if (SharedFateMod.config == null || !SharedFateMod.config.shareEnderChest) {
			return;
		}
		Player self = (Player) (Object) this;
		TeamState state = TeamLookup.serverStateOf(self);
		if (state != null) {
			cir.setReturnValue(sharedfate$enderChestView(self));
		}
	}

	@Inject(method = "getSlot", at = @At("HEAD"), cancellable = true)
	private void sharedfate$shareDirectEnderSlot(int slot, CallbackInfoReturnable<SlotAccess> cir) {
		if (slot < 200 || slot >= 200 + TeamState.ENDER_SIZE
				|| SharedFateMod.config == null || !SharedFateMod.config.shareEnderChest) {
			return;
		}
		Player self = (Player) (Object) this;
		if (TeamLookup.serverStateOf(self) != null) {
			cir.setReturnValue(sharedfate$enderChestView(self).getSlot(slot - 200));
		}
	}

	@Unique
	private PlayerEnderChestContainer sharedfate$enderChestView(Player self) {
		if (sharedfate$enderChestView == null) {
			PlayerEnderChestContainer personal =
					((PlayerEnderChestAccessor) self).sharedfate$getPersonalEnderChest();
			sharedfate$enderChestView = new TeamEnderChestView(() -> {
				TeamState state = TeamLookup.serverStateOf(self);
				return state == null ? personal : state.enderContainer;
			});
		}
		return sharedfate$enderChestView;
	}
}
