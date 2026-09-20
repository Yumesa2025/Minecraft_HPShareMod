package com.sharedfate.enchant;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * 모루 화면의 비용 라벨을 <b>「다이아몬드 N개」</b>로 고쳐 쓴다.
 *
 * <p>{@link EnchantmentDiamondTooltip} 과 같은 모양이다 — 바닐라가 만든 글월을 받아
 * <b>번역 열쇠를 보고</b> 해당하는 것만 갈아 끼우는 순수 함수이고, 화면 Mixin 은 이것을
 * 부르기만 한다. 화면 코드는 {@code src/client} 에 있어 시험 소스셋이 보지 못하므로
 * 확인은 {@code AnvilDiamondLabelTest} 가 대신한다.
 *
 * <h2>왜 이 줄이 거짓말인가</h2>
 *
 * <p>{@code AnvilMenuDiamondMixin} 이 {@code AnvilMenu.getCost()} 를
 * 「결과가 있으면 {@value AnvilDiamondCost#DIAMONDS_PER_REPAIR}, 없으면 0」으로 바꿔 두었다.
 * 그 덕에 「비용이 너무 비쌉니다」는 사라졌지만, 바닐라 {@code AnvilScreen.extractLabels} 는
 * 그 숫자를 {@code container.repair.cost} (「마법부여 비용: %1$s」)에 그대로 꽂아 <b>경험치
 * 레벨처럼 보이는 초록 글씨</b>로 그린다. 사람이 그것을 보고 다이아몬드 값인 줄 모른다.
 *
 * <h2>왜 언어 파일이 아니라 Mixin 인가</h2>
 *
 * <p>{@code container.repair.cost} 는 바닐라 열쇠라 언어 파일로 덮을 수도 있다. 그러나
 * <b>덮은 언어에서만</b> 바뀐다 — 마인크래프트는 고른 언어에 열쇠가 있으면 영어로 물러나지
 * 않으므로, 우리가 적지 않은 언어를 쓰는 사람에게는 바닐라 문구가 그대로 뜬다. 그리고 이
 * 모드에는 애초에 {@code assets/sharedfate/lang} 이 없다 — 인챈트 탁자 쪽
 * ({@link EnchantmentDiamondTooltip})도 한국어 글월을 코드에 박아 두었다. 같은 길을 따른다.
 */
public final class AnvilDiamondLabel {
	/** 「마법부여 비용: %1$s」 — 26.2 {@code AnvilScreen.extractLabels} 가 쓰는 열쇠다. */
	public static final String REPAIR_COST = "container.repair.cost";

	private AnvilDiamondLabel() {
	}

	/**
	 * 비용 라벨을 고쳐 쓴다. 다른 글월({@code TOO_EXPENSIVE_TEXT} 따위)은 그대로 돌려준다.
	 *
	 * <p>적는 개수는 바닐라가 넣어 둔 인자에서 가져온다. 그 인자가 곧
	 * {@code AnvilMenu.getCost()} 이고, 그 값은 이미 다이아몬드 개수로 바뀌어 있다. 인자를
	 * 읽지 못하면 {@link AnvilDiamondCost#DIAMONDS_PER_REPAIR} 로 물러난다.
	 *
	 * <p><b>색은 건드리지 않는다.</b> 바닐라는 글월에 색을 담지 않고 그릴 때 따로 넘기므로,
	 * 꾸밈 없는 글월을 돌려주면 초록/빨강 판정이 그대로 산다.
	 */
	public static MutableComponent rewrite(MutableComponent line) {
		if (!(line.getContents() instanceof TranslatableContents contents)) {
			return line;
		}
		if (!REPAIR_COST.equals(contents.getKey())) {
			return line;
		}
		return costLabel(firstNumber(contents, AnvilDiamondCost.DIAMONDS_PER_REPAIR));
	}

	/** 「다이아몬드 N개」. */
	public static MutableComponent costLabel(int diamonds) {
		return Component.literal("다이아몬드 " + diamonds + "개");
	}

	private static int firstNumber(TranslatableContents contents, int fallback) {
		Object[] args = contents.getArgs();
		if (args.length > 0 && args[0] instanceof Number number) {
			return number.intValue();
		}
		return fallback;
	}
}
