package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 적대 몹의 <b>자연 스폰 시도 횟수</b>에 배율을 건다.
 *
 * <p>예: {@code { "type": "mob_spawn_rate", "multiplier": 1.35 }} 는 적대 몹이 자연스럽게
 * 생겨나는 시도를 1.35배로 늘린다. 「세트 효과」 화력 4단계 보상이 쓰는 값이 이것이다.
 *
 * <p>이 효과는 팀원에게 붙이는 것이 아니므로 {@link #apply}/{@link #remove} 는 아무 일도 하지
 * 않고, 값만 들고 있는다.
 * 실제로 스폰 경로에 끼어드는 일은 {@link MobPerkModifiers} 와
 * {@code NaturalSpawnerRateMixin} 이 맡는다.
 *
 * <h2>왜 {@code targets} 를 받지 않는가</h2>
 * <p>체력·공격력·이동속도는 몹 <b>한 마리</b>에게 걸리므로 「어떤 종류에」를 고를 수 있다.
 * 스폰율은 다르다. 바닐라의 자연 스폰은 몹 종류가 아니라 <b>몹 갈래</b>
 * ({@code MobCategory.MONSTER} 하나)를 단위로 청크마다 한 번씩 돌고, 그 안에서 어떤 몹이
 * 뽑힐지는 생물 군계의 가중치 표가 정한다. 「좀비만 1.35배」 같은 것을 하려면 그 표를 손대야
 * 하는데, 그러면 다른 몹의 몫이 줄어 <b>전체 스폰율은 그대로</b>가 된다. 그래서 이 효과는
 * 적대 몹 전체({@code MONSTER})에만 걸리고 대상 한정을 받지 않는다. 주민·소 같은
 * {@code CREATURE} 갈래와 물속 몹은 손대지 않는다.
 *
 * <h2>값 범위</h2>
 * <p>{@value #MIN_MULTIPLIER} ~ {@value #MAX_MULTIPLIER}. 0 은 「적대 몹이 자연스럽게 전혀
 * 생기지 않는다」는 뜻이라 받지 않는다(스폰 방 · 스포너 · 요새 같은 다른 경로는 그대로
 * 남는다). 위쪽은
 * <b>성능</b> 때문에 막는다 — 배율이 곧 스폰 경로를 다시 도는 횟수라, 큰 값은 매 틱 비용을
 * 그대로 곱한다. 실제로 쓰는 값은 ×0.8 ~ ×1.5 언저리다.
 */
public final class MobSpawnRateEffect implements PerkEffect {
	/** 설정에서 받아들이는 배율 범위. */
	public static final double MIN_MULTIPLIER = 0.1;
	public static final double MAX_MULTIPLIER = 4.0;

	private final double multiplier;

	public MobSpawnRateEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 배율이 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || !Double.isFinite(multiplier)
				|| multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: mob_spawn_rate 효과의 multiplier 값이 없거나 범위를 벗어났습니다 ({})",
					perkId, multiplier);
			return null;
		}
		return new MobSpawnRateEffect(multiplier);
	}

	/**
	 * 안전한 범위로 자른 배율.
	 *
	 * <p>{@link #fromJson} 을 지나온 값은 이미 범위 안이지만, 코드에서 직접 만든 것까지
	 * 막으려고 한 번 더 자른다. 이상한 값은 1.0 으로 물러난다.
	 */
	public double multiplierFor() {
		if (!Double.isFinite(multiplier) || multiplier < MIN_MULTIPLIER) {
			return 1.0;
		}
		return Math.min(MAX_MULTIPLIER, multiplier);
	}

	/** 정의에 적힌 값 그대로. */
	public double multiplier() {
		return multiplier;
	}
}
