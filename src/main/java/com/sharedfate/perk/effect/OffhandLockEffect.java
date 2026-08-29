package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 왼손 칸을 정해진 아이템 하나에게 내주는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "offhand_lock", "item": "minecraft:totem_of_undying" }
 * </pre>
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>왼손 칸에는 지정 아이템만 들어간다. 다른 것을 넣으려 하면 거절되고, 명령이나 다른 모드가
 *       억지로 넣었더라도 다음 점검 때 공유 인벤토리로 되돌아간다.</li>
 *   <li>왼손이 비어 있는데 공유 인벤토리에 지정 아이템이 있으면 자동으로 한 개를 끌어와 채운다.
 *       그래서 아이템을 꺼내 놔도 곧 제자리로 돌아간다. "고정"은 이 자동 채움으로 이뤄진다.</li>
 * </ul>
 *
 * <h2>아이템이 사라지면</h2>
 * <p>불사의 토템은 쓰면 없어진다. <b>없어진 토템을 이 효과가 새로 만들어 주지는 않는다.</b>
 * 그렇게 하면 죽지 않는 팀이 되어 증강이 아니라 치트가 된다. 지급은 같은 증강에 붙은
 * {@code item_grant} 가 고른 순간 한 번만 하고, 그 뒤로 왼손 칸은 팀이 다른 토템을 구해 올 때까지
 * 빈 채로 잠겨 있다. 즉 "왼손 칸 점유"라는 대가는 토템이 없어도 그대로 남는다.
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 왼손을 실제로 지키는 일은
 * {@link com.sharedfate.perk.PerkGearManager} 와 {@code SlotOffhandLockMixin} 이 한다.
 */
public final class OffhandLockEffect implements PerkEffect {
	private final Identifier itemId;

	private @Nullable Item resolved;
	private boolean resolveFailed;

	public OffhandLockEffect(Identifier itemId) {
		this.itemId = itemId;
	}

	/** JSON에서 만든다. 아이템 이름이 없거나 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		String raw = PerkEffectType.readString(json, "item");
		if (raw == null || raw.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: offhand_lock 효과에 item 필드가 없습니다", perkId);
			return null;
		}
		Identifier itemId = Identifier.tryParse(raw.trim());
		if (itemId == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 아이템 이름 {}", perkId, raw);
			return null;
		}
		return new OffhandLockEffect(itemId);
	}

	/** 왼손을 차지하는 아이템의 이름. */
	public Identifier itemId() {
		return itemId;
	}

	/** 이 묶음이 왼손에 놓일 수 있는 그 아이템인가. */
	public boolean matches(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = resolve();
		return item != null && stack.getItem() == item;
	}

	/**
	 * 지정 아이템을 레지스트리에서 찾는다. 찾지 못하면 null.
	 *
	 * <p>정의를 읽는 시점이 아니라 처음 물어볼 때 찾는다. 정의를 읽을 때는 레지스트리가 아직
	 * 준비되지 않았을 수 있다.
	 */
	public @Nullable Item resolve() {
		if (resolved != null || resolveFailed) {
			return resolved;
		}
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(itemId);
			// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
			if (found.isEmpty() || found.get().value() == Items.AIR) {
				resolveFailed = true;
				SharedFateMod.LOGGER.warn("왼손 고정이 가리키는 아이템을 찾을 수 없습니다: {}", itemId);
			} else {
				resolved = found.get().value();
			}
		} catch (Exception error) {
			resolveFailed = true;
			SharedFateMod.LOGGER.warn("아이템 {} 을 찾다가 실패했습니다", itemId, error);
		}
		return resolved;
	}
}
