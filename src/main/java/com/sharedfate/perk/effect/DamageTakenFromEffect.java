package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 정해진 종류의 피해에만 배율을 건다.
 *
 * <pre>{@code
 * { "type": "damage_taken_from", "multiplier": 1.5,
 *   "sources": ["minecraft:in_fire", "minecraft:on_fire", "minecraft:lava"] }
 * }</pre>
 *
 * <p>{@code sources} 항목은 피해 종류 이름이고, {@code #} 로 시작하면 피해 종류 태그다.
 * 위 예시는 {@code "#minecraft:is_fire"} 한 줄로도 쓸 수 있다. 골드 5 요새 탐지기의
 * "화염 피해 ×1.5" 가 이 타입을 쓴다.
 *
 * <h2>왜 {@code damage_taken} 을 넓히지 않았는가</h2>
 * <p>{@link DamageTakenEffect} 의 배율은 {@link PerkEffect#damageTakenMultiplier()} 로 읽히는데,
 * 그 메서드에는 피해원이 넘어오지 않는다. 피해원을 넘기려면 그 자리를 부르는
 * {@code PerkManager.damageTakenMultiplier} 까지 함께 바꿔야 하고, 그러면 조건을 모르는 옛
 * 경로가 "불 피해 전용" 배율을 모든 피해에 곱하게 된다. 그래서 조건 없는 배율은
 * {@code damage_taken} 에 그대로 두고, 조건이 붙는 쪽만 별도 타입으로 나눴다. 두 타입은 서로
 * 영향을 주지 않으며 한 증강에 함께 적어도 된다.
 *
 * <h2>최상위에만 놓을 수 있다</h2>
 * <p>피해원을 아는 자리에서 이 효과를 찾는 {@link com.sharedfate.perk.PerkDamage} 는 증강의
 * 최상위 효과만 훑는다. {@code periodic} 이나 {@code conditional} 안에 넣으면 조용히 아무
 * 일도 하지 않으므로, 정의를 읽는 시점에 걸러 낸다.
 */
public final class DamageTakenFromEffect implements PerkEffect {
	/** 하위 효과로 들어갔는지 가려내는 기준. {@code periodic}·{@code conditional} 과 같은 값이다. */
	static final int MAX_TOP_LEVEL_INDEX = 100;
	/** 지정할 수 있는 피해 종류 개수 상한. */
	static final int MAX_SOURCES = 32;

	private final double multiplier;
	private final List<ResourceKey<DamageType>> types;
	private final List<TagKey<DamageType>> tags;

	public DamageTakenFromEffect(double multiplier, List<ResourceKey<DamageType>> types,
			List<TagKey<DamageType>> tags) {
		this.multiplier = multiplier;
		this.types = List.copyOf(types);
		this.tags = List.copyOf(tags);
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = DamageDealtEffect.readMultiplier(perkId, "damage_taken_from", json);
		if (multiplier == null) {
			return null;
		}
		if (index < 0 || index >= MAX_TOP_LEVEL_INDEX) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: damage_taken_from 은 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}

		List<String> raw = PerkEffectType.readStringList(json, "sources");
		if (raw == null || raw.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: damage_taken_from 에 sources 가 없거나 비어 있습니다", perkId);
			return null;
		}
		if (raw.size() > MAX_SOURCES) {
			SharedFateMod.LOGGER.warn("증강 {}: damage_taken_from 의 sources 가 너무 많습니다 ({})",
					perkId, raw.size());
			return null;
		}

		List<ResourceKey<DamageType>> types = new ArrayList<>();
		List<TagKey<DamageType>> tags = new ArrayList<>();
		for (String entry : raw) {
			boolean isTag = entry.startsWith("#");
			Identifier id = Identifier.tryParse(isTag ? entry.substring(1) : entry);
			if (id == null) {
				SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 피해 종류 이름 {}", perkId, entry);
				return null;
			}
			if (isTag) {
				tags.add(TagKey.create(Registries.DAMAGE_TYPE, id));
			} else {
				types.add(ResourceKey.create(Registries.DAMAGE_TYPE, id));
			}
		}
		return new DamageTakenFromEffect(multiplier, types, tags);
	}

	/**
	 * 조건 없는 배율은 없다.
	 *
	 * <p>피해원을 모르는 이 자리에서 배율을 돌려주면 지정하지 않은 피해에까지 걸린다.
	 * 실제 배율은 {@link #multiplierFor(DamageSource)} 로만 나간다.
	 */
	@Override
	public double damageTakenMultiplier() {
		return 1.0;
	}

	/** 이 피해원에 걸리면 배율, 아니면 1.0. */
	public double multiplierFor(@Nullable DamageSource source) {
		return matches(source) ? DamageDealtEffect.clamp(multiplier) : 1.0;
	}

	/** 이 피해원이 지정한 종류에 드는가. */
	public boolean matches(@Nullable DamageSource source) {
		if (source == null) {
			return false;
		}
		for (ResourceKey<DamageType> type : types) {
			if (source.is(type)) {
				return true;
			}
		}
		for (TagKey<DamageType> tag : tags) {
			if (source.is(tag)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 이 이름의 피해 종류를 직접 지정했는가.
	 *
	 * <p>태그는 실제 피해원이 있어야 풀 수 있으므로 여기서는 보지 않는다. 정의를 확인하는
	 * 용도다.
	 */
	public boolean coversType(@Nullable Identifier typeId) {
		if (typeId == null) {
			return false;
		}
		for (ResourceKey<DamageType> type : types) {
			if (type.identifier().equals(typeId)) {
				return true;
			}
		}
		return false;
	}

	public double multiplier() {
		return multiplier;
	}

	public List<ResourceKey<DamageType>> types() {
		return types;
	}

	public List<TagKey<DamageType>> tags() {
		return tags;
	}
}
