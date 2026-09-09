package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 프리즘 「성역」. 팀원이 뭉쳐 있으면 그 주위의 몹이 느려지고, 흩어져 있으면 반대로 빨라진다.
 *
 * <pre>{@code
 * { "type": "sanctuary", "distance": 20, "radius": 10, "slow": 0.4, "haste_when_apart": 0.15 }
 * }</pre>
 *
 * <h2>필드</h2>
 * <ul>
 *   <li>{@code distance} — 이 거리 안에 <b>팀원 전원</b>이 들어 있어야 성역이 켜진다. 안 적으면
 *       {@value #DEFAULT_DISTANCE} 다. 세트 「결속 3」의 거리 배율이 여기에 저절로 먹는다
 *       ({@link com.sharedfate.sync.TeamProximity#together}).</li>
 *   <li>{@code radius} — 팀원 한 명을 중심으로 성역이 미치는 반경(칸). 안 적으면
 *       {@value #DEFAULT_RADIUS} 다. 이 값에도 결속 배율이 먹는다.</li>
 *   <li>{@code slow} — 성역 안의 몹이 <b>틱을 건너뛸 확률</b>. 0.4 면 40% 느려진다. 안 적으면
 *       {@value #DEFAULT_SLOW} 다.</li>
 *   <li>{@code haste_when_apart} — 뭉쳐 있지 <b>않은</b> 동안 몹이 <b>틱을 한 번 더 돌 확률</b>.
 *       0.15 면 15% 빨라진다. 이쪽에는 <b>반경이 없다</b> — 대가는 상시·전역이다. 안 적으면
 *       {@value #DEFAULT_HASTE_WHEN_APART} 다.</li>
 * </ul>
 *
 * <p>{@code hasteWhenApart} 처럼 카멜케이스로 적어도 같게 읽는다. 다른 효과들과 같은 규칙이다.
 *
 * <h2>범위를 벗어난 값은 정의를 버리지 않는다</h2>
 * <p>{@link AuraDamageEffect#fromJson} 과 같은 정책이다. 숫자 하나가 틀렸다고 프리즘 증강이
 * 통째로 사라지는 쪽이 손해가 크다. 경고만 남기고 범위 끝으로 자른다. 숫자가 아닌 값은 아예
 * 없는 것으로 보고 기본값을 쓴다({@code readDouble} 의 규칙 그대로다).
 *
 * <p>{@code slow} 의 상한이 {@value #MAX_SLOW} 인 이유는 하나다. 1.0 이면 몹이 <b>틱을 영영
 * 돌지 못해</b> 불에 타지도, 죽어 사라지지도, 디스폰되지도 않는다. 「느려진다」가 아니라
 * 「시간이 멈춘다」가 되고 월드에 굳은 몹이 쌓인다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 「얼마나 가까워야 하고, 얼마나 넓고, 얼마나 느려지는가」만 들고 있는 자료 그릇이다.
 * 지금 뭉쳐 있는지 재고, 어떤 몹이 반경 안에 있는지 고르는 일은 전부
 * {@link com.sharedfate.sync.SanctuaryManager} 가 맡고, 실제로 틱을 건너뛰는 자리는
 * {@code ServerLevelSanctuaryTickMixin} 이다.
 */
public final class SanctuaryEffect implements PerkEffect {
	/** 팀원 전원이 이 거리 안에 있어야 성역이 켜진다(칸). */
	public static final double DEFAULT_DISTANCE = 20.0;
	/** 거리 하한. 이보다 짧으면 서로 몸이 닿아야 해서 사실상 켜지지 않는다. */
	public static final double MIN_DISTANCE = 4.0;
	/** 거리 상한. {@link ProximityEffect} 와 같은 값이다. */
	public static final double MAX_DISTANCE = 512.0;

	/** 팀원 한 명을 중심으로 성역이 미치는 기본 반경(칸). */
	public static final double DEFAULT_RADIUS = 10.0;
	/** 반경 하한. 이보다 좁으면 몹이 이미 때리는 거리라 있으나 마나다. */
	public static final double MIN_RADIUS = 2.0;
	/**
	 * 반경 상한.
	 *
	 * <p>{@link AuraDamageEffect} 와 달리 이 값은 서버 부담과 거의 상관이 없다. 성역은 상자로
	 * 엔티티를 찾지 않고 <b>이미 틱을 도는 몹에게 거리를 되묻는</b> 방식이라, 반경이 넓어져도
	 * 비교 한 번의 값만 커진다. 그래서 상한은 성능이 아니라 <b>판의 균형</b>으로 잡았다 —
	 * 64칸이면 이미 눈에 보이는 모든 몹이 성역 안이다.
	 */
	public static final double MAX_RADIUS = 64.0;

	/** 성역 안의 몹이 틱을 건너뛸 기본 확률. */
	public static final double DEFAULT_SLOW = 0.4;
	/** 감속 하한. 0 이면 아무 일도 하지 않는다 — 정의를 버리지는 않는다. */
	public static final double MIN_SLOW = 0.0;
	/** 감속 상한. 1.0 을 막는 이유는 클래스 주석에 적어 두었다. */
	public static final double MAX_SLOW = 0.9;

	/** 흩어져 있는 동안 몹이 틱을 한 번 더 돌 기본 확률. */
	public static final double DEFAULT_HASTE_WHEN_APART = 0.15;
	public static final double MIN_HASTE_WHEN_APART = 0.0;
	/**
	 * 가속 상한.
	 *
	 * <p>실행부는 틱을 <b>많아야 한 번만</b> 더 돌린다. 1.0 이 곧 「두 배로 빠르다」이고 그 위는
	 * 표현할 방법이 없으므로 여기서 자른다.
	 */
	public static final double MAX_HASTE_WHEN_APART = 1.0;

	private final double distance;
	private final double radius;
	private final double slow;
	private final double hasteWhenApart;

	public SanctuaryEffect(double distance, double radius, double slow, double hasteWhenApart) {
		this.distance = distance;
		this.radius = radius;
		this.slow = slow;
		this.hasteWhenApart = hasteWhenApart;
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>어떤 값이 잘못돼도 {@code null} 을 돌려주지 않는다. 프리즘 증강 하나가 통째로 사라지는
	 * 것보다 값 하나가 잘린 채 도는 쪽이 낫다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		double distance = clamp(perkId, "distance", read(json, "distance", DEFAULT_DISTANCE),
				MIN_DISTANCE, MAX_DISTANCE);
		double radius = clamp(perkId, "radius", read(json, "radius", DEFAULT_RADIUS),
				MIN_RADIUS, MAX_RADIUS);
		double slow = clamp(perkId, "slow", read(json, "slow", DEFAULT_SLOW), MIN_SLOW, MAX_SLOW);
		double haste = clamp(perkId, "haste_when_apart",
				read(json, hasteKey(json), DEFAULT_HASTE_WHEN_APART),
				MIN_HASTE_WHEN_APART, MAX_HASTE_WHEN_APART);
		return new SanctuaryEffect(distance, radius, slow, haste);
	}

	/** 카멜케이스로 적어도 읽어 준다. 나머지 셋은 한 낱말이라 갈릴 일이 없다. */
	private static String hasteKey(JsonObject json) {
		return json != null && json.has("hasteWhenApart") ? "hasteWhenApart" : "haste_when_apart";
	}

	/** 숫자가 아니거나 없으면 기본값. {@code readDouble} 의 규칙 그대로다. */
	private static double read(JsonObject json, String key, double fallback) {
		Double value = PerkEffectType.readDouble(json, key);
		return value == null ? fallback : value;
	}

	private static double clamp(String perkId, String key, double value, double min, double max) {
		if (value >= min && value <= max) {
			return value;
		}
		double cut = Math.max(min, Math.min(max, value));
		SharedFateMod.LOGGER.warn(
				"증강 {}: sanctuary 의 {} 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, key, min, max, cut, value);
		return cut;
	}

	/** 팀원 전원이 이 거리 안에 있어야 성역이 켜진다(칸). */
	public double distance() {
		return distance;
	}

	/** 팀원 한 명을 중심으로 성역이 미치는 반경(칸). */
	public double radius() {
		return radius;
	}

	/**
	 * 반경의 제곱. 거리 비교에 제곱근을 뽑지 않으려고 미리 내어 둔다.
	 *
	 * <p><b>결속 배율이 먹지 않은 값</b>이다. 실제 판정에 쓰는 반경은 팀마다 달라질 수 있으므로
	 * ({@code TeamProximity.scaled}) 실행부가 그때 다시 제곱한다. 여기 값은 배율이 1일 때의
	 * 기준선이자 시험용이다.
	 */
	public double radiusSquared() {
		return radius * radius;
	}

	/** 성역 안의 몹이 틱을 건너뛸 확률. */
	public double slow() {
		return slow;
	}

	/** 흩어져 있는 동안 몹이 틱을 한 번 더 돌 확률. */
	public double hasteWhenApart() {
		return hasteWhenApart;
	}

	/**
	 * 이번 틱을 건너뛰는가.
	 *
	 * <p>난수를 밖에서 받는다. 그래야 「40% 면 정확히 40%」를 서버 없이 시험할 수 있다.
	 *
	 * @param roll 0 이상 1 미만의 난수
	 */
	public boolean skipsTick(double roll) {
		return slow > 0.0 && roll < slow;
	}

	/**
	 * 이번 틱을 한 번 더 도는가. 흩어져 있을 때만 묻는다.
	 *
	 * @param roll 0 이상 1 미만의 난수
	 */
	public boolean runsExtraTick(double roll) {
		return hasteWhenApart > 0.0 && roll < hasteWhenApart;
	}
}
