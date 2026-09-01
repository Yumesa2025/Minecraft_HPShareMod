package com.sharedfate.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 인챈트로 경험치 레벨이 줄지 않게 합니다.
 *
 * <p>{@code onEnchantmentPerformed} 는 「레벨을 n 만큼 깎고, 조건 없이 인챈트 씨앗을
 * 새로 뽑는다」입니다. n 을 0 으로 바꾸면 <b>레벨은 그대로 두고 씨앗만 갱신</b>됩니다.
 * 씨앗을 갱신하지 않으면 인챈트할 때마다 같은 후보 목록이 계속 뜹니다.
 *
 * <p>이 메서드를 부르는 곳은 바닐라 전체에서 인챈트 탁자 하나뿐이라, 여기서 막는 것이
 * 인챈트 탁자에서만 막는 것과 같습니다. 대가는 다이아몬드로 따로 받습니다 —
 * {@link EnchantmentMenuDiamondMixin} 을 보십시오.
 */
@Mixin(Player.class)
public abstract class PlayerEnchantmentLevelMixin {
	@ModifyVariable(
			method = "onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V",
			at = @At("HEAD"),
			argsOnly = true,
			index = 2
	)
	private int sharedfate$noExperienceLevelCost(int levels) {
		return 0;
	}
}
