package com.sharedfate.ui;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 세트 줄에 마우스를 올렸을 때 뜨는 「아직 없는 것」 목록의 계산.
 *
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

	// ------------------------------------------------------------------ 단계 설명

	/**
	 * 세트 단계 하나. {@code net.PerkSetSyncPayload.TierLine} 에서 그대로 옮겨 담는다.
	 *
	 * @param count       이 단계가 열리는 데 필요한 개수
	 * @param description 무엇을 하는 단계인가. <b>서버가 보낸 글이다</b>
	 * @param active      지금 켜져 있는가. 화면이 켜진 줄을 밝게 그린다
	 */
	public record TierEntry(String typeId, int count, String description, boolean active) {
	}

	/**
	 * 유형 하나의 툴팁 한 덩어리. 화면이 색을 입혀 그린다.
	 *
	 * <p>선택 카드와 팀 화면이 <b>같은 것</b>을 쓴다. 두 곳에서 따로 만들면 한쪽만 고쳐지는
	 * 사고가 난다.
	 *
	 * @param progress 「채굴 2/3」 같은 진행도 줄
	 * @param tiers    단계 줄들. 개수 오름차순이고, 켜진 것은 {@link TierEntry#active()} 가 참
	 * @param missing  아직 안 가진 증강. 이미 잘려 있다
	 */
	public record Body(String progress, List<TierEntry> tiers, Trimmed missing) {

		public Body {
			tiers = List.copyOf(tiers);
		}
	}

	/**
	 * 이 유형의 단계만 골라 <b>열리는 개수 오름차순</b>으로 돌려준다.
	 *
	 * <p>차례를 여기서 한 번만 정한다. 서버가 보낸 차례에 기대면 정의 파일에 단계를 거꾸로
	 * 적은 서버에서 툴팁이 거꾸로 뜬다.
	 */
	public static List<TierEntry> tiersOf(List<TierEntry> all, String typeId) {
		if (all == null || typeId == null || typeId.isEmpty()) {
			return List.of();
		}
		List<TierEntry> picked = new ArrayList<>();
		for (TierEntry tier : all) {
			if (tier != null && typeId.equals(tier.typeId())) {
				picked.add(tier);
			}
		}
		picked.sort(java.util.Comparator.comparingInt(TierEntry::count));
		return List.copyOf(picked);
	}

	/**
	 * 「채굴 2/3」 같은 진행도 줄.
	 *
	 * <p><b>분모는 실제로 있는 단계만 가리킨다.</b> 더 열 것이 없으면 <b>최고 단계</b>를 적어
	 * 「채굴 10/4」가 된다. 가진 개수를 분모로 삼으면 「채굴 10/10」이 되어 10단계가 있는 것처럼
	 * 읽히는데, 바로 아래에 그리는 단계 줄에는 2·3·4 밖에 없어 눈앞에서 어긋난다.
	 *
	 * <p>단계가 하나도 없는 유형에서는 지어낼 숫자가 없으므로 분수를 아예 적지 않는다.
	 *
	 * @param tiers 이 유형의 단계들. 개수가 적은 것부터 정렬되어 있어야 한다
	 */
	public static String progress(String displayName, int owned, int nextThreshold,
			List<TierEntry> tiers) {
		String name = displayName == null ? "" : displayName;
		int goal = nextThreshold > 0 ? nextThreshold : highestCount(tiers);
		return goal > 0 ? name + " " + owned + "/" + goal : name + " " + owned;
	}

	/** 단계들 중 가장 큰 {@code count}. 하나도 없으면 0. */
	private static int highestCount(@Nullable List<TierEntry> tiers) {
		int highest = 0;
		if (tiers != null) {
			for (TierEntry tier : tiers) {
				if (tier != null) {
					highest = Math.max(highest, tier.count());
				}
			}
		}
		return highest;
	}

	/**
	 * 툴팁 본문을 한 번에 만든다. 화면은 이것을 받아 그리기만 하면 된다.
	 *
	 * @param maxRows 「아직 없는 것」을 몇 줄까지 적을지. 단계 줄은 자르지 않는다 —
	 *                넷을 넘는 유형이 없고, 단계는 이 툴팁의 본론이라 접으면 뜻이 없다
	 */
	public static Body describe(String typeId, String displayName, int owned, int nextThreshold,
			List<TierEntry> allTiers, List<Entry> catalog, int maxRows) {
		List<TierEntry> tiers = tiersOf(allTiers, typeId);
		return new Body(progress(displayName, owned, nextThreshold, tiers),
				tiers,
				trim(missingOf(catalog, typeId), maxRows));
	}
}
