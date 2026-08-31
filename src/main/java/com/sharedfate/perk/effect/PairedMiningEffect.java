package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 팀원과 16칸 안에서 같은 종류 블록을 캐면 둘 다 성급함 I 을 5초 받는다. 아무도 16칸 안에
 * 없으면 채굴 속도가 15% 줄어든다.
 *
 * <p>정의는 {@code { "type": "paired_mining" }} 하나뿐이고 필드가 없다. 실버 「공명」이 쓴다.
 * 거리(16칸)·기록 유효 시간(5초)·성급함 등급·지속시간(5초)·혼자일 때 배율(−15%) 전부 값이
 * 이미 확정돼 굴려 보고 조정하는 단계라, 지금은 코드 상수로 고정한다.
 *
 * <h2>왜 {@code proximity} 로 안 되는가</h2>
 * <p>{@code proximity}는 "팀 전원이 거리 안에 있는가"(전체 합의)만 보고, 매 판정마다 짧은
 * 지속시간을 다시 얹는 방식이다. 공명이 필요로 하는 것은 그것과 성격이 다르다.
 * <ul>
 *   <li><b>전원 합의가 아니라 쌍(pair) 단위다.</b> "나와 가장 가까운 팀원"이 기준이라 3인 이상
 *       팀에서는 전원 조건과 답이 갈린다.</li>
 *   <li><b>거리만이 아니라 "무엇을 캤는가"까지 봐야 한다.</b> {@code proximity}의 주기적 거리
 *       조회는 그 순간 누가 무슨 블록을 캐고 있었는지에 대해 아무것도 모른다. 그 정보는
 *       {@code PlayerBlockBreakEvents.AFTER} 사건이 일어나는 순간에만 존재한다.</li>
 * </ul>
 * <p>그래서 "짝 성사"는 이벤트 기반(블록을 캘 때마다 최근 기록을 비교)으로, "혼자 페널티"는
 * {@code proximity}처럼 주기적 거리 확인으로 만들었다 — 결과적으로 사건 훅과 주기적 폴링을
 * 하나의 새 타입 안에서 같이 쓴다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link SwapBlockEffect}와 같은 이유다. {@link PerkEffect#apply}로 팀원에게 미리 붙여 둘
 * 것이 없다 — 성급함은 사건이 있을 때만, 채굴 속도 페널티는 매초 다시 판단해서 걸기 때문이다.
 * 실제로 캔 순간의 짝 판정과 성급함 부여는 {@link com.sharedfate.perk.PerkBlockBreaks}가,
 * 매초 거리 확인과 페널티 적용은 {@link com.sharedfate.perk.PerkResonantMining}이 맡는다.
 */
public final class PairedMiningEffect implements PerkEffect {
	/** 짝 성사 판정 거리(블록). */
	public static final double DISTANCE = 16.0;
	/** 최근 캔 블록 기록이 유효한 시간(틱). 5초. */
	public static final int MEMORY_TICKS = 100;
	/** 짝이 성사됐을 때 거는 성급함 등급. I 이다(amplifier 0). */
	public static final int HASTE_AMPLIFIER = 0;
	/** 짝이 성사됐을 때 성급함이 유지되는 시간(틱). 5초. */
	public static final int HASTE_TICKS = 100;
	/** 16칸 안에 아무도 없을 때 채굴 속도에 곱할 배율. −15%. */
	public static final double SOLO_PENALTY_MULTIPLIER = -0.15;

	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final PairedMiningEffect INSTANCE = new PairedMiningEffect();

	private PairedMiningEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
