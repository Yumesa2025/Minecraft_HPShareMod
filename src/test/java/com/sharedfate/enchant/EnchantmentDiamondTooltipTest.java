package com.sharedfate.enchant;

import com.sharedfate.TestBootstrap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 인챈트 칸 툴팁이 레벨 대신 다이아몬드를 말하는지 확인합니다.
 *
 * <p>화면 코드는 {@code src/client} 에 있어 시험 소스셋이 보지 못합니다. 그래서 화면
 * Mixin 은 이 순수 함수를 부르기만 하고, 확인은 여기서 합니다.
 */
class EnchantmentDiamondTooltipTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 모자랄_때는_다이아몬드가_몇_개_필요한지_빨갛게_말한다() {
		Component line = Component
				.translatable("container.enchant.level.requirement", 5)
				.withStyle(ChatFormatting.RED);

		Component rewritten = EnchantmentDiamondTooltip.rewriteLine(line);

		assertEquals("다이아몬드 5개가 필요합니다", rewritten.getString());
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), rewritten.getStyle().getColor());
	}

	@Test
	void 소모하는_레벨을_알리던_줄이_다이아몬드_개수로_바뀐다() {
		assertEquals("다이아몬드 5개",
				EnchantmentDiamondTooltip.rewriteLine(
						Component.translatable("container.enchant.level.one")).getString());
		assertEquals("다이아몬드 5개",
				EnchantmentDiamondTooltip.rewriteLine(
						Component.translatable("container.enchant.level.many", 3)).getString());
	}

	@Test
	void 청금석_줄과_인챈트_힌트는_건드리지_않는다() {
		Component lapis = Component.translatable("container.enchant.lapis.many", 2);
		Component clue = Component.translatable("container.enchant.clue", "날카로움 III");

		assertSame(lapis, EnchantmentDiamondTooltip.rewriteLine(lapis));
		assertSame(clue, EnchantmentDiamondTooltip.rewriteLine(clue));
	}

	@Test
	void 줄_순서를_지키면서_해당하는_줄만_갈아_끼운다() {
		List<Component> lines = List.of(
				Component.translatable("container.enchant.clue", "내구성 II"),
				Component.empty(),
				Component.translatable("container.enchant.lapis.many", 2),
				Component.translatable("container.enchant.level.many", 2));

		List<Component> rewritten = EnchantmentDiamondTooltip.rewrite(lines);

		assertEquals(4, rewritten.size());
		assertSame(lines.get(0), rewritten.get(0));
		assertSame(lines.get(2), rewritten.get(2));
		assertEquals("다이아몬드 5개", rewritten.get(3).getString());
	}
}
