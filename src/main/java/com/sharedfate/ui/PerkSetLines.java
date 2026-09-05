package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 세트 효과를 <b>몇 줄로, 어떤 글자로, 어느 차례로</b> 보여 줄지 정하는 계산.
 *
 * <p>{@link PanelScroll}·{@link StatSummary} 와 같은 이유로 공용 소스셋에 있다 — 이 줄들을
 * 그리는 자리가 셋이나 되는데({@code client/hud/CoordinateHud},
 * {@code client/team/TeamScreen}, 그리고 그 위의 툴팁) 전부 {@code src/client} 라 시험
 * 소스셋이 보지 못한다. 마인크래프트 클래스가 하나도 들어오지 않으므로 게임 없이 시험한다.
 *
 * <h2>가진 것이 0인 유형은 뺀다</h2>
 * <p>유형이 열한 개다. 전부 그리면 <b>「기동 0/2」 같은 줄 여덟 개가 화면을 덮는다.</b> 아직
 * 한 발도 딛지 않은 유형은 알려 줄 것이 없다 — 그 유형에 무엇이 있는지는 팀 화면의 툴팁에서
 * 본다. 그래서 여기서는 {@code owned >= 1} 인 것만 남긴다.
 *
 * <h2>켜진 것이 위로 온다</h2>
 * <p>지금 몸에 붙어 있는 효과가 먼저고, 그 다음이 「조금만 더 모으면 되는 것」이다. 눈이
 * 위에서 아래로 흐르는 동안 <b>사실 → 계획</b> 순서로 읽힌다. 서버가 보낸 차례를 그대로
 * 쓰면 켜진 세트가 목록 한가운데 끼어 눈에 띄지 않는다.
 */
public final class PerkSetLines {
	/** 켜진 세트 앞에 붙는 표. 속이 찬 마름모다. */
	public static final String ACTIVE_MARK = "◆";
	/** 아직 안 켜진 세트 앞에 붙는 표. 속이 빈 마름모다. */
	public static final String PROGRESS_MARK = "◇";

	/**
	 * HUD 에 그릴 줄 수의 상한.
	 *
	 * <p>좌표·바이옴 아래에 붙는 자리라 화면 왼쪽 위를 통째로 먹으면 안 된다. 한 회차에 고르는
	 * 증강이 여덟 개 남짓이라 유형이 여덟 가지로 흩어지는 일은 드물고, 그렇게까지 흩어졌다면
	 * 어차피 켜진 세트가 없어 급히 볼 줄도 없다.
	 */
	public static final int MAX_HUD_LINES = 6;

	private PerkSetLines() {
	}

	/**
	 * 유형 하나의 진행 상황. {@code net.PerkSetSyncPayload.SetLine} 에서 그대로 옮겨 담는다.
	 *
	 * <p>패킷 레코드를 여기서 바로 받지 않는 이유는 그것이 마인크래프트 네트워크 클래스를
	 * 끌고 오기 때문이다. 옮겨 담는 한 줄이 시험을 게임에서 떼어 놓는 값을 한다.
	 *
	 * @param typeId        유형 id. 툴팁이 이름표를 고를 때 쓰는 열쇠다
	 * @param displayName   화면에 적을 한국어 이름
	 * @param owned         지금 가진 개수
	 * @param nextThreshold 다음 단계에 필요한 개수. 더 오를 곳이 없으면 0
	 * @param activeTier    켜진 단계. 안 켜졌으면 0
	 */
	public record Entry(String typeId, String displayName, int owned, int nextThreshold,
			int activeTier) {

		/** 세트 효과가 이미 켜져 있는가. */
		public boolean active() {
			return activeTier > 0;
		}

		/** 화면에 뜰 만한 것이 있는가. 한 개도 없는 유형은 알려 줄 것이 없다. */
		public boolean worthShowing() {
			return owned > 0;
		}

		/**
		 * 분모로 적을 수.
		 *
		 * <p>다음 단계가 있으면 그 개수다. 더 오를 곳이 없으면 <b>가진 개수를 그대로</b> 분모에
		 * 놓아 「3/3」처럼 꽉 찬 모습으로 적는다. 「채굴 3」처럼 분모를 지우면 바로 아래 줄의
		 * 「방어 1/2」와 모양이 달라져 두 줄을 견주기 어렵다.
		 */
		public int goal() {
			return nextThreshold > 0 ? nextThreshold : Math.max(1, owned);
		}
	}

	/** 그릴 줄 하나. 색은 화면이 {@link #active()} 를 보고 고른다. */
	public record Line(String typeId, String text, boolean active) {
	}

	/**
	 * 「◆ 채굴 3/3」 한 줄의 글자.
	 *
	 * <p>표를 앞에 두는 이유는 <b>색만으로 켜짐을 나타내면 색을 못 가리는 사람에게 아무것도
	 * 전해지지 않기</b> 때문이다. 색은 거들 뿐이고 뜻은 마름모가 진다.
	 */
	public static String label(Entry entry) {
		return (entry.active() ? ACTIVE_MARK : PROGRESS_MARK) + " " + entry.displayName()
				+ " " + entry.owned() + "/" + entry.goal();
	}

	/**
	 * 화면에 실제로 그릴 줄들.
	 *
	 * <p>가진 것이 없는 유형을 빼고, 켜진 것을 위로 올리고, {@code limit} 줄까지만 남긴다.
	 * 잘릴 때 없어지는 것은 <b>가장 덜 모은 유형</b>이다 — 정렬을 먼저 하기 때문이다.
	 *
	 * @param entries 서버가 보낸 그대로의 유형 목록. 순서를 건드리지 않는다(복사해서 정렬한다)
	 * @param limit   남길 줄 수의 상한. 0 이하면 빈 목록
	 */
	public static List<Line> visible(List<Entry> entries, int limit) {
		if (entries == null || limit <= 0) {
			return List.of();
		}
		List<Entry> kept = new ArrayList<>(entries.size());
		for (Entry entry : entries) {
			if (entry != null && entry.worthShowing()) {
				kept.add(entry);
			}
		}
		// 안정 정렬이라 같은 값끼리는 서버가 보낸 차례가 그대로 남는다. 그래야 줄이 프레임마다
		// 자리를 바꾸지 않는다.
		kept.sort(Comparator
				// 켜진 것이 먼저.
				.comparing((Entry entry) -> !entry.active())
				// 켜진 것끼리는 높은 단계가 먼저.
				.thenComparingInt(entry -> -entry.activeTier())
				// 아직인 것끼리는 많이 모은 쪽이 먼저.
				.thenComparingInt(entry -> -entry.owned()));

		List<Line> lines = new ArrayList<>(Math.min(limit, kept.size()));
		for (int index = 0; index < kept.size() && index < limit; index++) {
			Entry entry = kept.get(index);
			lines.add(new Line(entry.typeId(), label(entry), entry.active()));
		}
		return List.copyOf(lines);
	}

	/**
	 * 가장 긴 줄의 폭.
	 *
	 * <p>세트 줄 위에 긋는 구분선의 길이가 이 값이다. 짧은 줄에 맞추면 선이 글자를 덜 덮고,
	 * 화면 폭에 맞추면 왼쪽 위를 가로지르는 큰 선이 되어 좌표보다 눈에 먼저 든다.
	 *
	 * <p>글자 폭은 폰트가 안다. {@link InventoryStatPanel} 과 같은 이유로 <b>재는 일을 밖에서
	 * 받는다</b> — 화면은 {@code font::width} 를 넘기고 시험은 가짜 자를 넘긴다.
	 *
	 * @param minimum 줄이 아무리 짧아도 이만큼은 긋는다. 줄이 없으면 0 이다
	 */
	public static int blockWidth(List<Line> lines, java.util.function.ToIntFunction<String> measure,
			int minimum) {
		if (lines == null || lines.isEmpty() || measure == null) {
			return 0;
		}
		int widest = Math.max(0, minimum);
		for (Line line : lines) {
			widest = Math.max(widest, measure.applyAsInt(line.text()));
		}
		return widest;
	}

	/**
	 * 세트 덩어리가 차지하는 세로 길이.
	 *
	 * <p>줄이 하나도 없으면 <b>0</b>이다. 그래야 세트를 하나도 모으지 않은 사람의 화면에서
	 * 아래 목록이 이유 없이 내려가지 않는다. 팀 화면은 이 값을 {@code perkListTop()} 에 더해
	 * 스크롤 계산과 잘라내기를 한 번에 따라오게 한다.
	 *
	 * @param lineCount  그릴 줄 수
	 * @param lineHeight 한 줄의 높이
	 * @param gap        덩어리 아래에 둘 틈
	 */
	public static int blockHeight(int lineCount, int lineHeight, int gap) {
		if (lineCount <= 0) {
			return 0;
		}
		return lineCount * Math.max(0, lineHeight) + Math.max(0, gap);
	}

	/**
	 * 마우스가 올라가 있는 줄의 차례. 어느 줄에도 없으면 −1.
	 *
	 * <p>가로 범위까지 보는 이유는, 세트 덩어리가 판 왼쪽에 있고 오른쪽은 빈자리라 그쪽에서도
	 * 툴팁이 뜨면 <b>마우스를 어디에 두어도 무언가 뜨는</b> 화면이 되기 때문이다.
	 *
	 * @param width  줄 하나가 마우스를 받는 가로 길이. 보통은 글자 폭이다
	 * @param top    첫 줄의 윗변
	 */
	public static int rowAt(double mouseX, double mouseY, int left, int width, int top,
			int lineHeight, int lineCount) {
		if (lineCount <= 0 || lineHeight <= 0 || width <= 0) {
			return -1;
		}
		if (mouseX < left || mouseX >= left + width) {
			return -1;
		}
		if (mouseY < top || mouseY >= top + lineCount * lineHeight) {
			return -1;
		}
		return (int) ((mouseY - top) / lineHeight);
	}
}
