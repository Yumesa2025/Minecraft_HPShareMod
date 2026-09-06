package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 정해진 주기마다 팀에게 무작위 보급을 내려 준다. 「보급」 세트의 세 단계가 쓰는 타입이다.
 *
 * <p><b>이 클래스는 시각을 모른다.</b> "얼마나 자주 · 무엇을 · 어떤 확률로"만 들고 있는 자료
 * 그릇이고, 주기를 재고 실제로 아이템을 넣는 일은 {@link com.sharedfate.perk.PerkSupplyDrops}
 * 가 맡는다.
 *
 * <h2>{@code periodic} + {@code item_grant} 로는 만들 수 없다</h2>
 * <ul>
 *   <li>{@link PeriodicEffect} 는 <b>붙였다 떼는 효과 전용</b>이다. 구간이 바뀌면 하위 효과의
 *       {@code apply}/{@code remove} 를 부를 뿐이다.</li>
 *   <li>{@link ItemGrantEffect} 는 {@code apply}/{@code remove} 가 <b>의도적으로 아무 일도 하지
 *       않는다</b>. 접속·부활·효과 갱신마다 다시 불리기 때문이다. 지급은 증강을 고른 그 순간
 *       {@code PerkItemGrants} 가 한 번 할 뿐이다.</li>
 * </ul>
 *
 * <p>둘을 겹치면 <b>파싱도 통과하고 경고 로그도 안 나오는데 영원히 아무 일도 일어나지 않는다.</b>
 * 「주기적으로 아이템을 준다」는 이 별도 타입과 별도 매니저로만 된다.
 *
 * <p>다만 <b>아이템 한 종류를 실제 {@link ItemStack} 으로 바꾸는 일</b>만은
 * {@link ItemGrantEffect} 를 재사용한다. 물약 컴포넌트와 지속시간 늘리기가 이미 거기서 검증돼
 * 있기 때문이다. <b>JSON 에 {@code item_grant} 가 등장하는 것이 아니고</b>, 그렇게 만든 내부
 * 객체는 이 클래스 밖으로 나가지 않는다({@code PerkItemGrants} 가 훑는 것은 증강의 효과 목록
 * 뿐이라 이 안에 든 것은 보이지 않는다).
 *
 * <h2>가장 높은 단계 하나만 실제로 돈다</h2>
 * <p>세트 단계는 누적이라 「보급」을 넷 모으면 2·3·4 단계가 <b>전부</b> 켜지고
 * {@code PerkSetEffects.activeEffectsOf} 는 {@code supply_drop} 을 <b>셋</b> 돌려준다. 그대로
 * 돌리면 보급이 세 번 온다. 그래서 {@link com.sharedfate.perk.PerkSupplyDrops#select} 가
 * <b>후보 중 하나만</b> 고른다. 고르는 기준이 {@link #priority()} 이고, 그 값을 여기 JSON 에
 * 적는다. 자세한 규칙은 그 메서드에 있다.
 *
 * <p>따라서 <b>상위 단계의 정의는 그 자체로 완결이어야 한다.</b> 4단계가 이길 때 3단계 정의는
 * 아예 돌지 않으므로, 3단계의 「강화」를 4단계 정의에도 그대로 적어 두어야 한다. 값이 겹쳐
 * 적히는 대신 「지금 도는 보급은 정확히 이 한 덩어리」가 파일만 보고도 읽힌다.
 *
 * <h2>JSON 형식</h2>
 * <pre>{@code
 * {
 *   "type": "supply_drop",
 *   "interval_minutes": 10,
 *   "priority": 2,
 *   "nothing_chance": 0.30,
 *   "rolls": 1,
 *   "entries": [
 *     { "id": "minecraft:coal", "min": 10, "max": 15, "weight": 120 },
 *     { "id": "minecraft:golden_apple", "count": 1, "weight": 25 },
 *     { "id": "minecraft:potion", "count": 1, "weight": 20,
 *       "potion": "minecraft:fire_resistance", "duration_minutes": 1 },
 *     { "id": "minecraft:netherite_ingot", "count": 1, "weight": 1 }
 *   ]
 * }
 * }</pre>
 *
 * <h3>필드</h3>
 * <ul>
 *   <li>{@code interval_minutes} — 보급이 오는 주기(분). 반드시 적는다.
 *       {@value #MIN_INTERVAL_MINUTES}~{@value #MAX_INTERVAL_MINUTES}</li>
 *   <li>{@code priority} — 여럿이 켜졌을 때 누가 실제로 도는가. <b>큰 쪽이 이긴다.</b>
 *       안 적으면 0. 0~{@value #MAX_PRIORITY}</li>
 *   <li>{@code nothing_chance} — 이번 회에 <b>아무것도 안 올</b> 확률. 0~{@value #MAX_NOTHING_CHANCE}.
 *       안 적으면 0(반드시 무언가 온다). 아래 표와 <b>따로</b> 굴린다 — 표의 가중치를 손봐도
 *       꽝 확률이 흔들리지 않게 하려는 것이다</li>
 *   <li>{@code rolls} — 꽝이 아닐 때 표에서 몇 번 뽑는가. 안 적으면 1.
 *       1~{@value #MAX_ROLLS}. <b>같은 항목이 두 번 나올 수 있다</b>(복원 추출)</li>
 *   <li>{@code entries} — 뽑기 표. 비어 있으면 줄 것이 없는 셈이라 효과 자체를 버린다</li>
 * </ul>
 *
 * <h3>{@code entries} 항목</h3>
 * <ul>
 *   <li>{@code id} — 아이템 식별자. 반드시 적는다</li>
 *   <li>{@code min} / {@code max} — 개수 범위. 양 끝을 포함한다. 하나만 적으면 나머지도 같은
 *       값이고, 둘 다 없으면 1 이다. {@code count} 로 적으면 {@code min}={@code max} 다.
 *       1~{@value ItemGrantEffect#MAX_COUNT}</li>
 *   <li>{@code weight} — 뽑힐 가중치. 반드시 적는다. 확률은 「이 값 ÷ 표 전체 합」이다.
 *       1~{@value #MAX_WEIGHT}</li>
 *   <li>{@code potion} / {@code duration_minutes} — {@code item_grant} 와 같은 뜻이다.
 *       {@code minecraft:potion} 에만 적는다</li>
 * </ul>
 *
 * <p>항목 하나가 잘못되면 <b>그 항목만</b> 버리고 나머지는 살린다. 다만 살아남은 항목이 하나도
 * 없으면 효과 자체를 버린다({@code null}) — {@link ItemGrantEffect} 와 같은 규칙이다.
 */
public final class SupplyDropEffect implements PerkEffect {
	/** 1분은 몇 틱인가. */
	public static final int TICKS_PER_MINUTE = 20 * 60;
	/** 받아들이는 주기 범위(분). 1분보다 짧으면 보급이 아니라 소나기다. */
	public static final int MIN_INTERVAL_MINUTES = 1;
	public static final int MAX_INTERVAL_MINUTES = 240;
	/** 표에 적을 수 있는 최대 항목 수. */
	public static final int MAX_ENTRIES = 64;
	/** 항목 하나의 최대 가중치. */
	public static final int MAX_WEIGHT = 100_000;
	/** 한 회에 뽑을 수 있는 최대 횟수. */
	public static final int MAX_ROLLS = 8;
	/** 적을 수 있는 최대 꽝 확률. 1.0 을 허용하면 영원히 아무것도 안 오는 정의가 된다. */
	public static final double MAX_NOTHING_CHANCE = 0.95;
	/** 적을 수 있는 최대 우선순위. */
	public static final int MAX_PRIORITY = 64;

	/**
	 * 뽑기 표의 항목 하나.
	 *
	 * @param itemId          아이템 식별자
	 * @param minCount        최소 개수(포함)
	 * @param maxCount        최대 개수(포함). {@code minCount} 이상이다
	 * @param weight          뽑힐 가중치
	 * @param potionId        물약 종류. 물약 아이템이 아니면 null
	 * @param durationMinutes 물약 지속시간(분). 0 이면 물약이 원래 가진 길이 그대로
	 */
	public record Entry(Identifier itemId, int minCount, int maxCount, int weight,
			@Nullable Identifier potionId, int durationMinutes) {

		/** 이 항목의 개수를 굴린다. 양 끝을 포함한다. */
		public int rollCount(RandomSource random) {
			if (random == null || maxCount <= minCount) {
				return minCount;
			}
			return minCount + random.nextInt(maxCount - minCount + 1);
		}
	}

	private final int intervalTicks;
	private final int priority;
	private final double nothingChance;
	private final int rolls;
	private final List<Entry> entries;
	private final int totalWeight;

	/**
	 * 항목 하나를 실제 아이템 묶음으로 바꾸는 해석기들. {@link #entries} 와 순서가 같다.
	 *
	 * <p>개수는 굴려서 정하므로 여기서는 전부 1개짜리로 만들어 두고 뽑을 때 개수만 바꾼다.
	 * 레지스트리 조회는 {@link ItemGrantEffect} 안에서 처음 지급할 때 한 번만 일어난다.
	 */
	private final List<ItemGrantEffect> resolvers;

	public SupplyDropEffect(int intervalTicks, int priority, double nothingChance, int rolls,
			List<Entry> entries) {
		this.intervalTicks = intervalTicks;
		this.priority = priority;
		this.nothingChance = nothingChance;
		this.rolls = rolls;
		this.entries = List.copyOf(entries);

		int sum = 0;
		List<ItemGrantEffect> built = new ArrayList<>(this.entries.size());
		for (Entry entry : this.entries) {
			sum += entry.weight();
			built.add(new ItemGrantEffect(List.of(new ItemGrantEffect.Entry(
					entry.itemId(), 1, entry.potionId(), entry.durationMinutes(), Map.of()))));
		}
		this.totalWeight = sum;
		this.resolvers = List.copyOf(built);
	}

	// ------------------------------------------------------------------ 정의 읽기

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Integer intervalMinutes = readInterval(perkId, json);
		if (intervalMinutes == null) {
			return null;
		}

		int priority = PerkEffectType.readInt(json, "priority", 0);
		if (priority < 0 || priority > MAX_PRIORITY) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: supply_drop 의 priority 가 0~{} 범위를 벗어났습니다 ({})",
					perkId, MAX_PRIORITY, priority);
			return null;
		}

		double nothingChance = 0.0;
		Double rawNothing = PerkEffectType.readDouble(json, "nothing_chance");
		if (rawNothing == null) {
			rawNothing = PerkEffectType.readDouble(json, "nothingChance");
		}
		if (rawNothing != null) {
			if (rawNothing < 0.0 || rawNothing > MAX_NOTHING_CHANCE) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: supply_drop 의 nothing_chance 가 0~{} 범위를 벗어났습니다 ({})",
						perkId, MAX_NOTHING_CHANCE, rawNothing);
				return null;
			}
			nothingChance = rawNothing;
		}

		int rolls = PerkEffectType.readInt(json, "rolls", 1);
		if (rolls < 1 || rolls > MAX_ROLLS) {
			SharedFateMod.LOGGER.warn("증강 {}: supply_drop 의 rolls 가 1~{} 범위를 벗어났습니다 ({})",
					perkId, MAX_ROLLS, rolls);
			return null;
		}

		JsonElement element = json.get("entries");
		if (element == null || !element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: supply_drop 효과에 entries 배열이 없습니다", perkId);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		List<Entry> entries = new ArrayList<>();
		for (JsonElement raw : array) {
			if (entries.size() >= MAX_ENTRIES) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: supply_drop 항목이 {}개를 넘어 나머지를 버립니다", perkId, MAX_ENTRIES);
				break;
			}
			Entry entry = readEntry(perkId, raw);
			if (entry != null) {
				entries.add(entry);
			}
		}
		if (entries.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: supply_drop 에 읽을 수 있는 항목이 하나도 없습니다", perkId);
			return null;
		}

		return new SupplyDropEffect(
				intervalMinutes * TICKS_PER_MINUTE, priority, nothingChance, rolls, entries);
	}

	/** {@code interval_minutes} 를 읽는다. {@code intervalMinutes} 로 적어도 같다. */
	private static @Nullable Integer readInterval(String perkId, JsonObject json) {
		String key = json.has("interval_minutes") ? "interval_minutes" : "intervalMinutes";
		Double minutes = PerkEffectType.readDouble(json, key);
		if (minutes == null || minutes < MIN_INTERVAL_MINUTES || minutes > MAX_INTERVAL_MINUTES) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: supply_drop 의 interval_minutes 가 없거나 {}~{} 범위를 벗어났습니다 ({})",
					perkId, MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES, minutes);
			return null;
		}
		return (int) Math.round(minutes);
	}

	/** 항목 하나를 읽는다. 잘못됐으면 경고를 남기고 null — 그 항목만 빠지고 나머지는 살아남는다. */
	private static @Nullable Entry readEntry(String perkId, @Nullable JsonElement raw) {
		if (raw == null || !raw.isJsonObject()) {
			SharedFateMod.LOGGER.warn("증강 {}: supply_drop 항목이 객체가 아니라 건너뜁니다", perkId);
			return null;
		}
		JsonObject json = raw.getAsJsonObject();

		String rawItem = PerkEffectType.readString(json, "id");
		if (rawItem == null || rawItem.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: supply_drop 항목에 id 가 없어 건너뜁니다", perkId);
			return null;
		}
		Identifier itemId = Identifier.tryParse(rawItem.trim());
		if (itemId == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 아이템 이름 {} 을 건너뜁니다", perkId, rawItem);
			return null;
		}

		int weight = PerkEffectType.readInt(json, "weight", 0);
		if (weight < 1 || weight > MAX_WEIGHT) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: {} 의 weight 가 없거나 1~{} 범위를 벗어나 건너뜁니다 ({})",
					perkId, itemId, MAX_WEIGHT, weight);
			return null;
		}

		// count 를 적으면 min·max 가 그 값으로 고정된다. 둘 중 하나만 적으면 나머지도 같은 값이다.
		int fallback = PerkEffectType.readInt(json, "count", 0);
		int min = PerkEffectType.readInt(json, "min", fallback);
		int max = PerkEffectType.readInt(json, "max", fallback);
		if (min < 1 && max >= 1) {
			min = max;
		}
		if (max < 1 && min >= 1) {
			max = min;
		}
		if (min < 1 && max < 1) {
			min = 1;
			max = 1;
		}
		if (min > max) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 의 min 이 max 보다 커서 건너뜁니다 ({}~{})",
					perkId, itemId, min, max);
			return null;
		}
		if (max > ItemGrantEffect.MAX_COUNT) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 의 개수가 1~{} 범위를 벗어나 건너뜁니다 ({}~{})",
					perkId, itemId, ItemGrantEffect.MAX_COUNT, min, max);
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
		String durationKey =
				json.has("duration_minutes") ? "duration_minutes" : "durationMinutes";
		Double rawDuration = PerkEffectType.readDouble(json, durationKey);
		if (rawDuration != null) {
			if (potionId == null || rawDuration < 1
					|| rawDuration > ItemGrantEffect.MAX_DURATION_MINUTES) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: {} 값이 올바르지 않아 건너뜁니다 ({}). 물약 항목에만 1~{} 로 적을 수 있습니다",
						perkId, durationKey, rawDuration, ItemGrantEffect.MAX_DURATION_MINUTES);
				return null;
			}
			durationMinutes = (int) Math.round(rawDuration);
		}

		return new Entry(itemId, min, max, weight, potionId, durationMinutes);
	}

	// ------------------------------------------------------------------ 조회

	/** 보급이 오는 주기(틱). */
	public int intervalTicks() {
		return intervalTicks;
	}

	/** 보급이 오는 주기(분). */
	public int intervalMinutes() {
		return intervalTicks / TICKS_PER_MINUTE;
	}

	/**
	 * 여럿이 켜졌을 때 누가 실제로 도는가. <b>큰 쪽이 이긴다.</b>
	 *
	 * <p>「보급」 세트는 2·3·4 단계에 각각 2·3·4 가 적혀 있다.
	 */
	public int priority() {
		return priority;
	}

	/** 이번 회에 아무것도 안 올 확률. */
	public double nothingChance() {
		return nothingChance;
	}

	/** 꽝이 아닐 때 표에서 뽑는 횟수. */
	public int rolls() {
		return rolls;
	}

	/** 뽑기 표. 파일에 적힌 순서 그대로다. */
	public List<Entry> entries() {
		return entries;
	}

	/** 표 전체 가중치의 합. 항목 하나의 확률은 「그 항목의 가중치 ÷ 이 값」이다. */
	public int totalWeight() {
		return totalWeight;
	}

	// ------------------------------------------------------------------ 뽑기

	/**
	 * 이번 회가 꽝인가.
	 *
	 * <p>표와 <b>따로</b> 굴린다. 표의 가중치를 아무리 손봐도 꽝 확률은 적은 그대로다.
	 */
	public boolean isNothing(@Nullable RandomSource random) {
		if (nothingChance <= 0.0) {
			return false;
		}
		return random == null || random.nextDouble() < nothingChance;
	}

	/**
	 * 표에서 항목 하나를 가중치대로 뽑는다.
	 *
	 * <p>가중치의 누적 합에서 한 점을 고르는 표준적인 방식이다. 표가 비는 일은 없다 —
	 * 항목이 하나도 없는 정의는 {@link #fromJson} 이 이미 버렸다.
	 */
	public Entry pick(@Nullable RandomSource random) {
		return entries.get(pickIndex(random));
	}

	/** {@link #pick} 과 같되 표에서의 자리를 돌려준다. 해석기를 곧바로 찾을 때 쓴다. */
	public int pickIndex(@Nullable RandomSource random) {
		if (random == null || totalWeight <= 1) {
			return 0;
		}
		int point = random.nextInt(totalWeight);
		for (int index = 0; index < entries.size(); index++) {
			point -= entries.get(index).weight();
			if (point < 0) {
				return index;
			}
		}
		// 반올림 오차가 없는 정수 연산이라 여기 닿지 않는다. 닿더라도 마지막 항목이 답이다.
		return entries.size() - 1;
	}

	/**
	 * 이번 회에 실제로 줄 아이템 묶음들.
	 *
	 * <p>꽝이면 <b>빈 목록</b>이다. 그 외에는 {@link #rolls()} 번 뽑아 개수까지 정한 묶음들이다.
	 * 같은 항목이 두 번 나올 수 있고, 그때는 묶음이 둘 나간다 — 공유 목록에 넣을 때
	 * {@code TeamState.restoreOverflow} 가 같은 아이템끼리 알아서 합쳐 준다.
	 *
	 * @param registries 아이템 해석에 쓸 레지스트리. 없으면 {@code null} 이어도 된다
	 *                   (이 표에는 인챈트가 없으므로 결과가 달라지지 않는다)
	 */
	public List<ItemStack> roll(@Nullable RandomSource random,
			@Nullable HolderLookup.Provider registries) {
		if (isNothing(random)) {
			return List.of();
		}
		List<ItemStack> drawn = new ArrayList<>(rolls);
		for (int i = 0; i < rolls; i++) {
			int index = pickIndex(random);
			Entry entry = entries.get(index);
			ItemStack stack = stackAt(index, entry.rollCount(random), registries);
			if (stack != null && !stack.isEmpty()) {
				drawn.add(stack);
			}
		}
		return drawn;
	}

	/**
	 * 항목 하나를 그 개수만큼 담은 묶음. 아이템을 찾지 못하면 null.
	 *
	 * <p>한 칸 최대치를 넘는 개수도 그대로 담는다. 공유 목록에 넣는
	 * {@code TeamState.restoreOverflow} 가 칸 단위로 나눠 넣기 때문에 여기서 쪼갤 필요가 없다.
	 */
	public @Nullable ItemStack stackFor(Entry entry, int count,
			@Nullable HolderLookup.Provider registries) {
		return stackAt(entries.indexOf(entry), count, registries);
	}

	/** {@link #stackFor} 와 같되 표에서의 자리로 찾는다. */
	public @Nullable ItemStack stackAt(int position, int count,
			@Nullable HolderLookup.Provider registries) {
		if (position < 0 || position >= resolvers.size() || count < 1) {
			return null;
		}
		List<ItemStack> resolved = resolvers.get(position).grantStacks(registries);
		if (resolved.isEmpty()) {
			// 레지스트리에 없는 아이템이다. ItemGrantEffect 가 이미 경고를 남겼다.
			return null;
		}
		ItemStack stack = resolved.get(0);
		stack.setCount(count);
		return stack;
	}

	// ------------------------------------------------------------------ 붙였다 떼기 없음

	/**
	 * 아무 일도 하지 않는다.
	 *
	 * <p>{@code apply} 는 접속·부활·효과 갱신마다 다시 불리므로 여기서 아이템을 주면 접속할
	 * 때마다 보급이 쏟아진다. 지급 시점은
	 * {@link com.sharedfate.perk.PerkSupplyDrops} 가 주기를 재어 정하는 그때 하나뿐이다.
	 */
	@Override
	public void apply(net.minecraft.server.level.ServerPlayer player) {
	}

	/** 아무 일도 하지 않는다. 붙여 둔 것이 없으므로 걷어낼 것도 없다. */
	@Override
	public void remove(net.minecraft.server.level.ServerPlayer player) {
	}
}
