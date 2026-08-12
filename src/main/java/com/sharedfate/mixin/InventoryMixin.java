package com.sharedfate.mixin;

import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Shadow
	@Final
	@Mutable
	private NonNullList<ItemStack> items;

	@Shadow
	@Final
	public Player player;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void sharedfate$installSharedItems(Player player, EntityEquipment equipment, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer)) {
			return;
		}

		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state != null) {
			this.items = state.mainItems;
		}
	}

	@Inject(method = "save", at = @At("HEAD"), cancellable = true)
	private void sharedfate$skipPersonalSave(
			ValueOutput.TypedOutputList<ItemStackWithSlot> output, CallbackInfo ci) {
		if (TeamLookup.serverStateOf(this.player) != null) {
			ci.cancel();
		}
	}

	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void sharedfate$skipPersonalLoad(
			ValueInput.TypedInputList<ItemStackWithSlot> input, CallbackInfo ci) {
		if (TeamLookup.serverStateOf(this.player) != null) {
			ci.cancel();
		}
	}

	@Redirect(
			method = "addResource(Lnet/minecraft/world/item/ItemStack;)I",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;getFreeSlot()I"
			)
	)
	private int sharedfate$mergeExtraBeforeMainFreeSlot(Inventory inventory, ItemStack stack) {
		if (ExpandedInventoryManager.enabled() && this.player instanceof ServerPlayer) {
			ExpandedInventoryManager.extraFor(this.player).mergeExisting(stack);
			if (stack.isEmpty()) {
				return -1;
			}
		}
		return inventory.getFreeSlot();
	}

	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
	private void sharedfate$addToExtraItems(
			int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (slot != -1 || !ExpandedInventoryManager.enabled()
				|| !(this.player instanceof ServerPlayer)) {
			return;
		}
		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(this.player);
		if (extra.addStack(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"), cancellable = true)
	private void sharedfate$clearOrCountExtraItems(
			Predicate<ItemStack> predicate, int maximum, Container other,
			CallbackInfoReturnable<Integer> cir) {
		if (!ExpandedInventoryManager.enabled() || !(this.player instanceof ServerPlayer)) {
			return;
		}
		int counted = cir.getReturnValue();
		if (maximum > 0 && counted >= maximum) {
			return;
		}
		int remaining = maximum == 0 ? 0 : maximum - counted;
		int extra = ExpandedInventoryManager.extraFor(this.player)
				.clearOrCountMatchingItems(predicate, remaining, maximum == 0);
		cir.setReturnValue(counted + extra);
	}

	@Inject(method = "fillStackedContents", at = @At("TAIL"))
	private void sharedfate$fillExtraStackedContents(
			StackedItemContents contents, CallbackInfo ci) {
		if (ExpandedInventoryManager.enabled() && this.player instanceof ServerPlayer) {
			ExpandedInventoryManager.extraFor(this.player).fillStackedContents(contents);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void sharedfate$tickExtraItems(CallbackInfo ci) {
		if (!sharedfate$hasActiveExtra()) {
			return;
		}
		for (ItemStack stack : ExpandedInventoryManager.extraFor(this.player).getItems()) {
			if (!stack.isEmpty()) {
				stack.inventoryTick(this.player.level(), this.player, null);
			}
		}
	}

	@Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true)
	private void sharedfate$includeExtraInEmptyCheck(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && sharedfate$hasActiveExtra()) {
			cir.setReturnValue(ExpandedInventoryManager.extraFor(this.player).isEmpty());
		}
	}

	@Inject(method = "contains(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
	private void sharedfate$containsExtraStack(
			ItemStack wanted, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && sharedfate$hasActiveExtra()) {
			cir.setReturnValue(ExpandedInventoryManager.extraFor(this.player).getItems().stream()
					.anyMatch(stack -> !stack.isEmpty()
							&& ItemStack.isSameItemSameComponents(stack, wanted)));
		}
	}

	@Inject(method = "contains(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"), cancellable = true)
	private void sharedfate$containsExtraTag(
			TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && sharedfate$hasActiveExtra()) {
			cir.setReturnValue(ExpandedInventoryManager.extraFor(this.player).getItems().stream()
					.anyMatch(stack -> !stack.isEmpty() && stack.is(tag)));
		}
	}

	@Inject(method = "contains(Ljava/util/function/Predicate;)Z", at = @At("RETURN"), cancellable = true)
	private void sharedfate$containsExtraPredicate(
			Predicate<ItemStack> predicate, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && sharedfate$hasActiveExtra()) {
			cir.setReturnValue(ExpandedInventoryManager.extraFor(this.player).getItems().stream()
					.anyMatch(predicate));
		}
	}

	private boolean sharedfate$hasActiveExtra() {
		return ExpandedInventoryManager.enabled()
				&& this.player instanceof ServerPlayer
				&& ExpandedInventoryManager.extraFor(this.player).active();
	}
}
