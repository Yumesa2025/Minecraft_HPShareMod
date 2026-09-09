package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
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
 * 「소집의 조각」을 하나 지급하고, 그것을 우클릭하면 나머지 팀원을 자기 자리로 불러 모은다.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "rally_shard", "freeze_seconds": 3, "cooldown_seconds": 240 }
 * </pre>
 *
 * <p>두 값 모두 생략할 수 있고, 생략하면 {@link #DEFAULT_FREEZE_SECONDS}·
 * {@link #DEFAULT_COOLDOWN_SECONDS} 다. 범위를 벗어난 값은 정의를 버리지 않고 범위 안으로 자른다.
 *
 * <h2>새 아이템을 등록하지 않는다</h2>
 * <p>{@link DiamondSundialEffect} 와 같은 이유다. 아이템을 새로 등록하면 텍스처와 등록이
 * 클라이언트에도 필요해져 「모드를 안 깐 사람도 들어올 수 있다」는 전제가 깨진다. 그래서
 * 소집의 조각은 <b>바닐라 {@code minecraft:echo_shard}</b> 에 이름과 표식을 붙인 것이다.
 *
 * <ul>
 *   <li><b>표식</b> — {@code minecraft:custom_data} 에 {@code {sharedfate_item: "rally_shard"}}
 *       를 넣는다. 생존 모드에서 플레이어가 붙일 방법이 없는 컴포넌트라, 모루에서 이름만
 *       「소집의 조각」으로 바꿔도 가짜가 만들어지지 않는다.</li>
 *   <li><b>쿨타임 묶음</b> — {@code minecraft:use_cooldown} 에 {@link #COOLDOWN_GROUP} 을 적어,
 *       조각에 건 쿨타임이 평범한 메아리 조각까지 잠그지 않게 한다. 실제로 쿨타임을 거는 것은
 *       {@link com.sharedfate.perk.PerkRallyShard} 다.</li>
 * </ul>
 *
 * <h2>왜 메아리 조각인가</h2>
 * <p>조합 재료 판정은 아이템 종류만 보고 컴포넌트를 무시한다. 그래서 표식을 붙여도 <b>조합에
 * 넣으면 그대로 사라진다.</b> 메아리 조각이 재료로 쓰이는 곳은 회복 나침반 하나뿐이고, 이 모드는
 * 나침반을 증강으로 다루므로 그것을 만들 이유가 거의 없다. 네더의 별을 쓰면 신호기로 태워
 * 증강이 사라지므로 고르지 않았다.
 *
 * <h2>왜 {@code bubble_pop} 파티클인가</h2>
 * <p>{@code minecraft:bubble} 은 26.2 바이트코드에서 {@code BubbleParticle.tick} 이
 * {@code FluidTags.WATER} 를 확인하고 물이 아니면 스스로 {@code remove()} 한다. 물 밖에서는
 * 한 프레임도 보이지 않는다는 뜻이다. {@code BubblePopParticle} 에는 그 검사가 없어 공중에서도
 * 그대로 보인다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 값과 아이템 모양만 들고 있는 자료 그릇이다. 우클릭을 잡는 일은
 * {@link com.sharedfate.perk.PerkRallyShard} 가, 굳히고 끌어오는 일은
 * {@link com.sharedfate.sync.RallyShardManager} 가 한다.
 */
public final class RallyShardEffect implements PerkEffect {
	/** 끌려오기 전에 굳어 있는 기본 시간(초). */
	public static final int DEFAULT_FREEZE_SECONDS = 3;
	public static final int MIN_FREEZE_SECONDS = 1;
	/** 굳는 시간 상한(초). 이보다 길면 굳은 사람이 손쓸 새 없이 죽는다. */
	public static final int MAX_FREEZE_SECONDS = 10;

	/**
	 * 기본 쿨타임(초). 4분이다.
	 *
	 * <p>정의에 {@code cooldown_seconds} 를 적으면 그쪽이 이긴다. 값을 조절할 때는 <b>이 상수와
	 * {@code sharedfate-perks-default.json} 의 값이 어긋나지 않게</b> 같이 봐야 한다.
	 */
	public static final int DEFAULT_COOLDOWN_SECONDS = 240;
	public static final int MIN_COOLDOWN_SECONDS = 10;
	/** 쿨타임 상한(초). 20분이면 한 회차에 한 번꼴이라 그 위는 뜻이 없다. */
	public static final int MAX_COOLDOWN_SECONDS = 1200;

	private static final int TICKS_PER_SECOND = 20;

	/** 소집의 조각으로 쓰는 바닐라 아이템. 26.2 에 {@code assets/minecraft/items/echo_shard.json} 이 있다. */
	public static final Identifier ITEM = Identifier.withDefaultNamespace("echo_shard");

	/** {@code minecraft:custom_data} 에 남기는 표식의 키. 해시계와 같은 키를 값만 달리해 쓴다. */
	public static final String MARKER_KEY = "sharedfate_item";
	/** {@code minecraft:custom_data} 에 남기는 표식의 값. */
	public static final String MARKER_VALUE = "rally_shard";

	/** 아이템에 붙는 이름. 쿨타임 표시도 이 이름을 쓴다. */
	public static final String DISPLAY_NAME = "소집의 조각";

	/** 쿨타임 묶음. 평범한 메아리 조각과 갈라 두려고 모드 이름공간을 쓴다. */
	public static final Identifier COOLDOWN_GROUP = SharedFateMod.id("rally_shard");

	private final int freezeTicks;
	private final int cooldownTicks;

	/** 레지스트리에서 찾아 만든 견본. 처음 지급할 때 한 번 만들고 계속 쓴다. */
	private @Nullable ItemStack template;

	public RallyShardEffect(int freezeTicks, int cooldownTicks) {
		this.freezeTicks = freezeTicks;
		this.cooldownTicks = cooldownTicks;
	}

	/**
	 * 굳어 있는 동안 띄우는 파티클.
	 *
	 * <p>상수가 아니라 메서드인 것은 정의를 읽는 시점에 {@code ParticleTypes} 를 건드리지 않기
	 * 위해서다.
	 */
	public static SimpleParticleType particle() {
		return ParticleTypes.BUBBLE_POP;
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>값이 범위를 벗어나면 경고만 남기고 잘라 쓴다. 쿨타임이 틀렸다고 증강 전체를 버리면
	 * 손해가 더 크다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int freeze = clamp(perkId, "freeze_seconds",
				PerkEffectType.readInt(json, freezeKey(json), DEFAULT_FREEZE_SECONDS),
				MIN_FREEZE_SECONDS, MAX_FREEZE_SECONDS);
		int cooldown = clamp(perkId, "cooldown_seconds",
				PerkEffectType.readInt(json, cooldownKey(json), DEFAULT_COOLDOWN_SECONDS),
				MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS);
		return new RallyShardEffect(freeze * TICKS_PER_SECOND, cooldown * TICKS_PER_SECOND);
	}

	/** 카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다. */
	private static String freezeKey(JsonObject json) {
		return json != null && json.has("freezeSeconds") ? "freezeSeconds" : "freeze_seconds";
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
				"증강 {}: rally_shard 의 {} 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, key, min, max, cut, value);
		return cut;
	}

	/** 끌려오기 전에 굳어 있는 시간(틱). */
	public int freezeTicks() {
		return freezeTicks;
	}

	/** 쿨타임(틱). */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	/**
	 * 이번 선택으로 지급할 소집의 조각 하나.
	 *
	 * <p>부를 때마다 새 사본을 돌려준다. 받는 쪽이 개수를 깎으므로 견본을 그대로 넘기면 두 번째
	 * 지급 때 빈 묶음이 나간다.
	 *
	 * @return 조각 한 개. 아이템을 찾지 못했으면 null
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
				SharedFateMod.LOGGER.warn("소집의 조각으로 쓸 아이템을 찾을 수 없습니다: {}", ITEM);
				return null;
			}
			item = found.get().value();
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("소집의 조각으로 쓸 아이템 {} 을 찾다가 실패했습니다", ITEM, error);
			return null;
		}
		// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
		if (item == Items.AIR) {
			SharedFateMod.LOGGER.warn("소집의 조각으로 쓸 아이템을 찾을 수 없습니다: {}", ITEM);
			return null;
		}
		return decorate(new ItemStack(item, 1), cooldownTicks / (float) TICKS_PER_SECOND);
	}

	/**
	 * 메아리 조각 하나를 소집의 조각으로 만든다. 표식·이름·쿨타임 묶음을 붙인다.
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
	 * 이 묶음이 소집의 조각인가.
	 *
	 * <p>이름이 아니라 {@code minecraft:custom_data} 의 표식으로 판정한다.
	 */
	public static boolean isRallyShard(@Nullable ItemStack stack) {
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
