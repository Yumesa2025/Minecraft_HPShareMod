package com.sharedfate.client.perk;

import com.sharedfate.net.PerkChoiceC2SPayload;
import com.sharedfate.net.PerkOfferPayload;
import com.sharedfate.perk.PerkRarity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 *
 * <p>카드 한 장은 위에서부터 <b>등급 띠 → 아이템 아이콘 → 이름 → 구분선 → 설명</b> 순서로
 * 쌓인다. 등급색은 띠·테두리·배경 그라데이션에 함께 쓰여서 무엇을 고르는 라운드인지
 * 글자를 읽지 않아도 알 수 있게 한다.
 */
public class PerkOfferScreen extends Screen {
	private static final int COLOR_SILVER = 0xFFC0C6CC;
	private static final int COLOR_GOLD = 0xFFFFC63A;
	private static final int COLOR_PRISM = 0xFF5FE0D8;

	/** 카드 배경 그라데이션의 위/아래 색. 여기에 등급색을 조금 섞어서 쓴다. */
	private static final int CARD_TOP = 0xE81A1A24;
	private static final int CARD_BOTTOM = 0xE80B0B10;
	private static final int CARD_TOP_HOVER = 0xF4272734;
	private static final int CARD_BOTTOM_HOVER = 0xF4131319;
	/** 배경에 등급색을 섞는 비율. 평상시엔 은은하게, 호버 때는 조금 더 짙게. */
	private static final float CARD_TINT = 0.14F;
	private static final float CARD_TINT_HOVER = 0.26F;
	/** 호버한 카드의 테두리를 흰색 쪽으로 얼마나 끌어올릴지. */
	private static final float BORDER_BRIGHTEN_HOVER = 0.40F;

	private static final int SEPARATOR = 0x60FFFFFF;
	private static final int TEXT_MAIN = 0xFFFFFFFF;
	private static final int TEXT_SUB = 0xFFC0C0C0;
	private static final int TEXT_HINT = 0xFF909090;
	private static final int TEXT_SPECTATE = 0xFFFFD24A;
	private static final int TEXT_WAITING = 0xFF7FE07F;
	/** 등급 띠는 밝은 등급색으로 채우므로 글자는 어두워야 읽힌다. */
	private static final int BAND_TEXT = 0xFF10131A;

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
	/** 이름과 설명 사이 구분선이 차지하는 세로 공간(위 여백 3 + 선 1 + 아래 여백 4). */
	private static final int SEPARATOR_BLOCK_HEIGHT = 8;

	/** 카드 맨 위 등급 띠의 높이. */
	private static final int BAND_HEIGHT = 11;

	/** 프리즘 띠에 흘릴 색. 빨강에서 보라까지 이어진다. */
	private static final int[] PRISM_COLORS = {
			0xFFFF5C5C, 0xFFFFA24A, 0xFFFFE24A, 0xFF6BE06B, 0xFF5FE0D8, 0xFF6B9CFF, 0xFFC06BFF
	};

	/** 무지개 띠를 몇 조각으로 나눠 칠할지. 폭보다 크면 폭에 맞춘다. */
	private static final int PRISM_BAND_STEPS = 48;
	/** 아이템 아이콘 한 변의 원래 크기. */
	private static final int ICON_UNIT = 16;
	/** 자리가 넉넉할 때 쓰는 아이콘 크기 후보. 앞에서부터 들어가는 것을 고른다. */
	private static final int[] ICON_SIZES = {ICON_UNIT * 2, ICON_UNIT, 0};
	/** 등급 띠와 아이콘 사이 여백. */
	private static final int ICON_GAP_TOP = 4;
	/** 아이콘과 이름 사이 여백. */
	private static final int ICON_GAP_BOTTOM = 3;
	/** 아이콘 좌우로 최소한 남겨 둘 여백. 이만큼도 안 되면 한 단계 작은 아이콘을 쓴다. */
	private static final int ICON_SIDE_ROOM = 8;

	/** 마우스를 올린 카드가 위로 떠오르는 높이. */
	private static final int HOVER_LIFT = 2;
	/** 창이 열릴 때 카드가 아래에서 올라오는 높이. */
	private static final int ENTRY_RISE = 6;
	/** 카드 한 장이 제자리를 잡기까지 걸리는 시간(ms). */
	private static final long ENTRY_DURATION_MILLIS = 220L;
	/** 카드마다 등장 시작을 조금씩 미뤄 왼쪽부터 차례로 올라오게 한다(ms). */
	private static final long ENTRY_STAGGER_MILLIS = 45L;

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
	/** 창이 만들어진 시각. 등장 애니메이션의 기준점이다. */
	private final long openedAtMillis = System.currentTimeMillis();
	/**
	 * 이 라운드의 등급. 한 구간에서는 등급 하나 안에서만 후보를 뽑으므로 부제에 함께 적는다.
	 * 후보가 하나도 없거나 등급 문자열이 깨졌으면 null.
	 */
	private final @Nullable PerkRarity roundRarity;
	private final List<PerkOfferPayload.PerkOption> options;
	private final List<Card> cards = new ArrayList<>();

	/** 강제 오픈에서 선택을 보낸 뒤. 서버가 창을 닫아 줄 때까지 클릭을 막는다. */
	private boolean choiceSent;

	/**
	 * 결정된 증강의 후보 번호. 아직 결정 전이면 -1.
	 *
	 * <p>서버가 {@code PerkResultPayload} 를 보내면 그 카드 하나만 남기고 나머지를 지운다.
	 * 고른 사람 말고는 무엇이 정해졌는지 모른 채 창이 사라지던 것을 막기 위한 자리다.
	 */
	private int resultIndex = -1;
	/** 결과를 보여 주는 남은 시간(틱). 0 이면 결과 화면이 아니다. */
	private int resultTicks;
	/** 결과를 고른 사람 이름. 시간이 다 되어 자동으로 정해졌으면 빈 문자열. */
	private String resultChooser = "";

	private int timerY;
	private int titleY;
	private int subtitleY;
	private int cardWidth;
	private int cardHeight;
	private int cardTop;
	private int firstCardLeft;
	/** 이번 배치에서 카드에 그릴 아이콘 크기. 자리가 없으면 0이고 아이콘을 건너뛴다. */
	private int iconSize;
	/** 등장 애니메이션을 돌릴지. 화면이 빠듯하면 아래 문구를 침범하므로 끈다. */
	private boolean entryAnimated;

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
		this.roundRarity = firstRarity(this.options);
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

	/**
	 * 무엇이 골라졌는지 서버가 알려 왔다. 그 카드 하나만 남겨 잠깐 보여 준다.
	 *
	 * <p>후보에 없는 식별자가 오면 아무것도 하지 않는다. 늦게 도착한 지시가 다음 구간의 창을
	 * 건드리는 일을 막는다.
	 */
	public void showResult(String perkId, String chooserName, int holdTicks) {
		for (int index = 0; index < options.size(); index++) {
			if (options.get(index).id().equals(perkId)) {
				resultIndex = index;
				resultTicks = Math.max(1, holdTicks);
				resultChooser = chooserName == null ? "" : chooserName;
				choiceSent = true;
				playResultSound();
				return;
			}
		}
	}

	private void playResultSound() {
		if (this.minecraft == null) {
			return;
		}
		this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
				.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.1F, 0.7F));
	}

	/** 결과를 보여 주는 중인지. */
	public boolean showingResult() {
		return resultIndex >= 0;
	}

	@Override
	public void tick() {
		if (resultTicks > 0) {
			resultTicks--;
		}
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
		for (PerkOfferPayload.PerkOption option : options) {
			cards.add(Card.of(this.font, option, innerWidth));
		}

		int footerTop = Math.max(headerBottom + 46, this.height - 22);
		int room = footerTop - headerBottom - 6;
		// 아이콘을 큰 것부터 대 보고 세로·가로 자리가 모두 나오는 첫 크기를 고른다.
		iconSize = 0;
		int neededHeight = 40;
		for (int candidate : ICON_SIZES) {
			if (candidate > 0 && candidate + ICON_SIDE_ROOM > cardWidth) {
				continue;
			}
			int needed = requiredHeight(candidate);
			iconSize = candidate;
			neededHeight = needed;
			if (needed <= room) {
				break;
			}
		}

		cardHeight = Math.max(40, Math.min(neededHeight, room));
		cardTop = headerBottom + Math.max(0, (footerTop - headerBottom - cardHeight) / 2);
		// 등장 애니메이션은 카드를 잠깐 아래로 밀어 둔다. 아래 문구까지 여유가 있을 때만 쓴다.
		entryAnimated = cardTop + cardHeight + ENTRY_RISE <= this.height - 20;

		int totalWidth = cardWidth * count + CARD_GAP * (count - 1);
		firstCardLeft = Math.max(2, (this.width - totalWidth) / 2);
	}

	/** 주어진 아이콘 크기로 카드 내용을 다 담으려면 필요한 높이. */
	private int requiredHeight(int icon) {
		int tallest = 40;
		for (Card card : cards) {
			tallest = Math.max(tallest, card.height(this.font, icon));
		}
		return tallest;
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
			// 결과를 보여 주는 중에는 정해진 카드 하나만 남긴다.
			if (showingResult() && index != resultIndex) {
				continue;
			}
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

	/**
	 * 카드 한 장을 그린다.
	 *
	 * <p>호버 판정과 클릭 판정은 <b>움직이지 않는 자리</b>(cardTop 기준)로 한다. 떠오른 위치로
	 * 판정하면 카드가 올라가는 순간 마우스가 밖으로 빠져 깜빡이기 때문이다. 그리는 위치만
	 * 위아래로 흔든다.
	 */
	private void renderCard(GuiGraphicsExtractor graphics, int index, int mouseX, int mouseY) {
		Card card = cards.get(index);
		int left = cardLeft(index);
		int right = left + cardWidth;
		boolean hovered = clickable() && isInside(mouseX, mouseY, left, right, cardTop + cardHeight);

		int top = cardTop + entryOffset(index) - (hovered ? HOVER_LIFT : 0);
		int bottom = top + cardHeight;
		int rarity = card.rarityColor();

		// 등급색을 살짝 섞은 세로 그라데이션. 위가 밝고 아래로 가라앉는다.
		graphics.fillGradient(left, top, right, bottom,
				mix(hovered ? CARD_TOP_HOVER : CARD_TOP, rarity,
						hovered ? CARD_TINT_HOVER : CARD_TINT),
				mix(hovered ? CARD_BOTTOM_HOVER : CARD_BOTTOM, rarity,
						hovered ? CARD_TINT_HOVER / 2.0F : CARD_TINT / 2.0F));

		// 등급 띠. 카드 맨 위를 등급색으로 가득 채운다.
		// 프리즘만은 이름값을 하도록 무지개로 흘린다.
		if (card.rarity() == PerkRarity.PRISM) {
			renderPrismBand(graphics, left, top, right, hovered ? 0xFF : 0xDC);
		} else {
			graphics.fill(left, top, right, top + BAND_HEIGHT,
					hovered ? rarity : withAlpha(rarity, 0xDC));
		}

		int border = hovered ? brighten(rarity, BORDER_BRIGHTEN_HOVER) : rarity;
		graphics.outline(left, top, cardWidth, cardHeight, border);
		if (hovered) {
			// 마우스를 올린 카드는 테두리를 두 겹으로 그려 강조한다.
			graphics.outline(left + 1, top + 1, cardWidth - 2, cardHeight - 2, border);
		}

		// 카드가 세로로 잘린 경우 내용이 카드 밖으로 삐져나오지 않게 자른다.
		graphics.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
		int textCenterX = left + cardWidth / 2;
		graphics.centeredText(this.font, card.rarityLabel(), textCenterX,
				top + (BAND_HEIGHT - this.font.lineHeight + 1) / 2, BAND_TEXT);

		int y = top + BAND_HEIGHT + ICON_GAP_TOP;
		if (iconSize > 0) {
			renderIcon(graphics, card.icon(), textCenterX - iconSize / 2, y);
			y += iconSize + ICON_GAP_BOTTOM;
		}
		for (FormattedCharSequence line : card.nameLines()) {
			graphics.centeredText(this.font, line, textCenterX, y, TEXT_MAIN);
			y += this.font.lineHeight;
		}
		y += 3;
		graphics.fill(left + CARD_PADDING, y, right - CARD_PADDING, y + 1, SEPARATOR);
		y += 5;
		for (FormattedCharSequence line : card.descriptionLines()) {
			graphics.text(this.font, line, left + CARD_PADDING, y, TEXT_SUB);
			y += this.font.lineHeight;
		}
		graphics.disableScissor();
	}

	/**
	 * 아이템 아이콘을 {@code iconSize} 크기로 그린다.
	 *
	 * <p>{@code item()} 은 언제나 16×16 이라 카운트다운과 같은 방법으로 pose 에 배율을 쌓아
	 * 키운다. 확대 뒤 좌표는 배율로 나눈 값이어야 하므로 translate 로 먼저 옮겨 두고 0,0 에 그린다.
	 */
	private void renderIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		if (stack.isEmpty()) {
			return;
		}
		int scale = Math.max(1, iconSize / ICON_UNIT);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.item(stack, 0, 0);
		pose.popMatrix();
	}

	/**
	 * 창이 열린 직후 카드를 아래로 밀어 둘 거리. 시간이 지나면서 0으로 줄어든다.
	 *
	 * <p>카드가 세 장 줄줄이 튀어나오면 어느 것을 보라는 건지 알기 어렵다. 왼쪽부터 짧게
	 * 시차를 두고 올라오게 해서 눈이 왼쪽에서 오른쪽으로 흐르게 한다. 0.2초대라 급할 때
	 * 방해가 되지 않는다.
	 */
	private int entryOffset(int index) {
		if (!entryAnimated) {
			return 0;
		}
		long elapsed = System.currentTimeMillis() - openedAtMillis - index * ENTRY_STAGGER_MILLIS;
		if (elapsed >= ENTRY_DURATION_MILLIS) {
			return 0;
		}
		if (elapsed <= 0L) {
			return ENTRY_RISE;
		}
		float progress = (float) elapsed / (float) ENTRY_DURATION_MILLIS;
		float remaining = 1.0F - progress;
		// ease-out: 처음에 빠르게 올라오고 끝에서 부드럽게 멈춘다.
		return Math.round(ENTRY_RISE * remaining * remaining * remaining);
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
		return canChoose && !choiceSent && !showingResult();
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
		if (showingResult()) {
			return resultChooser.isEmpty()
					? Component.literal("시간이 다 되어 무작위로 정해졌습니다")
					: Component.literal(resultChooser + "님이 골랐습니다");
		}
		if (choiceSent) {
			return Component.literal("선택을 보냈습니다. 잠시만 기다리십시오");
		}
		if (canChoose) {
			if (roundRarity != null) {
				// 어떤 구간에서 무슨 등급을 고르는 중인지 한 줄로 알려 준다.
				return Component.literal("공유 레벨 " + milestone + " 달성 · "
						+ roundRarity.displayName() + " 라운드");
			}
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
		if (showingResult()) {
			return Component.literal("팀 전체에 적용됩니다");
		}
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

	/** 후보 목록에서 이 라운드의 등급을 집어낸다. 한 라운드는 등급 하나로만 채워진다. */
	private static @Nullable PerkRarity firstRarity(List<PerkOfferPayload.PerkOption> options) {
		for (PerkOfferPayload.PerkOption option : options) {
			PerkRarity rarity = PerkRarity.fromId(option.rarity());
			if (rarity != null) {
				return rarity;
			}
		}
		return null;
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
			case PRISM -> COLOR_PRISM;
		};
	}

	/**
	 * 증강이 지정한 아이콘 아이템을 찾는다.
	 *
	 * <p>서버가 이미 걸러서 보내지만, 서버에만 있는 모드 아이템이거나 클라이언트에서
	 * 이름이 바뀐 경우가 있을 수 있다. 못 찾으면 등급별 기본 아이콘으로 조용히 대체한다.
	 * 아이콘 하나 때문에 카드가 비어 보이면 안 된다.
	 */
	private static ItemStack iconStack(String iconId, PerkRarity rarity) {
		Item item = lookupItem(iconId);
		return new ItemStack(item == null ? defaultIcon(rarity) : item);
	}

	private static @Nullable Item lookupItem(String iconId) {
		if (iconId == null || iconId.isBlank()) {
			return null;
		}
		try {
			Identifier id = Identifier.tryParse(iconId.trim());
			if (id == null) {
				return null;
			}
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(id);
			if (found.isEmpty()) {
				return null;
			}
			// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
			Item item = found.get().value();
			return item == Items.AIR ? null : item;
		} catch (Exception error) {
			return null;
		}
	}

	/** 아이콘을 정하지 않은 증강이 쓰는 등급별 기본 아이콘. 금속 등급을 그대로 따른다. */
	private static Item defaultIcon(PerkRarity rarity) {
		if (rarity == null) {
			return Items.IRON_INGOT;
		}
		return switch (rarity) {
			case SILVER -> Items.IRON_INGOT;
			case GOLD -> Items.GOLD_INGOT;
			case PRISM -> Items.DIAMOND;
		};
	}

	/** {@code base} 에 {@code tint} 를 {@code amount} 만큼 섞는다. 투명도는 base 것을 쓴다. */
	private static int mix(int base, int tint, float amount) {
		float ratio = Math.clamp(amount, 0.0F, 1.0F);
		int red = Math.round((base >> 16 & 0xFF) * (1.0F - ratio) + (tint >> 16 & 0xFF) * ratio);
		int green = Math.round((base >> 8 & 0xFF) * (1.0F - ratio) + (tint >> 8 & 0xFF) * ratio);
		int blue = Math.round((base & 0xFF) * (1.0F - ratio) + (tint & 0xFF) * ratio);
		return (base & 0xFF000000) | red << 16 | green << 8 | blue;
	}

	/** 색을 흰색 쪽으로 끌어올린다. 호버한 카드의 테두리를 밝히는 데 쓴다. */
	private static int brighten(int color, float amount) {
		return mix(color, 0xFFFFFFFF, amount);
	}

	/**
	 * 프리즘 등급의 등급 띠를 무지개로 그린다.
	 *
	 * <p>{@code fillGradient} 는 위아래 두 색만 받으므로 가로 무지개를 한 번에 그릴 수 없다.
	 * 띠를 세로로 잘게 나누고 조각마다 이웃한 두 색을 섞어 칠하면 가로로 흐르는 것처럼 보인다.
	 * 등급 띠 하나에만 쓰므로 조각이 늘어도 비용은 무시할 만하다.
	 *
	 * <p>테두리까지 무지개로 하면 카드 경계가 흐려지므로 띠에만 쓴다.
	 */
	private void renderPrismBand(GuiGraphicsExtractor graphics, int left, int top, int right,
			int alpha) {
		int width = right - left;
		if (width <= 0) {
			return;
		}
		int bottom = top + BAND_HEIGHT;
		int steps = Math.min(width, PRISM_BAND_STEPS);
		for (int step = 0; step < steps; step++) {
			int sliceLeft = left + (int) ((long) width * step / steps);
			int sliceRight = left + (int) ((long) width * (step + 1) / steps);
			if (sliceRight <= sliceLeft) {
				continue;
			}
			// 조각 가운데 위치를 무지개 위의 한 점으로 본다.
			float position = (step + 0.5F) / steps * (PRISM_COLORS.length - 1);
			int index = Math.min((int) position, PRISM_COLORS.length - 2);
			int color = withAlpha(
					mix(PRISM_COLORS[index], PRISM_COLORS[index + 1], position - index), alpha);
			graphics.fill(sliceLeft, top, sliceRight, bottom, color);
		}
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha & 0xFF) << 24 | color & 0x00FFFFFF;
	}

	/** 화면 폭이 정해진 뒤 한 번 계산해 두는 카드 한 장의 표시 내용. */
	private record Card(List<FormattedCharSequence> nameLines,
			Component rarityLabel,
			PerkRarity rarity,
			int rarityColor,
			ItemStack icon,
			List<FormattedCharSequence> descriptionLines) {

		static Card of(Font font, PerkOfferPayload.PerkOption option, int innerWidth) {
			PerkRarity parsed = PerkRarity.fromId(option.rarity());
			// 등급을 못 읽어도 화면은 떠야 하므로 실버로 본다.
			PerkRarity rarity = parsed == null ? PerkRarity.SILVER : parsed;
			// 이름은 굵게 해서 설명과 무게를 벌린다. 굵으면 폭도 늘어나므로 줄바꿈도 굵은 채로 잰다.
			Component name = Component.literal(text(option.name()))
					.withStyle(ChatFormatting.BOLD);
			return new Card(
					font.split(name, innerWidth),
					Component.literal(PerkOfferScreen.rarityLabel(rarity)),
					rarity,
					PerkOfferScreen.rarityColor(rarity),
					PerkOfferScreen.iconStack(option.icon(), rarity),
					font.split(Component.literal(text(option.description())), innerWidth));
		}

		/** 아이콘을 {@code iconSize} 로 그린다고 할 때 이 카드가 필요로 하는 세로 길이. */
		int height(Font font, int iconSize) {
			int lines = nameLines.size() + descriptionLines.size();
			int iconBlock = iconSize > 0 ? iconSize + ICON_GAP_BOTTOM : 0;
			return BAND_HEIGHT + ICON_GAP_TOP + iconBlock
					+ lines * font.lineHeight + SEPARATOR_BLOCK_HEIGHT + CARD_PADDING;
		}

		private static String text(String value) {
			return value == null ? "" : value;
		}
	}
}
