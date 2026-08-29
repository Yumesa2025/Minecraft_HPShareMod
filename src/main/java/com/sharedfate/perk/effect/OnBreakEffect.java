package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.BlockSelector;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 정해진 블록을 캤을 때 캔 팀원에게 효과를 잠깐 얹는다.
 *
 * <p>예: 실버 7 광맥 감각.
 *
 * <pre>{@code
 * {
 *   "type": "on_break",
 *   "blocks": ["#c:ores"],
 *   "durationSeconds": 3,
 *   "effects": [ { "type": "status_effect", "effect": "minecraft:haste", "amplifier": 0 } ]
 * }
 * }</pre>
 *
 * <h2>{@code effects} — 캔 순간에 잠깐 얹을 효과</h2>
 * <p>배열 각 항목은 보통의 효과 정의와 형태가 똑같고 {@link PerkEffectType} 이 재귀적으로
 * 읽는다. {@code on_kill} 과 같은 규칙이다.
 *
 * <p>{@code status_effect} 는 <b>반드시 유한 지속</b>으로 건다. 무한으로 걸면
 * {@link com.sharedfate.perk.PerkStatusEffects} 가 그것을 "증강이 상시로 걸어 둔 것"으로 보고
 * 팀 공유 대상에서 빼 버린다. 그러면 3초만 얹으려던 성급함이 팀에 퍼지지도 않고, 증강을 잃은
 * 뒤에도 걷히지 않은 채 남는다. {@link OnKillEffect} 가 같은 이유로 유한 지속을 쓴다.
 *
 * <p>지속시간은 하위 효과의 {@code duration}(초)이 우선이고, 없으면 이 효과의
 * {@code durationSeconds}, 그것도 없으면 {@link #DEFAULT_DURATION_SECONDS} 초다.
 *
 * <p>{@code status_effect} 가 아닌 하위 효과는 {@link PerkEffect#apply} 를 그대로 부른다.
 * 그런 효과들({@code attribute} 등)은 걷어낼 시점이 없어 영구히 붙어 버리므로 권하지 않는다.
 * 그래도 막지 않는 이유는 {@code on_kill} 과 같다. 새 효과 타입이 생겼을 때 여기를 고치지
 * 않아도 되게 하기 위해서다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>언제 누가 무엇을 캤는지 보는 일은 {@link com.sharedfate.perk.PerkBlockBreaks} 가 맡는다.
 */
public final class OnBreakEffect implements PerkEffect {
	/** 지속시간을 아무 데도 안 적었을 때 하위 효과가 유지되는 시간. */
	public static final double DEFAULT_DURATION_SECONDS = 5.0;
	/** 지속시간 상한. 이보다 길면 "잠깐"이 아니라 상시나 다름없다. */
	static final double MAX_DURATION_SECONDS = 600.0;
	/** 한 번에 얹을 수 있는 하위 효과 수 상한. */
	static final int MAX_GRANTS = 16;
	static final int TICKS_PER_SECOND = 20;

	/**
	 * 하위 효과의 순번을 부모와 겹치지 않게 밀어 두는 폭.
	 *
	 * <p>{@link OnKillEffect#NESTED_INDEX_STRIDE} 와 같은 규칙을 쓴다. 순번은
	 * {@link AttributeEffect} 가 수정자 이름을 만드는 데 쓰므로 한 증강 안에서 유일해야 한다.
	 * 두 타입이 같은 공식을 써도 부모 순번이 서로 다르므로 구간이 겹치지 않는다.
	 */
	static final int NESTED_INDEX_STRIDE = 1000;

	/**
	 * 캔 순간에 잠깐 얹을 하위 효과 하나.
	 *
	 * @param effect        재귀적으로 읽어 낸 효과
	 * @param durationTicks {@code status_effect} 일 때 유지할 시간. 그 외 타입에는 쓰이지 않는다
	 */
	public record Grant(PerkEffect effect, int durationTicks) {
	}

	private final BlockSelector blocks;
	private final List<Grant> grants;

	public OnBreakEffect(BlockSelector blocks, List<Grant> grants) {
		this.blocks = blocks == null ? BlockSelector.ALL : blocks;
		this.grants = List.copyOf(grants);
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		BlockSelector blocks = BlockSelector.fromJson(perkId, "on_break", json);
		if (blocks == null) {
			return null;
		}

		Integer defaultTicks = readSeconds(perkId, json, "durationSeconds", DEFAULT_DURATION_SECONDS);
		if (defaultTicks == null) {
			return null;
		}

		List<Grant> grants = readGrants(perkId, index, json, defaultTicks);
		if (grants == null) {
			return null;
		}
		if (grants.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: on_break 가 아무것도 주지 않습니다. effects 에 하나는 있어야 합니다", perkId);
			return null;
		}
		return new OnBreakEffect(blocks, grants);
	}

	/** 이 블록에 걸리는 효과인가. */
	public boolean appliesTo(BlockState state) {
		return blocks.matches(state);
	}

	/**
	 * 캔 팀원에게 하위 효과를 얹는다.
	 *
	 * <p>상태이상은 한 명에게만 걸어도 {@code EffectSync} 가 팀 전원에게 퍼뜨린다. 여기서
	 * 굳이 팀 전원을 돌지 않는 이유가 그것이다. 하위 효과 하나가 실패해도 나머지는 계속 얹는다.
	 */
	public void grantTemporaryEffects(@Nullable ServerPlayer breaker) {
		if (breaker == null || grants.isEmpty()) {
			return;
		}
		for (Grant grant : grants) {
			try {
				grantOne(breaker, grant);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("on_break 의 하위 효과를 얹지 못했습니다", error);
			}
		}
	}

	private static void grantOne(ServerPlayer breaker, Grant grant) {
		if (!(grant.effect() instanceof StatusEffectPerk status)) {
			// 붙였다 떼는 보통의 효과다. 그대로 적용한다.
			grant.effect().apply(breaker);
			return;
		}
		Holder<MobEffect> resolved = status.resolvedEffect();
		if (resolved == null) {
			return;
		}
		// 무한 지속이 아니라 정해진 시간만 걸어야 "캔 순간에 잠깐"이 된다.
		breaker.addEffect(new MobEffectInstance(
				resolved, grant.durationTicks(), status.amplifier(), false, false, true));
	}

	public BlockSelector blocks() {
		return blocks;
	}

	public List<Grant> grants() {
		return grants;
	}

	/**
	 * 하위 효과의 순번. {@code (부모 순번 + 1) * 1000 + 자식 순번} 이다.
	 *
	 * <p>최상위 효과 순번은 0, 1, 2 … 로 아주 작아서 1000 이상과 절대 겹치지 않고, 부모가 다르면
	 * 구간 자체가 달라진다.
	 */
	static int nestedIndex(int parentIndex, int childIndex) {
		long shifted = ((long) Math.max(0, parentIndex) + 1L) * NESTED_INDEX_STRIDE
				+ Math.max(0, childIndex);
		return (int) Math.min(Integer.MAX_VALUE, shifted);
	}

	/**
	 * {@code effects} 배열을 재귀적으로 읽는다.
	 *
	 * <p>하나라도 잘못됐으면 null 을 돌려주고, 그러면 이 증강 전체가 버려진다. 설명은 그대로인데
	 * 효과 일부만 빠진 증강은 플레이어를 속이는 셈이기 때문이다.
	 */
	private static @Nullable List<Grant> readGrants(String perkId, int index, JsonObject json,
			int defaultTicks) {
		JsonElement element = json.get("effects");
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: on_break 의 effects 가 배열이 아닙니다", perkId);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		if (array.size() > MAX_GRANTS) {
			SharedFateMod.LOGGER.warn("증강 {}: on_break 의 effects 가 너무 많습니다 ({}개)",
					perkId, array.size());
			return null;
		}

		List<Grant> grants = new ArrayList<>(array.size());
		for (int child = 0; child < array.size(); child++) {
			JsonElement raw = array.get(child);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: on_break 의 {}번째 하위 효과가 객체가 아닙니다",
						perkId, child);
				return null;
			}
			JsonObject childJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(childJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: on_break 하위 효과의 알 수 없는 type 입니다 ({})",
						perkId, typeId);
				return null;
			}
			PerkEffect effect = type.create(perkId, nestedIndex(index, child), childJson);
			if (effect == null) {
				return null;
			}
			Integer ticks = readSeconds(perkId, childJson, "duration", -1.0);
			if (ticks == null) {
				return null;
			}
			grants.add(new Grant(effect, ticks < 0 ? defaultTicks : ticks));
		}
		return grants;
	}

	/**
	 * 초 단위 필드를 틱으로 바꾼다.
	 *
	 * <p>필드를 아예 안 적었으면 {@code fallbackSeconds} 를 쓴다. 그 값이 음수면 "위에서
	 * 물려받으라"는 뜻이라 음수 틱을 그대로 돌려주고, 부르는 쪽이 기본값으로 채운다. 적었는데
	 * 숫자가 아니거나 범위를 벗어나면 null 이고, 그러면 이 효과를 버린다.
	 */
	private static @Nullable Integer readSeconds(String perkId, JsonObject json, String key,
			double fallbackSeconds) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return fallbackSeconds < 0.0 ? -1 : toTicks(fallbackSeconds);
		}
		Double seconds = PerkEffectType.readDouble(json, key);
		if (seconds == null || seconds <= 0.0 || seconds > MAX_DURATION_SECONDS) {
			SharedFateMod.LOGGER.warn("증강 {}: on_break 의 {} 값이 올바르지 않습니다 ({})",
					perkId, key, element);
			return null;
		}
		return toTicks(seconds);
	}

	private static int toTicks(double seconds) {
		return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
	}
}
