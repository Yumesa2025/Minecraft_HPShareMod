package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 팀원이 5초 안에 캔 것과 같은 종류의 블록을 캐면 둘 다 성급함 III 을 5초 받는다.
 *
 * <p>정의는 {@code { "type": "paired_mining" }} 하나뿐이고 필드가 없다. 실버 「공명」이 쓴다.
 * 기록 유효 시간(5초)·성급함 등급(III)·지속시간(5초) 전부 값이 이미 확정돼 굴려 보고 조정하는
 * 단계라, 지금은 코드 상수로 고정한다.
 *
 * <h2>거리 조건이 없다</h2>
 * <p>예전에는 "16칸 안"이라는 조건이 있었고, 아무도 16칸 안에 없으면 채굴 속도가 15% 줄어드는
 * 디메리트가 따로 붙어 있었다. 둘 다 없앴다. 팀원이 <b>어디에 있든</b> — 다른 차원이어도 —
 * 5초 안에 같은 종류의 블록을 캐면 발동한다.
 * <ul>
 *   <li>공유 인벤토리를 쓰는 팀은 자원을 나눠 캐는 것이 정석인데, 거리 조건은 그 정석을 벌했다.
 *       둘이 붙어서 같은 광맥을 파야만 이득이라면 팀이 흩어질 이유가 사라진다.</li>
 *   <li>디메리트 쪽은 더 나빴다. 혼자 탐험하는 동안 계속 −15% 가 걸려 있어, 이 증강을 고르면
 *       평소 채굴이 느려지는 것으로 체감됐다. 얻는 것보다 잃는 것이 눈에 띄는 증강은 고를 이유가
 *       없다.</li>
 * </ul>
 * <p>대신 성급함을 I 에서 III 으로 올려, 짝이 성사됐을 때의 보상이 분명히 느껴지게 했다.
 *
 * <h2>왜 {@code proximity} 로 안 되는가</h2>
 * <p>거리 조건이 사라진 지금은 이유가 하나로 줄었다. {@code proximity} 의 주기적 조회는 그 순간
 * 누가 무슨 블록을 캐고 있었는지에 대해 아무것도 모른다. 그 정보는
 * {@code PlayerBlockBreakEvents.AFTER} 사건이 일어나는 순간에만 존재하므로, 짝 판정은 사건
 * 기반일 수밖에 없다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link SwapBlockEffect}와 같은 이유다. {@link PerkEffect#apply}로 팀원에게 미리 붙여 둘
 * 것이 없다 — 성급함은 사건이 있을 때만 걸기 때문이다. 실제로 캔 순간의 짝 판정과 성급함
 * 부여는 {@link com.sharedfate.perk.PerkResonantMining}이 맡는다.
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
