package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkItemMatcher;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 정해진 무기를 들고 몹을 잡았을 때 약탈 등급을 얹는 효과.
 *
 * <pre>{@code
 * { "type": "loot_bonus", "levels": 5, "items": ["minecraft:diamond_hoe"] }
 * }</pre>
 *
 * <ul>
 *   <li>{@code levels} — 얹을 약탈 등급. 바닐라 약탈은 III 까지지만 여기서는 그 위도 된다.</li>
 *   <li>{@code items}/{@code tags} — 손에 들고 있어야 하는 물건. {@link PerkItemMatcher} 가
 *       읽는다. 적지 않으면 정의를 버린다. "아무거나 들고 있어도 약탈 V" 는 실수로 적히기
 *       쉬운 값이라 기본값으로 두지 않는다.</li>
 * </ul>
 *
 * <h2>바닐라 약탈에 더한다, 덮어쓰지 않는다</h2>
 * <p>약탈 III 이 붙은 다이아 호미를 들었으면 결과는 3 + {@code levels} 다. 인챈트를 무의미하게
 * 만들지 않으려는 것이고, {@code max_health_bonus} 가 팀 상한에 <b>더하는</b> 것과 같은 규칙이다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "몇 등급을, 무엇을 들었을 때" 만 들고 있는 자료 그릇이다.
 * {@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 붙였다 뗄 수 있는 것이 아니라
 * 전리품을 굴리는 순간에 조회하는 값이기 때문이다. {@code mining_speed} 와 같은 구도다.
 *
 * <p>실제로 등급을 얹는 자리는 {@link com.sharedfate.mixin.EnchantmentHelperLootingMixin} 이고,
 * "지금 이 사람이 조건을 채웠는가"를 판단하는 것은
 * {@link com.sharedfate.perk.PerkLootRules} 다.
 */
public final class LootBonusEffect implements PerkEffect {
	/** 얹을 수 있는 등급의 상한. 전리품표의 곱셈이 폭주하지 않을 만큼만 허용한다. */
	private static final int MAX_LEVELS = 10;

	private final int levels;
	private final PerkItemMatcher matcher;

	public LootBonusEffect(int levels, PerkItemMatcher matcher) {
		this.levels = levels;
		this.matcher = matcher;
	}

	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int levels = PerkEffectType.readInt(json, "levels", 0);
		if (levels <= 0 || levels > MAX_LEVELS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: loot_bonus 의 levels 가 없거나 범위를 벗어났습니다 ({})", perkId, levels);
			return null;
		}
		PerkItemMatcher matcher = PerkItemMatcher.fromJson(perkId, "loot_bonus", json);
		if (matcher == null) {
			return null;
		}
		return new LootBonusEffect(levels, matcher);
	}

	/** 이 물건을 들고 있으면 등급을 얹는가. */
	public boolean matches(@Nullable ItemStack stack) {
		return matcher.matches(stack);
	}

	public int levels() {
		return levels;
	}

	public PerkItemMatcher matcher() {
		return matcher;
	}
}
