package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.ItemGrantEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code item_grant} 의 정의 읽기, 아이템·물약 해석, 그리고 지급이 팀 공유 목록에
 * 어떻게 들어가는지를 본다.
 *
 * <p>"딱 한 번만 준다"는 두 겹으로 지킨다. 하나는 {@link ItemGrantEffect#apply} 가 아무 일도
 * 하지 않는 것이고, 다른 하나는 {@link PerkItemGrants#grantOnChoice} 를 부르는 곳이
 * {@link PerkManager#applyChoice} 하나뿐인 것이다. 앞쪽은 여기서 확인하고, 뒤쪽은 살아 있는
 * 서버가 필요해 여기서 다루지 못하는 대신 {@code grantOnChoice} 를 한 번 부르면 정확히 한 벌만
 * 들어오고 두 번 부르면 두 벌이 들어오는 것을 확인한다.
 */
class ItemGrantEffectTest {

	@BeforeAll
	static void setUp() {
		// 아이템·물약 레지스트리와 아이템 컴포넌트를 보므로 최소한의 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 아이템_개수_물약을_읽는다() {
		ItemGrantEffect effect = itemGrant("""
				{
				  "type": "item_grant",
				  "items": [
				    { "id": "minecraft:golden_apple", "count": 5 },
				    { "id": "minecraft:potion", "count": 1, "potion": "minecraft:fire_resistance" }
				  ]
				}
				""");

		assertEquals(2, effect.entries().size());
		assertEquals(Identifier.parse("minecraft:golden_apple"), effect.entries().getFirst().itemId());
		assertEquals(5, effect.entries().getFirst().count());
		assertNull(effect.entries().getFirst().potionId());
		assertEquals(Identifier.parse("minecraft:fire_resistance"),
				effect.entries().get(1).potionId());
	}

	@Test
	void count_를_안_적으면_한_개다() {
		ItemGrantEffect effect = itemGrant("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:stone" } ] }
				""");

		assertEquals(1, effect.entries().getFirst().count());
	}

	@Test
	void items_가_없거나_배열이_아니면_버린다() {
		assertNull(create("{ \"type\": \"item_grant\" }"));
		assertNull(create("{ \"type\": \"item_grant\", \"items\": 3 }"));
		assertNull(create("{ \"type\": \"item_grant\", \"items\": [] }"));
	}

	@Test
	void 잘못된_항목_하나만_버리고_나머지는_살린다() {
		ItemGrantEffect effect = itemGrant("""
				{
				  "type": "item_grant",
				  "items": [
				    3,
				    { "count": 2 },
				    { "id": "이건::아이디가아니다" },
				    { "id": "minecraft:golden_apple", "count": 5 }
				  ]
				}
				""");

		assertEquals(1, effect.entries().size(), "쓸 만한 항목 하나만 남는다");
		assertEquals(Identifier.parse("minecraft:golden_apple"), effect.entries().getFirst().itemId());
	}

	@Test
	void 쓸_만한_항목이_하나도_없으면_버린다() {
		assertNull(create("""
				{ "type": "item_grant", "items": [ { "count": 2 }, 3 ] }
				"""), "줄 것이 없는 item_grant 는 효과가 아니다");
	}

	@Test
	void 개수_범위를_벗어난_항목은_버린다() {
		assertNull(create("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:stone", "count": 0 } ] }
				"""));
		assertNull(create("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:stone", "count": -1 } ] }
				"""));
		assertNull(create("""
				{ "type": "item_grant", "items": [
				  { "id": "minecraft:stone", "count": %d } ] }
				""".formatted(ItemGrantEffect.MAX_COUNT + 1)));
		assertNull(create("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:stone", "count": "다섯" } ] }
				"""));
	}

	@Test
	void 항목_수_상한을_넘으면_나머지를_버린다() {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < ItemGrantEffect.MAX_ENTRIES + 5; i++) {
			items.append(i == 0 ? "" : ",").append("{ \"id\": \"minecraft:stone\" }");
		}
		ItemGrantEffect effect = itemGrant(
				"{ \"type\": \"item_grant\", \"items\": [" + items + "] }");

		assertEquals(ItemGrantEffect.MAX_ENTRIES, effect.entries().size());
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.ITEM_GRANT, PerkEffectType.fromId("item_grant"));
		assertSame(PerkEffectType.ITEM_GRANT, PerkEffectType.fromId("  ITEM_GRANT  "));
	}

	// ------------------------------------------------------------------ 한 번만 주기

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		ItemGrantEffect effect = itemGrant("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:golden_apple", "count": 5 } ] }
				""");

		// 접속·부활·효과 갱신 때마다 불리는 자리다. 여기서 아이템을 주면 접속할 때마다 불어난다.
		// 플레이어가 null 이어도 손댈 것이 없으므로 그냥 지나가야 한다.
		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
		assertEquals(1.0, effect.damageDealtMultiplier());
		assertEquals(1.0, effect.damageTakenMultiplier());
	}

	@Test
	void 지급_묶음은_부를_때마다_새_사본이다() {
		ItemGrantEffect effect = itemGrant("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:golden_apple", "count": 5 } ] }
				""");

		ItemStack first = effect.grantStacks().getFirst();
		first.shrink(5);
		ItemStack second = effect.grantStacks().getFirst();

		assertNotSame(first, second);
		assertEquals(5, second.getCount(), "앞의 지급이 뒤의 지급을 갉아먹으면 안 된다");
	}

	// ------------------------------------------------------------------ 아이템·물약 해석

	@Test
	void 황금_사과_다섯_개를_만든다() {
		List<ItemStack> stacks = itemGrant("""
				{ "type": "item_grant", "items": [ { "id": "minecraft:golden_apple", "count": 5 } ] }
				""").grantStacks();

		assertEquals(1, stacks.size());
		assertSame(Items.GOLDEN_APPLE, stacks.getFirst().getItem());
		assertEquals(5, stacks.getFirst().getCount());
	}

	@Test
	void 물약_종류를_컴포넌트로_붙인다() {
		List<ItemStack> stacks = itemGrant("""
				{ "type": "item_grant", "items": [
				  { "id": "minecraft:potion", "potion": "minecraft:fire_resistance" },
				  { "id": "minecraft:potion", "potion": "minecraft:water_breathing" }
				] }
				""").grantStacks();

		assertEquals(2, stacks.size());
		assertEquals(Identifier.parse("minecraft:fire_resistance"), potionOf(stacks.get(0)));
		assertEquals(Identifier.parse("minecraft:water_breathing"), potionOf(stacks.get(1)));
	}

	@Test
	void 알_수_없는_아이템은_그_항목만_빠진다() {
		ItemGrantEffect effect = itemGrant("""
				{ "type": "item_grant", "items": [
				  { "id": "minecraft:not_a_real_item" },
				  { "id": "sharedfate:nope" },
				  { "id": "minecraft:golden_apple", "count": 5 }
				] }
				""");

		assertEquals(3, effect.entries().size(), "정의는 그대로 남는다");
		List<ItemStack> stacks = effect.grantStacks();
		assertEquals(1, stacks.size(), "찾을 수 있는 것만 지급한다");
		assertSame(Items.GOLDEN_APPLE, stacks.getFirst().getItem());
	}

	@Test
	void 알_수_없는_물약도_그_항목만_빠진다() {
		List<ItemStack> stacks = itemGrant("""
				{ "type": "item_grant", "items": [
				  { "id": "minecraft:potion", "potion": "minecraft:not_a_real_potion" },
				  { "id": "minecraft:potion", "potion": "minecraft:fire_resistance" }
				] }
				""").grantStacks();

		assertEquals(1, stacks.size());
		assertEquals(Identifier.parse("minecraft:fire_resistance"), potionOf(stacks.getFirst()));
	}

	// ------------------------------------------------------------------ 공유 목록 지급

	@Test
	void 지급한_아이템은_팀_공유_목록에_들어간다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:비상식량", "rarity": "silver", "name": "비상식량",
				  "effects": [ { "type": "item_grant",
				    "items": [ { "id": "minecraft:golden_apple", "count": 5 } ] } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);

		assertEquals(1, PerkItemGrants.grantOnChoice(null, null, state, perk));

		assertSame(Items.GOLDEN_APPLE, state.mainItems.getFirst().getItem());
		assertEquals(5, state.mainItems.getFirst().getCount());
		assertTrue(state.overflowItems.isEmpty(), "자리가 있으면 넘침 목록은 비어 있다");
	}

	@Test
	void 즉시_지급_증강은_한_회차에_한_번만_후보로_나온다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:비상식량", "rarity": "silver", "name": "비상식량",
				  "effects": [ { "type": "item_grant",
				    "items": [ { "id": "minecraft:golden_apple", "count": 5 } ] } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);

		PerkItemGrants.grantOnChoice(null, null, state, perk);
		state.ownedPerks.add(perk.id());

		// 중첩이 없으므로 한 번 고른 뒤에는 다시 뽑히지 않는다. 지급도 그만큼 한 번뿐이다.
		assertTrue(PerkDraft.draw(PerkRarity.SILVER, List.of(perk), state.ownedPerks,
				RandomSource.create(20260829L), 3).isEmpty());
		assertEquals(5, countOf(state, Items.GOLDEN_APPLE), "한 번 골랐으면 한 번만 준다");
	}

	@Test
	void 물약_두_종류가_각각_한_칸씩_들어간다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:원정준비물", "rarity": "silver", "name": "원정 준비물",
				  "effects": [ { "type": "item_grant", "items": [
				    { "id": "minecraft:potion", "count": 1, "potion": "minecraft:fire_resistance" },
				    { "id": "minecraft:potion", "count": 1, "potion": "minecraft:water_breathing" }
				  ] } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);

		assertEquals(2, PerkItemGrants.grantOnChoice(null, null, state, perk));

		assertEquals(Identifier.parse("minecraft:fire_resistance"), potionOf(state.mainItems.get(0)));
		assertEquals(Identifier.parse("minecraft:water_breathing"), potionOf(state.mainItems.get(1)));
		assertTrue(state.overflowItems.isEmpty());
	}

	@Test
	void 자리가_없으면_넘침_목록에_남고_칸이_비면_들어온다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:비상식량", "rarity": "silver", "name": "비상식량",
				  "effects": [ { "type": "item_grant",
				    "items": [ { "id": "minecraft:golden_apple", "count": 5 } ] } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		for (int slot = 0; slot < state.mainItems.size(); slot++) {
			state.mainItems.set(slot, new ItemStack(Items.STONE, 64));
		}

		PerkItemGrants.grantOnChoice(null, null, state, perk);

		assertEquals(1, state.overflowItems.size(), "바닥에 떨어뜨리지 않고 대기열에 남긴다");
		assertEquals(0, countOf(state, Items.GOLDEN_APPLE));

		state.mainItems.set(7, ItemStack.EMPTY);
		state.restoreOverflow(false);

		assertEquals(5, countOf(state, Items.GOLDEN_APPLE));
		assertTrue(state.overflowItems.isEmpty());
	}

	@Test
	void item_grant_가_없는_증강은_아무것도_주지_않는다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:상관없음", "rarity": "gold", "name": "상관없음",
				  "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);

		assertEquals(0, PerkItemGrants.grantOnChoice(null, null, state, perk));
		assertTrue(state.mainItems.stream().allMatch(ItemStack::isEmpty));
	}

	@Test
	void 인자가_비어도_터지지_않는다() {
		assertEquals(0, PerkItemGrants.grantOnChoice(null, null, null, null));
		assertEquals(0, PerkItemGrants.grantOnChoice(null, null, TeamState.fresh(20.0F), null));
	}

	// ------------------------------------------------------------------ 기본 풀

	@Test
	void 기본_풀의_즉시_지급_증강_두_개를_읽는다(@TempDir Path dir) {
		PerkRegistry.load(dir);

		Perk ration = PerkRegistry.byId("sharedfate:emergency_ration").orElseThrow();
		assertEquals("비상식량", ration.name());
		assertEquals(PerkRarity.SILVER, ration.rarity());
		List<ItemStack> rationStacks = grantEffectOf(ration).grantStacks();
		assertEquals(1, rationStacks.size());
		assertSame(Items.GOLDEN_APPLE, rationStacks.getFirst().getItem());
		assertEquals(5, rationStacks.getFirst().getCount());

		Perk kit = PerkRegistry.byId("sharedfate:expedition_kit").orElseThrow();
		assertEquals("원정 준비물", kit.name());
		assertEquals(PerkRarity.SILVER, kit.rarity());
		List<ItemStack> kitStacks = grantEffectOf(kit).grantStacks();
		assertEquals(2, kitStacks.size());
		assertEquals(Identifier.parse("minecraft:fire_resistance"), potionOf(kitStacks.get(0)));
		assertEquals(Identifier.parse("minecraft:water_breathing"), potionOf(kitStacks.get(1)));
	}

	// ------------------------------------------------------------------ 도우미

	private static ItemGrantEffect itemGrant(String json) {
		return assertInstanceOf(ItemGrantEffect.class, create(json));
	}

	private static PerkEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.ITEM_GRANT.create("sharedfate:테스트", 0, parsed);
	}

	private static ItemGrantEffect grantEffectOf(Perk perk) {
		for (PerkEffect effect : perk.effects()) {
			if (effect instanceof ItemGrantEffect grant) {
				return grant;
			}
		}
		throw new AssertionError(perk.id() + " 에 item_grant 효과가 없습니다");
	}

	private static Identifier potionOf(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		assertNotNull(contents, "물약 컴포넌트가 붙어 있어야 한다");
		return contents.potion().orElseThrow().unwrapKey().orElseThrow().identifier();
	}

	private static int countOf(TeamState state, net.minecraft.world.item.Item item) {
		int total = 0;
		for (ItemStack stack : state.mainItems) {
			if (stack.getItem() == item) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static Perk loadSingle(Path dir, String perkJson) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME),
				"{ \"perks\": [" + perkJson + "] }", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		return PerkRegistry.all().getFirst();
	}
}
