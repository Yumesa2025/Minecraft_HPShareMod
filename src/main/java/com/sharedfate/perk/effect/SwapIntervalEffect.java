package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 팀원 위치 교환 주기를 바꾼다. 배율과 고정값 두 가지 방식이 있다.
 *
 * <pre>{@code
 * { "type": "swap_interval", "multiplier": 0.5 }      // 주기를 절반으로
 * { "type": "swap_interval", "fixed_minutes": 1 }     // 주기를 1분으로 못박음
 * }</pre>
 *
 * <h2>고정값이 따로 있는 이유</h2>
 * <p>배율만 있으면 팀이 정한 주기에 따라 결과가 제각각이다. 주기를 2분으로 둔 팀은 1분이
 * 되지만 30분으로 둔 팀은 15분이라, 같은 증강인데 체감이 전혀 다르다. 실버 「본진이 바뀐다」
 * 처럼 "짧은 주기로 계속 흔들린다"가 핵심인 증강은 <b>결과값 자체를 못박아야</b> 뜻이 산다.
 *
 * <p>둘을 같이 적으면 고정값이 이긴다. 고정값이 있으면 배율은 읽지 않는다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "얼마로 만들 것인가"만 들고 있는 자료 그릇이다. 실제로 다음 교환까지 남은 틱을
 * 고쳐 쓰는 일은 {@link com.sharedfate.sync.PositionSwapManager} 가 맡고, 팀이 이 효과를
 * 갖고 있는지 묻는 자리는 {@link com.sharedfate.perk.PerkSwapRules} 다.
 *
 * <h2>왜 {@code TeamState} 를 고치지 않는가</h2>
 * <p>{@code positionSwapIntervalTicks} 는 {@code /shareteam} 명령이 정한 값이고 세이브에 그대로
 * 들어간다. 증강 값을 거기에 직접 먹이면 증강을 잃었을 때 명령으로 정한 주기가 사라진다.
 * 그래서 증강은 교환이 끝난 직후 <b>남은 틱</b>에만 관여한다.
 */
public final class SwapIntervalEffect implements PerkEffect {
	/** 받아들이는 배율 범위. 0 을 허용하면 매 틱 교환이 되므로 하한을 둔다. */
	static final double MIN_MULTIPLIER = 0.1;
	static final double MAX_MULTIPLIER = 10.0;
	/** 고정 주기로 받아들이는 분 범위. {@code /shareteam swap on} 과 같은 범위다. */
	static final int MIN_FIXED_MINUTES = 1;
	static final int MAX_FIXED_MINUTES = 120;
	/** 고정값을 적지 않았을 때. */
	public static final int NO_FIXED_MINUTES = 0;

	private final double multiplier;
	private final int fixedMinutes;

	public SwapIntervalEffect(double multiplier, int fixedMinutes) {
		this.multiplier = multiplier;
		this.fixedMinutes = fixedMinutes;
	}

	/** JSON에서 만든다. 값이 없거나 범위를 벗어나면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		String fixedKey = json.has("fixed_minutes") ? "fixed_minutes" : "fixedMinutes";
		if (json.has(fixedKey)) {
			Double minutes = PerkEffectType.readDouble(json, fixedKey);
			if (minutes == null || minutes < MIN_FIXED_MINUTES || minutes > MAX_FIXED_MINUTES) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: swap_interval 의 {} 값이 범위를 벗어났습니다 ({})",
						perkId, fixedKey, minutes);
				return null;
			}
			return new SwapIntervalEffect(1.0, (int) Math.round(minutes));
		}

		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: swap_interval 에 multiplier 도 fixed_minutes 도 올바르게 없습니다 ({})",
					perkId, multiplier);
			return null;
		}
		return new SwapIntervalEffect(multiplier, NO_FIXED_MINUTES);
	}

	public double multiplier() {
		return multiplier;
	}

	/** 못박을 주기(분). 고정하지 않으면 {@link #NO_FIXED_MINUTES}. */
	public int fixedMinutes() {
		return fixedMinutes;
	}

	public boolean hasFixedMinutes() {
		return fixedMinutes >= MIN_FIXED_MINUTES;
	}
}
