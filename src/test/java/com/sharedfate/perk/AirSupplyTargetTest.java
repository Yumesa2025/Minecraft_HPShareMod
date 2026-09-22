package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code LivingEntityAirSupplyMixin} 이 무는 바닐라 자리를 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상이 틀려도 <b>빌드가 그냥
 * 통과</b>한다. 게다가 산소는 "줄지 않아야" 맞는 것이라, 믹스인이 안 붙어도 화면에는
 * 아무 오류가 없고 그냥 <b>평소처럼 숨이 막힐 뿐</b>이다. 사람이 물에 오래 들어가 보기
 * 전까지는 아무도 모른다. 그래서 자리 자체를 시험으로 붙들어 둔다.
 */
class AirSupplyTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * 산소를 깎는 진입점. {@code @Inject} 의 {@code method} 문자열에 적은 것과 같아야 한다.
	 *
	 * <p>이름이 같은 메서드가 하나뿐이라 서술자를 안 적어도 붙지만, <b>인자나 돌려주는 값이
	 * 바뀌면</b> {@code CallbackInfoReturnable<Integer>} 로 받는 주입 형태 자체가 달라지므로
	 * 그것까지 본다.
	 */
	@Test
	void 산소_감소_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("decreaseAirSupply", int.class),
				"decreaseAirSupply(int) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(int.class, target.getReturnType(),
				"int 를 돌려주지 않으면 setReturnValue 로 값을 되돌릴 수 없다");
		assertFalse(Modifier.isStatic(target.getModifiers()),
				"정적이 되면 주입 형태가 달라진다");
		assertEquals(1, target.getParameterCount());
		assertEquals(int.class, target.getParameterTypes()[0]);
	}

	/**
	 * <b>이 시험이 이 파일의 핵심이다.</b>
	 *
	 * <p>믹스인은 {@code LivingEntity} 에 붙는다. {@code Player} 나 {@code ServerPlayer} 가
	 * {@code decreaseAirSupply} 를 재정의하면 플레이어의 호출은 재정의된 쪽으로 가고 이
	 * 믹스인은 <b>한 줄도 실행되지 않는다</b>. 그런데도 빌드는 통과하고 로그도 조용하다.
	 * 26.3 기준으로는 둘 다 재정의하지 않으며, 재정의가 생기는 순간 여기서 걸린다.
	 */
	@Test
	void 플레이어가_산소_감소를_재정의하지_않는다() {
		assertThrows(NoSuchMethodException.class,
				() -> Player.class.getDeclaredMethod("decreaseAirSupply", int.class),
				"Player 가 재정의하기 시작했다 — 믹스인 대상을 Player 로 옮겨야 한다");

		assertThrows(NoSuchMethodException.class,
				() -> ServerPlayer.class.getDeclaredMethod("decreaseAirSupply", int.class),
				"ServerPlayer 가 재정의하기 시작했다 — 믹스인 대상을 ServerPlayer 로 옮겨야 한다");
	}

	/**
	 * 물 밖에서 산소가 차오르는 쪽은 건드리지 않는다는 것을 함께 적어 둔다.
	 *
	 * <p>{@code increaseAirSupply} 가 그대로 있어야 "닳지는 않지만 차오르기는 한다"가
	 * 성립한다. 이름이 바뀌면 회복 갈래가 어디로 갔는지 다시 확인해야 한다.
	 */
	@Test
	void 산소_회복_메서드는_그대로다() {
		Method increase = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("increaseAirSupply", int.class),
				"increaseAirSupply(int) 가 사라졌다 — 산소 회복 흐름이 바뀌었다");

		assertEquals(int.class, increase.getReturnType());
	}
}
