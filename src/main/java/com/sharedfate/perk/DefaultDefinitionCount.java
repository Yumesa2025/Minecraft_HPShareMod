package com.sharedfate.perk;

import java.util.OptionalInt;

/**
 * 「판을 올려도 옛 설정 파일이 그대로 쓰인다」는 사고를 잡기 위한 개수 비교.
 *
 * <p>{@link PerkRegistry} 와 {@link PerkSetRegistry} 는 {@code config/} 에 정의 파일이 있으면
 * 그 파일만 읽고 JAR 안의 기본 정의와 다시 비교하지 않는다 — 운영자가 값을 고쳐 쓸 수 있게
 * 하려는 의도이고 그 자체는 옳다. 문제는 그 대가로, 판을 올려 기본 정의가 늘어났는데 옛
 * 파일을 그대로 들고 있어도 "건너뜀 0개" 라는 로그만 남아 정상처럼 보인다는 점이다. 실제로
 * 최신 JAR 에 아주 오래된 정의 파일을 쓰다가 증강 카드에 세트 유형 줄이 안 뜨는 증상으로만
 * 겪고 원인을 한참 찾은 적이 있다.
 *
 * <p>그래서 서버가 뜰 때 한 번, 번들 기본 정의의 개수와 실제로 읽은 개수를 견줘 다르면
 * 경고만 남긴다. <b>막지는 않는다</b> — 운영자가 일부러 증강·세트를 줄여 쓸 수도 있어서다.
 *
 * <p>여기 있는 판정은 순수 함수라 시험이 쉽다. 번들 리소스를 읽고 파싱하는 일, 실제 로그
 * 문구를 조립하는 일은 {@link PerkRegistry}·{@link PerkSetRegistry} 쪽에 남겨 뒀다 — 그
 * 둘은 파일 IO 라 시험이 닿기 어렵다.
 */
public final class DefaultDefinitionCount {

	private DefaultDefinitionCount() {
	}

	/**
	 * 기대한 개수(번들 기본 정의)와 실제로 읽은 개수를 견줘 경고가 필요한지 판정한다.
	 *
	 * <p>번들을 못 읽었거나 파싱에 실패해 기대 개수를 모를 때는 {@code expected} 가 비어
	 * 있다. 이때는 경고하지 않는다 — 모르는 것을 안다고 우겨서 새 오류를 만들 이유가 없다.
	 *
	 * @param expected 번들 기본 정의의 개수. 못 읽었으면 empty
	 * @param actual   실제로 읽어 들인 개수
	 * @return 둘 다 알고 있는데 다르면 true
	 */
	public static boolean isStale(OptionalInt expected, int actual) {
		return expected.isPresent() && expected.getAsInt() != actual;
	}
}
