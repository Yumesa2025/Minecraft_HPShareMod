package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;

/**
 * 오버월드의 시각을 한 자리에 붙들어 둔다.
 *
 * <pre>{@code
 * { "type": "time_lock", "time": 18000 }
 * }</pre>
 *
 * <p>{@code time} 은 하루 24000틱 기준의 시각이다. 0 이 아침, 6000 이 정오, 13000 이 밤,
 * 18000 이 자정이다. 이 숫자는 26.2 의 {@code data/minecraft/timeline/day.json} 에 적힌
 * {@code period_ticks} 24000 과 {@code time_markers} 에서 그대로 확인한 값이다. 골드
 * 달빛이면 충분해가 {@code status_effect}(야간 투시)와 짝지어 쓰는 대가다.
 *
 * <h2>오버월드에만 건다</h2>
 * <p>26.2 의 시각은 레벨이 아니라 <b>서버 전역의 시계</b>({@code minecraft:overworld},
 * {@code minecraft:the_end})가 들고 있고, 차원 정의의 {@code default_clock} 이 어느 시계를
 * 볼지 정한다. 네더에는 {@code default_clock} 이 아예 없고 엔드는 별도 시계를 쓰므로,
 * {@code minecraft:overworld} 시계 하나만 건드리면 "오버월드에만 건다"가 그대로 성립한다.
 * 그 판단과 실제 조작은 {@link com.sharedfate.perk.PerkWorldRules} 가 맡는다.
 *
 * <h2>게임룰을 끄지 않는다</h2>
 * <p>{@code doDaylightCycle} 을 끄면 서버 설정에 흔적이 남아, 증강을 잃거나 서버가 죽었을 때
 * 원래 값으로 되돌릴 방법이 없다. 사용자가 {@code /gamerule} 로 정해 둔 값을 덮어쓰는 것도
 * 안 된다. 그래서 게임룰은 손대지 않고 시계를 주기적으로 제자리에 돌려놓는 방식을 쓴다.
 * 증강을 잃으면 그 되돌리기가 멈추고, 그 즉시 시간이 다시 흐른다. 되돌릴 상태가 없다.
 */
public final class TimeLockEffect implements PerkEffect {
	/** 하루 길이. {@code data/minecraft/timeline/day.json} 의 {@code period_ticks} 와 같다. */
	public static final int DAY_LENGTH_TICKS = 24000;

	/** {@code time} 필드가 아예 없을 때 쓰는 표시. 실제 시각이 될 수 없는 값이어야 한다. */
	private static final int MISSING = -1;

	private final int time;

	public TimeLockEffect(int time) {
		this.time = time;
	}

	/**
	 * JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null.
	 *
	 * <p>{@code time} 은 반드시 적어야 한다. 기본값을 정해 두면 오타로 필드가 빠진 정의가 조용히
	 * 엉뚱한 시각으로 세계를 얼려 버린다. 범위는 0 이상 {@value #DAY_LENGTH_TICKS} 미만이다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		if (index < 0 || index >= DamageTakenFromEffect.MAX_TOP_LEVEL_INDEX) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: time_lock 은 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}

		int time = PerkEffectType.readInt(json, "time", MISSING);
		if (time < 0 || time >= DAY_LENGTH_TICKS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: time_lock 의 time 이 없거나 0~{} 범위를 벗어났습니다 ({})",
					perkId, DAY_LENGTH_TICKS - 1, time);
			return null;
		}
		return new TimeLockEffect(time);
	}

	/** 붙들어 둘 시각. 0 이상 {@value #DAY_LENGTH_TICKS} 미만이다. */
	public int time() {
		return time;
	}
}
