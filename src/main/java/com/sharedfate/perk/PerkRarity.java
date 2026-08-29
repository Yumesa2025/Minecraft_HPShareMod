package com.sharedfate.perk;

import java.util.Locale;

/**
 * 증강 등급.
 *
 * <p>구간마다 등급이 하나로 정해지고 그 등급 안에서만 후보 3개를 뽑는다. 한 라운드에
 * 여러 등급이 섞이지 않는다. 구간 → 등급 배정은 {@link PerkDraft#rarityFor} 가 맡는다.
 */
public enum PerkRarity {
	SILVER("실버"),
	GOLD("골드"),
	PRISM("프리즘");

	private final String displayName;

	PerkRarity(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * JSON의 rarity 문자열을 등급으로 바꾼다. 알 수 없는 값이면 null.
	 *
	 * <p>등급 이름을 바꾸기 전에 쓰던 {@code common / rare / epic} 과 {@code platinum} 도
	 * 계속 받아준다. 사용자가 이미 채워 둔 {@code config/sharedfate-perks.json} 이 조용히
	 * 통째로 버려지는 편보다 낫기 때문이다.
	 */
	public static PerkRarity fromId(String id) {
		if (id == null) {
			return null;
		}
		String normalized = id.trim().toUpperCase(Locale.ROOT);
		switch (normalized) {
			case "COMMON":
				return SILVER;
			case "RARE":
				return GOLD;
			case "EPIC":
			case "PLATINUM":
				return PRISM;
			default:
				break;
		}
		try {
			return valueOf(normalized);
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** 화면과 채팅에 쓰는 한국어 이름. 실버 / 골드 / 프리즘. */
	public String displayName() {
		return displayName;
	}
}
