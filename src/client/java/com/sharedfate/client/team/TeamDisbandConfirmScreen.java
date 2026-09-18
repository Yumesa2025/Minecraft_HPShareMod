package com.sharedfate.client.team;

import com.sharedfate.ui.StatRow;
import com.sharedfate.ui.TeamDisbandWarning;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 「팀 해체」를 누르면 그 앞을 막아서는 경고창.
 *
 * <p>무엇이 사라지는지와 여기서 나가는 명령은 {@link TeamDisbandWarning} 에 있다. 이 화면은
 * 그것을 그리고, 확인을 받으면 받아 둔 일을 실행할 뿐이다.
 *
 * <h2>왜 바닐라 {@code ConfirmScreen} 을 쓰지 않는가</h2>
 * <p>26.2 에 {@code net.minecraft.client.gui.screens.ConfirmScreen} 이 있고 쓸 수도 있었다.
 * 쓰지 않은 까닭이 셋이다.
 *
 * <ul>
 *   <li>그 화면은 본문을 <b>가운데 정렬 한 덩어리</b>로 그린다. 여기서 읽혀야 하는 것은
 *       「사라지는 것 넷」이라 왼쪽에 맞춘 목록이라야 눈이 하나씩 짚는다.</li>
 *   <li>확인 단추가 먼저 붙는다({@code addButtons}). 되돌릴 수 없는 쪽이 앞에 오면 키보드
 *       {@code Tab} 이 처음 짚는 것도 그쪽이다. 뒤집으려면 어차피 그 메서드를 재정의해야 해서
 *       아낄 것이 없다.</li>
 *   <li>이 모드의 창들은 {@code PANEL_BG} 판 위에 그린다({@link TeamScreen} ·
 *       {@code PerkOfferScreen}). 해체 경고만 바닐라 회색 창이면 다른 모드의 창처럼 보인다.</li>
 * </ul>
 *
 * <p>대신 그 화면에서 배운 것은 그대로 가져왔다 — <b>확인 단추를 잠시 잠가 두는 것</b>
 * ({@code setDelay})과 <b>ESC 는 취소</b>다.
 *
 * <h2>나가는 길은 둘 다 팀 화면으로 돌아간다</h2>
 * <p>취소든 확인이든 {@link #parent} 로 되돌린다. 해체가 성공하면 몇 틱 뒤 도착하는 동기화가
 * 그 화면을 「팀이 없음」 모습으로 바꾼다 — 사라졌다는 것을 눈으로 보게 된다. 창을 닫아
 * 버리면 아무 일도 안 일어난 것처럼 보인다.
 */
public class TeamDisbandConfirmScreen extends Screen {
	/** 판 폭. {@link TeamScreen} 과 같아야 두 창이 겹쳐 뜰 때 테두리가 어긋나 보이지 않는다. */
	private static final int PANEL_WIDTH = 300;
	private static final int ROW_HEIGHT = 12;
	private static final int BUTTON_HEIGHT = 20;
	/** 경고 줄 사이의 틈. 줄이 붙으면 넷을 한 문단으로 읽어 버린다. */
	private static final int LINE_GAP = 2;
	/** 물음과 첫 경고 줄 사이의 틈. */
	private static final int QUESTION_GAP = 8;
	/** 마지막 경고 줄과 단추 사이의 틈. 읽기 전에 손이 먼저 가지 않게 넉넉히 둔다. */
	private static final int BUTTON_GAP = 12;
	/** 판이 글자 바깥으로 물러나는 여백. */
	private static final int PANEL_PAD_X = 8;
	private static final int PANEL_PAD_Y = 12;

	private static final int TEXT_MAIN = StatRow.COLOR_NEUTRAL;
	private static final int TEXT_WARN = 0xFFFFD24A;
	/** 물음 줄의 색. 바닐라 {@link ChatFormatting#RED} 와 같은 값이다. */
	private static final int TEXT_DANGER = 0xFFFF5555;
	private static final int PANEL_BG = 0xE0101018;
	/**
	 * 판 바깥을 덮는 색.
	 *
	 * <p>{@link TeamScreen} 은 세상을 가리지 않지만 이 창은 가린다. 여기서 눈이 가야 할 곳은
	 * 네 줄뿐이고, 뒤에서 움직이는 세상이 그대로 보이면 읽지 않고 누른다.
	 */
	private static final int SCRIM = 0xC0000000;

	private final Screen parent;
	/** 확인을 받았을 때 할 일. 명령을 보내는 것은 {@link TeamScreen} 이 맡는다. */
	private final Runnable onConfirm;

	/** 창이 떠 있은 틱. 확인 단추를 언제 풀지를 {@link TeamDisbandWarning} 이 정한다. */
	private int ticksOpen;
	private Button confirmButton;

	public TeamDisbandConfirmScreen(Screen parent, Runnable onConfirm) {
		super(Component.literal(TeamDisbandWarning.TITLE));
		this.parent = parent;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_WIDTH) / 2;
		int buttonY = blockTop() + blockHeight() - BUTTON_HEIGHT;
		int half = PANEL_WIDTH / 2 - 2;

		// 취소를 먼저 붙인다. 26.2 의 Screen.setInitialFocus 는 마지막 입력이 키보드였을 때
		// 첫 위젯으로 Tab 을 보내므로, 붙이는 순서가 곧 「기본 선택」이다. 되돌릴 수 없는 쪽이
		// 그 자리에 오면 안 된다.
		addRenderableWidget(Button.builder(
				Component.literal(TeamDisbandWarning.CANCEL_LABEL), button -> onClose())
				.bounds(left, buttonY, half, BUTTON_HEIGHT).build());

		confirmButton = Button.builder(
				Component.literal(TeamDisbandWarning.CONFIRM_LABEL).withStyle(ChatFormatting.RED),
				button -> confirm())
				.bounds(left + PANEL_WIDTH / 2 + 2, buttonY, half, BUTTON_HEIGHT).build();
		// 화면 크기가 바뀌면 init 이 다시 돈다. 그때 잠금을 처음부터 다시 걸면 창을 오래 보고
		// 있던 사람이 다시 기다리게 되므로, 지금까지 센 틱으로 판정한다.
		confirmButton.active = TeamDisbandWarning.confirmActive(ticksOpen);
		addRenderableWidget(confirmButton);
	}

	@Override
	public void tick() {
		super.tick();
		if (TeamDisbandWarning.confirmActive(ticksOpen)) {
			return;
		}
		ticksOpen++;
		if (confirmButton != null) {
			confirmButton.active = TeamDisbandWarning.confirmActive(ticksOpen);
		}
	}

	private void confirm() {
		onConfirm.run();
		back();
	}

	/**
	 * ESC 와 「취소」가 함께 오는 자리.
	 *
	 * <p>{@code Screen.shouldCloseOnEsc()} 가 참이라 ESC 는 여기로 온다. 재정의하지 않는 것이
	 * <b>일부러 고른 것</b>이다 — 되돌릴 수 없는 쪽으로 가는 길만 막으면 되고, 빠져나오는 길은
	 * 많을수록 좋다.
	 */
	@Override
	public void onClose() {
		back();
	}

	private void back() {
		if (this.minecraft == null) {
			super.onClose();
			return;
		}
		this.minecraft.setScreenAndShow(parent);
	}

	// ------------------------------------------------------------------ 그리기

	/** 물음 · 경고 줄 · 단추를 묶은 덩어리의 높이. */
	private int blockHeight() {
		return ROW_HEIGHT + QUESTION_GAP
				+ TeamDisbandWarning.lines().size() * (ROW_HEIGHT + LINE_GAP)
				+ BUTTON_GAP + BUTTON_HEIGHT;
	}

	/**
	 * 덩어리의 맨 위.
	 *
	 * <p>화면 한가운데에 둔다. 아래에 붙이면 「팀 해체」를 부른 단추가 있던 자리와 겹쳐, 딸깍
	 * 두 번에 확인까지 끝나 버린다. 잠금({@link TeamDisbandWarning#CONFIRM_DELAY_TICKS})과
	 * 함께 두 겹으로 막는다.
	 */
	private int blockTop() {
		return Math.max(PANEL_PAD_Y, (this.height - blockHeight()) / 2);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = blockTop();

		graphics.fill(0, 0, this.width, this.height, SCRIM);
		graphics.fill(left - PANEL_PAD_X, top - PANEL_PAD_Y,
				left + PANEL_WIDTH + PANEL_PAD_X, top + blockHeight() + PANEL_PAD_Y, PANEL_BG);

		graphics.centeredText(this.font, TeamDisbandWarning.QUESTION,
				this.width / 2, top, TEXT_DANGER);

		int y = top + ROW_HEIGHT + QUESTION_GAP;
		List<String> lines = TeamDisbandWarning.lines();
		for (int index = 0; index < lines.size(); index++) {
			// 마지막 줄이 「되돌릴 수 없다」다. 그 한 줄만 경고색으로 두어, 훑어 읽어도 눈에 걸린다.
			int color = index == lines.size() - 1 ? TEXT_WARN : TEXT_MAIN;
			graphics.text(this.font, lines.get(index), left, y, color);
			y += ROW_HEIGHT + LINE_GAP;
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** 뒤에서 세상이 계속 돈다. {@link TeamScreen} 과 같다 — 멀티에서는 멈추지도 않는다. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
