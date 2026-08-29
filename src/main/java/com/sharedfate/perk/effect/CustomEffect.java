package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * JSON으로 표현할 수 없는 동작을 Java 핸들러에 넘기는 확장점.
 *
 * <p>핸들러는 {@link PerkRegistry#registerCustom(String, PerkEffect)}로 등록한다.
 * 등록은 정의를 읽은 뒤에 일어날 수도 있으므로 핸들러는 만들 때가 아니라 쓸 때 찾는다.
 * 끝내 등록되지 않았으면 경고를 한 번 남기고 아무 일도 하지 않는다. 증강 하나가 비어 있는 것이
 * 서버가 멈추는 것보다 낫기 때문이다.
 */
public final class CustomEffect implements PerkEffect {
	private final String handlerId;
	private boolean warned;

	public CustomEffect(String handlerId) {
		this.handlerId = handlerId;
	}

	/** JSON에서 만든다. handler 필드가 없으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		String handler = PerkEffectType.readString(json, "handler");
		if (handler == null || handler.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: custom 효과에 handler 필드가 없습니다", perkId);
			return null;
		}
		return new CustomEffect(handler.trim());
	}

	public String handlerId() {
		return handlerId;
	}

	/** 지금 이 순간 핸들러가 등록돼 있는지. */
	public boolean isResolved() {
		return PerkRegistry.customHandler(handlerId).isPresent();
	}

	@Override
	public void apply(ServerPlayer player) {
		PerkEffect delegate = delegate();
		if (delegate == null) {
			return;
		}
		try {
			delegate.apply(player);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("custom 핸들러 {} 적용 중 오류", handlerId, error);
		}
	}

	@Override
	public void remove(ServerPlayer player) {
		PerkEffect delegate = delegate();
		if (delegate == null) {
			return;
		}
		try {
			delegate.remove(player);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("custom 핸들러 {} 해제 중 오류", handlerId, error);
		}
	}

	@Override
	public double damageDealtMultiplier() {
		PerkEffect delegate = delegate();
		if (delegate == null) {
			return 1.0;
		}
		try {
			return safe(delegate.damageDealtMultiplier());
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("custom 핸들러 {} 의 피해 배율 계산 중 오류", handlerId, error);
			return 1.0;
		}
	}

	@Override
	public double damageTakenMultiplier() {
		PerkEffect delegate = delegate();
		if (delegate == null) {
			return 1.0;
		}
		try {
			return safe(delegate.damageTakenMultiplier());
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("custom 핸들러 {} 의 피해 배율 계산 중 오류", handlerId, error);
			return 1.0;
		}
	}

	private PerkEffect delegate() {
		Optional<PerkEffect> found = PerkRegistry.customHandler(handlerId);
		if (found.isEmpty()) {
			if (!warned) {
				warned = true;
				SharedFateMod.LOGGER.warn("등록되지 않은 custom 핸들러입니다. 이 효과는 무시합니다: {}", handlerId);
			}
			return null;
		}
		warned = false;
		return found.get();
	}

	/** 핸들러가 이상한 값을 돌려줘도 피해 계산이 깨지지 않게 막는다. */
	private static double safe(double multiplier) {
		if (!Double.isFinite(multiplier) || multiplier < 0.0) {
			return 1.0;
		}
		return multiplier;
	}
}
