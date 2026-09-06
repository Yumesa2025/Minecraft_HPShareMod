package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.SneakSpeedEffect;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sneak_speed} 가 딛고 선 바닐라 값들을 못박는다.
 *
 * <p>이 효과는 믹스인을 쓰지 않지만, 바닐라 숫자 세 개 위에 서 있다. 하나라도 조용히 바뀌면
 * 「달리기의 1.35배」가 아무 경고 없이 다른 값이 되어 버린다.
 *
 * <ul>
 *   <li>{@code minecraft:sneaking_speed} 의 기본값·상한 — 상한이 1.0 이라 이 속성만으로는
 *       달리기를 넘길 수 없다는 것이 이 효과가 존재하는 까닭이다.</li>
 *   <li>{@code minecraft:sprinting} 수정자의 값 — 달리기가 걷기의 몇 배인가.</li>
 *   <li>{@code movement_speed} 의 상한 — 웅크리는 동안 얹는 값이 잘리지 않아야 한다.</li>
 * </ul>
 *
 * <p>클라이언트가 웅크림 감쇠를 거는 자리({@code LocalPlayer.modifyInput} 안의
 * {@code isMovingSlowly()} 분기)는 클라이언트 전용 클래스라 이 시험에서 직접 잡을 수 없다.
 * 대신 그 판정이 부르는 두 메서드가 {@code Entity} 에 그대로 있는지를 본다 — 서버에서 같은
 * 판정을 하려면 이 둘이 있어야 한다.
 */
class SneakSpeedTargetTest {

	/** 바닐라가 달리기 배수를 얹을 때 쓰는 수정자 이름. */
	private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting");

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- 웅크림 비율

	@Test
	void 웅크림_비율은_기본_0_3_에_상한_1_0_이다() {
		Attribute attribute = Attributes.SNEAKING_SPEED.value();
		RangedAttribute ranged = assertInstanceOf(RangedAttribute.class, attribute,
				"범위가 있는 속성이 아니면 상한을 읽을 수 없다");

		assertEquals(0.3, ranged.getDefaultValue(), 1.0e-9);
		assertEquals(0.0, ranged.getMinValue(), 1.0e-9);
		assertEquals(1.0, ranged.getMaxValue(), 1.0e-9,
				"상한이 1.0 이라 이 속성만으로는 걷는 속도를 넘길 수 없다");
	}

	@Test
	void 상한을_넘겨_적으면_잘린다() {
		RangedAttribute ranged =
				assertInstanceOf(RangedAttribute.class, Attributes.SNEAKING_SPEED.value());

		// 「0.3 + 1.13 = 1.43」 으로 적은 정의가 실제로는 1.0 이 된다는 것을 그대로 보인다.
		assertEquals(1.0, ranged.sanitizeValue(0.3 + 1.13), 1.0e-9,
				"이 자르기가 있어서 sneaking_speed 만으로는 달리기를 넘길 수 없다");
	}

	@Test
	void 웅크림_비율은_클라이언트에_전해진다() {
		assertTrue(Attributes.SNEAKING_SPEED.value().isClientSyncable(),
				"이 값을 실제로 곱하는 곳은 클라이언트다. 전해지지 않으면 아무 일도 일어나지 않는다");
		assertTrue(Attributes.MOVEMENT_SPEED.value().isClientSyncable());
	}

	@Test
	void 플레이어가_두_속성을_모두_가진다() {
		AttributeSupplier supplier = Player.createAttributes().build();

		assertTrue(supplier.hasAttribute(Attributes.SNEAKING_SPEED),
				"플레이어에게 없는 속성이면 수정자를 붙일 자리가 없다");
		assertTrue(supplier.hasAttribute(Attributes.MOVEMENT_SPEED));
	}

	// -------------------------------------------------- 달리기 배수

	@Test
	void 달리기는_걷기의_1_3배다() throws ReflectiveOperationException {
		AttributeModifier sprinting = sprintingModifier();

		assertEquals(SPRINTING, sprinting.id(),
				"이 이름으로 수정자를 찾아 달리는 중인지 판정한다");
		assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, sprinting.operation(),
				"곱셈이라야 (1 + 값) 배로 셈이 맞는다");
		assertEquals(SneakSpeedEffect.SPRINT_FACTOR, 1.0 + sprinting.amount(), 1.0e-6,
				"이 값이 바뀌면 「달리기보다 몇 배」의 기준이 통째로 바뀐다");
	}

	@Test
	void 달리기_수정자는_이동_속도에_붙는다() {
		AttributeSupplier supplier = Player.createAttributes().build();
		AttributeInstance speed = supplier.createInstance(ignored -> {
		}, Attributes.MOVEMENT_SPEED);
		assertNotNull(speed);

		double walk = speed.getValue();
		speed.addTransientModifier(new AttributeModifier(
				SPRINTING, SneakSpeedEffect.SPRINT_FACTOR - 1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

		assertEquals(walk * SneakSpeedEffect.SPRINT_FACTOR, speed.getValue(), 1.0e-9);
		assertNotNull(speed.getModifier(SPRINTING),
				"붙어 있는지를 이 조회로 본다. 사라지면 달리는 중을 못 알아본다");
	}

	// -------------------------------------------------- 이동 속도 상한

	@Test
	void 이동_속도는_웅크림_보정에_걸릴_만큼_낮게_잘리지_않는다() {
		RangedAttribute ranged =
				assertInstanceOf(RangedAttribute.class, Attributes.MOVEMENT_SPEED.value());

		assertEquals(0.0, ranged.getMinValue(), 1.0e-9);
		assertTrue(ranged.getMaxValue() >= 1.0,
				"기본 0.1 에 1.755 를 곱해도 잘리지 않을 만큼은 넉넉해야 한다");
	}

	// -------------------------------------------------- 웅크림 판정

	@Test
	void 웅크림_판정에_쓰는_두_메서드가_그대로_있다() {
		assertDoesNotThrow(() -> Entity.class.getMethod("isCrouching"),
				"클라이언트의 isMovingSlowly() 가 보는 첫 번째 조건이다");
		assertDoesNotThrow(() -> Entity.class.getMethod("isVisuallyCrawling"),
				"두 번째 조건. 빠뜨리면 기어갈 때 감쇠만 걸리고 보정은 안 걸린다");
		assertDoesNotThrow(() -> Entity.class.getMethod("isShiftKeyDown"),
				"서버가 입력 꾸러미로 채우는 값. 자세는 여기서 나온다");
	}

	@Test
	void 자세를_서버에서도_갱신한다() {
		assertDoesNotThrow(() -> Player.class.getDeclaredMethod("updatePlayerPose"),
				"이 자리가 있어서 서버에서도 isCrouching() 을 믿을 수 있다");
	}

	// -------------------------------------------------- 도우미

	/** {@code LivingEntity} 가 달리기에 쓰는 수정자. private static 이라 반사로 꺼낸다. */
	private static AttributeModifier sprintingModifier() throws ReflectiveOperationException {
		Field field = LivingEntity.class.getDeclaredField("SPEED_MODIFIER_SPRINTING");
		field.setAccessible(true);
		Object value = field.get(null);
		return assertInstanceOf(AttributeModifier.class, value,
				"이 이름의 상수가 사라졌으면 달리기 배수를 어디서 얻는지 다시 확인해야 한다");
	}
}
