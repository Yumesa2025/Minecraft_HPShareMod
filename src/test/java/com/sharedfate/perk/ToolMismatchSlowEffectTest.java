package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.ToolMismatchSlowEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code tool_mismatch_slow} 의 정의 읽기와 "무엇을 도구로 보는가" 판정을 본다.
 *
 * <p>속도를 실제로 깎는 자리는 속성 수정자라 살아 있는 {@code ServerPlayer} 가 있어야 한다.
 * 여기서는 그 앞의 판정 — 어떤 아이템이 도구인가, 그래서 느려지는가 — 만 본다.
 *
 * <h2>태그 판정은 반쪽만 시험할 수 있다</h2>
 * <p>아이템 태그는 데이터팩이 로드되어야 채워지는데 단위 시험에는 데이터팩이 없다.
 * 그래서 태그로 적은 여섯 갈래는 <b>이름이
 * 26.2 에 실재하는 태그와 정확히 같은지</b>까지 보고, 실제 걸림 여부는 이름으로 적은 넷
 * (활·석궁·삼지창·철퇴)으로 확인한다. 태그 이름은 26.2 의
 * {@code data/minecraft/tags/item/} 안에 그 파일이 있는 것을 확인하고 적은 값이다.
 */
class ToolMismatchSlowEffectTest {

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
	void multiplier_만_적으면_곡괭이가_우대_대상이_된다() {
		ToolMismatchSlowEffect effect =
				create("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -0.3 }");

		assertEquals(-0.3, effect.multiplier(), 1.0e-9);
		assertEquals(1, effect.preferred().tags().size());
		assertEquals(ToolMismatchSlowEffect.DEFAULT_PREFERRED_TAG,
				effect.preferred().tags().getFirst().location().toString());
		assertTrue(effect.preferred().itemIds().isEmpty());
	}

	@Test
	void 우대_대상을_바꿔_적을_수_있다() {
		ToolMismatchSlowEffect effect = create("""
				{ "type": "tool_mismatch_slow", "multiplier": -0.3,
				  "items": ["minecraft:bow"] }
				""");

		assertTrue(effect.preferred().tags().isEmpty());
		assertEquals(1, effect.preferred().itemIds().size());
		assertFalse(effect.mismatched(new ItemStack(Items.BOW)), "우대 대상은 깎지 않는다");
		assertTrue(effect.mismatched(new ItemStack(Items.TRIDENT)), "그 밖의 무기는 깎는다");
	}

	@Test
	void multiplier_가_없거나_범위를_벗어나면_버린다() {
		assertNull(raw("{ \"type\": \"tool_mismatch_slow\" }"));
		assertNull(raw("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": 0 }"),
				"아무것도 바꾸지 않는 정의는 조용한 함정이다");
		assertNull(raw("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": 0.3 }"),
				"이 타입은 느리게 하는 데만 쓴다");
		assertNull(raw("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -1.0 }"),
				"-1 이하면 발이 아예 묶인다");
	}

	@Test
	void 효과_순번마다_다른_수정자를_쓴다() {
		// 같은 식별자를 쓰면 형제 효과의 수정자를 덮어쓴다.
		ToolMismatchSlowEffect first = create("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -0.3 }");
		ToolMismatchSlowEffect second = assertInstanceOf(ToolMismatchSlowEffect.class,
				raw("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -0.3 }", 1));

		assertNotEquals(first.modifierId(), second.modifierId());
	}

	// ------------------------------------------------------------------ 도구의 범위

	@Test
	void 도구_태그는_26_2_에_있는_이름과_같다() {
		assertEquals(List.of(
						"minecraft:swords",
						"minecraft:axes",
						"minecraft:shovels",
						"minecraft:hoes",
						"minecraft:pickaxes",
						"minecraft:spears"),
				ToolMismatchSlowEffect.TOOL_TAGS);
		assertEquals(List.of(
						"minecraft:bow",
						"minecraft:crossbow",
						"minecraft:trident",
						"minecraft:mace"),
				ToolMismatchSlowEffect.TOOL_ITEMS);
	}

	@Test
	void 도구_묶음이_여섯_태그와_네_아이템을_가리킨다() {
		assertEquals(ToolMismatchSlowEffect.TOOL_TAGS.size(),
				ToolMismatchSlowEffect.tools().tags().size());
		assertEquals(ToolMismatchSlowEffect.TOOL_ITEMS.size(),
				ToolMismatchSlowEffect.tools().itemIds().size());
	}

	@Test
	void 태그가_없는_무기_넷은_도구로_친다() {
		assertTrue(ToolMismatchSlowEffect.isTool(new ItemStack(Items.BOW)));
		assertTrue(ToolMismatchSlowEffect.isTool(new ItemStack(Items.CROSSBOW)));
		assertTrue(ToolMismatchSlowEffect.isTool(new ItemStack(Items.TRIDENT)));
		assertTrue(ToolMismatchSlowEffect.isTool(new ItemStack(Items.MACE)));
	}

	@Test
	void 블록과_음식과_재료는_도구가_아니다() {
		assertFalse(ToolMismatchSlowEffect.isTool(new ItemStack(Items.DIRT)));
		assertFalse(ToolMismatchSlowEffect.isTool(new ItemStack(Items.BREAD)));
		assertFalse(ToolMismatchSlowEffect.isTool(new ItemStack(Items.DIAMOND)));
		assertFalse(ToolMismatchSlowEffect.isTool(new ItemStack(Items.OAK_PLANKS)));
		assertFalse(ToolMismatchSlowEffect.isTool(ItemStack.EMPTY));
		assertFalse(ToolMismatchSlowEffect.isTool(null));
	}

	// ------------------------------------------------------------------ 느려지는가

	@Test
	void 도구이면서_우대_대상이_아닐_때만_느려진다() {
		assertTrue(ToolMismatchSlowEffect.mismatched(true, false), "다른 도구를 들었다");
		assertFalse(ToolMismatchSlowEffect.mismatched(true, true), "우대 대상을 들었다");
		assertFalse(ToolMismatchSlowEffect.mismatched(false, false), "도구가 아니다");
		assertFalse(ToolMismatchSlowEffect.mismatched(false, true));
	}

	@Test
	void 빈_손과_도구가_아닌_물건은_느려지지_않는다() {
		ToolMismatchSlowEffect effect =
				create("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -0.3 }");

		assertFalse(effect.mismatched(ItemStack.EMPTY), "손을 비우라는 대가가 아니다");
		assertFalse(effect.mismatched(null));
		assertFalse(effect.mismatched(new ItemStack(Items.DIRT)));
		assertFalse(effect.mismatched(new ItemStack(Items.COOKED_BEEF)));
	}

	@Test
	void 곡괭이가_아닌_무기를_들면_느려진다() {
		ToolMismatchSlowEffect effect =
				create("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -0.3 }");

		assertTrue(effect.mismatched(new ItemStack(Items.BOW)));
		assertTrue(effect.mismatched(new ItemStack(Items.TRIDENT)));
	}

	@Test
	void 아직_아무에게도_적용하지_않았으면_기억이_없다() {
		ToolMismatchSlowEffect effect =
				create("{ \"type\": \"tool_mismatch_slow\", \"multiplier\": -0.3 }");

		assertNull(effect.appliedState(null));
		assertNull(effect.appliedState(java.util.UUID.randomUUID()));
	}

	// ------------------------------------------------------------------ 도우미

	private static ToolMismatchSlowEffect create(String json) {
		return assertInstanceOf(ToolMismatchSlowEffect.class, raw(json));
	}

	private static PerkEffect raw(String json) {
		return raw(json, 0);
	}

	private static PerkEffect raw(String json, int index) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.TOOL_MISMATCH_SLOW.create("sharedfate:테스트", index, parsed);
	}
}
