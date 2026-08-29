package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 블록 목록을 적는 자리 하나를 여기로 모은다.
 *
 * <p>{@code bonus_drop}·{@code on_break}·{@code mining_speed} 는 모두 "어떤 블록에 걸리는가"를
 * 적어야 한다. 세 곳이 각자 다르게 읽으면 정의 파일을 쓰는 사람이 규칙을 세 번 배워야 하므로
 * 읽기와 판정을 이 클래스 하나에 둔다.
 *
 * <h2>적는 법</h2>
 * <p>{@code blocks} 는 문자열 배열이다. {@code #} 로 시작하면 <b>블록 태그</b>, 아니면 블록
 * 하나의 id 다. 태그를 쓰면 "광석 전부" 같은 묶음을 한 줄로 적을 수 있다.
 *
 * <pre>{@code
 * "blocks": ["#c:ores", "#minecraft:iron_ores", "minecraft:ancient_debris"]
 * }</pre>
 *
 * <p>쓸 수 있는 태그는 두 갈래다.
 * <ul>
 *   <li><b>바닐라</b> — {@code minecraft:coal_ores}, {@code minecraft:iron_ores} 처럼 광물
 *       종류마다 하나씩 있고, {@code minecraft:base_stone_overworld}·{@code minecraft:dirt}
 *       같은 지형 묶음도 있다. 바닐라가 데이터팩으로 들고 있으므로 항상 존재한다.</li>
 *   <li><b>Fabric 규약(c)</b> — {@code c:ores} 하나로 석탄·구리·다이아·에메랄드·금·철·청금석·
 *       레드스톤·네더 석영·고대 잔해까지 전부 덮는다. fabric-api 에 들어 있는
 *       {@code fabric-convention-tags-v2} 가 정의하며, 이 모드는 fabric-api 전체를 의존하므로
 *       런타임에 항상 올라와 있다. 다른 모드가 추가한 광석도 규약을 따랐다면 함께 덮인다.</li>
 * </ul>
 *
 * <p>없는 태그를 적어도 예외가 나지 않는다. 태그가 비어 있는 것과 같아 아무 블록도 걸리지
 * 않을 뿐이다. 그래서 넓은 태그와 좁은 태그를 겹쳐 적어 두는 편이 안전하다.
 *
 * <h2>세 가지 상태</h2>
 * <ul>
 *   <li>{@code blocks} 를 <b>안 적었으면</b> 모든 블록에 걸린다 ({@link #ALL}).</li>
 *   <li>배열을 적었는데 쓸 만한 항목이 하나도 없으면 {@code null} 을 돌려준다. 부르는 쪽은
 *       그 효과를 통째로 버린다. 오타를 "모든 블록"으로 조용히 넘기지 않기 위해서다.</li>
 *   <li>그 밖에는 적은 대로다.</li>
 * </ul>
 *
 * <p>레지스트리 조회는 처음 판정할 때 한 번만 한다. 정의를 읽는 시점에는 블록 레지스트리가
 * 아직 준비되지 않았을 수 있어 {@code StatusEffectPerk} 와 같은 방식을 쓴다.
 */
public final class BlockSelector {
	/** 태그 미준비 경고를 한 번만 내기 위한 표시. */
	private static boolean tagWarningShown;

	/** 한 효과에 적을 수 있는 항목 수 상한. 정의 파일이 실수로 부풀어도 판정 비용을 묶어 둔다. */
	public static final int MAX_ENTRIES = 128;

	/** {@code blocks} 를 안 적었을 때. 모든 블록에 걸린다. */
	public static final BlockSelector ALL = new BlockSelector(List.of(), List.of());

	private final List<TagKey<Block>> tags;
	private final List<Identifier> blockIds;

	/** 처음 판정할 때 찾아 두는 블록들. 못 찾은 id 는 빠진다. */
	private volatile @Nullable Set<Block> resolvedBlocks;

	private BlockSelector(List<TagKey<Block>> tags, List<Identifier> blockIds) {
		this.tags = List.copyOf(tags);
		this.blockIds = List.copyOf(blockIds);
	}

	/**
	 * {@code blocks} 필드를 읽는다.
	 *
	 * @return 안 적었으면 {@link #ALL}, 적었는데 쓸 만한 항목이 없으면 {@code null}
	 */
	public static @Nullable BlockSelector fromJson(String perkId, String typeId, JsonObject json) {
		List<String> raw = PerkEffectType.readStringList(json, "blocks");
		if (raw == null) {
			return ALL;
		}
		if (raw.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 blocks 가 비어 있거나 배열이 아닙니다. 모든 블록을 뜻하게 하려면 필드를 지우세요",
					perkId, typeId);
			return null;
		}
		if (raw.size() > MAX_ENTRIES) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 의 blocks 항목이 너무 많습니다 ({}개)",
					perkId, typeId, raw.size());
			return null;
		}

		List<TagKey<Block>> tags = new ArrayList<>();
		List<Identifier> blockIds = new ArrayList<>();
		for (String entry : raw) {
			boolean isTag = entry.startsWith("#");
			String body = isTag ? entry.substring(1).trim() : entry;
			Identifier parsed = Identifier.tryParse(body);
			if (parsed == null) {
				SharedFateMod.LOGGER.warn("증강 {}: {} 의 blocks 에 올바르지 않은 이름이 있습니다 ({})",
						perkId, typeId, entry);
				return null;
			}
			if (isTag) {
				tags.add(TagKey.create(Registries.BLOCK, parsed));
			} else {
				blockIds.add(parsed);
			}
		}
		return new BlockSelector(tags, blockIds);
	}

	/** 모든 블록에 걸리는가. */
	public boolean matchesEverything() {
		return tags.isEmpty() && blockIds.isEmpty();
	}

	/**
	 * 이 블록이 목록에 걸리는가.
	 *
	 * <p>어느 항목 하나라도 맞으면 참이다. 태그가 없는 이름을 적었으면 그 항목만 조용히
	 * 지나간다.
	 */
	public boolean matches(@Nullable BlockState state) {
		if (state == null) {
			return false;
		}
		if (matchesEverything()) {
			return true;
		}
		// 태그는 데이터팩이 올라와야 묶인다. 그 전에 물으면 예외가 나므로 태그 판정만 건너뛴다.
		try {
			for (TagKey<Block> tag : tags) {
				if (state.is(tag)) {
					return true;
				}
			}
		} catch (IllegalStateException tagsNotBound) {
			warnTagsNotBound();
		}
		Set<Block> blocks = resolveBlocks();
		return !blocks.isEmpty() && blocks.contains(state.getBlock());
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
				"블록 태그가 아직 준비되지 않아 태그 판정을 건너뜁니다. 데이터팩이 올라온 뒤에는 정상 동작합니다.");
	}

	/** 적힌 태그들. 시험용이다. */
	public List<TagKey<Block>> tags() {
		return tags;
	}

	/** 적힌 블록 id 들. 시험용이다. */
	public List<Identifier> blockIds() {
		return blockIds;
	}

	/**
	 * 블록 id 를 실제 블록으로 찾아 둔다.
	 *
	 * <p>두 스레드가 동시에 들어와도 같은 결과를 만들 뿐이라 잠그지 않는다. 못 찾은 id 는
	 * 결과에서 빠지고, 다음 호출에서 다시 찾지 않는다.
	 */
	private Set<Block> resolveBlocks() {
		Set<Block> cached = resolvedBlocks;
		if (cached != null) {
			return cached;
		}
		Set<Block> found = new LinkedHashSet<>();
		for (Identifier blockId : blockIds) {
			try {
				BuiltInRegistries.BLOCK.get(blockId)
						.ifPresentOrElse(holder -> found.add(holder.value()),
								() -> SharedFateMod.LOGGER.warn(
										"증강 효과가 가리키는 블록을 찾을 수 없습니다: {}", blockId));
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("블록 {} 을 찾다가 실패했습니다", blockId, error);
			}
		}
		Set<Block> result = found.isEmpty() ? Set.of() : Collections.unmodifiableSet(found);
		resolvedBlocks = result;
		return result;
	}
}
