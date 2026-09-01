package com.sharedfate.perk.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkItemMatcher;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 정해진 아이템 무리를 무력하게 만드는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "item_ban", "tags": ["sharedfate:diamond_gear"] }
 * { "type": "item_ban", "items": ["minecraft:diamond_sword"], "tags": ["..."] }
 * { "type": "item_ban", "tags": ["sharedfate:diamond_gear"], "discard": true }
 * </pre>
 *
 * <h2>"사용 불가"를 어떻게 정했는가</h2>
 * <p>아예 손에 들지 못하게 하는 방법도 있었지만 그렇게 하지 않았다. 공유 인벤토리에서
 * 아이템을 강제로 빼내는 처리가 되고, 캐다가 갑자기 손에서 사라지는 그림이 되며,
 * 상자에 넣고 꺼내는 평범한 행동까지 막힌다. <b>들 수는 있되 아무 쓸모가 없는 쪽</b>을 골랐다.
 * 구체적으로는 이렇게 된다.
 * <ul>
 *   <li>방어구: 어떤 경로로도 착용되지 않는다. 이미 입고 있었다면 벗겨서 공유 인벤토리로 보낸다.</li>
 *   <li>도구: 채굴 속도가 맨손과 같아지고, 맨손으로는 못 캐는 블록의 드롭도 나오지 않는다.</li>
 *   <li>무기: 그 아이템이 얹어 주던 공격력이 사라진다. 즉 맨손으로 때린 것과 같아진다.</li>
 * </ul>
 *
 * <p>차단 지점은 {@code LivingEntityEquipBanMixin}(착용),
 * {@code EquippableSwapBanMixin}(우클릭 착용), {@code PlayerBannedToolMixin}(채굴),
 * {@link com.sharedfate.perk.PerkWeaponDamage}(공격력)에 흩어져 있고, 이미 입고 있던 장비를
 * 벗기는 일은 {@link com.sharedfate.perk.PerkGearManager} 가 맡는다.
 *
 * <h2>{@code discard}: 무력화가 아니라 자동 폐기</h2>
 * <p>{@code discard: true} 를 적으면 위의 "쓸모없이 만든다" 대신 <b>핫바에 있거나 장착된
 * 상태 자체를 허용하지 않는다</b> — {@link com.sharedfate.perk.PerkGearManager} 의 주기 점검이
 * 이 아이템이 핫바(9칸)에 있거나 방어구 칸에 걸쳐 있으면 그때마다 즉시 떨어뜨린다. 프리즘
 * 「금기의 광석」이 쓴다. 인벤토리 깊숙이(핫바를 벗어난 칸) 보관하는 것은 막지 않는다 — 어차피
 * 무력한 아이템을 쥐고만 있는 것과, 핫바·장착에서 계속 튕겨나가는 것은 다른 느낌이라 서버
 * 주인이 대상 태그만 바꿔 둘 수 있게 별도 플래그로 뒀다. 적지 않으면 예전처럼 무력화만 한다.
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 증강을 잃으면 물어볼 규칙이
 * 사라져 제한도 함께 풀린다.
 */
public final class ItemBanEffect implements PerkEffect {
	private final PerkItemMatcher matcher;
	private final boolean discard;

	public ItemBanEffect(PerkItemMatcher matcher, boolean discard) {
		this.matcher = matcher;
		this.discard = discard;
	}

	/** JSON에서 만든다. 가리키는 아이템이 없으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		PerkItemMatcher matcher = PerkItemMatcher.fromJson(perkId, "item_ban", json);
		if (matcher == null) {
			return null;
		}
		Boolean discard = readBoolean(perkId, json, "discard", false);
		if (discard == null) {
			return null;
		}
		return new ItemBanEffect(matcher, discard);
	}

	/** 이 아이템이 막힌 무리에 들어가는가. */
	public boolean matches(@Nullable ItemStack stack) {
		return matcher.matches(stack);
	}

	public PerkItemMatcher matcher() {
		return matcher;
	}

	/**
	 * 무력화 대신 핫바·장착에서 자동으로 떨어뜨리는가.
	 *
	 * <p>참이면 {@link com.sharedfate.perk.PerkGearManager} 가 핫바와 방어구 칸을 훑어 계속
	 * 버린다. 거짓(기본값)이면 예전처럼 들고만 있을 수 있고 쓸모만 없어진다.
	 */
	public boolean discard() {
		return discard;
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
					"증강 {}: item_ban 의 {} 가 참·거짓이 아닙니다 ({})", perkId, key, element);
			return null;
		}
		return element.getAsBoolean();
	}
}
