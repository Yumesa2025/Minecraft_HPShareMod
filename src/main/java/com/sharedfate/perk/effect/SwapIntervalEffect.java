package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 팀원 위치 교환 주기에 배율을 건다.
 *
 * <p>예: {@code { "type": "swap_interval", "multiplier": 0.5 }} 는 교환 주기를 절반으로 줄인다.
 * 실버 「본진이 바뀐다」가 저항과 짝지어 쓰는 대가다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "얼마를 곱할 것인가"만 들고 있는 자료 그릇이다. 실제로 다음 교환까지 남은 틱을
 * 고쳐 쓰는 일은 {@link com.sharedfate.sync.PositionSwapManager} 가 맡고, 팀이 이 효과를
 * 갖고 있는지 묻는 자리는 {@link com.sharedfate.perk.PerkSwapRules} 다.
 * {@code mob_health} 와 {@link com.sharedfate.perk.MobPerkModifiers} 의 관계와 같은 구도다.
 *
 * <h2>왜 {@code TeamState} 를 고치지 않는가</h2>
 * <p>{@code positionSwapIntervalTicks} 는 {@code /shareteam} 명령이 정한 값이고 세이브에 그대로
 * 들어간다. 증강 배율을 그 값에 직접 먹이면 증강을 잃었을 때 명령으로 정한 주기가 사라진다.
 * 그래서 배율은 교환이 끝난 직후 <b>남은 틱</b>에만 먹인다. 명령이 정한 주기는 손대지 않는다.
 */
public final class SwapIntervalEffect implements PerkEffect {
	/** 받아들이는 배율 범위. 0 을 허용하면 매 틱 교환이 되므로 하한을 둔다. */
	static final double MIN_MULTIPLIER = 0.1;
	static final double MAX_MULTIPLIER = 10.0;

	private final double multiplier;

	public SwapIntervalEffect(double multiplier) {
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 배율이 없거나 범위를 벗어나면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: swap_interval 의 multiplier 값이 없거나 범위를 벗어났습니다 ({})",
					perkId, multiplier);
			return null;
		}
		return new SwapIntervalEffect(multiplier);
	}

	public double multiplier() {
		return multiplier;
	}
}
