package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sharedfate.enchant.AnvilDiamondAccess;
import com.sharedfate.enchant.AnvilDiamondCost;
import com.sharedfate.enchant.AnvilDiamondSlot;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 모루의 대가를 경험치 레벨에서 <b>다이아몬드</b>로 바꾸고, 그 다이아몬드를 넣을
 * <b>칸을 하나 만든다.</b>
 *
 * <p>{@code EnchantmentMenuDiamondMixin} 과 같은 모양이다. 다른 점은 값이 팀마다 달라지지
 * 않는다는 것뿐이다 — 그래서 데이터 칸({@code EnchantmentCostDataSlot} 같은 것)이 없다.
 * 자세한 계산과 「왜」는 {@link AnvilDiamondCost} 에 있다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과한다.</b> 그래서 26.2 바이트코드를 직접 읽어 확인했다. 서술자를 못박는 시험은
 * {@code AnvilMenuTargetTest} 에 있다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/inventory/AnvilMenu.class
 *
 * protected boolean mayPickup(Player, boolean);
 *      0: hasInfiniteMaterials
 *      7: player.experienceLevel (GETFIELD)   ← 항상 통과하게 만든다
 *     11: cost.get()
 *     18: if_icmplt   (레벨 < cost 면 거짓)
 *     21: cost.get()
 *     25: ifle         (cost <= 0 이면 거짓 — 「할 것이 없다」)
 *
 * protected void onTake(Player, ItemStack);
 *      0: hasInfiniteMaterials
 *      7: player / cost.get() / ineg
 *     16: invokevirtual  // Player.giveExperienceLevels:(I)V   ← 이 호출을 다이아몬드
 *                        //   차감으로 통째로 바꾼다
 *
 * public void createResult();
 *     …: cost.set(computed)   ← 손대지 않는다. 결과 유효성 판정({@code cost>0})이 이 값을 본다
 *     897~915: if (cost.get() >= 40) cost.set(39); onlyRenaming = true;   ← 이름표 전용 표시
 *                                                                            상한, 손대지 않는다
 *     923~948: if (cost.get() >= 40 && !hasInfiniteMaterials) result = EMPTY;
 *              ← createResult 안의 두 번째 DataSlot.get() 이 바로 이 조건이다. 여기만
 *                무력화한다({@code ordinal = 1}).
 *
 * public int getCost();
 *      0: return cost.get();   ← AnvilMenu 안에서 이 메서드를 부르는 곳은 없다(자체
 *                                 검증). 클라이언트의 {@code AnvilScreen.extractLabels} 만
 *                                 부른다. 그래서 반환값만 바꿔도 서버 쪽 판정에는 전혀
 *                                 새지 않는다.
 * }</pre>
 *
 * <h2>「너무 비쌉니다」 상한 — 표시까지 저절로 맞는다</h2>
 * <p>클라이언트 {@code AnvilScreen.extractLabels} 는 「Too Expensive!」와 숫자 라벨을
 * <b>모두 {@code menu.getCost()} 하나로</b> 판단한다({@code cost >= 40} 이면 전자,
 * 아니면 후자를 숫자와 함께 그린다). {@link #sharedfate$showDiamondCost} 가 그 값을
 * 「결과가 있으면 10, 없으면 0」으로 바꿔치기하므로, 클라이언트는 40을 절대 보지 못해
 * 「Too Expensive!」를 그릴 일이 없다. <b>클라이언트 코드(src/client/java)를 전혀 건드리지
 * 않고도</b> 화면이 맞아떨어지는 이유다.
 *
 * <h2>칸이 늘어나므로 통신 규약이 올라간다</h2>
 * <p>인챈트 탁자와 같은 이유다 — 옛 클라이언트의 {@code AnvilMenu} 는 이 칸이 없어 슬롯
 * 개수가 어긋난다. 메뉴의 칸 수는 서버와 클라이언트가 같아야 하므로, 이 칸을 모르는
 * 클라이언트는 창이 깨지거나 아이템이 엉뚱한 자리로 간다. 그래서 이 판에서
 * {@code SharedFateNetworking.PROTOCOL_VERSION} 이 27 로 올라갔다.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuDiamondMixin implements AnvilDiamondAccess {
	/** 다이아몬드 칸의 화면 좌표. 왼쪽 재료 칸(27,47) 바로 아래 줄이다. */
	@Unique
	private static final int DIAMOND_SLOT_X = 27;
	@Unique
	private static final int DIAMOND_SLOT_Y = 65;

	@Shadow
	@Final
	private DataSlot cost;

	@Unique
	private Container sharedfate$diamonds;
	@Unique
	private int sharedfate$diamondSlot = AnvilDiamondAccess.NO_SLOT;

	@Override
	public Container sharedfate$diamondContainer() {
		return sharedfate$diamonds;
	}

	@Override
	public int sharedfate$diamondMenuSlot() {
		return sharedfate$diamondSlot;
	}

	@Inject(
			method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
					+ "Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void sharedfate$addDiamondSlot(
			int containerId, Inventory inventory, ContainerLevelAccess levelAccess,
			CallbackInfo ci) {
		sharedfate$diamonds = new SimpleContainer(1);
		sharedfate$diamondSlot = ((AbstractContainerMenu) (Object) this).slots.size();
		((AbstractContainerMenuAccessor) this).sharedfate$invokeAddSlot(
				new AnvilDiamondSlot(sharedfate$diamonds, 0, DIAMOND_SLOT_X, DIAMOND_SLOT_Y));
	}

	/**
	 * 요구 레벨을 없앤다. 실제 가능 여부는 {@link #sharedfate$requireDiamonds} 가 다이아몬드로
	 * 따로 가린다.
	 */
	@ModifyExpressionValue(
			method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
					opcode = Opcodes.GETFIELD),
			require = 1)
	private int sharedfate$dropLevelRequirement(int experienceLevel) {
		return Integer.MAX_VALUE;
	}

	/** 다이아몬드가 모자라면 결과를 집지 못하게 한다. 크리에이티브는 그대로 통과한다. */
	@Inject(
			method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void sharedfate$requireDiamonds(
			Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
		if (!player.hasInfiniteMaterials()
				&& !AnvilDiamondCost.canAfford(player, sharedfate$diamonds)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * 경험치 레벨을 깎는 호출을 다이아몬드 차감으로 바꾼다.
	 *
	 * <p>바닐라는 이 호출을 {@code access.execute(...)} 밖에서 부른다 — 클라이언트에서도
	 * 그대로 도는 자리라는 뜻이다. 그래서 여기도 똑같이 양쪽에서 돈다. 클라이언트가 제 그릇을
	 * 먼저 줄여도 해롭지 않다 — 서버가 뒤이어 보내는 칸 동기화가 진짜 값으로 덮어쓴다.
	 */
	@Redirect(
			method = "onTake(Lnet/minecraft/world/entity/player/Player;"
					+ "Lnet/minecraft/world/item/ItemStack;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;"
							+ "giveExperienceLevels(I)V"))
	private void sharedfate$chargeDiamondsInsteadOfLevels(Player player, int negatedCost) {
		AnvilDiamondCost.consume(player, sharedfate$diamonds);
	}

	/**
	 * 「너무 비쌉니다」로 결과물을 지우는 갈래만 무력화한다.
	 *
	 * <p>{@code createResult} 안에는 {@code DataSlot.get()} 호출이 둘 있다 — 첫 번째
	 * ({@code ordinal = 0})는 이름표 전용 표시 상한(39로 깎기)이고, 두 번째
	 * ({@code ordinal = 1})가 실제로 결과물을 지우는 갈래다. 첫 번째는 표시에만 쓰이고
	 * 뒤에서 {@link #sharedfate$showDiamondCost} 가 어차피 값을 다이아몬드 개수로
	 * 바꿔치기하므로 손대지 않는다.
	 */
	@ModifyExpressionValue(
			method = "createResult()V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/inventory/DataSlot;get()I",
					ordinal = 1),
			require = 1)
	private int sharedfate$neverTooExpensive(int vanillaCost) {
		return 0;
	}

	/**
	 * 화면에 보여 줄 비용. {@code AnvilMenu} 안에서 이 메서드를 부르는 곳은 없으므로(자체
	 * 확인), 반환값을 바꿔도 {@code mayPickup}·{@code onTake}·{@code createResult} 의 판정에는
	 * 전혀 새지 않는다.
	 */
	@Inject(method = "getCost()I", at = @At("HEAD"), cancellable = true)
	private void sharedfate$showDiamondCost(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(AnvilDiamondCost.displayCost(cost.get()));
	}
}
