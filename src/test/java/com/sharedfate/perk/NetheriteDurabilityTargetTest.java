package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ItemStackDurabilityMixin} 이 무는 바닐라 자리를 반사로 못박는다.
 *
 * <p>이 저장소는 refmap 을 만들지 않아 {@code @ModifyReturnValue} 의 대상 서술자가 틀려도
 * <b>빌드가 그냥 통과</b>하고, 서버가 뜬 뒤 누군가 곡괭이를 한 번 휘두르는 순간에야 터진다.
 * 그래서 대상 서술자와 그 자리로 모이는 길을 여기서 붙들어 둔다.
 */
class NetheriteDurabilityTargetTest {

	/** 믹스인의 {@code method} 문자열에 적은 서술자. 글자 하나까지 이 시험과 같아야 한다. */
	private static final String TARGET_NAME = "processDurabilityChange";

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// -------------------------------------------------- 믹스인이 파고드는 메서드

	@Test
	void 내구도_소모량을_정하는_메서드가_그_서술자_그대로_있다() {
		Method target = assertDoesNotThrow(
				() -> ItemStack.class.getDeclaredMethod(TARGET_NAME,
						int.class, ServerLevel.class, ServerPlayer.class),
				"이 서술자가 바뀌면 내구도 증강이 조용히 안 걸리거나 도구를 쓰는 순간 터진다");

		assertEquals(int.class, target.getReturnType(),
				"돌려주는 int 가 곧 이번에 닳을 양이다. 이 값을 깎는 것이 증강의 전부다");
		assertTrue(Modifier.isPrivate(target.getModifiers()),
				"private 이어야 바깥에서 우회로가 생기지 않는다. 믹스인은 private 도 문다");
		assertFalse(Modifier.isStatic(target.getModifiers()),
				"인스턴스 메서드여야 (ItemStack)(Object)this 로 어떤 아이템인지 알 수 있다");
	}

	@Test
	void 내구도_대상_메서드는_이름이_겹치지_않는다() {
		assertEquals(1, countDeclared(TARGET_NAME),
				"겹침이 생겼으면 어느 쪽에 붙는지 알 수 없다. 서술자를 다시 확인해야 한다");
	}

	// -------------------------------------------------- 그 자리로 모이는 길

	/**
	 * 내구도가 닳는 길이 전부 위 메서드 하나로 모이는지 확인한다.
	 *
	 * <p>반사로는 "실제로 부르는가"까지는 볼 수 없다. 대신 26.2 바이트코드로 확인해 둔 호출
	 * 사슬의 마디가 그대로 남아 있는지를 본다. 마디 하나라도 사라지면 그 길이 바뀐 것이므로
	 * 바이트코드를 다시 읽어야 한다는 신호다.
	 */
	@Test
	void 내구도가_닳는_길이_그대로다() {
		assertDoesNotThrow(() -> ItemStack.class.getDeclaredMethod("hurtAndBreak",
						int.class, ServerLevel.class, ServerPlayer.class, Consumer.class),
				"블록 채굴·공격·방어구 피격이 모두 이 판으로 모이고, 이 판이 대상 메서드를 부른다");
		assertDoesNotThrow(() -> ItemStack.class.getDeclaredMethod("hurtAndBreak",
						int.class, LivingEntity.class, EquipmentSlot.class),
				"위 판을 부르는 자리. 여기서 ServerPlayer 가 아니면 null 이 넘어간다");
		assertDoesNotThrow(() -> ItemStack.class.getDeclaredMethod("hurtAndBreak",
						int.class, LivingEntity.class, InteractionHand.class),
				"손 기준으로 부르는 자리. 칸 기준 판으로 넘긴다");
		assertDoesNotThrow(() -> ItemStack.class.getDeclaredMethod("hurtAndConvertOnBreak",
						int.class, ItemLike.class, LivingEntity.class, EquipmentSlot.class),
				"부서지면 다른 아이템으로 바뀌는 장비도 같은 길을 탄다");
		assertDoesNotThrow(() -> ItemStack.class.getDeclaredMethod("hurtWithoutBreaking",
						int.class, Player.class),
				"부서지지 않게만 닳는 길. 이쪽도 대상 메서드를 직접 부른다");
	}

	/** 소모량을 확률로 깎을 때 쓰는 주사위. 믹스인이 {@code ServerLevel} 에서 꺼낸다. */
	@Test
	void 믹스인이_쓰는_바닐라_메서드가_그대로다() {
		assertDoesNotThrow(() -> Level.class.getDeclaredMethod("getRandom"),
				"ServerLevel 이 물려받는다. 이게 없으면 확률 깎기를 돌릴 주사위가 없다");
		assertDoesNotThrow(() -> ItemStack.class.getDeclaredMethod("isDamageableItem"),
				"대상 메서드가 맨 먼저 보는 조건. 내구도 없는 아이템은 0 을 돌려주고 끝난다");
	}

	// -------------------------------------------------- 도우미

	private static long countDeclared(String name) {
		return Arrays.stream(ItemStack.class.getDeclaredMethods())
				.filter(method -> method.getName().equals(name))
				.count();
	}
}
