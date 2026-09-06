package com.sharedfate.inventory;

import com.sharedfate.SharedFateMod;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 주민 거래가 <b>추가 27칸의 재료</b>도 값으로 인정하는지 본다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 대상이 틀려도 <b>빌드가 그냥 통과</b>하고 그 화면을
 * 여는 순간에야 터진다. 그래서 대상 메서드를 반사로 직접 부른다 — 이름이나 서술자가 바뀌면
 * 여기서 먼저 터진다.
 */
class ExpandedMerchantMenuTest {
	/** 거래 칸 세 개 — 값1, 값2, 결과. 플레이어 인벤토리는 그다음이다. */
	private static final int TRADE_SLOTS = 3;
	/** 바닐라가 값 채우기에서 훑는 끝 번호. 거래 칸 3 + 인벤토리 36. */
	private static final int VANILLA_INVENTORY_END = 39;

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

	private static MerchantMenu openMerchant() {
		ExpandedInventoryManager.extraFor(null).setClientActive(true);
		return new MerchantMenu(1, new Inventory(null, new EntityEquipment()));
	}

	@Test
	void 거래_화면에도_기존_번호_뒤에_추가_27칸을_붙인다() {
		MerchantMenu menu = openMerchant();

		assertEquals(VANILLA_INVENTORY_END + ExpandedInventoryManager.EXTRA_SIZE,
				menu.slots.size(), "거래 3 + 기존 인벤 36 + 추가 인벤 27");
		assertEquals(TRADE_SLOTS, ((ExpandedMenuLayout) menu).sharedfate$playerSlotStart());
		assertEquals(VANILLA_INVENTORY_END, ((ExpandedMenuLayout) menu).sharedfate$extraSlotStart(),
				"추가 칸이 바닐라 36칸 바로 뒤에 이어져야 끝값만 밀어서 함께 훑을 수 있다");
	}

	/**
	 * 값 채우기가 <b>추가 칸까지</b> 훑는지 본다.
	 *
	 * <p>바닐라 {@code moveFromInventoryToPaymentSlot} 을 반사로 직접 부른다. 이름이나
	 * 서술자가 바뀌면 여기서 터지고, 그때는 {@code ExpandedMerchantMenuMixin} 의
	 * {@code @ModifyConstant} 도 함께 죽는다.
	 */
	@Test
	void 값_채우기가_추가_칸의_에메랄드를_찾아낸다() throws Exception {
		MerchantMenu menu = openMerchant();
		menu.getSlot(VANILLA_INVENTORY_END).set(new ItemStack(Items.EMERALD, 10));

		fillPaymentSlot(menu, new ItemCost(Items.EMERALD, 5));

		assertEquals(10, menu.getSlot(0).getItem().getCount(),
				"아래 칸의 에메랄드가 값 칸으로 옮겨져야 한다");
		assertTrue(menu.getSlot(VANILLA_INVENTORY_END).getItem().isEmpty(),
				"옮긴 만큼만 줄어야 한다 — 사본이 생기면 아이템이 늘어난다");
	}

	/** 바닐라 36칸에 있던 재료는 예전과 똑같이 먼저 쓰인다. */
	@Test
	void 값_채우기는_바닐라_칸을_먼저_쓴다() throws Exception {
		MerchantMenu menu = openMerchant();
		menu.getSlot(TRADE_SLOTS).set(new ItemStack(Items.EMERALD, 4));
		menu.getSlot(VANILLA_INVENTORY_END).set(new ItemStack(Items.EMERALD, 7));

		fillPaymentSlot(menu, new ItemCost(Items.EMERALD, 5));

		assertEquals(11, menu.getSlot(0).getItem().getCount(),
				"둘을 합쳐 한 칸에 모여야 한다");
		assertTrue(menu.getSlot(TRADE_SLOTS).getItem().isEmpty());
		assertTrue(menu.getSlot(VANILLA_INVENTORY_END).getItem().isEmpty());
	}

	/** 팀이 없으면 추가 칸은 꺼져 있고, 값 채우기도 예전 그대로여야 한다. */
	@Test
	void 확장을_끄면_값_채우기가_바닐라_범위_그대로다() throws Exception {
		SharedFateMod.config.mainInventoryRows = 3;
		try {
			MerchantMenu menu = new MerchantMenu(1, new Inventory(null, new EntityEquipment()));

			assertEquals(VANILLA_INVENTORY_END, menu.slots.size(), "거래 3 + 기존 인벤 36");
			menu.getSlot(TRADE_SLOTS).set(new ItemStack(Items.EMERALD, 4));

			fillPaymentSlot(menu, new ItemCost(Items.EMERALD, 5));

			assertEquals(4, menu.getSlot(0).getItem().getCount());
		} finally {
			SharedFateMod.config.mainInventoryRows = 6;
		}
	}

	@Test
	void 추가_칸의_쉬프트_클릭은_빈_핫바를_먼저_채운다() {
		MerchantMenu menu = openMerchant();
		menu.getSlot(VANILLA_INVENTORY_END).set(new ItemStack(Items.DIAMOND, 3));

		ItemStack moved = menu.quickMoveStack(null, VANILLA_INVENTORY_END);

		assertEquals(3, moved.getCount());
		assertTrue(menu.getSlot(VANILLA_INVENTORY_END).getItem().isEmpty());
		assertEquals(3, menu.getSlot(TRADE_SLOTS + ExpandedInventoryManager.EXTRA_SIZE)
				.getItem().getCount(), "핫바 첫 칸으로 가야 한다");
	}

	/** refmap 이 없으므로 대상이 틀려도 빌드는 통과한다. 병합된 메서드 이름으로 본다. */
	@Test
	void 거래_메뉴에_확장_Mixin_이_실제로_붙는다() {
		Set<String> merged = Arrays.stream(MerchantMenu.class.getDeclaredMethods())
				.map(Method::getName)
				.filter(name -> name.contains("sharedfate"))
				.collect(Collectors.toSet());

		assertTrue(merged.stream().anyMatch(name -> name.contains("alsoScanExtraSlots")),
				"값 채우기 범위 넓히기가 병합되어야 한다: " + merged);
		assertTrue(merged.stream().anyMatch(name -> name.contains("quickMoveFromExtra")),
				"추가 칸 쉬프트 클릭이 병합되어야 한다: " + merged);
		assertDoesNotThrow(() -> paymentFiller(),
				"이 메서드가 사라지면 값 채우기 범위를 넓힐 자리가 없다");
	}

	private static Method paymentFiller() throws NoSuchMethodException {
		Method filler = MerchantMenu.class.getDeclaredMethod(
				"moveFromInventoryToPaymentSlot", int.class, ItemCost.class);
		filler.setAccessible(true);
		return filler;
	}

	private static void fillPaymentSlot(MerchantMenu menu, ItemCost cost) throws Exception {
		paymentFiller().invoke(menu, 0, cost);
	}
}
