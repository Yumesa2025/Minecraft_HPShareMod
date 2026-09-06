package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DropReplaceEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 골드 「비옥한 땅」이 쓰는 {@code drop_replace} 를 본다 — 정의 읽기, 전리품 가르기, 개수 분포,
 * 그리고 이 효과가 기대고 있는 26.2 쪽 사실들.
 *
 * <p>실제로 블록이 부서지고 아이템이 공유 인벤토리로 들어가는 자리는
 * {@link PerkBlockBreaks#replaceDrops} 이고, 그건 살아 있는 월드가 있어야 확인할 수 있다.
 * 여기서는 그 코드가 부르는 순수한 판단들만 본다 — 어떤 블록에 걸리는가, 어떤 묶음을 걷어
 * 내는가, 몇 개를 주는가.
 *
 * <p>전리품 목록은 손으로 만든다. 밀 작물의 전리품표는 데이터팩이 올라와야 굴릴 수 있고, 그
 * 표가 무엇을 내놓는지는 아래 「전리품표」 주석에 바닐라 정의 그대로 적어 두었다.
 */
class FertileGroundDropReplaceTest {

	/**
	 * 「비옥한 땅」의 실제 정의. 기본 증강 풀에 들어가는 것과 같은 값이다.
	 *
	 * <h2>전리품표</h2>
	 * <p>{@code data/minecraft/loot_table/blocks/wheat.json} 은 두 묶음(pool)으로 되어 있다.
	 * 첫 묶음은 {@code age=7} 이면 밀 1개, 아니면 씨앗 1개다. 둘째 묶음은 {@code age=7} 일 때만
	 * 돌고 씨앗을 0~3개(행운이 붙으면 더) 준다. 그래서 <b>밀 아이템은 다 자랐을 때만 나오고,
	 * 씨앗은 언제나 나온다.</b>
	 */
	private static final String FERTILE_GROUND = """
			{
			  "type": "drop_replace",
			  "blocks": ["minecraft:wheat"],
			  "from": "minecraft:wheat",
			  "item": "minecraft:golden_carrot",
			  "min": 1,
			  "max": 3
			}
			""";

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 비옥한_땅의_정의를_읽는다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertEquals("minecraft:wheat", effect.fromId().toString());
		assertEquals("minecraft:golden_carrot", effect.itemId().toString());
		assertEquals(1, effect.min());
		assertEquals(3, effect.max());
		assertEquals(1, effect.blocks().blockIds().size());
		assertTrue(effect.blocks().tags().isEmpty());
		assertFalse(effect.blocks().matchesEverything(), "모든 블록에 걸리면 안 된다");
	}

	@Test
	void 적은_이름이_실제_아이템으로_이어진다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertSame(Items.WHEAT, effect.fromItem());
		assertSame(Items.GOLDEN_CARROT, effect.grantItem());
	}

	@Test
	void 개수를_안_적으면_한_개다() {
		DropReplaceEffect effect = create("""
				{ "type": "drop_replace", "blocks": ["minecraft:wheat"],
				  "from": "minecraft:wheat", "item": "minecraft:golden_carrot" }
				""");

		assertEquals(1, effect.min());
		assertEquals(1, effect.max(), "min 만 적으면 그 값으로 고정된다");
		assertEquals(1, effect.spread());
	}

	@Test
	void min_만_적으면_max_가_따라온다() {
		DropReplaceEffect effect = create("""
				{ "type": "drop_replace", "blocks": ["minecraft:wheat"],
				  "from": "minecraft:wheat", "item": "minecraft:golden_carrot", "min": 4 }
				""");

		assertEquals(4, effect.min());
		assertEquals(4, effect.max());
	}

	// ------------------------------------------------------------------ 잘못된 정의는 버린다

	@Test
	void 걷어_낼_것이나_줄_것을_안_적으면_버린다() {
		assertNull(raw("""
				{ "type": "drop_replace", "blocks": ["minecraft:wheat"],
				  "item": "minecraft:golden_carrot" }
				"""), "from 이 없으면 무엇을 없앨지 알 수 없다");
		assertNull(raw("""
				{ "type": "drop_replace", "blocks": ["minecraft:wheat"],
				  "from": "minecraft:wheat" }
				"""), "item 이 없으면 대신 줄 것이 없다");
	}

	@Test
	void 아이템_이름_꼴이_아니면_버린다() {
		assertNull(raw("""
				{ "type": "drop_replace", "from": "대문자 안 됨",
				  "item": "minecraft:golden_carrot" }
				"""));
		assertNull(raw("""
				{ "type": "drop_replace", "from": "minecraft:wheat", "item": "대문자 안 됨" }
				"""));
	}

	@Test
	void 개수가_숫자가_아니면_버린다() {
		// 조용히 기본값으로 되돌리면 정의를 고친 사람은 값이 반영된 줄 알고 지나간다.
		assertNull(raw("""
				{ "type": "drop_replace", "from": "minecraft:wheat",
				  "item": "minecraft:golden_carrot", "min": "하나", "max": 3 }
				"""));
		assertNull(raw("""
				{ "type": "drop_replace", "from": "minecraft:wheat",
				  "item": "minecraft:golden_carrot", "min": 1, "max": "셋" }
				"""));
	}

	@Test
	void 개수가_범위를_벗어나면_버린다() {
		assertNull(counted(0, 3), "0개를 주는 정의는 아무 일도 하지 않는다");
		assertNull(counted(-1, 3));
		assertNull(counted(3, 1), "max 가 min 보다 작을 수는 없다");
		assertNull(counted(1, DropReplaceEffect.MAX_COUNT + 1));
	}

	@Test
	void 대상_블록이_비면_버린다() {
		assertNull(raw("""
				{ "type": "drop_replace", "blocks": [], "from": "minecraft:wheat",
				  "item": "minecraft:golden_carrot" }
				"""));
		assertNull(raw("""
				{ "type": "drop_replace", "blocks": ["대문자 안 됨"], "from": "minecraft:wheat",
				  "item": "minecraft:golden_carrot" }
				"""));
	}

	@Test
	void 없는_아이템을_적으면_줄_것이_없다() {
		// 정의 자체는 읽힌다 — 정의를 읽는 시점에는 아이템 레지스트리가 아직 없을 수 있어
		// 이름의 꼴만 본다. 실제로 찾을 수 없다는 것은 처음 쓸 때 경고와 함께 드러난다.
		DropReplaceEffect effect = create("""
				{ "type": "drop_replace", "from": "sharedfate:missing_item",
				  "item": "sharedfate:missing_item" }
				""");

		assertNull(effect.fromItem());
		assertNull(effect.grantItem());
		assertTrue(effect.grantStacks(3).isEmpty(), "못 찾은 아이템을 공기로 주지 않는다");
		assertFalse(effect.replaces(new ItemStack(Items.WHEAT)), "못 찾았으면 아무것도 걷어 내지 않는다");
	}

	// ------------------------------------------------------------------ 어떤 블록에 걸리는가

	@Test
	void 밀에만_걸리고_다른_작물에는_안_걸린다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertTrue(effect.appliesTo(Blocks.WHEAT.defaultBlockState()));
		assertFalse(effect.appliesTo(Blocks.CARROTS.defaultBlockState()));
		assertFalse(effect.appliesTo(Blocks.POTATOES.defaultBlockState()));
		assertFalse(effect.appliesTo(Blocks.BEETROOTS.defaultBlockState()));
		assertFalse(effect.appliesTo(Blocks.MELON_STEM.defaultBlockState()));
		assertFalse(effect.appliesTo(Blocks.STONE.defaultBlockState()));
		assertFalse(effect.appliesTo(null));
	}

	@Test
	void 자란_정도와_상관없이_같은_블록이다() {
		// 「다 자란 것만」은 블록으로 가르지 않는다. 덜 자란 밀도 이 효과의 대상 블록이고,
		// 전리품에 밀이 없어서 아무 일도 일어나지 않을 뿐이다.
		DropReplaceEffect effect = create(FERTILE_GROUND);
		CropBlock wheat = assertInstanceOf(CropBlock.class, Blocks.WHEAT);

		for (int age = 0; age <= wheat.getMaxAge(); age++) {
			assertTrue(effect.appliesTo(wheat.getStateForAge(age)), age + "단계도 같은 블록이다");
		}
	}

	// ------------------------------------------------------------------ 전리품 가르기

	@Test
	void 다_자란_밀에서_밀만_걷히고_씨앗은_남는다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		DropReplaceEffect.Outcome outcome = effect.filter(List.of(
				new ItemStack(Items.WHEAT, 1),
				new ItemStack(Items.WHEAT_SEEDS, 2)));

		assertEquals(1, outcome.removed(), "밀 1개가 걷혔다");
		assertEquals(1, outcome.kept().size());
		assertSame(Items.WHEAT_SEEDS, outcome.kept().getFirst().getItem(),
				"씨앗까지 없애면 다음 농사를 지을 수 없다");
		assertEquals(2, outcome.kept().getFirst().getCount(), "씨앗 개수는 건드리지 않는다");
	}

	@Test
	void 덜_자란_밀은_손대지_않는다() {
		// 덜 자란 밀은 씨앗 1개만 떨어뜨린다. 걷어 낼 밀이 없으므로 주는 것도 없다.
		DropReplaceEffect effect = create(FERTILE_GROUND);

		DropReplaceEffect.Outcome outcome =
				effect.filter(List.of(new ItemStack(Items.WHEAT_SEEDS, 1)));

		assertEquals(0, outcome.removed(), "걷어 낸 것이 없으면 대신 줄 것도 없다");
		assertEquals(1, outcome.kept().size());
		assertSame(Items.WHEAT_SEEDS, outcome.kept().getFirst().getItem());
	}

	@Test
	void 행운으로_늘어난_씨앗도_그대로_남는다() {
		// 행운은 씨앗 쪽 묶음에만 붙는다. 밀은 언제나 1개라 행운이 붙어도 갈아 끼우는 양은
		// 정의에 적힌 범위 그대로다.
		DropReplaceEffect effect = create(FERTILE_GROUND);

		DropReplaceEffect.Outcome outcome = effect.filter(List.of(
				new ItemStack(Items.WHEAT, 1),
				new ItemStack(Items.WHEAT_SEEDS, 6)));

		assertEquals(1, outcome.removed());
		assertEquals(6, outcome.kept().getFirst().getCount());
	}

	@Test
	void 빈_목록과_빈_묶음은_그냥_지나간다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertEquals(0, effect.filter(null).removed());
		assertTrue(effect.filter(null).kept().isEmpty());
		assertEquals(0, effect.filter(List.of()).removed());

		DropReplaceEffect.Outcome outcome = effect.filter(List.of(ItemStack.EMPTY));
		assertEquals(0, outcome.removed());
		assertTrue(outcome.kept().isEmpty(), "빈 묶음을 떨어뜨릴 이유가 없다");
	}

	@Test
	void 걷어_낸_개수는_묶음_수가_아니라_아이템_수다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertEquals(5, effect.filter(List.of(new ItemStack(Items.WHEAT, 5))).removed());
	}

	// ------------------------------------------------------------------ 개수 뽑기

	@Test
	void 뽑은_값이_구간_경계에서_넘어간다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertEquals(3, effect.spread(), "1·2·3 세 가지다");
		assertEquals(1, effect.countForRoll(0));
		assertEquals(2, effect.countForRoll(1));
		assertEquals(3, effect.countForRoll(2));
	}

	@Test
	void 범위를_벗어난_값도_안전하다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertEquals(1, effect.countForRoll(-1));
		assertEquals(3, effect.countForRoll(1000));
	}

	@Test
	void 실제로_굴리면_1_부터_3_까지_고르게_나온다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);
		RandomSource random = RandomSource.create(20260906L);

		int rolls = 200_000;
		int[] counts = new int[4];
		for (int i = 0; i < rolls; i++) {
			int drawn = effect.rollCount(random);
			assertTrue(drawn >= 1 && drawn <= 3, "1~3 밖의 값이 나왔습니다: " + drawn);
			counts[drawn]++;
		}

		for (int count = 1; count <= 3; count++) {
			assertEquals(1.0 / 3.0, counts[count] / (double) rolls, 0.01,
					count + "개가 나오는 비율이 치우쳤습니다");
		}
	}

	@Test
	void 난수원이_없으면_가장_적게_준다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertEquals(1, effect.rollCount(null), "난수를 못 굴렸다고 큰 쪽을 줄 수는 없다");
	}

	// ------------------------------------------------------------------ 지급 묶음

	@Test
	void 뽑은_개수만큼_한_묶음으로_준다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		List<ItemStack> stacks = effect.grantStacks(3);
		assertEquals(1, stacks.size());
		assertSame(Items.GOLDEN_CARROT, stacks.getFirst().getItem());
		assertEquals(3, stacks.getFirst().getCount());
	}

	@Test
	void 한_칸_최대치를_넘으면_나눠_담는다() {
		// 넘침 대기열은 한도를 넘는 묶음을 저장하지 못한다. 미리 나누지 않으면 서버를 껐다
		// 켜는 순간 통째로 사라진다.
		DropReplaceEffect effect = create("""
				{ "type": "drop_replace", "from": "minecraft:wheat",
				  "item": "minecraft:golden_carrot", "min": 64, "max": 64 }
				""");

		List<ItemStack> stacks = effect.grantStacks(100);
		assertEquals(2, stacks.size());
		assertEquals(64, stacks.get(0).getCount());
		assertEquals(36, stacks.get(1).getCount());
	}

	@Test
	void 개수가_0_이하면_아무것도_주지_않는다() {
		DropReplaceEffect effect = create(FERTILE_GROUND);

		assertTrue(effect.grantStacks(0).isEmpty());
		assertTrue(effect.grantStacks(-1).isEmpty());
	}

	// ------------------------------------------------------------------ 알림 문구

	@Test
	void 문구는_증강_이름과_아이템과_개수를_잇는다() {
		Component message = DropReplaceEffect.announcement(
				"비옥한 땅", Component.literal("황금 당근"), 2);

		assertEquals("[증강] 비옥한 땅 — 황금 당근 ×2", message.getString());
	}

	@Test
	void 이름이_없어도_문구가_만들어진다() {
		assertEquals("[증강] — ×1",
				DropReplaceEffect.announcement(null, null, 1).getString());
		assertEquals("[증강] — ×1",
				DropReplaceEffect.announcement("  ", null, 1).getString());
	}

	// ------------------------------------------------------------------ 26.2 쪽 사실

	/**
	 * {@code BlockDropReplaceMixin} 이 파고드는 자리.
	 *
	 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드가 그냥
	 * 통과</b>하고 서버를 띄우는 순간 터진다. 그래서 여기서 붙들어 둔다.
	 */
	@Test
	void 전리품을_떨어뜨리는_자리가_그대로_있다() {
		Method dropResources = declared(Block.class, "dropResources",
				BlockState.class, Level.class, BlockPos.class, BlockEntity.class,
				Entity.class, ItemStack.class);

		assertEquals(void.class, dropResources.getReturnType());
		assertTrue(Modifier.isStatic(dropResources.getModifiers()),
				"정적이 아니게 되면 처리기도 정적이 아니어야 한다");
	}

	/**
	 * 취소하고 대신 처리할 때 흉내 내야 하는 것 둘.
	 *
	 * <p>{@code dropResources} 가 하는 일은 전리품 목록을 떨어뜨리는 것과 {@code spawnAfterBreak}
	 * 를 부르는 것뿐이다. 둘 중 하나라도 서술자가 바뀌면 {@code PerkBlockBreaks.replaceDrops} 가
	 * 바닐라와 다른 일을 하게 된다.
	 */
	@Test
	void 대신_처리할_때_흉내_내는_두_가지가_그대로_있다() {
		Method getDrops = declared(Block.class, "getDrops",
				BlockState.class, ServerLevel.class, BlockPos.class, BlockEntity.class,
				Entity.class, net.minecraft.world.item.ItemInstance.class);
		assertEquals(List.class, getDrops.getReturnType());
		assertTrue(Modifier.isStatic(getDrops.getModifiers()));

		Method spawnAfterBreak = declared(
				net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class,
				"spawnAfterBreak", ServerLevel.class, BlockPos.class, ItemStack.class, boolean.class);
		assertEquals(void.class, spawnAfterBreak.getReturnType());
		assertTrue(Modifier.isPublic(spawnAfterBreak.getModifiers()),
				"공개가 아니게 되면 모드가 직접 부를 수 없다");
	}

	/**
	 * 「다 자란 밀」의 뜻이 기대는 26.2 쪽 사실.
	 *
	 * <p>이 효과는 자란 정도를 직접 보지 않는다. 그래도 「다 자랐을 때만 밀이 나온다」는 전제가
	 * 바닐라 쪽에서 무너지면 결과가 통째로 달라지므로, 그 전제가 서 있는 자리를 확인해 둔다.
	 */
	@Test
	void 밀은_자라는_작물이고_다_자람을_판정하는_길이_있다() {
		CropBlock wheat = assertInstanceOf(CropBlock.class, Blocks.WHEAT);

		assertEquals(7, CropBlock.MAX_AGE);
		assertEquals(7, wheat.getMaxAge());
		assertFalse(wheat.isMaxAge(wheat.getStateForAge(6)));
		assertTrue(wheat.isMaxAge(wheat.getStateForAge(7)));
		assertEquals(0, wheat.getAge(wheat.defaultBlockState()), "심자마자는 0단계다");
	}

	// ------------------------------------------------------------------ 도우미

	private static Method declared(Class<?> owner, String name, Class<?>... parameters) {
		try {
			return owner.getDeclaredMethod(name, parameters);
		} catch (NoSuchMethodException missing) {
			throw new AssertionError(
					owner.getSimpleName() + "." + name + " 의 서술자가 바뀌었다. mixin 대상을 고칠 것",
					missing);
		}
	}

	private static DropReplaceEffect create(String json) {
		DropReplaceEffect effect = assertInstanceOf(DropReplaceEffect.class, raw(json));
		assertNotNull(effect);
		return effect;
	}

	/**
	 * 정의를 읽는다.
	 *
	 * <p>{@code PerkEffectType} 을 거치지 않고 팩토리를 곧바로 부른다. 등록 줄은 메인이 넣는다.
	 */
	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return DropReplaceEffect.fromJson("sharedfate:테스트", 0, parsed);
	}

	private static PerkEffect counted(int min, int max) {
		return raw("{ \"type\": \"drop_replace\", \"from\": \"minecraft:wheat\","
				+ " \"item\": \"minecraft:golden_carrot\","
				+ " \"min\": " + min + ", \"max\": " + max + " }");
	}
}
