package com.sharedfate.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.enchant.EnchantmentDiamondCost;
import com.sharedfate.enchant.EnchantmentDiamondTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 인챈트 창을 <b>다이아몬드 기준</b>으로 그립니다.
 *
 * <p>대가가 경험치 레벨이 아니게 되었으므로 바닐라 화면은 전부 거짓말이 됩니다. 단추의
 * 숫자는 요구 레벨이고, 회색으로 죽는 조건도 레벨이며, 툴팁도 레벨을 말합니다.
 *
 * <p>고치는 방법은 <b>바닐라가 읽는 두 값을 갈아 끼우는 것</b>입니다.
 *
 * <ul>
 *   <li>{@code menu.costs} → 칸마다의 다이아몬드 개수 (후보가 없는 칸의 0 은 그대로)</li>
 *   <li>{@code player.experienceLevel} → 가진 다이아몬드 개수</li>
 * </ul>
 *
 * <p>바닐라가 하는 「레벨 ≥ costs[칸]」 비교가 그대로 「다이아몬드 ≥ 필요 개수」가 되고,
 * 단추에 찍히는 숫자도 저절로 다이아몬드 개수가 됩니다. 칸마다 값을 다르게 바꿔도
 * 화면은 손댈 필요가 없습니다.
 *
 * <p><b>통신은 필요 없습니다.</b> 청금석 개수({@code getGoldCount})와 {@code costs} 는
 * 이미 서버가 보내 주고, 다이아몬드는 클라이언트가 제 인벤토리를 세면 됩니다.
 * 크리에이티브 예외는 바닐라 자리 그대로 남습니다.
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
	/** 「인벤토리」 글자를 옮길 자리. 조합법 단추 세 개(y 14~71) 바로 아래입니다. */
	@Unique
	private static final int INVENTORY_LABEL_X = 60;
	@Unique
	private static final int INVENTORY_LABEL_Y = 72;

	/** 단추에 찍히는 숫자와 회색 판정의 기준값을 다이아몬드 개수로 바꿉니다. */
	@ModifyExpressionValue(
			method = "extractBackground",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I",
					opcode = Opcodes.GETFIELD
			),
			require = 1,
			allow = 1
	)
	private int[] sharedfate$diamondCostsOnButtons(int[] costs) {
		return EnchantmentDiamondCost.displayCosts(costs);
	}

	/** 단추를 살릴지 죽일지를 레벨이 아니라 가진 다이아몬드로 정합니다. */
	@ModifyExpressionValue(
			method = "extractBackground",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
					opcode = Opcodes.GETFIELD
			),
			require = 1,
			allow = 1
	)
	private int sharedfate$diamondsInsteadOfLevelOnButtons(int experienceLevel) {
		return sharedfate$ownedDiamonds();
	}

	/** 툴팁에서도 같은 값을 씁니다. {@code costs} 는 여기서 두 번 읽힙니다. */
	@ModifyExpressionValue(
			method = "extractRenderState",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I",
					opcode = Opcodes.GETFIELD
			),
			require = 2,
			allow = 2
	)
	private int[] sharedfate$diamondCostsInTooltip(int[] costs) {
		return EnchantmentDiamondCost.displayCosts(costs);
	}

	@ModifyExpressionValue(
			method = "extractRenderState",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I",
					opcode = Opcodes.GETFIELD
			),
			require = 1,
			allow = 1
	)
	private int sharedfate$diamondsInsteadOfLevelInTooltip(int experienceLevel) {
		return sharedfate$ownedDiamonds();
	}

	/**
	 * 툴팁 글월을 다시 씁니다.
	 *
	 * <p>위의 두 바꿔치기로 <b>어느 줄이 뜨는지</b>는 이미 다이아몬드 기준이 되었지만,
	 * 글월 자체는 아직 「필요 레벨」·「인챈트 레벨 N」이라 거짓말입니다. 바닐라가 다 만든
	 * 줄 목록을 받아 그 두 종류만 갈아 끼웁니다.
	 */
	@WrapOperation(
			method = "extractRenderState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"
			)
	)
	private void sharedfate$diamondTooltipText(
			GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY,
			Operation<Void> original) {
		original.call(graphics, font, EnchantmentDiamondTooltip.rewrite(lines), mouseX, mouseY);
	}

	/**
	 * 「인벤토리」 글자를 다이아몬드 칸 옆으로 옮깁니다.
	 *
	 * <p>바닐라는 그 글자를 (8, 72)에 찍는데, 다이아몬드 칸이 (15, 65)~(15, 82)를 쓰므로
	 * 그대로 두면 글자가 칸 위에 겹쳐 찍힙니다. 인챈트 탁자에서 그 줄만 조합법 단추 아래
	 * 빈자리로 밀어 둡니다.
	 */
	@Inject(method = "init", at = @At("RETURN"))
	private void sharedfate$moveInventoryLabel(CallbackInfo ci) {
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
		screen.sharedfate$setInventoryLabelX(INVENTORY_LABEL_X);
		screen.sharedfate$setInventoryLabelY(INVENTORY_LABEL_Y);
	}

	@Unique
	private int sharedfate$ownedDiamonds() {
		return EnchantmentDiamondCost.countIn(
				((EnchantmentScreen) (Object) this).getMenu());
	}
}
