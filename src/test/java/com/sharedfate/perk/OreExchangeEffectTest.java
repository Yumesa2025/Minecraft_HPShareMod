package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.OreExchangeEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ore_exchange}(실버 「나무꾼의 욕심」)의 정의 읽기와 확률 계산({@link
 * OreExchangeEffect#rollResult})을 본다.
 *
 * <p>실제로 우클릭을 감지하고 공유 인벤토리에서 나무를 세고 빼는 것은
 * {@link com.sharedfate.perk.effect.PairedMiningEffect} 문서와 같은 이유(사건·태그 판정이
 * 데이터팩·살아 있는 서버를 필요로 함)로 여기서 다루지 않는다. 빈 인벤토리처럼 태그 판정
 * 자체가 필요 없는 경계값만 확인한다.
 */
class OreExchangeEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 필드가_없고_인스턴스를_돌려쓴다() {
		PerkEffect first = create();
		PerkEffect second = create();

		assertSame(OreExchangeEffect.INSTANCE, first);
		assertSame(first, second);
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.ORE_EXCHANGE, PerkEffectType.fromId("ore_exchange"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		OreExchangeEffect effect = assertInstanceOf(OreExchangeEffect.class, create());

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	@Test
	void 상수가_설명과_맞는다() {
		assertEquals(60, OreExchangeEffect.WOOD_COST);
		assertEquals(Identifier.withDefaultNamespace("wooden_axe"), OreExchangeEffect.TOOL);
		assertEquals(4, OreExchangeEffect.HUNGER_AMPLIFIER, "허기 V 는 amplifier 4 다");
		assertEquals(0, OreExchangeEffect.POISON_AMPLIFIER, "독 I 은 amplifier 0 이다");
		assertEquals(200, OreExchangeEffect.PENALTY_TICKS, "10초 = 200틱");
	}

	@Test
	void 확률의_합은_100이다() {
		assertEquals(100, OreExchangeEffect.TOTAL_WEIGHT);
		assertEquals(10 + 15 + 15 + 30 + 30, OreExchangeEffect.TOTAL_WEIGHT);
	}

	// ------------------------------------------------------------------ 확률 분포

	@Test
	void 결과_분포가_가중치와_거의_맞는다() {
		RandomSource random = RandomSource.create(20260901L);
		Map<Identifier, Integer> counts = new HashMap<>();
		int trials = 200_000;
		for (int i = 0; i < trials; i++) {
			Identifier result = OreExchangeEffect.rollResult(random);
			counts.merge(result, 1, Integer::sum);
		}

		for (OreExchangeEffect.Result expected : OreExchangeEffect.RESULTS) {
			double expectedFraction = expected.weight() / (double) OreExchangeEffect.TOTAL_WEIGHT;
			double actualFraction = counts.getOrDefault(expected.itemId(), 0) / (double) trials;
			assertTrue(Math.abs(expectedFraction - actualFraction) < 0.01,
					expected.itemId() + ": 기대 " + expectedFraction + ", 실제 " + actualFraction);
		}
	}

	@Test
	void 항상_다섯_결과_중_하나다() {
		RandomSource random = RandomSource.create(1L);
		for (int i = 0; i < 1000; i++) {
			Identifier result = OreExchangeEffect.rollResult(random);
			boolean known = OreExchangeEffect.RESULTS.stream()
					.anyMatch(r -> r.itemId().equals(result));
			assertTrue(known, result + " 는 RESULTS 에 없다");
		}
	}

	// ------------------------------------------------------------------ 나무 세기 경계값

	@Test
	void 빈_인벤토리는_나무가_0개다() {
		TeamState state = TeamState.fresh(20.0F);

		assertEquals(0, PerkOreExchange.countWood(state));
	}

	@Test
	void 나무_도끼만_도구로_인정한다() {
		assertTrue(PerkOreExchange.matchesTool(new ItemStack(Items.WOODEN_AXE)));
		assertFalse(PerkOreExchange.matchesTool(new ItemStack(Items.STONE_AXE)),
				"돌도끼는 나무 도끼가 아니다");
		assertFalse(PerkOreExchange.matchesTool(new ItemStack(Items.OAK_LOG)),
				"도끼가 아닌 아이템은 대상이 아니다");
		assertFalse(PerkOreExchange.matchesTool(ItemStack.EMPTY));
		assertFalse(PerkOreExchange.matchesTool(null));
	}

	private static PerkEffect create() {
		com.google.gson.JsonObject parsed = com.google.gson.JsonParser
				.parseString("{ \"type\": \"ore_exchange\" }").getAsJsonObject();
		return PerkEffectType.ORE_EXCHANGE.create("sharedfate:테스트", 0, parsed);
	}
}
