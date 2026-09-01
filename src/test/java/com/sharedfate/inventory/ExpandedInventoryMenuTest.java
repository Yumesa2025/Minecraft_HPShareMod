package com.sharedfate.inventory;

import com.sharedfate.SharedFateMod;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpandedInventoryMenuTest {
	private static SharedFateConfig previousConfig;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
		previousConfig = SharedFateMod.config;
		SharedFateMod.config = new SharedFateConfig();
		SharedFateMod.config.mainInventoryRows = 6;
	}

	@AfterAll
	static void tearDown() {
		ExpandedInventoryManager.clearRuntimeState();
		SharedFateMod.config = previousConfig;
	}

	@BeforeEach
	void resetState() {
		ExpandedInventoryManager.clearRuntimeState();
		SharedFateMod.config.mainInventoryRows = 6;
	}

	@Test
	void 기존_메뉴_번호를_보존하고_추가_27칸을_끝에_붙인다() {
		Inventory inventory = new Inventory(null, new EntityEquipment());
		InventoryMenu menu = new InventoryMenu(inventory, true, null);

		assertEquals(ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE, menu.slots.size());
		assertEquals(39, menu.getSlot(5).getContainerSlot(), "기존 머리 장비 번호가 유지되어야 한다");
		assertEquals(40, menu.getSlot(45).getContainerSlot(), "기존 오프핸드 번호가 유지되어야 한다");
		assertEquals(0, menu.getSlot(46).getContainerSlot(), "추가 컨테이너는 메뉴 46에서 시작한다");
		assertEquals(26, menu.getSlot(72).getContainerSlot(), "추가 컨테이너는 정확히 27칸이다");
	}

	@Test
	void 세줄_설정이면_바닐라_46칸을_유지한다() {
		SharedFateMod.config.mainInventoryRows = 3;
		try {
			Inventory inventory = new Inventory(null, new EntityEquipment());
			InventoryMenu menu = new InventoryMenu(inventory, true, null);

			assertEquals(ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE, menu.slots.size());
		} finally {
			SharedFateMod.config.mainInventoryRows = 6;
		}
	}

	@Test
	void 추가_슬롯은_장비_36부터_42와_겹치지_않는다() {
		EntityEquipment equipment = new EntityEquipment();
		Inventory inventory = new Inventory(null, equipment);
		InventoryMenu menu = new InventoryMenu(inventory, true, null);

		menu.getSlot(46).set(new ItemStack(Items.DIAMOND, 3));

		assertTrue(menu.getSlot(46).getItem().is(Items.DIAMOND));
		assertTrue(inventory.getItem(36).isEmpty(), "FEET 가상 번호는 추가 슬롯과 분리되어야 한다");
		assertTrue(equipment.get(EquipmentSlot.FEET).isEmpty(), "추가 슬롯 기록이 장비로 새면 안 된다");
		assertEquals(36, inventory.getNonEquipmentItems().size(), "바닐라 목록 크기는 그대로여야 한다");
	}

	@Test
	void 방어구와_오프핸드를_조작해도_추가_슬롯이_유지된다() {
		EntityEquipment equipment = new EntityEquipment();
		Inventory inventory = new Inventory(null, equipment);
		InventoryMenu menu = new InventoryMenu(inventory, true, null);
		menu.getSlot(46).set(new ItemStack(Items.DIAMOND, 5));

		menu.getSlot(8).set(new ItemStack(Items.DIAMOND_BOOTS));
		menu.getSlot(45).set(new ItemStack(Items.SHIELD));

		assertTrue(equipment.get(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS));
		assertTrue(equipment.get(EquipmentSlot.OFFHAND).is(Items.SHIELD));
		assertEquals(5, menu.getSlot(46).getItem().getCount());
	}

	@Test
	void 추가_컨테이너는_스택을_합치고_조건에_맞는_아이템을_제거한다() {
		ExpandedInventoryContainer extra = new ExpandedInventoryContainer(null);
		extra.setClientActive(true);
		extra.setItem(0, new ItemStack(Items.DIAMOND, 60));
		ItemStack incoming = new ItemStack(Items.DIAMOND, 10);

		assertTrue(extra.addStack(incoming));
		assertTrue(incoming.isEmpty());
		assertEquals(64, extra.getItem(0).getCount());
		assertEquals(6, extra.getItem(1).getCount());

		int removed = extra.clearOrCountMatchingItems(stack -> stack.is(Items.DIAMOND), 5, false);
		assertEquals(5, removed);
		assertEquals(59, extra.getItem(0).getCount());
	}

	@Test
	void 기존_추가_스택_병합은_빈_추가칸을_먼저_사용하지_않는다() {
		ExpandedInventoryContainer extra = new ExpandedInventoryContainer(null);
		extra.setClientActive(true);
		extra.setItem(5, new ItemStack(Items.DIAMOND, 60));
		ItemStack incoming = new ItemStack(Items.DIAMOND, 3);

		assertTrue(extra.mergeExisting(incoming));
		assertTrue(incoming.isEmpty());
		assertEquals(63, extra.getItem(5).getCount());
		assertTrue(extra.getItem(0).isEmpty(), "기존 스택보다 빈칸을 먼저 쓰면 안 된다");
	}

	@Test
	void 추가_슬롯의_쉬프트_클릭은_바닐라_메인_인벤토리로_이동한다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		InventoryMenu menu = new InventoryMenu(inventory, true, null);
		menu.getSlot(46).set(new ItemStack(Items.DIAMOND, 3));

		ItemStack moved = menu.quickMoveStack(null, 46);

		assertEquals(3, moved.getCount());
		assertTrue(menu.getSlot(46).getItem().isEmpty());
		assertEquals(3, menu.getSlot(9).getItem().getCount());
	}

	@Test
	void 제작대에도_기존_번호_뒤에_추가_27칸을_붙인다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		CraftingMenu menu = new CraftingMenu(1, inventory);

		assertEquals(ExpandedInventoryManager.EXPANDED_INVENTORY_MENU_SIZE, menu.slots.size());
		assertEquals(0, menu.getSlot(0).getContainerSlot(), "결과 슬롯 번호가 유지되어야 한다");
		assertEquals(9, menu.getSlot(10).getContainerSlot(), "기존 메인 슬롯 번호가 유지되어야 한다");
		assertEquals(8, menu.getSlot(45).getContainerSlot(), "기존 핫바 마지막 번호가 유지되어야 한다");
		assertEquals(0, menu.getSlot(46).getContainerSlot(), "추가 컨테이너는 메뉴 46에서 시작한다");
		assertEquals(26, menu.getSlot(72).getContainerSlot(), "추가 컨테이너는 정확히 27칸이다");
		assertEquals(196, menu.getSlot(37).y, "핫바는 추가 행 아래로 이동해야 한다");
		assertEquals(138, menu.getSlot(46).y, "첫 추가 행 좌표가 화면과 일치해야 한다");
	}

	@Test
	void 플레이어_화면의_추가_27칸은_모두_활성이고_서로_다른_좌표를_가진다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		InventoryMenu menu = new InventoryMenu(inventory, true, null);

		for (int extraIndex = 0; extraIndex < ExpandedInventoryManager.EXTRA_SIZE; extraIndex++) {
			var slot = menu.getSlot(
					ExpandedInventoryManager.VANILLA_INVENTORY_MENU_SIZE + extraIndex);
			assertTrue(slot.isActive(), "추가 슬롯 " + extraIndex + "이 활성이어야 한다");
			assertTrue(slot.mayPlace(new ItemStack(Items.COBBLESTONE)),
					"추가 슬롯 " + extraIndex + "에 아이템을 놓을 수 있어야 한다");
			assertTrue(slot.mayPickup(null),
					"추가 슬롯 " + extraIndex + "에서 아이템을 꺼낼 수 있어야 한다");
			assertEquals(8 + (extraIndex % 9) * 18, slot.x);
			assertEquals(138 + (extraIndex / 9) * 18, slot.y);
			slot.set(new ItemStack(Items.DIAMOND, 1));
			assertEquals(1, slot.getItem().getCount());
		}
		assertEquals(27, ExpandedInventoryManager.extraFor(null).getItems().stream()
				.filter(stack -> !stack.isEmpty()).count());
	}

	@Test
	void 제작대_추가_슬롯의_쉬프트_클릭은_제작_격자를_먼저_사용한다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		CraftingMenu menu = new CraftingMenu(1, inventory);
		menu.getSlot(46).set(new ItemStack(Items.DIAMOND, 3));

		ItemStack moved = menu.quickMoveStack(null, 46);

		assertEquals(3, moved.getCount());
		assertTrue(menu.getSlot(46).getItem().isEmpty());
		assertEquals(3, menu.getSlot(1).getItem().getCount());
	}

	@Test
	void 제작대에서_바닐라_인벤토리가_가득_차면_추가_27칸으로_이동한다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		CraftingMenu menu = new CraftingMenu(1, inventory);
		for (int index = 10; index < 46; index++) {
			menu.getSlot(index).set(new ItemStack(Items.COBBLESTONE, 64));
		}
		menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 3));

		ItemStack moved = menu.quickMoveStack(null, 1);

		assertEquals(3, moved.getCount());
		assertTrue(menu.getSlot(1).getItem().isEmpty());
		assertEquals(3, menu.getSlot(46).getItem().getCount());
	}

	@Test
	void 상자_메뉴에도_추가_27칸이_붙고_쉬프트클릭_목적지가_된다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		ChestMenu menu = ChestMenu.threeRows(1, inventory);

		assertEquals(90, menu.slots.size(), "상자 27 + 기존 인벤 36 + 추가 인벤 27");
		assertEquals(0, menu.getSlot(63).getContainerSlot());
		assertTrue(menu.getSlot(63).isActive());
		for (int index = 27; index < 63; index++) {
			menu.getSlot(index).set(new ItemStack(Items.COBBLESTONE, 64));
		}
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));

		ItemStack moved = menu.quickMoveStack(null, 0);

		assertEquals(3, moved.getCount());
		assertTrue(menu.getSlot(0).getItem().isEmpty());
		assertEquals(3, menu.slots.subList(63, 90).stream()
				.map(Slot::getItem).mapToInt(ItemStack::getCount).sum(),
				"바닐라 36칸이 가득 차면 추가 27칸 중 한 곳에 전부 들어가야 한다");
	}

	@Test
	void 공통_메뉴에_확장_Mixin_이_실제로_붙는다() {
		// refmap 이 없으므로 대상이 틀려도 빌드는 통과한다. 병합된 메서드 이름으로 본다.
		Set<String> merged = Arrays.stream(
						net.minecraft.world.inventory.AbstractContainerMenu.class
								.getDeclaredMethods())
				.map(Method::getName)
				.filter(name -> name.contains("sharedfate"))
				.collect(Collectors.toSet());

		assertTrue(merged.stream().anyMatch(name -> name.contains("appendExtraInventory")),
				"추가 27칸 붙이기가 병합되어야 한다: " + merged);
		assertTrue(merged.stream().anyMatch(name -> name.contains("moveAcrossExpandedInventory")),
				"쉬프트 클릭 이어붙이기가 병합되어야 한다: " + merged);
		assertTrue(ChestMenu.threeRows(1, new Inventory(null, new EntityEquipment()))
						instanceof ExpandedMenuLayout,
				"메뉴가 배치 통로를 구현해야 한다");
	}

	// ------------------------------------------------------------------- 자리

	@Test
	void 상자의_추가_27칸은_인벤토리_바로_아래_창_안쪽에_있다() {
		// 예전에는 창 오른쪽 바깥(x 184~237)에 붙어 있었다. 창 밖이라 바닐라가
		// 「창 밖을 눌렀다」로 읽고 들고 있던 아이템을 바닥에 버렸다.
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		ChestMenu menu = ChestMenu.threeRows(1, inventory);

		int inventoryTop = menu.getSlot(27).y;
		for (int extraIndex = 0; extraIndex < ExpandedInventoryManager.EXTRA_SIZE; extraIndex++) {
			Slot slot = menu.getSlot(63 + extraIndex);
			assertEquals(8 + (extraIndex % 9) * 18, slot.x);
			assertEquals(inventoryTop + 54 + (extraIndex / 9) * 18, slot.y);
			assertTrue(slot.x + 16 <= 176, "추가 칸이 창 넓이 176 안에 들어와야 한다");
		}
		assertEquals(inventoryTop + 112, menu.getSlot(54).y, "핫바가 추가 세 줄 아래로 내려가야 한다");
		assertEquals(inventoryTop + 36, menu.getSlot(53).y, "바닐라 세 줄은 제자리여야 한다");
	}

	@Test
	void 팀이_없으면_추가_칸을_치우고_핫바를_되돌린다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(false);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		ChestMenu menu = ChestMenu.threeRows(1, inventory);

		int inventoryTop = menu.getSlot(27).y;
		assertEquals(inventoryTop + 58, menu.getSlot(54).y, "핫바가 바닐라 자리여야 한다");
		for (int extraIndex = 0; extraIndex < ExpandedInventoryManager.EXTRA_SIZE; extraIndex++) {
			Slot slot = menu.getSlot(63 + extraIndex);
			assertFalse(slot.isActive(), "팀이 없으면 추가 칸은 꺼져 있어야 한다");
			assertEquals(ExpandedInventoryManager.HIDDEN_Y, slot.y);
		}
	}

	// --------------------------------------------------------- 쉬프트 클릭 연속성

	@Test
	void 상자_쉬프트클릭은_바닐라처럼_핫바_끝부터_채운다() {
		// 추가 27칸이 메뉴 번호로는 맨 뒤에 있어서, 손대지 않으면 역방향 이동이
		// 핫바보다 추가 칸을 먼저 채웠다.
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		ChestMenu menu = ChestMenu.threeRows(1, inventory);
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));

		menu.quickMoveStack(null, 0);

		assertEquals(3, menu.getSlot(62).getItem().getCount(), "핫바 맨 오른쪽이어야 한다");
	}

	@Test
	void 바닐라_27칸과_추가_27칸은_합칠_때_하나의_공간이다() {
		// 구간을 나눠 바닐라를 두 번 부르면 앞 구간의 빈칸이 뒤 구간의 합칠 자리보다
		// 먼저 쓰인다. 그러면 같은 아이템이 한 칸에 모이지 않고 흩어진다.
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		ChestMenu menu = ChestMenu.threeRows(1, inventory);
		menu.getSlot(27).set(new ItemStack(Items.DIAMOND, 60));
		menu.getSlot(63).set(new ItemStack(Items.DIAMOND, 60));
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 8));

		menu.quickMoveStack(null, 0);

		assertEquals(64, menu.getSlot(27).getItem().getCount(), "바닐라 칸이 가득 차야 한다");
		assertEquals(64, menu.getSlot(63).getItem().getCount(), "추가 칸도 함께 가득 차야 한다");
		assertTrue(menu.getSlot(62).getItem().isEmpty(), "빈칸을 먼저 쓰면 안 된다");
	}

	@Test
	void 핫바에서_쉬프트클릭하면_바닐라_세_줄_다음_추가_칸으로_이어진다() {
		// 제작대는 핫바에서 올릴 때 「윗줄만」 범위(10~37)로 부른다. 그 범위에도
		// 추가 27칸이 이어져야 위아래가 하나로 움직인다.
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		CraftingMenu menu = new CraftingMenu(1, inventory);
		// 제작 격자를 먼저 채워 둡니다. 비어 있으면 바닐라가 그리로 먼저 보냅니다.
		for (int index = 1; index < 37; index++) {
			menu.getSlot(index).set(new ItemStack(Items.COBBLESTONE, 64));
		}
		menu.getSlot(37).set(new ItemStack(Items.DIAMOND, 4));

		ItemStack moved = menu.quickMoveStack(null, 37);

		assertEquals(4, moved.getCount());
		assertEquals(4, menu.getSlot(46).getItem().getCount(),
				"바닐라 세 줄이 가득 차면 추가 첫 칸으로 이어져야 한다");
	}

	@Test
	void 팀이_없으면_쉬프트클릭이_추가_칸을_쓰지_않는다() {
		ExpandedInventoryManager.extraFor(null).setClientActive(false);
		Inventory inventory = new Inventory(null, new EntityEquipment());
		ChestMenu menu = ChestMenu.threeRows(1, inventory);
		for (int index = 27; index < 63; index++) {
			menu.getSlot(index).set(new ItemStack(Items.COBBLESTONE, 64));
		}
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));

		menu.quickMoveStack(null, 0);

		assertEquals(3, menu.getSlot(0).getItem().getCount(), "갈 곳이 없으므로 그대로여야 한다");
		assertEquals(0, menu.slots.subList(63, 90).stream()
				.map(Slot::getItem).mapToInt(ItemStack::getCount).sum(),
				"꺼진 추가 칸에 들어가면 안 된다");
	}

	@Test
	void 확장을_끄면_추가_칸_없이_바닐라_쉬프트클릭_그대로다() {
		SharedFateMod.config.mainInventoryRows = 3;
		try {
			Inventory inventory = new Inventory(null, new EntityEquipment());
			ChestMenu menu = ChestMenu.threeRows(1, inventory);

			assertEquals(63, menu.slots.size(), "상자 27 + 기존 인벤 36");
			menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));

			menu.quickMoveStack(null, 0);

			assertEquals(3, menu.getSlot(62).getItem().getCount());
		} finally {
			SharedFateMod.config.mainInventoryRows = 6;
		}
	}
}
