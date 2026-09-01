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
	void 방어구_네_칸을_전부_비우지만_승계_목록은_건드리지_않는다(@TempDir Path dir) throws IOException {
		// 2026-09-01 7차부터: 다음 회차로 넘어가는 기준은 고르는 시점이 아니라 전멸하는
		// 시점이다(PerkLegacyGear.captureAtDeath). 그래서 몰수는 없애기만 하고
		// state.legacyGear 는 손대지 않는다.
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
		assertTrue(state.legacyGear.isEmpty(),
				"몰수는 승계 목록을 채우지 않는다 — 그건 전멸하는 시점의 몫이다");
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

	// ------------------------------------------------------------------ 전멸 시점 승계 (captureAtDeath)

	@Test
	void 전멸_시점에_가진_장비를_스냅샷으로_남긴다(@TempDir Path dir) throws IOException {
		loadSingle(dir, """
				{ "id": "sharedfate:유산", "rarity": "prism", "name": "유산",
				  "effects": [ { "type": "legacy_gear" } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:유산");
		state.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

		int captured = PerkLegacyGear.captureAtDeath(state);

		assertEquals(1, captured);
		assertEquals(1, state.legacyGear.size());
		assertTrue(state.legacyGear.getFirst().is(Items.DIAMOND_HELMET));
	}

	@Test
	void 고를_때_없던_장비도_전멸_시점에_새로_갖췄으면_넘어간다(@TempDir Path dir) throws IOException {
		// 고른 순간 몰수한 스냅샷이 아니라, 전멸하는 순간 실제로 가진 것이 기준이어야 한다.
		Perk perk = loadSingle(dir, """
				{ "id": "sharedfate:유산", "rarity": "prism", "name": "유산",
				  "effects": [ { "type": "legacy_gear" } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(perk.id());
		// 고르는 순간: 아무것도 없어 몰수할 것도 없다.
		assertEquals(0, PerkLegacyGear.sacrificeOnChoice(null, null, state, perk));

		// 이후 회차를 진행하며 새로 투구를 갖췄다. 방어구 네 칸은 태그 판정 없이 직접 잡으므로
		// (LegacyGearEffect 문서 참고) 데이터팩 없는 단위 시험에서도 실제로 잡히는지 볼 수 있다.
		state.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

		int captured = PerkLegacyGear.captureAtDeath(state);

		assertEquals(1, captured, "몰수 시점에 없던 장비도 전멸 시점엔 승계 대상이어야 한다");
		assertTrue(state.legacyGear.getFirst().is(Items.DIAMOND_HELMET));
	}

	@Test
	void 이전_전멸의_스냅샷을_새로_덮어쓴다(@TempDir Path dir) throws IOException {
		loadSingle(dir, """
				{ "id": "sharedfate:유산", "rarity": "prism", "name": "유산",
				  "effects": [ { "type": "legacy_gear" } ] }
				""");
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add("sharedfate:유산");
		state.legacyGear.add(new ItemStack(Items.DIAMOND_SHOVEL));
		state.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

		int captured = PerkLegacyGear.captureAtDeath(state);

		assertEquals(1, captured);
		assertEquals(1, state.legacyGear.size(), "예전 스냅샷이 남아 새 것과 섞이면 안 된다");
		assertTrue(state.legacyGear.getFirst().is(Items.DIAMOND_HELMET));
	}

	@Test
	void 유산이_없으면_스냅샷을_뜨지_않는다(@TempDir Path dir) throws IOException {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.DIAMOND_PICKAXE));

		assertEquals(0, PerkLegacyGear.captureAtDeath(state));
		assertTrue(state.legacyGear.isEmpty());
	}

	// ------------------------------------------------------------------ 도우미

	private static Perk loadSingle(Path dir, String perkJson) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME),
				"{ \"perks\": [" + perkJson + "] }", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		return PerkRegistry.all().getFirst();
	}
}
