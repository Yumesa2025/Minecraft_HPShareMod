package com.sharedfate.perk;

import com.sharedfate.perk.effect.StatusEffectPerk;
import com.sharedfate.team.TeamState;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 팀의 증강이 걸어 둔 상태이상 목록.
 *
 * <p>{@code EffectSync} 는 대표 플레이어의 활성 상태이상을 통째로 {@link TeamState#effects} 로
 * 복사해 팀에 공유한다. 그런데 {@link StatusEffectPerk} 가 건 상태이상까지 거기 딸려 들어가면,
 * 회차가 바뀌어 증강을 잃은 뒤에도 팀 상태에 남아 되살아난다. 그래서 공유 대상을 모을 때
 * 증강분만 빼야 하고, 그 판별을 이 클래스가 맡는다.
 *
 * <p>플레이어의 상태이상 표는 종류마다 인스턴스가 하나뿐이다. 증강이 건 것과 포션이 준 것이
 * 겹치면 강한 쪽만 겉으로 남고 약한 쪽은 그 안에 숨는다. 따라서 "증강이 거는 종류인가"가 아니라
 * "지금 걸려 있는 이 인스턴스가 증강이 건 바로 그것인가"를 봐야 한다. 판별 기준은 두 가지다.
 *
 * <ol>
 *   <li>무한 지속일 것 — 증강은 항상 무한으로 걸고, 포션·비컨·전도체는 절대 무한이 아니다.
 *   <li>등급이 증강이 주는 등급 이하일 것 — 더 센 포션이 위에 덮인 상태라면 그건 포션분이므로
 *       평소대로 팀에 공유돼야 한다.
 * </ol>
 *
 * <p>보유 증강이 하나도 없으면 {@link #NONE} 을 돌려준다. 이때는 어떤 인스턴스도 증강분으로
 * 보지 않으므로 {@code EffectSync} 의 동작이 증강 도입 전과 완전히 같다.
 */
public final class PerkStatusEffects {
	/** 증강이 거는 상태이상이 하나도 없는 상태. */
	private static final PerkStatusEffects NONE = new PerkStatusEffects(Map.of());

	/** 상태이상 종류 → 증강이 주는 등급 중 가장 높은 값. */
	private final Map<Holder<MobEffect>, Integer> amplifiers;

	private PerkStatusEffects(Map<Holder<MobEffect>, Integer> amplifiers) {
		this.amplifiers = amplifiers;
	}

	/**
	 * 팀 상태에서 증강이 거는 상태이상을 모은다.
	 *
	 * <p>보유 증강이 비어 있으면 증강 풀을 들여다보지도 않고 바로 {@link #NONE} 이다.
	 * 매 틱 팀마다 불리는 자리라 그 경로가 짧아야 한다.
	 */
	public static PerkStatusEffects of(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return NONE;
		}

		Map<Holder<MobEffect>, Integer> collected = null;
		for (PerkStack stack : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(stack.perkId()).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (!(effect instanceof StatusEffectPerk status)) {
					continue;
				}
				Holder<MobEffect> resolved = status.resolvedEffect();
				if (resolved == null) {
					continue;
				}
				if (collected == null) {
					collected = new HashMap<>();
				}
				// 같은 종류를 여러 증강이 걸면 결국 가장 센 것만 겉으로 남는다.
				collected.merge(resolved, status.amplifierFor(stack.count()), Math::max);
			}
		}
		return collected == null ? NONE : new PerkStatusEffects(collected);
	}

	/** 증강이 거는 상태이상이 하나도 없는가. */
	public boolean isEmpty() {
		return amplifiers.isEmpty();
	}

	/** 증강이 이 종류의 상태이상을 거는가. 등급이나 지속시간은 보지 않는다. */
	public boolean covers(@Nullable Holder<MobEffect> effect) {
		return effect != null && amplifiers.containsKey(effect);
	}

	/**
	 * 지금 걸려 있는 이 인스턴스가 증강이 건 것인가.
	 *
	 * <p>포션은 지속시간이 유한하므로 절대 여기 걸리지 않는다. 증강과 같은 종류의 포션을
	 * 마셨다면 그 인스턴스는 그대로 팀에 공유된다.
	 */
	public boolean grants(@Nullable MobEffectInstance instance) {
		if (instance == null || amplifiers.isEmpty()) {
			return false;
		}
		Integer granted = amplifiers.get(instance.getEffect());
		return granted != null
				&& instance.isInfiniteDuration()
				&& instance.getAmplifier() <= granted;
	}

	/** 활성 상태이상 중 팀에 공유해도 되는 것만 골라 복사본으로 돌려준다. */
	public List<MobEffectInstance> shareable(Collection<MobEffectInstance> active) {
		List<MobEffectInstance> result = new ArrayList<>(active.size());
		for (MobEffectInstance instance : active) {
			if (instance == null || grants(instance)) {
				continue;
			}
			result.add(new MobEffectInstance(instance));
		}
		return result;
	}
}
