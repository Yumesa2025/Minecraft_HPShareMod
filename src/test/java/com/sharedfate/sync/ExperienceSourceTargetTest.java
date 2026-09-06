package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 경험치 mixin 세 개가 <b>바닐라 쪽 사실</b>에 기대고 있다. 그 사실이 바뀌면 여기서 먼저 터진다.
 *
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드가 그냥
 * 통과</b>하고, 서버를 띄우는 순간(또는 그 코드가 처음 돌 때) 터진다. 그래서 대상 서술자와
 * 그것이 기대는 상속 관계를 여기서 붙들어 둔다.
 *
 * <ul>
 *   <li>{@code ExperienceOrbAwardMixin} — 상시 배율(설정)</li>
 *   <li>{@code BlockExperienceSourceMixin} — 광물 배율</li>
 *   <li>{@code MobExperienceSourceMixin} — 몹 배율</li>
 * </ul>
 */
class ExperienceSourceTargetTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- 상시 배율이 무는 자리

	/** {@code ExperienceOrbAwardMixin} 이 파고드는 자리. 모든 경험치가 여기로 모인다. */
	@Test
	void 경험치_오브를_만드는_두_정적_메서드가_그대로_있다() {
		Method awardWithDirection = declared(ExperienceOrb.class, "awardWithDirection",
				ServerLevel.class, Vec3.class, Vec3.class, int.class);
		assertEquals(void.class, awardWithDirection.getReturnType());
		assertTrue(Modifier.isStatic(awardWithDirection.getModifiers()));
		assertEquals(1, intParameters(awardWithDirection),
				"int 인자가 하나뿐이라야 ordinal = 0 이 그 값을 가리킨다");

		// award 는 awardWithDirection 을 그대로 부르는 한 줄짜리다. 둘 다 남아 있어야
		// 「아래쪽 하나만 잡으면 두 경로가 모두 걸린다」는 전제가 유지된다.
		assertEquals(void.class,
				declared(ExperienceOrb.class, "award", ServerLevel.class, Vec3.class, int.class)
						.getReturnType());
	}

	// -------------------------------------------------- 광물 배율이 무는 자리

	/** {@code BlockExperienceSourceMixin} 이 문맥을 적고 지우는 자리. */
	@Test
	void 블록을_사람이_캐는_자리가_그대로_있다() {
		Method playerDestroy = declared(Block.class, "playerDestroy",
				Level.class, Player.class, BlockPos.class, BlockState.class,
				BlockEntity.class, ItemStack.class);

		assertEquals(void.class, playerDestroy.getReturnType());
		assertFalse(Modifier.isStatic(playerDestroy.getModifiers()),
				"정적이 되면 처리기의 인자 이어받기가 한 칸씩 밀린다");
	}

	/** {@code BlockExperienceSourceMixin} 이 값을 고치는 자리. */
	@Test
	void 블록이_경험치를_떨어뜨리는_자리가_그대로_있다() {
		Method popExperience =
				declared(Block.class, "popExperience", ServerLevel.class, BlockPos.class, int.class);

		assertEquals(void.class, popExperience.getReturnType());
		assertFalse(Modifier.isStatic(popExperience.getModifiers()));
		assertEquals(1, intParameters(popExperience),
				"int 인자가 하나뿐이라야 ordinal = 0 이 그 값을 가리킨다");

		// 광석의 경험치가 popExperience 까지 오는 길. 26.2 에는 BlockBehaviour.getExpDrop 이
		// 없고 이 메서드가 그 자리를 대신한다.
		assertEquals(void.class, declared(Block.class, "tryDropExperience",
				ServerLevel.class, BlockPos.class, ItemStack.class, IntProvider.class)
				.getReturnType());
	}

	/**
	 * 광석 블록들은 {@code playerDestroy} 를 <b>재정의하지 않는다.</b>
	 *
	 * <p>{@code Block} 한 곳에 파고들어 모든 광석을 덮을 수 있는 것은 이 사실 덕분이다. 어느
	 * 광석이 자기 {@code playerDestroy} 를 갖게 되면 그 광석만 조용히 배율에서 빠진다.
	 */
	@Test
	void 광석_블록은_playerDestroy_를_재정의하지_않는다() {
		for (Class<?> type : new Class<?>[] {DropExperienceBlock.class, RedStoneOreBlock.class}) {
			assertThrows(NoSuchMethodException.class,
					() -> type.getDeclaredMethod("playerDestroy",
							Level.class, Player.class, BlockPos.class, BlockState.class,
							BlockEntity.class, ItemStack.class),
					type.getSimpleName() + " 가 playerDestroy 를 갖게 되면 그 블록만 배율에서 빠진다");
		}

		// 대신 이 둘이 재정의하는 것은 spawnAfterBreak 이고, 그 안에서 tryDropExperience 를 부른다.
		assertEquals(void.class, declared(DropExperienceBlock.class, "spawnAfterBreak",
				BlockState.class, ServerLevel.class, BlockPos.class, ItemStack.class, boolean.class)
				.getReturnType());
	}

	/** 대표 광석들이 실제로 그 두 클래스다. 아니면 위 시험이 아무것도 지키지 못한다. */
	@Test
	void 대표_광석은_경험치를_떨어뜨리는_블록이다() {
		assertInstanceOf(DropExperienceBlock.class, Blocks.DIAMOND_ORE);
		assertInstanceOf(DropExperienceBlock.class, Blocks.DEEPSLATE_DIAMOND_ORE);
		assertInstanceOf(DropExperienceBlock.class, Blocks.NETHER_QUARTZ_ORE);
		assertInstanceOf(RedStoneOreBlock.class, Blocks.REDSTONE_ORE);
	}

	// -------------------------------------------------- 몹 배율이 무는 자리

	/**
	 * {@code MobExperienceSourceMixin} 이 파고드는 메서드와, 그 안에서 값을 가로채는 호출.
	 *
	 * <p>{@code dropExperience} 의 두 번째 인자가 곧 처치자다. 이것이 있어서 스레드 전역 문맥
	 * 없이도 「누구의 팀 배율인가」를 알 수 있다.
	 */
	@Test
	void 몹이_경험치를_떨어뜨리는_자리가_그대로_있다() {
		Method dropExperience =
				declared(LivingEntity.class, "dropExperience", ServerLevel.class, Entity.class);
		assertEquals(void.class, dropExperience.getReturnType());
		assertFalse(Modifier.isStatic(dropExperience.getModifiers()));

		Method reward =
				declared(LivingEntity.class, "getExperienceReward", ServerLevel.class, Entity.class);
		assertEquals(int.class, reward.getReturnType(),
				"@ModifyExpressionValue 처리기의 반환형이 이것과 같아야 한다");
		assertFalse(Modifier.isStatic(reward.getModifiers()));
	}

	// -------------------------------------------------- 도우미

	private static Method declared(Class<?> owner, String name, Class<?>... parameters) {
		try {
			return owner.getDeclaredMethod(name, parameters);
		} catch (NoSuchMethodException missing) {
			throw new AssertionError(
					owner.getSimpleName() + "." + name + " 의 서술자가 바뀌었다. mixin 대상을 고칠 것",
					missing);
		}
	}

	private static int intParameters(Method method) {
		int count = 0;
		for (Class<?> parameter : method.getParameterTypes()) {
			if (parameter == int.class) {
				count++;
			}
		}
		return count;
	}
}
