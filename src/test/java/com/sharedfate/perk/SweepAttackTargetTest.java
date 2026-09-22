package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code PlayerSweepFriendlyFireMixin} 이 무는 바닐라 자리를 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Redirect} 의 대상이 틀려도 <b>빌드가 그냥
 * 통과</b>한다. 더구나 휩쓸기는 검을 들고 서서 달리지 않은 채 몹을 쳐야 도는 자리라 사람이
 * 놀다가 알아차리기까지 한참 걸린다. 그래서 여기서 세 가지를 따로 붙든다.
 *
 * <ol>
 *   <li>{@code Player.doSweepAttack} 의 이름과 서술자 — {@code @Redirect} 의 {@code method}
 *       문자열에 적은 것과 같아야 한다.</li>
 *   <li>{@code LivingEntity.hurtServer} 의 이름과 서술자 — {@code @At} 의 {@code target}
 *       문자열에 적은 것과 같아야 한다.</li>
 *   <li>그 호출이 <b>휩쓸기 고리 안에 실제로 딱 한 번</b> 있는가 — 바이트코드를 읽어서
 *       본다. 메서드가 멀쩡히 있어도 안쪽이 다른 호출로 바뀌면 주입은 죽는다.</li>
 * </ol>
 *
 * <p>덧붙여 {@code doSweepAttack} 이 하위 클래스에 재정의되지 않았음도 본다. 재정의되는
 * 메서드에 건 믹스인은 소리 없이 죽는다.
 */
class SweepAttackTargetTest {

	private static final String HURT_SERVER_OWNER = "net/minecraft/world/entity/LivingEntity";
	private static final String HURT_SERVER_DESC =
			"(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z";

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/** 휩쓸기 고리가 담긴 메서드. {@code @Redirect} 의 {@code method} 문자열과 같아야 한다. */
	@Test
	void 휩쓸기_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> Player.class.getDeclaredMethod("doSweepAttack",
						Entity.class, float.class, DamageSource.class, float.class),
				"Player.doSweepAttack(Entity,float,DamageSource,float) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(void.class, target.getReturnType());
		assertFalse(Modifier.isStatic(target.getModifiers()),
				"정적이 되면 주입 형태가 달라진다");
	}

	/** {@code @At} 의 {@code target} 문자열과 같아야 한다. 돌려주는 값이 분기 조건이다. */
	@Test
	void 피해_호출이_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("hurtServer",
						ServerLevel.class, DamageSource.class, float.class),
				"LivingEntity.hurtServer(ServerLevel,DamageSource,float) 가 사라졌거나 서명이 바뀌었다");

		assertEquals(boolean.class, target.getReturnType(),
				"거짓을 돌려주어 「안 맞았다」로 만드는 방식이라 boolean 이 아니면 안 된다");
	}

	/**
	 * 하위 클래스가 다시 정의하면 {@code Player} 에 건 믹스인은 조용히 죽는다.
	 *
	 * <p>{@code doSweepAttack} 은 private 이라 원래 재정의될 수 없지만, 접근이 넓어지고
	 * {@code ServerPlayer} 가 가로채는 날이 오면 여기서 먼저 걸린다.
	 */
	@Test
	void 하위_클래스가_휩쓸기를_가로채지_않는다() throws NoSuchMethodException {
		Method declared = Player.class.getDeclaredMethod("doSweepAttack",
				Entity.class, float.class, DamageSource.class, float.class);
		assertTrue(Modifier.isPrivate(declared.getModifiers()),
				"private 이 아니게 되면 하위 클래스가 가로챌 수 있다");

		assertThrows(NoSuchMethodException.class,
				() -> ServerPlayer.class.getDeclaredMethod("doSweepAttack",
						Entity.class, float.class, DamageSource.class, float.class),
				"ServerPlayer 가 휩쓸기를 재정의하면 Player 에 건 믹스인이 조용히 죽는다");
	}

	/** 휩쓸기 고리 안에 {@code hurtServer} 호출이 딱 하나 있다. */
	@Test
	void 휩쓸기_고리가_피해를_그_호출로_넣는다() throws IOException {
		List<String> calls = callsIn(Player.class, "doSweepAttack");

		long hits = calls.stream()
				.filter(call -> call.equals(HURT_SERVER_OWNER + ".hurtServer" + HURT_SERVER_DESC))
				.count();

		assertEquals(1, hits,
				"휩쓸기 고리 안의 LivingEntity.hurtServer 호출이 하나가 아니다. 실제 호출 목록: "
						+ calls);
	}

	/** {@code Player.attack} 이 아직 휩쓸기를 부른다 — 이 길 자체가 사라지지 않았는가. */
	@Test
	void 공격이_아직_휩쓸기를_부른다() throws IOException {
		List<String> calls = callsIn(Player.class, "attack");

		assertTrue(calls.stream().anyMatch(call -> call.contains(".doSweepAttack")),
				"Player.attack 이 더 이상 doSweepAttack 을 부르지 않는다. 실제 호출 목록: " + calls);
	}

	// ------------------------------------------------------------------ 도우미

	/**
	 * 메서드 하나가 부르는 것들을 {@code 소유자.이름서술자} 꼴로 모은다.
	 *
	 * <p>asm-tree 를 쓰지 않고 방문자만 쓴다 — 코어 ASM 은 믹스인이 이미 끌고 오므로 의존성이
	 * 늘지 않는다.
	 */
	private static List<String> callsIn(Class<?> owner, String methodName) throws IOException {
		List<String> calls = new ArrayList<>();
		boolean[] seen = {false};

		ClassReader reader = new ClassReader(classBytes(owner));
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
				if (!name.equals(methodName)) {
					return null;
				}
				seen[0] = true;
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String callOwner, String callName,
							String callDescriptor, boolean isInterface) {
						calls.add(callOwner + "." + callName + callDescriptor);
					}
				};
			}
		}, ClassReader.SKIP_FRAMES);

		assertTrue(seen[0], owner.getSimpleName() + "." + methodName + " 을 읽지 못했다");
		return calls;
	}

	private static byte[] classBytes(Class<?> type) throws IOException {
		String path = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream in = type.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("클래스 파일을 찾지 못했습니다: " + path);
			}
			return in.readAllBytes();
		}
	}
}
