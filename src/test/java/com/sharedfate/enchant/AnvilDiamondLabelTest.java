package com.sharedfate.enchant;

import com.sharedfate.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 모루 화면의 비용 라벨이 레벨이 아니라 다이아몬드를 말하는지 확인한다.
 *
 * <p>화면 코드({@code AnvilScreenMixin})는 {@code src/client} 에 있어 시험 소스셋이 보지
 * 못한다. 그래서 화면 Mixin 은 이 순수 함수를 부르기만 하고, 확인은 여기서 한다.
 * 대상이 아직 거기 있는지는 {@link AnvilMenuTargetTest} 가 따로 못박는다.
 */
class AnvilDiamondLabelTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * 바닐라가 넣는 숫자는 {@code AnvilMenu.getCost()} 이고, 그 값은
	 * {@code AnvilMenuDiamondMixin} 이 이미 다이아몬드 개수로 바꿔 두었다.
	 */
	@Test
	void 마법부여_비용_줄이_다이아몬드_개수로_바뀐다() {
		MutableComponent vanilla = Component.translatable(
				AnvilDiamondLabel.REPAIR_COST, AnvilDiamondCost.DIAMONDS_PER_REPAIR);

		assertEquals("다이아몬드 10개", AnvilDiamondLabel.rewrite(vanilla).getString());
	}

	/** 개수를 바꾸면 라벨도 따라간다 — 코드에 10을 두 번 적어 두지 않았다. */
	@Test
	void 인자로_온_개수를_그대로_적는다() {
		assertEquals("다이아몬드 3개", AnvilDiamondLabel.rewrite(
				Component.translatable(AnvilDiamondLabel.REPAIR_COST, 3)).getString());
	}

	/** 인자를 읽지 못하면 실제로 걷는 개수로 물러난다. 거짓말은 하지 않는다. */
	@Test
	void 인자가_없으면_실제로_걷는_개수를_적는다() {
		assertEquals("다이아몬드 " + AnvilDiamondCost.DIAMONDS_PER_REPAIR + "개",
				AnvilDiamondLabel.rewrite(
						Component.translatable(AnvilDiamondLabel.REPAIR_COST)).getString());
	}

	/**
	 * 바닐라는 색을 글월이 아니라 그릴 때 따로 넘긴다. 여기서 꾸밈을 얹으면 다이아몬드가
	 * 모자라 빨갛게 되는 판정을 덮어쓴다.
	 */
	@Test
	void 색을_담지_않는다() {
		MutableComponent rewritten = AnvilDiamondLabel.rewrite(
				Component.translatable(AnvilDiamondLabel.REPAIR_COST, 10));

		assertNull(rewritten.getStyle().getColor(), "색은 화면이 정한다");
	}

	/** 「비용이 너무 비쌉니다」를 비롯한 다른 글월은 손대지 않는다. */
	@Test
	void 다른_글월은_그대로_돌려준다() {
		MutableComponent tooExpensive = Component.translatable("container.repair.expensive");
		MutableComponent plain = Component.literal("그냥 글월");

		assertSame(tooExpensive, AnvilDiamondLabel.rewrite(tooExpensive));
		assertSame(plain, AnvilDiamondLabel.rewrite(plain));
	}
}
