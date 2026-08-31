package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkItemMatcher;
import org.jetbrains.annotations.Nullable;

/**
 * 증강을 고른 그 순간, 팀이 지금 들고·입고·쌓아 둔 도구·무기·방어구를 전부 몰수해
 * 다음 회차(전멸 이후) 시작 인벤토리로 돌려주기로 예약하는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "legacy_gear" }
 * </pre>
 *
 * <p>다른 필드는 없다. 대상은 언제나 {@code sharedfate:legacy_gear} 태그
 * ({@code data/sharedfate/tags/item/legacy_gear.json}) 하나로 고정한다. {@code item_ban} 이
 * {@code sharedfate:diamond_gear} 를 쓰는 것과 같은 방식이다. 서버 주인은 데이터팩으로 그
 * 태그만 덮어써서 대상 목록을 바꿀 수 있다.
 *
 * <h2>이 효과는 {@link #apply}/{@link #remove}에서 아무 일도 하지 않는다</h2>
 * <p>몰수는 {@link com.sharedfate.perk.PerkManager#applyChoice}가 부르는
 * {@link com.sharedfate.perk.PerkLegacyGear#sacrificeOnChoice} 한 곳에서 딱 한 번 일어난다.
 * {@code item_grant}({@link ItemGrantEffect})와 정반대 방향(주는 대신 뺏는다)이지만 "고른
 * 순간 한 번"이라는 시점은 같다. 접속·부활 때마다 다시 도는 {@link #apply}에서 몰수를 하면
 * 접속할 때마다 아이템이 또 사라지므로 절대 거기서 하면 안 된다.
 *
 * <h2>방어구 네 칸은 이 태그로 잡지 않는다</h2>
 * <p>지금 입고 있는 방어구는 {@link com.sharedfate.team.SharedEquipmentStore}의 HEAD·CHEST·
 * LEGS·FEET 네 칸에 있고, 아이템이 흩어져 쌓이는 목록이 아니다. {@link
 * com.sharedfate.perk.PerkLegacyGear}가 이 네 칸은 태그 판정 없이 직접 비운다. 이 태그(그리고
 * 여기 담긴 {@link #matcher()})는 {@code mainItems}·{@code extraItems}·공유 엔더상자처럼
 * 아이템 스택이 줄지어 있는 곳에만 쓰인다 — 거기에 <b>넣어 둔</b> 여벌 방어구까지 잡으려고
 * 태그 목록 자체에는 {@code #minecraft:trimmable_armor}가 들어 있다.
 */
public final class LegacyGearEffect implements PerkEffect {
	/** 대상 아이템 무리를 가리키는 고정 태그. 서버 주인이 데이터팩으로 덮어쓸 수 있다. */
	public static final String LEGACY_GEAR_TAG = "sharedfate:legacy_gear";

	private final PerkItemMatcher matcher;

	private LegacyGearEffect(PerkItemMatcher matcher) {
		this.matcher = matcher;
	}

	/** JSON에서 만든다. 대상 태그는 항상 고정이라 실패하는 경우가 없다. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		JsonObject synthetic = new JsonObject();
		JsonArray tags = new JsonArray();
		tags.add(LEGACY_GEAR_TAG);
		synthetic.add("tags", tags);
		PerkItemMatcher matcher = PerkItemMatcher.fromJson(perkId, "legacy_gear", synthetic);
		return matcher == null ? null : new LegacyGearEffect(matcher);
	}

	/** 인벤토리·엔더상자에 흩어진 아이템을 고를 때 쓰는 판정기. */
	public PerkItemMatcher matcher() {
		return matcher;
	}
}
