package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 증강을 고른 그 순간 팀 공유 인벤토리에 아이템을 한 번 넣어 준다.
 *
 * <p><b>이 효과는 {@link #apply}/{@link #remove}에서 아무 일도 하지 않는다.</b> 두 메서드는
 * 접속·부활·효과 갱신 때마다 다시 불리므로 여기서 아이템을 주면 접속할 때마다 아이템이
 * 불어난다. 지급 시점은 {@link com.sharedfate.perk.PerkManager#applyChoice} 한 곳뿐이고,
 * 실제 전달은 {@link com.sharedfate.perk.PerkItemGrants}가 맡는다.
 *
 * <p>중첩 가능한 증강은 고를 때마다 한 번씩 준다. 그래서 {@link #grantStacks()}는 중첩 수를
 * 받지 않는다. 세 번 고르면 지급도 세 번 일어난다.
 *
 * <p>JSON 형식:
 * <pre>
 * {
 *   "type": "item_grant",
 *   "items": [
 *     { "id": "minecraft:golden_apple", "count": 5 },
 *     { "id": "minecraft:potion", "count": 1, "potion": "minecraft:fire_resistance" }
 *   ]
 * }
 * </pre>
 *
 * <p>{@code potion}은 26.2의 아이템 컴포넌트({@code minecraft:potion_contents})로 붙는다.
 *
 * <p>잘못된 항목 하나가 증강 전체를 날리지 않도록, 읽을 수 없는 항목은 경고를 남기고 그 항목만
 * 버린다. 다만 쓸 만한 항목이 하나도 남지 않으면 줄 것이 없는 셈이므로 효과 자체를 버린다.
 * 아이템·물약을 레지스트리에서 찾는 일은 정의를 읽는 시점이 아니라 처음 지급할 때 한다.
 * 정의를 읽는 시점에는 레지스트리가 아직 준비되지 않았을 수 있기 때문이다.
 */
public final class ItemGrantEffect implements PerkEffect {
	/** 항목 하나가 줄 수 있는 최대 개수. 바닐라 한 칸 최대치와 맞춘다. */
	public static final int MAX_COUNT = 64;
	/** 효과 하나가 가질 수 있는 최대 항목 수. */
	public static final int MAX_ENTRIES = 16;

	/**
	 * 지급 항목 하나.
	 *
	 * @param itemId   아이템 식별자
	 * @param count    개수. 1 이상 {@link #MAX_COUNT} 이하
	 * @param potionId 물약 종류. 물약 아이템이 아니면 null
	 */
	public record Entry(Identifier itemId, int count, @Nullable Identifier potionId) {
	}

	private final List<Entry> entries;

	/** 레지스트리에서 찾아 만든 견본. 처음 지급할 때 한 번 만들고 계속 쓴다. */
	private @Nullable List<ItemStack> templates;

	public ItemGrantEffect(List<Entry> entries) {
		this.entries = List.copyOf(entries);
	}

	/** JSON에서 만든다. 줄 것이 하나도 없으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		JsonElement element = json.get("items");
		if (element == null || !element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: item_grant 효과에 items 배열이 없습니다", perkId);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		List<Entry> entries = new ArrayList<>();
		for (JsonElement raw : array) {
			if (entries.size() >= MAX_ENTRIES) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: item_grant 항목이 {}개를 넘어 나머지를 버립니다", perkId, MAX_ENTRIES);
				break;
			}
			Entry entry = readEntry(perkId, raw);
			if (entry != null) {
				entries.add(entry);
			}
		}

		if (entries.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: item_grant 에 읽을 수 있는 항목이 하나도 없습니다", perkId);
			return null;
		}
		return new ItemGrantEffect(entries);
	}

	/** 항목 하나를 읽는다. 잘못됐으면 경고를 남기고 null. 그 항목만 빠지고 나머지는 살아남는다. */
	private static @Nullable Entry readEntry(String perkId, @Nullable JsonElement raw) {
		if (raw == null || !raw.isJsonObject()) {
			SharedFateMod.LOGGER.warn("증강 {}: item_grant 항목이 객체가 아니라 건너뜁니다", perkId);
			return null;
		}
		JsonObject json = raw.getAsJsonObject();

		String rawItem = PerkEffectType.readString(json, "id");
		if (rawItem == null || rawItem.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: item_grant 항목에 id 가 없어 건너뜁니다", perkId);
			return null;
		}
		Identifier itemId = Identifier.tryParse(rawItem.trim());
		if (itemId == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 아이템 이름 {} 을 건너뜁니다", perkId, rawItem);
			return null;
		}

		int count = 1;
		if (json.has("count")) {
			Double rawCount = PerkEffectType.readDouble(json, "count");
			if (rawCount == null) {
				SharedFateMod.LOGGER.warn("증강 {}: {} 의 count 가 숫자가 아니라 건너뜁니다", perkId, itemId);
				return null;
			}
			count = (int) Math.floor(rawCount);
		}
		if (count < 1 || count > MAX_COUNT) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 count 가 1~{} 범위를 벗어나 건너뜁니다 ({})",
					perkId, itemId, MAX_COUNT, count);
			return null;
		}

		Identifier potionId = null;
		String rawPotion = PerkEffectType.readString(json, "potion");
		if (rawPotion != null && !rawPotion.isBlank()) {
			potionId = Identifier.tryParse(rawPotion.trim());
			if (potionId == null) {
				SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 물약 이름 {} 을 건너뜁니다", perkId, rawPotion);
				return null;
			}
		}

		return new Entry(itemId, count, potionId);
	}

	/**
	 * 이번 선택으로 지급할 아이템 묶음.
	 *
	 * <p>부를 때마다 새 사본을 돌려준다. 받는 쪽이 {@code shrink} 로 개수를 깎기 때문에
	 * 견본을 그대로 넘기면 두 번째 지급 때 빈 묶음이 나간다.
	 */
	public List<ItemStack> grantStacks() {
		List<ItemStack> result = new ArrayList<>();
		for (ItemStack template : templates()) {
			result.add(template.copy());
		}
		return result;
	}

	/** 정의에 적힌 항목 목록. 레지스트리 조회 전 상태다. */
	public List<Entry> entries() {
		return entries;
	}

	private List<ItemStack> templates() {
		if (templates != null) {
			return templates;
		}
		List<ItemStack> resolved = new ArrayList<>();
		for (Entry entry : entries) {
			ItemStack stack = resolve(entry);
			if (stack != null) {
				resolved.add(stack);
			}
		}
		templates = List.copyOf(resolved);
		return templates;
	}

	/** 항목 하나를 실제 아이템 묶음으로 바꾼다. 레지스트리에 없으면 경고를 남기고 null. */
	private static @Nullable ItemStack resolve(Entry entry) {
		Item item;
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(entry.itemId());
			if (found.isEmpty()) {
				SharedFateMod.LOGGER.warn(
						"증강이 주려는 아이템을 찾을 수 없어 건너뜁니다: {}", entry.itemId());
				return null;
			}
			item = found.get().value();
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("아이템 {} 을 찾다가 실패했습니다", entry.itemId(), error);
			return null;
		}
		// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
		if (item == Items.AIR) {
			SharedFateMod.LOGGER.warn("증강이 주려는 아이템을 찾을 수 없어 건너뜁니다: {}", entry.itemId());
			return null;
		}

		ItemStack stack = new ItemStack(item, entry.count());
		if (entry.potionId() != null && !applyPotion(stack, entry.potionId())) {
			return null;
		}
		return stack;
	}

	/** 물약 종류를 컴포넌트로 붙인다. 찾지 못하면 false. */
	private static boolean applyPotion(ItemStack stack, Identifier potionId) {
		try {
			Optional<Holder.Reference<Potion>> found = BuiltInRegistries.POTION.get(potionId);
			if (found.isEmpty()) {
				SharedFateMod.LOGGER.warn("증강이 주려는 물약을 찾을 수 없어 건너뜁니다: {}", potionId);
				return false;
			}
			stack.set(DataComponents.POTION_CONTENTS, new PotionContents(found.get()));
			return true;
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("물약 {} 을 찾다가 실패했습니다", potionId, error);
			return false;
		}
	}
}
