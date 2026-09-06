package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 팀원이 5초 안에 캔 것과 같은 종류의 블록을 캐면 둘 다 성급함 III 을 5초 받는다.
 *
 * <p>정의는 {@code { "type": "paired_mining" }} 하나뿐이고 필드가 없다. 실버 「공명」이 쓴다.
 * 기록 유효 시간(5초)·성급함 등급(III)·지속시간(5초)은 전부 코드 상수로 고정돼 있다.
 *
 * <h2>거리 조건이 없다</h2>
 * <p>팀원이 <b>어디에 있든</b> — 다른 차원이어도 — 5초 안에 같은 종류의 블록을 캐면 발동한다.
 *
 * <p>{@code proximity} 로는 만들 수 없다. 그쪽의 주기적 조회는 그 순간 누가 무슨 블록을 캐고
 * 있었는지에 대해 아무것도 모른다. 그 정보는 {@code PlayerBlockBreakEvents.AFTER} 사건이
 * 일어나는 순간에만 존재하므로, 짝 판정은 사건 기반일 수밖에 없다.
 *
 * <p>{@link PerkEffect#apply}로 팀원에게 미리 붙여 둘 것이 없다 — 성급함은 사건이 있을 때만
 * 걸기 때문이다. 실제로 캔 순간의 짝 판정과 성급함 부여는
 * {@link com.sharedfate.perk.PerkResonantMining}이 맡는다.
 */
public final class PairedMiningEffect implements PerkEffect {
	/** 최근 캔 블록 기록이 유효한 시간(틱). 5초. */
	public static final int MEMORY_TICKS = 100;
	/** 짝이 성사됐을 때 거는 성급함 등급. III 이다(amplifier 2). */
	public static final int HASTE_AMPLIFIER = 2;
	/** 짝이 성사됐을 때 성급함이 유지되는 시간(틱). 5초. */
	public static final int HASTE_TICKS = 100;

	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final PairedMiningEffect INSTANCE = new PairedMiningEffect();

	private PairedMiningEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
