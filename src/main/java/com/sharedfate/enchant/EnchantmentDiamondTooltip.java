package com.sharedfate.enchant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

/**
 * 인챈트 칸 툴팁에서 <b>경험치 레벨</b> 이야기를 <b>다이아몬드</b> 이야기로 바꿉니다.
 *
 * <p>레벨을 더 이상 쓰지 않으므로 바닐라 툴팁의 두 줄이 거짓말이 됩니다. 화면 코드를
 * 통째로 다시 쓰는 대신, 바닐라가 만들어 둔 줄 목록을 받아 <b>번역 열쇠를 보고</b>
 * 해당하는 줄만 갈아 끼웁니다. 나머지 줄(인챈트 힌트, 청금석 요구량)은 그대로 둡니다.
 *
 * <p>화면이 아니라 여기에 둔 이유는 <b>시험할 수 있게</b> 하기 위해서입니다.
 * {@code src/client} 는 시험 소스셋이 보지 못합니다.
 */
public final class EnchantmentDiamondTooltip {
	/** 「필요 레벨: %s」 — 레벨이 모자랄 때 빨갛게 뜨는 줄. */
	private static final String LEVEL_REQUIREMENT = "container.enchant.level.requirement";
	/** 「인챈트 레벨 1」 — 소모하는 레벨을 알리는 줄. */
	private static final String LEVEL_ONE = "container.enchant.level.one";
	/** 「인챈트 레벨 %s」 — 위와 같되 둘 이상일 때. 인자가 칸 번호 + 1 입니다. */
	private static final String LEVEL_MANY = "container.enchant.level.many";

	private EnchantmentDiamondTooltip() {
	}

	public static List<Component> rewrite(List<Component> lines) {
		List<Component> rewritten = new ArrayList<>(lines.size());
		for (Component line : lines) {
			rewritten.add(rewriteLine(line));
		}
		return rewritten;
	}

	public static Component rewriteLine(Component line) {
		if (!(line.getContents() instanceof TranslatableContents contents)) {
			return line;
		}
		return switch (contents.getKey()) {
			// 바닐라가 이 줄에 넣는 숫자는 costs[칸] 인데, 화면 Mixin 이 그 배열을 이미
			// 다이아몬드 개수로 바꿔 두었으므로 그대로 읽으면 됩니다.
			case LEVEL_REQUIREMENT -> Component
					.literal("다이아몬드 " + firstNumber(contents, EnchantmentDiamondCost.DIAMONDS_PER_ENCHANT)
							+ "개가 필요합니다")
					.withStyle(ChatFormatting.RED);
			case LEVEL_ONE -> costLine(0);
			// 바닐라 인자는 「칸 번호 + 1」 입니다. 칸마다 값이 다를 때를 위해 되돌립니다.
			case LEVEL_MANY -> costLine(firstNumber(contents, 1) - 1);
			default -> line;
		};
	}

	private static Component costLine(int slot) {
		int clamped = Math.clamp(slot, 0, EnchantmentDiamondCost.SLOT_COUNT - 1);
		return Component.literal("다이아몬드 " + EnchantmentDiamondCost.forSlot(clamped) + "개")
				.withStyle(ChatFormatting.GRAY);
	}

	private static int firstNumber(TranslatableContents contents, int fallback) {
		Object[] args = contents.getArgs();
		if (args.length > 0 && args[0] instanceof Number number) {
			return number.intValue();
		}
		return fallback;
	}
}
