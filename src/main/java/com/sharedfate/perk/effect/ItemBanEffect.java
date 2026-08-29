package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
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
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 증강을 잃으면 물어볼 규칙이
 * 사라져 제한도 함께 풀린다.
 */
public final class ItemBanEffect implements PerkEffect {
	private final PerkItemMatcher matcher;

	public ItemBanEffect(PerkItemMatcher matcher) {
		this.matcher = matcher;
	}

	/** JSON에서 만든다. 가리키는 아이템이 없으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		PerkItemMatcher matcher = PerkItemMatcher.fromJson(perkId, "item_ban", json);
		return matcher == null ? null : new ItemBanEffect(matcher);
	}

	/** 이 아이템이 막힌 무리에 들어가는가. */
	public boolean matches(@Nullable ItemStack stack) {
		return matcher.matches(stack);
	}

	public PerkItemMatcher matcher() {
		return matcher;
	}
}
