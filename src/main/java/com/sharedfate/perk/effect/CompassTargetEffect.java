package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 정해진 차원에서 나침반이 가장 가까운 지정 구조물을 가리키게 한다.
 *
 * <pre>{@code
 * { "type": "compass_target", "structure": "minecraft:fortress",
 *   "dimension": "minecraft:the_nether", "search_radius": 100 }
 * }</pre>
 *
 * <p>{@code structure} 는 구조물 이름이고, {@code #} 로 시작하면 구조물 태그다.
 * {@code dimension} 은 그 구조물을 찾을 차원이며, 팀원이 그 차원에 있을 때만 나침반이 바뀐다.
 * {@code search_radius} 는 <b>청크</b> 단위 탐색 반경으로, 적지 않으면
 * {@value #DEFAULT_SEARCH_RADIUS} 다. 바닐라 {@code /locate structure} 와 같은 값이다.
 * 골드 요새 탐지기가 {@code damage_taken_from} 과 짝지어 쓰는 이득이다.
 *
 * <h2>{@code #minecraft:fortress} 태그는 26.2 에 없다</h2>
 * <p>{@code data/minecraft/tags/worldgen/structure/} 를 열어 보면 {@code village},
 * {@code mineshaft}, {@code ruined_portal} 같은 태그만 있고 요새 태그는 없다. 그래서 요새는
 * 태그가 아니라 구조물 이름 {@code minecraft:fortress} 로 직접 적어야 한다
 * ({@code data/minecraft/worldgen/structure/fortress.json} 에 실재한다). 태그 형식도 그대로
 * 받아 두는 이유는 마을처럼 여러 변종을 한꺼번에 가리켜야 하는 구조물이 있기 때문이다.
 *
 * <h2>여기서는 아무것도 찾지 않는다</h2>
 * <p>이 클래스는 "무엇을 어디서 얼마나 넓게 찾을 것인가"만 들고 있다. 실제 탐색과 나침반
 * 손보기, 결과 캐시는 전부 {@link com.sharedfate.perk.PerkCompassTargets} 가 맡는다.
 * 구조물 이름을 레지스트리에서 푸는 일도 서버가 떠 있어야 하므로 그때 미룬다.
 */
public final class CompassTargetEffect implements PerkEffect {
	/** 적지 않았을 때 쓰는 탐색 반경(청크). 바닐라 {@code /locate structure} 와 같다. */
	public static final int DEFAULT_SEARCH_RADIUS = 100;
	/** 탐색 반경 상한(청크). 이보다 넓히면 한 번의 탐색이 서버를 눈에 띄게 붙잡는다. */
	public static final int MAX_SEARCH_RADIUS = 256;

	private final @Nullable ResourceKey<Structure> structureKey;
	private final @Nullable TagKey<Structure> structureTag;
	private final ResourceKey<Level> dimension;
	private final int searchRadius;

	public CompassTargetEffect(@Nullable ResourceKey<Structure> structureKey,
			@Nullable TagKey<Structure> structureTag, ResourceKey<Level> dimension,
			int searchRadius) {
		this.structureKey = structureKey;
		this.structureTag = structureTag;
		this.dimension = dimension;
		this.searchRadius = searchRadius;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		if (index < 0 || index >= DamageTakenFromEffect.MAX_TOP_LEVEL_INDEX) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: compass_target 은 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}

		String rawStructure = PerkEffectType.readString(json, "structure");
		if (rawStructure == null || rawStructure.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: compass_target 에 structure 가 없습니다", perkId);
			return null;
		}
		String trimmed = rawStructure.trim();
		boolean isTag = trimmed.startsWith("#");
		Identifier structureId = Identifier.tryParse(isTag ? trimmed.substring(1) : trimmed);
		if (structureId == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 구조물 이름 {}", perkId, rawStructure);
			return null;
		}

		String rawDimension = PerkEffectType.readString(json, "dimension");
		if (rawDimension == null || rawDimension.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: compass_target 에 dimension 이 없습니다", perkId);
			return null;
		}
		Identifier dimensionId = Identifier.tryParse(rawDimension.trim());
		if (dimensionId == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 차원 이름 {}", perkId, rawDimension);
			return null;
		}

		int radius = PerkEffectType.readInt(json, "search_radius", DEFAULT_SEARCH_RADIUS);
		if (radius < 1 || radius > MAX_SEARCH_RADIUS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: compass_target 의 search_radius 가 1~{} 범위를 벗어났습니다 ({})",
					perkId, MAX_SEARCH_RADIUS, radius);
			return null;
		}

		return new CompassTargetEffect(
				isTag ? null : ResourceKey.create(Registries.STRUCTURE, structureId),
				isTag ? TagKey.create(Registries.STRUCTURE, structureId) : null,
				ResourceKey.create(Registries.DIMENSION, dimensionId),
				radius);
	}

	/**
	 * 찾을 구조물들. 레지스트리에 없으면 null.
	 *
	 * <p>{@code ChunkGenerator.findNearestMapStructure} 는 {@code HolderSet} 을 받으므로 이름
	 * 하나짜리 정의도 홀더 하나만 담은 집합으로 감싼다. 26.2 의
	 * {@code ServerLevel.findNearestMapStructure} 가 태그를 풀어 넘기는 방식과 같다.
	 */
	public @Nullable HolderSet<Structure> resolve(@Nullable RegistryAccess registries) {
		if (registries == null) {
			return null;
		}
		if (structureTag != null) {
			return registries.get(structureTag).orElse(null);
		}
		if (structureKey == null) {
			return null;
		}
		Holder.Reference<Structure> holder = registries.get(structureKey).orElse(null);
		return holder == null ? null : HolderSet.direct(List.of(holder));
	}

	/** 구조물 이름으로 직접 적었을 때의 이름. 태그로 적었으면 null. */
	public @Nullable ResourceKey<Structure> structureKey() {
		return structureKey;
	}

	/** 구조물 태그로 적었을 때의 태그. 이름으로 적었으면 null. */
	public @Nullable TagKey<Structure> structureTag() {
		return structureTag;
	}

	/** 이 구조물을 찾을 차원. */
	public ResourceKey<Level> dimension() {
		return dimension;
	}

	/** 탐색 반경(청크). */
	public int searchRadius() {
		return searchRadius;
	}
}
