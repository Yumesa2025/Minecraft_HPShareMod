package com.sharedfate.enchant;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모루 다이아몬드 Mixin 두 개가 <b>바닐라 쪽 사실</b>에 기대고 있다. 그 사실이 바뀌면
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
