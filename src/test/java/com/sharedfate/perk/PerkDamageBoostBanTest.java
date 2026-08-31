package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code no_damage_boost}의 상쇄 계산({@link PerkDamageBoostBan#compute})을 본다.
 *
 * <p>플레이어를 읽지 않는 순수 계산이라 살아 있는 서버 없이 확인할 수 있다. 실제로 수정자를
 * 붙이고 떼는 자리({@link PerkDamageBoostBan#refresh})는 {@code PerkWeaponDamageTest}와 같은
 * 이유로 여기서 다루지 않는다.
 */
class PerkDamageBoostBanTest {
	private static final Identifier STRENGTH = Identifier.fromNamespaceAndPath("minecraft", "effect.strength");
	private static final Identifier WEAKNESS = Identifier.fromNamespaceAndPath("minecraft", "effect.weakness");
	private static final Identifier ITEM_OWN = Identifier.fromNamespaceAndPath("minecraft", "base_attack_damage");
	private static final double EPSILON = 1.0e-9;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 수정자가_없으면_상쇄할_것도_없다() {
		var compensation = PerkDamageBoostBan.compute(List.of(), Set.of());

		assertEquals(0.0, compensation.addValue(), EPSILON);
		assertEquals(0.0, compensation.addMultipliedBase(), EPSILON);
		assertEquals(0.0, compensation.addMultipliedTotal(), EPSILON);
	}

	@Test
	void 예외_목록에_있으면_양수여도_건드리지_않는다() {
		var modifiers = List.of(new AttributeModifier(ITEM_OWN, 3.0, ADD_VALUE));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of(ITEM_OWN));

		assertEquals(0.0, compensation.addValue(), EPSILON, "무기 자신의 값은 정상이다");
	}

	@Test
	void 감소는_원천에_상관없이_손대지_않는다() {
		var modifiers = List.of(new AttributeModifier(WEAKNESS, -4.0, ADD_VALUE));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of());

		assertEquals(0.0, compensation.addValue(), EPSILON, "나약함 같은 감소는 그대로 통과한다");
	}

	@Test
	void 더하기_양수는_정확히_반대로_상쇄한다() {
		var modifiers = List.of(new AttributeModifier(STRENGTH, 3.0, ADD_VALUE));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of());

		assertEquals(-3.0, compensation.addValue(), EPSILON);
	}

	@Test
	void 기본값에_곱하기_양수도_정확히_반대로_상쇄한다() {
		var modifiers = List.of(new AttributeModifier(STRENGTH, 0.2, ADD_MULTIPLIED_BASE));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of());

		assertEquals(-0.2, compensation.addMultipliedBase(), EPSILON);
	}

	@Test
	void 전체에_곱하기_양수_하나는_역수로_상쇄한다() {
		// (1+1.0) 을 곱한 것을 상쇄하려면 1/(1+1.0) - 1 = -0.5 를 더 곱해야 한다.
		var modifiers = List.of(new AttributeModifier(STRENGTH, 1.0, ADD_MULTIPLIED_TOTAL));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of());

		assertEquals(-0.5, compensation.addMultipliedTotal(), EPSILON);
	}

	@Test
	void 전체에_곱하기_양수_여럿은_곱해서_한번에_상쇄한다() {
		// ×(1+1.0) 과 ×(1+0.5) 를 둘 다 상쇄하려면 그 곱의 역수를 곱해야 한다.
		var modifiers = List.of(
				new AttributeModifier(STRENGTH, 1.0, ADD_MULTIPLIED_TOTAL),
				new AttributeModifier(Identifier.fromNamespaceAndPath("sharedfate", "buff"), 0.5,
						ADD_MULTIPLIED_TOTAL));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of());

		double expected = 1.0 / ((1.0 + 1.0) * (1.0 + 0.5)) - 1.0;
		assertEquals(expected, compensation.addMultipliedTotal(), EPSILON);
	}

	@Test
	void 자기_자신의_상쇄용_수정자는_다시_상쇄하지_않는다() {
		Identifier ownId = Identifier.fromNamespaceAndPath("sharedfate", "perk/damage_boost_ban/add_value");
		var modifiers = List.of(
				new AttributeModifier(STRENGTH, 3.0, ADD_VALUE),
				new AttributeModifier(ownId, -3.0, ADD_VALUE));

		// exemptIds 에 자기 자신의 id 를 넣어 두면 무한히 자신을 되갚지 않는다.
		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of(ownId));

		assertEquals(-3.0, compensation.addValue(), EPSILON);
	}

	@Test
	void 양수와_음수가_섞이면_양수만_모은다() {
		var modifiers = List.of(
				new AttributeModifier(STRENGTH, 3.0, ADD_VALUE),
				new AttributeModifier(WEAKNESS, -4.0, ADD_VALUE));

		var compensation = PerkDamageBoostBan.compute(modifiers, Set.of());

		assertEquals(-3.0, compensation.addValue(), EPSILON, "음수는 상쇄 대상이 아니다");
	}
}
