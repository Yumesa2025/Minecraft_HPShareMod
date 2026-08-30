package com.sharedfate.perk;

import com.sharedfate.perk.effect.LootBonusEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 전리품에 끼어드는 증강({@code loot_bonus})의 판정부.
 *
 * <p>{@link com.sharedfate.mixin.EnchantmentHelperLootingMixin} 이 약탈 등급을 묻는 자리에서
 * 여기에 물어보고, 답만큼 등급을 더한다. 판정을 mixin 밖에 떼어 둔 이유는
 * {@link PerkFoodRules} 와 같다. mixin 에는 "어디서 끼어드는가"만 남고, 판정은 월드 없이
 * 시험할 수 있다.
 *
 * <h2>손에 든 것만 본다</h2>
 * <p>바닐라 약탈이 {@code mainhand} 슬롯에서만 세는 것과 맞춘다. 왼손에 들거나 가방에 넣어
 * 두는 것으로는 걸리지 않는다.
 */
public final class PerkLootRules {
	private PerkLootRules() {
	}

	/**
	 * 이 사람이 지금 손에 든 것으로 얻는 약탈 추가 등급. 해당 없으면 0.
	 *
	 * <p>가진 증강이 여럿이면 <b>모두 더한다.</b> 서로 다른 증강이 각각 약속한 등급이라 하나만
	 * 골라 줄 이유가 없다. {@code food_nutrition} 배율을 모으는 규칙과 같은 결이다.
	 */
	public static int bonusLootingLevels(@Nullable LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return 0;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return 0;
		}
		ItemStack held = player.getMainHandItem();
		if (held == null || held.isEmpty()) {
			return 0;
		}
		int total = 0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof LootBonusEffect loot && loot.matches(held)) {
					total += loot.levels();
				}
			}
		}
		return total;
	}
}
