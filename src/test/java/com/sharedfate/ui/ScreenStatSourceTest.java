package com.sharedfate.ui;

import com.sharedfate.TestBootstrap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 화면 두 가지가 <b>바닐라 쪽 사실</b>에 기대고 있다. 그 사실이 바뀌면 여기서 먼저 터진다.
 *
 * <p>시험 소스셋은 {@code src/client} 의 우리 코드는 보지 못하지만 <b>바닐라 클라이언트
 * 클래스는 볼 수 있다.</b> 이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상이 틀려도
 * <b>빌드가 그냥 통과</b>하고 그 화면이 열리는 순간에야 터지므로, 대상 서술자만이라도 여기서
 * 붙들어 둔다.
 */
class ScreenStatSourceTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- 인벤토리 「팀」 단추가 무는 자리

	/** {@code InventoryScreenTeamButtonMixin} 이 파고드는 두 메서드. */
	@Test
	void 인벤토리_화면에_믹스인_대상_메서드가_그대로_있다() {
		assertDoesNotThrow(() -> InventoryScreen.class.getDeclaredMethod("init"),
				"init 이 사라지면 「팀」 단추가 아예 붙지 않는다");
		assertDoesNotThrow(() -> InventoryScreen.class.getDeclaredMethod("extractRenderState",
						GuiGraphicsExtractor.class, int.class, int.class, float.class),
				"이 서술자가 바뀌면 단추가 조합법 책을 따라 옮겨 가지 못한다");
	}

	/** 접근자 세 가지가 읽는 밭. 이름이 바뀌면 자리 계산이 통째로 멈춘다. */
	@Test
	void 창_좌표_밭이_그대로_있다() {
		for (String field : new String[] {"imageWidth", "imageHeight", "leftPos", "topPos"}) {
			assertDoesNotThrow(() -> AbstractContainerScreen.class.getDeclaredField(field), field);
		}
		assertDoesNotThrow(
				() -> Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class),
				"이것이 없으면 화면에 단추를 얹을 길이 없다");
	}

	/**
	 * 조합법 책 판의 크기. {@link InventoryTeamButton#anchorLeft} 가 이 값으로 책을 피한다.
	 *
	 * <p>{@code OFFSET_X_POSITION} 은 {@code private} 이라 반사로 읽는다. 인라인되는 상수라
	 * 우리 쪽에도 같은 숫자가 적혀 있으니, 어긋나면 단추가 펼쳐진 책에 깔린다.
	 */
	@Test
	void 조합법_책의_크기가_그대로다() {
		assertEquals(147, RecipeBookComponent.IMAGE_WIDTH);
		assertEquals(86, readPrivateInt(RecipeBookComponent.class, "OFFSET_X_POSITION"));
	}

	// -------------------------------------------------- 능력치 탭이 읽는 값

	/**
	 * 세 가지는 서버가 클라이언트에 보내 준다 — 그래서 통신 규약을 올리지 않고 적을 수 있다.
	 *
	 * <p>{@code AttributeMap.getSyncableAttributes} 가 이 표를 보고 거르고,
	 * {@code ServerEntity} 가 그 묶음을 <b>본인에게도</b> 보낸다.
	 */
	@Test
	void 최대_체력과_방어력과_이동_속도는_클라이언트로_온다() {
		assertSyncable(Attributes.MAX_HEALTH, true);
		assertSyncable(Attributes.ARMOR, true);
		assertSyncable(Attributes.MOVEMENT_SPEED, true);
	}

	/**
	 * 공격력만은 오지 않는다. 우리가 {@code AttackDamagePayload} 를 따로 두는 <b>유일한
	 * 이유</b>다.
	 *
	 * <p><b>이 시험을 지우지 말 것.</b> 지금은 서버가 그 값을 직접 보내 주므로 능력치 탭에
	 * 공격력이 적히지만, 그 패킷과 통신 규약 한 칸은 여기가 거짓인 동안에만 값어치가 있다.
	 * 바닐라가 언젠가 이 속성에도 {@code setSyncable(true)} 를 붙이는 날이 오면 여기가 먼저
	 * 터져서 「이제 패킷을 걷어내도 된다」고 알려 준다.
	 */
	@Test
	void 공격력은_클라이언트로_오지_않는다() {
		assertSyncable(Attributes.ATTACK_DAMAGE, false);
	}

	/**
	 * 플레이어의 바닐라 기본값. 화면이 「→」 왼쪽에 적는 값이 곧 이것이다.
	 *
	 * <p>{@code AttributeInstance.getBaseValue()} 가 돌려주는 것은 <b>플레이어의</b> 기본값이지
	 * 속성 등록표의 기본값이 아니다. 둘은 실제로 다르다 — 26.2 의 {@code movement_speed} 등록
	 * 기본값은 0.7 이고, 플레이어는 그것을 0.1 로 덮어쓴다. <b>이동 속도를 백분율로 적는 까닭이
	 * 이 0.1 이다</b> — 그대로 적으면 빠른지 느린지 알 수 없다.
	 */
	@Test
	void 플레이어의_바닐라_기본값이_그대로다() {
		AttributeSupplier player = Player.createAttributes().build();
		assertEquals(20.0, player.getBaseValue(Attributes.MAX_HEALTH));
		assertEquals(0.0, player.getBaseValue(Attributes.ARMOR));
		assertEquals(0.1, player.getBaseValue(Attributes.MOVEMENT_SPEED), 1.0E-7);
		// 공격력은 서버가 보내 주지만 「→」 왼쪽에 적는 기준은 나머지 셋과 똑같다 —
		// 플레이어의 바닐라 기본값, 곧 맨손으로 때렸을 때의 1.0 이다.
		assertEquals(1.0, player.getBaseValue(Attributes.ATTACK_DAMAGE), 1.0E-7);
	}

	private static void assertSyncable(Holder<Attribute> attribute, boolean expected) {
		String name = attribute.value().getDescriptionId();
		if (expected) {
			assertTrue(attribute.value().isClientSyncable(), name);
		} else {
			assertFalse(attribute.value().isClientSyncable(), name);
		}
	}

	private static int readPrivateInt(Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.getInt(null);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError(owner.getSimpleName() + "." + name + " 을 읽지 못했습니다", error);
		}
	}
}
