package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.enchant.EnchantmentDiamondCost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/**
 * 인챈트의 대가를 경험치 레벨에서 <b>다이아몬드</b>로 바꿉니다.
 *
 * <p>바닐라 {@code clickMenuButton} 은 다섯 가지를 봅니다 — 청금석 개수, {@code costs[칸]},
 * 아이템칸이 비었는지, 레벨이 「칸 번호 + 1」 이상인지, 레벨이 {@code costs[칸]} 이상인지.
 * 이 중 <b>레벨을 보는 둘</b>을 무력화하고, 대신 다이아몬드 검사를 앞에 붙입니다.
 * {@code costs[칸]} 계산과 인챈트 위력 체계는 손대지 않습니다 — 그 숫자는 그대로
 * {@code getEnchantmentList} 로 들어가 인챈트의 세기를 정합니다.
 *
 * <p><b>{@code clickMenuButton} 은 클라이언트에서도 그대로 돕니다.</b>
 * {@code EnchantmentScreen.mouseClicked} 가 패킷을 보내기 전에 로컬에서 부르고 참일 때만
 * 보냅니다. 그래서 검사는 양쪽에서 같이 돌아야 하고, <b>실제 차감은
 * {@code access.execute(...)} 안</b>이어야 합니다. 클라이언트의 접근자는
 * {@code ContainerLevelAccess.NULL} 이라 그 람다가 돌지 않습니다.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuDiamondMixin {
	/** 다이아몬드가 모자라면 아예 누르지 못하게 합니다. 크리에이티브는 그대로 통과합니다. */
	@Inject(
			method = "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void sharedfate$requireDiamonds(
			Player player, int id, CallbackInfoReturnable<Boolean> cir) {
		if (id < 0 || id >= EnchantmentDiamondCost.SLOT_COUNT) {
			// 범위 밖은 바닐라가 기록을 남기고 거절합니다. 그 자리를 뺏지 않습니다.
			return;
		}
		if (!EnchantmentDiamondCost.canAfford(player, id)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * 요구 레벨을 없앱니다.
	 *
	 * <p>{@code clickMenuButton} 안의 {@code experienceLevel} 읽기는 정확히 둘이고
	 * 각각 「레벨 ≥ 칸 번호 + 1」과 「레벨 ≥ costs[칸]」에 쓰입니다. 둘 다 무한대로
	 * 바꿔 언제나 통과시킵니다. 레벨을 깎는 일은
	 * {@link PlayerEnchantmentLevelMixin} 이 막습니다.
	 */
	@ModifyExpressionValue(
			method = "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
					opcode = Opcodes.GETFIELD
			),
			require = 2,
			allow = 2
	)
	private int sharedfate$dropLevelRequirement(int experienceLevel) {
		return Integer.MAX_VALUE;
	}

	/**
	 * 다이아몬드를 실제로 걷습니다.
	 *
	 * <p>바닐라가 넘기는 람다를 한 겹 감싸서, <b>서버에서만 도는 그 안</b>에서 걷습니다.
	 * 인챈트가 정말 이루어졌는지는 <b>청금석이 줄었는지</b>로 봅니다 — 바닐라는 인챈트
	 * 후보가 하나도 없으면 아무것도 하지 않고 빠져나가는데, 그때 다이아몬드만 사라지면
	 * 안 됩니다.
	 */
	@WrapOperation(
			method = "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"
			)
	)
	private void sharedfate$chargeDiamonds(
			ContainerLevelAccess access, BiConsumer<Level, BlockPos> enchantAction,
			Operation<Void> original, Player player, int id) {
		EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
		original.call(access, (BiConsumer<Level, BlockPos>) (level, pos) -> {
			// getGoldCount 는 이름과 달리 청금석 개수를 돌려줍니다.
			int lapisBefore = menu.getGoldCount();
			enchantAction.accept(level, pos);
			if (menu.getGoldCount() < lapisBefore) {
				EnchantmentDiamondCost.consume(player, id);
			}
		});
	}
}
