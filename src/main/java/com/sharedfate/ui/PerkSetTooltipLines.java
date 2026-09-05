package com.sharedfate.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 세트 툴팁 한 덩어리를 <b>화면이 그대로 그릴 줄들</b>로 바꾸는 계산.
 *
 * <p>{@link PerkSetTooltip} 이 「무엇을 담을지」를 정한다면 여기는 「어떤 글자와 어떤 색으로
 * 놓을지」를 정한다. 뜨는 자리가 둘이라서 나눠 두었다 — 증강 선택 카드의 <b>유형 줄</b>과 그
 * 화면 왼쪽 <b>세트 판</b>이 같은 것을 띄워야 한다. 두 곳에서 따로 만들면 한쪽만 고쳐진다.
 *
 * <h2>켜진 단계와 안 켜진 단계는 반드시 갈려야 한다</h2>
 * <p>이 툴팁의 본론은 「지금 무엇이 켜져 있고 하나 더 모으면 무엇이 켜지는가」다. 네 줄이
 * 같은 색으로 늘어서면 그 물음에 아무 답도 못 한다. 그래서 켜진 줄은 밝게, 아직인 줄은
 * 흐리게 준다.
 */
public final class PerkSetTooltipLines {
	/** 단계 번호와 설명 사이. 요청받은 모습 그대로다 — {@code "2:  광물에 …"}. */
	public static final String TIER_SEPARATOR = ":  ";
	/** 「아직 없는 것」 이름 앞에 붙는 점. */
	public static final String MISSING_BULLET = "· ";

	private PerkSetTooltipLines() {
	}

	/**
	 * 그릴 줄 하나.
	 *
	 * @param text  적을 글자. 빈 문자열이면 줄 사이를 벌리는 빈 줄이다
	 * @param color 0xAARRGGBB 색. 화면이 알파를 떼어 쓴다
	 */
	public record Row(String text, int color) {

		/** 줄 사이를 벌리려고 넣은 빈 줄인가. */
		public boolean blank() {
			return text.isEmpty();
		}
	}

	/**
	 * 줄마다 쓸 색.
	 *
	 * <p>색을 밖에서 받는 이유는 이 계산이 {@code src/client} 의 화면 상수를 몰라야 하기
	 * 때문이다. 시험은 알아보기 쉬운 가짜 색을 넣고 <b>어느 줄에 어느 색이 갔는지</b>만 본다.
	 *
	 * @param progress      「채굴 2/3」 진행도 줄
	 * @param tierActive    이미 켜진 단계
	 * @param tierIdle      아직 안 켜진 단계
	 * @param missingHeader 「채굴 — 아직 없는 것」 머리글
	 * @param silver        실버 증강 이름
	 * @param gold          골드 증강 이름
	 * @param prism         프리즘 증강 이름
	 * @param overflow      「… 외 N개」
	 */
	public record Palette(int progress, int tierActive, int tierIdle, int missingHeader,
			int silver, int gold, int prism, int overflow) {

		/**
		 * 등급 문자열에 맞는 글자색. 모르는 등급은 실버로 본다.
		 *
		 * <p>서버가 새 등급을 만들어 보내도 툴팁이 검은 글자로 사라지면 안 된다.
		 */
		public int rarityColor(String rarity) {
			if (rarity == null) {
				return silver;
			}
			return switch (rarity) {
				case "gold" -> gold;
				case "prism" -> prism;
				default -> silver;
			};
		}
	}

	/** 단계 한 줄의 글자. {@code "3:  다이아몬드 광석을 …"}. */
	public static String tierLine(PerkSetTooltip.TierEntry tier) {
		if (tier == null) {
			return "";
		}
		String description = tier.description() == null ? "" : tier.description();
		return tier.count() + TIER_SEPARATOR + description;
	}

	/**
	 * 툴팁 줄들을 만든다.
	 *
	 * <p>담기는 차례는 <b>진행도 → 단계 → 빈 줄 → 아직 없는 것</b>이다. 지금 상태를 먼저 말하고
	 * 그 다음에 「무엇을 더 집으면 되는가」를 말한다.
	 *
	 * <p>진행도도 단계도 없으면 <b>빈 목록</b>을 돌려준다. 서버가 모르는 유형 id 를 보냈거나
	 * 세트 패킷이 아직 안 왔을 때인데, 그때 「— 전부 모았습니다」만 뜨면 새빨간 거짓말이 된다.
	 *
	 * @param body        {@code ClientPerkSets.tooltip(typeId)} 가 준 덩어리
	 * @param displayName 유형의 한국어 이름. 「아직 없는 것」 머리글에 쓴다
	 * @param missingLimit 「아직 없는 것」을 몇 줄까지 적을지. {@code body} 가 이미 잘려 있어도
	 *                     여기서 <b>더</b> 줄일 수 있다 — 유형이 둘인 증강은 툴팁이 두 배가 된다
	 */
	public static List<Row> build(PerkSetTooltip.Body body, String displayName, int missingLimit,
			Palette palette) {
		if (body == null || palette == null) {
			return List.of();
		}
		String progress = body.progress() == null ? "" : body.progress();
		List<PerkSetTooltip.TierEntry> tiers = body.tiers();
		if (progress.isEmpty() && tiers.isEmpty()) {
			return List.of();
		}

		List<Row> rows = new ArrayList<>();
		if (!progress.isEmpty()) {
			rows.add(new Row(progress, palette.progress()));
		}
		for (PerkSetTooltip.TierEntry tier : tiers) {
			if (tier == null) {
				continue;
			}
			rows.add(new Row(tierLine(tier),
					tier.active() ? palette.tierActive() : palette.tierIdle()));
		}

		// 이미 한 번 잘린 것을 또 자른다. 자른 개수는 더해야 「외 N개」가 진실이 된다.
		PerkSetTooltip.Trimmed given = body.missing();
		PerkSetTooltip.Trimmed shown = PerkSetTooltip.trim(given.shown(), missingLimit);
		int hidden = shown.hidden() + given.hidden();
		int total = given.shown().size() + given.hidden();

		if (!rows.isEmpty()) {
			// 단계 설명과 이름 목록은 성격이 다르다. 빈 줄 하나가 그 경계를 말한다.
			rows.add(new Row("", palette.overflow()));
		}
		rows.add(new Row(PerkSetTooltip.header(displayName, total), palette.missingHeader()));
		for (PerkSetTooltip.Missing missing : shown.shown()) {
			rows.add(new Row(MISSING_BULLET + missing.perkName(),
					palette.rarityColor(missing.rarity())));
		}
		if (hidden > 0) {
			rows.add(new Row(PerkSetTooltip.overflowLine(hidden), palette.overflow()));
		}
		return List.copyOf(rows);
	}
}
