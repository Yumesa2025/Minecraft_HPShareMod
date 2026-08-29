package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.ConditionalPerkManager;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkHolderManager;
import com.sharedfate.perk.TimedPerkEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 팀원 <b>한 명</b>에게만 효과를 몰아 주는 래퍼.
 *
 * <p>지금까지의 증강은 전부 "효과가 팀 전체에 똑같이 적용된다"는 전제 위에 있었다. 이 타입은
 * 그 전제를 깬다. 무작위로 뽑힌 팀원 한 명(이하 <b>보유자</b>)이 {@code on_holder} 를 받고,
 * 나머지 팀원은 {@code on_others} 를 받는다. 두 묶음 중 하나만 언제나 붙어 있는 구조는
 * {@link ConditionalEffect} 의 {@code when_true}/{@code when_false} 와 완전히 같다.
 *
 * <pre>{@code
 * {
 *   "type": "holder",
 *   "rotate_ticks": 1200,
 *   "min_hold_ticks": 200,
 *   "pass_on_hurt": true,
 *   "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 },
 *                  { "type": "status_effect", "effect": "minecraft:haste", "amplifier": 0 } ],
 *   "on_others": [ ],
 *   "on_pass":   [ { "type": "status_effect", "effect": "minecraft:weakness",
 *                    "amplifier": 0, "duration": 5 } ]
 * }
 * }</pre>
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "누가 보유자인가에 따라 무엇을 붙이는가"만 아는 자료 그릇이다. "지금 누가
 * 보유자인가"와 "언제 누구에게 넘기는가"는 {@link PerkHolderManager} 가 정한다.
 * {@code on_kill} 과 {@code PerkKillRewards} 의 관계와 같은 구도다.
 *
 * <h2>보유자 상태는 저장되지 않는다</h2>
 * <p>보유자는 {@link PerkHolderManager} 의 런타임 메모리에만 있다. 팀 상태에 저장하면 세이브
 * 형식이 바뀌어 기존 월드와의 호환을 따져야 하는데, "지금 누가 버프를 들고 있는가"는 서버가
 * 다시 뜨면 새로 뽑아도 아무 문제가 없는 값이다. 그래서 저장하지 않는다.
 *
 * <h2>하위 효과의 순번</h2>
 * <p>속성 수정자 식별자가 {@code 증강id + 효과순번} 으로 만들어지므로
 * ({@link AttributeEffect#modifierId}) 하위 순번이 형제와 겹치면 서로를 덮어쓴다.
 * {@code on_holder}/{@code on_others} 는 {@link ConditionalEffect#childIndex} 를 그대로 쓰고
 * ({@code on_others} 는 50 부터 센다), {@code on_pass} 는 그 구간과 부딪히지 않도록
 * {@link OnKillEffect#nestedIndex} 쪽 구간을 쓴다. {@code TemporaryPerkGrants} 가 같은 방식으로
 * {@code conditional} 과 한 증강 안에 공존한다.
 *
 * <h2>최상위에만 놓을 수 있다</h2>
 * <p>{@link PerkHolderManager} 는 증강의 최상위 효과만 훑으므로, 다른 효과의 하위로 들어간
 * {@code holder} 는 아무도 순환시켜 주지 않아 보유자가 영영 정해지지 않는다.
 * {@link PeriodicEffect} 가 같은 이유로 최상위만 허용한다.
 */
public final class HolderEffect implements PerkEffect {
	/** 순환 주기 상한. 한 시간이면 어떤 증강이라도 충분하고, 실수로 적은 큰 값을 걸러 준다. */
	public static final int MAX_ROTATE_TICKS = 72_000;

	/** 최소 유지 시간 상한. 순환 주기와 같은 기준을 쓴다. */
	public static final int MAX_MIN_HOLD_TICKS = 72_000;

	/** 한 묶음에 담을 수 있는 하위 효과 수. {@link ConditionalEffect} 의 순번 규칙과 같은 한계다. */
	private static final int MAX_BRANCH_EFFECTS = 50;

	/** {@code on_others} 쪽 하위 순번에 더하는 값. {@code when_false} 와 같은 50 이다. */
	private static final int OTHERS_BRANCH_OFFSET = 50;

	/** {@code on_pass} 에 적을 수 있는 효과 수. 잠깐 거는 것이라 많을 이유가 없다. */
	private static final int MAX_PASS_EFFECTS = 8;

	/** {@code on_pass} 하위 효과가 {@code duration} 을 적지 않았을 때의 지속시간(초). */
	public static final double DEFAULT_PASS_DURATION_SECONDS = 5.0;

	/** {@code on_pass} 지속시간 상한(초). 이보다 길면 "잠깐"이 아니라 상시나 다름없다. */
	private static final double MAX_PASS_DURATION_SECONDS = 600.0;

	public static final int TICKS_PER_SECOND = 20;

	/** {@code holder} 는 최상위에만 놓을 수 있다. 최상위 순번은 언제나 이 값보다 작다. */
	private static final int TOP_LEVEL_INDEX_LIMIT = 100;

	private final int rotateTicks;
	private final int minHoldTicks;
	private final boolean passOnHurt;
	private final List<PerkEffect> onHolder;
	private final List<PerkEffect> onOthers;
	private final List<OnKillEffect.Grant> onPass;

	public HolderEffect(int rotateTicks, int minHoldTicks, boolean passOnHurt,
			List<PerkEffect> onHolder, List<PerkEffect> onOthers, List<OnKillEffect.Grant> onPass) {
		this.rotateTicks = Math.max(0, rotateTicks);
		this.minHoldTicks = Math.max(0, minHoldTicks);
		this.passOnHurt = passOnHurt;
		this.onHolder = List.copyOf(onHolder);
		this.onOthers = List.copyOf(onOthers);
		this.onPass = List.copyOf(onPass);
	}

	// ------------------------------------------------------------------ 읽기

	/**
	 * JSON 에서 만든다. 정의가 잘못됐으면 경고를 남기고 null.
	 *
	 * <p>하위 효과가 하나라도 잘못됐으면 이 효과 전체를 버린다. 절반만 살아남으면 설명과 다르게
	 * 동작해 플레이어를 속이게 되기 때문이다. 버리면 증강 자체가 풀에서 빠진다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		if (index < 0 || index >= TOP_LEVEL_INDEX_LIMIT) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: holder 효과는 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}

		int rotateTicks = PerkEffectType.readInt(json, "rotate_ticks", 0);
		if (rotateTicks < 0 || rotateTicks > MAX_ROTATE_TICKS) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 rotate_ticks 가 범위를 벗어났습니다 ({})",
					perkId, rotateTicks);
			return null;
		}
		int minHoldTicks = PerkEffectType.readInt(json, "min_hold_ticks", 0);
		if (minHoldTicks < 0 || minHoldTicks > MAX_MIN_HOLD_TICKS) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 min_hold_ticks 가 범위를 벗어났습니다 ({})",
					perkId, minHoldTicks);
			return null;
		}

		Boolean passOnHurt = readBoolean(perkId, json, "pass_on_hurt");
		if (passOnHurt == null) {
			return null;
		}

		List<PerkEffect> onHolder = parseBranch(perkId, index, json, "on_holder", 0);
		List<PerkEffect> onOthers = parseBranch(perkId, index, json, "on_others", OTHERS_BRANCH_OFFSET);
		if (onHolder == null || onOthers == null) {
			return null;
		}
		if (onHolder.isEmpty() && onOthers.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: holder 에 on_holder 도 on_others 도 없습니다", perkId);
			return null;
		}

		List<OnKillEffect.Grant> onPass = parsePass(perkId, index, json);
		if (onPass == null) {
			return null;
		}
		if (!onPass.isEmpty() && !passOnHurt && rotateTicks == 0) {
			// 넘어갈 일이 없는데 넘길 때 걸 효과만 적어 둔 정의다. 조용히 죽어 있는 것보다
			// 여기서 걸러 내는 편이 낫다.
			SharedFateMod.LOGGER.warn(
					"증강 {}: holder 에 on_pass 를 적었지만 보유자가 넘어갈 길이 없습니다 "
							+ "(rotate_ticks 0, pass_on_hurt 거짓)", perkId);
			return null;
		}

		return new HolderEffect(rotateTicks, minHoldTicks, passOnHurt, onHolder, onOthers, onPass);
	}

	/**
	 * 한 묶음을 읽는다. 필드가 없으면 빈 목록, 하나라도 잘못됐으면 null.
	 *
	 * <p>{@link ConditionalEffect} 가 {@code when_true} 를 읽는 규칙과 같다. 다만 {@code holder} 안에 또
	 * {@code holder} 를 넣는 것만은 막는다. 안쪽 보유자는 아무도 뽑아 주지 않는다.
	 *
	 * @param branchOffset 이 묶음의 하위 순번에 더할 값
	 */
	private static @Nullable List<PerkEffect> parseBranch(String perkId, int index, JsonObject json,
			String key, int branchOffset) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 {} 가 배열이 아닙니다", perkId, key);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		if (array.size() > MAX_BRANCH_EFFECTS) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 {} 에 효과가 너무 많습니다 ({} > {})",
					perkId, key, array.size(), MAX_BRANCH_EFFECTS);
			return null;
		}

		List<PerkEffect> effects = new ArrayList<>(array.size());
		for (int i = 0; i < array.size(); i++) {
			JsonElement raw = array.get(i);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 의 {} 중 {}번째가 객체가 아닙니다",
						perkId, key, i);
				return null;
			}
			JsonObject childJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(childJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 의 {} 에 알 수 없는 효과 type 입니다 ({})",
						perkId, key, typeId);
				return null;
			}
			if (type == PerkEffectType.HOLDER) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 안에 holder 를 넣을 수 없습니다", perkId);
				return null;
			}
			PerkEffect child = type.create(
					perkId, ConditionalEffect.childIndex(index, branchOffset + i), childJson);
			if (child == null) {
				return null;
			}
			effects.add(child);
		}
		return effects;
	}

	/**
	 * {@code on_pass} 를 읽는다. 필드가 없으면 빈 목록, 하나라도 잘못됐으면 null.
	 *
	 * <p>{@link OnKillEffect} 의 {@code effects} 와 같은 형태다. 각 항목은 보통의 효과 정의에
	 * {@code duration}(초)을 덧붙인 것이고, 적지 않으면 {@link #DEFAULT_PASS_DURATION_SECONDS} 초다.
	 */
	private static @Nullable List<OnKillEffect.Grant> parsePass(String perkId, int index,
			JsonObject json) {
		JsonElement element = json.get("on_pass");
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 on_pass 가 배열이 아닙니다", perkId);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		if (array.size() > MAX_PASS_EFFECTS) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 on_pass 에 효과가 너무 많습니다 ({} > {})",
					perkId, array.size(), MAX_PASS_EFFECTS);
			return null;
		}

		List<OnKillEffect.Grant> grants = new ArrayList<>(array.size());
		for (int child = 0; child < array.size(); child++) {
			JsonElement raw = array.get(child);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 의 on_pass 중 {}번째가 객체가 아닙니다",
						perkId, child);
				return null;
			}
			JsonObject childJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(childJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 의 on_pass 에 알 수 없는 효과 type 입니다 ({})",
						perkId, typeId);
				return null;
			}
			if (type == PerkEffectType.HOLDER) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 안에 holder 를 넣을 수 없습니다", perkId);
				return null;
			}
			PerkEffect effect = type.create(
					perkId, OnKillEffect.nestedIndex(index, child), childJson);
			if (effect == null) {
				return null;
			}
			Integer duration = readPassDurationTicks(perkId, childJson);
			if (duration == null) {
				return null;
			}
			grants.add(new OnKillEffect.Grant(effect, duration));
		}
		return grants;
	}

	/** {@code on_pass} 하위 효과의 {@code duration}(초)을 틱으로 바꾼다. 범위를 벗어나면 null. */
	private static @Nullable Integer readPassDurationTicks(String perkId, JsonObject json) {
		Double seconds = PerkEffectType.readDouble(json, "duration");
		if (seconds == null) {
			if (json.has("duration") && !json.get("duration").isJsonNull()) {
				SharedFateMod.LOGGER.warn("증강 {}: holder 의 on_pass 하위 효과 duration 이 숫자가 아닙니다",
						perkId);
				return null;
			}
			seconds = DEFAULT_PASS_DURATION_SECONDS;
		}
		if (seconds <= 0.0 || seconds > MAX_PASS_DURATION_SECONDS) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 on_pass 하위 효과 duration 이 범위를 벗어났습니다 ({})",
					perkId, seconds);
			return null;
		}
		return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
	}

	/** 참·거짓 필드. 없으면 거짓, 적었는데 참·거짓이 아니면 null. */
	private static @Nullable Boolean readBoolean(String perkId, JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return Boolean.FALSE;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			SharedFateMod.LOGGER.warn("증강 {}: holder 의 {} 가 참·거짓이 아닙니다 ({})", perkId, key, element);
			return null;
		}
		return element.getAsBoolean();
	}

	// ------------------------------------------------------------------ 적용

	/**
	 * 지금 있어야 할 모습으로 맞춘다.
	 *
	 * <p>접속·부활 직후처럼 수정자가 통째로 날아간 자리에서 불리므로, 기억해 둔 것과 관계없이
	 * {@link PerkHolderManager} 에 지금 보유자가 누구인지 다시 물어 맞춘다. 아직 보유자가 정해지지
	 * 않았으면 {@code on_others} 를 붙인다. 곧 {@link PerkHolderManager#tick} 이 보유자를 뽑아
	 * 그 사람만 {@code on_holder} 로 갈아 끼운다.
	 */
	@Override
	public void apply(ServerPlayer player) {
		if (player == null) {
			return;
		}
		applyAs(player, PerkHolderManager.isHolder(this, player.getUUID()));
	}

	/**
	 * 이 사람을 보유자/비보유자로 맞춘다.
	 *
	 * <p><b>지는 쪽을 먼저 떼고 이기는 쪽을 붙인다.</b> 두 묶음이 같은 속성이나 같은 상태이상을
	 * 건드려도 이 순서면 안전하다. 이것을 뒤집으면 갓 붙인 수정자를 곧바로 떼어 내는 일이 생긴다.
	 * {@link ConditionalEffect} 의 {@code switchTo} 와 같은 규칙이다.
	 */
	public void applyAs(@Nullable ServerPlayer player, boolean holding) {
		if (player == null) {
			return;
		}
		removeAll(holding ? onOthers : onHolder, player);
		applyAll(holding ? onHolder : onOthers, player);
	}

	/**
	 * 두 묶음을 모두 걷어내고 {@code on_pass} 로 걸어 둔 것도 취소한다.
	 *
	 * <p>어느 쪽이 붙어 있었는지 몰라도 안전하다. 하나라도 남기면 속성 수정자가 영구히 붙어
	 * 팀이 망가진다.
	 */
	@Override
	public void remove(ServerPlayer player) {
		if (player == null) {
			return;
		}
		removeAll(onHolder, player);
		removeAll(onOthers, player);
		for (OnKillEffect.Grant grant : onPass) {
			revokePass(player, grant);
		}
	}

	/**
	 * 버프가 넘어가는 순간 <b>직전 보유자에게만</b> 잠깐 거는 효과들을 얹는다.
	 *
	 * <p>{@link #applyAs} 로 묶음을 갈아 끼운 <b>뒤에</b> 불러야 한다. 먼저 얹으면 갈아 끼우는
	 * 과정의 {@code remove} 가 방금 얹은 것을 도로 걷어낼 수 있다.
	 */
	public void grantPassEffects(@Nullable ServerPlayer previous) {
		if (previous == null || onPass.isEmpty()) {
			return;
		}
		for (OnKillEffect.Grant grant : onPass) {
			try {
				grantPass(previous, grant);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("holder 의 on_pass 효과를 얹지 못했습니다", error);
			}
		}
	}

	private static void grantPass(ServerPlayer player, OnKillEffect.Grant grant) {
		if (grant.effect() instanceof StatusEffectPerk status) {
			Holder<MobEffect> resolved = status.resolvedEffect();
			if (resolved == null) {
				return;
			}
			// 무한이 아니라 정해진 시간만 걸어야 PerkStatusEffects 가 증강분으로 오해하지 않는다.
			player.addEffect(new MobEffectInstance(
					resolved, grant.durationTicks(), status.amplifier(), false, false, true));
			return;
		}
		// 속성처럼 스스로 만료되지 않는 효과다. 걷어낼 시점을 예약해 둔다.
		TimedPerkEffects.grant(player, grant.effect(), grant.durationTicks());
	}

	private static void revokePass(ServerPlayer player, OnKillEffect.Grant grant) {
		if (grant.effect() instanceof StatusEffectPerk status) {
			Holder<MobEffect> resolved = status.resolvedEffect();
			if (resolved != null) {
				player.removeEffect(resolved);
			}
			return;
		}
		TimedPerkEffects.cancel(player, grant.effect());
	}

	private static void applyAll(List<PerkEffect> effects, ServerPlayer player) {
		for (PerkEffect effect : effects) {
			try {
				effect.apply(player);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("holder 증강의 하위 효과를 적용하지 못했습니다", error);
			}
		}
	}

	private static void removeAll(List<PerkEffect> effects, ServerPlayer player) {
		for (PerkEffect effect : effects) {
			try {
				effect.remove(player);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("holder 증강의 하위 효과를 걷어내지 못했습니다", error);
			}
		}
	}

	// ------------------------------------------------------------------ 피해 배율

	/**
	 * 지금 이 배율을 묻는 사람이 보유자인지 보고 그 묶음의 배율만 돌려준다.
	 *
	 * <p>이 메서드에는 플레이어 인자가 없다. 그래서 누구를 위한 조회인지는
	 * {@link ConditionalPerkManager#multiplierContext()} 로 알아낸다. 배율을 모으는 자리
	 * ({@code PerkManager.multiplier})가 조회를 시작하면서 대상 플레이어를 적어 두기 때문에,
	 * 그 UUID 가 보유자인지 {@link PerkHolderManager} 에 물으면 된다.
	 *
	 * <p>대상을 알 수 없으면 <b>1.0 을 돌려준다.</b> {@link ConditionalEffect} 는 이때 기억해 둔
	 * 판정으로 물러서지만 여기서는 그럴 수 없다. 조건부 효과는 팀원끼리 판정이 언제나 같아서
	 * 짐작이 통하지만, 보유자는 정의상 팀에 한 명뿐이라 짐작하면 팀 전원이 보유자 배율을 받아
	 * 증강이 통째로 망가진다. 모르면 관여하지 않는 편이 안전하다.
	 */
	@Override
	public double damageDealtMultiplier() {
		Boolean holding = resolveHolding();
		return holding == null ? 1.0 : damageDealtMultiplier(holding);
	}

	@Override
	public double damageTakenMultiplier() {
		Boolean holding = resolveHolding();
		return holding == null ? 1.0 : damageTakenMultiplier(holding);
	}

	/** 보유자 여부를 직접 주고 구하는 주는 피해 배율. */
	public double damageDealtMultiplier(boolean holding) {
		return branchMultiplier(holding ? onHolder : onOthers, true);
	}

	/** 보유자 여부를 직접 주고 구하는 받는 피해 배율. */
	public double damageTakenMultiplier(boolean holding) {
		return branchMultiplier(holding ? onHolder : onOthers, false);
	}

	/** 지금 조회의 대상이 보유자인지. 대상을 알 수 없으면 null. */
	private @Nullable Boolean resolveHolding() {
		UUID target = ConditionalPerkManager.multiplierContext();
		return target == null ? null : PerkHolderManager.isHolder(this, target);
	}

	private static double branchMultiplier(List<PerkEffect> effects, boolean dealt) {
		double total = 1.0;
		for (PerkEffect effect : effects) {
			try {
				total *= dealt
						? effect.damageDealtMultiplier()
						: effect.damageTakenMultiplier();
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("holder 증강의 하위 피해 배율을 구하지 못했습니다", error);
				return 1.0;
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	// ------------------------------------------------------------------ 조회

	/** 0 이면 시간으로는 바뀌지 않는다. 접속 종료·사망 같은 사건으로만 넘어간다. */
	public int rotateTicks() {
		return rotateTicks;
	}

	/** 이 시간 안에는 {@code pass_on_hurt} 로 넘어가지 않는다. */
	public int minHoldTicks() {
		return minHoldTicks;
	}

	/** 보유자가 피해를 받으면 넘기는가. */
	public boolean passOnHurt() {
		return passOnHurt;
	}

	public List<PerkEffect> onHolder() {
		return onHolder;
	}

	public List<PerkEffect> onOthers() {
		return onOthers;
	}

	public List<OnKillEffect.Grant> onPass() {
		return onPass;
	}

	/**
	 * 상시로 붙었다 떼는 두 묶음의 하위 효과 전부.
	 *
	 * <p>지금 누가 보유자든 둘 다 이 증강이 거는 효과라, 하위 효과까지 훑어야 하는 곳
	 * ({@code PerkStatusEffects} 처럼)에서는 양쪽을 모두 봐야 한다.
	 *
	 * <p>{@code on_pass} 는 넣지 않는다. 그쪽은 언제나 유한 지속으로 걸리므로
	 * {@code PerkStatusEffects} 의 "무한 지속이어야 증강분" 판정에 애초에 걸리지 않는다.
	 */
	public List<PerkEffect> children() {
		if (onOthers.isEmpty()) {
			return onHolder;
		}
		if (onHolder.isEmpty()) {
			return onOthers;
		}
		List<PerkEffect> all = new ArrayList<>(onHolder.size() + onOthers.size());
		all.addAll(onHolder);
		all.addAll(onOthers);
		return List.copyOf(all);
	}
}
