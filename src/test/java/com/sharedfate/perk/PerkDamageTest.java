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
		double dealt = new DamageDealtEffect(1.5).damageDealtMultiplier();
		double taken = new DamageTakenEffect(0.5).damageTakenMultiplier();

		assertEquals(6.0F, PerkDamage.combine(8.0F, dealt * taken), 1.0e-5F);
	}

	@Test
	void 배율은_정의에_적힌_값_그대로_걸린다() {
		// 중첩이 없으므로 같은 증강을 두 번 가질 수 없다. 배율이 불어날 길이 없다.
		double dealt = new DamageDealtEffect(1.2).damageDealtMultiplier();

		assertEquals(1.2, dealt, 1.0e-9);
		assertEquals(12.0F, PerkDamage.combine(10.0F, dealt), 1.0e-4F);
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

	// ------------------------------------------------------------------ 바닐라 피격 쿨타임 판정

	/**
	 * 쿨타임 밖이면 바닐라는 아무것도 깎지 않는다. <b>경계값 10 도 아직 밖이다</b> — 바닐라가
	 * {@code > 10} 으로 묻기 때문이다. 한 칸 밀리면 「호위」가 한 틱 동안 엉뚱하게 판단한다.
	 */
	@Test
	void 피격_쿨타임_밖에서는_받은_값_그대로다() {
		assertEquals(6.0F, PerkDamage.effectiveAmount(6.0F, 4.0F, 0, false), 0.0F);
		assertEquals(6.0F, PerkDamage.effectiveAmount(6.0F, 4.0F, 10, false), 0.0F);
	}

	/** 쿨타임 안에서는 직전 피해를 넘는 몫만 실제로 들어간다. */
	@Test
	void 쿨타임_안에서는_직전보다_넘치는_만큼만_남는다() {
		assertEquals(2.0F, PerkDamage.effectiveAmount(6.0F, 4.0F, 11, false), 0.0F);
		assertEquals(2.0F, PerkDamage.effectiveAmount(6.0F, 4.0F, 20, false), 0.0F);
	}

	/**
	 * 「호위」가 낭비되지 않아야 하는 바로 그 상황이다. 좀비 셋에게 동시에 맞으면 둘째·셋째 대는
	 * 바닐라가 통째로 버리므로 여기서 0 이 나와야 하고, 그래야 부르는 쪽의 {@code > 0} 검사가
	 * 쿨타임을 쓰지 않는다.
	 */
	@Test
	void 쿨타임_안에서_직전보다_약한_대는_통째로_버려진다() {
		assertEquals(0.0F, PerkDamage.effectiveAmount(3.0F, 4.0F, 20, false), 0.0F);
		assertEquals(0.0F, PerkDamage.effectiveAmount(4.0F, 4.0F, 20, false), 0.0F,
				"같은 값이면 바닐라는 amount > lastHurt 가 거짓이라 버린다");
	}

	/** {@code bypasses_cooldown} 은 쿨타임을 아예 보지 않는다. 독·굶주림·마법 피해가 그렇다. */
	@Test
	void 쿨타임을_무시하는_피해는_깎이지_않는다() {
		assertEquals(6.0F, PerkDamage.effectiveAmount(6.0F, 4.0F, 20, true), 0.0F);
		assertEquals(3.0F, PerkDamage.effectiveAmount(3.0F, 4.0F, 20, true), 0.0F);
	}

	/**
	 * 쿨타임을 한 번도 안 겪은 사람({@code lastHurt} 가 0)에게는 아무 영향이 없다. 이 함수가
	 * 평소 피해를 조용히 깎아 버리는 일이 없다는 뜻이다.
	 */
	@Test
	void 직전_피해가_없으면_깎이지_않는다() {
		assertEquals(6.0F, PerkDamage.effectiveAmount(6.0F, 0.0F, 20, false), 0.0F);
	}
}
