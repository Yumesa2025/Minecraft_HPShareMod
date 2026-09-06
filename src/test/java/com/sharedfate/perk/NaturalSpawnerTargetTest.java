package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code NaturalSpawnerRateMixin} 이 무는 바닐라 자리를 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @Inject} 의 대상 서술자가 틀려도 <b>빌드가 그냥
 * 통과</b>하고, 서버가 뜬 뒤 스폰이 처음 도는 순간에야 터진다. 스폰은 월드에 들어가야 도는
 * 자리라 그 사고를 늦게 알아차리게 된다. 그래서 대상 서술자만이라도 여기서 붙들어 둔다.
 */
class NaturalSpawnerTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- 믹스인이 파고드는 메서드

	/** {@code @Inject} 의 method 문자열에 적은 것과 글자 하나까지 같아야 한다. */
	@Test
	void 스폰_대상_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> NaturalSpawner.class.getDeclaredMethod("spawnCategoryForChunk",
						MobCategory.class, ServerLevel.class, LevelChunk.class,
						NaturalSpawner.SpawnPredicate.class,
						NaturalSpawner.AfterSpawnCallback.class),
				"이 서술자가 바뀌면 스폰율 증강이 조용히 걸리지 않거나 스폰이 도는 순간 터진다");

		assertTrue(Modifier.isStatic(target.getModifiers()), "믹스인 처리기도 static 이어야 한다");
		assertTrue(Modifier.isPublic(target.getModifiers()),
				"추가 스폰을 돌 때 믹스인이 이 메서드를 직접 다시 부른다");
		assertEquals(void.class, target.getReturnType(),
				"void 가 아니게 되면 CallbackInfo 로는 취소할 수 없다");
	}

	/**
	 * 이름이 겹치면 믹스인이 이름만으로 고를 때 엉뚱한 쪽에 붙는다.
	 *
	 * <p>{@code spawnCategoryForPosition} 은 실제로 둘로 겹쳐 있다. 우리가 무는 쪽은 겹치지
	 * 않는다는 것을 확인해 둔다.
	 */
	@Test
	void 스폰_대상_메서드는_이름이_겹치지_않는다() {
		assertEquals(1, countDeclared("spawnCategoryForChunk"),
				"겹침이 생겼으면 서술자를 다시 확인해야 한다");
		assertEquals(2, countDeclared("spawnCategoryForPosition"),
				"이쪽은 원래 둘이다. 여기에 붙지 않는지 확인하는 기준선");
	}

	/** 청크마다 갈래별로 위 메서드를 부르는 한 단계 위. 이 길이 바뀌면 호출 횟수가 달라진다. */
	@Test
	void 청크_스폰_경로가_그대로다() {
		assertDoesNotThrow(() -> NaturalSpawner.class.getDeclaredMethod("spawnForChunk",
						ServerLevel.class, LevelChunk.class, NaturalSpawner.SpawnState.class,
						List.class),
				"ServerChunkCache 가 청크마다 부르는 자리다");
		assertDoesNotThrow(
				() -> NaturalSpawner.class.getDeclaredMethod("getFilteredSpawningCategories",
						NaturalSpawner.SpawnState.class, boolean.class, boolean.class),
				"전역 상한(mobcap)을 넘은 갈래를 거르는 자리. 우리 배율은 이 뒤에 걸린다");
	}

	/** 넘겨받아 그대로 다시 넘기는 두 인터페이스. 여기가 바뀌면 상한 계산을 물려줄 수 없다. */
	@Test
	void 스폰_판정과_뒷정리_인터페이스가_그대로다() {
		assertTrue(NaturalSpawner.SpawnPredicate.class.isInterface());
		assertTrue(NaturalSpawner.AfterSpawnCallback.class.isInterface());
		assertEquals(NaturalSpawner.class, NaturalSpawner.SpawnPredicate.class.getEnclosingClass(),
				"중첩 클래스가 아니게 되면 서술자의 $ 표기가 틀어진다");
		assertEquals(NaturalSpawner.class,
				NaturalSpawner.AfterSpawnCallback.class.getEnclosingClass());
	}

	// -------------------------------------------------- 믹스인이 읽는 값

	/** 적대 몹만 고르는 기준. 이것이 바뀌면 주민·소까지 배율을 먹는다. */
	@Test
	void 적대_몹_갈래가_그대로다() {
		assertFalse(MobCategory.MONSTER.isFriendly(), "MONSTER 가 적대 갈래여야 한다");
		assertTrue(MobCategory.CREATURE.isFriendly(), "주민·소는 손대지 않는다");
		assertDoesNotThrow(() -> MobCategory.class.getDeclaredMethod("getMaxInstancesPerChunk"),
				"몹 상한 계산의 뿌리. 우리가 늘리는 것은 시도 횟수뿐이고 이 천장은 그대로 둔다");
	}

	/** 믹스인 본문이 부르는 두 가지. 없어지면 컴파일부터 막히지만 서술자까지 못박아 둔다. */
	@Test
	void 믹스인이_부르는_바닐라_메서드가_그대로다() {
		assertDoesNotThrow(() -> Level.class.getDeclaredMethod("getRandom"),
				"소수점 배율을 확률로 바꿀 때 쓰는 주사위");
		assertDoesNotThrow(() -> ServerLevel.class.getDeclaredMethod("getServer"),
				"어느 서버의 증강인지 여기서 알아낸다");
	}

	// -------------------------------------------------- 도우미

	private static long countDeclared(String name) {
		return Arrays.stream(NaturalSpawner.class.getDeclaredMethods())
				.filter(method -> method.getName().equals(name))
				.count();
	}
}
