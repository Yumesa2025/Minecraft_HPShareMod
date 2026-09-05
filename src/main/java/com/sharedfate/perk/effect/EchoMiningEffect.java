package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 블록을 캐면 그 주변 블록이 {@value #EXTRA_BLOCKS}개 더 캐진다. 대신 쓰는 도구의 내구도가
 * 2배로 닳는다(원래 소모 + 추가 1점).
 *
 * <p>정의는 {@code { "type": "echo_mining" }} 하나뿐이고 필드가 없다. 골드 「메아리 채굴」이 쓴다.
 *
 * <h2>세부 규칙</h2>
 * <ul>
 *   <li><b>방금 캔 자리를 둘러싼 26칸 중에서 고른다.</b> 캔 것과 같은 종류인지는 따지지 않는다 —
 *       "메아리"는 행동이 반복된다는 뜻이지 같은 자원이 나온다는 약속이 아니다.</li>
 *   <li><b>팀원(자신 포함)의 발밑 블록은 절대 캐지 않는다.</b> 아래에 따로 적었다.</li>
 *   <li><b>로드되지 않은 청크의 블록은 후보에서 빠진다.</b> 청크를 억지로 불러오지 않는다.</li>
 *   <li><b>내 도구로 그 블록을 캘 자격이 없으면 후보에서 빠진다.</b>
 *       {@code breaker.hasCorrectToolForDrops(...)}로 판정한다. 도구가 안 맞는데도 블록이 이유
 *       없이 사라지는 것을 막기 위해서다.</li>
 *   <li><b>내구도는 몇 개를 더 캐든 정확히 1점만 더 닳는다.</b> 그래야 합계가 바닐라의 2배로
 *       유지된다. 추가 파괴 개수를 늘렸다고 소모까지 따라 늘리면 도구가 순식간에 사라진다.</li>
 * </ul>
 *
 * <h2>발밑을 캐지 않는 이유</h2>
 * <p><b>이 규칙이 없으면 이 증강은 사람을 죽인다.</b> 서 있는 블록이 사라지면 그 자리에서
 * 그대로 떨어진다. 동굴 천장 근처나 용암 위, 공중에 다리를 놓고 건너는 중이라면 그 한 칸이
 * 곧 사망이고, 이 모드는 <b>체력을 공유</b>하므로 한 사람의 낙하사가 팀 전체의 체력을 깎는다.
 * 게다가 캔 사람이 의도한 일이 아니라서 — 발밑이 사라지는 쪽은 대개 <b>다른 팀원</b>이다 —
 * 왜 죽었는지 알 방법도 없다.
 *
 * <p>그래서 후보를 고르기 전에, 접속해 있고 같은 차원에 있는 팀원(캔 사람 자신 포함) 각각에
 * 대해 <b>그 사람이 서 있는 블록과 그 아래 한 칸</b>을 후보에서 뺀다. 두 칸을 모두 빼는 이유는
 * 마인크래프트에서 "발밑"이 상황에 따라 두 곳 중 하나이기 때문이다. 보통 서 있을 때 몸은
 * {@code blockPosition()} 칸에 있고 딛고 선 것은 그 아래 칸이지만, 반 블록·계단·눈처럼 높이가
 * 1보다 낮은 블록 위에서는 딛고 선 블록 자체가 {@code blockPosition()} 이 된다. 한쪽만 빼면
 * 나머지 한쪽이 사라져 같은 사고가 난다.
 *
 * <p>판정은 {@link com.sharedfate.perk.PerkBlockBreaks#isUnderFoot}에 좌표 계산만으로 떼어 두었다.
 * 이 규칙은 살아 있는 서버 없이도 반드시 시험할 수 있어야 하기 때문이다.
 *
 * <h2>「같은 종류만」이 필요하면 이 클래스가 아니다</h2>
 * <p>{@link SameKindMiningEffect}({@code same_kind_mining}) 가 따로 있다. 같은 26칸을 같은
 * 규칙으로 훑되 <b>방금 캔 것과 같은 종류만</b> 고른다. 이 클래스에 「같은 종류만」 옵션을
 * 더하지 않은 이유는, 여기가 필드 없는 홑 인스턴스({@link #INSTANCE})라서 옵션을 하나라도
 * 넣는 순간 그 구조가 깨지고, 「메아리 채굴」이 예전 그대로라는 보장이 <b>기본값 하나에만</b>
 * 걸리기 때문이다. 두 효과가 공유하는 것은 이웃을 훑는 계산뿐이고, 그쪽은
 * {@link com.sharedfate.perk.PerkBlockBreaks#neighborCandidates} 한 함수로 이미 합쳐져 있다.
 *
 * <p><b>둘을 같이 가지면 둘 다 발동한다.</b> 이쪽이 먼저 2칸을 캐고 그 뒤에 저쪽이 남은 칸을
 * 다시 훑는다. 자세한 것은 {@code PerkBlockBreaks.trySameKindMining} 에 적어 뒀다.
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
	/** 한 번 캘 때 덤으로 더 캐지는 블록 수. 후보가 모자라면 있는 만큼만 캔다. */
	public static final int EXTRA_BLOCKS = 2;

	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final EchoMiningEffect INSTANCE = new EchoMiningEffect();

	private EchoMiningEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
