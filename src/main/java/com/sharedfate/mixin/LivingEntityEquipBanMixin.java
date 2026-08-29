package com.sharedfate.mixin;

import com.sharedfate.perk.PerkGearRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code equip_ban} 과 {@code item_ban} 의 착용 차단 지점.
 *
 * <p>바닐라는 "이 칸을 쓸 수 있는가"({@code canUseSlot})와 "이 아이템을 이 칸에 넣을 수
 * 있는가"({@code isEquippableInSlot}) 두 물음에 착용 판단을 몰아 두었다. 인벤토리 화면의 방어구
 * 칸이 살아 있는지, 거기에 아이템을 놓을 수 있는지, 우클릭으로 갈아입을 수 있는지, 디스펜서가
 * 입혀 줄 수 있는지가 전부 이 둘을 지나간다. 그래서 여기 두 곳만 막으면 착용 경로가 전부 막힌다.
 *
 * <p>대상이 {@link LivingEntity} 인 이유는 {@code Player} 가 이 두 메서드를 재정의하지 않기
 * 때문이다. 몹도 함께 지나가지만 첫 줄의 {@code instanceof ServerPlayer} 에서 곧바로 빠져나온다.
 * 팀에 속하지 않은 플레이어와 증강이 없는 팀도 {@link PerkGearRules} 의 빠른 경로에서 걸러진다.
 *
 * <p>여기서 막지 못하는 것은 이미 입고 있던 장비뿐이다. 그건
 * {@link com.sharedfate.perk.PerkGearManager} 가 벗겨서 공유 인벤토리로 보낸다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEquipBanMixin {

	@Inject(
			method = "canUseSlot(Lnet/minecraft/world/entity/EquipmentSlot;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$blockBannedSlot(
			EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer self && PerkGearRules.slotBanned(self, slot)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(
			method = "isEquippableInSlot(Lnet/minecraft/world/item/ItemStack;"
					+ "Lnet/minecraft/world/entity/EquipmentSlot;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$blockBannedItem(
			ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer self && PerkGearRules.itemBanned(self, stack)) {
			cir.setReturnValue(false);
		}
	}
}
