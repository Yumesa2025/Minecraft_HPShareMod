package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VictoryTeamResolver#candidatesOf} 가 기대는 <b>바닐라 쪽 사실</b>을 못박는다.
 *
 * <p>{@code VictoryTeamResolverTest} 는 「뽑아 온 후보로 무엇을 고르는가」만 본다. 뽑아 오는
 * 쪽은 살아 있는 월드가 있어야 불러 볼 수 있어서 시험이 통째로 비어 있었고, 그래서 <b>바닐라
 * 계약이 틀어져도 시험 전부가 통과</b>했다. 여기가 그 구멍이다.
 *
 * <p>이것이 실제로 문 자리다 — 0.28.0-dev 에서 침대로 잡은 드래곤의 승리 기록이 통째로
 * 사라졌다. 「처치자를 못 찾았다」는 그때도 조용했고, 로그에 팀 이름이 「모험가」로 찍힌
 * 것만이 단서였다. 후보를 뽑는 쪽이 고장 나면 <b>증상이 사고가 난 다음에야 보인다.</b>
 *
 * <h2>컴파일이 잡아 주는 것은 여기 적지 않는다</h2>
 * <p>{@code candidatesOf} 가 직접 부르는 메서드의 <b>서명</b>이 바뀌면 빌드가 깨진다. 그러니
 * 서명만 베껴 적는 시험은 값이 없다. 여기서 보는 것은 컴파일이 못 보는 셋이다.
 * <ol>
 *   <li><b>동작</b> — {@code null} 을 넣었을 때 무엇이 나오는가. 우리는 {@code null} 일 수
 *       있는 값을 그대로 넘긴다</li>
 *   <li><b>덮어쓰기</b> — 드래곤이 상속받은 메서드를 자기 것으로 갈아 끼웠는가</li>
 *   <li><b>부르기는 하는가</b> — 네 단계 중 하나를 군더더기로 보고 걷어내는 것</li>
 * </ol>
 */
class VictoryCandidatesTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 2단계: 폭발의 주인

	/**
	 * 2단계가 그대로 쓰는 바닐라 판정.
	 *
	 * <p>{@code static} 이 풀리거나 인스턴스 메서드로 바뀌면 {@code Explosion} 구현체가 있어야
	 * 부를 수 있게 된다. 우리에겐 그런 것이 없다 — 폭발은 이미 끝났고 손에 있는 것은
	 * {@code DamageSource} 뿐이다.
	 */
	@Test
	void 폭발의_주인을_푸는_바닐라_함수가_정적_그대로다() {
		Method resolve = assertDoesNotThrow(
				() -> Explosion.class.getDeclaredMethod("getIndirectSourceEntity", Entity.class),
				"Explosion.getIndirectSourceEntity(Entity) 가 사라졌거나 서명이 바뀌었다");

		assertTrue(Modifier.isStatic(resolve.getModifiers()),
				"정적이 아니면 폭발 객체 없이는 부를 수 없다. 우리에겐 DamageSource 뿐이다");
		assertTrue(Modifier.isPublic(resolve.getModifiers()));
		assertEquals(LivingEntity.class, resolve.getReturnType());
	}

	/**
	 * <b>{@code null} 을 넣어도 터지지 않는다.</b> 이 시험 하나가 이 파일의 요점이다.
	 *
	 * <p>{@code candidatesOf} 는 {@code source.getDirectEntity()} 를 그대로 넘기는데 그 값은
	 * <b>흔히 {@code null}</b> 이다 — 침대 폭발이 바로 그렇다({@code level.explode(null, …, null)}).
	 * 바닐라가 여기서 {@code null} 을 거절하게 되는 날, 우리 코드는 <b>드래곤이 죽는 순간</b>
	 * 터진다. 회차가 끝나는 바로 그 틱이라 최악의 자리다.
	 *
	 * <p>지금은 타입 스위치가 {@code -1} 로 떨어져 {@code null} 이 나온다. 컴파일도 서명 검사도
	 * 이것을 못 본다 — 실제로 불러 봐야 안다.
	 */
	@Test
	void 폭발의_주인_풀기는_null_을_받아도_터지지_않는다() {
		assertNull(Explosion.getIndirectSourceEntity(null),
				"직접 원인이 없는 폭발(침대·명령 TNT)에서 이 값이 그대로 넘어간다");
	}

	// ------------------------------------------------------------------ 3단계: 마지막 가해자

	/** 3단계가 읽는 자리. 사람이 아닌 것이 나오면 {@code playerId} 가 통째로 걸러 버린다. */
	@Test
	void 마지막_가해자는_사람_그대로_나온다() {
		Method last = assertDoesNotThrow(
				() -> LivingEntity.class.getDeclaredMethod("getLastHurtByPlayer"),
				"LivingEntity.getLastHurtByPlayer() 가 사라졌거나 서명이 바뀌었다");

		assertEquals(Player.class, last.getReturnType(),
				"사람이 아닌 것이 나오면 3단계가 조용히 아무도 못 찾는다");
		assertFalse(Modifier.isStatic(last.getModifiers()));
		assertTrue(Modifier.isPublic(last.getModifiers()));
	}

	/**
	 * <b>드래곤이 이 메서드를 재정의하면 안 된다.</b>
	 *
	 * <p>이 코드베이스가 되풀이해 밟은 함정이다 — 하위 클래스가 재정의한 메서드에 기대면 빌드도
	 * 로그도 조용한 채 다른 값이 나온다({@code SlotExpandedLockMixin} 이 그랬다). 드래곤이
	 * 자기 판정을 갖게 되면 3단계가 가리키는 사람이 달라지는데, 컴파일은 아무 말도 안 한다.
	 */
	@Test
	void 엔더_드래곤은_마지막_가해자를_재정의하지_않는다() {
		assertThrows(NoSuchMethodException.class,
				() -> EnderDragon.class.getDeclaredMethod("getLastHurtByPlayer"),
				"드래곤이 자기 판정을 들고 있으면 3단계가 보는 값이 달라진다");

		Method resolved = assertDoesNotThrow(
				() -> EnderDragon.class.getMethod("getLastHurtByPlayer"));
		assertEquals(LivingEntity.class, resolved.getDeclaringClass(),
				"드래곤이 읽는 것은 LivingEntity 의 것이어야 한다");
	}

	// ------------------------------------------------------------------ 1·2단계가 서로 다른가

	/**
	 * 「일으킨 주체」와 「직접 원인」은 <b>서로 다른 물음</b>이어야 한다.
	 *
	 * <p>1단계는 {@code getEntity}(화살을 쏜 사람), 2단계는 {@code getDirectEntity}(화살 그 자체)
	 * 를 본다. 둘이 같은 것을 돌려주게 되면 2단계는 1단계의 되풀이가 되고, <b>폭발로 잡은
	 * 드래곤을 아무도 못 찾게 된다</b> — 그때 1단계는 이미 비어 있기 때문이다.
	 */
	@Test
	void 피해_원인은_일으킨_자와_직접_원인을_따로_들고_있다() {
		for (String name : new String[] {"getEntity", "getDirectEntity"}) {
			Method method = assertDoesNotThrow(
					() -> DamageSource.class.getDeclaredMethod(name),
					"DamageSource." + name + "() 가 사라졌다");
			assertEquals(Entity.class, method.getReturnType(), name);
			assertFalse(Modifier.isStatic(method.getModifiers()), name);
		}
	}

	// ------------------------------------------------------------------ 우리가 실제로 부르는가

	/**
	 * 네 단계를 하나라도 <b>걷어내는 것</b>을 막는다.
	 *
	 * <p>단계마다 「이건 없어도 되지 않나」로 보인다. 실제로 2·4단계는 평범하게 때려잡는 판에서는
	 * 한 번도 안 쓰이므로, 코드만 읽으면 군더더기처럼 보인다. 그런데 그 둘이 침대·명령 TNT·
	 * 「폭발 교환」으로 잡았을 때의 <b>유일한 구제</b>다.
	 *
	 * <p>클래스 파일의 상수 풀을 그대로 뒤진다. 시험이 살아 있는 월드를 못 만들어 실제 호출을
	 * 확인할 수 없으니, <b>부르는 이름이 거기 적혀 있는지</b>라도 본다.
	 */
	@Test
	void 후보를_뽑는_자리가_세_단계를_모두_거친다() throws IOException {
		String bytes = classBytes(VictoryTeamResolver.class);

		assertTrue(bytes.contains("getEntity"),
				"1단계(일으킨 주체)가 사라졌다");
		assertTrue(bytes.contains("getIndirectSourceEntity"),
				"2단계(폭발의 주인)가 사라졌다. 침대로 잡은 드래곤을 아무도 못 찾게 된다");
		assertTrue(bytes.contains("getDirectEntity"),
				"2단계가 보는 직접 원인이 사라졌다");
		assertTrue(bytes.contains("getLastHurtByPlayer"),
				"3단계(마지막 가해자)가 사라졌다");
		assertTrue(bytes.contains("soleStartedTeam"),
				"4단계(시작한 팀이 하나뿐이면 그 팀)가 사라졌다. 이것이 마지막 구제다");
	}

	private static String classBytes(Class<?> type) throws IOException {
		String path = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream in = type.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("클래스 파일을 찾지 못했습니다: " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		}
	}
}
