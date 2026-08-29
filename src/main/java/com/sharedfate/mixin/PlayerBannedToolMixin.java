package com.sharedfate.mixin;

import com.sharedfate.perk.PerkGearRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code item_ban} 의 채굴 차단 지점.
 *
 * <p>막힌 도구는 손에 들 수는 있지만 도구 노릇을 못 해야 한다. 바닐라의 채굴 판단 두 곳
 * ({@code getDestroySpeed} 로 얼마나 빨리 캐는가, {@code hasCorrectToolForDrops} 로 드롭이
 * 나오는가)이 모두 "지금 고른 칸의 아이템"을 읽는 것으로 시작하므로, <b>그 읽기 한 번을
 * 빈손으로 바꿔치기</b>하면 나머지 계산이 저절로 맨손과 같아진다. 성급함·채굴 피로 같은
 * 나머지 보정은 바닐라 계산이 그대로 이어서 해 준다.
 *
 * <p>{@code Redirect} 는 읽는 값만 바꿀 뿐 인벤토리를 건드리지 않는다. 아이템은 손에 그대로
 * 남아 있고 공유 인벤토리도 손대지 않는다.
 *
 * <p>클라이언트는 팀의 증강을 모르므로 화면상 채굴 진행은 평소 속도로 그려진다. 서버가 아직
 * 안 깨졌다고 보면 블록이 잠깐 깨졌다 되돌아오고, 결국 서버가 계산한 맨손 속도대로 깨진다.
 * 손해가 아니라 "이 도구로는 안 파진다"는 신호로 읽힌다.
 */
@Mixin(Player.class)
public abstract class PlayerBannedToolMixin {

	@Redirect(
			method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;"
							+ "getSelectedItem()Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private ItemStack sharedfate$hideBannedToolFromSpeed(Inventory inventory) {
		return sharedfate$neutralize(inventory.getSelectedItem());
	}

	@Redirect(
			method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;"
							+ "getSelectedItem()Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private ItemStack sharedfate$hideBannedToolFromDrops(Inventory inventory) {
		return sharedfate$neutralize(inventory.getSelectedItem());
	}

	/** 막힌 도구면 빈손으로 바꿔치기한다. 그 밖에는 원래 값 그대로. */
	private ItemStack sharedfate$neutralize(ItemStack selected) {
		if ((Object) this instanceof ServerPlayer self && PerkGearRules.itemBanned(self, selected)) {
			return ItemStack.EMPTY;
		}
		return selected;
	}
}
