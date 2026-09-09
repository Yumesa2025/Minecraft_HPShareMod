package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ServerLevelSanctuaryTickMixin} 이 무는 바닐라 자리와, <b>그 자리를 고른 근거</b>를
 * 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상 서술자가 틀려도 <b>빌드가 그냥
 * 통과</b>하고 서버가 뜬 뒤 첫 틱에 터진다. 게다가 성역은 「팀이 뭉쳐 있고 근처에 몹이 있을
 * 때」만 도는 자리라, 대상이 아니라 <b>판정</b>이 어긋나면 아무도 모른 채 무동작이 된다.
 *
 * <p>여기서 붙드는 것은 두 종류다.
 *
 * <ol>
 *   <li>주입 대상 — {@code ServerLevel.tickNonPassenger(Entity)}</li>
 *   <li><b>그 위가 아니라 여기여야 하는 이유</b> — 크리퍼가 부풀기를 {@code super.tick()} 보다
 *       먼저 자기 {@code tick()} 안에서 끝낸다는 사실. 이것이 무너지면(예: 부풀기가 goal 이나
 *       {@code customServerAiStep} 으로 옮겨 가면) 더 싼 자리
 *       ({@code Mob.tick()})로 옮길 수 있다는 신호다.</li>
 * </ol>
 */
class SanctuaryTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 주입 대상

	/**
	 * 틱을 건너뛰는 자리. {@code @Inject} 의 {@code method} 문자열에 적은 것과 글자 하나까지
	 * 같아야 한다 — {@code "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V"}.
	 *
	 * <p>{@code void} 라야 {@code HEAD} 에서 취소해도 호출자에게 아무 신호가 가지 않는다.
	 * 반환형이 생기면 취소가 다른 뜻이 된다.
	 */
	@Test
	void 엔티티_틱_입구가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> ServerLevel.class.getDeclaredMethod("tickNonPassenger", Entity.class),
				"ServerLevel.tickNonPassenger(Entity) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(void.class, target.getReturnType(), "void 라야 취소가 조용히 먹힌다");
		assertFalse(Modifier.isStatic(target.getModifiers()), "정적이면 주입 형태가 달라진다");
		assertTrue(Modifier.isPublic(target.getModifiers()),
				"가속이 이 메서드를 한 번 더 부르므로 밖에서 부를 수 있어야 한다");
		assertEquals(1, target.getParameterCount());
		assertEquals(Entity.class, target.getParameterTypes()[0]);
	}

	/**
	 * 건너뛰는 대상 그 자체.
	 *
	 * <p>{@code tickNonPassenger} 는 이 메서드를 <b>가상 호출</b>한다. 그래서 취소하면
	 * {@code Creeper.tick()} 같은 재정의가 시작조차 하지 않는다.
	 */
	@Test
	void 엔티티_틱이_가상_호출_그대로_있다() {
		Method target = assertDoesNotThrow(() -> Entity.class.getDeclaredMethod("tick"),
				"Entity.tick() 이 사라졌다");

		assertEquals(void.class, target.getReturnType());
		assertFalse(Modifier.isStatic(target.getModifiers()));
		assertFalse(Modifier.isFinal(target.getModifiers()),
				"final 이 되면 몹이 tick() 을 재정의할 수 없어 이 mixin 의 전제가 통째로 바뀐다");
	}

	/**
	 * 틱 수 칸.
	 *
	 * <p>{@code tickNonPassenger} 가 {@code Entity.tick()} 을 부르기 <b>전에</b> 1 올리는 값이라,
	 * 건너뛴 틱에는 이것도 늘지 않는다. {@code tickCount % N} 으로 도는 몹의 주기 행동까지 같은
	 * 비율로 느려지는 근거다.
	 */
	@Test
	void 틱_수_칸이_그대로_있다() {
		Field field = assertDoesNotThrow(() -> Entity.class.getDeclaredField("tickCount"),
				"Entity.tickCount 가 사라졌거나 이름이 바뀌었다");

		assertEquals(int.class, field.getType());
		assertFalse(Modifier.isStatic(field.getModifiers()));
	}

	// ------------------------------------------------------------------ 자리를 고른 근거

	/**
	 * <b>이 시험이 이 mixin 의 자리를 정한다.</b>
	 *
	 * <p>크리퍼는 {@code tick()} 을 스스로 재정의해 부풀기를 먼저 끝내고 맨 마지막에
	 * {@code super.tick()} 을 부른다. 그래서 {@code Mob.tick()} 에서 취소해 봐야 그 틱의 부풀기는
	 * 이미 지나간 뒤다 — 「크리퍼가 부푸는 속도도 느려져야 한다」가 조용히 무동작이 된다.
	 *
	 * <p>{@code Creeper} 가 {@code tick()} 을 더 이상 스스로 갖지 않게 되면 부풀기가 다른 곳으로
	 * 옮겨 갔다는 뜻이고, 그때는 더 싼 자리({@code Mob.tick()})를 다시 검토할 수 있다.
	 */
	@Test
	void 크리퍼가_부풀기를_자기_틱에서_한다() {
		Method creeperTick = assertDoesNotThrow(() -> Creeper.class.getDeclaredMethod("tick"),
				"Creeper 가 tick() 을 스스로 갖고 있지 않다 — 부풀기가 다른 곳으로 옮겨 갔는지 확인할 것");
		Method mobTick = assertDoesNotThrow(() -> Mob.class.getDeclaredMethod("tick"),
				"Mob.tick() 이 사라졌다");

		assertNotEquals(mobTick.getDeclaringClass(), creeperTick.getDeclaringClass(),
				"크리퍼가 tick() 을 재정의하지 않는다면 Mob.tick() 을 잘라도 됐다는 뜻이다");

		Field swell = assertDoesNotThrow(() -> Creeper.class.getDeclaredField("swell"),
				"Creeper.swell 이 사라졌거나 이름이 바뀌었다");
		assertEquals(int.class, swell.getType());
		assertFalse(Modifier.isStatic(swell.getModifiers()));
	}

	/**
	 * 활 쏘기 간격.
	 *
	 * <p>{@code attackTime} 은 goal 갱신에서 도는 값이고, goal 갱신은 몹의 틱 안에 있다. 즉
	 * 틱을 건너뛰면 이 값도 같은 비율로 천천히 준다 — 따로 손댈 자리가 없다는 근거다.
	 */
	@Test
	void 활_쏘기_간격이_goal_갱신에_그대로_있다() {
		Field attackTime = assertDoesNotThrow(
				() -> RangedAttackGoal.class.getDeclaredField("attackTime"),
				"RangedAttackGoal.attackTime 이 사라졌거나 이름이 바뀌었다");
		assertEquals(int.class, attackTime.getType());
		assertFalse(Modifier.isStatic(attackTime.getModifiers()));

		Method goalTick = assertDoesNotThrow(() -> RangedAttackGoal.class.getDeclaredMethod("tick"),
				"RangedAttackGoal.tick() 이 사라졌다 — 활 쏘기 간격이 도는 자리가 바뀌었는지 확인할 것");
		assertEquals(void.class, goalTick.getReturnType());
	}

	/**
	 * 고르지 <b>않은</b> 자리들도 함께 붙들어 둔다.
	 *
	 * <p>{@code serverAiStep}·{@code customServerAiStep} 은 물리를 살린 채 AI 만 자를 수 있는
	 * 자리라 언제든 다시 검토할 후보다. 다만 지금은 크리퍼 부풀기를 덮지 못해 쓰지 않는다.
	 * 서명이 바뀌면 그 검토의 전제도 바뀌므로 여기서 함께 본다.
	 */
	@Test
	void 고르지_않은_후보들도_그대로_있다() {
		Method serverAiStep = assertDoesNotThrow(
				() -> Mob.class.getDeclaredMethod("serverAiStep"), "Mob.serverAiStep() 이 사라졌다");
		assertTrue(Modifier.isFinal(serverAiStep.getModifiers()),
				"final 이 아니게 되면 하위 몹이 가로챌 수 있어 여기를 자르는 뜻이 달라진다");

		Method customServerAiStep = assertDoesNotThrow(
				() -> Mob.class.getDeclaredMethod("customServerAiStep", ServerLevel.class),
				"Mob.customServerAiStep(ServerLevel) 이 사라졌거나 서명이 바뀌었다");
		assertEquals(void.class, customServerAiStep.getReturnType());

		assertDoesNotThrow(() -> Mob.class.getDeclaredMethod("aiStep"), "Mob.aiStep() 이 사라졌다");
	}
}
