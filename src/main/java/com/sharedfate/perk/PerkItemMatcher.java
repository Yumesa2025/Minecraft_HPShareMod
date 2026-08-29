package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * "이 아이템이 이 묶음에 들어가는가"를 판정하는 도우미.
 *
 * <p>장비 관련 증강은 하나같이 아이템 무리를 가리킨다. 다이아몬드 장비 전부, 삽 전부처럼.
 * 그 무리를 적는 방법을 한 곳에 모아 두고 {@code item_ban}·{@code weapon_damage} 가 같이 쓴다.
 *
 * <h2>태그를 먼저 쓴다</h2>
 * <p>아이템을 일일이 나열하는 대신 마인크래프트 아이템 태그를 쓸 수 있다. 삽은
 * {@code minecraft:shovels} 하나로 끝난다. 다만 <b>"다이아몬드 장비 전부"에 해당하는 바닐라
 * 태그는 없다.</b> 이름이 비슷한 {@code minecraft:diamond_tool_materials} 는 도구가 아니라
 * 수리 재료({@code minecraft:diamond} 한 개)를 담은 태그다. 그래서 이 모드가
 * {@code sharedfate:diamond_gear} 태그를 직접 실어 보낸다
 * ({@code data/sharedfate/tags/item/diamond_gear.json}). 서버 주인은 데이터팩으로 그 태그만
 * 덮어써서 대상 목록을 바꿀 수 있다.
 *
 * <p>태그와 개별 아이템을 같이 적어도 된다. 둘 중 하나라도 맞으면 해당한다.
 *
 * <p>아이템 조회는 정의를 읽을 때가 아니라 처음 판정할 때 한다. 정의를 읽는 시점에는
 * 레지스트리가 아직 준비되지 않았을 수 있기 때문이다. 태그는 조회조차 필요 없다.
 * {@link ItemStack#is(TagKey)} 가 아이템에 달린 태그를 바로 보기 때문이다.
 */
public final class PerkItemMatcher {
	/** 태그 미준비 경고를 한 번만 내기 위한 표시. */
	private static boolean tagWarningShown;

	/** 한 묶음이 가질 수 있는 최대 항목 수. 아이템과 태그를 따로 센다. */
	public static final int MAX_ENTRIES = 64;

	private final Set<Identifier> itemIds;
	private final List<TagKey<Item>> tags;

	/** 레지스트리에서 찾아 둔 아이템. 처음 판정할 때 한 번 만들고 계속 쓴다. */
	private @Nullable Set<Item> resolvedItems;

	/** 가리키는 것이 없다는 경고는 처음 판정할 때 한 번만 남긴다. */
	private boolean verified;

	private PerkItemMatcher(Set<Identifier> itemIds, List<TagKey<Item>> tags) {
		this.itemIds = Set.copyOf(itemIds);
		this.tags = List.copyOf(tags);
	}

	/**
	 * {@code items}·{@code tags} 두 배열을 읽어 묶음을 만든다.
	 *
	 * <p>둘 다 비어 있으면 가리키는 것이 없다는 뜻이라 {@code null} 을 돌려준다. 부르는 쪽은
	 * 그 효과를 통째로 버린다. "아무것도 가리키지 않는 제한"은 조용히 아무 일도 안 하는
	 * 함정이 되므로 정의를 읽는 자리에서 걸러 내는 편이 낫다.
	 *
	 * @param label 경고 문구에 쓸 효과 이름
	 */
	public static @Nullable PerkItemMatcher fromJson(String perkId, String label, JsonObject json) {
		Set<Identifier> itemIds = new LinkedHashSet<>();
		List<String> rawItems = PerkEffectType.readStringList(json, "items");
		if (rawItems != null) {
			for (String raw : rawItems) {
				if (itemIds.size() >= MAX_ENTRIES) {
					SharedFateMod.LOGGER.warn(
							"증강 {}: {} 의 items 가 {}개를 넘어 나머지를 버립니다", perkId, label, MAX_ENTRIES);
					break;
				}
				Identifier id = Identifier.tryParse(raw);
				if (id == null) {
					SharedFateMod.LOGGER.warn(
							"증강 {}: {} 의 아이템 이름 {} 을 읽을 수 없어 건너뜁니다", perkId, label, raw);
					continue;
				}
				itemIds.add(id);
			}
		}

		List<TagKey<Item>> tags = new ArrayList<>();
		List<String> rawTags = PerkEffectType.readStringList(json, "tags");
		if (rawTags != null) {
			for (String raw : rawTags) {
				if (tags.size() >= MAX_ENTRIES) {
					SharedFateMod.LOGGER.warn(
							"증강 {}: {} 의 tags 가 {}개를 넘어 나머지를 버립니다", perkId, label, MAX_ENTRIES);
					break;
				}
				// 데이터팩에서 태그를 적을 때 쓰는 앞머리 '#' 을 적어도 받아 준다.
				String trimmed = raw.startsWith("#") ? raw.substring(1).trim() : raw;
				Identifier id = Identifier.tryParse(trimmed);
				if (id == null) {
					SharedFateMod.LOGGER.warn(
							"증강 {}: {} 의 태그 이름 {} 을 읽을 수 없어 건너뜁니다", perkId, label, raw);
					continue;
				}
				tags.add(TagKey.create(Registries.ITEM, id));
			}
		}

		if (itemIds.isEmpty() && tags.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 에 items 도 tags 도 없어 가리키는 아이템이 없습니다", perkId, label);
			return null;
		}
		return new PerkItemMatcher(itemIds, tags);
	}

	/** 이 아이템이 묶음에 들어가는가. 빈 묶음은 언제나 거짓이다. */
	public boolean matches(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		// 태그는 데이터팩이 올라와야 묶인다. 그 전에 물으면 예외가 나므로 태그 판정만 건너뛴다.
		try {
			verifyOnce();
			for (TagKey<Item> tag : tags) {
				if (stack.is(tag)) {
					return true;
				}
			}
		} catch (IllegalStateException tagsNotBound) {
			verified = false;
			warnTagsNotBound();
		}
		return !itemIds.isEmpty() && resolvedItems().contains(stack.getItem());
	}

	/**
	 * 태그가 아직 묶이지 않았다고 한 번만 알린다.
	 *
	 * <p>데이터팩이 올라오기 전이나 리로드 도중에 판정이 들어오면 생긴다. 잠깐 태그 항목만
	 * 안 맞을 뿐 게임이 멈추지는 않으므로 로그만 남기고 지나간다.
	 */
	private static void warnTagsNotBound() {
		if (tagWarningShown) {
			return;
		}
		tagWarningShown = true;
		SharedFateMod.LOGGER.warn(
				"아이템 태그가 아직 준비되지 않아 태그 판정을 건너뜁니다. 데이터팩이 올라온 뒤에는 정상 동작합니다.");
	}

	/** 정의에 적힌 아이템 이름들. 레지스트리 조회 전 상태다. */
	public Set<Identifier> itemIds() {
		return itemIds;
	}

	/** 정의에 적힌 태그들. */
	public List<TagKey<Item>> tags() {
		return tags;
	}

	/**
	 * 처음 판정할 때 한 번, 이 묶음이 정말 무언가를 가리키는지 확인한다.
	 *
	 * <p>태그는 데이터팩이 로드되어야 채워진다. 데이터팩이 빠졌거나 이름을 잘못 적으면 태그는
	 * 조용히 빈 채로 남고, 그러면 제한이 걸리지 않는데 아무도 눈치채지 못한다. 실제 판정이
	 * 처음 일어나는 시점이면 태그가 이미 채워져 있으므로 여기서 한 번 확인하고 경고를 남긴다.
	 */
	private void verifyOnce() {
		if (verified) {
			return;
		}
		verified = true;
		for (TagKey<Item> tag : tags) {
			if (BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext()) {
				return;
			}
		}
		if (!resolvedItems().isEmpty()) {
			return;
		}
		SharedFateMod.LOGGER.warn(
				"증강의 아이템 묶음이 아무것도 가리키지 않아 제한이 걸리지 않습니다. 아이템={} 태그={}",
				itemIds, tags);
	}

	private Set<Item> resolvedItems() {
		if (resolvedItems != null) {
			return resolvedItems;
		}
		Set<Item> found = new HashSet<>();
		for (Identifier id : itemIds) {
			Item item = resolve(id);
			if (item != null) {
				found.add(item);
			}
		}
		resolvedItems = found.isEmpty() ? Collections.emptySet() : Set.copyOf(found);
		return resolvedItems;
	}

	private static @Nullable Item resolve(Identifier id) {
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(id);
			if (found.isEmpty()) {
				SharedFateMod.LOGGER.warn("증강이 가리키는 아이템을 찾을 수 없어 건너뜁니다: {}", id);
				return null;
			}
			Item item = found.get().value();
			// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
			if (item == Items.AIR) {
				SharedFateMod.LOGGER.warn("증강이 가리키는 아이템을 찾을 수 없어 건너뜁니다: {}", id);
				return null;
			}
			return item;
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("아이템 {} 을 찾다가 실패했습니다", id, error);
			return null;
		}
	}
}
