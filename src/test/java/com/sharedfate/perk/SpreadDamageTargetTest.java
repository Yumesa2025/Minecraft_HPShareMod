package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * {@code LivingEntityPerkDamageMixin} 이 무는 바닐라 자리를 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject}·{@code @ModifyVariable} 의 대상 서술자가
 * 틀려도 <b>빌드가 그냥 통과</b>하고, 그 자리를 처음 지나는 순간에야 터진다. 피해와 회복은
 * 서버가 뜨고 사람이 맞아 봐야 도는 자리라 사고를 늦게 알아차린다.
 *
 * <p>특히 {@code heal} 은 「완충」({@code spread_damage})의 <b>대가 전부</b>가 걸린 자리다.
 * 여기가 조용히 빗나가면 「나뉘어 들어오는 동안 회복되지 않는다」가 통째로 사라지는데,
 * 빌드도 서버도 멀쩡해서 아무도 모른 채 이득만 있는 프리즘이 된다.
 */
class SpreadDamageTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * 피해 진입점. {@code @Inject}·{@code @ModifyVariable} 의 {@code method} 문자열에 적은 것과
	 * 글자 하나까지 같아야 한다.
	 *
	 * <p>{@code @ModifyVariable} 이 {@code index = 3} 으로 세 번째 인자(피해량)를 집는다. 앞에
	 * 인자가 하나라도 끼어들면 엉뚱한 값을 갈아 끼우므로 <b>인자 차례</b>까지 함께 붙든다.
	 */
	@Test
	void 피해_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("hurtServer",
						ServerLevel.class, DamageSource.class, float.class),
				"hurtServer(ServerLevel, DamageSource, float) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(boolean.class, target.getReturnType());
		assertFalse(Modifier.isStatic(target.getModifiers()), "정적이면 인자 번호가 하나씩 밀린다");
		assertEquals(ServerLevel.class, target.getParameterTypes()[0]);
		assertEquals(DamageSource.class, target.getParameterTypes()[1]);
		assertEquals(float.class, target.getParameterTypes()[2],
				"index = 3 이 집는 것이 피해량이어야 한다");
	}

	/**
	 * 회복 진입점.
	 *
	 * <p>26.2 에서 자연 회복·재생 상태이상·금사과가 모두 이 한 곳을 지난다. 그래서 여기만 막으면
	 * 「완충」의 대가가 성립한다. {@code void} 라 취소해도 호출자에게 아무 신호가 가지 않는다는
	 * 점도 함께 붙든다 — 반환형이 생기면 취소가 다른 뜻이 된다.
	 */
	@Test
	void 회복_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("heal", float.class),
				"heal(float) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(void.class, target.getReturnType(), "void 라야 취소가 조용히 먹힌다");
		assertTrue(Modifier.isPublic(target.getModifiers()));
		assertFalse(Modifier.isStatic(target.getModifiers()));
	}

	/**
	 * 무적 시간 칸.
	 *
	 * <p>{@code SpreadDamageManager} 가 미뤄 둔 몫을 넣기 직전에 이 값을 0 으로 두었다가 되돌린다.
	 * 되돌리지 않으면 나뉘어 들어오는 동안 몹에게 거의 맞지 않는 증강이 된다.
	 * {@code public int} 가 아니게 되면 컴파일이 깨지므로 여기서 미리 붙든다.
	 */
	@Test
	void 무적_시간_칸이_그대로_있다() {
		Field field = assertDoesNotThrow(
				() -> Entity.class.getDeclaredField("invulnerableTime"),
				"Entity.invulnerableTime 이 사라졌거나 이름이 바뀌었다");

		assertEquals(int.class, field.getType());
		assertFalse(Modifier.isFinal(field.getModifiers()));

		// 26.3 에서 칸이 private 이 되고 접근자가 생겼다. 우리는 그 접근자로 읽고 쓴다.
		assertDoesNotThrow(() -> Entity.class.getMethod("getInvulnerableTime"),
				"무적 시간을 읽을 길이 없어졌다");
		assertDoesNotThrow(() -> Entity.class.getMethod("setInvulnerableTime", int.class),
				"무적 시간을 되돌릴 길이 없어졌다");
	}
}
