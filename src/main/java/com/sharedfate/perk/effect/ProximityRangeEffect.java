package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * 팀이 지켜야 할 거리를 넓힌다. {@code proximity} 와 {@code gather} 의 기준 거리에 함께 곱한다.
 *
 * <pre>{@code
 * { "type": "proximity_range", "multiplier": 2.0 }
 * }</pre>
 *
 * <p>{@code multiplier} 는 생략할 수 있고, 생략하면 {@link #DEFAULT_MULTIPLIER} 다. 범위를 벗어난
 * 값은 정의를 버리지 않고 {@link #MIN_MULTIPLIER}~{@link #MAX_MULTIPLIER} 안으로 자른다.
 * {@link RallyShardEffect} 와 같은 정책이다 — 숫자 하나가 틀렸다고 증강 전체를 잃는 손해가 더 크다.
 *
 * <h2>둘을 반드시 함께 늘려야 한다</h2>
 * <p>프리즘 「운명 공동체」는 {@code proximity}(모이면 보상)와 {@code gather}(흩어지면 강제 집합)를
 * <b>같은 거리</b>로 짝지어 쓴다. 여기서 보상 쪽 거리만 늘리면 집합 거리가 그대로 남아, 늘어난
 * 거리에 닿기도 전에 끌려 모인다. 넓힌 보상은 영원히 받을 수 없고 플레이어에게는 증강이 아무
 * 일도 하지 않은 것처럼 보인다. 그래서 이 효과는 한쪽만 고를 수 없고,
 * {@link com.sharedfate.sync.TeamGathering} 의 두 판정 모두에 같은 배율로 들어간다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 배율만 들고 있는 자료 그릇이고, 곱셈 자체는 월드 없이도 확인할 수 있게
 * {@link #multiplierOf}·{@link #scale} 두 순수 함수로 떼어 두었다. 실제로 거리를 재고 그 값을
 * 쓰는 자리는 {@link com.sharedfate.sync.TeamGathering} 이다.
 */
public final class ProximityRangeEffect implements PerkEffect {
	/** {@code multiplier} 를 적지 않았을 때의 배율. 거리를 두 배로 넓힌다. */
	public static final double DEFAULT_MULTIPLIER = 2.0;
	/** 1.0 보다 작으면 거리를 좁히는 효과가 된다. 이 타입이 하려는 일이 아니다. */
	public static final double MIN_MULTIPLIER = 1.0;
	/**
	 * 배율 상한.
	 *
	 * <p>넷을 넘기면 「운명 공동체」의 64블록이 256블록을 넘어 사실상 제약이 사라진다. 서로
	 * 떨어져 살아도 되는 팀에게는 이 증강도, 짝지은 집합도 아무 뜻이 없다.
	 */
	public static final double MAX_MULTIPLIER = 4.0;

	private final double multiplier;

	/**
	 * 범위 밖이거나 숫자가 아닌 값은 여기서 한 번 더 잘라, 이 객체는 언제나 성한 배율만 들고 있다.
	 *
	 * <p>무한대는 자르면 상한이 되지만 {@code NaN} 은 크고 작음을 따질 수 없어 그대로 새어
	 * 나간다. 그것만 따로 기본값으로 돌린다.
	 */
	public ProximityRangeEffect(double multiplier) {
		this.multiplier = Double.isNaN(multiplier)
				? DEFAULT_MULTIPLIER
				: Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
	}

	/** JSON에서 만든다. 값이 없거나 이상해도 정의를 버리지 않는다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return new ProximityRangeEffect(readMultiplier(perkId, json));
	}

	private static double readMultiplier(String perkId, @Nullable JsonObject json) {
		if (json == null || !json.has("multiplier")) {
			return DEFAULT_MULTIPLIER;
		}
		Double raw = PerkEffectType.readDouble(json, "multiplier");
		if (raw == null) {
			SharedFateMod.LOGGER.warn("증강 {}: proximity_range 의 multiplier 가 숫자가 아니라 {} 로 봅니다 ({})",
					perkId, DEFAULT_MULTIPLIER, json.get("multiplier"));
			return DEFAULT_MULTIPLIER;
		}
		if (raw >= MIN_MULTIPLIER && raw <= MAX_MULTIPLIER) {
			return raw;
		}
		double cut = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, raw));
		SharedFateMod.LOGGER.warn(
				"증강 {}: proximity_range 의 multiplier 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, MIN_MULTIPLIER, MAX_MULTIPLIER, cut, raw);
		return cut;
	}

	/** 거리에 곱할 배율. 언제나 {@link #MIN_MULTIPLIER}~{@link #MAX_MULTIPLIER} 안이다. */
	public double multiplier() {
		return multiplier;
	}

	/**
	 * 이 효과들이 거리에 곱할 배율을 모두 곱한 값. 하나도 없으면 1.0.
	 *
	 * <p>여러 개를 가졌으면 전부 곱한다. 2.0 둘이면 4.0 이다 — 상한은 정의 하나하나에 걸리는
	 * 것이고, 여러 증강을 모아 얻은 값에는 걸지 않는다. 그렇게 모으는 것 자체가 성과다.
	 *
	 * <p>{@link com.sharedfate.perk.PerkSwapRules#intervalMultiplier} 와 같은 방식으로, 곱이
	 * 성한 수가 아니면 아무것도 곱하지 않은 것으로 본다.
	 *
	 * @param effects 팀이 가진 효과들. {@code proximity_range} 가 아닌 것은 그냥 지나친다
	 */
	public static double multiplierOf(@Nullable Iterable<? extends PerkEffect> effects) {
		if (effects == null) {
			return 1.0;
		}
		double total = 1.0;
		for (PerkEffect effect : effects) {
			if (effect instanceof ProximityRangeEffect range) {
				total *= range.multiplier();
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	/**
	 * 기준 거리에 배율을 먹인다.
	 *
	 * <p>배율이 성한 수가 아니거나 결과가 넘치면 원래 거리를 그대로 돌려준다. 거리 판정이
	 * 0 이나 무한대가 되면 팀이 영영 모이거나 영영 모이지 않는다.
	 *
	 * @param distance   정의에 적힌 기준 거리(블록)
	 * @param multiplier {@link #multiplierOf} 가 낸 배율
	 */
	public static double scale(double distance, double multiplier) {
		if (!Double.isFinite(distance) || !Double.isFinite(multiplier) || multiplier <= 0.0) {
			return distance;
		}
		double scaled = distance * multiplier;
		return Double.isFinite(scaled) ? scaled : distance;
	}
}
