package com.sharedfate.mixin;

import com.sharedfate.perk.PerkGearRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code item_ban} 의 우클릭 착용 차단 지점.
 *
 * <p>손에 든 방어구를 우클릭해 갈아입는 길만 {@code isEquippableInSlot} 을 지나지 않는다.
 * 바닐라가 그 자리에서는 칸만 보고({@code canUseSlot}) 아이템은 보지 않기 때문이다.
 * 그래서 {@code item_ban} 을 여기서 한 번 더 막는다. 이걸 빠뜨리면 막힌 다이아몬드 투구를
 * 우클릭으로 입은 다음 {@link com.sharedfate.perk.PerkGearManager} 가 곧바로 벗기는,
 * 입었다 벗었다를 되풀이하는 그림이 된다.
 *
 * <p>{@code PASS} 를 돌려주므로 바닐라가 "아무 일도 일어나지 않은 우클릭"으로 처리한다.
 * 아이템이 사라지거나 옮겨지지 않는다.
 *
 * <p>칸 자체가 막힌 경우({@code equip_ban})는 여기서 볼 필요가 없다. 이 메서드가 첫 줄에서
 * {@code canUseSlot} 을 확인하고, 그건 {@link LivingEntityEquipBanMixin} 이 이미 막아 둔다.
 */
@Mixin(Equippable.class)
public abstract class EquippableSwapBanMixin {

	@Inject(
			method = "swapWithEquipmentSlot(Lnet/minecraft/world/item/ItemStack;"
					+ "Lnet/minecraft/world/entity/player/Player;)"
					+ "Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$blockBannedSwap(
			ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof ServerPlayer self && PerkGearRules.itemBanned(self, stack)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
