package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.enchant.EnchantmentDiamondCost;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 인챈트 탁자가 받는 다이아몬드 개수를 바꾼다.
 *
 * <pre>{@code
 * { "type": "enchant_cost", "diamonds": 1 }
 * }</pre>
 *
 * <p>이 모드의 인챈트는 경험치 레벨이 아니라 다이아몬드를 받는다
 * ({@link EnchantmentDiamondCost}). 기본값은 한 칸에
 * {@value EnchantmentDiamondCost#DIAMONDS_PER_ENCHANT} 개이고, 이 효과를 가진 팀만 그 값이
 * 달라진다. 효과를 갖지 않은 팀은 지금까지와 비트 하나도 다르지 않다.
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 팀원에게 붙였다 떼는 것이 아니라
 * <b>인챈트 탁자를 열 때 팀을 보고 값을 정하는</b> 방식이라, 실행부는 전부
 * {@link EnchantmentDiamondCost} 에 있다. 여기서는 "몇 개인가"만 들고 있다.
 *
 * <h2>0 을 허용한다</h2>
 * <p>0 이면 다이아몬드 없이 인챈트할 수 있다. 상한은 한 묶음({@value #MAX_DIAMONDS})이다.
 * 그보다 큰 값은 다이아몬드 칸 한 칸에 담기지 않아 어떤 팀도 인챈트할 수 없게 된다.
 *
 * <h2>여러 개를 가졌을 때</h2>
 * <p>가장 작은 값이 이긴다. {@link com.sharedfate.perk.PerkWorldRules#lockedDayTime} 과 같은
 * 규칙이다.
 */
public final class EnchantCostEffect implements PerkEffect {
	/** 적을 수 있는 가장 작은 값. 0 은 「공짜」다. */
	public static final int MIN_DIAMONDS = 0;
	/** 적을 수 있는 가장 큰 값. 다이아몬드 칸 한 칸에 담기는 최대치다. */
	public static final int MAX_DIAMONDS = 64;

	/** {@code diamonds} 필드가 아예 없을 때 쓰는 표시. 실제 개수가 될 수 없는 값이어야 한다. */
	private static final int MISSING = -1;

	private final int diamonds;

	public EnchantCostEffect(int diamonds) {
		this.diamonds = diamonds;
	}

	/**
	 * JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null.
	 *
	 * <p>{@code diamonds} 는 반드시 적어야 한다. 기본값을 정해 두면 오타로 필드가 빠진 정의가
	 * 조용히 엉뚱한 값으로 인챈트 비용을 바꾼다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int diamonds = PerkEffectType.readInt(json, "diamonds", MISSING);
		if (diamonds < MIN_DIAMONDS || diamonds > MAX_DIAMONDS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: enchant_cost 의 diamonds 가 없거나 {}~{} 범위를 벗어났습니다 ({})",
					perkId, MIN_DIAMONDS, MAX_DIAMONDS, diamonds);
			return null;
		}
		return new EnchantCostEffect(diamonds);
	}

	/** 인챈트 한 번에 드는 다이아몬드 개수. */
	public int diamonds() {
		return diamonds;
	}
}
