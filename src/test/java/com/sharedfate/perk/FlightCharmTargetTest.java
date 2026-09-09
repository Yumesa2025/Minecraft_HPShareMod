package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SlotFlightCharmLockMixin} 이 무는 바닐라 자리와, 「비행 부적」이 직접 읽고 쓰는
 * 바닐라 칸을 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상 서술자가 틀려도 <b>빌드가 그냥
 * 통과</b>하고, 그 자리를 처음 지나는 순간에야 터진다. 칸 잠금은 <b>인벤토리 화면을 여는 순간</b>
 * 지나는 자리라, 서술자가 어긋나면 서버가 멀쩡히 뜬 뒤 사람이 가방을 여는 순간 화면이 닫힌다.
 *
 * <p>{@code mayfly} 쪽은 더 조용하다. 칸 이름이 바뀌면 컴파일이 깨지므로 이 시험이 없어도
 * 잡히지만, <b>이름이 {@code mayFly} 가 아니라 {@code mayfly}(소문자 f)</b>라는 점과 그것이
 * 저장 자료에 그대로 실리는 {@code public boolean} 이라는 점을 여기서 함께 붙들어 둔다.
 */
class FlightCharmTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 칸 잠금

	/**
	 * 꺼내기 차단 지점. {@code @Inject} 의 {@code method} 문자열
	 * {@code mayPickup(Lnet/minecraft/world/entity/player/Player;)Z} 와 글자 하나까지 같아야 한다.
	 */
	@Test
	void 꺼내기_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> Slot.class.getDeclaredMethod("mayPickup", Player.class),
				"Slot.mayPickup(Player) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(boolean.class, target.getReturnType());
		assertTrue(Modifier.isPublic(target.getModifiers()));
		assertFalse(Modifier.isStatic(target.getModifiers()), "정적이면 인자 번호가 하나씩 밀린다");
	}

	/**
	 * 놓기 차단 지점. {@code @Inject} 의 {@code method} 문자열
	 * {@code mayPlace(Lnet/minecraft/world/item/ItemStack;)Z} 와 글자 하나까지 같아야 한다.
	 */
	@Test
	void 놓기_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> Slot.class.getDeclaredMethod("mayPlace", ItemStack.class),
				"Slot.mayPlace(ItemStack) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(boolean.class, target.getReturnType());
		assertTrue(Modifier.isPublic(target.getModifiers()));
		assertFalse(Modifier.isStatic(target.getModifiers()));
	}

	/**
	 * 화면을 열어 둔 채 누르는 Q({@code THROW})가 {@code mayPickup} 을 지나는 근거.
	 *
	 * <p>26.2 의 {@code AbstractContainerMenu.doClick} 은 그 갈래에서 {@code Slot.safeTake} 를
	 * 부르고, {@code safeTake} 는 {@code tryRemove} 를 거쳐 {@code mayPickup} 을 본다. 이 두
	 * 메서드가 사라지면 그 경로가 바뀐 것이므로 Q 차단을 다시 확인해야 한다.
	 */
	@Test
	void 화면_안_버리기가_지나는_두_메서드가_그대로_있다() {
		assertDoesNotThrow(
				() -> Slot.class.getDeclaredMethod("safeTake", int.class, int.class, Player.class),
				"Slot.safeTake 가 사라졌다. THROW 갈래가 mayPickup 을 지나는지 다시 봐야 한다");
		assertDoesNotThrow(
				() -> Slot.class.getDeclaredMethod("tryRemove", int.class, int.class, Player.class),
				"Slot.tryRemove 가 사라졌다. THROW 갈래가 mayPickup 을 지나는지 다시 봐야 한다");
	}

	/**
	 * 어느 칸인지 알아보는 근거.
	 *
	 * <p>{@code Slot.index} 는 생성자가 아니라 {@code AbstractContainerMenu.addSlot} 이
	 * <b>메뉴 안의 순번</b>으로 덮어쓰는 칸이라 화면마다 값이 달라진다. 그래서 잠금 판정은
	 * {@code getContainerSlot()}(인벤토리 안의 칸 번호)을 쓴다. 이 메서드가 사라지면 판정이
	 * 통째로 무너진다.
	 */
	@Test
	void 칸_번호를_읽는_길이_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> Slot.class.getDeclaredMethod("getContainerSlot"),
				"Slot.getContainerSlot() 이 사라졌다");

		assertEquals(int.class, target.getReturnType());
		assertFalse(Modifier.isStatic(target.getModifiers()));

		Field container = assertDoesNotThrow(
				() -> Slot.class.getDeclaredField("container"), "Slot.container 가 사라졌다");
		assertEquals(Container.class, container.getType());
		assertTrue(Modifier.isPublic(container.getModifiers()), "직접 읽는 칸이다");
	}

	/** 그 칸의 주인을 찾는 길. 공유 인벤토리라도 {@code Inventory.player} 는 사람마다 다르다. */
	@Test
	void 인벤토리의_주인을_읽는_칸이_그대로_있다() {
		Field owner = assertDoesNotThrow(
				() -> Inventory.class.getDeclaredField("player"), "Inventory.player 가 사라졌다");

		assertEquals(Player.class, owner.getType());
		assertTrue(Modifier.isPublic(owner.getModifiers()), "직접 읽는 칸이다");
	}

	/**
	 * 핫바가 인벤토리의 앞 {@code SELECTION_SIZE} 칸이라는 전제.
	 *
	 * <p>{@code LOCKED_SLOT = 0} 이 「핫바 맨 왼쪽」인 근거가 이것뿐이다. 핫바가 뒤로 옮겨지면
	 * 0번은 가방 안쪽 칸이 되어 엉뚱한 곳이 잠긴다.
	 */
	@Test
	void 핫바가_인벤토리_앞자리라는_전제가_그대로다() {
		assertEquals(9, Inventory.SELECTION_SIZE, "핫바는 9칸이다");
		assertEquals(36, Inventory.INVENTORY_SIZE, "주 인벤토리는 36칸이다");
	}

	/**
	 * <b>화면을 닫은 채</b> 누르는 Q 는 칸을 지나지 않는다는 증거.
	 *
	 * <p>26.2 의 {@code ServerGamePacketListenerImpl.handlePlayerAction} 은
	 * {@code ServerPlayer.drop(boolean)} 을 부르고, 그것이
	 * {@code Inventory.removeFromSelected(boolean)} 로 곧장 묶음을 뽑아 간다. 두 자리 어디에도
	 * {@code Slot} 이 없다. 이 시험은 그 <b>구멍이 아직 그 자리에 있다</b>는 것을 못박는다 —
	 * 나중에 Q 를 mixin 으로 막게 되면 여기가 그 대상 서술자가 된다.
	 */
	@Test
	void 화면_밖_버리기의_구멍이_그_자리에_있다() {
		Method drop = assertDoesNotThrow(
				() -> ServerPlayer.class.getDeclaredMethod("drop", boolean.class),
				"ServerPlayer.drop(boolean) 이 사라졌다. Q 키가 어디로 가는지 다시 봐야 한다");
		assertEquals(void.class, drop.getReturnType());

		Method removeFromSelected = assertDoesNotThrow(
				() -> Inventory.class.getDeclaredMethod("removeFromSelected", boolean.class),
				"Inventory.removeFromSelected(boolean) 이 사라졌다");
		assertEquals(ItemStack.class, removeFromSelected.getReturnType());
	}

	// ------------------------------------------------------------------ 비행 허가

	/**
	 * 비행 허가 칸.
	 *
	 * <p><b>이름은 {@code mayFly} 가 아니라 {@code mayfly} 다.</b> 26.2 바이트코드에서 확인했다.
	 * {@code public boolean} 이 아니게 되면 켜고 끄는 코드가 통째로 깨진다.
	 */
	@Test
	void 비행_허가_칸이_그대로_있다() {
		Field mayFly = assertDoesNotThrow(
				() -> Abilities.class.getDeclaredField("mayfly"),
				"Abilities.mayfly 가 사라졌거나 이름이 바뀌었다(소문자 f 다)");

		assertEquals(boolean.class, mayFly.getType());
		assertTrue(Modifier.isPublic(mayFly.getModifiers()), "직접 읽고 쓰는 칸이다");
		assertFalse(Modifier.isFinal(mayFly.getModifiers()));

		Field flying = assertDoesNotThrow(
				() -> Abilities.class.getDeclaredField("flying"), "Abilities.flying 이 사라졌다");
		assertEquals(boolean.class, flying.getType());
		assertTrue(Modifier.isPublic(flying.getModifiers()));
	}

	/**
	 * 바꾼 값을 클라이언트에 알리는 길.
	 *
	 * <p>이것이 바닐라 {@code ClientboundPlayerAbilitiesPacket} 을 보내므로 <b>통신 규약이
	 * 올라가지 않는다.</b> 이 메서드가 사라지면 모드를 안 깐 클라이언트에게 비행을 알릴 방법을
	 * 다시 찾아야 한다.
	 */
	@Test
	void 비행_허가를_알리는_길이_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> ServerPlayer.class.getDeclaredMethod("onUpdateAbilities"),
				"ServerPlayer.onUpdateAbilities() 가 사라졌다");

		assertEquals(void.class, target.getReturnType());
		assertTrue(Modifier.isPublic(target.getModifiers()));
		assertFalse(Modifier.isStatic(target.getModifiers()));
	}

	/** 켜기 전 값을 읽는 길. */
	@Test
	void 능력_묶음을_읽는_길이_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> Player.class.getDeclaredMethod("getAbilities"),
				"Player.getAbilities() 가 사라졌다");

		assertEquals(Abilities.class, target.getReturnType());
		assertFalse(Modifier.isStatic(target.getModifiers()));
	}
}
