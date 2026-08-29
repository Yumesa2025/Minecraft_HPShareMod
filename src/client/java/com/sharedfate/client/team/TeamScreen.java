package com.sharedfate.client.team;

import com.sharedfate.client.ClientTeamState;
import com.sharedfate.client.perk.PerkClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@code /shareteam} 로 여는 팀 화면.
 *
 * <p>탭 넷으로 나뉜다. <b>현황</b>은 누구나, <b>팀</b>은 만들기·초대·탈퇴, <b>설정</b>은 리더만,
 * <b>증강</b>은 지금 보유한 증강을 보여 준다.
 *
 * <h2>왜 새 패킷을 만들지 않았나</h2>
 * <p>이 화면이 하는 일은 전부 이미 있는 {@code /shareteam ...} 명령으로 표현된다. 그래서 단추를
 * 누르면 {@link net.minecraft.client.multiplayer.ClientPacketListener#sendCommand(String)} 로
 * 그 명령을 보낸다. 조작마다 C2S 패킷을 새로 만들면 <b>권한 검사와 실패 문구를 서버 명령 쪽과
 * 두 벌로 관리</b>하게 되는데, 그러면 한쪽만 고쳐지는 사고가 난다. 명령을 그대로 태우면 검사는
 * 언제나 한 곳이다.
 *
 * <p>보여 줄 값은 이미 계속 오고 있는 {@code TeamSyncPayload}·{@code PerkSyncPayload} 가 채운
 * {@link ClientTeamState}·{@link PerkClientState} 에서 읽는다. 창을 열 때 서버에 따로 묻지 않는다.
 *
 * <h2>화면이 스스로 갱신되는 방식</h2>
 * <p>명령을 보낸 결과는 서버가 다시 보내 주는 동기화 묶음으로 들어온다. 몇 틱 뒤의 일이라
 * 단추를 누른 순간에는 아직 옛 값이다. 그래서 {@link #tick()} 마다 팀 상태의 요약을 견주어
 * 달라졌을 때만 위젯을 다시 만든다. 매 틱 다시 만들면 글자 입력 칸의 커서가 튄다.
 */
public class TeamScreen extends Screen {
	private static final int PANEL_TOP = 40;
	private static final int ROW_HEIGHT = 12;
	private static final int BUTTON_HEIGHT = 20;
	private static final int TAB_HEIGHT = 18;
	private static final int PANEL_WIDTH = 300;

	private static final int TEXT_MAIN = 0xFFE8E8F0;
	private static final int TEXT_DIM = 0xFF9AA0AA;
	private static final int TEXT_GOOD = 0xFF80FF20;
	private static final int TEXT_WARN = 0xFFFFD24A;
	private static final int PANEL_BG = 0xC0101018;

	/** 팀 이름 길이 상한. 서버의 {@code MAX_TEAM_NAME_LENGTH} 와 같아야 한다. */
	private static final int MAX_TEAM_NAME_LENGTH = 32;
	private static final int MIN_HEALTH = 20;
	private static final int MAX_HEALTH = 40;
	private static final int HEALTH_STEP = 2;
	private static final int MIN_SWAP_MINUTES = 1;
	private static final int MAX_SWAP_MINUTES = 120;

	private enum Tab {
		STATUS("현황"),
		TEAM("팀"),
		SETTINGS("설정"),
		PERKS("증강");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.STATUS;
	private EditBox nameBox;
	/** 마지막으로 위젯을 만들 때의 팀 상태 요약. 달라지면 다시 만든다. */
	private String lastSignature = "";

	public TeamScreen() {
		super(Component.literal("SharedFate 팀"));
	}

	@Override
	protected void init() {
		lastSignature = signature();
		int left = (this.width - PANEL_WIDTH) / 2;

		int tabWidth = PANEL_WIDTH / Tab.values().length;
		for (Tab value : Tab.values()) {
			int x = left + tabWidth * value.ordinal();
			Component label = value == tab
					? Component.literal(value.label).withStyle(ChatFormatting.YELLOW)
					: Component.literal(value.label);
			addRenderableWidget(Button.builder(label, button -> switchTo(value))
					.bounds(x, PANEL_TOP - TAB_HEIGHT - 2, tabWidth - 2, TAB_HEIGHT).build());
		}

		switch (tab) {
			case STATUS -> initStatus(left);
			case TEAM -> initTeam(left);
			case SETTINGS -> initSettings(left);
			case PERKS -> initPerks(left);
		}

		addRenderableWidget(Button.builder(Component.literal("닫기"), button -> onClose())
				.bounds(left + PANEL_WIDTH / 2 - 50, this.height - 28, 100, BUTTON_HEIGHT).build());
	}

	private void switchTo(Tab next) {
		tab = next;
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	// ------------------------------------------------------------------ 현황

	private void initStatus(int left) {
		if (ClientTeamState.inTeam() && PerkClientState.hasPending()) {
			addRenderableWidget(Button.builder(
					Component.literal("증강 선택 창 열기"), button -> run("perk"))
					.bounds(left, this.height - 54, PANEL_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	// ------------------------------------------------------------------ 팀

	private void initTeam(int left) {
		if (!ClientTeamState.inTeam()) {
			nameBox = new EditBox(this.font, left, PANEL_TOP + 14, PANEL_WIDTH, BUTTON_HEIGHT,
					Component.literal("팀 이름"));
			nameBox.setMaxLength(MAX_TEAM_NAME_LENGTH);
			addRenderableWidget(nameBox);

			addRenderableWidget(Button.builder(Component.literal("증강 켜고 만들기"),
					button -> createTeam(true))
					.bounds(left, PANEL_TOP + 40, PANEL_WIDTH / 2 - 2, BUTTON_HEIGHT).build());
			addRenderableWidget(Button.builder(Component.literal("증강 없이 만들기"),
					button -> createTeam(false))
					.bounds(left + PANEL_WIDTH / 2 + 2, PANEL_TOP + 40,
							PANEL_WIDTH / 2 - 2, BUTTON_HEIGHT).build());
			return;
		}

		nameBox = null;
		int y = PANEL_TOP + 14 + ROW_HEIGHT * (ClientTeamState.memberIds().size() + 1);

		if (ClientTeamState.isLeader()) {
			for (String name : invitableNames()) {
				if (y > this.height - 80) {
					break;
				}
				addRenderableWidget(Button.builder(Component.literal(name + " 초대"),
						button -> run("invite " + name))
						.bounds(left, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
				y += BUTTON_HEIGHT + 2;
			}
		}

		addRenderableWidget(Button.builder(Component.literal("팀 나가기"), button -> run("leave"))
				.bounds(left, this.height - 54, PANEL_WIDTH / 2 - 2, BUTTON_HEIGHT).build());
		if (ClientTeamState.isLeader()) {
			addRenderableWidget(Button.builder(
					Component.literal("팀 해체").withStyle(ChatFormatting.RED),
					button -> run("disband confirm"))
					.bounds(left + PANEL_WIDTH / 2 + 2, this.height - 54,
							PANEL_WIDTH / 2 - 2, BUTTON_HEIGHT).build());
		}
	}

	private void createTeam(boolean perks) {
		if (nameBox == null) {
			return;
		}
		String name = nameBox.getValue().trim();
		if (name.isEmpty()) {
			return;
		}
		run(perks ? "create perks on " + name : "create " + name);
	}

	/** 팀에 없는 접속자 이름. 자기 자신은 뺀다. */
	private List<String> invitableNames() {
		List<String> names = new ArrayList<>();
		if (this.minecraft == null || this.minecraft.getConnection() == null) {
			return names;
		}
		UUID self = this.minecraft.player == null ? null : this.minecraft.player.getUUID();
		for (PlayerInfo info : this.minecraft.getConnection().getListedOnlinePlayers()) {
			UUID id = info.getProfile().id();
			if (id.equals(self) || ClientTeamState.memberIds().contains(id)) {
				continue;
			}
			names.add(info.getProfile().name());
		}
		names.sort(Comparator.naturalOrder());
		return names;
	}

	// ------------------------------------------------------------------ 설정

	private void initSettings(int left) {
		if (!ClientTeamState.inTeam() || !ClientTeamState.isLeader()) {
			return;
		}
		int y = PANEL_TOP + 24;
		int half = PANEL_WIDTH / 2 - 2;

		int health = Math.round(ClientTeamState.maxHealth());
		addRenderableWidget(Button.builder(Component.literal("최대 체력 −" + HEALTH_STEP),
				button -> run("health " + clampHealth(health - HEALTH_STEP)))
				.bounds(left, y, half, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("최대 체력 +" + HEALTH_STEP),
				button -> run("health " + clampHealth(health + HEALTH_STEP)))
				.bounds(left + PANEL_WIDTH / 2 + 2, y, half, BUTTON_HEIGHT).build());

		y += BUTTON_HEIGHT + 14;
		int minutes = ClientTeamState.swapIntervalMinutes();
		if (ClientTeamState.swapEnabled()) {
			addRenderableWidget(Button.builder(Component.literal("교환 주기 −1분"),
					button -> run("swap on " + clampMinutes(minutes - 1)))
					.bounds(left, y, half / 2 - 1, BUTTON_HEIGHT).build());
			addRenderableWidget(Button.builder(Component.literal("+1분"),
					button -> run("swap on " + clampMinutes(minutes + 1)))
					.bounds(left + half / 2 + 1, y, half / 2 - 1, BUTTON_HEIGHT).build());
			addRenderableWidget(Button.builder(Component.literal("위치 교환 끄기"),
					button -> run("swap off"))
					.bounds(left + PANEL_WIDTH / 2 + 2, y, half, BUTTON_HEIGHT).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal("위치 교환 켜기 (5분)"),
					button -> run("swap on 5"))
					.bounds(left, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
		}

		y += BUTTON_HEIGHT + 14;
		boolean perks = ClientTeamState.perksEnabled();
		addRenderableWidget(Button.builder(
				Component.literal(perks ? "증강 끄기" : "증강 켜기"),
				button -> run(perks ? "perks off" : "perks on"))
				.bounds(left, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
	}

	private static int clampHealth(int value) {
		return Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, value));
	}

	private static int clampMinutes(int value) {
		return Math.max(MIN_SWAP_MINUTES, Math.min(MAX_SWAP_MINUTES, value));
	}

	// ------------------------------------------------------------------ 증강

	private void initPerks(int left) {
		if (ClientTeamState.inTeam() && PerkClientState.hasPending()) {
			addRenderableWidget(Button.builder(
					Component.literal("증강 선택 창 열기"), button -> run("perk"))
					.bounds(left, this.height - 54, PANEL_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	// ------------------------------------------------------------------ 그리기

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		int left = (this.width - PANEL_WIDTH) / 2;
		graphics.fill(left - 6, PANEL_TOP - TAB_HEIGHT - 8, left + PANEL_WIDTH + 6,
				this.height - 32, PANEL_BG);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, TEXT_MAIN);

		switch (tab) {
			case STATUS -> renderStatus(graphics, left);
			case TEAM -> renderTeam(graphics, left);
			case SETTINGS -> renderSettings(graphics, left);
			case PERKS -> renderPerks(graphics, left);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void renderStatus(GuiGraphicsExtractor graphics, int left) {
		int y = PANEL_TOP;
		if (!ClientTeamState.inTeam()) {
			graphics.text(this.font, "팀에 속해 있지 않습니다.", left, y, TEXT_DIM);
			graphics.text(this.font, "'팀' 탭에서 새로 만들거나, 리더에게 초대를 받으세요.",
					left, y + ROW_HEIGHT, TEXT_DIM);
			return;
		}

		graphics.text(this.font, "팀 " + ClientTeamState.teamName(), left, y, TEXT_MAIN);
		y += ROW_HEIGHT + 2;
		graphics.text(this.font, "팀원 " + onlineCount() + "/"
				+ ClientTeamState.memberIds().size() + "명 접속", left, y, TEXT_DIM);
		y += ROW_HEIGHT + 2;

		graphics.text(this.font, "팀 레벨 " + ClientTeamState.teamLevel(), left, y, TEXT_GOOD);
		y += ROW_HEIGHT;
		int remaining = ClientTeamState.levelsToNextPerk();
		graphics.text(this.font, remaining < 0 ? "남은 증강 없음" : "다음 증강까지 " + remaining,
				left, y, TEXT_WARN);
		y += ROW_HEIGHT + 2;

		Player player = this.minecraft == null ? null : this.minecraft.player;
		String health = player == null
				? trimZero(ClientTeamState.maxHealth())
				: trimZero(player.getHealth()) + " / " + trimZero(ClientTeamState.maxHealth());
		graphics.text(this.font, "공유 체력 " + health, left, y, TEXT_MAIN);
		y += ROW_HEIGHT;
		graphics.text(this.font, "위치 교환 " + (ClientTeamState.swapEnabled()
				? ClientTeamState.swapIntervalMinutes() + "분 주기" : "꺼짐"), left, y, TEXT_DIM);
		y += ROW_HEIGHT;
		graphics.text(this.font, "증강 " + (ClientTeamState.perksEnabled() ? "사용" : "사용 안 함"),
				left, y, TEXT_DIM);

		if (PerkClientState.hasPending()) {
			y += ROW_HEIGHT + 4;
			String chooser = PerkClientState.chooserName();
			graphics.text(this.font, chooser.isEmpty()
					? "고르지 않은 증강 선택권이 있습니다."
					: chooser + "님이 증강을 고를 차례입니다.", left, y, TEXT_WARN);
		}
	}

	private void renderTeam(GuiGraphicsExtractor graphics, int left) {
		int y = PANEL_TOP;
		if (!ClientTeamState.inTeam()) {
			graphics.text(this.font, "새 팀 이름을 적고 아래 단추를 누르세요.", left, y, TEXT_DIM);
			graphics.text(this.font, "증강은 만들 때 정하며 나중에 설정 탭에서 바꿉니다.",
					left, PANEL_TOP + 66, TEXT_DIM);
			return;
		}

		graphics.text(this.font, "팀 " + ClientTeamState.teamName()
				+ (ClientTeamState.isLeader() ? " (내가 리더)" : ""), left, y, TEXT_MAIN);
		y += ROW_HEIGHT + 2;
		for (UUID id : ClientTeamState.memberIds()) {
			boolean online = isOnline(id);
			graphics.text(this.font, (online ? "● " : "○ ") + ClientTeamState.memberName(id),
					left, y, online ? TEXT_GOOD : TEXT_DIM);
			y += ROW_HEIGHT;
		}
		if (!ClientTeamState.isLeader()) {
			graphics.text(this.font, "초대는 리더만 할 수 있습니다.", left, y + 4, TEXT_DIM);
		} else if (invitableNames().isEmpty()) {
			graphics.text(this.font, "초대할 수 있는 접속자가 없습니다.", left, y + 4, TEXT_DIM);
		} else {
			graphics.text(this.font, "누르면 곧바로 팀에 들어옵니다. 상대의 개인 아이템은 드랍됩니다.",
					left, y + 4, TEXT_WARN);
		}
	}

	private void renderSettings(GuiGraphicsExtractor graphics, int left) {
		int y = PANEL_TOP;
		if (!ClientTeamState.inTeam()) {
			graphics.text(this.font, "팀이 있어야 설정을 바꿀 수 있습니다.", left, y, TEXT_DIM);
			return;
		}
		if (!ClientTeamState.isLeader()) {
			graphics.text(this.font, "설정은 팀 리더만 바꿀 수 있습니다.", left, y, TEXT_DIM);
			return;
		}
		graphics.text(this.font, "최대 체력 " + trimZero(ClientTeamState.maxHealth())
				+ "  (하트 " + trimZero(ClientTeamState.maxHealth() / 2) + "개)", left, y, TEXT_MAIN);
		graphics.text(this.font, "위치 교환 " + (ClientTeamState.swapEnabled()
						? ClientTeamState.swapIntervalMinutes() + "분 주기" : "꺼짐"),
				left, y + BUTTON_HEIGHT + 14, TEXT_MAIN);
		graphics.text(this.font, "증강 " + (ClientTeamState.perksEnabled() ? "사용 중" : "사용 안 함"),
				left, y + (BUTTON_HEIGHT + 14) * 2, TEXT_MAIN);
	}

	private void renderPerks(GuiGraphicsExtractor graphics, int left) {
		int y = PANEL_TOP;
		if (!ClientTeamState.inTeam()) {
			graphics.text(this.font, "팀이 있어야 증강을 봅니다.", left, y, TEXT_DIM);
			return;
		}
		if (!ClientTeamState.perksEnabled()) {
			graphics.text(this.font, "이 팀은 증강을 쓰지 않습니다.", left, y, TEXT_DIM);
			return;
		}
		List<String> owned = PerkClientState.ownedLines();
		if (owned.isEmpty()) {
			graphics.text(this.font, "아직 고른 증강이 없습니다.", left, y, TEXT_DIM);
			return;
		}
		graphics.text(this.font, "보유 증강 " + owned.size() + "개", left, y, TEXT_MAIN);
		y += ROW_HEIGHT + 2;
		for (String line : owned) {
			if (y > this.height - 60) {
				graphics.text(this.font, "…", left, y, TEXT_DIM);
				break;
			}
			graphics.text(this.font, "· " + line, left, y, TEXT_DIM);
			y += ROW_HEIGHT;
		}
	}

	// ------------------------------------------------------------------ 거들기

	private boolean isOnline(UUID id) {
		return this.minecraft != null && this.minecraft.getConnection() != null
				&& this.minecraft.getConnection().getPlayerInfo(id) != null;
	}

	private int onlineCount() {
		int count = 0;
		for (UUID id : ClientTeamState.memberIds()) {
			if (isOnline(id)) {
				count++;
			}
		}
		return count;
	}

	/** 20.0 처럼 소수점이 의미 없는 값을 "20" 으로 보여 준다. */
	private static String trimZero(float value) {
		return value == Math.rint(value)
				? String.valueOf((long) value)
				: String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	/** {@code /shareteam <rest>} 를 서버로 보낸다. */
	private void run(String rest) {
		Minecraft client = this.minecraft;
		if (client == null || client.getConnection() == null) {
			return;
		}
		client.getConnection().sendCommand("shareteam " + rest);
	}

	@Override
	public void tick() {
		String now = signature();
		if (!now.equals(lastSignature)) {
			rebuild();
		}
	}

	/** 위젯 구성을 좌우하는 값만 모은 요약. 이 값이 그대로면 다시 만들 필요가 없다. */
	private String signature() {
		return tab + "|" + ClientTeamState.inTeam() + "|" + ClientTeamState.isLeader()
				+ "|" + ClientTeamState.memberIds().size() + "|" + ClientTeamState.swapEnabled()
				+ "|" + ClientTeamState.swapIntervalMinutes() + "|" + ClientTeamState.perksEnabled()
				+ "|" + ClientTeamState.maxHealth() + "|" + PerkClientState.hasPending()
				+ "|" + invitableNames();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
