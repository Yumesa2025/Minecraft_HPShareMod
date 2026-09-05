package com.sharedfate.client.hud;

import com.sharedfate.client.perk.ClientPerkSets;
import com.sharedfate.ui.PerkSetLines;
import com.sharedfate.ui.StatRow;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;

/**
 * 화면 <b>왼쪽 위</b>에 지금 서 있는 좌표와 한글 바이옴 이름을 두 줄로 늘 띄운다.
 *
 * <h2>왜 필요한가</h2>
 * <p>여럿이 같은 세계를 도는 놀이에서 「어디냐」는 물음이 제일 자주 나온다. 좌표를 보려면
 * F3 을 눌러야 하는데, F3 화면은 글자가 스무 줄 넘게 깔려 화면을 통째로 덮는다. 그래서 늘
 * 켜 둘 수가 없고, 눌렀다 껐다 하는 동안 채팅으로 좌표를 부르는 흐름이 끊긴다. 필요한
 * 것은 좌표 한 줄과 여기가 어디인지 한 줄, 그 두 줄뿐이다.
 *
 * <h2>왜 서버와 통신하지 않는가</h2>
 * <p>좌표도 바이옴도 <b>클라이언트가 이미 들고 있는 값</b>이다. 좌표는 자기 엔티티에 있고,
 * 바이옴은 서버가 청크와 함께 보내 준 것을 {@code ClientLevel} 이 그대로 갖고 있다. 새 패킷을
 * 만들면 통신 규약 번호를 올려야 하고, 그러면 규약이 다른 클라이언트가 접속을 거부당한다.
 * 아무것도 새로 얻지 못하면서 접속만 깨뜨리는 셈이라 이 표시는 <b>순수하게 클라이언트 전용</b>
 * 으로 둔다.
 *
 * <h2>왜 왼쪽 위인가</h2>
 * <p>이 모드가 이미 쓰고 있는 자리는 전부 화면 아래다 — {@link TeamLevelHud} 와
 * {@link DamageAlertHud} 가 핫바 왼쪽 위로 쌓이고({@link BottomLeftStack}),
 * {@link PerkProgressHud} 가 경험치 바 위에 붙는다. {@link GameOverHud} 만 화면 가운데 위를
 * 쓰는데 그것도 전멸한 순간에만 뜬다. 바닐라도 왼쪽 위는 비워 둔다(상태이상은 오른쪽 위,
 * 보스 바는 가운데 위, 채팅은 왼쪽 아래). 그래서 <b>왼쪽 위는 아무와도 겹치지 않는 유일하게
 * 비어 있는 구석</b>이고, 늘 떠 있어야 하는 표시를 두기에 알맞다.
 *
 * <p>이 자리에 무언가를 더 그리게 되면 {@link #NEXT_LINE_Y} 부터 아래로 쌓으면 된다. 아래쪽
 * 글줄들처럼 {@code BottomLeftStack} 같은 계산기를 따로 두지 않은 이유는, 지금 이 자리를 쓰는
 * 것이 이 요소 하나뿐이라 나눌 것이 없기 때문이다.
 *
 * <h2>세트 효과</h2>
 * <p>{@link #NEXT_LINE_Y} 아래에 구분선을 하나 긋고 세트를 쌓는다. 좌표·바이옴과 한 덩어리로
 * 보이면 안 되므로 선으로 가른다. 무엇을 몇 줄이나 어떤 차례로 그릴지는
 * {@link com.sharedfate.ui.PerkSetLines} 가 정한다 — 그리기와 떼어 놓아야 게임 없이 시험할 수
 * 있고, 같은 계산을 팀 화면도 쓴다.
 *
 * <pre>
 * X 128  Y 64  Z -302
 * 어두운 숲
 * ────────────────
 * ◆ 채굴 3/3
 * ◇ 방어 1/2
 * </pre>
 *
 * <h2>안 그리는 때</h2>
 * <p>F3 디버그 화면이 켜져 있으면 그리지 않는다. F3 이 왼쪽 위부터 글자를 깔기 때문에 그대로
 * 두면 두 글자가 겹쳐 둘 다 못 읽는다. F1 로 HUD 를 껐을 때와 관전 중일 때도 빠진다 —
 * 관전자는 자기 자리가 아니라 남을 따라다니는 중이라 그 좌표에 뜻이 없다.
 */
public class CoordinateHud implements HudElement {
	/** 화면 가장자리에서 띄우는 여백. */
	private static final int MARGIN = 4;
	/** 글줄 높이. 바닐라 기본 글꼴 기준이며 {@link BottomLeftStack} 이 쓰는 값과 같다. */
	private static final int LINE_HEIGHT = 10;
	/** 좌표 줄의 윗변. */
	private static final int POSITION_Y = MARGIN;
	/** 바이옴 줄의 윗변. */
	private static final int BIOME_Y = POSITION_Y + LINE_HEIGHT;
	/** 이 자리에 글줄을 더 쌓게 되면 여기서부터 아래로 내려간다. */
	public static final int NEXT_LINE_Y = BIOME_Y + LINE_HEIGHT;

	/** 좌표는 제일 자주 읽는 값이라 가장 밝게 둔다. */
	private static final int POSITION_COLOR = 0xFFFFFFFF;
	/** 바이옴은 한 단계 눈에 덜 띄는 회색. {@link GameOverHud} 의 안내 줄과 같은 색이다. */
	private static final int BIOME_COLOR = 0xFFCCCCCC;

	/** 바이옴 줄과 구분선 사이의 틈. */
	private static final int SEPARATOR_GAP = 3;
	/** 구분선 아래 첫 세트 줄까지의 틈. */
	private static final int SEPARATOR_BOTTOM_GAP = 3;
	/** 구분선이 아무리 짧아도 이만큼은 긋는다. */
	private static final int SEPARATOR_MIN_WIDTH = 60;
	/** 구분선 색. 좌표·바이옴보다 흐려야 <b>가르는 선</b>으로만 읽힌다. */
	private static final int SEPARATOR_COLOR = 0x50FFFFFF;
	/**
	 * 켜진 세트의 글자색. 능력치 줄의 「올랐다」와 같은 색이다({@link StatRow#COLOR_GOOD}).
	 *
	 * <p>두 곳에 같은 뜻으로 두 색을 쓰면 화면마다 다른 규칙을 외워야 한다.
	 */
	private static final int SET_ACTIVE_COLOR = StatRow.COLOR_GOOD;
	/** 아직 안 켜진 세트. 바이옴보다도 한 단계 더 가라앉혀 「지금은 아니다」를 색으로도 말한다. */
	private static final int SET_PROGRESS_COLOR = StatRow.COLOR_MASKED;

	/**
	 * 좌표 한 줄을 만든다.
	 *
	 * <p><b>내림</b>이지 자름이 아니다. {@code (int)} 로 자르면 x 가 -0.5 일 때 0 이 나오는데,
	 * 실제로 서 있는 블록은 -1 이다. 음수 쪽 좌표에서만 한 칸씩 틀리는 이런 어긋남은 좌표를
	 * 불러 주고 찾아가는 상황에서 제일 나쁘다. 바닐라 F3 도 같은 이유로 내림을 쓴다.
	 *
	 * <p>소수점을 적지 않는 이유는, 서로에게 불러 주는 좌표에 소수점이 필요한 적이 없기
	 * 때문이다. F3 처럼 소수 다섯 자리를 늘어놓으면 줄만 길어지고 읽는 속도가 느려진다.
	 *
	 * <p>같은 일을 하는 {@code Mth.floor} 를 쓰지 않은 것은 일부러다. {@code Mth} 의 static
	 * 초기화가 {@code Util} 을 끌어오고, {@code Util} 은 스레드 풀까지 만든다. 이 함수를 게임
	 * 없이 단위 시험으로 부를 수 있게 두려면 마인크래프트 클래스를 아예 건드리지 않아야 한다.
	 * 좌표는 어차피 월드 경계 안이라 {@code int} 로 담기고, 결과도 {@code Mth.floor} 와 같다.
	 */
	public static String positionLine(double x, double y, double z) {
		return "X " + floor(x) + "  Y " + floor(y) + "  Z " + floor(z);
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		Font font = client.font;
		if (player == null || level == null || font == null) {
			return;
		}
		// F1 로 HUD 를 껐거나 F3 디버그 화면이 켜져 있으면 그리지 않는다. 관전 중에도 뺀다.
		if (client.gui.hud.isHidden() || isDebugScreenOpen(client) || player.isSpectator()) {
			return;
		}

		graphics.text(font, positionLine(player.getX(), player.getY(), player.getZ()),
				MARGIN, POSITION_Y, POSITION_COLOR);

		String biome = BiomeNames.korean(currentBiomeId(level, player));
		if (!biome.isEmpty()) {
			graphics.text(font, biome, MARGIN, BIOME_Y, BIOME_COLOR);
		}

		renderSets(graphics, font);
	}

	/**
	 * 좌표·바이옴 아래에 세트를 쌓는다.
	 *
	 * <p><b>켜진 것만이 아니라 진행도까지</b> 보여 준다. 「방어 1/2」를 알아야 다음 카드에서
	 * 무엇을 집을지 정할 수 있고, 그 판단은 선택창이 뜬 몇 초 안에 내려야 하므로 그때 팀 화면을
	 * 열 겨를이 없다. 대신 <b>한 개도 없는 유형은 뺀다</b> — 열한 줄이 다 뜨면 화면 왼쪽 위를
	 * 통째로 덮는다. 그 규칙은 {@link PerkSetLines#visible} 이 들고 있다.
	 *
	 * <p>세트가 하나도 없으면 구분선도 긋지 않는다. 아래에 아무것도 없는 선은 무언가 그리다 만
	 * 것처럼 보인다.
	 */
	private static void renderSets(GuiGraphicsExtractor graphics, Font font) {
		List<PerkSetLines.Line> lines = ClientPerkSets.lines(PerkSetLines.MAX_HUD_LINES);
		if (lines.isEmpty()) {
			return;
		}

		int separatorY = NEXT_LINE_Y + SEPARATOR_GAP;
		int width = PerkSetLines.blockWidth(lines, font::width, SEPARATOR_MIN_WIDTH);
		graphics.fill(MARGIN, separatorY, MARGIN + width, separatorY + 1, SEPARATOR_COLOR);

		int y = separatorY + 1 + SEPARATOR_BOTTOM_GAP;
		for (PerkSetLines.Line line : lines) {
			graphics.text(font, line.text(), MARGIN, y,
					line.active() ? SET_ACTIVE_COLOR : SET_PROGRESS_COLOR);
			y += LINE_HEIGHT;
		}
	}

	/**
	 * 지금 서 있는 자리의 바이옴 id. 알 수 없으면 {@code null}.
	 *
	 * <p>{@code unwrapKey()} 가 비는 경우는 데이터팩이 등록표에 없는 바이옴을 직접 끼워 넣었을
	 * 때다. 흔하지 않지만 {@code get()} 으로 꺼내면 그때 예외가 나고, HUD 는 매 프레임 돌기
	 * 때문에 그 한 번이 화면 전체를 죽인다. 그래서 {@code null} 로 받아 넘긴다.
	 */
	private static Identifier currentBiomeId(ClientLevel level, LocalPlayer player) {
		return level.getBiome(player.blockPosition())
				.unwrapKey()
				.map(ResourceKey::identifier)
				.orElse(null);
	}

	private static boolean isDebugScreenOpen(Minecraft client) {
		DebugScreenOverlay overlay = client.getDebugOverlay();
		return overlay != null && overlay.showDebugScreen();
	}
}
