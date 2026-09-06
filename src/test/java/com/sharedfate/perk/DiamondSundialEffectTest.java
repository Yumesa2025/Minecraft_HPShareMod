package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DiamondSundialEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code diamond_sundial}(골드 「해시계」)의 정의 읽기, 아이템 표식 판정, 블록 후보 판정,
 * 반경·상한 계산을 본다.
 *
 * <p>우클릭을 잡고 청크를 훑고 파티클을 보내는 부분은 살아 있는 서버와 월드가 있어야 하므로
 * 여기서 다루지 않는다. 대신 그 계산에서 실제로 판단을 내리는 조각
 * ({@link PerkDiamondSundial.Found}, {@link DiamondSundialEffect#isDiamondOre},
 * {@link DiamondSundialEffect#isSundial})은 월드를 읽지 않게 떼어 두었으므로 전부 여기서 확인한다.
 */
class DiamondSundialEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 값을_적지_않으면_기본값이다() {
		DiamondSundialEffect effect = create("{ \"type\": \"diamond_sundial\" }");

		assertEquals(DiamondSundialEffect.DEFAULT_RADIUS, effect.radius());
		assertEquals(DiamondSundialEffect.DEFAULT_MAX_RESULTS, effect.maxResults());
		assertEquals(DiamondSundialEffect.DEFAULT_COOLDOWN_SECONDS * 20, effect.cooldownTicks());
	}

	@Test
	void 기본값이_설명과_맞는다() {
		assertEquals(20, DiamondSundialEffect.DEFAULT_RADIUS, "반경 기본값은 20칸이다");
		assertEquals(20, DiamondSundialEffect.DEFAULT_COOLDOWN_SECONDS, "쿨타임 기본값은 20초다");
		assertEquals(16, DiamondSundialEffect.DEFAULT_MAX_RESULTS, "한 번에 최대 16개다");
	}

	@Test
	void 적은_값을_그대로_읽는다() {
		DiamondSundialEffect effect = create("{ \"type\": \"diamond_sundial\", \"radius\": 12,"
				+ " \"cooldown_seconds\": 45, \"max_results\": 4 }");

		assertEquals(12, effect.radius());
		assertEquals(45 * 20, effect.cooldownTicks());
		assertEquals(4, effect.maxResults());
	}

	@Test
	void 카멜케이스로_적은_쿨타임도_읽는다() {
		DiamondSundialEffect effect =
				create("{ \"type\": \"diamond_sundial\", \"cooldownSeconds\": 30 }");

		assertEquals(30 * 20, effect.cooldownTicks());
	}

	@Test
	void 범위를_벗어난_값은_버리지_않고_자른다() {
		DiamondSundialEffect tooBig = create("{ \"type\": \"diamond_sundial\", \"radius\": 999,"
				+ " \"cooldown_seconds\": 99999, \"max_results\": 999 }");
		DiamondSundialEffect tooSmall = create("{ \"type\": \"diamond_sundial\", \"radius\": 0,"
				+ " \"cooldown_seconds\": 0, \"max_results\": 0 }");

		assertEquals(DiamondSundialEffect.MAX_RADIUS, tooBig.radius());
		assertEquals(DiamondSundialEffect.MAX_COOLDOWN_SECONDS * 20, tooBig.cooldownTicks());
		assertEquals(DiamondSundialEffect.MAX_MAX_RESULTS, tooBig.maxResults());

		assertEquals(DiamondSundialEffect.MIN_RADIUS, tooSmall.radius());
		assertEquals(DiamondSundialEffect.MIN_COOLDOWN_SECONDS * 20, tooSmall.cooldownTicks());
		assertEquals(DiamondSundialEffect.MIN_MAX_RESULTS, tooSmall.maxResults());
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		DiamondSundialEffect effect = create("{ \"type\": \"diamond_sundial\" }");

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	// ------------------------------------------------------------------ 26.2 에 있는 id 인가

	/**
	 * 아이템·블록 레지스트리는 기본값이 공기라 {@code get} 이 비어 있는지 보는 것으로는 없는
	 * id 를 잡아내지 못한다({@code ItemGrantEffect} 에 같은 함정이 적혀 있다). 그래서 반대로
	 * 블록·아이템에서 id 를 되뽑아 우리가 적어 둔 문자열과 같은지 본다.
	 */
	@Test
	void 쓰는_id_가_26_2_의_id_와_같다() {
		assertEquals(DiamondSundialEffect.ITEM, BuiltInRegistries.ITEM.getKey(Items.CLOCK));
		assertEquals(DiamondSundialEffect.ORE, BuiltInRegistries.BLOCK.getKey(Blocks.DIAMOND_ORE));
		assertEquals(DiamondSundialEffect.DEEPSLATE_ORE,
				BuiltInRegistries.BLOCK.getKey(Blocks.DEEPSLATE_DIAMOND_ORE));
	}

	// ------------------------------------------------------------------ 아이템 표식

	@Test
	void 지급한_해시계에_표식과_쿨타임_묶음이_붙는다() {
		DiamondSundialEffect effect = create("{ \"type\": \"diamond_sundial\" }");

		ItemStack stack = effect.createItem();

		assertNotNull(stack);
		assertEquals(Items.CLOCK, stack.getItem(), "새 아이템을 등록하지 않고 바닐라 시계를 쓴다");
		assertEquals(1, stack.getCount());
		assertTrue(DiamondSundialEffect.isSundial(stack));

		UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
		assertNotNull(cooldown, "쿨타임 묶음이 있어야 평범한 시계까지 잠기지 않는다");
		assertEquals(Optional.of(DiamondSundialEffect.COOLDOWN_GROUP), cooldown.cooldownGroup());
		assertEquals((float) DiamondSundialEffect.DEFAULT_COOLDOWN_SECONDS, cooldown.seconds(),
				0.001F);
	}

	@Test
	void 지급할_때마다_새_사본을_준다() {
		DiamondSundialEffect effect = create("{ \"type\": \"diamond_sundial\" }");

		ItemStack first = effect.createItem();
		ItemStack second = effect.createItem();

		assertNotNull(first);
		assertNotNull(second);
		assertNotSame(first, second, "견본을 그대로 넘기면 두 번째 지급이 빈 묶음이 된다");
		first.shrink(1);
		assertEquals(1, second.getCount(), "앞의 묶음을 깎아도 뒤의 묶음은 멀쩡해야 한다");
	}

	@Test
	void 표식이_없는_시계는_해시계가_아니다() {
		assertFalse(DiamondSundialEffect.isSundial(new ItemStack(Items.CLOCK)));
	}

	@Test
	void 이름만_바꾼_시계는_해시계가_아니다() {
		ItemStack fake = new ItemStack(Items.CLOCK);
		fake.set(DataComponents.CUSTOM_NAME, Component.literal("해시계"));

		assertFalse(DiamondSundialEffect.isSundial(fake),
				"모루로 이름만 바꿔서는 해시계가 만들어지지 않아야 한다");
	}

	@Test
	void 빈_묶음과_null_은_해시계가_아니다() {
		assertFalse(DiamondSundialEffect.isSundial(ItemStack.EMPTY));
		assertFalse(DiamondSundialEffect.isSundial(null));
	}

	@Test
	void 표식을_직접_붙여도_해시계로_읽는다() {
		ItemStack stack = DiamondSundialEffect.decorate(new ItemStack(Items.CLOCK), 20.0F);

		assertTrue(DiamondSundialEffect.isSundial(stack));
	}

	// ------------------------------------------------------------------ 블록 후보 판정

	@Test
	void 다이아몬드_광석_두_가지만_후보다() {
		assertTrue(DiamondSundialEffect.isDiamondOre(Blocks.DIAMOND_ORE.defaultBlockState()));
		assertTrue(DiamondSundialEffect.isDiamondOre(
				Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()));

		assertFalse(DiamondSundialEffect.isDiamondOre(Blocks.STONE.defaultBlockState()));
		assertFalse(DiamondSundialEffect.isDiamondOre(Blocks.DEEPSLATE.defaultBlockState()));
		assertFalse(DiamondSundialEffect.isDiamondOre(Blocks.IRON_ORE.defaultBlockState()),
				"다른 광석은 보이지 않아야 한다");
		assertFalse(DiamondSundialEffect.isDiamondOre(Blocks.DIAMOND_BLOCK.defaultBlockState()),
				"다이아몬드 블록은 광석이 아니다");
		assertFalse(DiamondSundialEffect.isDiamondOre(Blocks.AIR.defaultBlockState()));
		assertFalse(DiamondSundialEffect.isDiamondOre(null));
	}

	// ------------------------------------------------------------------ 반경과 상한

	@Test
	void 반경은_상자가_아니라_구다() {
		PerkDiamondSundial.Found found = new PerkDiamondSundial.Found(new BlockPos(0, 64, 0), 20, 16);

		assertTrue(found.inRange(0, 64, 20), "정확히 20칸은 안이다");
		assertTrue(found.inRange(0, 84, 0));
		assertFalse(found.inRange(0, 64, 21), "21칸은 밖이다");
		assertFalse(found.inRange(12, 76, 12),
				"상자 모서리(12,12,12)는 20칸 상자 안이지만 구 밖이다");
		assertTrue(found.inRange(11, 75, 11), "11³ 은 구 안이다");
	}

	@Test
	void 상한을_넘으면_더_받지_않는다() {
		int max = 4;
		PerkDiamondSundial.Found found = new PerkDiamondSundial.Found(new BlockPos(0, 0, 0), 64, max);
		int limit = max * PerkDiamondSundial.Found.OVERSCAN;

		int accepted = 0;
		while (found.add(0, accepted, 0)) {
			accepted++;
		}
		accepted++;

		assertEquals(limit, accepted, "상한만큼만 받는다");
		assertTrue(found.full());
		assertFalse(found.add(0, 100, 0), "가득 찬 뒤에는 계속 거절한다");
		assertEquals(max, found.hits().size(), "돌려주는 것은 max_results 개다");
	}

	@Test
	void 가까운_것부터_돌려준다() {
		PerkDiamondSundial.Found found = new PerkDiamondSundial.Found(new BlockPos(0, 0, 0), 64, 3);

		// 일부러 먼 것부터 넣는다. 훑는 순서는 청크·섹션 순이라 거리순이 아니기 때문이다.
		found.add(0, 0, 30);
		found.add(0, 0, 20);
		found.add(0, 0, 10);
		found.add(0, 0, 5);
		found.add(0, 0, 1);

		List<BlockPos> hits = found.hits();

		assertEquals(3, hits.size());
		assertEquals(new BlockPos(0, 0, 1), hits.get(0));
		assertEquals(new BlockPos(0, 0, 5), hits.get(1));
		assertEquals(new BlockPos(0, 0, 10), hits.get(2));
	}

	@Test
	void 하나도_못_찾으면_빈_목록이다() {
		PerkDiamondSundial.Found found = new PerkDiamondSundial.Found(new BlockPos(0, 0, 0), 20, 16);

		assertTrue(found.hits().isEmpty());
		assertFalse(found.full());
	}

	// ------------------------------------------------------------------ 도우미

	private static DiamondSundialEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(DiamondSundialEffect.class,
				DiamondSundialEffect.fromJson("sharedfate:테스트", 0, parsed));
	}
}
