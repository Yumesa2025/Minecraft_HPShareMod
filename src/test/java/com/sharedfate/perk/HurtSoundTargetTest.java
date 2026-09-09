package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.damagesource.DamageSource;
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
 * {@code LivingEntityHurtSoundMixin} 이 무는 바닐라 자리와, 「완충」이 되돌리는 피격 표시
 * 필드를 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상이 틀려도 <b>빌드가 그냥
 * 통과</b>하고 그 자리를 처음 지나는 순간에야 터진다. 피격 소리는 사람이 실제로 맞아 봐야 도는
 * 자리라 사고를 늦게 알아차린다.
 *
 * <p>필드 둘({@code hurtTime}·{@code hurtDuration})은 Mixin 이 아니라
 * {@code SpreadDamageManager.deliver} 가 직접 저장했다 되돌리는 값이다. 접근 제한이 좁아지면
 * <b>컴파일이 깨지므로</b> 그쪽은 이 시험 없이도 알 수 있지만, 이름이 바뀌어 다른 필드를 잡게
 * 되는 경우까지 함께 붙들어 둔다.
 */
class HurtSoundTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * 피격 소리 진입점. {@code @Inject} 의 {@code method} 문자열에 적은 것과 같아야 한다.
	 *
	 * <p>인자가 하나뿐이라 이름만 맞으면 서술자도 맞지만, <b>정적이 되면</b> 주입 형태가
	 * 달라지므로 그것까지 본다.
	 */
	@Test
	void 피격_소리_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("playHurtSound", DamageSource.class),
				"playHurtSound(DamageSource) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(void.class, target.getReturnType(),
				"돌려주는 값이 생기면 cancel 만으로는 못 막는다");
		assertFalse(Modifier.isStatic(target.getModifiers()));
		assertEquals(1, target.getParameterCount());
		assertEquals(DamageSource.class, target.getParameterTypes()[0]);
	}

	/** 「완충」이 몫마다 되돌리는 피격 표시 필드 둘. */
	@Test
	void 피격_표시_필드가_그대로_있다() {
		for (String name : new String[] {"hurtTime", "hurtDuration"}) {
			Field field = assertDoesNotThrow(() -> LivingEntity.class.getDeclaredField(name),
					name + " 가 사라졌거나 이름이 바뀌었다");
			assertEquals(int.class, field.getType(), name);
			assertTrue(Modifier.isPublic(field.getModifiers()),
					name + " 이 public 이 아니면 되돌릴 수 없다");
			assertFalse(Modifier.isStatic(field.getModifiers()), name);
		}
	}
}
