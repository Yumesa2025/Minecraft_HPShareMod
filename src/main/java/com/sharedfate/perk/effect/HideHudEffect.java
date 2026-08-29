package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 바닐라 HUD 의 정해진 칸을 가리는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "hide_hud", "elements": ["health", "food"] }
 * </pre>
 *
 * <p>{@code elements} 에 적을 수 있는 값은 {@link Element} 넷뿐이다. 모르는 값은 경고를
 * 남기고 건너뛰고, 그렇게 걸러낸 뒤 남은 것이 하나도 없으면 정의를 버린다.
 *
 * <h2>가리는 것이지 없애는 것이 아니다</h2>
 * <p>체력도 허기도 그대로 깎이고 그대로 죽는다. 보이지 않을 뿐이다. 그래서 이 효과는
 * 서버 쪽 계산에 전혀 끼어들지 않고, "무엇을 가릴지"를 클라이언트에 알려 주기만 한다.
 * 알려 주는 길은 {@link com.sharedfate.perk.PerkClientRules} 이고, 실제로 그리지 않는 일은
 * 클라이언트의 {@code ClientPerkFeatures} 와 Fabric 의 {@code HudElementRegistry} 가 한다.
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. {@link EquipBanEffect} 와 같은
 * 구도로, 플레이어에게 붙였다 떼는 것이 아니라 그때그때 물어보는 규칙이다. 증강을 잃으면
 * 다음 동기화에서 가림이 저절로 풀린다.
 */
public final class HideHudEffect implements PerkEffect {
	/**
	 * 가릴 수 있는 HUD 칸.
	 *
	 * <p>{@link #id()} 는 JSON 에 적는 이름이자 패킷에 실리는 이름이다. 두 자리에서 같은
	 * 문자열을 쓰므로 이름을 바꾸면 정의 파일과 프로토콜이 함께 바뀐다.
	 */
	public enum Element {
		HEALTH("health"),
		FOOD("food"),
		ARMOR("armor"),
		AIR("air");

		private final String id;

		Element(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** JSON·패킷의 이름에 맞는 칸. 모르는 값이면 null. */
		public static @Nullable Element fromId(@Nullable String raw) {
			if (raw == null) {
				return null;
			}
			String normalized = raw.trim().toLowerCase(Locale.ROOT);
			for (Element element : values()) {
				if (element.id.equals(normalized)) {
					return element;
				}
			}
			return null;
		}
	}

	private final Set<Element> elements;

	public HideHudEffect(Set<Element> elements) {
		this.elements = elements.isEmpty()
				? EnumSet.noneOf(Element.class) : EnumSet.copyOf(elements);
	}

	/** JSON에서 만든다. 가릴 칸이 하나도 남지 않으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		List<String> raw = PerkEffectType.readStringList(json, "elements");
		if (raw == null || raw.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: hide_hud 효과에 elements 배열이 없습니다", perkId);
			return null;
		}

		Set<Element> elements = EnumSet.noneOf(Element.class);
		for (String name : raw) {
			Element element = Element.fromId(name);
			if (element == null) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: hide_hud 가 모르는 칸 이름 {} 을 건너뜁니다", perkId, name);
				continue;
			}
			elements.add(element);
		}

		if (elements.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: hide_hud 에 가릴 수 있는 칸이 하나도 없습니다", perkId);
			return null;
		}
		return new HideHudEffect(elements);
	}

	/** 가려지는 칸들. */
	public Set<Element> elements() {
		return elements;
	}

	/** 이 칸이 가려지는가. */
	public boolean hides(@Nullable Element element) {
		return element != null && elements.contains(element);
	}
}
