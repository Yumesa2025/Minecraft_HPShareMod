package com.sharedfate.mixin;

import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * 확장 인벤토리(추가 27칸)에 있는 화살을 활·석궁이 찾도록 넓힌다.
 *
 * <p>26.2 의 {@code Player.getProjectile(ItemStack)} 은 마지막에 자기 소유의 36칸
 * {@code Inventory} 만 {@code getContainerSize()}/{@code getItem} 루프로 훑는다. 추가 칸은 별도의
 * {@link ExpandedInventoryContainer} 라 그 루프에 전혀 안 잡힌다. 석궁의
 * {@code CrossbowItem.tryLoadProjectiles} 도 ({@code LivingEntity.getProjectile} 를 통해, 실제로는
 * {@code Player} 가 재정의한 것을) 같은 자리를 부르므로 활·석궁 둘 다 여기 한 곳만 넓히면 함께
 * 고쳐진다. 삼지창({@code TridentItem})은 {@code ProjectileWeaponItem} 을 상속하지 않고 자기
 * 자신을 그대로 던지는 방식이라 탄약을 찾지 않는다 — 이 버그와 무관하다.
 *
 * <h2>⚠ 서버에서만 켜면 화면이 반응하지 않는다</h2>
 * <p>예전에는 {@code player instanceof ServerPlayer} 로 <b>서버에서만</b> 이 검사를 돌렸다.
 * 그런데 활을 당기기 시작하는 판정({@code BowItem.use}/{@code CrossbowItem.use})은 클라이언트에서도
 * 먼저 한 번 돈다 — {@code MultiPlayerGameMode.useItem} 은 서버로 보낼 패킷을 만들어 두고, 그와
 * <b>별개로 로컬에서도 같은 {@code Item.use} 를 그대로 실행</b>해 당기는 애니메이션과
 * {@code startUsingItem} 을 즉시 정한다. 로컬 쪽 {@code getProjectile} 이 확장 칸을 못 보고
 * 「없다」고 답하면 그 자리에서 실패로 끝나 <b>화면에 아무 반응이 없다.</b> 추가 칸에만 화살을
 * 두고 활을 당겼을 때 아무 일도 안 일어나던 것이 이 틈이었다.
 *
 * <p>그래서 {@code instanceof} 로 가르지 않고 {@link ExpandedInventoryContainer#active()} 로
 * 묻는다 — 서버에서는 팀 상태로, 클라이언트에서는 서버가 협상해 내려준 값으로 판단하므로 양쪽
 * 모두 정확하다.
 *
 * <h2>열린 칸까지만 본다</h2>
 * <p>{@link ExpandedInventoryManager#unlockedFor(Player)} 로 <b>지금 실제로 열려 있는 칸</b>까지만
 * 훑는다. 잠긴 칸은 넣는 길을 {@code ExpandedInventoryContainer.openSlots()} 와
 * {@code ExpandedInventorySlot.mayPlace} 로 막아 둔 자리인데, 정작 화살 찾기가 27칸 전부를 보면
 * 그 잠금이 뚫린 것과 같다.
 *
 * <p><b>여기는 읽기만 한다.</b> 칸에 물건을 넣는 길은 한 줄도 건드리지 않는다.
 */
@Mixin(Player.class)
public abstract class PlayerExpandedInventoryMixin {
	@Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
	private void sharedfate$findProjectileInExtra(
			ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
		if (!cir.getReturnValue().isEmpty()
				|| !ExpandedInventoryManager.enabled()
				|| !(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) {
			return;
		}

		Player player = (Player) (Object) this;
		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(player);
		if (!extra.active()) {
			return;
		}

		Predicate<ItemStack> predicate = projectileWeapon.getAllSupportedProjectiles();
		int open = ExpandedInventoryManager.unlockedFor(player);
		for (int slot = 0; slot < open; slot++) {
			ItemStack stack = extra.getItem(slot);
			if (predicate.test(stack)) {
				cir.setReturnValue(stack);
				return;
			}
		}
	}
}
