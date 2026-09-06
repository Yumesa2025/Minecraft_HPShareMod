package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.BlockSelector;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 정해진 블록의 전리품에서 <b>정해진 아이템을 걷어 내고 대신 다른 것을 준다.</b>
 *
 * <p>예: {@code { "type": "drop_replace", "blocks": ["minecraft:wheat"],
 * "from": "minecraft:wheat", "item": "minecraft:golden_carrot", "min": 1, "max": 3 }} 은 밀을
 * 수확할 때 밀을 없애고 황금 당근을 1~3개 준다. 골드 「비옥한 땅」이 이 타입을 쓴다.
 *
 * <h2>{@code bonus_drop} 과 무엇이 다른가</h2>
 * <p>{@link BonusDropEffect} 는 원래 나오는 것 <b>위에 덤을 얹는다.</b> 이쪽은 원래 나오던 것을
 * <b>없애고 그 자리에 다른 것을 놓는다.</b> 「밀 대신」처럼 원래 전리품이 사라져야 하는 것은
 * 덤을 얹는 방식으로 표현할 수 없어 타입이 따로 있다.
 *
 * <h2>{@code from} 에 적은 것만 사라진다</h2>
 * <p>블록이 떨어뜨리는 것 중 {@code from} 과 <b>같은 아이템만</b> 걷어 내고 나머지는 그대로
 * 떨어진다. 밀 작물은 밀 1개와 씨앗 0~3개를 함께 떨어뜨리는데, {@code from} 이 밀이므로
 * <b>씨앗은 손대지 않는다.</b> 씨앗까지 없애면 다음 농사를 지을 수 없다.
 *
 * <p>이 규칙 하나가 「다 자란 것만 바뀐다」도 함께 해결한다. 덜 자란 밀 작물은 애초에 밀을
 * 떨어뜨리지 않고 씨앗만 내므로, 걷어 낼 것이 없어 아무 일도 일어나지 않는다. 자란 정도를 따로
 * 보지 않아도 결과가 같고, 작물이 아닌 블록에도 그대로 쓸 수 있다.
 *
 * <p>걷어 낸 것이 하나도 없으면 <b>대신 줄 것도 주지 않는다.</b> 「무엇이 사라졌을 때만 무엇을
 * 받는다」가 이 효과의 약속이다.
 *
 * <h2>개수</h2>
 * <p>{@code min}~{@code max} 사이에서 <b>고르게</b> 하나를 뽑는다. 한 번 캘 때 한 번만 뽑으므로,
 * {@code from} 이 여러 개 걷혀도 주는 양은 그 한 번의 결과다. 행운(Fortune)은 여기에 곱해지지
 * 않는다 — 전리품표를 다시 굴리는 것이 아니라 정의에 적힌 범위에서 그대로 뽑기 때문이다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 「어떤 블록에서, 무엇을 걷어 내고, 무엇을 몇 개 주는가」만 들고 있는 자료 그릇이다.
 * 언제 누가 무엇을 캤는지 보고 실제로 전리품을 갈아 끼우고 아이템을 넣어 주는 일은
 * {@link com.sharedfate.perk.PerkBlockBreaks} 가 맡는다.
 */
public final class DropReplaceEffect implements PerkEffect {
	/** 정의 파일에 적는 이름. 경고 문구에도 이 이름이 나간다. */
	public static final String TYPE_ID = "drop_replace";

	/** 한 번에 줄 수 있는 최대 개수. 바닐라 한 칸 최대치와 맞춘다. */
	public static final int MAX_COUNT = 64;

	private final BlockSelector blocks;
	private final Identifier fromId;
	private final Identifier itemId;
	private final int min;
	private final int max;

	/**
	 * 레지스트리에서 찾아 둔 아이템 둘. 처음 판정할 때 한 번만 찾는다.
	 *
	 * <p>정의를 읽는 시점에는 아이템 레지스트리가 아직 준비되지 않았을 수 있어 미뤄 둔다.
	 * {@link BlockSelector} 가 블록을 다루는 방식과 같다.
	 */
	private volatile @Nullable Resolved resolved;

	/** 찾아 둔 아이템. 이름을 찾지 못한 쪽은 {@code null} 이다. */
	private record Resolved(@Nullable Item from, @Nullable Item item) {
	}

	public DropReplaceEffect(@Nullable BlockSelector blocks, Identifier fromId, Identifier itemId,
			int min, int max) {
		this.blocks = blocks == null ? BlockSelector.ALL : blocks;
		this.fromId = fromId;
		this.itemId = itemId;
		this.min = min;
		this.max = max;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		BlockSelector blocks = BlockSelector.fromJson(perkId, TYPE_ID, json);
		if (blocks == null) {
			return null;
		}

		Identifier fromId = readItemId(perkId, json, "from");
		if (fromId == null) {
			return null;
		}
		Identifier itemId = readItemId(perkId, json, "item");
		if (itemId == null) {
			return null;
		}

		Integer min = readCount(perkId, json, "min", 1);
		if (min == null) {
			return null;
		}
		Integer max = readCount(perkId, json, "max", min);
		if (max == null) {
			return null;
		}
		if (min < 1 || max < min || max > MAX_COUNT) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 개수가 1~{} 범위의 min<=max 가 아닙니다 (min={}, max={})",
					perkId, TYPE_ID, MAX_COUNT, min, max);
			return null;
		}
		return new DropReplaceEffect(blocks, fromId, itemId, min, max);
	}

	/**
	 * 아이템 이름 하나를 읽는다.
	 *
	 * <p>없거나 이름 꼴이 아니면 경고를 남기고 {@code null} 이다. 레지스트리에 실제로 있는지는
	 * 여기서 보지 않는다 — 정의를 읽는 시점에는 아이템 레지스트리가 아직 없을 수 있다.
	 */
	private static @Nullable Identifier readItemId(String perkId, JsonObject json, String key) {
		String raw = PerkEffectType.readString(json, key);
		if (raw == null || raw.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 에 {} 아이템 이름이 없습니다", perkId, TYPE_ID, key);
			return null;
		}
		Identifier parsed = Identifier.tryParse(raw.trim());
		if (parsed == null) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 {} 에 올바르지 않은 아이템 이름이 있습니다 ({})", perkId, TYPE_ID, key, raw);
			return null;
		}
		return parsed;
	}

	/**
	 * 개수 하나를 읽는다.
	 *
	 * <p>필드가 없으면 {@code fallback} 이다. 적혀 있는데 숫자가 아니면 경고를 남기고
	 * {@code null} 을 돌려준다 — 오타 하나가 개수를 조용히 기본값으로 되돌리면, 정의를 고친
	 * 사람은 값이 반영된 줄 알고 지나간다.
	 */
	private static @Nullable Integer readCount(String perkId, JsonObject json, String key,
			int fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		Double value = PerkEffectType.readDouble(json, key);
		if (value == null) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 {} 가 숫자가 아닙니다 ({})", perkId, TYPE_ID, key, json.get(key));
			return null;
		}
		return (int) Math.floor(value);
	}

	// ------------------------------------------------------------------ 판정

	/** 이 블록에 걸리는 효과인가. */
	public boolean appliesTo(@Nullable BlockState state) {
		return blocks.matches(state);
	}

	/** 이 묶음이 걷어 낼 대상인가. */
	public boolean replaces(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item target = resolved().from();
		return target != null && stack.is(target);
	}

	/**
	 * 전리품 목록에서 걷어 낼 것을 빼낸 결과.
	 *
	 * @param kept    그대로 떨어뜨릴 것들. 원래 목록의 순서를 지킨다
	 * @param removed 걷어 낸 아이템의 총 개수. 0 이면 이번에는 바꿀 것이 없었다
	 */
	public record Outcome(List<ItemStack> kept, int removed) {
		public Outcome {
			kept = List.copyOf(kept);
		}
	}

	/**
	 * 전리품 목록을 「그대로 떨어질 것」과 「걷어 낼 것」으로 가른다.
	 *
	 * <p>묶음을 새로 만들지 않고 원래 목록의 묶음을 그대로 넘긴다. 부르는 쪽이 그것을 바로
	 * 떨어뜨리기 때문이다.
	 */
	public Outcome filter(@Nullable List<ItemStack> drops) {
		if (drops == null || drops.isEmpty()) {
			return new Outcome(List.of(), 0);
		}
		List<ItemStack> kept = new ArrayList<>(drops.size());
		int removed = 0;
		for (ItemStack drop : drops) {
			if (drop == null || drop.isEmpty()) {
				continue;
			}
			if (replaces(drop)) {
				removed += drop.getCount();
				continue;
			}
			kept.add(drop);
		}
		return new Outcome(kept, removed);
	}

	// ------------------------------------------------------------------ 개수 뽑기

	/** 뽑을 수 있는 값의 가짓수. {@code min} 부터 {@code max} 까지 모두 같은 확률이다. */
	public int spread() {
		return max - min + 1;
	}

	/**
	 * 이번에 줄 개수를 뽑는다.
	 *
	 * <p>난수원이 없으면 가장 적은 개수로 본다. 난수를 못 굴렸다고 큰 쪽을 줄 수는 없다.
	 */
	public int rollCount(@Nullable RandomSource random) {
		return random == null ? min : countForRoll(random.nextInt(spread()));
	}

	/**
	 * 뽑은 값에 해당하는 개수. 난수와 떨어져 있어 살아 있는 서버 없이 시험할 수 있다.
	 *
	 * @param roll {@code 0} 이상 {@link #spread()} 미만의 값. 벗어난 값은 양 끝으로 자른다
	 */
	public int countForRoll(int roll) {
		if (roll <= 0) {
			return min;
		}
		return Math.min(max, min + roll);
	}

	// ------------------------------------------------------------------ 지급

	/**
	 * 이번에 줄 아이템 묶음들. 한 칸 최대치를 넘으면 나눠 담는다.
	 *
	 * <p>아이템을 찾지 못했으면 빈 목록이다. 경고는 찾는 자리에서 이미 한 번 남겼다.
	 *
	 * <p>한도를 미리 지키는 것이 중요하다. 넘침 대기열은 {@code ItemStack} 코덱으로 저장되는데
	 * 그 코덱이 한도를 넘는 개수를 오류로 되돌리므로, 자리가 없어 대기열에 남은 큰 묶음은 서버를
	 * 껐다 켜는 순간 통째로 사라진다.
	 */
	public List<ItemStack> grantStacks(int count) {
		Item item = resolved().item();
		if (item == null || count <= 0) {
			return List.of();
		}
		int limit = Math.max(1, new ItemStack(item, 1).getMaxStackSize());
		List<ItemStack> stacks = new ArrayList<>();
		int remaining = count;
		while (remaining > 0) {
			int piece = Math.min(remaining, limit);
			stacks.add(new ItemStack(item, piece));
			remaining -= piece;
		}
		return stacks;
	}

	// ------------------------------------------------------------------ 알림

	/**
	 * 캔 사람의 액션바에 띄울 문구. 예: {@code [증강] 비옥한 땅 — 황금 당근 ×2}
	 *
	 * <p>아이템은 공유 인벤토리로 바로 들어가 바닥에 아무것도 튀지 않는다. 알려 주지 않으면
	 * 「밀이 왜 안 나오지」로만 보인다. 채팅이 아니라 액션바인 이유는 밭 하나를 베면 수십 번
	 * 나가기 때문이다 — 액션바는 다음 줄이 앞 줄을 덮고 사라진다.
	 *
	 * <p>아이템 이름은 {@link Component} 그대로 이어 붙인다. 번역 키를 살려 두어야 플레이어의
	 * 언어 설정대로 보인다.
	 */
	public static Component announcement(@Nullable String sourceName, @Nullable Component itemName,
			int count) {
		MutableComponent message = Component.literal("[증강]");
		if (sourceName != null && !sourceName.isBlank()) {
			message.append(Component.literal(" " + sourceName.trim()));
		}
		message.append(Component.literal(" —"));
		if (itemName != null) {
			message.append(Component.literal(" ")).append(itemName);
		}
		return message.append(Component.literal(" ×" + Math.max(0, count)));
	}

	// ------------------------------------------------------------------ 레지스트리 조회

	private Resolved resolved() {
		Resolved cached = resolved;
		if (cached != null) {
			return cached;
		}
		// 두 스레드가 동시에 들어와도 같은 결과를 만들 뿐이라 잠그지 않는다.
		Resolved found = new Resolved(lookup(fromId), lookup(itemId));
		resolved = found;
		return found;
	}

	private static @Nullable Item lookup(Identifier id) {
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(id);
			// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
			if (found.isEmpty() || found.get().value() == Items.AIR) {
				SharedFateMod.LOGGER.warn("{} 가 가리키는 아이템을 찾을 수 없습니다: {}", TYPE_ID, id);
				return null;
			}
			return found.get().value();
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("아이템 {} 을 찾다가 실패했습니다", id, error);
			return null;
		}
	}

	// ------------------------------------------------------------------ 조회

	public BlockSelector blocks() {
		return blocks;
	}

	/** 걷어 낼 아이템의 이름. */
	public Identifier fromId() {
		return fromId;
	}

	/** 대신 줄 아이템의 이름. */
	public Identifier itemId() {
		return itemId;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

	/** 레지스트리에서 찾은 「걷어 낼 아이템」. 못 찾았으면 null. 시험용이다. */
	public @Nullable Item fromItem() {
		return resolved().from();
	}

	/** 레지스트리에서 찾은 「대신 줄 아이템」. 못 찾았으면 null. 시험용이다. */
	public @Nullable Item grantItem() {
		return resolved().item();
	}
}
