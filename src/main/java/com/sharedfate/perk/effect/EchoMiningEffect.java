package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 내가 캔 블록이 같은 차원의 가장 가까운 팀원 발밑에서도 한 번 더 캐진다. 대신 쓰는 도구의
 * 내구도가 2배로 닳는다(원래 소모 + 추가 1점).
 *
 * <p>정의는 {@code { "type": "echo_mining" }} 하나뿐이고 필드가 없다. 골드 「메아리 채굴」이 쓴다.
 *
 * <h2>확정된 세부 규칙</h2>
 * <ul>
 *   <li><b>대상은 발밑 블록 하나뿐이다.</b> 캔 것과 같은 종류인지는 따지지 않는다 — "메아리"는
 *       행동이 반복된다는 뜻이지 같은 자원이 나온다는 약속이 아니다.</li>
 *   <li><b>거리 제한은 없다.</b> 같은 차원이면 된다. "가장 가까운 팀원"이라는 조건 자체가
 *       이미 자연스러운 한도 역할을 한다.</li>
 *   <li><b>그 팀원의 청크가 로드돼 있지 않으면 아무 일도 하지 않는다.</b> 청크를 억지로
 *       불러오지 않는다.</li>
 *   <li><b>내 도구로 그 블록을 캘 자격이 없으면 메아리가 일어나지 않는다.</b>
 *       {@code breaker.hasCorrectToolForDrops(...)}로 판정한다. 도구가 안 맞는데도 팀원 발밑
 *       블록이 이유 없이 사라지는 것을 막기 위해서다.</li>
 * </ul>
 *
 * <h2>무한 연쇄를 막는 방법</h2>
 * <p>메아리로 캐는 블록은 {@code ServerLevel.removeBlock}로 지운다. {@code destroyBlock}이
 * 아니라 이 메서드를 쓰는 이유는, {@code PlayerBlockBreakEvents.AFTER}가 발화하는 자리가
 * {@code ServerPlayerGameMode.destroyBlock} 안(《PerkBlockBreaks》의 문서 참고)이기 때문이다.
 * {@code removeBlock}은 그 경로를 지나지 않으므로 이 사건이 자기 자신을 다시 부르는 고리가
 * 애초에 생기지 않는다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link SwapBlockEffect}와 같은 이유다. {@link PerkEffect#apply}로 팀원에게 붙일 것이
 * 없다. 실제로 메아리를 일으키고 도구를 추가로 닳게 하는 일은
 * {@link com.sharedfate.perk.PerkBlockBreaks}가 맡는다.
 */
public final class EchoMiningEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final EchoMiningEffect INSTANCE = new EchoMiningEffect();

	private EchoMiningEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
