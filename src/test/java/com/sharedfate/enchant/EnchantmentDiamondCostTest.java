package com.sharedfate.enchant;

import com.sharedfate.SharedFateMod;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.inventory.ExpandedInventoryContainer;
import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인챈트의 대가가 경험치 레벨에서 다이아몬드로 바뀌었는지 <b>진짜 메뉴로</b> 확인합니다.
 *
 * <p>여기서 만드는 것은 바닐라 {@link EnchantmentMenu} 그 자체입니다. 아래 단언은
 * {@code EnchantmentMenuDiamondMixin} 이 실제로 붙어야만 통과합니다 — 붙지 않으면 레벨 0
 * 인 플레이어는 어느 칸도 누르지 못하고, 다이아몬드는 아예 보지도 않습니다.
 *
 * <p><b>플레이어를 만드는 방법에 대하여.</b> {@code Player} 의 생성자는 {@code Level} 을
 * 요구하고, {@code Level} 의 생성자는 생물군계·피해 종류 같은 데이터팩 레지스트리를
 * 요구합니다. 시험 한 판을 위해 월드를 통째로 세울 수는 없으므로, 생성자를 건너뛰고
 * <b>이 시험이 실제로 쓰는 세 가지만</b>(인벤토리, 능력, 난수) 채운 껍데기를 씁니다.
 * 인챈트 검사가 플레이어에게서 읽는 것은 그 셋과 {@code experienceLevel}·
 * {@code enchantmentSeed} 뿐입니다.
 */
class EnchantmentDiamondCostTest {
	private static SharedFateConfig previousConfig;

	/** {@link Player} 는 추상 클래스라 껍데기가 하나 필요합니다. 생성자는 불리지 않습니다. */
	private static final class HollowPlayer extends Player {
		private HollowPlayer() {
			super(null, null);
			throw new AssertionError("이 생성자는 절대 불리지 않습니다");
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}
	}

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

	// ------------------------------------------------------------------ 메뉴

	@Test
	void 다이아몬드가_네_개면_레벨이_아무리_있어도_세_칸_모두_막힌다() throws Exception {
		Player player = hollowPlayer(false);
		player.experienceLevel = 100;
		giveToInventory(player, 4);
		EnchantmentMenu menu = readyMenu(player, 3);

		for (int slot = 0; slot < 3; slot++) {
			assertFalse(menu.clickMenuButton(player, slot),
					slot + "번 칸이 다이아몬드 4개로 눌리면 안 된다");
		}
	}

	@Test
	void 다이아몬드가_다섯_개면_레벨이_0이어도_세_칸_모두_눌린다() throws Exception {
		Player player = hollowPlayer(false);
		player.experienceLevel = 0;
		giveToInventory(player, 5);
		EnchantmentMenu menu = readyMenu(player, 3);

		for (int slot = 0; slot < 3; slot++) {
			assertTrue(menu.clickMenuButton(player, slot),
					slot + "번 칸이 레벨 0·다이아몬드 5개로 눌려야 한다");
		}
	}

	@Test
	void 청금석이_모자라면_그_칸만_막힌다() throws Exception {
		Player player = hollowPlayer(false);
		player.experienceLevel = 0;
		giveToInventory(player, 64);
		EnchantmentMenu menu = readyMenu(player, 1);

		assertTrue(menu.clickMenuButton(player, 0), "청금석 1개면 첫 칸은 눌린다");
		assertFalse(menu.clickMenuButton(player, 1), "둘째 칸은 청금석 2개가 필요하다");
		assertFalse(menu.clickMenuButton(player, 2), "셋째 칸은 청금석 3개가 필요하다");
	}

	@Test
	void 확장_패널의_다이아몬드도_센다() throws Exception {
		Player player = hollowPlayer(false);
		player.experienceLevel = 0;
		giveToInventory(player, 2);
		ExpandedInventoryManager.extraFor(player).getItems().set(4, new ItemStack(Items.DIAMOND, 3));
		EnchantmentMenu menu = readyMenu(player, 3);

		assertEquals(5, EnchantmentDiamondCost.count(player));
		assertTrue(menu.clickMenuButton(player, 2), "인벤토리 2 + 확장 3 = 5 라 눌려야 한다");
	}

	@Test
	void 접근자가_비어_있으면_눌려도_다이아몬드가_사라지지_않는다() throws Exception {
		// 클라이언트의 접근자가 바로 ContainerLevelAccess.NULL 입니다. 여기서 다이아몬드가
		// 줄면 서버와 즉시 어긋납니다 — 차감이 execute 람다 밖으로 새어 나온 것입니다.
		Player player = hollowPlayer(false);
		giveToInventory(player, 9);
		EnchantmentMenu menu = readyMenu(player, 3);

		assertTrue(menu.clickMenuButton(player, 0));

		assertEquals(9, EnchantmentDiamondCost.count(player), "람다가 돌지 않았으면 걷지 않아야 한다");
		assertEquals(3, menu.getGoldCount(), "청금석도 그대로여야 한다");
	}

	@Test
	void 크리에이티브는_다이아몬드가_하나도_없어도_눌린다() throws Exception {
		Player player = hollowPlayer(true);
		player.experienceLevel = 0;
		EnchantmentMenu menu = readyMenu(player, 0);

		assertTrue(menu.clickMenuButton(player, 2), "크리에이티브는 청금석도 다이아몬드도 필요 없다");
	}

	@Test
	void 인챈트_메뉴에_다이아몬드_Mixin_이_실제로_붙는다() {
		Set<String> merged = Arrays.stream(EnchantmentMenu.class.getDeclaredMethods())
				.map(Method::getName)
				.filter(name -> name.contains("sharedfate"))
				.collect(Collectors.toSet());

		assertTrue(merged.stream().anyMatch(name -> name.contains("requireDiamonds")),
				"다이아몬드 검사가 병합되어야 한다: " + merged);
		assertTrue(merged.stream().anyMatch(name -> name.contains("dropLevelRequirement")),
				"요구 레벨 무력화가 병합되어야 한다: " + merged);
		assertTrue(merged.stream().anyMatch(name -> name.contains("chargeDiamonds")),
				"다이아몬드 차감이 병합되어야 한다: " + merged);
	}

	// ------------------------------------------------------------- 레벨 소모

	@Test
	void 인챈트를_해도_경험치_레벨이_줄지_않고_씨앗만_바뀐다() throws Exception {
		Player player = hollowPlayer(false);
		player.experienceLevel = 7;
		int seedBefore = player.getEnchantmentSeed();

		player.onEnchantmentPerformed(new ItemStack(Items.BOOK), 3);

		assertEquals(7, player.experienceLevel, "레벨을 깎으면 안 된다");
		assertNotEquals(seedBefore, player.getEnchantmentSeed(),
				"씨앗을 갱신하지 않으면 같은 인챈트 후보가 계속 뜬다");
	}

	// --------------------------------------------------------------- 차감

	@Test
	void 다이아몬드는_인벤토리를_먼저_비우고_모자라면_확장_패널에서_채운다() throws Exception {
		Player player = hollowPlayer(false);
		giveToInventory(player, 2);
		ExpandedInventoryContainer extra = ExpandedInventoryManager.extraFor(player);
		extra.getItems().set(0, new ItemStack(Items.DIAMOND, 10));

		assertEquals(5, EnchantmentDiamondCost.consume(player, 0));

		assertEquals(0, EnchantmentDiamondCost.count(player.getInventory().getNonEquipmentItems()));
		assertEquals(7, extra.getItem(0).getCount(), "모자란 3개만 확장 패널에서 가져간다");
	}

	@Test
	void 크리에이티브에서는_다이아몬드를_걷지_않는다() throws Exception {
		Player player = hollowPlayer(true);
		giveToInventory(player, 9);

		assertEquals(0, EnchantmentDiamondCost.consume(player, 0));
		assertEquals(9, EnchantmentDiamondCost.count(player));
	}

	// --------------------------------------------------------------- 표시

	@Test
	void 단추_숫자는_요구_레벨_대신_다이아몬드_개수가_되고_빈_칸은_0으로_남는다() {
		assertArrayEqualsInt(new int[] {5, 5, 5}, EnchantmentDiamondCost.displayCosts(new int[] {3, 12, 27}));
		assertArrayEqualsInt(new int[] {0, 5, 0}, EnchantmentDiamondCost.displayCosts(new int[] {0, 9, 0}));
	}

	@Test
	void 세_칸의_다이아몬드_요구량이_같다() {
		assertEquals(5, EnchantmentDiamondCost.forSlot(0));
		assertEquals(5, EnchantmentDiamondCost.forSlot(1));
		assertEquals(5, EnchantmentDiamondCost.forSlot(2));
	}

	private static void assertArrayEqualsInt(int[] expected, int[] actual) {
		assertEquals(Arrays.toString(expected), Arrays.toString(actual));
	}

	// --------------------------------------------------------------- 도구

	private static EnchantmentMenu readyMenu(Player player, int lapis) {
		EnchantmentMenu menu = new EnchantmentMenu(1, player.getInventory());
		// 인챈트할 물건이 먼저 들어가야 합니다. 비어 있으면 바닐라가 costs 를 0 으로 지웁니다.
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
		if (lapis > 0) {
			menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, lapis));
		}
		// costs 는 서버가 월드를 보며 계산합니다. 여기서는 월드가 없으므로 직접 채웁니다.
		menu.costs[0] = 5;
		menu.costs[1] = 12;
		menu.costs[2] = 27;
		return menu;
	}

	private static void giveToInventory(Player player, int diamonds) {
		if (diamonds > 0) {
			player.getInventory().getNonEquipmentItems().set(0, new ItemStack(Items.DIAMOND, diamonds));
		}
	}

	private static Player hollowPlayer(boolean creative) throws Exception {
		Player player = (Player) unsafe().allocateInstance(HollowPlayer.class);
		setField(Entity.class, player, "random", RandomSource.create(20260901L));
		Abilities abilities = new Abilities();
		abilities.instabuild = creative;
		setField(Player.class, player, "abilities", abilities);
		setField(Player.class, player, "inventory", new Inventory(player, new EntityEquipment()));
		return player;
	}

	private static void setField(Class<?> owner, Object target, String name, Object value)
			throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static sun.misc.Unsafe unsafe() throws Exception {
		Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (sun.misc.Unsafe) field.get(null);
	}
}
