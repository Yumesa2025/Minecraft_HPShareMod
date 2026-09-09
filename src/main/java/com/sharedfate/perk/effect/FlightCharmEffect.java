package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkBlessingSet;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.team.TeamState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.UseCooldown;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 「비행 부적」을 하나 지급하고, 그것을 우클릭하면 잠깐 하늘을 날 수 있다.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "flight_charm", "flight_seconds": 10, "cooldown_seconds": 60 }
 * </pre>
 *
 * <p>두 값 모두 생략할 수 있고, 생략하면 {@link #DEFAULT_FLIGHT_SECONDS}·
 * {@link #DEFAULT_COOLDOWN_SECONDS} 다. 범위를 벗어난 값은 정의를 버리지 않고 범위 안으로 자른다.
 * 시간이 틀렸다고 증강 전체를 버리면 손해가 더 크기 때문이다.
 *
 * <h2>새 아이템을 등록하지 않는다</h2>
 * <p>{@link DiamondSundialEffect}·{@link RallyShardEffect} 와 같은 이유다. 아이템을 새로
 * 등록하면 텍스처와 등록이 클라이언트에도 필요해져 「모드를 안 깐 사람도 들어올 수 있다」는
 * 전제가 깨진다. 그래서 비행 부적은 <b>바닐라 {@code minecraft:phantom_membrane}(팬텀 막)</b> 에
 * 이름과 표식을 붙인 것이다.
 *
 * <ul>
 *   <li><b>표식</b> — {@code minecraft:custom_data} 에 {@code {sharedfate_item: "flight_charm"}}
 *       을 넣는다. 생존 모드에서 플레이어가 붙일 방법이 없는 컴포넌트라, 모루에서 이름만
 *       「비행 부적」으로 바꿔도 가짜가 만들어지지 않는다. 판정이 이름이 아니라 표식을 보는
 *       이유가 이것이다.</li>
 *   <li><b>쿨타임 묶음</b> — {@code minecraft:use_cooldown} 에 {@link #COOLDOWN_GROUP} 을 적는다.
 *       26.2 의 {@code ItemCooldowns.getCooldownGroup} 은 이 컴포넌트가 있으면 아이템 id 대신
 *       그 값을 쓰므로, 부적에 건 쿨타임이 <b>평범한 팬텀 막까지 잠그지 않는다.</b> 이 컴포넌트는
 *       묶음 이름만 정할 뿐 저절로 쿨타임을 걸지는 않는다 — 실제로 거는 것은
 *       {@link com.sharedfate.perk.PerkFlightCharm} 이 {@code getCooldowns().addCooldown} 으로
 *       한다. 그래야 바닐라 쿨타임 게이지가 그대로 뜬다.</li>
 * </ul>
 *
 * <h2>왜 팬텀 막인가</h2>
 * <p>조합·수리 판정은 아이템 종류만 보고 컴포넌트를 무시한다. 그래서 표식을 붙여도 <b>재료로
 * 넣으면 그대로 사라진다.</b> 팬텀 막이 재료로 쓰이는 곳은 겉날개 수리와 「느린 낙하」 물약
 * 둘뿐인데, 부적은 아래에서 설명하는 대로 핫바 1번 칸에 잠겨 있어 애초에 조합대나 모루로
 * 옮길 수 없다. 소재가 나는 「하늘」과도 뜻이 맞는다.
 *
 * <h2>핫바 1번 칸에 잠긴다</h2>
 * <p>부적은 지급된 뒤 공유 인벤토리의 {@link #LOCKED_SLOT} 번 칸(핫바 맨 왼쪽)에 고정된다.
 * 그 칸에서 꺼내거나 옮기거나 버릴 수 없다. 잠그는 일은
 * {@code com.sharedfate.mixin.SlotFlightCharmLockMixin} 이, 잃어버린 부적을 그 자리에 되돌리는
 * 일은 {@link com.sharedfate.perk.PerkFlightCharm} 의 주기 점검이 한다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 값과 아이템 모양만 들고 있는 자료 그릇이다. 우클릭을 잡고, 비행을 켜고 끄고,
 * 쿨타임을 걸고, 칸을 지키는 일은 전부 {@link com.sharedfate.perk.PerkFlightCharm} 이 한다.
 */
public final class FlightCharmEffect implements PerkEffect {
	/**
	 * 기본 비행 시간(초).
	 *
	 * <p>값을 조절할 때는 <b>이 상수와 {@code sharedfate-perks-default.json} 의 값이 어긋나지
	 * 않게</b> 같이 봐야 한다.
	 */
	public static final int DEFAULT_FLIGHT_SECONDS = 10;
	/** 비행 시간 하한(초). 1초보다 짧으면 뜨기도 전에 끝나 뜻이 없다. */
	public static final int MIN_FLIGHT_SECONDS = 1;
	/**
	 * 비행 시간 상한(초). 1분이면 쿨타임 하한(5초)과 겹쳐 사실상 상시 비행이 되는 지점이라,
	 * 그 위는 「잠깐 난다」가 아니라 다른 증강이 된다.
	 */
	public static final int MAX_FLIGHT_SECONDS = 60;

	/** 기본 쿨타임(초). 1분이다. */
	public static final int DEFAULT_COOLDOWN_SECONDS = 60;
	/** 쿨타임 하한(초). */
	public static final int MIN_COOLDOWN_SECONDS = 5;
	/** 쿨타임 상한(초). 10분이면 한 회차에 몇 번 안 되므로 그 위는 뜻이 없다. */
	public static final int MAX_COOLDOWN_SECONDS = 600;

	private static final int TICKS_PER_SECOND = 20;

	/**
	 * 부적이 고정되는 칸. 공유 인벤토리의 0번, 곧 핫바 맨 왼쪽 칸이다.
	 *
	 * <p>{@code Slot.getContainerSlot()} 이 돌려주는 번호와 같은 자리다. 화면마다 달라지는
	 * {@code Slot.index}(메뉴 안의 순번)와 혼동하면 안 된다.
	 */
	public static final int LOCKED_SLOT = 0;

	/** 비행 부적으로 쓰는 바닐라 아이템. 26.2 에 {@code assets/minecraft/items/phantom_membrane.json} 이 있다. */
	public static final Identifier ITEM = Identifier.withDefaultNamespace("phantom_membrane");

	/** {@code minecraft:custom_data} 에 남기는 표식의 키. 해시계·소집의 조각과 같은 키를 값만 달리해 쓴다. */
	public static final String MARKER_KEY = "sharedfate_item";
	/** {@code minecraft:custom_data} 에 남기는 표식의 값. */
	public static final String MARKER_VALUE = "flight_charm";

	/** 쿨타임 묶음. 평범한 팬텀 막과 갈라 두려고 모드 이름공간을 쓴다. */
	public static final Identifier COOLDOWN_GROUP = SharedFateMod.id("flight_charm");

	/** 아이템에 붙는 이름. 알아보기 위한 것일 뿐 판정에는 쓰지 않는다. */
	public static final String DISPLAY_NAME = "비행 부적";

	private final int flightTicks;
	private final int amplifiedFlightTicks;
	private final int cooldownTicks;

	/** 레지스트리에서 찾아 만든 견본. 처음 지급할 때 한 번 만들고 계속 쓴다. */
	private @Nullable ItemStack template;

	public FlightCharmEffect(int flightTicks, int cooldownTicks) {
		this(flightTicks, flightTicks, cooldownTicks);
	}

	/**
	 * @param amplifiedFlightTicks 「가호 3」이 켜졌을 때 날 수 있는 시간. 안 적으면 평소와 같다
	 */
	public FlightCharmEffect(int flightTicks, int amplifiedFlightTicks, int cooldownTicks) {
		this.flightTicks = flightTicks;
		this.amplifiedFlightTicks = amplifiedFlightTicks;
		this.cooldownTicks = cooldownTicks;
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>값이 범위를 벗어나면 경고만 남기고 잘라 쓴다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int flight = clamp(perkId, "flight_seconds",
				PerkEffectType.readInt(json, flightKey(json), DEFAULT_FLIGHT_SECONDS),
				MIN_FLIGHT_SECONDS, MAX_FLIGHT_SECONDS);
		int amplified = clamp(perkId, "amplified_flight_seconds",
				PerkEffectType.readInt(json, amplifiedFlightKey(json), flight),
				MIN_FLIGHT_SECONDS, MAX_FLIGHT_SECONDS);
		int cooldown = clamp(perkId, "cooldown_seconds",
				PerkEffectType.readInt(json, cooldownKey(json), DEFAULT_COOLDOWN_SECONDS),
				MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS);
		return new FlightCharmEffect(flight * TICKS_PER_SECOND, amplified * TICKS_PER_SECOND,
				cooldown * TICKS_PER_SECOND);
	}

	private static String amplifiedFlightKey(JsonObject json) {
		return json != null && json.has("amplifiedFlightSeconds")
				? "amplifiedFlightSeconds"
				: "amplified_flight_seconds";
	}

	/** 카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다. */
	private static String flightKey(JsonObject json) {
		return json != null && json.has("flightSeconds") ? "flightSeconds" : "flight_seconds";
	}

	private static String cooldownKey(JsonObject json) {
		return json != null && json.has("cooldownSeconds") ? "cooldownSeconds" : "cooldown_seconds";
	}

	private static int clamp(String perkId, String key, int value, int min, int max) {
		if (value >= min && value <= max) {
			return value;
		}
		int cut = Math.max(min, Math.min(max, value));
		SharedFateMod.LOGGER.warn(
				"증강 {}: flight_charm 의 {} 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, key, min, max, cut, value);
		return cut;
	}

	/** 한 번 우클릭했을 때 날 수 있는 시간(틱). */
	public int flightTicks() {
		return flightTicks;
	}

	/** 「가호 3」이 켜졌을 때 날 수 있는 시간(틱). 안 적으면 {@link #flightTicks()} 와 같다. */
	public int amplifiedFlightTicks() {
		return amplifiedFlightTicks;
	}

	/**
	 * 지금 이 팀에서 한 번에 날 수 있는 시간(틱).
	 *
	 * <p>부적은 팀 공유 인벤토리의 핫바 1번 칸에 꽂히므로 <b>「가호 4」의 「전원에게」는 이미
	 * 성립한다</b> — 누가 들어도 쓸 수 있다. 그래서 가호가 이 효과에 더하는 것은 3단계의
	 * 시간 연장 하나뿐이다.
	 */
	public int flightTicksFor(@Nullable TeamState state) {
		return PerkBlessingSet.teamAmplified(state) ? amplifiedFlightTicks : flightTicks;
	}

	/** 쿨타임(틱). */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	/**
	 * 이번 선택으로 지급할 비행 부적 하나.
	 *
	 * <p>부를 때마다 새 사본을 돌려준다. 받는 쪽이 개수를 깎으므로 견본을 그대로 넘기면 두 번째
	 * 지급 때 빈 묶음이 나간다. 아이템을 레지스트리에서 찾는 일은 정의를 읽는 시점이 아니라
	 * 처음 지급할 때 한다 — 정의를 읽는 시점에는 레지스트리가 아직 준비되지 않았을 수 있다.
	 *
	 * @return 부적 한 개. 아이템을 찾지 못했으면 null
	 */
	public @Nullable ItemStack createItem() {
		ItemStack cached = template;
		if (cached == null) {
			cached = build();
			if (cached == null) {
				return null;
			}
			template = cached;
		}
		return cached.copy();
	}

	private @Nullable ItemStack build() {
		Item item;
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(ITEM);
			if (found.isEmpty()) {
				SharedFateMod.LOGGER.warn("비행 부적으로 쓸 아이템을 찾을 수 없습니다: {}", ITEM);
				return null;
			}
			item = found.get().value();
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("비행 부적으로 쓸 아이템 {} 을 찾다가 실패했습니다", ITEM, error);
			return null;
		}
		// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
		if (item == Items.AIR) {
			SharedFateMod.LOGGER.warn("비행 부적으로 쓸 아이템을 찾을 수 없습니다: {}", ITEM);
			return null;
		}
		return decorate(new ItemStack(item, 1), cooldownTicks / (float) TICKS_PER_SECOND);
	}

	/**
	 * 팬텀 막 하나를 비행 부적으로 만든다. 표식·이름·쿨타임 묶음을 붙인다.
	 *
	 * @param cooldownSeconds {@code minecraft:use_cooldown} 에 적을 초. 이 컴포넌트는 <b>묶음 이름을
	 *                        정하려고</b> 붙이는 것이라 실제로 쿨타임을 거는 값이 아니다. 그래도
	 *                        정의와 어긋난 숫자를 남기지 않도록 같은 값을 적어 둔다.
	 */
	public static ItemStack decorate(ItemStack stack, float cooldownSeconds) {
		CompoundTag marker = new CompoundTag();
		marker.putString(MARKER_KEY, MARKER_VALUE);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
		// 이름은 알아보기 위한 것일 뿐 판정에는 쓰지 않는다. 기본 기울임은 꺼 둔다.
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(DISPLAY_NAME)
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.AQUA)));
		stack.set(DataComponents.USE_COOLDOWN,
				new UseCooldown(cooldownSeconds, Optional.of(COOLDOWN_GROUP)));
		return stack;
	}

	/**
	 * 이 묶음이 비행 부적인가.
	 *
	 * <p>이름이 아니라 {@code minecraft:custom_data} 의 표식으로 판정한다.
	 */
	public static boolean isFlightCharm(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return false;
		}
		return MARKER_VALUE.equals(data.copyTag().getStringOr(MARKER_KEY, ""));
	}
}
