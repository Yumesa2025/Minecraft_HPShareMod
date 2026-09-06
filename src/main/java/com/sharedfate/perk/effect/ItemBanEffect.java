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
 * <p><b>들 수는 있되 아무 쓸모가 없다.</b> 구체적으로는 이렇게 된다.
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
 * <h2>{@code discard}: 무력화가 아니라 핫바에서 밀어내기</h2>
 * <p>{@code discard: true} 를 적으면 위의 "쓸모없이 만든다" 대신 <b>핫바에 있거나 장착된
 * 상태 자체를 허용하지 않는다</b> — {@link com.sharedfate.perk.PerkGearManager} 의 주기 점검이
 * 이 아이템을 핫바(9칸)나 방어구 칸에서 찾으면 <b>인벤토리 위쪽 보관 칸으로 올려 보낸다.</b>
 * 프리즘 「금기의 광석」이 쓴다. 인벤토리 깊숙이(핫바를 벗어난 칸) 보관하는 것은 막지 않는다.
 * 적지 않으면 무력화만 한다.
 *
 * <p><b>보관 칸까지 꽉 차서 옮길 자리가 없을 때만 떨어뜨린다.</b> 이름은 {@code discard} 지만
 * 버리는 것은 마지막 수단이다. 밀어낼 자리를 셀 때는 확장 인벤토리
 * ({@code ExpandedInventoryManager}) 의 추가 칸도 함께 센다 — 플레이어 눈에는 그것도 그냥
 * 인벤토리 칸이라, 빼고 세면 빈 칸을 눈앞에 두고 아이템이 버려진다.
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
	 * 무력화 대신 핫바·장착 칸에서 자동으로 밀어내는가.
	 *
	 * <p>참이면 {@link com.sharedfate.perk.PerkGearManager} 가 핫바와 방어구 칸을 훑어 보관
	 * 칸으로 올려 보내고, 보관 칸까지 꽉 찼을 때만 떨어뜨린다. 거짓(기본값)이면 들고만 있을 수
	 * 있고 쓸모만 없어진다.
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
