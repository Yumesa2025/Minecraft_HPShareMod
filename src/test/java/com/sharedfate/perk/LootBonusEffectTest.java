package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.LootBonusEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code loot_bonus} 의 정의 읽기와 손에 든 물건 판정을 본다.
 *
 * <p>등급이 실제로 전리품에 더해지는 자리는
 * {@code EnchantmentHelperLootingMixin} → {@link PerkLootRules} 인데, 거기까지 가려면 살아 있는
 * 플레이어와 전리품표가 있어야 한다. 여기서는 월드 없이 확인할 수 있는 두 가지 —
 * "정의를 어떻게 읽는가"와 "무엇을 들었을 때 걸리는가" — 만 본다.
 */
class LootBonusEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 등급과_물건을_읽는다() {
		LootBonusEffect effect = create("""
				{ "type": "loot_bonus", "levels": 5, "items": ["minecraft:diamond_hoe"] }
				""");

		assertEquals(5, effect.levels());
		assertTrue(effect.matches(new ItemStack(Items.DIAMOND_HOE)));
		assertFalse(effect.matches(new ItemStack(Items.IRON_HOE)));
		assertFalse(effect.matches(new ItemStack(Items.DIAMOND_SWORD)));
	}

	@Test
	void 태그로도_적을_수_있다() {
		LootBonusEffect effect = create("""
				{ "type": "loot_bonus", "levels": 2, "tags": ["minecraft:hoes"] }
				""");

		assertEquals(2, effect.levels());
		assertEquals(1, effect.matcher().tags().size());
	}

	@Test
	void levels_가_없거나_범위를_벗어나면_버린다() {
		assertNull(raw("{ \"type\": \"loot_bonus\", \"items\": [\"minecraft:diamond_hoe\"] }"));
		assertNull(raw("""
				{ "type": "loot_bonus", "levels": 0, "items": ["minecraft:diamond_hoe"] }
				"""));
		assertNull(raw("""
				{ "type": "loot_bonus", "levels": -3, "items": ["minecraft:diamond_hoe"] }
				"""));
		assertNull(raw("""
				{ "type": "loot_bonus", "levels": 99, "items": ["minecraft:diamond_hoe"] }
				"""));
	}

	@Test
	void 들_물건을_적지_않으면_버린다() {
		// "아무거나 들고 있어도 약탈 V" 는 실수로 적히기 쉬운 값이라 기본값으로 두지 않는다.
		assertNull(raw("{ \"type\": \"loot_bonus\", \"levels\": 5 }"));
		assertNull(raw("{ \"type\": \"loot_bonus\", \"levels\": 5, \"items\": [] }"));
	}

	@Test
	void 빈_손에는_걸리지_않는다() {
		LootBonusEffect effect = create("""
				{ "type": "loot_bonus", "levels": 5, "items": ["minecraft:diamond_hoe"] }
				""");

		assertFalse(effect.matches(ItemStack.EMPTY));
		assertFalse(effect.matches(null));
	}

	// ------------------------------------------------------------------ 판정부

	@Test
	void 팀이_없으면_추가_등급은_0_이다() {
		assertEquals(0, PerkLootRules.bonusLootingLevels(null));
	}

	// ------------------------------------------------------------------ 도우미

	private static LootBonusEffect create(String json) {
		return assertInstanceOf(LootBonusEffect.class, raw(json));
	}

	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.LOOT_BONUS.create("sharedfate:테스트", 0, parsed);
	}
}
