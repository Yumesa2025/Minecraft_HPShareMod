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
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 증강 후보를 카드 형태로 가로 배치해 보여주는 화면.
 *
 * <p>{@code canChoose} 가 false 면 클릭이 막힌 관전 모드로 동작한다.
 *
 * <p>여는 경로가 두 가지다.
 *
 * <ul>
 *   <li><b>강제 오픈</b>({@code forced}) — 서버가 시간을 멈추고 띄운 창이다. ESC 로 닫을 수 없고
 *       상단에 제한시간 카운트다운이 크게 뜬다. 닫는 책임은 서버에 있다
 *       ({@code PerkCloseOfferPayload}).</li>
 *   <li><b>직접 열기</b> — {@code /shareteam perk} 로 연 확인용 창이다. 시간도 멈추지 않았고
 *       마감도 없으므로 ESC 로 자유롭게 닫을 수 있다.</li>
 * </ul>
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
	private static final int TEXT_WAITING = 0xFF7FE07F;

	private static final int TIMER_CALM = 0xFFFFFFFF;
	private static final int TIMER_URGENT = 0xFFFF5555;
	private static final int TIMER_BAR_BACKGROUND = 0x80202028;
	private static final int TIMER_BAR_FILL = 0xFFFFC63A;
	private static final int TIMER_BAR_FILL_URGENT = 0xFFFF5555;

	/** 카운트다운 글자를 몇 배로 키울지. */
	private static final int TIMER_SCALE = 2;
	/** 이 초 이하로 남으면 빨갛게 바뀐다. */
	private static final int URGENT_SECONDS = 10;
	/**
	 * 마감이 지난 뒤 ESC 를 다시 허용하기까지의 유예(ms).
	 *
	 * <p>강제 오픈된 창을 닫는 것은 서버의 몫이지만, 닫기 지시가 오지 못하는 상황
	 * (패킷 유실·접속 이상)에서 플레이어가 화면에 영영 갇히면 안 된다. 서버는 이미
	 * 제한시간에 시간을 녹였을 시점이므로 이 뒤로는 클라이언트가 스스로 빠져나갈 수 있다.
	 */
	private static final long ESCAPE_GRACE_MILLIS = 5000L;
	private static final int TIMER_BAR_HEIGHT = 3;
	private static final int MILLIS_PER_TICK = 50;

	private static final int PREFERRED_CARD_WIDTH = 116;
	private static final int MIN_CARD_WIDTH = 56;
	private static final int CARD_GAP = 8;
	private static final int SCREEN_MARGIN = 8;
	private static final int CARD_PADDING = 6;
	/** 이름·등급과 설명 사이 구분선이 차지하는 세로 공간(위 여백 3 + 선 1 + 아래 여백 4). */
	private static final int SEPARATOR_BLOCK_HEIGHT = 8;

	private final int milestone;
	private final boolean canChoose;
	private final boolean forced;
	/**
	 * 제한시간 전체 길이(ms). 남은 시간 막대의 분모다. 마감이 없으면 0.
	 */
	private final long totalMillis;
	/**
	 * 마감 시각. 서버가 보낸 "남은 틱"을 받는 순간의 클라이언트 시계에 더해 둔 값이다.
	 *
	 * <p>서버 시계와 클라이언트 시계를 맞출 필요가 없고, 화면이 몇 프레임 밀려도 표시가 어긋나지
	 * 않는다. 어차피 실제 마감 판정은 서버가 하므로 여기 값은 보여 주기 위한 것뿐이다.
	 */
	private final long deadlineMillis;
	private final List<PerkOfferPayload.PerkOption> options;
	private final List<Card> cards = new ArrayList<>();

	/** 강제 오픈에서 선택을 보낸 뒤. 서버가 창을 닫아 줄 때까지 클릭을 막는다. */
	private boolean choiceSent;

	private int timerY;
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
		this.forced = payload.forced();
		this.totalMillis = payload.hasDeadline()
				? (long) payload.remainingTicks() * MILLIS_PER_TICK : 0L;
		this.deadlineMillis = payload.hasDeadline()
				? System.currentTimeMillis() + this.totalMillis : 0L;
		this.options = payload.options() == null
				? List.of() : List.copyOf(payload.options());
	}

	/** 이 창이 다루는 레벨 구간. 서버의 닫기 지시가 맞는 창인지 가릴 때 쓴다. */
	public int milestone() {
		return milestone;
	}

	/** 서버가 강제로 띄운 창인지. */
	public boolean forced() {
		return forced;
	}

	/** 서버의 닫기 지시로 창을 닫는다. 강제 오픈이든 아니든 그대로 닫힌다. */
	public void closeFromServer() {
		super.onClose();
	}

	@Override
	protected void init() {
		// GUI 배율이 커서 화면이 좁아지면 카드 폭·높이를 줄여 화면 밖으로 나가지 않게 한다.
		int top = this.height < 170 ? 6 : 12;
		timerY = top;
		// 카운트다운은 TIMER_SCALE 배로 그리므로 그만큼 세로 자리를 먼저 비워 둔다.
		int timerBlock = hasDeadline()
				? this.font.lineHeight * TIMER_SCALE + TIMER_BAR_HEIGHT + 6 : 0;
		titleY = top + timerBlock + (this.height < 170 ? 2 : 4);
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
		if (hasDeadline()) {
			renderCountdown(graphics, centerX);
		}
		graphics.centeredText(this.font, this.title, centerX, titleY, TEXT_MAIN);
		graphics.centeredText(this.font, subtitle(), centerX, subtitleY, subtitleColor());

		for (int index = 0; index < cards.size(); index++) {
			renderCard(graphics, index, mouseX, mouseY);
		}

		graphics.centeredText(this.font, footerHint(), centerX, this.height - 14, TEXT_HINT);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/**
	 * 남은 시간을 크게 그린다.
	 *
	 * <p>글자 확대는 {@code pose()} 에 배율을 쌓아서 한다. 텍스트는 그려질 때 현재 pose 를 그대로
	 * 복사해 가므로 push/scale/popMatrix 사이에서 그리면 확대된 상태로 남는다.
	 */
	private void renderCountdown(GuiGraphicsExtractor graphics, int centerX) {
		int remaining = remainingSeconds();
		boolean urgent = remaining <= URGENT_SECONDS;
		Component label = remaining > 0
				? Component.literal("남은 시간 " + remaining + "초")
				: Component.literal("시간 초과");

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, timerY);
		pose.scale(TIMER_SCALE, TIMER_SCALE);
		graphics.centeredText(this.font, label, 0, 0, urgent ? TIMER_URGENT : TIMER_CALM);
		pose.popMatrix();

		int barTop = timerY + this.font.lineHeight * TIMER_SCALE + 3;
		int barWidth = Math.max(60, Math.min(240, this.width - SCREEN_MARGIN * 2));
		int barLeft = centerX - barWidth / 2;
		graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + TIMER_BAR_HEIGHT,
				TIMER_BAR_BACKGROUND);
		int filled = (int) (barWidth * remainingFraction());
		if (filled > 0) {
			graphics.fill(barLeft, barTop, barLeft + filled, barTop + TIMER_BAR_HEIGHT,
					urgent ? TIMER_BAR_FILL_URGENT : TIMER_BAR_FILL);
		}
	}

	private void renderCard(GuiGraphicsExtractor graphics, int index, int mouseX, int mouseY) {
		Card card = cards.get(index);
		int left = cardLeft(index);
		int right = left + cardWidth;
		int bottom = cardTop + cardHeight;
		boolean hovered = clickable() && isInside(mouseX, mouseY, left, right, bottom);

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
		if (clickable() && event.button() == 0) {
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

	/**
	 * 선택을 서버로 보낸다. 검증은 서버가 다시 한다.
	 *
	 * <p>강제 오픈이면 여기서 창을 닫지 않는다. 서버가 선택을 거절했는데 창만 사라지면 시간이
	 * 멈춘 채로 아무것도 할 수 없게 된다. 닫는 것은 서버의 몫이다.
	 */
	private void choose(int index) {
		PerkOfferPayload.PerkOption option = options.get(index);
		if (ClientPlayNetworking.canSend(PerkChoiceC2SPayload.TYPE)) {
			ClientPlayNetworking.send(new PerkChoiceC2SPayload(milestone, option.id()));
		}
		if (forced) {
			choiceSent = true;
			return;
		}
		this.onClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		// 강제로 띄운 창은 ESC 로 닫을 수 없다. /shareteam perk 로 직접 연 창은 닫힌다.
		// 마감이 한참 지나도록 서버가 닫아 주지 않으면 그때는 열어 준다. 갇히는 것보다 낫다.
		return !forced || escapable();
	}

	/** 서버가 닫아 주지 못했을 때 스스로 빠져나갈 수 있는 시점인지. */
	private boolean escapable() {
		if (deadlineMillis <= 0L) {
			// 마감을 알 수 없는 강제 창이다. 언제 풀릴지 알 수 없으니 잠그지 않는다.
			return true;
		}
		return System.currentTimeMillis() > deadlineMillis + ESCAPE_GRACE_MILLIS;
	}

	@Override
	public boolean isPauseScreen() {
		// 다른 팀원이 관전하는 동안에도 게임은 계속 돌아가야 한다.
		return false;
	}

	private boolean clickable() {
		return canChoose && !choiceSent;
	}

	private boolean hasDeadline() {
		return forced && deadlineMillis > 0L;
	}

	/** 마감까지 남은 초. 이미 지났으면 0. */
	private int remainingSeconds() {
		long left = deadlineMillis - System.currentTimeMillis();
		if (left <= 0L) {
			return 0;
		}
		return (int) ((left + 999L) / 1000L);
	}

	/** 남은 시간 막대의 채움 비율 0.0~1.0. */
	private float remainingFraction() {
		if (totalMillis <= 0L) {
			return 0.0F;
		}
		long left = deadlineMillis - System.currentTimeMillis();
		if (left <= 0L) {
			return 0.0F;
		}
		return Math.min(1.0F, (float) left / (float) totalMillis);
	}

	private int cardLeft(int index) {
		return firstCardLeft + index * (cardWidth + CARD_GAP);
	}

	private boolean isInside(double x, double y, int left, int right, int bottom) {
		return x >= left && x < right && y >= cardTop && y < bottom;
	}

	private int subtitleColor() {
		if (choiceSent) {
			return TEXT_WAITING;
		}
		return canChoose ? TEXT_SUB : TEXT_SPECTATE;
	}

	private Component subtitle() {
		if (choiceSent) {
			return Component.literal("선택을 보냈습니다. 잠시만 기다리십시오");
		}
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
		if (forced) {
			if (escapable()) {
				return Component.literal("응답이 없습니다 · ESC로 닫을 수 있습니다");
			}
			if (this.width < 320) {
				return Component.literal("시간 정지 중 · 피해 무효");
			}
			return Component.literal(
					"시간이 멈췄고 팀 전원이 무적입니다 · 시간이 다 되면 무작위로 선택됩니다");
		}
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
