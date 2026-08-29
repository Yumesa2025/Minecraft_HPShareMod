package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;

/**
 * 침대에서 잠들 수 없게 한다.
 *
 * <p>정의는 {@code { "type": "no_sleep" }} 하나뿐이고 필드가 없다. 실버 불면의 파수꾼이
 * {@code mob_damage} 와 짝지어 쓰는 대가다.
 *
 * <h2>막는 것은 눕는 순간 하나뿐이다</h2>
 * <p>침대를 놓고 부수는 것도, 부활 지점을 이미 정해 둔 사람이 그 지점에서 되살아나는 것도 그대로다.
 * 막히는 것은 <b>침대에 눕는 시도</b> 한 자리뿐이고, 실제로 막는 지점은
 * {@code EntitySleepEvents.ALLOW_SLEEPING} 이다. 지금 이 팀에 이 효과가 있는지 판단하는 것은
 * {@link com.sharedfate.perk.PerkWorldRules} 다.
 *
 * <h2>부활 지점을 정할 수 없는 것은 의도된 대가다</h2>
 * <p>바닐라는 침대에 <b>누워야</b> 부활 지점을 옮겨 준다. 눕지 못하면 부활 지점도 옮길 수 없으므로,
 * 이 증강을 든 팀은 월드 스폰이나 리스폰 앵커에 기대야 한다. 밤을 넘길 수 없다는 대가보다 이쪽이
 * 실제로는 더 무겁다는 점을 설명 문구에 함께 적어 두는 편이 좋다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 눕기 처리 한가운데서 "이 팀이 이
 * 효과를 갖고 있는가"만 물어보면 되므로, 이 클래스는 그 물음에 답하기 위한 표시로만 존재한다.
 * 상태가 없어 인스턴스를 나눠 써도 안전하다. {@link NoFoodHungerEffect} 와 같은 구도다.
 */
public final class NoSleepEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoSleepEffect INSTANCE = new NoSleepEffect();

	private NoSleepEffect() {
	}

	/**
	 * JSON에서 만든다. 읽을 필드는 없지만 놓인 자리는 본다.
	 *
	 * <p>{@link com.sharedfate.perk.PerkWorldRules} 는 증강의 최상위 효과만 훑으므로
	 * {@code periodic} 이나 {@code conditional} 안에 넣으면 조용히 아무 일도 하지 않는다.
	 * {@link DamageTakenFromEffect} 와 같은 기준으로 읽는 시점에 걸러 낸다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		if (index < 0 || index >= DamageTakenFromEffect.MAX_TOP_LEVEL_INDEX) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: no_sleep 은 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}
		return INSTANCE;
	}
}
