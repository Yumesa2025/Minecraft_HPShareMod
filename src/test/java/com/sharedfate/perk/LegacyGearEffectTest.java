package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.LegacyGearEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code legacy_gear}(프리즘 「유산」)의 정의 읽기와 몰수 실행을 본다.
 *
 * <h2>태그 판정 자체는 여기서 시험하지 않는다</h2>
 * <p>{@code GearPerkEffectTest}와 같은 이유다 — 아이템 태그는 데이터팩이 로드되어야 채워지는데
 * 단위 시험에는 데이터팩이 없다. 그래서 {@code mainItems}·{@code extraItems}·엔더상자를 훑는
 * 태그 판정 쪽은 "매칭되는 게 있다고 잘못 집지는 않는가"(가짜 양성이 없는가)만 확인하고,
 * 실제로 곡괭이 하나를 집어내는지는 살아 있는 서버에서 확인해야 한다({@code PROGRESS.md}
 * 확인 사항으로 남긴다). 반대로 <b>방어구 네 칸은 태그와 무관하게 슬롯을 직접 비우므로</b>
 * 그쪽은 실제 아이템으로 완전히 검증한다.
 */
class LegacyGearEffectTest {

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
	void 대상_태그는_언제나_고정이다() {
		LegacyGearEffect effect = assertInstanceOf(LegacyGearEffect.class,
				PerkEffectType.LEGACY_GEAR.create("sharedfate:테스트", 0,
						com.google.gson.JsonParser.parseString("{ \"type\": \"legacy_gear\" }")
								.getAsJsonObject()));

		assertEquals(1, effect.matcher().tags().size());
		assertEquals("sharedfate:legacy_gear",
				effect.matcher().tags().getFirst().location().toString());
		assertTrue(effect.matcher().itemIds().isEmpty(), "개별 아이템이 아니라 태그 하나로만 고정한다");
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.LEGACY_GEAR, PerkEffectType.fromId("legacy_gear"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		LegacyGearEffect effect = assertInstanceOf(LegacyGearEffect.class,
				PerkEffectType.LEGACY_GEAR.create("sharedfate:테스트", 0,
						com.google.gson.JsonParser.parseString("{ \"type\": \"legacy_gear\" }")
								.getAsJsonObject()));

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	// ------------------------------------------------------------------ 몰수 — 방어구

	@Test
	void 방어구_네_칸을_전부_비우고_보관한다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:유산", "rarity": "prism", "name": "유산",
				  "effects": [ { "type": "legacy_gear" } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		state.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		state.equipment.set(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		state.equipment.set(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		state.equipment.set(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));

		int seized = PerkLegacyGear.sacrificeOnChoice(null, null, state, perk);

		assertEquals(4, seized);
		assertTrue(state.equipment.isEmpty(), "지금 입고 있던 방어구는 모두 벗겨져야 한다");
		assertEquals(4, state.legacyGear.size());
		assertTrue(state.legacyGear.stream().anyMatch(stack -> stack.is(Items.DIAMOND_HELMET)));
		assertTrue(state.legacyGear.stream().anyMatch(stack -> stack.is(Items.DIAMOND_BOOTS)));
	}

	@Test
	void 방어구가_없으면_몰수할_것도_없다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:유산", "rarity": "prism", "name": "유산",
				  "effects": [ { "type": "legacy_gear" } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);

		assertEquals(0, PerkLegacyGear.sacrificeOnChoice(null, null, state, perk));
		assertTrue(state.legacyGear.isEmpty());
	}

	@Test
	void 태그와_무관한_아이템은_인벤토리에_그대로_남는다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:유산", "rarity": "prism", "name": "유산",
				  "effects": [ { "type": "legacy_gear" } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.STONE, 64));
		state.extraItems.set(0, new ItemStack(Items.DIRT, 32));
		state.enderContainer.setItem(0, new ItemStack(Items.ENDER_PEARL, 1));

		PerkLegacyGear.sacrificeOnChoice(null, null, state, perk);

		assertTrue(state.mainItems.get(0).is(Items.STONE), "가짜 양성으로 엉뚱한 아이템까지 집으면 안 된다");
		assertEquals(64, state.mainItems.get(0).getCount());
		assertTrue(state.extraItems.get(0).is(Items.DIRT));
		assertTrue(state.enderContainer.getItem(0).is(Items.ENDER_PEARL));
	}

	@Test
	void legacy_gear_가_없는_증강은_아무것도_몰수하지_않는다(@TempDir Path dir) throws IOException {
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:상관없음", "rarity": "gold", "name": "상관없음",
				  "effects": [ { "type": "damage_dealt", "multiplier": 1.2 } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		state.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

		assertEquals(0, PerkLegacyGear.sacrificeOnChoice(null, null, state, perk));
		assertTrue(state.equipment.get(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
				"관련 없는 증강이 방어구를 벗기면 안 된다");
	}

	@Test
	void 인자가_비어도_터지지_않는다() {
		assertEquals(0, PerkLegacyGear.sacrificeOnChoice(null, null, null, null));
		assertEquals(0, PerkLegacyGear.sacrificeOnChoice(null, null, TeamState.fresh(20.0F), null));
	}

	// ------------------------------------------------------------------ 회차 경계

	@Test
	void 몰수한_아이템은_전멸_초기화에서도_사라지지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.legacyGear.add(new ItemStack(Items.DIAMOND_PICKAXE));

		state.resetAfterDeath(20.0F, false);

		assertEquals(1, state.legacyGear.size(),
				"전멸을 넘겨야 뜻이 있는 값이라 resetAfterDeath 가 비우면 안 된다");
		assertTrue(state.legacyGear.getFirst().is(Items.DIAMOND_PICKAXE));
	}

	// ------------------------------------------------------------------ 도우미

	private static Perk loadSingle(Path dir, String perkJson) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME),
				"{ \"perks\": [" + perkJson + "] }", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		return PerkRegistry.all().getFirst();
	}
}
