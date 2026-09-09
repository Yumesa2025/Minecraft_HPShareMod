package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkBlessingSet;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 몹에게 받는 피해를 일정 시간마다 한 번 통째로 막아 주는 「호위」.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "damage_ward", "cooldown_seconds": 10 }
 * </pre>
 *
 * <p>{@code cooldown_seconds} 는 생략할 수 있고 생략하면 {@link #DEFAULT_COOLDOWN_SECONDS} 다.
 * {@link #MIN_COOLDOWN_SECONDS}~{@link #MAX_COOLDOWN_SECONDS} 를 벗어난 값은 정의를 버리지 않고
 * 경고만 남긴 뒤 범위 안으로 자른다. 쿨타임 숫자 하나가 틀렸다고 증강이 통째로 사라지면 손해가
 * 더 크기 때문이다 — {@link RallyShardEffect} 와 같은 규칙이다.
 *
 * <h2>고른 사람 한 명만 막는다</h2>
 * <p>이 증강은 팀 전체가 아니라 <b>이 증강을 고른 사람</b>에게만 걸린다. 「누가 골랐는가」는
 * {@code TeamState.perkOwners}(증강 id → 고른 사람의 UUID)에 회차 내내 남아 있으므로 그것만
 * 보면 된다. 그래서 {@link #wardFor} 는 팀의 보유 증강을 훑되 <b>주인이 물어본 사람과 같은
 * 증강만</b> 센다.
 *
 * <p>같은 이유로 <b>켜진 세트 효과는 보지 않는다.</b> 세트는 팀이 조건을 채워 얻는 것이라 고른
 * 사람이라는 개념이 없다. 세트 효과를 여기에 섞으면 "아무도 고르지 않았는데 아무도 못 쓰는"
 * 효과가 되거나, 반대로 팀 전원이 쓰게 되어 이 증강의 약속이 깨진다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 값과 대상 판정만 들고 있는 자료 그릇이다. 실제로 피해를 버리는 자리는
 * {@code LivingEntityPerkDamageMixin} 이 {@code hurtServer} 진입점에서 부르는
 * {@link com.sharedfate.perk.PerkDamage#blocksMobDamage} 이고, 사람마다 마지막으로 막은 시각을
 * 기억하는 일은 {@link com.sharedfate.perk.DamageWardTracker} 가 한다.
 *
 * <p>배율을 0 으로 만드는 길({@code damage_taken_from} + {@code multiplier: 0})로는 이 효과를
 * 만들 수 없다. 배율은 조건 없이 언제나 걸리는데 이 효과는 <b>쿨타임이 찼을 때 한 번만</b>
 * 걸려야 하고, 피해량 0 으로 들어간 피해도 바닐라 입장에서는 피해라서 피격 소리와 무적시간이
 * 생긴다. 진입점에서 통째로 버리면 그런 흔적이 남지 않는다 — {@link ShieldFallImmunityEffect}
 * 와 같은 판단이다.
 */
public final class DamageWardEffect implements PerkEffect {
	/** 기본 쿨타임(초). */
	public static final int DEFAULT_COOLDOWN_SECONDS = 10;
	/** 쿨타임 하한(초). 0 을 허용하면 몹 피해에 완전 면역이 된다. */
	public static final int MIN_COOLDOWN_SECONDS = 1;
	/** 쿨타임 상한(초). 2분이면 한 번 막고 나면 사실상 다시 안 도는 수준이라 그 위는 뜻이 없다. */
	public static final int MAX_COOLDOWN_SECONDS = 120;

	private static final int TICKS_PER_SECOND = 20;

	private final int cooldownTicks;
	private final int amplifiedCooldownTicks;

	public DamageWardEffect(int cooldownTicks) {
		this(cooldownTicks, cooldownTicks);
	}

	/**
	 * @param amplifiedCooldownTicks 「가호 3」이 켜졌을 때의 쿨타임. 안 적으면 평소와 같다
	 */
	public DamageWardEffect(int cooldownTicks, int amplifiedCooldownTicks) {
		this.cooldownTicks = cooldownTicks;
		this.amplifiedCooldownTicks = amplifiedCooldownTicks;
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>값이 범위를 벗어나면 경고만 남기고 잘라 쓴다. 읽을 값이 하나뿐이라 실패로 끝나는 길이
	 * 없고, 언제나 쓸 수 있는 효과가 나온다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int seconds = clamp(perkId,
				PerkEffectType.readInt(json, cooldownKey(json), DEFAULT_COOLDOWN_SECONDS));
		int amplified = clamp(perkId,
				PerkEffectType.readInt(json, amplifiedKey(json), seconds));
		return new DamageWardEffect(seconds * TICKS_PER_SECOND, amplified * TICKS_PER_SECOND);
	}

	/** 강화 쿨타임 칸의 이름. 카멜케이스로 적어도 읽어 준다. */
	private static String amplifiedKey(@Nullable JsonObject json) {
		return json != null && json.has("amplifiedCooldownSeconds")
				? "amplifiedCooldownSeconds"
				: "amplified_cooldown_seconds";
	}

	/** 카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다. */
	private static String cooldownKey(@Nullable JsonObject json) {
		return json != null && json.has("cooldownSeconds") ? "cooldownSeconds" : "cooldown_seconds";
	}

	private static int clamp(String perkId, int seconds) {
		if (seconds >= MIN_COOLDOWN_SECONDS && seconds <= MAX_COOLDOWN_SECONDS) {
			return seconds;
		}
		int cut = Math.max(MIN_COOLDOWN_SECONDS, Math.min(MAX_COOLDOWN_SECONDS, seconds));
		SharedFateMod.LOGGER.warn(
				"증강 {}: damage_ward 의 cooldown_seconds 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS, cut, seconds);
		return cut;
	}

	/** 한 번 막은 뒤 다시 막을 수 있을 때까지의 시간(틱). */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	/** 한 번 막은 뒤 다시 막을 수 있을 때까지의 시간(초). 화면에 적을 때 쓴다. */
	public int cooldownSeconds() {
		return cooldownTicks / TICKS_PER_SECOND;
	}

	/** 「가호 3」이 켜졌을 때의 쿨타임(틱). 안 적으면 {@link #cooldownTicks()} 와 같다. */
	public int amplifiedCooldownTicks() {
		return amplifiedCooldownTicks;
	}

	/**
	 * 강화 쿨타임을 평소 값으로 삼은 같은 효과.
	 *
	 * <p>{@link #wardFor} 가 「가호 3」인 팀에 돌려주는 것이 이것이다. 값 객체라 원래 것은
	 * 그대로 두고 새로 만든다 — 정의는 팀마다 공유되므로 제자리에서 고치면 다른 팀까지 바뀐다.
	 */
	public DamageWardEffect amplified() {
		return cooldownTicks == amplifiedCooldownTicks
				? this
				: new DamageWardEffect(amplifiedCooldownTicks, amplifiedCooldownTicks);
	}

	/**
	 * 이 사람이 고른 증강 중에 「호위」가 있는가. 있으면 그 효과를, 없으면 {@code null}.
	 *
	 * <p>훑는 방식은 {@link ShieldFallImmunityEffect#heldBy} 와 같다. 팀이 없거나, 증강을 껐거나,
	 * 아직 아무 증강도 없으면 곧바로 {@code null} 이다. 풀에서 사라진 id 는 건너뛴다.
	 *
	 * <p>여기에 <b>「고른 사람」 조건이 하나 더 붙는다.</b> {@code perkOwners} 에 적힌 주인이
	 * 물어본 사람과 같은 증강만 센다. 「숨은 재능」처럼 다른 증강이 덤으로 준 증강은 고른 사람을
	 * 알 수 없어 {@code perkOwners} 에 들어오지 않으므로 여기서도 자연히 걸리지 않는다.
	 *
	 * <p>여럿을 고른 사람에게는 <b>쿨타임이 가장 짧은 것</b>이 이긴다. 두 개를 골랐는데 더 긴
	 * 쪽이 이기면 나중에 고른 증강이 앞의 것을 깎아 먹는 꼴이 된다.
	 *
	 * @param state  피해를 받는 사람의 팀 상태
	 * @param victim 피해를 받는 사람의 UUID
	 */
	public static @Nullable DamageWardEffect wardFor(@Nullable TeamState state,
			@Nullable UUID victim) {
		if (state == null || victim == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return null;
		}
		DamageWardEffect shortest = null;
		for (String perkId : state.ownedPerks) {
			// 「가호 4」가 켜지면 고른 사람이 아니어도 걸린다. 판정은 PerkBlessingSet 한 곳이다.
			if (!PerkBlessingSet.appliesTo(state, perkId, victim)) {
				continue;
			}
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			DamageWardEffect found = shortestOf(perk.effects());
			// 「가호 3」이면 강화 쿨타임을 평소 값으로 삼은 것을 돌려준다. 부르는 쪽이 강화
			// 여부를 몰라도 되게 하려는 것이다.
			if (found != null && PerkBlessingSet.isAmplified(state, perkId)) {
				found = found.amplified();
			}
			shortest = shorter(shortest, found);
		}
		return shortest;
	}

	/**
	 * 이 증강을 이 사람이 골랐는가.
	 *
	 * <p>{@code perkOwners} 는 증강 id → 고른 사람의 UUID 다. 적혀 있지 않은 증강(다른 증강이
	 * 덤으로 준 것)은 주인이 {@code null} 이라 누구와도 같지 않다. 팀 상태 하나만 보는 순수
	 * 판정이라 살아 있는 월드 없이 시험할 수 있다.
	 */
	public static boolean chosenBy(@Nullable TeamState state, @Nullable String perkId,
			@Nullable UUID player) {
		return state != null && player != null && player.equals(state.perkOwners.get(perkId));
	}

	/**
	 * 효과 목록에서 쿨타임이 가장 짧은 「호위」를 고른다. 없으면 {@code null}.
	 *
	 * <p>레지스트리도 팀도 보지 않는 순수 계산이다.
	 */
	public static @Nullable DamageWardEffect shortestOf(@Nullable Iterable<PerkEffect> effects) {
		if (effects == null) {
			return null;
		}
		DamageWardEffect shortest = null;
		for (PerkEffect effect : effects) {
			if (effect instanceof DamageWardEffect ward) {
				shortest = shorter(shortest, ward);
			}
		}
		return shortest;
	}

	private static @Nullable DamageWardEffect shorter(@Nullable DamageWardEffect first,
			@Nullable DamageWardEffect second) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		return second.cooldownTicks < first.cooldownTicks ? second : first;
	}
}
