package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 증강을 고른 그 순간 팀 공유 인벤토리에 아이템을 한 번 넣어 준다.
 *
 * <p><b>이 효과는 {@link #apply}/{@link #remove}에서 아무 일도 하지 않는다.</b> 두 메서드는
 * 접속·부활·효과 갱신 때마다 다시 불리므로 여기서 아이템을 주면 접속할 때마다 아이템이
 * 불어난다. 지급 시점은 {@link com.sharedfate.perk.PerkManager#applyChoice} 한 곳뿐이고,
 * 실제 전달은 {@link com.sharedfate.perk.PerkItemGrants}가 맡는다.
 *
 * <p>중첩은 없다. 한 증강은 한 회차에 한 번만 고를 수 있으므로 지급도 한 번뿐이다.
 *
 * <p>JSON 형식:
 * <pre>
 * {
 *   "type": "item_grant",
 *   "items": [
 *     { "id": "minecraft:golden_apple", "count": 5 },
 *     { "id": "minecraft:potion", "count": 1, "potion": "minecraft:fire_resistance" },
 *     { "id": "minecraft:potion", "count": 1, "potion": "minecraft:water_breathing",
 *       "duration_minutes": 30 },
 *     { "id": "minecraft:netherite_pickaxe", "count": 1,
 *       "enchantments": { "minecraft:efficiency": 6, "minecraft:unbreaking": 10 } }
 *   ]
 * }
 * </pre>
 *
 * <p>{@code potion}은 26.2의 아이템 컴포넌트({@code minecraft:potion_contents})로 붙는다.
 *
 * <h2>{@code enchantments}</h2>
 * <p>아이템 컴포넌트 {@code minecraft:enchantments}로 붙는다. <b>바닐라 최대 레벨을 넘겨도
 * 된다.</b> 최대 레벨은 인챈트 탁자와 모루가 지키는 약속일 뿐이고, 컴포넌트 자체가 받아들이는
 * 범위는 {@code ItemEnchantments.LEVEL_CODEC} 의 1~{@value #MAX_ENCHANT_LEVEL} 이다. 그래서
 * 효율 VI · 내구성 X 같은 값도 그대로 붙고, 효과 크기도 레벨을 변수로 쓰는 데이터팩 정의를
 * 따라 그만큼 커진다.
 *
 * <p>인챈트는 26.2에서 <b>데이터팩 레지스트리</b>라 {@code BuiltInRegistries} 에 없다. 월드가
 * 들고 있는 {@code registryAccess} 를 통해야만 {@code Holder<Enchantment>} 를 얻을 수 있으므로,
 * 지급 시점에 그 레지스트리를 받아 견본을 만든다. 레지스트리를 받지 못한 채로 만든 견본은
 * 인챈트가 빠진 것이라 <b>재사용하지 않는다</b>. 그러지 않으면 시험이나 이른 호출 한 번이
 * 인챈트 없는 견본을 영영 굳혀 버린다.
 *
 * <p>인챈트 이름을 찾지 못하면 <b>그 인챈트만</b> 건너뛰고 아이템은 그대로 준다. 오타 하나로
 * 증강이 통째로 사라지는 것보다 곡괭이라도 손에 쥐는 편이 낫다.
 *
 * <h2>{@code duration_minutes}</h2>
 * <p>바닐라에서 가장 긴 물약도 8분이라 그보다 길게 주려면 지속시간을 직접 적어야 한다.
 * 이 값을 적으면 <b>기본 물약 대신</b> 같은 효과를 그 길이로 담은 사용자 효과를 넣는다.
 * 기본 물약을 함께 두면 3분짜리와 30분짜리가 겹쳐 설명이 두 줄로 나오기 때문이다.
 * 이름은 원래 물약 이름을 그대로 쓰므로 「화염 저항 물약」처럼 보인다.
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
	/** 적을 수 있는 최대 지속시간(분). 한 회차보다 길 이유가 없다. */
	public static final int MAX_DURATION_MINUTES = 120;
	/** 항목 하나에 붙일 수 있는 최대 인챈트 수. */
	public static final int MAX_ENCHANTMENTS = 12;
	/**
	 * 적을 수 있는 최대 인챈트 레벨.
	 *
	 * <p>26.2 {@code ItemEnchantments.LEVEL_CODEC} 이 {@code Codec.intRange(1, 255)} 이고
	 * {@code ItemEnchantments.Mutable.set} 도 255 로만 자른다. 바닐라 최대 레벨(효율 V 등)은
	 * 여기서 아무 역할도 하지 않는다.
	 */
	public static final int MAX_ENCHANT_LEVEL = 255;
	private static final int TICKS_PER_MINUTE = 20 * 60;

	/**
	 * 지급 항목 하나.
	 *
	 * @param itemId   아이템 식별자
	 * @param count    개수. 1 이상 {@link #MAX_COUNT} 이하
	 * @param potionId 물약 종류. 물약 아이템이 아니면 null
	 * @param durationMinutes 지속시간(분). 0 이면 물약이 원래 가진 길이를 그대로 쓴다
	 * @param enchantments 붙일 인챈트와 레벨. 없으면 빈 맵
	 */
	public record Entry(Identifier itemId, int count, @Nullable Identifier potionId,
			int durationMinutes, Map<Identifier, Integer> enchantments) {
		public Entry {
			enchantments = Map.copyOf(enchantments);
		}
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

		int durationMinutes = 0;
		String durationKey = json.has("duration_minutes") ? "duration_minutes" : "durationMinutes";
		Double rawDuration = PerkEffectType.readDouble(json, durationKey);
		if (rawDuration != null) {
			if (potionId == null || rawDuration < 1 || rawDuration > MAX_DURATION_MINUTES) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: {} 값이 올바르지 않아 건너뜁니다 ({}). 물약 항목에만 1~{} 로 적을 수 있습니다",
						perkId, durationKey, rawDuration, MAX_DURATION_MINUTES);
				return null;
			}
			durationMinutes = (int) Math.round(rawDuration);
		}

		return new Entry(itemId, count, potionId, durationMinutes,
				readEnchantments(perkId, itemId, json));
	}

	/**
	 * {@code enchantments} 를 읽는다.
	 *
	 * <p>필드가 없으면 빈 맵이다. 이름이나 레벨이 잘못된 항목은 <b>그 인챈트만</b> 버린다.
	 * 인챈트 하나가 잘못됐다고 아이템 지급 자체를 없던 일로 만들지는 않는다.
	 * 레지스트리에 실제로 있는 이름인지는 여기서 보지 않는다 — 정의를 읽는 시점에는 인챈트
	 * 레지스트리(데이터팩 레지스트리)가 아직 존재하지 않기 때문이다.
	 */
	private static Map<Identifier, Integer> readEnchantments(String perkId, Identifier itemId,
			JsonObject json) {
		JsonElement element = json.get("enchantments");
		if (element == null || element.isJsonNull()) {
			return Map.of();
		}
		if (!element.isJsonObject()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 enchantments 가 객체가 아니라 무시합니다", perkId, itemId);
			return Map.of();
		}

		JsonObject table = element.getAsJsonObject();
		Map<Identifier, Integer> enchantments = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> pair : table.entrySet()) {
			if (enchantments.size() >= MAX_ENCHANTMENTS) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: {} 의 인챈트가 {}개를 넘어 나머지를 버립니다",
						perkId, itemId, MAX_ENCHANTMENTS);
				break;
			}
			Identifier enchantId = Identifier.tryParse(pair.getKey().trim());
			if (enchantId == null) {
				SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 인챈트 이름 {} 을 건너뜁니다",
						perkId, pair.getKey());
				continue;
			}
			Double level = PerkEffectType.readDouble(table, pair.getKey());
			if (level == null || level < 1 || level > MAX_ENCHANT_LEVEL) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: 인챈트 {} 의 레벨이 1~{} 범위를 벗어나 건너뜁니다 ({})",
						perkId, enchantId, MAX_ENCHANT_LEVEL, level);
				continue;
			}
			enchantments.put(enchantId, (int) Math.floor(level));
		}
		return Map.copyOf(enchantments);
	}

	/**
	 * 인챈트 없이 지급할 아이템 묶음.
	 *
	 * <p>레지스트리를 알 수 없는 자리에서 쓴다. 인챈트를 적은 항목은 인챈트가 빠진 채로 나온다.
	 */
	public List<ItemStack> grantStacks() {
		return grantStacks(null);
	}

	/**
	 * 이번 선택으로 지급할 아이템 묶음.
	 *
	 * <p>부를 때마다 새 사본을 돌려준다. 받는 쪽이 {@code shrink} 로 개수를 깎기 때문에
	 * 견본을 그대로 넘기면 두 번째 지급 때 빈 묶음이 나간다.
	 *
	 * @param registries 인챈트를 찾을 레지스트리. 보통 {@code server.registryAccess()} 다.
	 *                   {@code null} 이면 인챈트를 붙이지 못하고 아이템만 나간다
	 */
	public List<ItemStack> grantStacks(@Nullable HolderLookup.Provider registries) {
		List<ItemStack> result = new ArrayList<>();
		for (ItemStack template : templates(registries)) {
			result.add(template.copy());
		}
		return result;
	}

	/** 정의에 적힌 항목 목록. 레지스트리 조회 전 상태다. */
	public List<Entry> entries() {
		return entries;
	}

	/** 이 효과가 붙이려는 인챈트가 하나라도 있는가. */
	private boolean needsRegistries() {
		for (Entry entry : entries) {
			if (!entry.enchantments().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private List<ItemStack> templates(@Nullable HolderLookup.Provider registries) {
		if (templates != null) {
			return templates;
		}
		List<ItemStack> resolved = new ArrayList<>();
		for (Entry entry : entries) {
			ItemStack stack = resolve(entry, registries);
			if (stack != null) {
				resolved.add(stack);
			}
		}
		// 인챈트를 붙여야 하는데 레지스트리를 못 받았으면 이번 결과는 반쪽짜리다. 굳혀 두면
		// 나중에 제대로 된 레지스트리를 받아도 인챈트 없는 견본이 계속 나간다.
		if (registries == null && needsRegistries()) {
			return List.copyOf(resolved);
		}
		templates = List.copyOf(resolved);
		return templates;
	}

	/** 항목 하나를 실제 아이템 묶음으로 바꾼다. 레지스트리에 없으면 경고를 남기고 null. */
	private static @Nullable ItemStack resolve(Entry entry,
			@Nullable HolderLookup.Provider registries) {
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
		if (entry.potionId() != null
				&& !applyPotion(stack, entry.potionId(), entry.durationMinutes())) {
			return null;
		}
		applyEnchantments(stack, entry, registries);
		return stack;
	}

	/**
	 * 인챈트를 컴포넌트로 붙인다.
	 *
	 * <p>실패해도 {@code false} 를 돌려주지 않는다. 인챈트를 못 붙인 곡괭이는 여전히 곡괭이라,
	 * 아이템 자체를 없애 버리면 증강 하나가 통째로 사라진다. 찾지 못한 인챈트는 경고만 남기고
	 * 건너뛴다.
	 *
	 * <p>레벨은 자르지 않는다. 바닐라 최대 레벨을 넘겨 붙이는 것이 이 기능의 요구사항이고,
	 * {@code ItemEnchantments} 가 받아들이는 상한 {@value #MAX_ENCHANT_LEVEL} 은 정의를 읽는
	 * 시점에 이미 확인했다.
	 */
	private static void applyEnchantments(ItemStack stack, Entry entry,
			@Nullable HolderLookup.Provider registries) {
		if (entry.enchantments().isEmpty()) {
			return;
		}
		if (registries == null) {
			SharedFateMod.LOGGER.warn(
					"증강이 주려는 {} 에 인챈트를 붙일 레지스트리가 없어 그냥 줍니다", entry.itemId());
			return;
		}

		HolderLookup.RegistryLookup<Enchantment> lookup;
		try {
			// 26.2 의 인챈트는 데이터팩 레지스트리다. 월드가 없으면 이 조회 자체가 없다.
			Optional<? extends HolderLookup.RegistryLookup<Enchantment>> found =
					registries.<Enchantment>lookup(Registries.ENCHANTMENT);
			if (found.isEmpty()) {
				SharedFateMod.LOGGER.warn(
						"인챈트 레지스트리를 찾을 수 없어 {} 를 인챈트 없이 줍니다", entry.itemId());
				return;
			}
			lookup = found.get();
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("인챈트 레지스트리를 읽다가 실패했습니다", error);
			return;
		}

		Map<Holder<Enchantment>, Integer> resolved = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Integer> wanted : entry.enchantments().entrySet()) {
			Holder<Enchantment> holder = find(lookup, wanted.getKey());
			if (holder == null) {
				// 이 인챈트만 빠진다. 아이템은 그대로 나간다.
				SharedFateMod.LOGGER.warn(
						"증강이 주려는 인챈트를 찾을 수 없어 건너뜁니다: {}", wanted.getKey());
				continue;
			}
			resolved.put(holder, wanted.getValue());
		}
		if (resolved.isEmpty()) {
			return;
		}
		// updateEnchantments 는 컴포넌트가 아직 없는 아이템에서 아무 일도 하지 않고 빠져나간다.
		// 여기서는 새로 만든 묶음에 처음 붙이는 것이므로 직접 만들어 넣는다. 두 도우미 모두
		// 「인챈트 책이면 stored_enchantments」를 알아서 갈라 준다.
		ItemEnchantments.Mutable mutable =
				new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
		resolved.forEach(mutable::set);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
	}

	private static @Nullable Holder<Enchantment> find(
			HolderLookup.RegistryLookup<Enchantment> lookup, Identifier id) {
		try {
			return lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("인챈트 {} 를 찾다가 실패했습니다", id, error);
			return null;
		}
	}

	/**
	 * 물약 종류를 컴포넌트로 붙인다. 찾지 못하면 false.
	 *
	 * @param durationMinutes 0 이면 기본 물약 그대로, 1 이상이면 그 길이의 사용자 효과로 담는다
	 */
	private static boolean applyPotion(ItemStack stack, Identifier potionId, int durationMinutes) {
		try {
			Optional<Holder.Reference<Potion>> found = BuiltInRegistries.POTION.get(potionId);
			if (found.isEmpty()) {
				SharedFateMod.LOGGER.warn("증강이 주려는 물약을 찾을 수 없어 건너뜁니다: {}", potionId);
				return false;
			}
			if (durationMinutes <= 0) {
				stack.set(DataComponents.POTION_CONTENTS, new PotionContents(found.get()));
				return true;
			}

			int ticks = durationMinutes * TICKS_PER_MINUTE;
			List<MobEffectInstance> stretched = new ArrayList<>();
			for (MobEffectInstance original : found.get().value().getEffects()) {
				stretched.add(new MobEffectInstance(original.getEffect(), ticks,
						original.getAmplifier(), original.isAmbient(), original.isVisible(),
						original.showIcon()));
			}
			if (stretched.isEmpty()) {
				SharedFateMod.LOGGER.warn("물약 {} 에 늘릴 효과가 없어 기본값으로 줍니다", potionId);
				stack.set(DataComponents.POTION_CONTENTS, new PotionContents(found.get()));
				return true;
			}
			// 기본 물약은 비운다. 함께 두면 원래 길이의 효과가 하나 더 붙어 설명이 겹친다.
			// 이름만 원래 물약에서 가져와 「화염 저항 물약」처럼 보이게 한다.
			stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
					Optional.empty(), Optional.empty(), stretched,
					Optional.of(potionId.getPath())));
			return true;
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("물약 {} 을 찾다가 실패했습니다", potionId, error);
			return false;
		}
	}
}
