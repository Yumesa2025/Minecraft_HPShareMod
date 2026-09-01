package com.sharedfate.client.team;

import com.sharedfate.client.ClientAttackDamage;
import com.sharedfate.client.ClientTeamState;
import com.sharedfate.client.perk.ClientPerkFeatures;
import com.sharedfate.client.perk.PerkClientState;
import com.sharedfate.net.PerkSyncPayload;
import com.sharedfate.perk.effect.HideHudEffect;
import com.sharedfate.team.TeamCreationSettings;
import com.sharedfate.ui.GameStartButton;
import com.sharedfate.ui.PanelScroll;
import com.sharedfate.ui.StatSummary;
import com.sharedfate.ui.TeamCreationCycle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

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
	/** 기본값보다 내려간 능력치. 대가로 무언가를 깎는 증강이 있어 반드시 눈에 띄어야 한다. */
	private static final int TEXT_BAD = 0xFFFF6B6B;
	private static final int PANEL_BG = 0xC0101018;
	private static final int SCROLL_TRACK = 0x40FFFFFF;
	private static final int SCROLL_THUMB = 0xC0C0C6CC;

	/** 스크롤 막대의 폭. 판 오른쪽 여백(6px) 안에 들어가야 한다. */
	private static final int SCROLL_BAR_WIDTH = 3;
	/** 손잡이가 아무리 짧아져도 이만큼은 남긴다. 사라지면 어디까지 왔는지 알 수 없다. */
	private static final int SCROLL_THUMB_MIN = 12;
	/** 휠 한 칸에 밀리는 거리. 두 줄씩 움직이는 편이 손에 붙는다. */
	private static final int SCROLL_STEP = ROW_HEIGHT * 2;
	/** 증강 목록 아래에 「증강 선택 창 열기」 단추가 있을 때 비워 둘 자리. */
	private static final int PERK_LIST_BOTTOM_WITH_BUTTON = 58;
	/** 단추가 없을 때 비워 둘 자리. 판 바닥(height − 32)과 닫기 단추를 침범하지 않는 값이다. */
	private static final int PERK_LIST_BOTTOM_PLAIN = 36;

	/** 팀 이름 길이 상한. 서버의 {@code MAX_TEAM_NAME_LENGTH} 와 같아야 한다. */
	private static final int MAX_TEAM_NAME_LENGTH = 32;
	private static final int MIN_HEALTH = TeamCreationSettings.MIN_MAX_HEALTH;
	private static final int MAX_HEALTH = TeamCreationSettings.MAX_MAX_HEALTH;
	private static final int HEALTH_STEP = 2;

	/**
	 * 팀 만들기 탭의 설정 줄들.
	 *
	 * <p>정할 것이 일곱 가지로 늘어 한 줄에 하나씩 두면 창을 넘긴다. 절반씩 둘을 나란히 놓고,
	 * 숫자는 −/+ 두 단추 대신 <b>누를 때마다 값이 굴러가는</b> 단추 하나로 줄였다
	 * ({@link TeamCreationCycle}).
	 */
	private static final int FORM_TOP = PANEL_TOP + 34;
	private static final int FORM_ROW = 20;
	/** 설정 줄의 단추 높이. 기본 20 보다 낮춰야 일곱 가지가 창 안에 들어간다. */
	private static final int FORM_BUTTON_HEIGHT = 18;

	private enum Tab {
		STATUS("현황"),
		TEAM("팀"),
		SETTINGS("설정"),
		PERKS("증강"),
		/**
		 * 증강이 능력치를 얼마나 바꿨는지.
		 *
		 * <p>「증강」 탭 바로 뒤다. 저쪽이 <b>무엇을 가졌는지</b>라면 이쪽은 <b>그래서 얼마나
		 * 세졌는지</b>라 이어 읽힌다. 「현황」에 넣지 않은 것은 그 탭이 이미 아홉 줄이고, 창이
		 * 낮으면 아래 단추와 겹치기 때문이다.
		 */
		STATS("능력치");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.STATUS;
	private EditBox nameBox;
	/** 마지막으로 위젯을 만들 때의 팀 상태 요약. 달라지면 다시 만든다. */
	private String lastSignature = "";

	/**
	 * 증강 탭에 그릴 줄을 미리 접어 둔 것.
	 *
	 * <p>{@code font.split} 은 폭이 정해져야 할 수 있는 계산이라 {@link #init()} 에서 한 번만
	 * 한다. 매 프레임 다시 접으면 증강이 늘어날수록 그리기가 무거워진다.
	 */
	private final List<PerkLine> perkLines = new ArrayList<>();
	/** 증강 목록 전체의 세로 길이(px). 스크롤 범위의 분모다. */
	private int perkContentHeight;
	/** 증강 목록이 보이는 창의 세로 길이(px). */
	private int perkViewHeight;
	/** 증강 목록을 위로 밀어 올린 거리(px). 0이면 맨 위다. */
	private int perkScroll;

	/**
	 * 팀을 만들 때 정할 일곱 가지. 아직 팀이 없으니 서버에 있을 수 없어 화면이 들고 있다가
	 * 「팀 만들기」를 누를 때 명령 한 줄로 보낸다. 창을 닫았다 열면 기본값으로 돌아간다.
	 *
	 * <p><b>기본값은 서버의 {@link TeamCreationSettings} 와 같아야 한다.</b> 화면에 보이는
	 * 값과 아무것도 안 적었을 때 서버가 쓰는 값이 다르면, 사람이 화면을 보고 짐작한 것과
	 * 실제 팀이 어긋난다. 증강만 켬이고 나머지는 끔·서버 설정값이다.
	 *
	 * <p>두 알림과 난이도 상승은 <b>만든 뒤에 바꿀 수 없으므로</b> 켜는 것을 일부러 손으로
	 * 고르게 한다.
	 */
	private boolean newTeamPerks = TeamCreationSettings.DEFAULT_PERKS_ENABLED;
	private boolean newTeamDamageAlert;
	private boolean newTeamDeathAlert;
	private boolean newTeamDifficulty = TeamCreationSettings.DEFAULT_DIFFICULTY_ESCALATION;
	/**
	 * 서버가 정한 기본 최대 체력을 화면이 알 길이 없다 — 팀에 속하기 전에는 동기화가 오지
	 * 않는다. 명령이 받는 아래 끝(20)에서 시작한다.
	 */
	private int newTeamMaxHealth = MIN_HEALTH;
	private int newTeamSwapMinutes = TeamCreationCycle.SWAP_OFF;
	private int newTeamRerollCount = TeamCreationSettings.DEFAULT_REROLL_COUNT;
	/**
	 * 적다 만 팀 이름.
	 *
	 * <p>단추를 누르면 위젯을 통째로 다시 만들므로 {@link EditBox} 도 새것이 된다. 여기에
	 * 옮겨 두지 않으면 <b>켜고 끄기를 누를 때마다 적던 이름이 지워진다.</b>
	 */
	private String newTeamName = "";

	/**
	 * 「게임 시작」 단추가 확인 단계인가.
	 *
	 * <p>참이면 다음 누름이 실제로 명령을 보낸다. 되돌릴 수 없는 동작이라 한 번에 나가지 않게
	 * 한 것이고, 규칙은 {@link com.sharedfate.ui.GameStartButton} 에 적어 뒀다. 탭을 옮기거나
	 * 창을 닫으면 <b>반드시 풀린다</b> — 확인 단계로 둔 채 다른 일을 하다 돌아와서 무심코 누르는
	 * 것이 이 장치가 막으려던 바로 그 사고다.
	 */
	private boolean startConfirming;

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
			// 능력치 탭에는 위젯이 없다. 값 표시는 renderStats 가 맡는다.
			case STATS -> {
			}
		}

		addRenderableWidget(Button.builder(Component.literal("닫기"), button -> onClose())
				.bounds(left + PANEL_WIDTH / 2 - 50, this.height - 28, 100, BUTTON_HEIGHT).build());
	}

	private void switchTo(Tab next) {
		// 탭을 옮기면 목록을 맨 위부터 다시 본다. 틱마다 도는 rebuild() 는 자리를 지킨다.
		if (next != tab) {
			perkScroll = 0;
			// 확인 단계는 탭을 벗어나는 순간 풀린다.
			startConfirming = false;
		}
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
			nameBox = new EditBox(this.font, left, PANEL_TOP + 12, PANEL_WIDTH, FORM_BUTTON_HEIGHT,
					Component.literal("팀 이름"));
			nameBox.setMaxLength(MAX_TEAM_NAME_LENGTH);
			nameBox.setValue(newTeamName);
			nameBox.setResponder(value -> newTeamName = value);
			addRenderableWidget(nameBox);

			int half = PANEL_WIDTH / 2 - 2;
			int right = left + PANEL_WIDTH / 2 + 2;

			addRenderableWidget(toggle(left, formRowY(0), half, "증강", newTeamPerks,
					"사용", "사용 안 함", () -> newTeamPerks = !newTeamPerks));
			addRenderableWidget(toggle(right, formRowY(0), half, "난이도 상승", newTeamDifficulty,
					"켬", "끔", () -> newTeamDifficulty = !newTeamDifficulty));

			addRenderableWidget(toggle(left, formRowY(1), half, "피격 알림", newTeamDamageAlert,
					"켬", "끔", () -> newTeamDamageAlert = !newTeamDamageAlert));
			addRenderableWidget(toggle(right, formRowY(1), half, "사망 알림", newTeamDeathAlert,
					"켬", "끔", () -> newTeamDeathAlert = !newTeamDeathAlert));

			// 숫자 셋은 누를 때마다 다음 값으로 굴러간다. 위 끝을 넘으면 아래 끝으로 돌아온다.
			addRenderableWidget(cycle(left, formRowY(2), half,
					"최대 체력 — " + newTeamMaxHealth,
					() -> newTeamMaxHealth = TeamCreationCycle.nextMaxHealth(
							newTeamMaxHealth, MIN_HEALTH, MAX_HEALTH, HEALTH_STEP)));
			addRenderableWidget(cycle(right, formRowY(2), half,
					"위치 교환 — " + TeamCreationCycle.swapLabel(newTeamSwapMinutes),
					() -> newTeamSwapMinutes =
							TeamCreationCycle.nextSwapMinutes(newTeamSwapMinutes)));
			addRenderableWidget(cycle(left, formRowY(3), half,
					"다시 뽑기 — " + newTeamRerollCount + "회",
					() -> newTeamRerollCount = TeamCreationCycle.nextRerollCount(
							newTeamRerollCount,
							TeamCreationSettings.MIN_REROLL_COUNT,
							TeamCreationSettings.MAX_REROLL_COUNT)));

			addRenderableWidget(Button.builder(Component.literal("팀 만들기"),
					button -> createTeam())
					.bounds(left, formRowY(4) + 4, PANEL_WIDTH, FORM_BUTTON_HEIGHT).build());
			return;
		}

		nameBox = null;
		int y = PANEL_TOP + 14 + ROW_HEIGHT * (ClientTeamState.memberIds().size() + 1);

		boolean showStart = GameStartButton.visible(
				true, ClientTeamState.isLeader(), ClientTeamState.runStarted());
		// 「게임 시작」 단추와 그 위의 경고 한 줄이 들어갈 자리를 초대 단추가 침범하면 안 된다.
		// 겹치면 초대하려다 시작을 누르게 되는데, 그것이 이 화면에서 가장 나쁜 사고다.
		int inviteBottom = showStart ? this.height - 108 : this.height - 80;

		if (ClientTeamState.isLeader()) {
			for (String name : invitableNames()) {
				if (y > inviteBottom) {
					break;
				}
				addRenderableWidget(Button.builder(Component.literal(name + " 초대"),
						button -> run("invite " + name))
						.bounds(left, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
				y += BUTTON_HEIGHT + 2;
			}
		}

		if (showStart) {
			// 「팀 나가기」·「팀 해체」 한 줄 위. 되돌릴 수 없는 단추 셋이 나란히 서지만, 이것만
			// 판 전체 폭이라 눌러야 할 것과 눌러서는 안 될 것이 눈으로 갈린다.
			addRenderableWidget(Button.builder(
					Component.literal(GameStartButton.label(startConfirming))
							.withStyle(startConfirming ? ChatFormatting.RED : ChatFormatting.GREEN),
					button -> pressStart())
					.bounds(left, this.height - 78, PANEL_WIDTH, BUTTON_HEIGHT).build());
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

	/**
	 * 「게임 시작」 단추를 눌렀다.
	 *
	 * <p>첫 누름은 <b>아무것도 보내지 않고</b> 글자만 경고로 바꾼다. 두 번째 누름에서만 명령이
	 * 나가고, 그 명령도 서버에서 리더 여부와 이미 시작했는지를 처음부터 다시 확인한다.
	 */
	private void pressStart() {
		if (!startConfirming) {
			startConfirming = true;
			rebuild();
			return;
		}
		startConfirming = false;
		run(GameStartButton.CONFIRM_COMMAND);
	}

	/** 설정 줄 {@code index} 의 y 좌표. 0부터 센다. */
	private static int formRowY(int index) {
		return FORM_TOP + FORM_ROW * index;
	}

	/**
	 * 켜고 끄기 단추 하나. 누르면 값이 뒤집히고 화면을 다시 만든다.
	 *
	 * <p>글자만으로도 지금 값이 보이지만 색까지 바꾼다. 일곱을 훑을 때 무엇이 켜져 있는지
	 * 한눈에 들어와야 한다.
	 */
	private Button toggle(int x, int y, int width, String label, boolean value,
			String onText, String offText, Runnable flip) {
		Component text = Component.literal(label + " — " + (value ? onText : offText))
				.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY);
		return Button.builder(text, button -> {
			flip.run();
			rebuild();
		}).bounds(x, y, width, FORM_BUTTON_HEIGHT).build();
	}

	/**
	 * 숫자를 굴리는 단추 하나. 누르면 다음 값이 되고 화면을 다시 만든다.
	 *
	 * <p>켜고 끄기와 달리 색을 바꾸지 않는다. 어떤 값이 「켜짐」인지 정할 수 없는 값들이라,
	 * 초록·회색으로 물들이면 없는 뜻이 생긴다.
	 */
	private Button cycle(int x, int y, int width, String label, Runnable next) {
		return Button.builder(Component.literal(label), button -> {
			next.run();
			rebuild();
		}).bounds(x, y, width, FORM_BUTTON_HEIGHT).build();
	}

	/**
	 * 화면이 들고 있던 일곱을 모두 적어 보낸다.
	 *
	 * <p>적지 않은 항목은 서버가 기본값으로 두는데, 화면에는 이미 다른 값이 보이고 있을 수
	 * 있어 눈에 보이는 것과 실제가 어긋난다. 그래서 늘 완전한 형태를 보낸다. 낱말 순서는
	 * {@link TeamCreationCycle#createCommand} 가 서버 명령과 맞춰 둔다.
	 */
	private void createTeam() {
		if (nameBox == null) {
			return;
		}
		String name = nameBox.getValue().trim();
		if (name.isEmpty()) {
			return;
		}
		run(TeamCreationCycle.createCommand(newTeamPerks, newTeamDamageAlert, newTeamDeathAlert,
				newTeamDifficulty, newTeamMaxHealth, newTeamSwapMinutes, newTeamRerollCount, name));
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

	/**
	 * 설정 탭에는 단추가 하나도 없다.
	 *
	 * <p>최대 체력·위치 교환·증강 사용 여부는 <b>팀을 만들 때만</b> 정하는 값이 되어, 누르면
	 * 서버가 거부만 하는 단추가 셋 남아 있었다. 없는 기능을 있는 것처럼 보여 주는 쪽이 더
	 * 나쁘므로 단추를 걷어내고 {@link #renderSettings} 가 글자로만 보여 준다.
	 */
	private void initSettings(int left) {
		// 그릴 위젯이 없다. 값 표시는 renderSettings 가 맡는다.
	}

	// ------------------------------------------------------------------ 증강

	private void initPerks(int left) {
		if (ClientTeamState.inTeam() && PerkClientState.hasPending()) {
			addRenderableWidget(Button.builder(
					Component.literal("증강 선택 창 열기"), button -> run("perk"))
					.bounds(left, this.height - 54, PANEL_WIDTH, BUTTON_HEIGHT).build());
		}
		layoutPerkList();
	}

	/**
	 * 증강 목록을 미리 접어 두고 스크롤 범위를 다시 잰다.
	 *
	 * <p>증강을 여럿 가지면 목록이 창을 넘쳐 아래쪽이 보이지 않던 자리다. 예전에는 넘치는
	 * 만큼을 「…」 한 줄로 잘라 버려서 <b>나중에 고른 증강일수록 확인할 길이 없었다.</b>
	 * 이제는 넘치면 자르지 않고 스크롤로 내려 본다.
	 */
	private void layoutPerkList() {
		perkLines.clear();
		int top = perkListTop();
		perkViewHeight = Math.max(ROW_HEIGHT, perkListBottom() - top);

		int height = 0;
		List<PerkSyncPayload.Owned> owned = PerkClientState.owned();
		for (PerkSyncPayload.Owned perk : owned) {
			perkLines.add(new PerkLine(
					Component.literal("· " + perk.name()).getVisualOrderText(),
					0, rarityColor(perk.rarity()), ROW_HEIGHT));
			height += ROW_HEIGHT;

			// 설명은 폭에 맞춰 접는다. 이름만으로는 무엇을 들고 있는지 알 수 없다.
			List<FormattedCharSequence> wrapped =
					this.font.split(Component.literal(perk.description()), PANEL_WIDTH - 8);
			for (int index = 0; index < wrapped.size(); index++) {
				// 증강과 증강 사이는 마지막 설명 줄의 키를 늘려 벌린다. 빈 줄을 넣는 것보다
				// 스크롤 계산이 단순하다.
				int lineHeight = index == wrapped.size() - 1 ? ROW_HEIGHT + 2 : ROW_HEIGHT;
				perkLines.add(new PerkLine(wrapped.get(index), 8, TEXT_DIM, lineHeight));
				height += lineHeight;
			}
		}
		perkContentHeight = height;
		perkScroll = PanelScroll.clamp(perkScroll, perkContentHeight, perkViewHeight);
	}

	/** 증강 목록이 시작하는 y. 「보유 증강 N개」 머리글 바로 아래다. */
	private int perkListTop() {
		return PANEL_TOP + ROW_HEIGHT + 2;
	}

	/** 증강 목록이 끝나는 y. 아래 단추와 판 바닥을 침범하지 않는다. */
	private int perkListBottom() {
		boolean hasOpenButton = ClientTeamState.inTeam() && PerkClientState.hasPending();
		return this.height
				- (hasOpenButton ? PERK_LIST_BOTTOM_WITH_BUTTON : PERK_LIST_BOTTOM_PLAIN);
	}

	/** 증강 목록 한 줄. 접어 둔 글자와 들여쓰기, 색, 차지하는 세로 길이를 함께 든다. */
	private record PerkLine(FormattedCharSequence text, int indent, int color, int height) {
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
			case STATS -> renderStats(graphics, left);
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

		// 시작 전에는 이 줄이 맨 앞에 와야 한다. 아래의 레벨·증강을 먼저 읽으면 회차가 이미
		// 굴러가는 줄 안다. 단추는 여기 두지 않는다 — 창을 열자마자 보이는 자리에 되돌릴 수
		// 없는 단추가 있으면 잘못 누른다.
		if (!ClientTeamState.runStarted()) {
			graphics.text(this.font, GameStartButton.waitingNotice(ClientTeamState.isLeader()),
					left, y, TEXT_WARN);
			y += ROW_HEIGHT + 2;
		}

		graphics.text(this.font, "팀 레벨 " + ClientTeamState.teamLevel(), left, y, TEXT_GOOD);
		y += ROW_HEIGHT;
		int remaining = ClientTeamState.levelsToNextPerk();
		graphics.text(this.font, remaining < 0 ? "남은 증강 없음" : "다음 증강까지 " + remaining,
				left, y, TEXT_WARN);
		y += ROW_HEIGHT + 2;

		// 현재 체력은 적지 않는다. 여기서는 팀의 상한만 확인하고, 지금 얼마나 남았는지는
		// HUD 의 하트로 본다. 숫자와 하트가 한 화면에서 어긋나 보이는 일도 함께 없어진다.
		//
		// 「장님 거인」처럼 체력 표시를 가리는 증강이 있어도 이 줄은 그대로 둔다. 그 증강이
		// 감추는 것은 <b>지금 체력</b>이고, 상한은 어차피 설정 탭에서도 그냥 보인다.
		// 여기만 「???」로 가리면 두 탭이 서로 다른 말을 하게 된다.
		graphics.text(this.font, "공유 최대 체력 " + trimZero(ClientTeamState.maxHealth())
				+ "  (하트 " + trimZero(ClientTeamState.maxHealth() / 2) + "개)",
				left, y, TEXT_MAIN);
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
			graphics.text(this.font, "새 팀 이름을 적고 일곱 가지를 정한 뒤 만드세요.",
					left, y, TEXT_DIM);
			// 「팀 만들기」 단추 바로 아래. 일곱 가지 전부 되돌릴 수 없으므로 눈에 띄는 색으로
			// 적고, 줄 수를 둘로 줄여 창이 낮을 때 닫기 단추와 겹치지 않게 한다.
			int noteY = formRowY(4) + 4 + FORM_BUTTON_HEIGHT + 6;
			graphics.text(this.font, "일곱 가지 모두 팀을 만들 때만 정합니다. 바꾸려면 팀을 해체하세요.",
					left, noteY, TEXT_WARN);
			graphics.text(this.font, "숫자 단추는 누를 때마다 다음 값으로 바뀝니다.",
					left, noteY + ROW_HEIGHT, TEXT_DIM);
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

		if (!ClientTeamState.runStarted()) {
			// 단추 바로 위. 무엇을 잃는지는 단추 글자에도 적히지만, 확인 단계로 넘어가기 전에
			// 한 번은 읽혀야 한다.
			int noteY = this.height - 78 - ROW_HEIGHT - 4;
			graphics.text(this.font, ClientTeamState.isLeader()
					? "시작하면 모든 아이템이 사라지고 팀원이 스폰으로 모입니다. 되돌릴 수 없습니다."
					// 리더가 아닌 사람에게는 여기서도 그 사람이 할 수 있는 것만 적는다.
					: GameStartButton.waitingNotice(false),
					left, noteY, TEXT_WARN);
		}
	}

	/**
	 * 설정 탭. <b>보여 주기만 한다.</b>
	 *
	 * <p>아무도 값을 바꿀 수 없지만 <b>보는 것은 리더만</b>이다. 그래서 안내 문구도
	 * 「리더만 바꿀 수 있습니다」가 아니라 못 바꾼다는 사실만 적는다.
	 */
	private void renderSettings(GuiGraphicsExtractor graphics, int left) {
		int y = PANEL_TOP;
		if (!ClientTeamState.inTeam()) {
			graphics.text(this.font, "팀이 있어야 설정을 볼 수 있습니다.", left, y, TEXT_DIM);
			return;
		}
		if (!ClientTeamState.isLeader()) {
			graphics.text(this.font, "설정은 리더만 볼 수 있습니다.", left, y, TEXT_DIM);
			return;
		}
		graphics.text(this.font, "최대 체력 " + trimZero(ClientTeamState.maxHealth())
				+ "  (하트 " + trimZero(ClientTeamState.maxHealth() / 2) + "개)", left, y, TEXT_MAIN);
		y += ROW_HEIGHT;
		graphics.text(this.font, "위치 교환 " + (ClientTeamState.swapEnabled()
						? ClientTeamState.swapIntervalMinutes() + "분 주기" : "꺼짐"),
				left, y, TEXT_MAIN);
		y += ROW_HEIGHT;
		graphics.text(this.font, "증강 " + (ClientTeamState.perksEnabled() ? "사용 중" : "사용 안 함"),
				left, y, TEXT_MAIN);
		y += ROW_HEIGHT;
		graphics.text(this.font, "피격 알림 " + onOffText(ClientTeamState.damageAlertEnabled()),
				left, y, TEXT_MAIN);
		y += ROW_HEIGHT;
		graphics.text(this.font, "사망 알림 " + onOffText(ClientTeamState.deathAlertEnabled()),
				left, y, TEXT_MAIN);

		y += ROW_HEIGHT + 6;
		graphics.text(this.font, "이 설정들은 팀을 만들 때 정한 값이라 바꿀 수 없습니다.",
				left, y, TEXT_DIM);
		y += ROW_HEIGHT;
		graphics.text(this.font, "바꾸려면 리더가 팀을 해체하고 다시 만들어야 합니다.",
				left, y, TEXT_DIM);
	}

	private static String onOffText(boolean value) {
		return value ? "켜짐" : "꺼짐";
	}

	// ------------------------------------------------------------------ 능력치

	/**
	 * 능력치 탭. <b>증강이 무엇을 얼마나 바꿨는지</b>를 바닐라 기본값과 나란히 보여 준다.
	 *
	 * <h2>값은 클라이언트가 이미 갖고 있다 — 통신 규약을 올리지 않았다</h2>
	 * <p>여기 적는 것은 전부 바닐라 속성이고, 서버는 {@code ServerEntity} 에서 그것을
	 * <b>본인에게도</b> 보낸다({@code sendToTrackingPlayersAndSelf}). 증강이 거는 수정자도
	 * 임시(transient) 수정자일 뿐이라 그 묶음에 함께 실린다. 그래서 {@code getBaseValue()} 가
	 * 바닐라 기본값, {@code getValue()} 가 증강·장비까지 얹힌 지금 값이 된다.
	 *
	 * <h2>공격력만은 서버가 보내 준다</h2>
	 * <p>{@code minecraft:attack_damage} 만은 바닐라가 <b>클라이언트에 동기화하지 않는다</b> —
	 * {@code Attributes} 에서 이 속성만 {@code setSyncable(true)} 없이 등록되고, 장비의 속성
	 * 수정자를 실제로 붙이는 {@code LivingEntity.detectEquipmentUpdates} 도 서버에서만 돈다.
	 * 그래서 클라이언트가 스스로 읽는 공격력은 <b>맨손 기본값</b>일 뿐이고 무기도 증강도 들어
	 * 있지 않다. 그 한 줄을 위해 {@code AttackDamagePayload} 를 따로 두었고, 그것이 채우는
	 * {@link ClientAttackDamage} 에서 읽는다. 서버가 아직 알려 주지 않았으면 줄을 건너뛴다.
	 *
	 * <h2>줄의 차례</h2>
	 * <p>최대 체력 · 공격력 · 방어력 · 이동 속도 순이다. 앞의 셋이 전투의 세 축이라 붙여 두고
	 * ({@code 얼마나 버티는가 → 얼마나 때리는가 → 얼마나 덜 맞는가}), 성격이 다른 이동 속도를
	 * 맨 뒤에 둔다.
	 *
	 * <h2>「장님 거인」과의 관계</h2>
	 * <p>그 증강이 감추는 것은 <b>지금 체력</b>과 허기이고, 여기 적는 것은 <b>상한</b>이다.
	 * 상한은 「현황」·「설정」 탭에서도 그냥 보이므로 여기서만 가리면 세 탭이 서로 다른 말을 한다.
	 * 다만 방어력은 사정이 다르다 — HUD 의 방어구 칸이 곧 이 숫자라, 그것을 가리는 증강이
	 * 붙으면 여기서도 가린다.
	 */
	private void renderStats(GuiGraphicsExtractor graphics, int left) {
		int y = PANEL_TOP;
		LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
		if (player == null) {
			graphics.text(this.font, "능력치를 읽을 수 없습니다.", left, y, TEXT_DIM);
			return;
		}

		graphics.text(this.font, "바닐라 기본값 → 지금 값  (증감)", left, y, TEXT_DIM);
		y += ROW_HEIGHT + 4;

		y = statRow(graphics, player, left, y, "최대 체력", Attributes.MAX_HEALTH,
				StatSummary.Unit.RAW, heartSuffix(player), false);
		y = attackDamageRow(graphics, left, y);
		y = statRow(graphics, player, left, y, "방어력", Attributes.ARMOR,
				StatSummary.Unit.RAW, "",
				ClientPerkFeatures.isHidden(HideHudEffect.Element.ARMOR));
		y = statRow(graphics, player, left, y, "이동 속도", Attributes.MOVEMENT_SPEED,
				StatSummary.Unit.PERCENT, "", false);

		y += 6;
		if (!ClientTeamState.inTeam()) {
			graphics.text(this.font, "팀에 들어가면 증강이 이 값들을 바꿉니다.", left, y, TEXT_DIM);
			y += ROW_HEIGHT;
			graphics.text(this.font, "공격력에는 마법부여와 치명타가 빠져 있습니다.",
					left, y, TEXT_DIM);
			return;
		}
		graphics.text(this.font, "최대 체력은 팀이 정한 값과 증강이 함께 반영된 값입니다.",
				left, y, TEXT_DIM);
		y += ROW_HEIGHT;
		graphics.text(this.font, "공격력·방어력·이동 속도에는 지금 든 장비도 들어 있습니다.",
				left, y, TEXT_DIM);
		y += ROW_HEIGHT;
		graphics.text(this.font, "공격력에는 마법부여와 치명타가 빠져 있습니다.",
				left, y, TEXT_DIM);
	}

	/**
	 * 공격력 한 줄. 값은 속성이 아니라 서버가 보낸 것에서 읽는다.
	 *
	 * <p>아직 받지 못했으면 <b>줄 자체를 건너뛴다.</b> 다른 줄이 속성을 찾지 못했을 때와 같은
	 * 처리다 — 여기서 맨손 기본값 1.0 을 그리면 무기도 증강도 빠진 숫자를 진짜인 양 적게 되고,
	 * 그것이 바로 이 값을 따로 받아 오게 만든 문제다.
	 */
	private int attackDamageRow(GuiGraphicsExtractor graphics, int left, int y) {
		if (!ClientAttackDamage.known()) {
			return y;
		}
		return statRow(graphics, left, y, "공격력",
				ClientAttackDamage.base(), ClientAttackDamage.current(), StatSummary.Unit.RAW, "");
	}

	/**
	 * 능력치 한 줄을 그리고 다음 줄의 y 를 돌려준다.
	 *
	 * <p>속성을 찾지 못하면 <b>줄 자체를 건너뛴다.</b> 다른 모드가 속성을 지웠거나 하는 드문
	 * 경우인데, 거기서 0 을 적으면 없는 사실을 만들어 낸다.
	 */
	private int statRow(GuiGraphicsExtractor graphics, LocalPlayer player, int left, int y,
			String label, Holder<Attribute> attribute, StatSummary.Unit unit, String suffix,
			boolean masked) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return y;
		}
		if (masked) {
			graphics.text(this.font, label + "  ???", left, y, TEXT_DIM);
			return y + ROW_HEIGHT;
		}
		return statRow(graphics, left, y, label,
				instance.getBaseValue(), instance.getValue(), unit, suffix);
	}

	/**
	 * 값을 이미 손에 쥐고 있을 때의 한 줄.
	 *
	 * <p>공격력은 속성에서 읽지 못하고 패킷으로 받으므로 이 갈래로 들어온다. 그리는 방법은
	 * 나머지 셋과 <b>한 곳에서</b> 정해 둔다 — 색과 표기가 줄마다 달라지면 안 된다.
	 */
	private int statRow(GuiGraphicsExtractor graphics, int left, int y, String label,
			double base, double current, StatSummary.Unit unit, String suffix) {
		graphics.text(this.font, StatSummary.line(label, base, current, unit) + suffix,
				left, y, statColor(StatSummary.direction(base, current)));
		return y + ROW_HEIGHT;
	}

	/** 하트 개수. 숫자만으로는 몇 칸인지 세어 봐야 알기 때문에 함께 적는다. */
	private static String heartSuffix(LocalPlayer player) {
		return "  (하트 " + StatSummary.number(player.getMaxHealth() / 2.0) + "개)";
	}

	/** 올랐으면 초록, 내렸으면 빨강, 그대로면 보통 글자색. */
	private static int statColor(int direction) {
		if (direction > 0) {
			return TEXT_GOOD;
		}
		return direction < 0 ? TEXT_BAD : TEXT_MAIN;
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
		List<PerkSyncPayload.Owned> owned = PerkClientState.owned();
		if (owned.isEmpty()) {
			graphics.text(this.font, "아직 고른 증강이 없습니다.", left, y, TEXT_DIM);
			return;
		}
		boolean overflows = PanelScroll.overflows(perkContentHeight, perkViewHeight);
		graphics.text(this.font, "보유 증강 " + owned.size() + "개"
				+ (overflows ? "  (휠로 넘겨 보세요)" : ""), left, y, TEXT_MAIN);

		// 창 밖으로 나가는 줄이 그려지지 않게 자른다. 자르지 않으면 스크롤한 목록이 머리글과
		// 아래 단추를 덮어쓴다.
		int top = perkListTop();
		int bottom = top + perkViewHeight;
		graphics.enableScissor(left - 4, top, left + PANEL_WIDTH + 4, bottom);
		int lineY = top - perkScroll;
		for (PerkLine line : perkLines) {
			if (lineY + ROW_HEIGHT > top && lineY < bottom) {
				graphics.text(this.font, line.text(), left + line.indent(), lineY, line.color());
			}
			lineY += line.height();
		}
		graphics.disableScissor();

		if (overflows) {
			renderPerkScrollBar(graphics, left, top);
		}
	}

	/** 목록 오른쪽의 스크롤 막대. 지금 어디쯤을 보고 있는지만 알려 준다. */
	private void renderPerkScrollBar(GuiGraphicsExtractor graphics, int left, int top) {
		int barLeft = left + PANEL_WIDTH + 1;
		int barRight = barLeft + SCROLL_BAR_WIDTH;
		graphics.fill(barLeft, top, barRight, top + perkViewHeight, SCROLL_TRACK);
		int thumb = PanelScroll.thumbHeight(perkContentHeight, perkViewHeight, SCROLL_THUMB_MIN);
		int thumbTop = PanelScroll.thumbTop(top, perkViewHeight, thumb, perkScroll,
				perkContentHeight);
		graphics.fill(barLeft, thumbTop, barRight, thumbTop + thumb, SCROLL_THUMB);
	}

	/**
	 * 증강 탭에서 휠을 굴리면 목록을 위아래로 옮긴다.
	 *
	 * <p>넘칠 것이 없으면 아무것도 하지 않고 넘겨서, 위젯이 휠을 쓰는 다른 탭의 동작을
	 * 가로채지 않는다.
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.PERKS && scrollY != 0.0
				&& PanelScroll.overflows(perkContentHeight, perkViewHeight)) {
			int moved = perkScroll - (int) Math.round(scrollY) * SCROLL_STEP;
			perkScroll = PanelScroll.clamp(moved, perkContentHeight, perkViewHeight);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** 등급별 글자색. 서버가 보낸 등급 이름을 그대로 받는다. */
	private static int rarityColor(String rarity) {
		return switch (rarity) {
			case "gold" -> 0xFFFFC63A;
			case "prism" -> 0xFF5FE0D8;
			default -> 0xFFC0C6CC;
		};
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
				// 시작하면 단추가 사라져야 하고, 확인 단계가 바뀌면 글자와 색이 바뀐다.
				+ "|" + ClientTeamState.runStarted() + startConfirming
				+ "|" + ClientTeamState.memberIds().size() + "|" + ClientTeamState.swapEnabled()
				+ "|" + ClientTeamState.swapIntervalMinutes() + "|" + ClientTeamState.perksEnabled()
				+ "|" + ClientTeamState.maxHealth() + "|" + PerkClientState.hasPending()
				// 증강이 늘면 목록을 다시 접어야 한다. init() 이 그 일을 한다.
				+ "|" + PerkClientState.owned().size()
				+ "|" + newTeamPerks + newTeamDamageAlert + newTeamDeathAlert + newTeamDifficulty
				+ "|" + newTeamMaxHealth + "," + newTeamSwapMinutes + "," + newTeamRerollCount
				+ "|" + invitableNames();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
