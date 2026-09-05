package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 피해를 받으면 그만큼 팀 공유 허기가 찬다.
 *
 * <pre>{@code
 * { "type": "hunger_on_damage", "per_damage": 0.4 }
 * }</pre>
 *
 * <p>생존 3단계 「맞으면 배부르다」가 이 타입 하나로 만들어진다. {@code per_damage} 는
 * <b>피해 1당 채워지는 허기</b>다. 0.4 면 피해 5 를 받았을 때 허기 2 칸이 찬다.
 *
 * <h2>무엇을 「피해」로 세는가</h2>
 * <p><b>방어구·저항·흡수를 모두 지난 뒤 실제로 들어간 피해</b>다. 감산 전의 원래 피해가 아니다.
 * 어디서 그 값을 고르는지는 {@link com.sharedfate.perk.PerkTriggers#damageBasis} 에 적혀 있고
 * 시험이 그 선택을 못박고 있다. 감산 전을 기준으로 삼으면 방어구를 두껍게 입을수록 실제로 깎이는
 * 체력은 적은데 허기는 그대로 차서, 방어 증강과 겹칠 때 값이 터무니없이 커진다.
 *
 * <h2>왜 개인이 아니라 공유 풀인가</h2>
 * <p>이 모드는 허기를 팀이 공유한다. 맞은 사람의 {@code FoodData} 를 직접 채우면
 * {@code StatMirror} 가 그 변화량을 관측해 공유 풀에 <b>한 번 더</b> 더한다. 그래서 이 효과는
 * 개인에게 아무것도 하지 않는다 — {@link #apply}/{@link #remove} 를 재정의하지 않는 것이 그
 * 뜻이다. 실제로 공유 풀에 더하는 일은 {@link com.sharedfate.perk.PerkTriggers} 가 맡고,
 * 왜 개인이 아니라 풀에 더하는지는 {@link com.sharedfate.perk.PerkKillRewards} 머리말에 자세히
 * 적혀 있다.
 *
 * <h2>한 번 맞으면 한 번만 찬다</h2>
 * <p>피격은 맞은 사람 한 명이 만든 한 번의 사건이고, 공유 풀은 팀에 하나뿐인
 * {@code TeamState} 다. 팀원 수만큼 도는 고리가 어디에도 없으므로 인원수와 무관하게 언제나
 * 1인분이다. 공유된 상태이상처럼 <em>하나의 원인</em>이 인원수만큼 복제되는 경우는
 * {@code SharedEffectDamage} 가 피해 진입점에서 대표 한 명만 남기고 나머지를 버리므로,
 * {@code AFTER_DAMAGE} 까지 올라오는 사건도 한 번뿐이다.
 *
 * <h2>최상위에만 놓을 수 있다</h2>
 * <p>피격을 아는 자리에서 이 효과를 찾는 {@link com.sharedfate.perk.PerkTriggers} 는 정의의
 * 최상위 효과만 훑는다. {@code periodic} 이나 {@code conditional} 안에 넣으면 조용히 아무 일도
 * 하지 않으므로 읽는 시점에 걸러 낸다.
 */
public final class HungerOnDamageEffect implements PerkEffect {
	/**
	 * {@code per_damage} 의 상한.
	 *
	 * <p>허기 상한이 20 이라 피해 1 에 20 이면 이미 한 대에 배가 가득 찬다. 그보다 큰 값은
	 * 정의 실수다.
	 */
	static final double MAX_PER_DAMAGE = 20.0;

	/**
	 * 한 번의 피격으로 채울 수 있는 허기의 상한.
	 *
	 * <p>허기 자체가 20 을 넘지 못하므로 그 위는 뜻이 없다. 용암에 한 번 크게 맞은 값이
	 * 공유 풀 계산으로 그대로 흘러들지 않게 막는 그물이다.
	 */
	static final float MAX_GAIN = 20.0F;

	private final double perDamage;

	public HungerOnDamageEffect(double perDamage) {
		this.perDamage = perDamage;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double perDamage = PerkEffectType.readDouble(json, "per_damage");
		if (perDamage == null || !(perDamage > 0.0) || perDamage > MAX_PER_DAMAGE) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: hunger_on_damage 의 per_damage 가 없거나 범위를 벗어났습니다 ({})",
					perkId, perDamage);
			return null;
		}
		if (index < 0 || index >= DamageTakenFromEffect.MAX_TOP_LEVEL_INDEX) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: hunger_on_damage 는 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}
		return new HungerOnDamageEffect(perDamage);
	}

	/** 정의에 적힌 값 그대로. */
	public double perDamage() {
		return perDamage;
	}

	/**
	 * 이 피해로 채워질 허기.
	 *
	 * <p>정수가 아니다. 피해 3 에 {@code per_damage} 0.4 면 1.2 이고, 허기 칸은 정수라
	 * 남는 0.2 를 다음 피격까지 들고 가는 것은 부르는 쪽
	 * ({@link com.sharedfate.perk.PerkTriggers})의 일이다. 여기서 반올림해 버리면 작은 피해가
	 * 통째로 사라지거나 부풀어 「피해 1당 0.4」가 지켜지지 않는다.
	 *
	 * <p>JSON 경로는 이미 범위를 검사하지만 생성자는 공개돼 있어 Java 쪽에서 어떤 값이든 들어올
	 * 수 있다. 공유 풀 계산이 NaN 이나 무한대를 보면 안 된다.
	 *
	 * @param damageTaken 감산 뒤 실제로 들어간 피해
	 */
	public float hungerFor(float damageTaken) {
		if (!(damageTaken > 0.0F) || !Float.isFinite(damageTaken) || !Double.isFinite(perDamage)) {
			return 0.0F;
		}
		double rate = Math.max(0.0, Math.min(MAX_PER_DAMAGE, perDamage));
		double gain = (double) damageTaken * rate;
		if (!Double.isFinite(gain)) {
			return MAX_GAIN;
		}
		return (float) Math.max(0.0, Math.min(MAX_GAIN, gain));
	}
}
