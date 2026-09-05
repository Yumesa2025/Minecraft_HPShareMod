package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * 무엇을 들고 있든 <b>언제나</b> 붙는 약탈 등급.
 *
 * <pre>{@code
 * { "type": "always_looting", "levels": 3 }
 * }</pre>
 *
 * <ul>
 *   <li>{@code levels} — 붙일 약탈 등급. 바닐라 약탈은 III 까지지만 여기서도 그 위를 막지는
 *       않는다. 다만 {@link #MAX_LEVELS} 를 넘으면 정의를 버린다.</li>
 * </ul>
 *
 * <h2>{@code loot_bonus} 와 무엇이 다른가</h2>
 * <p>{@link LootBonusEffect} 는 「정해진 무기를 들었을 때」가 조건이고 {@code items}/{@code tags}
 * 를 반드시 적어야 한다. 「수확자」의 다이아 호미가 그것이다. 이쪽은 <b>조건이 아예 없다.</b>
 * 맨손이어도, 활이어도, 가방만 들고 있어도 걸린다. 세트 보상처럼 「이 팀은 그냥 전리품이 는다」를
 * 적는 자리를 위한 것이다.
 *
 * <p>그 차이를 {@code loot_bonus} 에 「조건 없음」 스위치로 얹지 않은 까닭은 두 가지다.
 *
 * <ol>
 *   <li>{@code loot_bonus} 가 {@code items}/{@code tags} 를 강제하는 것은 <b>의도된 안전장치</b>다
 *       (「아무거나 들고 있어도 약탈 V」는 실수로 적히기 쉽다). 거기에 스위치를 다는 순간 그
 *       안전장치가 무력해진다.</li>
 *   <li><b>겹쳤을 때의 규칙이 서로 다르다.</b> 아래에 적었다.</li>
 * </ol>
 *
 * <h2>겹치면 더 높은 쪽 하나만 — 더하지 않는다</h2>
 * <p>세트 단계는 누적이라 사냥 셋을 모으면 2단계(약탈 I)와 3단계(약탈 III)가 <b>둘 다</b>
 * 켜진다. 그때 결과는 <b>III</b> 이지 IV 가 아니다. 3단계 설명이 「약탈이 III 으로 오릅니다」인
 * 이상 더해 버리면 화면에 적힌 말과 실제가 어긋난다. 그래서 이 형끼리는 <b>가장 높은 하나만</b>
 * 센다. 판정하는 자리는 {@link com.sharedfate.perk.PerkLootRules} 이고 규칙도 거기 다시 적어
 * 두었다.
 *
 * <p>반대로 {@code loot_bonus} 끼리는 예전 그대로 <b>더한다.</b> 그쪽은 「이 무기를 들었을 때」
 * 라는 서로 다른 약속이라 하나만 골라 줄 이유가 없다. 형이 달라지면 규칙도 달라지므로 두 형을
 * 나눠 둔 것이 곧 규칙을 나눠 둔 것이 된다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>{@link LootBonusEffect} 와 같다. 여기는 「몇 등급인가」만 들고 있는 자료 그릇이고
 * {@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 붙였다 뗄 수 있는 것이 아니라
 * 전리품을 굴리는 순간에 조회하는 값이기 때문이다. 실제로 등급을 얹는 자리는
 * {@link com.sharedfate.mixin.EnchantmentHelperLootingMixin} 이다.
 */
public final class AlwaysLootingEffect implements PerkEffect {
	/**
	 * 붙일 수 있는 등급의 상한.
	 *
	 * <p>{@link LootBonusEffect} 와 같은 값이다. 전리품표의 곱셈이 폭주하지 않을 만큼만 허용한다.
	 */
	private static final int MAX_LEVELS = 10;

	private final int levels;

	public AlwaysLootingEffect(int levels) {
		this.levels = levels;
	}

	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int levels = PerkEffectType.readInt(json, "levels", 0);
		if (levels <= 0 || levels > MAX_LEVELS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: always_looting 의 levels 가 없거나 범위를 벗어났습니다 ({})", perkId, levels);
			return null;
		}
		// items/tags 는 읽지 않는다. 조건이 없는 것이 이 형의 뜻이라, 적혀 있어도 조용히
		// 무시하는 편이 「적었는데 안 걸린다」로 헤매는 것보다 낫다.
		return new AlwaysLootingEffect(levels);
	}

	/** 언제나 붙는 약탈 등급. */
	public int levels() {
		return levels;
	}
}
