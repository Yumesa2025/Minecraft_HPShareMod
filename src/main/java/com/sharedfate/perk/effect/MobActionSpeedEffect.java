package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.PerkEffect;
import net.minecraft.world.entity.EntityType;

/**
 * 몹의 <b>행동 속도</b>에 배율을 건다. 이동만이 아니라 <b>몹의 틱에 매인 모든 것</b>이다.
 *
 * <p>예: {@code { "type": "mob_action_speed", "multiplier": 1.2,
 * "excludes": ["minecraft:ender_dragon"] }} 는 엔더 드래곤을 뺀 적대적 몹이 하는 모든 일을
 * 20% 빠르게 만든다.
 *
 * <h2>{@code mob_speed} 와 무엇이 다른가 — 이것부터 읽을 것</h2>
 * <p>{@link MobSpeedEffect}({@code mob_speed})는 {@code MOVEMENT_SPEED} <b>속성 하나</b>에
 * 수정자를 붙인다. 걷는 속도만 바뀌고, 때리는 주기도 크리퍼 부풀기도 활 쏘는 간격도 그대로다.
 *
 * <p>이쪽은 반대로 <b>속성을 하나도 건드리지 않는다.</b> 대신 서버가 그 몹에게 틱을 주는
 * 횟수를 바꾼다. 몹의 거의 모든 행동이 자기 틱 안에서 숫자를 하나씩 세는 방식이라, 틱을 더
 * 주면 넷이 한꺼번에 빨라진다.
 *
 * <ul>
 *   <li>이동 — 틱마다 한 걸음씩 나아간다</li>
 *   <li>근접 공격 간격 — {@code MeleeAttackGoal} 이 goal 갱신마다 센다</li>
 *   <li>활 쏘기 — {@code RangedAttackGoal.attackTime}, 역시 goal 갱신</li>
 *   <li><b>크리퍼 부풀기</b> — {@code Creeper.swell}, {@code Creeper.tick()} 안</li>
 * </ul>
 *
 * <p><b>그래서 이동 속도는 여기 값 하나로 이미 함께 바뀐다.</b> 한 틱에 나아가는 거리는
 * 그대로인데 틱 수가 20% 늘기 때문이다. {@code mob_speed} 를 같은 배율로 <b>함께 적으면
 * 안 된다</b> — 걸음 폭과 걸음 수에 각각 곱해져 이동만 ×1.44 가 되고, 「모든 행동이 20%
 * 빨라진다」는 설명과 어긋난다.
 *
 * <h2>느리게도 쓸 수 있다</h2>
 * <p>1.0 보다 작으면 반대로 틱을 건너뛴다. ×0.8 이면 다섯 틱에 한 번을 건너뛴다. 프리즘
 * 「성역」이 반경 안에서 하는 일과 같은 셈인데, 이쪽은 조건도 반경도 없는 상시 효과다.
 *
 * <p>{@value #MIN_MULTIPLIER} 아래로는 못 간다. 0 이면 그 몹은 틱을 영영 돌지 못해 불에 타지도,
 * 죽어 사라지지도, 디스폰되지도 않는다. 「느려진다」가 아니라 「시간이 멈춘다」가 되고 월드에
 * 굳은 몹이 쌓인다. {@code SanctuaryEffect.MAX_SLOW} 가 1.0 을 막는 것과 같은 이유다.
 *
 * <p>{@value #MAX_MULTIPLIER} 가 위쪽 끝인 이유도 하나다. 실행부는 한 틱에 <b>많아야 한 번만</b>
 * 더 돌리므로 ×2.0 이 표현할 수 있는 전부다. 그 위를 허용하면 정의에 적힌 숫자와 실제가
 * 조용히 달라진다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 「얼마를, 누구에게」만 들고 있는 자료 그릇이다. 몹 종류별 배율을 합치는 일은
 * {@link MobPerkModifiers} 가, 어느 틱에 한 번 더 돌릴지 고르는 일은
 * {@code com.sharedfate.sync.MobTickRate} 가, 실제로 틱을 주는 자리는
 * {@code ServerLevelSanctuaryTickMixin} 이 맡는다.
 */
public final class MobActionSpeedEffect implements PerkEffect {
	/**
	 * 배율 하한. 0.1 이면 열 틱에 아홉 틱을 건너뛴다.
	 *
	 * <p>{@code SanctuaryEffect.MAX_SLOW}(0.9) 와 같은 자리를 가리킨다. 더 내리면 몹이 굳는다.
	 */
	public static final double MIN_MULTIPLIER = 0.1;

	/** 배율 상한. 실행부가 한 틱에 많아야 한 번 더 돌리므로 ×2.0 이 표현할 수 있는 전부다. */
	public static final double MAX_MULTIPLIER = 2.0;

	private final double multiplier;
	private final MobPerkModifiers.Targets targets;

	public MobActionSpeedEffect(double multiplier, MobPerkModifiers.Targets targets) {
		this.multiplier = multiplier;
		this.targets = targets == null ? MobPerkModifiers.Targets.ALL_HOSTILE : targets;
	}

	/**
	 * JSON에서 만든다. 배율이 없거나 범위를 벗어났으면 경고를 남기고 {@code null}.
	 *
	 * <p>{@link MobSpeedEffect#fromJson} 과 같은 정책이다. 이쪽은 숫자 하나가 곧 「몹이 얼마나
	 * 빨리 움직이고 얼마나 자주 때리는가」라, 잘못된 값을 잘라 쓰면 정의에 적힌 설명과 실제가
	 * 어긋난 채 판이 돈다. 범위를 벗어나면 그 효과를 만들지 않고, 읽는 쪽이 증강 전체를 버린다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = MobHealthEffect.readMultiplier(
				perkId, "mob_action_speed", json, MIN_MULTIPLIER, MAX_MULTIPLIER);
		if (multiplier == null) {
			return null;
		}
		return new MobActionSpeedEffect(multiplier, MobPerkModifiers.parseTargets(perkId, json));
	}

	/** 안전한 범위로 자른 배율. */
	public double multiplierFor() {
		return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
	}

	/** 이 종류의 몹에 걸리는 효과인지. */
	public boolean appliesTo(EntityType<?> type, boolean hostile) {
		return targets.matches(type, hostile);
	}

	public double multiplier() {
		return multiplier;
	}

	public MobPerkModifiers.Targets targets() {
		return targets;
	}

	/**
	 * 배율을 <b>「틱을 한 번 더 주는 비율」</b>로 바꾼다. ×1.2 면 0.2 — 다섯 틱에 한 번이다.
	 *
	 * <p>1.0 이하면 0 이다. 느려지는 쪽은 {@link #skipRate(double)} 가 답한다.
	 *
	 * <p>실행부가 아니라 여기에 두는 이유는 하나다. 이 변환이 곧 「20%」의 뜻이라, 서버 없이
	 * 시험으로 붙들어 둘 수 있어야 한다.
	 */
	public static double extraTickRate(double multiplier) {
		if (!Double.isFinite(multiplier) || multiplier <= 1.0) {
			return 0.0;
		}
		return Math.min(1.0, multiplier - 1.0);
	}

	/**
	 * 배율을 <b>「틱을 건너뛰는 비율」</b>로 바꾼다. ×0.8 이면 0.2 — 다섯 틱에 한 번을 거른다.
	 *
	 * <p>1.0 이상이면 0 이다. {@value #MIN_MULTIPLIER} 아래는 들어올 수 없으므로 이 값이 1.0 이
	 * 되는 일은 없다 — 1.0 이면 몹의 시간이 아예 멈춘다.
	 */
	public static double skipRate(double multiplier) {
		if (!Double.isFinite(multiplier) || multiplier >= 1.0) {
			return 0.0;
		}
		return Math.min(1.0 - MIN_MULTIPLIER, 1.0 - multiplier);
	}
}
