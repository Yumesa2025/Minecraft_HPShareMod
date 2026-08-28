package com.sharedfate.perk;

import com.sharedfate.perk.effect.DamageDealtEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 피해 배율 반영의 순수 계산 부분만 본다. 몹·플레이어·월드가 필요한 부분은
 * {@code LivingEntityPerkDamageMixin} 쪽이라 여기서는 다루지 않는다.
 */
class PerkDamageTest {
	@Test
	void 배율이_1이면_원래_값을_그대로_돌려준다() {
		assertEquals(7.5F, PerkDamage.combine(7.5F, 1.0), 0.0F);
		assertEquals(0.5F, PerkDamage.combine(0.5F, 1.0), 0.0F);
	}

	@Test
	void 주는_피해와_받는_피해_배율은_곱해서_한_번에_걸린다() {
		double dealt = new DamageDealtEffect(1.5).damageDealtMultiplier(1);
		double taken = new DamageTakenEffect(0.5).damageTakenMultiplier(1);

		assertEquals(6.0F, PerkDamage.combine(8.0F, dealt * taken), 1.0e-5F);
	}

	@Test
	void 중첩은_거듭제곱으로_쌓인다() {
		double twoStacks = new DamageDealtEffect(1.2).damageDealtMultiplier(2);

		assertEquals(1.44, twoStacks, 1.0e-9);
		assertEquals(14.4F, PerkDamage.combine(10.0F, twoStacks), 1.0e-4F);
	}

	@Test
	void 배율이_0이면_피해가_사라진다() {
		assertEquals(0.0F, PerkDamage.combine(20.0F, 0.0), 0.0F);
	}

	@Test
	void 피해가_0이하거나_유한하지_않으면_손대지_않는다() {
		assertEquals(0.0F, PerkDamage.scale(null, null, 0.0F), 0.0F);
		assertEquals(-3.0F, PerkDamage.scale(null, null, -3.0F), 0.0F);
		assertTrue(Float.isNaN(PerkDamage.scale(null, null, Float.NaN)));
	}

	@Test
	void 팀이_없으면_피해는_바닐라와_같다() {
		// victim·source 가 없으면 배율 조회 자체를 타지 않는다.
		assertEquals(12.25F, PerkDamage.scale(null, null, 12.25F), 0.0F);
	}

	@Test
	void 이상한_배율은_원래_값으로_물러난다() {
		assertEquals(4.0F, PerkDamage.combine(4.0F, Double.NaN), 0.0F);
		assertEquals(4.0F, PerkDamage.combine(4.0F, Double.POSITIVE_INFINITY), 0.0F);
		assertEquals(4.0F, PerkDamage.combine(4.0F, -2.0), 0.0F);
	}

	@Test
	void 곱한_결과는_상한에서_잘린다() {
		float huge = PerkDamage.combine(Float.MAX_VALUE / 2.0F, 1.0e30);

		assertEquals(PerkDamage.MAX_DAMAGE, huge, 0.0F);
		assertTrue(Float.isFinite(huge), "무한대가 바닐라 계산으로 새어나가면 안 된다");
	}

	@Test
	void 배율_1은_같은_float_비트를_유지한다() {
		float original = 3.3333333F;

		assertEquals(Float.floatToRawIntBits(original),
				Float.floatToRawIntBits(PerkDamage.combine(original, 1.0)),
				"1.0 배는 부동소수 반올림조차 일으키지 않아야 한다");
	}
}
