package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MobTickRate} 의 <b>틱 배분 셈</b>과, 그 셈이 기대는 바닐라 자리를 못박는다.
 *
 * <p>여기서 붙드는 것은 두 종류다.
 *
 * <ol>
 *   <li><b>「20%」가 정말 20%인가.</b> {@code mob_action_speed} 는 증강 카드에 숫자가 적히는
 *       상시 효과라 평균이 아니라 <b>정확히</b> 그 비율이어야 한다. 이 셈이 한 칸 어긋나면
 *       몹의 공격 간격·크리퍼 부풀기·활 쏘기가 전부 함께 어긋난다.</li>
 *   <li><b>위상을 만드는 자리.</b> {@code MinecraftServer.getTickCount()} 와
 *       {@code Entity.getId()} 는 mixin 대상이 아니라 그냥 호출이지만, 사라지면 컴파일이
 *       깨지는 대신 <b>의미가 조용히 달라질</b> 수 있는 값들이다. 서명을 함께 붙들어 둔다.</li>
 * </ol>
 *
 * <p>틱을 실제로 주고 거르는 자리({@code ServerLevel.tickNonPassenger})는
 * {@code SanctuaryTargetTest} 가 못박는다.
 */
class MobTickRateTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 틱 배분 셈

	/**
	 * <b>이 시험이 「20% 빨라진다」의 뜻이다.</b>
	 *
	 * <p>0.2 는 다섯 틱에 정확히 한 번이어야 한다. 999 틱이든 1000 틱이든 남거나 모자라지 않는다.
	 */
	@Test
	void 비율이_0점2_면_다섯_틱에_정확히_한_번이다() {
		assertEquals(200, 센다(0.2, 1000));
		assertEquals(1, 센다(0.2, 5));
		assertEquals(2, 센다(0.2, 10));
		// 아직 몫이 차지 않은 구간에서는 한 번도 주지 않는다.
		assertEquals(0, 센다(0.2, 4));
	}

	/** 0.4 면 다섯 틱에 두 번. 어떤 값이든 오차가 쌓이지 않는다. */
	@Test
	void 어떤_비율이든_쌓인_몫만큼만_준다() {
		assertEquals(400, 센다(0.4, 1000));
		assertEquals(150, 센다(0.15, 1000));
		assertEquals(333, 센다(1.0 / 3.0, 1000));
		assertEquals(50, 센다(0.05, 1000));
	}

	/**
	 * 같은 몹이 두 틱 연속으로 더 돌지 않는다.
	 *
	 * <p>난수로 세지 않는 가장 큰 이유가 이것이다. 연달아 걸리면 그 몹만 잠깐 두 배로 빨라지고,
	 * 터지는 시각을 눈으로 재는 크리퍼에게는 그 들쭉날쭉함이 그대로 억울함이 된다.
	 */
	@Test
	void 연달아_두_번은_없다() {
		boolean previous = false;
		for (long phase = 1; phase <= 1000; phase++) {
			boolean current = MobTickRate.crossesThisTick(phase, 0.2);
			assertFalse(previous && current, phase + " 번째 틱에서 연달아 두 번 돌았다");
			previous = current;
		}
	}

	/** 0 이면 한 번도, 1.0 이상이면 매 틱이다. 1.0 이 곧 ×2.0 이라 그 위는 없다. */
	@Test
	void 양_끝은_한_번도_아니면_매_틱이다() {
		assertEquals(0, 센다(0.0, 1000));
		assertEquals(0, 센다(-0.5, 1000));
		assertEquals(1000, 센다(1.0, 1000));
		assertEquals(1000, 센다(2.5, 1000), "1.0 을 넘는 값이 새어 들어와도 매 틱이 상한이다");
		assertFalse(MobTickRate.crossesThisTick(7, Double.NaN));
	}

	/**
	 * 몹마다 위상이 다르면 더 도는 틱도 다르다.
	 *
	 * <p>위상에 {@code entity.getId()} 를 더하는 이유다. 무리 전체가 같은 틱에 한꺼번에 두 번
	 * 돌면 화면에서도 걸음이 한꺼번에 커지고, 서버 부담도 5틱마다 한 번에 몰린다.
	 */
	@Test
	void 위상이_다르면_더_도는_틱도_흩어진다() {
		List<Long> 첫째 = 걸린틱(0, 0.2);
		List<Long> 둘째 = 걸린틱(1, 0.2);
		List<Long> 셋째 = 걸린틱(2, 0.2);

		assertNotEquals(첫째, 둘째, "번호가 다른 몹이 같은 틱에 함께 돈다");
		assertNotEquals(둘째, 셋째);
		// 흩어지기만 할 뿐, 받는 횟수는 모두 같아야 한다.
		assertEquals(첫째.size(), 둘째.size());
		assertEquals(둘째.size(), 셋째.size());
	}

	// ------------------------------------------------------------------ 위상을 만드는 자리

	/**
	 * 서버 틱 수.
	 *
	 * <p>{@code int} 라 오래 돌면 한 바퀴 돈다. {@link MobTickRate} 가 부호 없는 값으로 늘려
	 * 받는 근거이므로 반환형이 바뀌면 그 처리도 함께 봐야 한다.
	 */
	@Test
	void 서버_틱_수를_물을_수_있다() {
		Method target = assertDoesNotThrow(() -> MinecraftServer.class.getDeclaredMethod("getTickCount"),
				"MinecraftServer.getTickCount() 가 사라졌거나 이름이 바뀌었다");

		assertEquals(int.class, target.getReturnType(),
				"long 이 되면 Integer.toUnsignedLong 으로 감싸던 처리를 걷어내야 한다");
		assertTrue(Modifier.isPublic(target.getModifiers()));
		assertFalse(Modifier.isStatic(target.getModifiers()));
	}

	/** 월드에서 서버로 건너가는 길. 이것이 없으면 위상을 만들 수 없다. */
	@Test
	void 월드에서_서버로_건너갈_수_있다() {
		Method target = assertDoesNotThrow(() -> ServerLevel.class.getDeclaredMethod("getServer"),
				"ServerLevel.getServer() 가 사라졌거나 이름이 바뀌었다");

		assertEquals(MinecraftServer.class, target.getReturnType());
		assertTrue(Modifier.isPublic(target.getModifiers()));
	}

	/**
	 * 엔티티 번호. 몹마다 위상을 어긋뜨리는 데 쓴다.
	 *
	 * <p>같은 값이 아니라 <b>서로 다른 값</b>이기만 하면 되므로 의미가 바뀌어도 큰일은 아니지만,
	 * 사라지면 흩뜨리기가 통째로 없어진다.
	 */
	@Test
	void 엔티티_번호를_물을_수_있다() {
		Method target = assertDoesNotThrow(() -> Entity.class.getDeclaredMethod("getId"),
				"Entity.getId() 가 사라졌거나 이름이 바뀌었다");

		assertEquals(int.class, target.getReturnType());
		assertTrue(Modifier.isPublic(target.getModifiers()));
	}

	// ------------------------------------------------------------------ 도우미

	/** 1 부터 {@code ticks} 까지 도는 동안 몇 번이나 더 돌았는지. */
	private static int 센다(double rate, int ticks) {
		int count = 0;
		for (long phase = 1; phase <= ticks; phase++) {
			if (MobTickRate.crossesThisTick(phase, rate)) {
				count++;
			}
		}
		return count;
	}

	/** 번호가 {@code id} 인 몹이 처음 50 틱 동안 더 도는 틱들. */
	private static List<Long> 걸린틱(int id, double rate) {
		List<Long> found = new ArrayList<>();
		for (long tick = 1; tick <= 50; tick++) {
			if (MobTickRate.crossesThisTick(tick + id, rate)) {
				found.add(tick);
			}
		}
		return found;
	}
}
