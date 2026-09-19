package com.sharedfate.rule;

import com.sharedfate.TestBootstrap;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AdvancementRewardsExperienceMixin} 이 <b>바닐라 쪽 사실</b>에 기대고 있다. 그 사실이
 * 바뀌면 여기서 먼저 터진다.
 *
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드가 그냥
 * 통과</b>하고, 발전과제를 처음 달성하는 순간(또는 서버가 뜨는 순간) 터진다. 그래서 대상
 * 서술자와 그것이 기대는 클래스 모양을 여기서 붙들어 둔다.
 */
class AdvancementRewardsTargetTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * {@code AdvancementRewards} 가 {@code record} 이자 {@code final} 이다.
	 *
	 * <p>「하위 클래스가 재정의하는 메서드에 믹스인을 거는 것」함정이 이 클래스에는 애초에
	 * 성립하지 않는다는 증거다 — 재정의할 하위 클래스 자체가 있을 수 없다.
	 */
	@Test
	void AdvancementRewards_는_final_레코드다() {
		assertTrue(Modifier.isFinal(AdvancementRewards.class.getModifiers()));
		assertTrue(AdvancementRewards.class.isRecord());
	}

	/** {@code AdvancementRewardsExperienceMixin} 이 파고드는 자리. */
	@Test
	void grant_메서드가_그대로_있다() throws Exception {
		Method grant = AdvancementRewards.class.getDeclaredMethod("grant", ServerPlayer.class);
		assertEquals(void.class, grant.getReturnType());
		assertFalse(Modifier.isStatic(grant.getModifiers()));
	}

	/** {@code experience} 가 레코드 컴포넌트로 남아 있어야 필드 서술자({@code I})가 맞는다. */
	@Test
	void experience_는_int_레코드_컴포넌트다() {
		boolean found = false;
		for (var component : AdvancementRewards.class.getRecordComponents()) {
			if (component.getName().equals("experience")) {
				assertEquals(int.class, component.getType());
				found = true;
			}
		}
		assertTrue(found, "experience 컴포넌트가 있어야 한다");
	}
}
