package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.SupplyDropEffect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code supply_drop} 의 정의 읽기·가중치 뽑기·주기 계산, 그리고 <b>누적 단계가 겹쳐도 보급이
 * 한 번만 오는가</b>를 본다.
 *
 * <p>이 시험의 표와 확률은 <b>실제로 세트 정의 파일에 들어갈 값 그대로</b>다. 값이 바뀌면
 * 여기도 함께 고쳐야 하고, 그래야 「파일에는 적었는데 뜻이 달랐다」를 잡을 수 있다.
 *
 * <p>게임을 띄우지 않는다. 아이템·물약 레지스트리와 아이템 컴포넌트만 있으면 되므로
 * {@link TestBootstrap#ensureInitialized()} 로 충분하다.
 */
class SupplyDropEffectTest {
	/** 시드를 고정한다. 뽑기 결과가 확률대로인지 보려면 난수가 재현돼야 한다. */
	private static final long SEED = 20260904L;

	/** 20 * 60. 1분. */
	private static final int MINUTE = SupplyDropEffect.TICKS_PER_MINUTE;

	// ------------------------------------------------------------------ 실제 값

	/**
	 * 「보급 2」의 뽑기 표. 가중치 합이 정확히 1000 이라 가중치가 곧 천분율이다.
	 *
	 * <p>합을 1000 으로 맞춰 두면 값을 손볼 때 확률을 암산할 수 있다. 강제되는 규칙은 아니고
	 * {@link #표_전체_가중치의_합이_1000이다} 가 지켜 준다.
	 */
	private static final String BASE_TABLE = """
			{ "id": "minecraft:coal",                   "min": 10, "max": 15, "weight": 120 },
			{ "id": "minecraft:copper_ingot",           "min": 10, "max": 15, "weight": 110 },
			{ "id": "minecraft:oak_log",                "min": 10, "max": 50, "weight": 110 },
			{ "id": "minecraft:iron_ingot",             "min": 3,  "max": 5,  "weight": 100 },
			{ "id": "minecraft:arrow",                  "min": 6,  "max": 12, "weight": 95 },
			{ "id": "minecraft:lapis_lazuli",           "min": 5,  "max": 7,  "weight": 85 },
			{ "id": "minecraft:cooked_beef",            "min": 10, "max": 15, "weight": 80 },
			{ "id": "minecraft:cooked_chicken",         "min": 10, "max": 15, "weight": 80 },
			{ "id": "minecraft:gold_ingot",             "min": 2,  "max": 3,  "weight": 65 },
			{ "id": "minecraft:experience_bottle",      "min": 1,  "max": 2,  "weight": 50 },
			{ "id": "minecraft:diamond",                "min": 1,  "max": 2,  "weight": 40 },
			{ "id": "minecraft:golden_apple",           "count": 1,           "weight": 25 },
			{ "id": "minecraft:potion",                 "count": 1,           "weight": 20,
			  "potion": "minecraft:fire_resistance", "duration_minutes": 1 },
			{ "id": "minecraft:cake",                   "count": 1,           "weight": 15 },
			{ "id": "minecraft:enchanted_golden_apple", "count": 1,           "weight": 4 },
			{ "id": "minecraft:netherite_ingot",        "count": 1,           "weight": 1 }""";

	/**
	 * 「보급 3·4」의 뽑기 표. 강화는 세 갈래를 모두 썼고 전부 JSON 에 적혀 있다.
	 *
	 * <ul>
	 *   <li>개수 범위 상향 — 다이아 1~2 → 2~4 처럼 표의 {@code min}/{@code max} 를 올렸다</li>
	 *   <li>희귀품 가중치 상향 — 네더라이트 1 → 10, 인챈트 황금 사과 4 → 20</li>
	 *   <li>물약 지속 1분 → 3분</li>
	 * </ul>
	 *
	 * <p>나머지 둘(뽑기 횟수 1 → 2, 꽝 확률 0.30 → 0.12)은 표가 아니라 효과 머리의
	 * {@code rolls}·{@code nothing_chance} 에 적혀 있다.
	 */
	private static final String RICH_TABLE = """
			{ "id": "minecraft:coal",                   "min": 12, "max": 20, "weight": 110 },
			{ "id": "minecraft:copper_ingot",           "min": 12, "max": 20, "weight": 100 },
			{ "id": "minecraft:oak_log",                "min": 20, "max": 64, "weight": 100 },
			{ "id": "minecraft:iron_ingot",             "min": 5,  "max": 8,  "weight": 95 },
			{ "id": "minecraft:arrow",                  "min": 12, "max": 24, "weight": 85 },
			{ "id": "minecraft:lapis_lazuli",           "min": 8,  "max": 12, "weight": 75 },
			{ "id": "minecraft:cooked_beef",            "min": 15, "max": 24, "weight": 70 },
			{ "id": "minecraft:cooked_chicken",         "min": 15, "max": 24, "weight": 70 },
			{ "id": "minecraft:gold_ingot",             "min": 4,  "max": 6,  "weight": 60 },
			{ "id": "minecraft:diamond",                "min": 2,  "max": 4,  "weight": 60 },
			{ "id": "minecraft:experience_bottle",      "min": 3,  "max": 6,  "weight": 55 },
			{ "id": "minecraft:golden_apple",           "min": 1,  "max": 2,  "weight": 45 },
			{ "id": "minecraft:potion",                 "count": 1,           "weight": 30,
			  "potion": "minecraft:fire_resistance", "duration_minutes": 3 },
			{ "id": "minecraft:enchanted_golden_apple", "count": 1,           "weight": 20 },
			{ "id": "minecraft:cake",                   "count": 1,           "weight": 15 },
			{ "id": "minecraft:netherite_ingot",        "min": 1,  "max": 2,  "weight": 10 }""";

	/** 보급 2 — 10분마다, 꽝 30%, 한 번 뽑기. */
	private static final String TIER_2 = supplyJson(10, 2, 0.30, 1, BASE_TABLE);
	/** 보급 3 — 주기는 그대로인데 내용이 좋아진다. */
	private static final String TIER_3 = supplyJson(10, 3, 0.12, 2, RICH_TABLE);
	/** 보급 4 — 3단계의 내용을 그대로 품고 주기만 5분이 된다. */
	private static final String TIER_4 = supplyJson(5, 4, 0.12, 2, RICH_TABLE);

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkSetRegistry.clear();
		PerkSupplyDrops.reset();
	}

	// ------------------------------------------------------------------ 정의 읽기 — 정상

	@Test
	void 주기와_우선순위와_꽝확률과_뽑기횟수를_읽는다() {
		SupplyDropEffect effect = supply(TIER_2);

		assertEquals(10, effect.intervalMinutes());
		assertEquals(10 * MINUTE, effect.intervalTicks());
		assertEquals(2, effect.priority());
		assertEquals(0.30, effect.nothingChance(), 1.0e-9);
		assertEquals(1, effect.rolls());
		assertEquals(16, effect.entries().size(), "보급 목록은 16종이다");
	}

	@Test
	void 개수_범위와_가중치를_읽는다() {
		SupplyDropEffect.Entry oak = entry(supply(TIER_2), "minecraft:oak_log");

		assertEquals(10, oak.minCount());
		assertEquals(50, oak.maxCount());
		assertEquals(110, oak.weight());
		assertNull(oak.potionId(), "원목은 물약이 아니다");
	}

	@Test
	void count_한_줄은_min과_max가_같다는_뜻이다() {
		SupplyDropEffect.Entry cake = entry(supply(TIER_2), "minecraft:cake");

		assertEquals(1, cake.minCount());
		assertEquals(1, cake.maxCount());
	}

	@Test
	void 물약은_종류와_지속시간을_들고_있다() {
		SupplyDropEffect.Entry potion = entry(supply(TIER_2), "minecraft:potion");

		assertNotNull(potion.potionId());
		assertEquals("minecraft:fire_resistance", potion.potionId().toString());
		assertEquals(1, potion.durationMinutes(), "2단계의 화염 저항은 1분이다");
		assertEquals(3, entry(supply(TIER_3), "minecraft:potion").durationMinutes(),
				"3단계에서 3분으로 늘어난다");
	}

	@Test
	void 표_전체_가중치의_합이_1000이다() {
		assertEquals(1000, supply(TIER_2).totalWeight(), "보급 2 — 가중치가 곧 천분율이어야 한다");
		assertEquals(1000, supply(TIER_3).totalWeight(), "보급 3");
		assertEquals(1000, supply(TIER_4).totalWeight(), "보급 4");
	}

	@Test
	void 안_적은_값은_기본값을_쓴다() {
		SupplyDropEffect effect = supply("""
				{ "type": "supply_drop", "interval_minutes": 10,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				""");

		assertEquals(0, effect.priority(), "priority 를 안 적으면 0");
		assertEquals(0.0, effect.nothingChance(), 1.0e-9, "nothing_chance 를 안 적으면 꽝이 없다");
		assertEquals(1, effect.rolls(), "rolls 를 안 적으면 한 번");
		assertEquals(1, effect.entries().getFirst().minCount(), "개수를 안 적으면 1개");
		assertEquals(1, effect.entries().getFirst().maxCount());
	}

	// ------------------------------------------------------------------ 정의 읽기 — 비정상

	@Test
	void 주기가_없거나_범위를_벗어나면_효과를_버린다() {
		assertNull(parse("""
				{ "type": "supply_drop",
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				"""), "interval_minutes 가 없다");
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 0,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				"""), "0분 주기");
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 9999,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				"""), "상한을 넘는 주기");
	}

	@Test
	void 꽝확률이_1이면_영원히_아무것도_안_오므로_버린다() {
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10, "nothing_chance": 1.0,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				"""));
	}

	@Test
	void 뽑기횟수가_범위를_벗어나면_버린다() {
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10, "rolls": 0,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				"""));
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10, "rolls": 99,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				"""));
	}

	@Test
	void entries_가_없거나_배열이_아니면_버린다() {
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10 }
				"""), "entries 자체가 없다");
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10, "entries": "다이아" }
				"""), "entries 가 배열이 아니다");
	}

	/** 빈 목록은 「줄 것이 하나도 없는 보급」이라 효과 자체를 버린다. */
	@Test
	void 빈_목록이면_효과를_버린다() {
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10, "entries": [] }
				"""));
	}

	/** 항목 하나가 잘못돼도 나머지는 살아남는다. */
	@Test
	void 잘못된_항목만_빠지고_나머지는_산다() {
		SupplyDropEffect effect = supply("""
				{ "type": "supply_drop", "interval_minutes": 10, "entries": [
				  { "id": "minecraft:diamond", "weight": 10 },
				  { "id": "minecraft:coal" },
				  { "weight": 5 },
				  { "id": "이건 이름이 아니다", "weight": 5 },
				  { "id": "minecraft:iron_ingot", "min": 9, "max": 3, "weight": 5 },
				  { "id": "minecraft:arrow", "min": 6, "max": 12, "weight": 20 } ] }
				""");

		assertEquals(2, effect.entries().size(), "다이아와 화살만 남는다");
		assertEquals(30, effect.totalWeight());
	}

	/** 살아남은 항목이 하나도 없으면 효과 자체를 버린다. */
	@Test
	void 쓸_만한_항목이_하나도_없으면_효과를_버린다() {
		assertNull(parse("""
				{ "type": "supply_drop", "interval_minutes": 10, "entries": [
				  { "id": "minecraft:coal" },
				  { "weight": 5 } ] }
				"""));
	}

	// ------------------------------------------------------------------ 가중치 뽑기

	/**
	 * 뽑기가 가중치대로다.
	 *
	 * <p>10 : 20 : 70 이면 10% : 20% : 70% 여야 한다. 시드를 고정했으므로 결과는 언제나 같다.
	 */
	@Test
	void 가중치대로_뽑는다() {
		SupplyDropEffect effect = supply("""
				{ "type": "supply_drop", "interval_minutes": 10, "entries": [
				  { "id": "minecraft:netherite_ingot", "weight": 10 },
				  { "id": "minecraft:diamond", "weight": 20 },
				  { "id": "minecraft:coal", "weight": 70 } ] }
				""");
		assertEquals(100, effect.totalWeight());

		int rounds = 200_000;
		int[] hits = new int[3];
		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < rounds; i++) {
			hits[effect.pickIndex(random)]++;
		}

		assertEquals(0.10, hits[0] / (double) rounds, 0.005, "네더라이트 10%");
		assertEquals(0.20, hits[1] / (double) rounds, 0.005, "다이아 20%");
		assertEquals(0.70, hits[2] / (double) rounds, 0.005, "석탄 70%");
	}

	/** 실제 표에서도 가중치가 곧 천분율이다. */
	@Test
	void 실제_보급표의_확률이_가중치와_맞는다() {
		SupplyDropEffect effect = supply(TIER_2);

		int rounds = 400_000;
		Map<String, Integer> hits = new HashMap<>();
		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < rounds; i++) {
			String id = effect.entries().get(effect.pickIndex(random)).itemId().toString();
			hits.merge(id, 1, Integer::sum);
		}

		for (SupplyDropEffect.Entry entry : effect.entries()) {
			double expected = entry.weight() / (double) effect.totalWeight();
			double actual = hits.getOrDefault(entry.itemId().toString(), 0) / (double) rounds;
			// 400,000번이면 1‰ 짜리 항목도 400회쯤 나온다. 허용폭은 넉넉히 잡는다.
			assertEquals(expected, actual, 0.004, entry.itemId() + " 의 확률");
		}
	}

	/** 「아주 낮은 확률」이 정말로 낮다. 두 항목을 합쳐도 표의 1% 미만이다. */
	@Test
	void 네더라이트와_인챈트_황금사과는_아주_낮은_확률이다() {
		SupplyDropEffect effect = supply(TIER_2);

		int rare = entry(effect, "minecraft:netherite_ingot").weight()
				+ entry(effect, "minecraft:enchanted_golden_apple").weight();

		assertTrue(rare / (double) effect.totalWeight() < 0.01,
				"희귀품 둘을 합쳐도 1% 미만이어야 한다: " + rare + "/" + effect.totalWeight());
	}

	// ------------------------------------------------------------------ 꽝 30%

	/** 꽝이 정확히 30% 다. 표의 가중치와 <b>따로</b> 굴리므로 표를 손봐도 이 값은 안 흔들린다. */
	@Test
	void 꽝은_정확히_삼십퍼센트다() {
		SupplyDropEffect effect = supply(TIER_2);

		int rounds = 200_000;
		int nothing = 0;
		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < rounds; i++) {
			if (effect.isNothing(random)) {
				nothing++;
			}
		}

		assertEquals(0.30, nothing / (double) rounds, 0.005);
	}

	/** 꽝인 회에는 아이템이 한 묶음도 안 나온다. */
	@Test
	void 꽝인_회에는_빈_목록이_나온다() {
		SupplyDropEffect effect = supply("""
				{ "type": "supply_drop", "interval_minutes": 10, "nothing_chance": 0.95,
				  "entries": [ { "id": "minecraft:diamond", "weight": 1 } ] }
				""");

		int rounds = 20_000;
		int empty = 0;
		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < rounds; i++) {
			if (effect.roll(random, null).isEmpty()) {
				empty++;
			}
		}

		assertEquals(0.95, empty / (double) rounds, 0.01);
	}

	@Test
	void 삼단계는_꽝이_줄어든다() {
		assertTrue(supply(TIER_3).nothingChance() < supply(TIER_2).nothingChance(),
				"3단계의 강화 중 하나가 꽝 확률 감소다");
		assertEquals(0.12, supply(TIER_3).nothingChance(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 실제로 나오는 것

	/** 뽑은 개수가 언제나 그 항목의 min~max 안이다. */
	@Test
	void 개수가_적어_둔_범위를_벗어나지_않는다() {
		SupplyDropEffect effect = supply(TIER_2);
		Map<String, SupplyDropEffect.Entry> byId = new HashMap<>();
		effect.entries().forEach(entry -> byId.put(entry.itemId().toString(), entry));

		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < 20_000; i++) {
			for (ItemStack stack : effect.roll(random, null)) {
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				SupplyDropEffect.Entry entry = byId.get(id.toString());
				assertNotNull(entry, "표에 없는 아이템이 나왔다: " + id);
				assertTrue(stack.getCount() >= entry.minCount()
								&& stack.getCount() <= entry.maxCount(),
						id + " 의 개수가 범위를 벗어났다: " + stack.getCount());
			}
		}
	}

	/** {@code rolls} 가 2 면 꽝이 아닌 회에 묶음이 둘 나온다. */
	@Test
	void 삼단계는_한_회에_두_묶음이_나온다() {
		SupplyDropEffect effect = supply(TIER_3);

		RandomSource random = RandomSource.create(SEED);
		int drops = 0;
		for (int i = 0; i < 2_000; i++) {
			List<ItemStack> drawn = effect.roll(random, null);
			if (!drawn.isEmpty()) {
				assertEquals(2, drawn.size(), "꽝이 아니면 두 묶음이다");
				drops++;
			}
		}
		assertTrue(drops > 0, "2000회를 굴렸는데 한 번도 안 왔다");
	}

	/** 물약은 화염 저항 컴포넌트를 달고 나오고, 지속시간은 적은 대로다. */
	@Test
	void 화염_저항_물약이_일분짜리로_나온다() {
		SupplyDropEffect effect = supply(TIER_2);
		SupplyDropEffect.Entry potion = entry(effect, "minecraft:potion");

		ItemStack stack = effect.stackFor(potion, 1, TestBootstrap.registries());
		assertNotNull(stack);

		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		assertNotNull(contents, "물약 컴포넌트가 안 붙었다");
		assertEquals(1, contents.customEffects().size());
		assertEquals(1 * 20 * 60, contents.customEffects().getFirst().getDuration(), "1분 = 1200틱");
	}

	/** 부를 때마다 새 묶음이다. 견본을 그대로 내보내면 두 번째 보급이 빈손이 된다. */
	@Test
	void 뽑을_때마다_새_묶음을_준다() {
		SupplyDropEffect effect = supply(TIER_2);
		SupplyDropEffect.Entry diamond = entry(effect, "minecraft:diamond");

		ItemStack first = effect.stackFor(diamond, 2, null);
		ItemStack second = effect.stackFor(diamond, 2, null);

		assertNotNull(first);
		assertNotNull(second);
		assertNotSame(first, second, "같은 객체를 두 번 주면 안 된다");
		first.setCount(0);
		assertEquals(2, second.getCount(), "하나를 비워도 다른 하나는 그대로다");
	}

	/**
	 * {@code apply}/{@code remove} 는 아무 일도 하지 않는다.
	 *
	 * <p>두 메서드는 접속·부활·효과 갱신마다 다시 불리므로 여기서 아이템을 주면 접속할 때마다
	 * 보급이 쏟아진다.
	 */
	@Test
	void 붙였다_떼는_일은_하지_않는다() {
		SupplyDropEffect effect = supply(TIER_2);

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
		assertEquals(1.0, effect.damageDealtMultiplier(), 1.0e-9);
		assertEquals(1.0, effect.damageTakenMultiplier(), 1.0e-9);
	}

	// ------------------------------------------------------------------ ⚠ 한 번만 돈다

	/**
	 * 「보급」을 넷 모으면 2·3·4 단계가 <b>전부</b> 켜지고 효과 목록에 {@code supply_drop} 이
	 * 셋 들어온다. 그것을 그대로 돌리면 보급이 세 번 온다. 실제로 도는 것은 언제나 하나다.
	 */
	@Test
	void 이단계_삼단계_사단계가_함께_켜져도_하나만_돈다() {
		SupplyDropEffect two = supply(TIER_2);
		SupplyDropEffect three = supply(TIER_3);
		SupplyDropEffect four = supply(TIER_4);

		SupplyDropEffect running = PerkSupplyDrops.select(List.of(two, three, four));

		assertSame(four, running, "가장 높은 단계 하나만 돈다");
	}

	/** 순서를 어떻게 섞어도 답이 같다. 효과 목록의 순서에 기대면 안 된다. */
	@Test
	void 목록_순서가_바뀌어도_같은_것이_돈다() {
		SupplyDropEffect two = supply(TIER_2);
		SupplyDropEffect three = supply(TIER_3);
		SupplyDropEffect four = supply(TIER_4);

		assertSame(four, PerkSupplyDrops.select(List.of(four, three, two)));
		assertSame(four, PerkSupplyDrops.select(List.of(three, four, two)));
		assertSame(four, PerkSupplyDrops.select(List.of(two, four, three)));
	}

	/** 셋 중 둘만 켜졌으면 그 둘 중 높은 것이 돈다. */
	@Test
	void 이단계와_삼단계만_켜지면_삼단계가_돈다() {
		SupplyDropEffect three = supply(TIER_3);

		assertSame(three, PerkSupplyDrops.select(List.of(supply(TIER_2), three)));
	}

	/** 우선순위가 같으면 주기가 짧은 쪽이 이긴다. 우선순위를 안 적어도 상식적인 답이 나온다. */
	@Test
	void 우선순위가_같으면_주기가_짧은_쪽이_돈다() {
		SupplyDropEffect slow = supply(supplyJson(10, 0, 0.0, 1, "{ \"id\": \"minecraft:coal\", \"weight\": 1 }"));
		SupplyDropEffect fast = supply(supplyJson(5, 0, 0.0, 1, "{ \"id\": \"minecraft:coal\", \"weight\": 1 }"));

		assertSame(fast, PerkSupplyDrops.select(List.of(slow, fast)));
		assertSame(fast, PerkSupplyDrops.select(List.of(fast, slow)));
	}

	@Test
	void 보급_효과가_하나도_없으면_돌_것이_없다() {
		assertNull(PerkSupplyDrops.select(null));
		assertNull(PerkSupplyDrops.select(List.of()));
		assertNull(PerkSupplyDrops.select(List.of(new PerkEffect() {
		})), "다른 종류의 효과만 있으면 없는 것과 같다");
	}

	@Test
	void 사단계가_켜지면_주기가_오분이다() {
		SupplyDropEffect running =
				PerkSupplyDrops.select(List.of(supply(TIER_2), supply(TIER_3), supply(TIER_4)));

		assertNotNull(running);
		assertEquals(5, running.intervalMinutes());
		assertEquals(5 * MINUTE, running.intervalTicks());
	}

	@Test
	void 사단계는_삼단계의_강화를_그대로_품고_있다() {
		SupplyDropEffect three = supply(TIER_3);
		SupplyDropEffect four = supply(TIER_4);

		// 4단계가 이기면 3단계 정의는 아예 안 돈다. 그래서 4단계가 3단계의 내용을 다 갖고 있어야 한다.
		assertEquals(three.nothingChance(), four.nothingChance(), 1.0e-9, "꽝 확률");
		assertEquals(three.rolls(), four.rolls(), "뽑기 횟수");
		assertEquals(three.entries(), four.entries(), "뽑기 표");
	}

	// ------------------------------------------------------------------ 주기

	/**
	 * 주기의 경계는 게임 시간의 배수라는 <b>절대적인 자리</b>다.
	 *
	 * <p>서버를 껐다 켜도 이 계산은 달라지지 않는다. {@code MinecraftServer#getTickCount()}
	 * 처럼 켤 때마다 0 부터 세는 값을 쓰면 재시작 시각이 곧 새 기준이 되어 경계가 통째로
	 * 옮겨진다.
	 */
	@Test
	void 주기_경계는_게임_시간만으로_정해진다() {
		int interval = 10 * MINUTE;

		assertEquals(0, PerkSupplyDrops.cycleAt(0, interval));
		assertEquals(0, PerkSupplyDrops.cycleAt(interval - 1, interval));
		assertEquals(1, PerkSupplyDrops.cycleAt(interval, interval));
		assertEquals(7, PerkSupplyDrops.cycleAt(interval * 7L + 3, interval));
		assertEquals(interval * 7L, PerkSupplyDrops.cycleStart(7, interval));

		for (long time = 0; time < 500_000; time += 997) {
			assertEquals(Math.floorDiv(time, (long) interval),
					PerkSupplyDrops.cycleAt(time, interval),
					"어느 시각에서 다시 계산해도 같은 답이어야 한다");
		}
	}

	/**
	 * 서버를 껐다 켜도 주기가 어긋나지 않는다.
	 *
	 * <p>재시작을 흉내 낸다. 게임 시간은 {@code level.dat} 에 저장돼 이어지므로 껐던 시각에서
	 * 그대로 다시 시작한다. 다음 보급 시각이 재시작 <b>전에 계산한 것과 같아야</b> 한다.
	 */
	@Test
	void 서버를_껐다_켜도_다음_보급_시각이_같다() {
		int interval = 10 * MINUTE;
		UUID team = UUID.randomUUID();

		long shutdownAt = interval * 3L + 4321;
		long nextBefore = PerkSupplyDrops.cycleStart(
				PerkSupplyDrops.cycleAt(shutdownAt, interval) + 1, interval);

		// 껐다 켠다. 기억은 사라지고 게임 시간만 남는다.
		PerkSupplyDrops.reset();
		assertNull(PerkSupplyDrops.lastCycleForTesting(team), "재시작 뒤에는 기억이 없다");

		long nextAfter = PerkSupplyDrops.cycleStart(
				PerkSupplyDrops.cycleAt(shutdownAt, interval) + 1, interval);

		assertEquals(nextBefore, nextAfter);
		assertEquals(interval * 4L, nextAfter, "경계는 언제나 주기의 배수다");
	}

	/** 서버를 켠 직후에는 지급하지 않는다. 안 그러면 재시작만으로 보급을 한 벌씩 더 받는다. */
	@Test
	void 처음_보는_팀에게는_지급하지_않고_기준만_잡는다() {
		int interval = 10 * MINUTE;
		UUID team = UUID.randomUUID();

		assertFalse(PerkSupplyDrops.advance(team, interval, interval * 5L),
				"주기 경계에 딱 걸린 시각이어도 첫 관찰은 지급하지 않는다");
		assertEquals(5L, PerkSupplyDrops.lastCycleForTesting(team));
	}

	@Test
	void 같은_주기_안에서는_한_번도_지급하지_않는다() {
		int interval = 10 * MINUTE;
		UUID team = UUID.randomUUID();

		PerkSupplyDrops.advance(team, interval, 0);
		for (long time = 1; time < interval; time++) {
			assertFalse(PerkSupplyDrops.advance(team, interval, time), time + "틱에서 지급됐다");
		}
	}

	@Test
	void 경계를_넘은_그_틱에만_한_번_지급한다() {
		int interval = 10 * MINUTE;
		UUID team = UUID.randomUUID();

		PerkSupplyDrops.advance(team, interval, 0);
		int given = 0;
		for (long time = 1; time <= interval * 3L; time++) {
			if (PerkSupplyDrops.advance(team, interval, time)) {
				given++;
			}
		}

		assertEquals(3, given, "30분 동안 10분 주기면 세 번이다");
	}

	/** 명령으로 시간을 크게 옮겨 여러 회를 건너뛰어도 보급은 한 벌이다. */
	@Test
	void 여러_회를_한꺼번에_건너뛰어도_한_번만_지급한다() {
		int interval = 10 * MINUTE;
		UUID team = UUID.randomUUID();

		PerkSupplyDrops.advance(team, interval, 0);
		assertTrue(PerkSupplyDrops.advance(team, interval, interval * 100L));
		assertFalse(PerkSupplyDrops.advance(team, interval, interval * 100L + 1));
	}

	/** 4단계가 켜져 주기가 10분에서 5분이 되면 번호의 뜻이 달라지므로 기준을 다시 잡는다. */
	@Test
	void 주기가_바뀐_틱에는_지급하지_않고_기준을_다시_잡는다() {
		UUID team = UUID.randomUUID();
		long time = 10 * MINUTE * 3L;

		PerkSupplyDrops.advance(team, 10 * MINUTE, time);
		assertFalse(PerkSupplyDrops.advance(team, 5 * MINUTE, time), "주기가 바뀐 그 틱은 지급하지 않는다");
		assertEquals(6L, PerkSupplyDrops.lastCycleForTesting(team), "5분 주기로 다시 센다");

		assertTrue(PerkSupplyDrops.advance(team, 5 * MINUTE, time + 5 * MINUTE),
				"그 다음 경계부터는 정상이다");
	}

	// ------------------------------------------------------------------ 세트 정의 파일

	/**
	 * <b>세트 정의 파일을 통째로 읽어 「세 단계가 켜지는데 도는 것은 하나」를 확인한다.</b>
	 *
	 * <p>⚠ 이 시험은 {@code PerkEffectType} 에 {@code SUPPLY_DROP("supply_drop", ...)} 한 줄이
	 * 들어가야 통과한다. 그 줄이 없으면 {@link PerkSetRegistry} 가 세 단계를 모두 조용히
	 * 버리므로(빌드도 통과하고 서버도 뜬다) 여기서 잡힌다.
	 */
	@Test
	void 세트_정의를_읽으면_세_단계가_켜지고_하나만_돈다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), setsJson(), StandardCharsets.UTF_8);
		PerkSetRegistry.load(dir);

		List<PerkSets.Tier> tiers = PerkSetRegistry.tiersOf(PerkSetType.SUPPLY);
		assertEquals(3, tiers.size(),
				"세 단계가 다 읽혀야 한다. 하나라도 없으면 PerkEffectType 등록이 빠진 것이다");
		for (PerkSets.Tier tier : tiers) {
			assertFalse(tier.isPlaceholder(), tier.count() + "단계에 효과가 없다");
		}

		// 「보급」 넷을 모은 상태. 단계는 누적이라 셋이 다 켜진다.
		List<PerkEffect> effects = PerkSets.activeEffects(
				Map.of(PerkSetType.SUPPLY, 4), Map.of(PerkSetType.SUPPLY, tiers));
		assertEquals(3, effects.size(), "2·3·4 단계가 전부 켜진다");

		SupplyDropEffect running = PerkSupplyDrops.select(effects);
		assertNotNull(running, "돌 것을 하나 골라야 한다");
		assertEquals(5, running.intervalMinutes(), "실제로 도는 것은 4단계 하나뿐이다");
		assertEquals(4, running.priority());
	}

	/** 둘만 모았으면 2단계 하나만 켜지고 그것이 돈다. */
	@Test
	void 둘만_모으면_이단계가_돈다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), setsJson(), StandardCharsets.UTF_8);
		PerkSetRegistry.load(dir);

		List<PerkEffect> effects = PerkSets.activeEffects(
				Map.of(PerkSetType.SUPPLY, 2),
				Map.of(PerkSetType.SUPPLY, PerkSetRegistry.tiersOf(PerkSetType.SUPPLY)));

		assertEquals(1, effects.size());
		SupplyDropEffect running = PerkSupplyDrops.select(effects);
		assertNotNull(running);
		assertEquals(10, running.intervalMinutes());
		assertEquals(0.30, running.nothingChance(), 1.0e-9);
	}

	// ------------------------------------------------------------------ 도우미

	/** 효과 하나를 만든다. 정의가 잘못됐으면 null. */
	private static PerkEffect parse(String json) {
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		return SupplyDropEffect.fromJson("시험", 0, object);
	}

	/** 효과 하나를 만든다. 만들지 못하면 시험이 실패한다. */
	private static SupplyDropEffect supply(String json) {
		PerkEffect effect = parse(json);
		assertNotNull(effect, "정의를 읽지 못했다: " + json);
		return assertInstanceOf(SupplyDropEffect.class, effect);
	}

	private static SupplyDropEffect.Entry entry(SupplyDropEffect effect, String itemId) {
		return effect.entries().stream()
				.filter(candidate -> candidate.itemId().toString().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("표에 " + itemId + " 이 없다"));
	}

	/** 단계 하나의 {@code supply_drop} 정의를 만든다. 실제 파일에 들어갈 모양 그대로다. */
	private static String supplyJson(int intervalMinutes, int priority, double nothingChance,
			int rolls, String table) {
		return """
				{
				  "type": "supply_drop",
				  "interval_minutes": %d,
				  "priority": %d,
				  "nothing_chance": %s,
				  "rolls": %d,
				  "entries": [
				%s
				  ]
				}""".formatted(intervalMinutes, priority,
				nothingChance == 0.0 ? "0" : String.valueOf(nothingChance), rolls, table);
	}

	/** 「보급」 세 단계만 든 세트 정의 파일. */
	private static String setsJson() {
		List<String> tiers = new ArrayList<>();
		tiers.add(tierJson(2, "하늘에서 떨어진다", "10분마다 무작위 보급이 옵니다. 아무것도 안 올 때도 있습니다.", TIER_2));
		tiers.add(tierJson(3, "짐이 무거워졌다", "보급으로 오는 것이 더 좋아집니다.", TIER_3));
		tiers.add(tierJson(4, "배송이 빨라졌다", "보급 주기가 5분으로 줄어듭니다.", TIER_4));
		return """
				{ "sets": [ { "type": "supply", "tiers": [
				%s
				] } ] }""".formatted(String.join(",\n", tiers));
	}

	private static String tierJson(int count, String name, String description, String effect) {
		return """
				{ "count": %d, "name": "%s", "description": "%s", "effects": [
				%s
				] }""".formatted(count, name, description, effect);
	}
}
