package com.sharedfate.perk.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 팀원이 너무 멀리 흩어지면 한곳으로 모으고, 모은 뒤 잠깐 효과를 얹는다.
 *
 * <pre>{@code
 * { "type": "gather", "distance": 64, "cooldown_ticks": 200,
 *   "effects": [ { "type": "status_effect", "effect": "minecraft:blindness", "duration": 5 },
 *                { "type": "status_effect", "effect": "minecraft:slowness", "duration": 5 } ] }
 * }</pre>
 *
 * <p>프리즘 「운명 공동체」가 이 타입을 쓴다.
 *
 * <h2>필드</h2>
 * <ul>
 *   <li>{@code distance} — 아무 두 팀원 사이가 이 거리를 넘으면 발동한다. 반드시 적어야 한다.
 *       <b>차원이 다르면 거리와 무관하게 발동한다.</b></li>
 *   <li>{@code cooldown_ticks} — 한 번 모은 뒤 이만큼은 다시 발동하지 않는다. 안 적으면
 *       {@link #DEFAULT_COOLDOWN_TICKS} 틱이다. {@code cooldownTicks} 로 적어도 같다.</li>
 *   <li>{@code effects} — 모은 뒤 얹을 하위 효과. 없어도 된다. 읽는 규칙은
 *       {@link OnSwapEffect#readGrants} 그대로다</li>
 * </ul>
 *
 * <h2>재우는 시간이 필요한 이유</h2>
 * <p>모으고 나서도 팀원들은 곧바로 흩어질 수 있고, 그때마다 다시 끌어오면 아무도 움직일 수
 * 없다. 게다가 실명·구속이 겹겹이 쌓인다. 한 번 모은 뒤에는 정해진 시간 동안 판정을 쉰다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "얼마나 멀면, 얼마나 쉬고, 무엇을 얹을 것인가"만 들고 있는 자료 그릇이다.
 * 실제로 거리를 재고 끌어오는 일은 {@link com.sharedfate.sync.TeamGathering} 이 맡고, 팀이
 * 이 효과를 갖고 있는지 묻는 자리는 {@link com.sharedfate.perk.PerkSwapRules} 다.
 */
public final class GatherEffect implements PerkEffect {
	/** 받아들이는 기준 거리 범위(블록). 너무 짧으면 서로 붙어 있어도 계속 발동한다. */
	static final double MIN_DISTANCE = 8.0;
	static final double MAX_DISTANCE = 4096.0;
	/** {@code cooldown_ticks} 를 적지 않았을 때 재우는 시간. 10초다. */
	public static final int DEFAULT_COOLDOWN_TICKS = 200;
	/** 재우는 시간 범위. 1초보다 짧으면 판정 주기(20틱)와 사실상 같아진다. */
	static final int MIN_COOLDOWN_TICKS = 20;
	static final int MAX_COOLDOWN_TICKS = 72000;

	private final double distance;
	private final int cooldownTicks;
	private final List<OnSwapEffect.Grant> grants;

	public GatherEffect(double distance, int cooldownTicks, List<OnSwapEffect.Grant> grants) {
		this.distance = distance;
		this.cooldownTicks = cooldownTicks;
		this.grants = List.copyOf(grants);
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double distance = PerkEffectType.readDouble(json, "distance");
		if (distance == null || distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: gather 의 distance 값이 없거나 범위를 벗어났습니다 ({})", perkId, distance);
			return null;
		}

		Integer cooldownTicks = readCooldownTicks(perkId, json);
		if (cooldownTicks == null) {
			return null;
		}

		List<OnSwapEffect.Grant> grants =
				OnSwapEffect.readGrants(perkId, index, "gather", json, false);
		if (grants == null) {
			return null;
		}
		return new GatherEffect(distance, cooldownTicks, grants);
	}

	/** 이 거리를 넘으면 모은다. */
	public double distance() {
		return distance;
	}

	/** 한 번 모은 뒤 판정을 쉬는 틱 수. */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	public List<OnSwapEffect.Grant> grants() {
		return grants;
	}

	/**
	 * 모인 팀원에게 하위 효과를 얹는다.
	 *
	 * <p>이미 그 자리에 있던 사람에게도 얹는다. 이 모드는 팀이 하나로 묶여 움직인다는 원칙을
	 * 따르므로, 우연히 기준점이 된 한 사람만 대가를 면제받는 것이 오히려 어긋난다.
	 */
	public void grantTo(@Nullable ServerPlayer player) {
		OnSwapEffect.grantAll(player, grants);
	}

	/** 상시로 붙는 것이 없으므로 증강을 잃을 때 마침 걸려 있던 것만 걷어낸다. */
	@Override
	public void remove(ServerPlayer player) {
		OnSwapEffect.revokeAll(player, grants);
	}

	/**
	 * {@code cooldown_ticks} 를 읽는다. 범위를 벗어나면 null.
	 *
	 * <p>코드베이스의 snake_case 를 기본으로 하되 {@code cooldownTicks} 로 적어도 같다.
	 * {@link com.sharedfate.perk.TemporaryPerkGrants} 가 {@code durationSeconds} 에 쓰는 규칙과 같다.
	 */
	private static @Nullable Integer readCooldownTicks(String perkId, JsonObject json) {
		String key = json.has("cooldown_ticks") ? "cooldown_ticks" : "cooldownTicks";
		JsonElement raw = json.get(key);
		if (raw == null || raw.isJsonNull()) {
			return DEFAULT_COOLDOWN_TICKS;
		}
		Double ticks = PerkEffectType.readDouble(json, key);
		if (ticks == null || ticks < MIN_COOLDOWN_TICKS || ticks > MAX_COOLDOWN_TICKS) {
			SharedFateMod.LOGGER.warn("증강 {}: gather 의 {} 값이 올바르지 않습니다 ({})",
					perkId, key, raw);
			return null;
		}
		return (int) Math.round(ticks);
	}
}
