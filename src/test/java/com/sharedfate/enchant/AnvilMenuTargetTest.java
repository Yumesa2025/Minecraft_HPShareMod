package com.sharedfate.enchant;

import com.sharedfate.TestBootstrap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모루 다이아몬드 Mixin 세 개가 <b>바닐라 쪽 사실</b>에 기대고 있다. 그 사실이 바뀌면
 * 여기서 먼저 터진다.
 *
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드가 그냥
 * 통과</b>하고, 모루를 여는 순간 터진다.
 *
 * <ul>
 *   <li>{@code AnvilMenuDiamondMixin} — {@code AnvilMenu} 자체에 건다(생성자·mayPickup·
 *       onTake·createResult·getCost)</li>
 *   <li>{@code AnvilMenuQuickMoveMixin} — {@code ItemCombinerMenu} 에 건다(quickMoveStack·
 *       removed). {@code AnvilMenu} 가 그 둘을 재정의하지 <b>않기</b> 때문이다</li>
 *   <li>{@code AnvilScreenMixin} — {@code AnvilScreen.extractLabels} 안의 비용 라벨을
 *       「다이아몬드 10개」로 갈아 끼운다</li>
 * </ul>
 */
class AnvilMenuTargetTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- AnvilMenu 자체에 거는 자리

	@Test
	void mayPickup_이_AnvilMenu_자신의_메서드다() throws Exception {
		Method mayPickup = AnvilMenu.class.getDeclaredMethod(
				"mayPickup", Player.class, boolean.class);
		assertEquals(boolean.class, mayPickup.getReturnType());
		assertFalse(Modifier.isStatic(mayPickup.getModifiers()));
	}

	@Test
	void onTake_이_AnvilMenu_자신의_메서드다() throws Exception {
		Method onTake = AnvilMenu.class.getDeclaredMethod("onTake", Player.class, ItemStack.class);
		assertEquals(void.class, onTake.getReturnType());
	}

	@Test
	void createResult_과_getCost_가_AnvilMenu_자신의_메서드다() throws Exception {
		Method createResult = AnvilMenu.class.getDeclaredMethod("createResult");
		assertEquals(void.class, createResult.getReturnType());

		Method getCost = AnvilMenu.class.getDeclaredMethod("getCost");
		assertEquals(int.class, getCost.getReturnType());
	}

	@Test
	void cost_필드가_final_DataSlot_이다() throws Exception {
		var field = AnvilMenu.class.getDeclaredField("cost");
		assertEquals(DataSlot.class, field.getType());
		assertTrue(Modifier.isFinal(field.getModifiers()));
	}

	// -------------------------------------------------- ItemCombinerMenu 에 거는 자리

	/**
	 * {@code AnvilMenu} 가 이 둘을 <b>재정의하지 않는다.</b> 재정의하게 되면
	 * {@code AnvilMenuQuickMoveMixin} 이 {@code ItemCombinerMenu} 에 걸어 둔 처리기가 조용히
	 * 실행되지 않는다 — refmap 없는 이 저장소에서 가장 위험한 형태의 실패다.
	 */
	@Test
	void AnvilMenu_는_quickMoveStack_과_removed_를_재정의하지_않는다() {
		assertThrows(NoSuchMethodException.class,
				() -> AnvilMenu.class.getDeclaredMethod(
						"quickMoveStack", Player.class, int.class),
				"AnvilMenu 가 quickMoveStack 을 갖게 되면 ItemCombinerMenu 쪽 Mixin 이 걸리지 않는다");
		assertThrows(NoSuchMethodException.class,
				() -> AnvilMenu.class.getDeclaredMethod("removed", Player.class),
				"AnvilMenu 가 removed 를 갖게 되면 ItemCombinerMenu 쪽 Mixin 이 걸리지 않는다");
	}

	/** 그래서 대신 {@code ItemCombinerMenu} 에 건다. 그 클래스가 진짜로 두 메서드를 갖는다. */
	@Test
	void ItemCombinerMenu_가_quickMoveStack_과_removed_를_갖는다() throws Exception {
		Method quickMoveStack = ItemCombinerMenu.class.getDeclaredMethod(
				"quickMoveStack", Player.class, int.class);
		assertEquals(ItemStack.class, quickMoveStack.getReturnType());
		assertFalse(Modifier.isStatic(quickMoveStack.getModifiers()));

		Method removed = ItemCombinerMenu.class.getDeclaredMethod("removed", Player.class);
		assertEquals(void.class, removed.getReturnType());
	}

	// -------------------------------------------------- AnvilScreen 에 거는 자리

	/**
	 * {@code AnvilScreenMixin} 이 무는 메서드. 서술자가 바뀌면 모루를 여는 순간 터진다.
	 *
	 * <p><b>{@code AnvilScreen} 자신이 갖고 있어야 한다.</b> 부모
	 * ({@code ItemCombinerScreen})에만 있게 되면 우리 주입은 아무 데도 붙지 않고, 자식이
	 * 생겨 재정의하면 조용히 실행되지 않는다.
	 */
	@Test
	void extractLabels_가_AnvilScreen_자신의_메서드다() throws Exception {
		Method extractLabels = AnvilScreen.class.getDeclaredMethod(
				"extractLabels", GuiGraphicsExtractor.class, int.class, int.class);
		assertEquals(void.class, extractLabels.getReturnType());
		assertFalse(Modifier.isStatic(extractLabels.getModifiers()));
	}

	/**
	 * 그 안에서 <b>번역 열쇠로</b> 비용 라벨을 만든다는 사실. {@code AnvilScreenMixin} 은
	 * {@code Component.translatable} 호출을 가로채 글월을 갈아 끼우고,
	 * {@code AnvilDiamondLabel} 은 열쇠를 보고 그것이 비용 라벨인지 가린다.
	 *
	 * <p>상수 풀은 아스키라 클래스 파일 바이트를 그대로 훑어도 찾을 수 있다. 바닐라가 이
	 * 열쇠를 버리거나 글월 조립 방식을 바꾸면 여기서 먼저 터진다.
	 */
	@Test
	void AnvilScreen_이_container_repair_cost_를_translatable_로_만든다() throws IOException {
		assertTrue(classBytesOf(AnvilScreen.class).contains(AnvilDiamondLabel.REPAIR_COST),
				"비용 라벨의 번역 열쇠가 사라졌다 — AnvilDiamondLabel 이 아무것도 못 고친다");
		assertTrue(classBytesOf(AnvilScreen.class).contains(
						"(Ljava/lang/String;[Ljava/lang/Object;)"
								+ "Lnet/minecraft/network/chat/MutableComponent;"),
				"Component.translatable 서술자가 바뀌었다 — @At 대상이 틀린다");
	}

	/**
	 * 「비용이 너무 비쌉니다」는 <b>호출이 아니라 정적 밭</b>이다. 그래서 위 주입이
	 * {@code allow = 1} 로 딱 하나만 걸린다.
	 */
	@Test
	void 너무_비쌉니다_는_정적_밭이라_translatable_호출이_아니다() throws Exception {
		var field = AnvilScreen.class.getDeclaredField("TOO_EXPENSIVE_TEXT");
		assertTrue(Modifier.isStatic(field.getModifiers()));
		assertEquals(net.minecraft.network.chat.Component.class, field.getType());
	}

	private static String classBytesOf(Class<?> type) throws IOException {
		String path = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream in = type.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("클래스 파일을 찾지 못했습니다: " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		}
	}

	/**
	 * {@code instanceof AnvilMenu} 가드가 무엇을 막는지 못박는다.
	 *
	 * <p>{@code ItemCombinerMenu} 를 물려받는 것은 모루만이 아니라 <b>대장장이 작업대</b>
	 * ({@code SmithingMenu})도 마찬가지다. 그 부모에 주입하면 대장장이 작업대까지 걸리므로,
	 * 가드가 없으면 그 화면이 있지도 않은 다이아몬드 칸을 찾다가 엉뚱하게 동작한다.
	 *
	 * <p><b>연마석은 이 갈래가 아니다.</b> {@code GrindstoneMenu} 는
	 * {@code AbstractContainerMenu} 를 직접 물려받으므로 애초에 이 주입에 걸리지 않는다 —
	 * 그쪽은 {@code ExpandedQuickMoveFallbackMixin} 이 맡는다. 26.2 바이트코드로 확인했다.
	 */
	@Test
	void 가드가_막는_것은_연마석이_아니라_대장장이_작업대다() {
		assertTrue(ItemCombinerMenu.class.isAssignableFrom(SmithingMenu.class),
				"대장장이 작업대는 같은 부모라 가드가 없으면 함께 걸린다");
		assertFalse(ItemCombinerMenu.class.isAssignableFrom(GrindstoneMenu.class),
				"연마석은 이 갈래가 아니다 — 이것이 참이 되면 가드의 뜻이 달라진다");
	}

}
