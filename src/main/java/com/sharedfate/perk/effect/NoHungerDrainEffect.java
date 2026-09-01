package com.sharedfate.perk.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import org.jetbrains.annotations.Nullable;

/**
 * 허기가 아예 줄지 않게 한다.
 *
 * <p>정의는 {@code { "type": "no_hunger_drain" }} 이고, 필드 {@code includeNaturalRegen}
 * 은 선택이며 기본값은 {@code false} 다. 프리즘 1 고행자가 최대 체력 10 고정의 대가로
 * {@code includeNaturalRegen: true} 를 얹어 이 타입을 쓴다.
 *
 * <p>{@link HungerDrainEffect} 와 같은 자리에서 같은 방식으로 동작한다. 소모도가 쌓이는 입구인
 * {@code Player.causeFoodExhaustion} 에서 배율을 0 으로 만들 뿐이다. 배율 0 으로 적어도 결과는
 * 같지만, "떨어지지 않는다"는 정의에 숫자를 쓰게 하면 오타 하나로 조용히 되살아나므로 뜻이
 * 분명한 타입을 따로 뒀다.
 *
 * <h2>자연 회복의 대가는 기본적으로 면제되지 않는다</h2>
 * <p>이 효과가 언제나 면제해 주는 것은 <b>플레이어의 행동</b>이 치르는 소모도다. 달리기·점프·
 * 수영·채굴·공격, 그리고 같은 통로를 쓰는 허기 상태이상이 여기에 든다.
 *
 * <p>자연 회복은 다르다. 마인크래프트는 체력을 돌려주는 대가로 그 자리에서 소모도를 치르게
 * 하는데, 그 대가까지 0 이 되면 체력이 공짜로 무한히 차오른다. 그래서 기본값
 * ({@code includeNaturalRegen} 을 안 적었거나 {@code false})에서는 회복의 대가를 그대로
 * 통과시킨다. 26.2 의 {@code FoodData.tick} 은 회복 갈래에서 {@code causeFoodExhaustion} 이
 * 아니라 {@code FoodData.addExhaustion} 을 직접 부르므로 배율이 걸리는 자리를 애초에 지나가지
 * 않고, 그 갈림을 통로에 기대지 않고 못 박아 두는 것이
 * {@link com.sharedfate.mixin.FoodDataRegenExhaustionMixin} 이다.
 *
 * <h2>{@code includeNaturalRegen: true} — 고행자만의 예외</h2>
 * <p>고행자는 최대 체력이 10 으로 영영 고정된다는 대가가 이미 있어, 자연 회복까지 공짜로
 * 만들어도 "체력이 공짜로 무한히 차오른다"는 문제가 다른 팀만큼 크지 않다. 그래서 이 필드를
 * 참으로 두면 {@link com.sharedfate.perk.PerkFoodRules#addNaturalRegenExhaustion} 이 자연
 * 회복의 대가도 건너뛴다. 이 필드가 없는(또는 거짓인) 다른 팀에는 위 기본 규칙이 그대로
 * 적용된다 — 이 타입을 나중에 다른 증강이 가져다 써도 자연 회복까지 공짜가 되는 사고가
 * 저절로 일어나지 않는다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 소모도가 쌓이는 한가운데서 "이 팀이
 * 이 효과를 갖고 있는가", "자연 회복까지 면제하는가"만 물어보면 되므로, 이 클래스는 그 물음에
 * 답하기 위한 표시로만 존재한다. {@code includeNaturalRegen} 이 거짓인 흔한 경우에는 여러
 * 증강이 {@link #INSTANCE} 하나를 돌려써도 안전하도록 그대로 뒀다.
 */
public final class NoHungerDrainEffect implements PerkEffect {
	/** {@code includeNaturalRegen} 이 없거나 거짓인 흔한 경우 하나만 만들어 돌려쓴다. */
	public static final NoHungerDrainEffect INSTANCE = new NoHungerDrainEffect(false);

	private final boolean includeNaturalRegen;

	private NoHungerDrainEffect(boolean includeNaturalRegen) {
		this.includeNaturalRegen = includeNaturalRegen;
	}

	/** JSON에서 만든다. {@code includeNaturalRegen} 이 참·거짓이 아니면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Boolean includeNaturalRegen = readBoolean(perkId, json, "includeNaturalRegen", false);
		if (includeNaturalRegen == null) {
			return null;
		}
		return includeNaturalRegen ? new NoHungerDrainEffect(true) : INSTANCE;
	}

	/** 체력 자연 회복이 치르는 소모도의 대가까지 면제하는가. 고행자만 참이다. */
	public boolean includeNaturalRegen() {
		return includeNaturalRegen;
	}

	/** 참·거짓 필드. 없으면 {@code fallback}, 적었는데 참·거짓이 아니면 null. */
	private static @Nullable Boolean readBoolean(String perkId, JsonObject json, String key,
			boolean fallback) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: no_hunger_drain 의 {} 가 참·거짓이 아닙니다 ({})", perkId, key, element);
			return null;
		}
		return element.getAsBoolean();
	}
}
