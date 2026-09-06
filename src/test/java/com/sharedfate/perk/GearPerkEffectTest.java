package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DurabilityMultiplierEffect;
import com.sharedfate.perk.effect.EquipBanEffect;
import com.sharedfate.perk.effect.ItemBanEffect;
import com.sharedfate.perk.effect.OffhandLockEffect;
import com.sharedfate.perk.effect.WeaponDamageEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장비·무기 관련 증강 다섯 종({@code equip_ban}, {@code item_ban}, {@code offhand_lock},
 * {@code weapon_damage}, {@code durability_multiplier})의 정의 읽기와 판정을 본다.
 *
 * <p>실제로 착용을 막는 자리는 mixin 이고 그건 살아 있는 서버가 있어야 확인할 수 있다. 여기서는
 * 그 mixin 들이 물어보는 질문({@link PerkGearRules}), 공격력 수정자를 정하는 순수 계산
 * ({@link PerkWeaponDamage#desired}), 내구도 소모량을 깎는 순수 계산
 * ({@link PerkGearRules#reduceDurabilityLoss}), 공유 인벤토리를 건드리는 부분
 * ({@link PerkGearManager}) 을 본다. Mixin 대상 자체는 {@code NetheriteDurabilityTargetTest} 가
 * 따로 못박는다.
 *
 * <p>알림 쿨다운({@link GearNoticeCooldown})도 여기서 본다. 시각을 인자로 받는 순수한 판정이라
 * 살아 있는 서버 없이 1초를 재울 수 있고, <b>재워도 밀어내기 동작 자체는 계속 돈다</b>는 것을
 * 실제 {@link PerkGearManager#pushToStorage} 로 확인한다.
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
	void item_ban_의_discard_는_안_적으면_거짓이다() {
		ItemBanEffect effect = (ItemBanEffect) create("item_ban", """
				{ "type": "item_ban", "tags": ["#sharedfate:diamond_gear"] }
				""");

		assertFalse(effect.discard(), "예전처럼 무력화만 하는 것이 기본값이어야 한다");
	}

	@Test
	void item_ban_은_discard_를_읽는다() {
		ItemBanEffect effect = (ItemBanEffect) create("item_ban", """
				{ "type": "item_ban", "tags": ["#sharedfate:diamond_gear"], "discard": true }
				""");

		assertTrue(effect.discard());
	}

	@Test
	void item_ban_의_discard_가_참_거짓이_아니면_버려진다() {
		assertNull(PerkEffectType.ITEM_BAN.create("sharedfate:테스트", 0, json("""
				{ "type": "item_ban", "tags": ["#sharedfate:diamond_gear"], "discard": "yes" }
				""")));
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
	void discard_가_없는_item_ban_은_자동_폐기_대상이_아니다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden");

		assertTrue(PerkGearRules.itemBanned(state, new ItemStack(Items.DIAMOND_SWORD)),
				"무력화는 여전히 걸려야 한다");
		assertFalse(PerkGearRules.itemBanDiscards(state, new ItemStack(Items.DIAMOND_SWORD)),
				"discard 를 안 적었으면 예전처럼 무력화만 하고 버리지는 않는다");
	}

	@Test
	void discard_가_있는_item_ban_은_핫바_장착_아이템을_자동_폐기_대상으로_삼는다(@TempDir Path dir)
			throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden_discard");

		assertTrue(PerkGearRules.itemBanDiscards(state, new ItemStack(Items.DIAMOND_SWORD)));
		assertTrue(PerkGearRules.itemBanDiscards(state, new ItemStack(Items.DIAMOND_HELMET)));
		assertFalse(PerkGearRules.itemBanDiscards(state, new ItemStack(Items.NETHERITE_SWORD)),
				"막힌 무리가 아닌 아이템은 폐기 대상도 아니다");
		assertFalse(PerkGearRules.itemBanDiscards(state, ItemStack.EMPTY));
		assertFalse(PerkGearRules.itemBanDiscards((TeamState) null, new ItemStack(Items.DIAMOND_SWORD)));
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

	// ------------------------------------------------------------------ 핫바에서 밀어내기

	@Test
	void 핫바에서_밀어낸_아이템은_핫바로_돌아오지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		ItemStack banished = new ItemStack(Items.DIAMOND_PICKAXE);

		int moved = PerkGearManager.pushToStorage(state, banished, false);

		assertEquals(1, moved);
		assertTrue(banished.isEmpty(), "다 들어갔으면 빈 묶음이 되어야 합니다");
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			assertTrue(state.mainItems.get(slot).isEmpty(),
					slot + "번은 핫바다. 여기로 돌아오면 다음 점검이 또 밀어내는 무한 왕복이 된다");
		}
		assertEquals(Items.DIAMOND_PICKAXE,
				state.mainItems.get(Inventory.getSelectionSize()).getItem(),
				"핫바 바로 다음 칸부터 채운다");
		assertTrue(state.overflowItems.isEmpty(),
				"넘침 대기열은 0번부터 다시 채우는 곳이라 여기서는 쓰지 않는다");
	}

	@Test
	void 밀어낸_아이템은_보관_칸의_같은_묶음에_합쳐진다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(20, new ItemStack(Items.DIAMOND, 10));

		ItemStack banished = new ItemStack(Items.DIAMOND, 5);
		PerkGearManager.pushToStorage(state, banished, false);

		assertEquals(15, state.mainItems.get(20).getCount());
		assertTrue(banished.isEmpty());
	}

	@Test
	void 보관_칸이_꽉_차면_확장_칸까지_쓴다() {
		TeamState state = TeamState.fresh(20.0F);
		fillStorage(state);

		ItemStack banished = new ItemStack(Items.DIAMOND_PICKAXE);
		int moved = PerkGearManager.pushToStorage(state, banished, true);

		assertEquals(1, moved, "확장 칸도 플레이어 눈에는 그냥 인벤토리 칸이다");
		assertEquals(Items.DIAMOND_PICKAXE, state.extraItems.get(0).getItem());
		assertTrue(banished.isEmpty());
	}

	@Test
	void 확장_칸을_안_쓰는_서버에서는_확장_칸을_세지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		fillStorage(state);

		ItemStack banished = new ItemStack(Items.DIAMOND_PICKAXE);
		int moved = PerkGearManager.pushToStorage(state, banished, false);

		assertEquals(0, moved);
		assertEquals(1, banished.getCount(), "남은 몫은 부르는 쪽이 버린다");
		assertTrue(state.extraItems.get(0).isEmpty());
	}

	@Test
	void 옮길_자리가_없으면_남은_몫이_그대로_돌아온다() {
		TeamState state = TeamState.fresh(20.0F);
		fillStorage(state);
		for (int slot = 0; slot < state.extraItems.size(); slot++) {
			state.extraItems.set(slot, new ItemStack(Items.STONE, 64));
		}

		ItemStack banished = new ItemStack(Items.DIAMOND_PICKAXE);
		int moved = PerkGearManager.pushToStorage(state, banished, true);

		assertEquals(0, moved, "여기까지 와야 비로소 버린다");
		assertEquals(1, banished.getCount());
	}

	@Test
	void 자리가_모자라면_들어간_만큼만_옮긴다() {
		TeamState state = TeamState.fresh(20.0F);
		fillStorage(state);
		// 보관 칸 한 곳만 6칸 비워 둔다.
		state.mainItems.set(20, new ItemStack(Items.DIAMOND, 58));

		ItemStack banished = new ItemStack(Items.DIAMOND, 20);
		int moved = PerkGearManager.pushToStorage(state, banished, false);

		assertEquals(6, moved);
		assertEquals(64, state.mainItems.get(20).getCount());
		assertEquals(14, banished.getCount(), "못 넣은 몫만 남는다");
	}

	// ------------------------------------------------------------------ 알림 쿨다운

	@Test
	void 같은_알림은_1초_안에_두_번_나가지_않는다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID player = UUID.randomUUID();

		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 0L),
				"첫 알림은 언제나 나가야 한다");
		assertFalse(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 5L),
				"점검 주기는 5틱이라 곧바로 다시 온다. 이때 나가면 초당 네 줄이 된다");
		assertFalse(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 19L),
				"1틱 모자라면 아직 재우는 중이다");
	}

	@Test
	void 쿨다운이_지나면_알림이_다시_나간다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID player = UUID.randomUUID();

		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 0L));
		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED,
				GearNoticeCooldown.NOTICE_COOLDOWN_TICKS),
				"딱 1초가 지나면 다시 나간다. 영영 입을 다물면 왜 아이템이 사라지는지 알 수 없다");
		assertFalse(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED,
				GearNoticeCooldown.NOTICE_COOLDOWN_TICKS + 1),
				"방금 나갔으니 다시 재운다");
	}

	@Test
	void 한_사람의_도배가_다른_사람의_알림을_막지_않는다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID spammer = UUID.randomUUID();
		UUID other = UUID.randomUUID();

		assertTrue(notices.claim(spammer, GearNoticeCooldown.Kind.RELOCATED, 0L));
		assertFalse(notices.claim(spammer, GearNoticeCooldown.Kind.RELOCATED, 5L));

		assertTrue(notices.claim(other, GearNoticeCooldown.Kind.RELOCATED, 5L),
				"쿨다운은 사람마다다. 팀원이 도배당한다고 내 알림까지 막히면 안 된다");
	}

	@Test
	void 밀어냄_알림이_삼켜져도_버림_알림은_나간다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID player = UUID.randomUUID();

		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 0L));
		assertFalse(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 5L));

		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.DROPPED, 5L),
				"버려지는 것은 실제 손실이라 옮김 알림에 묻히면 안 된다");
		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.STOWED, 5L),
				"왼손 고정 알림도 따로 센다");
	}

	@Test
	void 알림을_재워도_밀어내기는_점검마다_계속_돈다() {
		TeamState state = TeamState.fresh(20.0F);
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID player = UUID.randomUUID();

		// 사람이 밀려난 곡괭이를 매번 핫바로 되가져오는 5초(100틱) 동안의 점검을 흉내낸다.
		int moved = 0;
		int shown = 0;
		for (long tick = PerkGearManager.SWEEP_INTERVAL_TICKS; tick <= 100;
				tick += PerkGearManager.SWEEP_INTERVAL_TICKS) {
			ItemStack banned = new ItemStack(Items.DIAMOND_PICKAXE);
			moved += PerkGearManager.pushToStorage(state, banned, false);
			if (notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, tick)) {
				shown++;
			}
		}

		assertEquals(20, moved,
				"치우는 일은 한 번도 거르면 안 된다. 거르는 순간 그 틈에 금지 장비를 쓸 수 있다");
		assertEquals(5, shown, "말은 1초에 한 번씩만. 5초면 다섯 줄이다");
	}

	@Test
	void 접속을_끊은_사람의_알림_기록은_남지_않는다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID stayed = UUID.randomUUID();

		// 며칠 동안 들락날락한 사람들.
		for (int visit = 0; visit < 1_000; visit++) {
			notices.claim(UUID.randomUUID(), GearNoticeCooldown.Kind.RELOCATED, 0L);
		}
		notices.claim(stayed, GearNoticeCooldown.Kind.RELOCATED, 0L);
		assertEquals(1_001, notices.trackedPlayers());

		notices.prune(java.util.Set.of(stayed), 0L);

		assertEquals(1, notices.trackedPlayers(), "접속 중인 사람만 남는다");
	}

	@Test
	void 다_식은_기록은_접속_중이어도_버린다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID player = UUID.randomUUID();
		notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 0L);

		notices.prune(java.util.Set.of(player), 0L);
		assertEquals(1, notices.trackedPlayers(), "아직 재우는 중이면 남겨 둬야 한다");

		notices.prune(java.util.Set.of(player), GearNoticeCooldown.NOTICE_COOLDOWN_TICKS);

		assertEquals(0, notices.trackedPlayers(),
				"다 식었으면 남길 이유가 없다. 다시 도배되면 그때 새로 만들면 된다");
		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED,
				GearNoticeCooldown.NOTICE_COOLDOWN_TICKS),
				"기록을 버렸다고 알림이 막히면 안 된다");
	}

	@Test
	void 서버가_멈추면_알림_기록을_비운다() {
		GearNoticeCooldown notices = new GearNoticeCooldown();
		UUID player = UUID.randomUUID();
		notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 0L);

		notices.clear();

		assertEquals(0, notices.trackedPlayers());
		assertTrue(notices.claim(player, GearNoticeCooldown.Kind.RELOCATED, 0L),
				"다음 월드에서는 0틱부터 다시 센다");
	}

	// ------------------------------------------------------------------ 내구도 배수

	@Test
	void durability_multiplier_는_배수와_대상을_읽는다() {
		DurabilityMultiplierEffect effect = (DurabilityMultiplierEffect) create(
				"durability_multiplier", """
				{ "type": "durability_multiplier", "tags": ["#sharedfate:netherite_gear"],
				  "multiplier": 3.0 }
				""");

		assertEquals(3.0, effect.multiplier());
		assertEquals("sharedfate:netherite_gear",
				effect.matcher().tags().getFirst().location().toString());
	}

	@Test
	void durability_multiplier_는_쓸모없는_값을_버린다() {
		assertNull(type("durability_multiplier").create("sharedfate:테스트", 0, json("""
				{ "type": "durability_multiplier", "items": ["minecraft:netherite_pickaxe"] }
				""")), "배수를 안 적으면 아무것도 바꾸지 않는다");
		assertNull(type("durability_multiplier").create("sharedfate:테스트", 0, json("""
				{ "type": "durability_multiplier", "items": ["minecraft:netherite_pickaxe"],
				  "multiplier": 1.0 }
				""")), "1배는 바닐라와 같다");
		assertNull(type("durability_multiplier").create("sharedfate:테스트", 0, json("""
				{ "type": "durability_multiplier", "items": ["minecraft:netherite_pickaxe"],
				  "multiplier": 9999.0 }
				""")), "상한을 넘으면 사실상 안 닳는 장비가 된다");
		assertNull(type("durability_multiplier").create("sharedfate:테스트", 0, json("""
				{ "type": "durability_multiplier", "multiplier": 3.0 }
				""")), "가리키는 아이템이 없으면 조용한 함정이 된다");
	}

	@Test
	void 금기의_광석은_네더라이트만_오래_쓴다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden_full");

		assertEquals(3.0,
				PerkGearRules.durabilityMultiplier(state, new ItemStack(Items.NETHERITE_PICKAXE)));
		assertEquals(3.0,
				PerkGearRules.durabilityMultiplier(state, new ItemStack(Items.NETHERITE_HELMET)));
		assertEquals(1.0,
				PerkGearRules.durabilityMultiplier(state, new ItemStack(Items.DIAMOND_PICKAXE)),
				"다이아몬드는 애초에 쓰지도 못한다");
		assertEquals(1.0, PerkGearRules.durabilityMultiplier(state, ItemStack.EMPTY));
	}

	@Test
	void 증강이_없으면_내구도는_바닐라대로_닳는다() {
		assertEquals(1.0, PerkGearRules.durabilityMultiplier(
				(TeamState) null, new ItemStack(Items.NETHERITE_PICKAXE)));
		assertEquals(7, PerkGearRules.reduceDurabilityLoss(
				null, new ItemStack(Items.NETHERITE_PICKAXE), 7, RandomSource.create(1L)));
	}

	@Test
	void 증강을_잃으면_다음_한_번부터_바닐라대로_닳는다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden_full");
		ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		assertEquals(3.0, PerkGearRules.durabilityMultiplier(state, pickaxe));

		// 회차 리셋이나 팀 해체로 보유 목록이 비는 상황.
		state.ownedPerks.clear();

		assertEquals(1.0, PerkGearRules.durabilityMultiplier(state, pickaxe),
				"아이템을 개조하지 않았으므로 되돌릴 것도 없다");
		assertEquals(1, PerkGearRules.reduceDurabilityLoss(
				state, pickaxe, 1, RandomSource.create(1L)));
	}

	@Test
	void 소모량_1_은_배수만큼의_확률로만_닳는다() {
		// 1 을 3 으로 나누면 몫 0, 나머지 1/3. 그대로 나누면 영영 안 닳는 장비가 된다.
		assertEquals(1, PerkGearRules.reduceDurabilityLoss(1, 3.0, 0.0));
		assertEquals(1, PerkGearRules.reduceDurabilityLoss(1, 3.0, 0.33));
		assertEquals(0, PerkGearRules.reduceDurabilityLoss(1, 3.0, 0.34));
		assertEquals(0, PerkGearRules.reduceDurabilityLoss(1, 3.0, 0.99));
	}

	@Test
	void 소모량이_크면_몫은_그대로_깎고_나머지만_확률로_돈다() {
		// 10 / 3 = 3.333 → 3 은 확정, 0.333 확률로 1 더.
		assertEquals(4, PerkGearRules.reduceDurabilityLoss(10, 3.0, 0.0));
		assertEquals(3, PerkGearRules.reduceDurabilityLoss(10, 3.0, 0.5));
		assertEquals(3, PerkGearRules.reduceDurabilityLoss(9, 3.0, 0.0), "딱 나누어떨어지면 확률이 없다");
	}

	@Test
	void 배수가_없거나_안_닳는_호출은_손대지_않는다() {
		assertEquals(5, PerkGearRules.reduceDurabilityLoss(5, 1.0, 0.0));
		assertEquals(5, PerkGearRules.reduceDurabilityLoss(5, 0.5, 0.0), "1 미만도 그대로 둔다");
		assertEquals(0, PerkGearRules.reduceDurabilityLoss(0, 3.0, 0.0));
		assertEquals(-1, PerkGearRules.reduceDurabilityLoss(-1, 3.0, 0.0),
				"음수는 수리다. 여기서 건드리면 수리량이 깎인다");
	}

	@Test
	void 내구도_배수는_평균적으로_3배가_된다(@TempDir Path dir) throws IOException {
		TeamState state = teamWith(dir, "sharedfate:forbidden_full");
		ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		RandomSource random = RandomSource.create(20260906L);

		int worn = 0;
		for (int swing = 0; swing < 30_000; swing++) {
			worn += PerkGearRules.reduceDurabilityLoss(state, pickaxe, 1, random);
		}

		// 3배면 30,000번 휘둘러 10,000쯤 닳아야 한다. 표본이 크므로 ±3% 면 충분히 좁다.
		assertTrue(worn > 9_700 && worn < 10_300,
				"30,000번에 " + worn + " 만 닳아야 내구도가 3배다");
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
		PerkEffect effect = type(typeId).create("sharedfate:테스트", 0, json(raw));
		assertNotNull(effect, typeId + " 정의를 읽지 못했습니다");
		return effect;
	}

	/** 등록된 효과 타입 하나. 없으면 그 자리에서 시험이 깨진다. */
	private static PerkEffectType type(String typeId) {
		PerkEffectType type = PerkEffectType.fromId(typeId);
		assertNotNull(type, typeId + " 타입이 등록되어 있어야 합니다");
		return type;
	}

	/** 핫바를 뺀 보관 칸 27칸을 돌로 가득 채운다. 확장 칸은 건드리지 않는다. */
	private static void fillStorage(TeamState state) {
		for (int slot = Inventory.getSelectionSize(); slot < state.mainItems.size(); slot++) {
			state.mainItems.set(slot, new ItemStack(Items.STONE, 64));
		}
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
				    { "id": "sharedfate:forbidden_discard", "rarity": "prism", "name": "금기의 광석(폐기)",
				      "effects": [ { "type": "item_ban",
				        "items": ["minecraft:diamond_sword", "minecraft:diamond_helmet"],
				        "discard": true } ] },
				    { "id": "sharedfate:forbidden_full", "rarity": "prism", "name": "금기의 광석(완성)",
				      "effects": [
				        { "type": "item_ban",
				          "items": ["minecraft:diamond_sword", "minecraft:diamond_pickaxe"],
				          "discard": true },
				        { "type": "durability_multiplier",
				          "items": ["minecraft:netherite_pickaxe", "minecraft:netherite_helmet"],
				          "multiplier": 3.0 } ] },
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
