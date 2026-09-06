package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.ConditionalPerkManager;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkItemMatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 우대하는 도구가 아닌 <b>다른 도구·무기</b>를 주 손에 들고 있는 동안 이동 속도를 깎는다.
 *
 * <pre>{@code
 * { "type": "tool_mismatch_slow", "multiplier": -0.3 }
 * }</pre>
 *
 * <p>{@code multiplier} 는 {@code minecraft:movement_speed} 에
 * {@code add_multiplied_total} 로 얹는 값이다. {@code -0.3} 이면 30% 느려진다. 골드
 * 「곡괭이 전사」가 쓴다 — 곡괭이로 싸우면 강해지는 대신, 검이나 도끼를 꺼내 드는 순간 발이
 * 무거워진다.
 *
 * <p>우대할 도구는 {@code tags}/{@code items} 로 바꿀 수 있고, 안 적으면
 * {@link #DEFAULT_PREFERRED_TAG}(곡괭이 전부)다. {@code weapon_damage} 와 같은 방식으로 적는다.
 *
 * <h2>"도구"의 범위</h2>
 * <p>깎이는 것은 <b>무기와 도구를 들었을 때뿐</b>이다. 블록·음식·재료 같은 그 밖의 아이템은
 * 아무 영향이 없다 — 흙을 한 스택 들었다고 느려지면 이 대가는 "곡괭이를 들어라"가 아니라
 * "손을 비워라"가 되어 버린다. 판정은 아이템 태그로 하고, 태그가 없는 한 자루짜리 무기만
 * 이름으로 적는다.
 *
 * <ul>
 *   <li>{@link #TOOL_TAGS} — {@code minecraft:swords} {@code axes} {@code shovels}
 *       {@code hoes} {@code pickaxes} {@code spears}. 여섯 개 모두 26.2 의
 *       {@code data/minecraft/tags/item/} 에 실제로 들어 있는 바닐라 태그다
 *       ({@code spears} 는 26.2 에서 새로 생긴 창 계열이다).</li>
 *   <li>{@link #TOOL_ITEMS} — 활·석궁·삼지창·철퇴. 이 넷은 종류마다 아이템이 하나뿐이라
 *       바닐라에 묶음 태그가 없다({@code #minecraft:enchantable/bow} 처럼 부여 대상 태그는
 *       있지만 그건 "이 마법을 걸 수 있다"는 뜻이라 여기 쓰기에는 의미가 어긋난다).</li>
 * </ul>
 *
 * <p>곡괭이도 {@link #TOOL_TAGS} 에 들어 있다. 우대 대상이면 깎지 않으므로 결과는 같지만,
 * "도구 목록"이 도구를 하나 빼먹은 목록이 되지 않아야 우대 대상을 바꿔도 그대로 성립한다.
 *
 * <h2>주 손만 본다</h2>
 * <p>왼손은 보지 않는다. 방패나 횃불을 왼손에 들고 다니는 것이 워낙 흔해서 왼손까지 세면 거의
 * 항상 느려지는 증강이 되고, 무엇 때문에 느린지도 알기 어렵다. "무엇을 꺼내 들었는가"는 주
 * 손이 답하는 물음이다.
 *
 * <h2>매 틱 바뀌는 조건이므로 다시 본다</h2>
 * <p>손에 든 것은 수시로 바뀌므로 {@link #apply} 한 번으로 끝나지 않는다.
 * {@link ConditionalPerkManager}가 주기적으로 {@link #refresh}를 불러 준다. 판정이 지난번과
 * 같으면 아무 일도 하지 않는다 — {@link ConditionalEffect}가 조건을 다시 볼 때와 같은 규칙이다.
 * 수정자를 뗐다 붙이면 속성 갱신 꾸러미가 매번 나간다.
 */
public final class ToolMismatchSlowEffect implements PerkEffect {
	/** 속도를 깎는 폭의 하한. {@code -1.0} 이하면 발이 아예 묶여 버린다. */
	static final double MIN_MULTIPLIER = -0.95;

	/** 속도를 깎는 폭의 상한. 이 타입은 느리게 하는 데만 쓴다. */
	static final double MAX_MULTIPLIER = -0.001;

	/**
	 * 도구·무기로 치는 아이템 태그. 26.2 에 실재하는 바닐라 태그다.
	 *
	 * <p>앞머리 {@code #} 없이 적는다. {@link PerkItemMatcher} 는 둘 다 받아 주지만 여기서는
	 * 이름만 담아 두는 목록이라 한 가지 모양으로 통일한다.
	 */
	public static final List<String> TOOL_TAGS = List.of(
			"minecraft:swords",
			"minecraft:axes",
			"minecraft:shovels",
			"minecraft:hoes",
			"minecraft:pickaxes",
			"minecraft:spears");

	/** 묶음 태그가 없는 한 자루짜리 무기들. */
	public static final List<String> TOOL_ITEMS = List.of(
			"minecraft:bow",
			"minecraft:crossbow",
			"minecraft:trident",
			"minecraft:mace");

	/** {@code tags}/{@code items} 를 안 적었을 때 우대할 도구. */
	public static final String DEFAULT_PREFERRED_TAG = "minecraft:pickaxes";

	/** 도구·무기 전체를 가리키는 고정 묶음. 정의마다 다시 만들 이유가 없다. */
	private static final PerkItemMatcher TOOLS = buildTools();

	private final double multiplier;
	private final PerkItemMatcher preferred;
	private final Identifier modifierId;

	/**
	 * 플레이어별로 지금 깎아 둔 상태인지.
	 *
	 * <p>{@link #refresh} 가 "바뀌지 않았으면 아무것도 하지 않는다"를 지키려면 직전 판정을
	 * 기억해야 한다. {@link ConditionalEffect} 와 같은 장치다.
	 */
	private final Map<UUID, Boolean> applied = new ConcurrentHashMap<>();

	public ToolMismatchSlowEffect(double multiplier, PerkItemMatcher preferred, Identifier modifierId) {
		this.multiplier = multiplier;
		this.preferred = preferred;
		this.modifierId = modifierId;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: tool_mismatch_slow 의 multiplier 가 없거나 {}~{} 범위를 벗어났습니다 ({}). "
							+ "이 타입은 느리게 하는 데만 씁니다",
					perkId, MIN_MULTIPLIER, MAX_MULTIPLIER, multiplier);
			return null;
		}

		PerkItemMatcher preferred = readPreferred(perkId, json);
		if (preferred == null) {
			return null;
		}
		return new ToolMismatchSlowEffect(
				multiplier, preferred, AttributeEffect.modifierId(perkId, index));
	}

	/** 우대 도구 묶음을 읽는다. 안 적었으면 곡괭이 전부. */
	private static @Nullable PerkItemMatcher readPreferred(String perkId, JsonObject json) {
		if (json != null && (json.has("tags") || json.has("items"))) {
			return PerkItemMatcher.fromJson(perkId, "tool_mismatch_slow", json);
		}
		JsonObject synthetic = new JsonObject();
		JsonArray tags = new JsonArray();
		tags.add(DEFAULT_PREFERRED_TAG);
		synthetic.add("tags", tags);
		return PerkItemMatcher.fromJson(perkId, "tool_mismatch_slow", synthetic);
	}

	private static PerkItemMatcher buildTools() {
		JsonObject synthetic = new JsonObject();
		JsonArray tags = new JsonArray();
		for (String tag : TOOL_TAGS) {
			tags.add(tag);
		}
		JsonArray items = new JsonArray();
		for (String item : TOOL_ITEMS) {
			items.add(item);
		}
		synthetic.add("tags", tags);
		synthetic.add("items", items);
		PerkItemMatcher matcher =
				PerkItemMatcher.fromJson("sharedfate:tool_mismatch_slow", "tool_mismatch_slow", synthetic);
		if (matcher == null) {
			// 위 두 목록은 상수라 여기 올 수 없다. 그래도 확인하는 이유는, 나중에 목록을 고치다
			// 비워 버리면 "아무 도구도 도구가 아니다"가 되어 증강이 조용히 죽기 때문이다.
			throw new IllegalStateException("tool_mismatch_slow 의 도구 목록이 비어 있습니다");
		}
		return matcher;
	}

	// ------------------------------------------------------------------ 판정

	/** 이 아이템이 도구·무기인가. 태그가 아직 안 묶였으면 이름으로 적은 넷만 걸린다. */
	public static boolean isTool(@Nullable ItemStack stack) {
		return TOOLS.matches(stack);
	}

	/** 이 아이템이 우대 대상(기본값은 곡괭이)인가. */
	public boolean isPreferred(@Nullable ItemStack stack) {
		return preferred.matches(stack);
	}

	/**
	 * 지금 주 손에 든 것 때문에 느려져야 하는가.
	 *
	 * <p>빈 손, 블록, 음식, 재료는 모두 거짓이다. 도구·무기이면서 우대 대상이 아닐 때만 참이다.
	 */
	public boolean mismatched(@Nullable ItemStack mainHand) {
		if (mainHand == null || mainHand.isEmpty()) {
			return false;
		}
		return mismatched(isTool(mainHand), isPreferred(mainHand));
	}

	/**
	 * 판정 규칙만 떼어 놓은 것.
	 *
	 * <p>아이템 태그는 데이터팩이 올라와야 묶이므로 단위 시험에서는 태그가 걸리지 않는다. 그래서
	 * "무엇이 도구인가"와 "그래서 느려지는가"를 나눠 두고, 뒤쪽 규칙은 이 자리에서 따로 확인한다.
	 */
	public static boolean mismatched(boolean tool, boolean preferredTool) {
		return tool && !preferredTool;
	}

	// ------------------------------------------------------------------ 적용

	/**
	 * 지금 손에 든 것을 보고 수정자를 맞춘다.
	 *
	 * <p>기억해 둔 판정과 관계없이 무조건 다시 맞춘다. 접속이나 부활 직후처럼 수정자가 통째로
	 * 날아간 상태에서도 불리기 때문이다.
	 */
	@Override
	public void apply(ServerPlayer player) {
		if (player == null) {
			return;
		}
		boolean slow = mismatched(player.getMainHandItem());
		switchTo(player, slow);
		applied.put(player.getUUID(), slow);
	}

	/** 수정자를 걷어낸다. 붙어 있지 않아도 안전하다. */
	@Override
	public void remove(ServerPlayer player) {
		if (player == null) {
			return;
		}
		switchTo(player, false);
		applied.remove(player.getUUID());
	}

	/**
	 * 손에 든 것을 다시 보고, 지난번과 달라졌을 때만 수정자를 갈아 끼운다.
	 *
	 * <p>{@link ConditionalPerkManager} 가 주기적으로 부른다.
	 *
	 * @return 실제로 갈아 끼웠으면 true
	 */
	public boolean refresh(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		boolean slow = mismatched(player.getMainHandItem());
		Boolean previous = applied.get(player.getUUID());
		if (previous != null && previous == slow) {
			return false;
		}
		switchTo(player, slow);
		applied.put(player.getUUID(), slow);
		return true;
	}

	/** 이 플레이어에게 기억해 둔 판정. 아직 적용한 적이 없으면 null. */
	public @Nullable Boolean appliedState(@Nullable UUID playerId) {
		return playerId == null ? null : applied.get(playerId);
	}

	/** 기억해 둔 판정을 모두 버린다. 서버가 멈출 때 다음 회차로 새어나가지 않게 한다. */
	public void forgetAll() {
		applied.clear();
	}

	private void switchTo(ServerPlayer player, boolean slow) {
		AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifierId);
		if (slow) {
			instance.addTransientModifier(new AttributeModifier(
					modifierId, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	// ------------------------------------------------------------------ 조회

	public double multiplier() {
		return multiplier;
	}

	public PerkItemMatcher preferred() {
		return preferred;
	}

	/** 도구·무기 전체를 가리키는 묶음. 시험용이다. */
	public static PerkItemMatcher tools() {
		return TOOLS;
	}

	public Identifier modifierId() {
		return modifierId;
	}
}
