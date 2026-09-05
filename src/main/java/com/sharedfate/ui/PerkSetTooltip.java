package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 세트 줄에 마우스를 올렸을 때 뜨는 「아직 없는 것」 목록의 계산.
 *
 * <p>{@link PerkSetLines} 와 같은 이유로 공용 소스셋에 있다. 그리는 자리는
 * {@code client/team/TeamScreen} 이고 시험 소스셋이 그쪽을 보지 못한다.
 *
 * <h2>왜 잘라야 하는가</h2>
 * <p>채굴에는 증강이 <b>열 개</b> 있다. 하나도 안 가진 채로 마우스를 올리면 툴팁이 열 줄이
 * 되어 팀 화면의 절반을 덮고, GUI 배율이 큰 화면에서는 아래가 잘려 <b>무엇이 잘렸는지조차</b>
 * 보이지 않는다. 그래서 몇 줄만 보이고 나머지는 「… 외 N개」 한 줄로 접는다. 접은 줄이 있다는
 * 사실 자체를 반드시 적는 이유는, 말없이 자르면 「채굴은 이게 전부구나」로 읽히기 때문이다.
 */
public final class PerkSetTooltip {
	/**
	 * 이름을 그대로 늘어놓을 줄 수의 상한.
	 *
	 * <p>머리글 한 줄과 합쳐 일곱 줄이다. 가장 많은 유형(채굴 10개)에서도 툴팁이 팀 화면 판을
	 * 넘지 않는 길이다.
	 */
	public static final int MAX_ROWS = 6;

	private PerkSetTooltip() {
	}

	/**
	 * 툴팁에 적을 증강 하나.
	 *
	 * @param perkName 증강 이름
	 * @param rarity   등급 문자열({@code silver}/{@code gold}/{@code prism}). 화면이 글자색으로 쓴다
	 */
	public record Missing(String perkName, String rarity) {
	}

	/**
	 * 자른 결과.
	 *
	 * @param shown  실제로 이름을 적을 것들
	 * @param hidden 접힌 개수. 0 이면 「외 N개」 줄을 붙이지 않는다
	 */
	public record Trimmed(List<Missing> shown, int hidden) {

		/** 접힌 것이 있는가. */
		public boolean truncated() {
			return hidden > 0;
		}
	}

	/**
	 * {@code catalog} 에서 이 유형의 <b>아직 안 가진</b> 증강만 골라낸다.
	 *
	 * <p>유형 열쇠는 문자열로 견준다. 클라이언트는 {@code PerkSetType} 을 아예 몰라도 되고,
	 * 모르는 유형 id 가 섞여 와도 그냥 아무 줄에도 안 붙는다.
	 *
	 * @param typeId 고를 유형 id
	 */
	public static List<Missing> missingOf(List<Entry> catalog, String typeId) {
		if (catalog == null || typeId == null || typeId.isEmpty()) {
			return List.of();
		}
		List<Missing> missing = new ArrayList<>();
		for (Entry entry : catalog) {
			if (entry != null && !entry.owned() && typeId.equals(entry.typeId())) {
				missing.add(new Missing(entry.perkName(), entry.rarity()));
			}
		}
		return List.copyOf(missing);
	}

	/**
	 * 이름표 하나. {@code net.PerkSetSyncPayload.CatalogEntry} 에서 그대로 옮겨 담는다.
	 *
	 * <p>{@link PerkSetLines.Entry} 와 같은 이유로 여기 따로 둔다 — 패킷 레코드를 받으면
	 * 마인크래프트 네트워크 클래스가 이 파일까지 따라 들어와 시험이 게임을 요구하게 된다.
	 */
	public record Entry(String typeId, String perkName, String rarity, boolean owned) {
	}

	/**
	 * 앞에서부터 {@code limit} 개만 남기고 나머지 개수를 센다.
	 *
	 * <p>차례는 서버가 보낸 그대로다. 서버가 등급 순으로 실어 주면 툴팁도 등급 순으로 뜬다 —
	 * 여기서 다시 정렬하면 <b>목록을 어떤 차례로 볼지가 두 곳에 적히게</b> 되어 한쪽만 고쳐지는
	 * 사고가 난다.
	 */
	public static Trimmed trim(List<Missing> missing, int limit) {
		if (missing == null || missing.isEmpty()) {
			return new Trimmed(List.of(), 0);
		}
		if (limit <= 0) {
			return new Trimmed(List.of(), missing.size());
		}
		if (missing.size() <= limit) {
			return new Trimmed(List.copyOf(missing), 0);
		}
		return new Trimmed(List.copyOf(new ArrayList<>(missing.subList(0, limit))),
				missing.size() - limit);
	}

	/** 접힌 줄을 알리는 글자. {@code hidden} 이 0 이하면 빈 문자열. */
	public static String overflowLine(int hidden) {
		return hidden <= 0 ? "" : "… 외 " + hidden + "개";
	}

	/**
	 * 툴팁 첫 줄.
	 *
	 * <p>전부 모았을 때는 「아직 없는 것」이 거짓말이 된다. 그래서 남은 것이 없으면 다른 말을
	 * 한다 — 그 유형은 더 집을 것이 없다는 뜻이고, 그것도 알아야 할 정보다.
	 */
	public static String header(String displayName, int missingCount) {
		String name = displayName == null ? "" : displayName;
		return missingCount <= 0 ? name + " — 전부 모았습니다" : name + " — 아직 없는 것";
	}
}
