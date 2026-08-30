package com.sharedfate.mixin;

import com.sharedfate.perk.PerkLootRules;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code loot_bonus} 증강이 약탈 등급에 끼어드는 자리.
 *
 * <h2>왜 여기인가</h2>
 * <p>26.2 에서 약탈이 실제로 전리품을 늘리는 통로는 <b>두 개</b>다. 몹 전리품표를 보면 알 수
 * 있다.
 *
 * <ul>
 *   <li>{@code minecraft:enchanted_count_increase} — 떨어지는 <b>개수</b>를 늘린다
 *       (썩은 살점, 화약, 실 같은 것)</li>
 *   <li>{@code minecraft:random_chance_with_enchanted_bonus} — <b>희귀 드롭 확률</b>을 올린다
 *       (좀비의 철괴·감자·당근 같은 것)</li>
 * </ul>
 *
 * <p>둘은 서로 다른 클래스지만 등급을 물어보는 자리는 똑같이
 * {@code EnchantmentHelper.getEnchantmentLevel(Holder, LivingEntity)} 하나다. 그래서 여기 한
 * 곳만 잡으면 개수와 확률이 <b>함께</b> 오른다. 두 클래스를 각각 잡으면 mixin 이 둘로 늘고,
 * 앞으로 약탈을 읽는 세 번째 통로가 생겼을 때 조용히 빠진다.
 *
 * <p>약탈 인챈트 정의 자체({@code data/minecraft/enchantment/looting.json})는 손대지 않는다.
 * 그 파일에 적힌 {@code equipment_drops} 는 "몹이 쓰던 장비를 떨어뜨릴 확률"이라 전리품 개수와
 * 상관이 없고, 데이터팩을 고치면 이 증강이 없는 팀까지 영향을 받는다.
 *
 * <h2>약탈을 물을 때만 끼어든다</h2>
 * <p>이 메서드는 모든 마법의 등급을 묻는 공용 통로다. 그래서 가장 먼저 "지금 묻는 것이
 * 약탈인가"를 보고, 아니면 즉시 빠져나온다. 날카로움·보호처럼 자주 불리는 마법의 경로에는
 * {@link Holder#is} 한 번 말고는 아무것도 얹히지 않는다.
 *
 * <p>RETURN 에 붙는 이유는 <b>바닐라 등급에 더해야</b> 하기 때문이다. 약탈 III 이 붙은 도구를
 * 들었으면 결과는 3 + 증강 등급이다. HEAD 에서 값을 정해 돌려주면 인챈트가 무의미해진다.
 *
 * <h2>클라이언트에서는 아무 일도 하지 않는다</h2>
 * <p>{@link PerkLootRules#bonusLootingLevels} 가 {@code ServerPlayer} 가 아니면 0 을
 * 돌려주므로 클라이언트 계산은 바닐라 그대로다. 전리품은 어차피 서버에서만 굴린다.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperLootingMixin {
	@Inject(method = "getEnchantmentLevel", at = @At("RETURN"), cancellable = true)
	private static void sharedfate$addPerkLootingLevels(Holder<Enchantment> enchantment,
			LivingEntity entity, CallbackInfoReturnable<Integer> callback) {
		if (enchantment == null || !enchantment.is(Enchantments.LOOTING)) {
			return;
		}
		int bonus = PerkLootRules.bonusLootingLevels(entity);
		if (bonus > 0) {
			callback.setReturnValue(callback.getReturnValueI() + bonus);
		}
	}
}
