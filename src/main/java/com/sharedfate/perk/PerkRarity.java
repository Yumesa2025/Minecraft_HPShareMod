package com.sharedfate.perk;

import java.util.Locale;

/** 증강 등급. 구간이 오를수록 높은 등급이 나올 확률이 커진다. */
public enum PerkRarity {
	COMMON,
	RARE,
	EPIC;

	/** JSON의 rarity 문자열을 등급으로 바꾼다. 알 수 없는 값이면 null. */
	public static PerkRarity fromId(String id) {
		if (id == null) {
			return null;
		}
		try {
			return valueOf(id.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
