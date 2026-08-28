package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 팀원에게 상시 상태이상을 건다.
 *
 * <p>지속시간은 무한으로 두고, 증강을 잃을 때 {@link #remove}로 걷어낸다.
 * 중첩하면 등급이 한 단계씩 올라간다. 즉 중첩 수가 n일 때 실제 등급은 {@code amplifier + (n - 1)}이다.
 * 중첩 1일 때는 설정한 값 그대로여야 하므로 곱하지 않고 더한다.
 *
 * <p>상태이상 자체는 처음 적용할 때 찾는다. 정의를 읽는 시점에는 레지스트리가 아직
 * 준비되지 않았을 수 있기 때문이다.
 */
public final class StatusEffectPerk implements PerkEffect {
	/** 바닐라 상태이상 등급 한계. */
	private static final int MAX_AMPLIFIER = 255;

	private final Identifier effectId;
	private final int amplifier;

	private Holder<MobEffect> effect;
	private boolean resolveFailed;

	public StatusEffectPerk(Identifier effectId, int amplifier) {
		this.effectId = effectId;
		this.amplifier = amplifier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		String rawEffect = PerkEffectType.readString(json, "effect");
		if (rawEffect == null || rawEffect.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: status_effect 효과에 effect 필드가 없습니다", perkId);
			return null;
		}
		Identifier effectId = Identifier.tryParse(rawEffect.trim());
		if (effectId == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 올바르지 않은 상태이상 이름 {}", perkId, rawEffect);
			return null;
		}

		int amplifier = PerkEffectType.readInt(json, "amplifier", 0);
		if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
			SharedFateMod.LOGGER.warn("증강 {}: amplifier 값이 범위를 벗어났습니다 ({})", perkId, amplifier);
			return null;
		}

		return new StatusEffectPerk(effectId, amplifier);
	}

	@Override
	public void apply(ServerPlayer player, int stacks) {
		if (player == null) {
			return;
		}
		MobEffectInstance granted = grantedInstance(stacks);
		if (granted != null) {
			player.addEffect(granted);
		}
	}

	/**
	 * 이 증강이 거는 상태이상 인스턴스.
	 *
	 * <p>{@link #apply}와 {@link com.sharedfate.perk.PerkStatusEffects}가 같은 정의를 보도록
	 * 만드는 곳을 여기 하나로 모았다. 상태이상을 찾지 못하면 null.
	 */
	public @Nullable MobEffectInstance grantedInstance(int stacks) {
		Holder<MobEffect> resolved = resolve();
		if (resolved == null) {
			return null;
		}
		return new MobEffectInstance(resolved, MobEffectInstance.INFINITE_DURATION,
				amplifierFor(stacks), false, false, true);
	}

	/**
	 * 이 증강이 거는 상태이상 홀더. 아직 안 찾았으면 지금 찾는다.
	 * 레지스트리에 없으면 null.
	 */
	public @Nullable Holder<MobEffect> resolvedEffect() {
		return resolve();
	}

	@Override
	public void remove(ServerPlayer player) {
		if (player == null) {
			return;
		}
		Holder<MobEffect> resolved = resolve();
		if (resolved != null) {
			player.removeEffect(resolved);
		}
	}

	/** 중첩 수를 반영한 실제 등급. */
	public int amplifierFor(int stacks) {
		int extra = Math.max(1, stacks) - 1;
		return Math.min(MAX_AMPLIFIER, amplifier + extra);
	}

	public Identifier effectId() {
		return effectId;
	}

	public int amplifier() {
		return amplifier;
	}

	private Holder<MobEffect> resolve() {
		if (effect != null || resolveFailed) {
			return effect;
		}
		try {
			Optional<Holder.Reference<MobEffect>> found = BuiltInRegistries.MOB_EFFECT.get(effectId);
			if (found.isEmpty()) {
				resolveFailed = true;
				SharedFateMod.LOGGER.warn("증강 효과가 가리키는 상태이상을 찾을 수 없습니다: {}", effectId);
			} else {
				effect = found.get();
			}
		} catch (Exception error) {
			resolveFailed = true;
			SharedFateMod.LOGGER.warn("상태이상 {} 을 찾다가 실패했습니다", effectId, error);
		}
		return effect;
	}
}
