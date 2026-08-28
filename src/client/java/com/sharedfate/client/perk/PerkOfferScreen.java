package com.sharedfate.client.perk;

import com.sharedfate.net.PerkChoiceC2SPayload;
import com.sharedfate.net.PerkOfferPayload;
import com.sharedfate.perk.PerkRarity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 증강 후보를 카드 형태로 가로 배치해 보여주는 화면.
 * canChoose 가 false 면 클릭이 막힌 관전 모드로 동작한다.
 */
public class PerkOfferScreen extends Screen {
	private static final int COLOR_SILVER = 0xFFC0C6CC;
	private static final int COLOR_GOLD = 0xFFFFC63A;
	private static final int COLOR_PLATINUM = 0xFF5FE0D8;

	private static final int CARD_BACKGROUND = 0xE0121218;
	private static final int CARD_BACKGROUND_HOVER = 0xF01E2233;
	private static final int SEPARATOR = 0x60FFFFFF;
	private static final int TEXT_MAIN = 0xFFFFFFFF;
	private static final int TEXT_SUB = 0xFFC0C0C0;
	private static final int TEXT_HINT = 0xFF909090;
	private static final int TEXT_SPECTATE = 0xFFFFD24A;

	private static final int PREFERRED_CARD_WIDTH = 116;
	private static final int MIN_CARD_WIDTH = 56;
	private static final int CARD_GAP = 8;
	private static final int SCREEN_MARGIN = 8;
	private static final int CARD_PADDING = 6;
	/** 이름·등급과 설명 사이 구분선이 차지하는 세로 공간(위 여백 3 + 선 1 + 아래 여백 4). */
	private static final int SEPARATOR_BLOCK_HEIGHT = 8;

	private final int milestone;
	private final boolean canChoose;
	private final List<PerkOfferPayload.PerkOption> options;
	private final List<Card> cards = new ArrayList<>();

	private int titleY;
	private int subtitleY;
	private int cardWidth;
	private int cardHeight;
	private int cardTop;
	private int firstCardLeft;

	public PerkOfferScreen(PerkOfferPayload payload) {
		super(Component.literal("증강 선택"));
		this.milestone = payload.milestone();
		this.canChoose = payload.canChoose();
		this.options = payload.options() == null
				? List.of() : List.copyOf(payload.options());
	}

	@Override
	protected void init() {
		// GUI 배율이 커서 화면이 좁아지면 카드 폭·높이를 줄여 화면 밖으로 나가지 않게 한다.
		titleY = this.height < 170 ? 8 : 16;
		subtitleY = titleY + 14;
		int headerBottom = subtitleY + 12;

		cards.clear();
		int count = Math.max(1, options.size());
		int available = Math.max(MIN_CARD_WIDTH, this.width - SCREEN_MARGIN * 2);
		int fitted = (available - CARD_GAP * (count - 1)) / count;
		cardWidth = Math.max(MIN_CARD_WIDTH, Math.min(PREFERRED_CARD_WIDTH, fitted));

		int innerWidth = Math.max(8, cardWidth - CARD_PADDING * 2);
		int neededHeight = 40;
		for (PerkOfferPayload.PerkOption option : options) {
			Card card = Card.of(this.font, option, innerWidth);
			cards.add(card);
			neededHeight = Math.max(neededHeight, card.height(this.font));
		}

		int footerTop = Math.max(headerBottom + 46, this.height - 22);
		cardHeight = Math.max(40, Math.min(neededHeight, footerTop - headerBottom - 6));
		cardTop = headerBottom + Math.max(0, (footerTop - headerBottom - cardHeight) / 2);

		int totalWidth = cardWidth * count + CARD_GAP * (count - 1);
		firstCardLeft = Math.max(2, (this.width - totalWidth) / 2);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		int centerX = this.width / 2;
		graphics.centeredText(this.font, this.title, centerX, titleY, TEXT_MAIN);
		graphics.centeredText(this.font, subtitle(), centerX, subtitleY,
				canChoose ? TEXT_SUB : TEXT_SPECTATE);

		for (int index = 0; index < cards.size(); index++) {
			renderCard(graphics, index, mouseX, mouseY);
		}

		graphics.centeredText(this.font, footerHint(), centerX, this.height - 14, TEXT_HINT);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void renderCard(GuiGraphicsExtractor graphics, int index, int mouseX, int mouseY) {
		Card card = cards.get(index);
		int left = cardLeft(index);
		int right = left + cardWidth;
		int bottom = cardTop + cardHeight;
		boolean hovered = canChoose && isInside(mouseX, mouseY, left, right, bottom);

		graphics.fill(left, cardTop, right, bottom,
				hovered ? CARD_BACKGROUND_HOVER : CARD_BACKGROUND);
		graphics.outline(left, cardTop, cardWidth, cardHeight, card.rarityColor());
		if (hovered) {
			// 마우스를 올린 카드는 테두리를 두 겹으로 그려 강조한다.
			graphics.outline(left + 1, cardTop + 1, cardWidth - 2, cardHeight - 2,
					card.rarityColor());
		}

		// 카드가 세로로 잘린 경우 글자가 카드 밖으로 삐져나오지 않게 자른다.
		graphics.enableScissor(left + 1, cardTop + 1, right - 1, bottom - 1);
		int textCenterX = left + cardWidth / 2;
		int y = cardTop + CARD_PADDING;
		for (FormattedCharSequence line : card.nameLines()) {
			graphics.centeredText(this.font, line, textCenterX, y, TEXT_MAIN);
			y += this.font.lineHeight;
		}
		graphics.centeredText(this.font, card.rarityLabel(), textCenterX, y, card.rarityColor());
		y += this.font.lineHeight + 3;
		graphics.fill(left + CARD_PADDING, y, right - CARD_PADDING, y + 1, SEPARATOR);
		y += 4;
		for (FormattedCharSequence line : card.descriptionLines()) {
			graphics.text(this.font, line, left + CARD_PADDING, y, TEXT_SUB);
			y += this.font.lineHeight;
		}
		graphics.disableScissor();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (canChoose && event.button() == 0) {
			for (int index = 0; index < cards.size(); index++) {
				int left = cardLeft(index);
				if (isInside(event.x(), event.y(), left, left + cardWidth,
						cardTop + cardHeight)) {
					choose(index);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** 선택을 서버로 보내고 화면을 닫는다. 검증은 서버가 다시 한다. */
	private void choose(int index) {
		PerkOfferPayload.PerkOption option = options.get(index);
		if (ClientPlayNetworking.canSend(PerkChoiceC2SPayload.TYPE)) {
			ClientPlayNetworking.send(new PerkChoiceC2SPayload(milestone, option.id()));
		}
		this.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		// 다른 팀원이 관전하는 동안에도 게임은 계속 돌아가야 한다.
		return false;
	}

	private int cardLeft(int index) {
		return firstCardLeft + index * (cardWidth + CARD_GAP);
	}

	private boolean isInside(double x, double y, int left, int right, int bottom) {
		return x >= left && x < right && y >= cardTop && y < bottom;
	}

	private Component subtitle() {
		if (canChoose) {
			return Component.literal(
					"공유 레벨 " + milestone + " 달성 · 증강 하나를 고르세요");
		}
		String chooser = PerkClientState.chooserName();
		if (chooser.isEmpty()) {
			chooser = "팀원";
		}
		return Component.literal(chooser + "님이 고르는 중입니다 (관전 중)");
	}

	private Component footerHint() {
		if (this.width < 320) {
			return Component.literal("ESC · /shareteam perk 로 다시 열기");
		}
		return Component.literal(
				"ESC로 닫아도 선택권은 남습니다 · /shareteam perk 로 다시 열 수 있습니다");
	}

	private static String rarityLabel(PerkRarity rarity) {
		return rarity == null ? PerkRarity.SILVER.displayName() : rarity.displayName();
	}

	private static int rarityColor(PerkRarity rarity) {
		if (rarity == null) {
			return COLOR_SILVER;
		}
		return switch (rarity) {
			case SILVER -> COLOR_SILVER;
			case GOLD -> COLOR_GOLD;
			case PLATINUM -> COLOR_PLATINUM;
		};
	}

	/** 화면 폭이 정해진 뒤 한 번 계산해 두는 카드 한 장의 표시 내용. */
	private record Card(List<FormattedCharSequence> nameLines,
			Component rarityLabel,
			int rarityColor,
			List<FormattedCharSequence> descriptionLines) {

		static Card of(Font font, PerkOfferPayload.PerkOption option, int innerWidth) {
			PerkRarity rarity = PerkRarity.fromId(option.rarity());
			return new Card(
					font.split(Component.literal(text(option.name())), innerWidth),
					Component.literal(PerkOfferScreen.rarityLabel(rarity)),
					PerkOfferScreen.rarityColor(rarity),
					font.split(Component.literal(text(option.description())), innerWidth));
		}

		int height(Font font) {
			int lines = nameLines.size() + descriptionLines.size() + 1;
			return CARD_PADDING * 2 + lines * font.lineHeight + SEPARATOR_BLOCK_HEIGHT;
		}

		private static String text(String value) {
			return value == null ? "" : value;
		}
	}
}
