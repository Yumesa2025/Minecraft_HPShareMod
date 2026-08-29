package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.EquipBanEffect;
import com.sharedfate.perk.effect.ItemBanEffect;
import com.sharedfate.perk.effect.OffhandLockEffect;
import com.sharedfate.perk.effect.WeaponDamageEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장비·무기 제한 증강 네 종({@code equip_ban}, {@code item_ban}, {@code offhand_lock},
 * {@code weapon_damage})의 정의 읽기와 판정을 본다.
 *
 * <p>실제로 착용을 막는 자리는 mixin 이고 그건 살아 있는 서버가 있어야 확인할 수 있다. 여기서는
 * 그 mixin 들이 물어보는 질문({@link PerkGearRules}), 공격력 수정자를 정하는 순수 계산
 * ({@link PerkWeaponDamage#desired}), 공유 인벤토리를 건드리는 부분
 * ({@link PerkGearManager}) 을 본다.
 *
 * <h2>태그는 여기서 시험하지 않는다</h2>
 * <p>아이템 태그는 데이터팩이 로드되어야 채워지는데 단위 시험에는 데이터팩이 없다. 그래서
 * 판정 시험은 전부 {@code items} 로 적고, 태그는 정의를 읽어 {@code TagKey} 가 제대로 만들어지는
 * 데까지만 본다.
 */
class GearPerkEffectTest {

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
	void equip_ban_은_적은_칸만_막는다() {
		EquipBanEffect effect = (EquipBanEffect) create("equip_ban",
				"{ \"type\": \"equip_ban\", \"slots\": [\"head\"] }");

		assertEquals(java.util.Set.of(EquipmentSlot.HEAD), effect.slots());
		assertTrue(effect.bans(EquipmentSlot.HEAD));
		assertFalse(effect.bans(EquipmentSlot.CHEST));
	}

	@Test
	void equip_ban_의_armor_는_방어구_네_칸을_가리킨다() {
		EquipBanEffect effect = (EquipBanEffect) create("equip_ban",
				"{ \"type\": \"equip_ban\", \"slots\": [\"armor\"] }");

		assertEquals(4, effect.slots().size());
		for (EquipmentSlot slot : EquipBanEffect.ARMOR_SLOTS) {
			assertTrue(effect.bans(slot), slot + " 칸이 막혀야 합니다");
		}
		assertFalse(effect.bans(EquipmentSlot.MAINHAND));
		assertFalse(effect.bans(EquipmentSlot.OFFHAND));
	}

	@Test
	void equip_ban_은_손_칸을_받지_않는다() {
		assertNull(PerkEffectType.EQUIP_BAN.create("sharedfate:테스트", 0,
				json("{ \"type\": \"equip_ban\", \"slots\": [\"mainhand\", \"offhand\"] }")),
				"주 손을 막으면 게임이 성립하지 않고 왼손은 offhand_lock 이 맡는다");
	}

	@Test
	void equip_ban_은_칸이_없으면_버려진다() {
		assertNull(PerkEffectType.EQUIP_BAN.create("sharedfate:테스트", 0,
				json("{ \"type\": \"equip_ban\" }")));
		assertNull(PerkEffectType.EQUIP_BAN.create("sharedfate:테스트", 0,
				json("{ \"type\": \"equip_ban\", \"slots\": [] }")));
	}

	@Test
	void item_ban_은_아이템과_태그를_같이_받는다() {
		ItemBanEffect effect = (ItemBanEffect) create("item_ban", """
				{ "type": "item_ban",
				  "items": ["minecraft:diamond_sword"],
				  "tags": ["#sharedfate:diamond_gear"] }
				""");

		assertEquals(1, effect.matcher().itemIds().size());
		assertEquals(1, effect.matcher().tags().size());
		assertEquals("sharedfate:diamond_gear",
				effect.matcher().tags().getFirst().location().toString(),
				"앞머리 '#' 을 적어도 받아 준다");
		assertTrue(effect.matches(new ItemStack(Items.DIAMOND_SWORD)));
		assertFalse(effect.matches(new ItemStack(Items.IRON_SWORD)));
		assertFalse(effect.matches(ItemStack.EMPTY));
	}

	@Test
	void item_ban_은_가리키는_것이_없으면_버려진다() {
		assertNull(PerkEffectType.ITEM_BAN.create("sharedfate:테스트", 0,
				json("{ \"type\": \"item_ban\" }")),
				"아무것도 막지 않는 제한은 조용한 함정이 되므로 읽는 자리에서 버린다");
	}

	@Test
	void offhand_lock_은_아이템_이름이_있어야_한다() {
		OffhandLockEffect effect = (OffhandLockEffect) create("offhand_lock",
				"{ \"type\": \"offhand_lock\", \"item\": \"minecraft:totem_of_undying\" }");

		assertEquals("minecraft:totem_of_undying", effect.itemId().toString());
		assertTrue(effect.matches(new ItemStack(Items.TOTEM_OF_UNDYING)));
		assertFalse(effect.matches(new ItemStack(Items.SHIELD)));

		assertNull(PerkEffectType.OFFHAND_LOCK.create("sharedfate:테스트", 0,
				json("{ \"type\": \"offhand_lock\" }")));
	}

	@Test
	void weapon_damage_는_아무것도_바꾸지_않으면_버려진다() {
		assertNull(PerkEffectType.WEAPON_DAMAGE.create("sharedfate:테스트", 0, json("""
				{ "type": "weapon_damage", "items": ["minecraft:diamond_shovel"] }
				""")), "배수도 1이고 다른 무기도 그대로면 붙일 이유가 없다");

		assertNull(PerkEffectType.WEAPON_DAMAGE.create("sharedfate:테스트", 0, json("""
				{ "type": "weapon_damage", "items": ["minecraft:diamond_shovel"],
				  "multiplier": 9999.0 }
				""")), "배수 상한을 넘으면 버린다");
	}

	@Test
	void weapon_damage_는_배수와_고정값을_읽는다() {
		WeaponDamageEffect effect = (WeaponDamageEffect) create("weapon_damage", """
				{ "type": "weapon_damage", "tags": ["minecraft:shovels"],
				  "multiplier": 3.0, "othersDamage": 1.0 }
				""");

		assertEquals(3.0, effect.multiplier());
		assertEquals(1.0, effect.othersDamage());
		assertEquals("minecraft:shovels", effect.matcher().tags().getFirst().location().toString());
	}

	// ------------------------------------------------------------------ 판정

	@Test
	void 증강이_없으면_아무_제한도_없다() {
		assertFalse(PerkGearRules.slotBanned((TeamState) null, EquipmentSlot.HEAD));
		assertFalse(PerkGearRules.itemBanned((TeamState) null, new ItemStack(Items.DIAMOND_SWORD)));
		assertNull(PerkGearRules.offhandLock((TeamState) null));
		assertNull(PerkGearRules.weaponRule((TeamState) null));

		TeamState empty = TeamState.fresh(20.0F);
		empty.perksEnabled = true;
		assertFalse(PerkGearRules.slotBanned(empty, EquipmentSlot.HEAD));
		assertTrue(PerkGearRules.mayPlaceInOffhand(empty, new ItemStack(Items.SHIELD)));
	}

	@Test
	void 뚝배기_대신_피통은_머리만_막는다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:helmetless");

		assertTrue(PerkGearRules.slotBanned(state, EquipmentSlot.HEAD));
		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.CHEST));
		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.LEGS));
		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.FEET));
	}

	@Test
	void 광전사는_방어구_네_칸을_모두_막는다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:berserker");

		for (EquipmentSlot slot : EquipBanEffect.ARMOR_SLOTS) {
			assertTrue(PerkGearRules.slotBanned(state, slot), slot + " 칸이 막혀야 합니다");
		}
		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.MAINHAND));
	}

	@Test
	void 금기의_광석은_다이아몬드_장비만_막는다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden");

		assertTrue(PerkGearRules.itemBanned(state, new ItemStack(Items.DIAMOND_SWORD)));
		assertTrue(PerkGearRules.itemBanned(state, new ItemStack(Items.DIAMOND_HELMET)));
		assertFalse(PerkGearRules.itemBanned(state, new ItemStack(Items.NETHERITE_SWORD)));
		assertFalse(PerkGearRules.itemBanned(state, new ItemStack(Items.IRON_HELMET)));
		// 칸 자체는 막히지 않는다. 철 투구는 그대로 쓸 수 있어야 한다.
		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.HEAD));
	}

	@Test
	void 증강을_잃으면_제한이_사라진다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:berserker");
		assertTrue(PerkGearRules.slotBanned(state, EquipmentSlot.CHEST));

		// 회차 리셋이나 팀 해체로 보유 목록이 비는 상황.
		state.ownedPerks.clear();

		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.CHEST));
		assertNull(PerkGearRules.weaponRule(state));
		assertTrue(PerkGearRules.mayPlaceInOffhand(state, new ItemStack(Items.SHIELD)));
	}

	@Test
	void 증강을_끈_팀에는_제한이_걸리지_않는다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:berserker");
		state.perksEnabled = false;

		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.CHEST));
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:사라진것");

		assertFalse(PerkGearRules.slotBanned(state, EquipmentSlot.HEAD));
		assertFalse(PerkGearRules.itemBanned(state, new ItemStack(Items.DIAMOND_SWORD)));
	}

	// ------------------------------------------------------------------ 왼손 고정

	@Test
	void 왼손에는_지정_아이템만_놓을_수_있다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:totem");

		assertTrue(PerkGearRules.mayPlaceInOffhand(state, new ItemStack(Items.TOTEM_OF_UNDYING)));
		assertFalse(PerkGearRules.mayPlaceInOffhand(state, new ItemStack(Items.SHIELD)));
		assertTrue(PerkGearRules.mayPlaceInOffhand(state, ItemStack.EMPTY),
				"칸을 비우는 것까지 막으면 아이템을 꺼내지 못한다");
	}

	@Test
	void 왼손_고정은_공유_목록에서_한_개만_꺼낸다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:totem");
		state.mainItems.set(4, new ItemStack(Items.TOTEM_OF_UNDYING, 3));
		OffhandLockEffect lock = PerkGearRules.offhandLock(state);
		assertNotNull(lock);

		ItemStack pulled = PerkGearManager.takeOne(state, lock);

		assertEquals(1, pulled.getCount());
		assertEquals(Items.TOTEM_OF_UNDYING, pulled.getItem());
		assertEquals(2, state.mainItems.get(4).getCount(), "꺼낸 만큼만 줄어야 합니다");
	}

	@Test
	void 왼손_고정_아이템은_넘침_대기열에서도_꺼낸다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:totem");
		state.overflowItems.add(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
		OffhandLockEffect lock = PerkGearRules.offhandLock(state);
		assertNotNull(lock);

		ItemStack pulled = PerkGearManager.takeOne(state, lock);

		assertEquals(1, pulled.getCount());
		assertTrue(state.overflowItems.isEmpty(), "다 꺼냈으면 대기열에서 빠져야 합니다");
	}

	@Test
	void 왼손_고정_아이템이_없으면_빈_묶음이_나온다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:totem");
		OffhandLockEffect lock = PerkGearRules.offhandLock(state);
		assertNotNull(lock);

		assertTrue(PerkGearManager.takeOne(state, lock).isEmpty(),
				"쓴 토템을 새로 만들어 주지는 않는다");
	}

	// ------------------------------------------------------------------ 공유 인벤토리

	@Test
	void 벗긴_장비는_공유_목록으로_들어간다() {
		TeamState state = TeamState.fresh(20.0F);

		boolean leftover = PerkGearManager.deliver(state, new ItemStack(Items.DIAMOND_HELMET));

		assertFalse(leftover);
		assertEquals(Items.DIAMOND_HELMET, state.mainItems.get(0).getItem());
		assertTrue(state.overflowItems.isEmpty());
	}

	@Test
	void 공유_목록이_꽉_차면_넘침_대기열로_간다() {
		TeamState state = TeamState.fresh(20.0F);
		for (int slot = 0; slot < state.mainItems.size(); slot++) {
			state.mainItems.set(slot, new ItemStack(Items.STONE, 64));
		}

		boolean leftover = PerkGearManager.deliver(state, new ItemStack(Items.DIAMOND_HELMET));

		assertTrue(leftover, "바닥에 떨어뜨리지 않고 대기열에 남긴다");
		assertEquals(1, state.overflowItems.size());
		assertEquals(Items.DIAMOND_HELMET, state.overflowItems.getFirst().getItem());
	}

	// ------------------------------------------------------------------ 무기 공격력

	@Test
	void 삽을_들면_공격력에_배수가_걸린다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:shovel");

		AttributeModifier modifier =
				PerkWeaponDamage.desired(state, new ItemStack(Items.DIAMOND_SHOVEL), 1.0);

		assertNotNull(modifier);
		assertEquals(PerkWeaponDamage.MODIFIER_ID, modifier.id());
		assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, modifier.operation());
		assertEquals(2.0, modifier.amount(), 1.0e-6, "×3 은 총합에 +200%");
	}

	@Test
	void 다른_무기는_공격력이_지정값이_된다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:shovel");

		// 다이아몬드 검은 주 손에서 공격력을 6 얹는다. 기본 1 + 6 = 7 을 1 로 만들어야 하므로 -6.
		AttributeModifier modifier =
				PerkWeaponDamage.desired(state, new ItemStack(Items.DIAMOND_SWORD), 1.0);

		assertNotNull(modifier);
		assertEquals(AttributeModifier.Operation.ADD_VALUE, modifier.operation());
		assertEquals(-6.0, modifier.amount(), 1.0e-6);
	}

	@Test
	void 맨손과_무기가_아닌_물건은_건드리지_않는다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:shovel");

		assertNull(PerkWeaponDamage.desired(state, ItemStack.EMPTY, 1.0),
				"맨손은 원래 공격력이 1이라 손댈 이유가 없다");
		assertNull(PerkWeaponDamage.desired(state, new ItemStack(Items.DIRT), 1.0),
				"공격력을 얹지 않는 물건은 무기가 아니다");
	}

	@Test
	void 막힌_무기는_얹은_공격력만_사라진다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden");

		AttributeModifier modifier =
				PerkWeaponDamage.desired(state, new ItemStack(Items.DIAMOND_SWORD), 1.0);

		assertNotNull(modifier);
		assertEquals(AttributeModifier.Operation.ADD_VALUE, modifier.operation());
		assertEquals(-6.0, modifier.amount(), 1.0e-6, "맨손과 같아진다");

		assertNull(PerkWeaponDamage.desired(state, new ItemStack(Items.NETHERITE_SWORD), 1.0),
				"막히지 않은 무기는 그대로다");
	}

	@Test
	void 증강이_없으면_공격력_수정자를_붙이지_않는다() {
		assertNull(PerkWeaponDamage.desired(null, new ItemStack(Items.DIAMOND_SHOVEL), 1.0));

		TeamState empty = TeamState.fresh(20.0F);
		empty.perksEnabled = true;
		assertNull(PerkWeaponDamage.desired(empty, new ItemStack(Items.DIAMOND_SHOVEL), 1.0));
	}

	// ------------------------------------------------------------------ 도우미

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	/** 타입 하나를 읽어 효과를 만든다. 못 읽으면 그 자리에서 시험이 깨진다. */
	private static PerkEffect create(String typeId, String raw) {
		PerkEffectType type = PerkEffectType.fromId(typeId);
		assertNotNull(type, typeId + " 타입이 등록되어 있어야 합니다");
		PerkEffect effect = type.create("sharedfate:테스트", 0, json(raw));
		assertNotNull(effect, typeId + " 정의를 읽지 못했습니다");
		return effect;
	}

	/** 시험용 증강 풀을 깔고 그중 하나를 가진 팀 상태를 만든다. */
	private static TeamState teamWith(Path dir, String perkId) throws IOException {
		PerkRegistry.load(pool(dir));
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	/**
	 * 시험용 증강 풀.
	 *
	 * <p>배포되는 기본 풀과 달리 태그가 아니라 아이템 이름으로 적는다. 단위 시험에는 데이터팩이
	 * 없어 아이템 태그가 채워지지 않기 때문이다.
	 */
	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:helmetless", "rarity": "silver", "name": "뚝배기 대신 피통",
				      "effects": [ { "type": "equip_ban", "slots": ["head"] } ] },
				    { "id": "sharedfate:berserker", "rarity": "prism", "name": "광전사",
				      "effects": [ { "type": "equip_ban", "slots": ["armor"] } ] },
				    { "id": "sharedfate:forbidden", "rarity": "prism", "name": "금기의 광석",
				      "effects": [ { "type": "item_ban",
				        "items": ["minecraft:diamond_sword", "minecraft:diamond_helmet"] } ] },
				    { "id": "sharedfate:totem", "rarity": "prism", "name": "손에 쥔 목숨",
				      "effects": [ { "type": "offhand_lock",
				        "item": "minecraft:totem_of_undying" } ] },
				    { "id": "sharedfate:shovel", "rarity": "prism", "name": "삽질의 대가",
				      "effects": [ { "type": "weapon_damage",
				        "items": ["minecraft:diamond_shovel"],
				        "multiplier": 3.0, "othersDamage": 1.0 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
