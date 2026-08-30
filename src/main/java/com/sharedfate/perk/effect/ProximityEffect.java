package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 팀원 전원이 서로 가까이 붙어 있는 동안에만 효과를 얹는다.
 *
 * <pre>{@code
 * { "type": "proximity", "distance": 30,
 *   "effects": [ { "type": "status_effect", "effect": "minecraft:regeneration", "duration": 3 } ] }
 * }</pre>
 *
 * <p>프리즘 「운명 공동체」가 {@code gather} 와 짝지어 쓴다. 흩어지면 강제로 모으고, 모여
 * 있으면 보상을 준다. 둘이 같은 거리 기준을 쓰면 "떨어지지 말라"는 한 가지 요구가 된다.
 *
 * <h2>왜 지속시간을 적어야 하는가</h2>
 * <p>이 효과는 붙였다 떼는 것이 아니라 <b>조건이 참인 동안 계속 다시 붙이는</b> 방식이다.
 * 판정은 {@link com.sharedfate.sync.TeamGathering#CHECK_INTERVAL_TICKS} 마다 한 번뿐이라,
 * 그 간격보다 넉넉히 긴 지속시간을 적어야 끊기지 않는다. 대신 조건이 깨지면 아무도
 * 걷어내지 않아도 적어 둔 시간 안에 저절로 사라진다.
 *
 * <p>붙였다 떼는 방식으로 만들면 접속 종료·차원 이동·죽음처럼 떼는 시점을 놓치는 자리가
 * 늘어난다. 저절로 사라지는 쪽이 남는 상태가 없다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>거리를 재고 실제로 얹는 일은 {@link com.sharedfate.sync.TeamGathering} 이 맡는다.
 * 여기는 "얼마나 가까워야 하고 무엇을 얹을 것인가"만 들고 있는 자료 그릇이다.
 */
public final class ProximityEffect implements PerkEffect {
	/** 받아들이는 거리 범위(블록). 너무 짧으면 사실상 발동하지 않는다. */
	static final double MIN_DISTANCE = 4.0;
	static final double MAX_DISTANCE = 512.0;

	private final double distance;
	private final List<OnSwapEffect.Grant> grants;

	public ProximityEffect(double distance, List<OnSwapEffect.Grant> grants) {
		this.distance = distance;
		this.grants = List.copyOf(grants);
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double distance = PerkEffectType.readDouble(json, "distance");
		if (distance == null || distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: proximity 의 distance 값이 없거나 범위를 벗어났습니다 ({})", perkId, distance);
			return null;
		}
		List<OnSwapEffect.Grant> grants =
				OnSwapEffect.readGrants(perkId, index, "proximity", json, true);
		if (grants == null || grants.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: proximity 에 얹을 효과가 없습니다", perkId);
			return null;
		}
		return new ProximityEffect(distance, grants);
	}

	/** 이 거리 안에 전원이 있어야 발동한다. */
	public double distance() {
		return distance;
	}

	/** 조건이 참인 동안 이 사람에게 효과를 다시 얹는다. */
	public void grantTo(@Nullable ServerPlayer player) {
		OnSwapEffect.grantAll(player, grants);
	}

	/**
	 * 증강을 잃을 때 마침 걸려 있던 것만 걷어낸다.
	 *
	 * <p>지속시간이 짧아 그냥 두어도 곧 사라지지만, 증강을 끈 직후에도 몇 초 남아 있으면
	 * 껐다는 사실이 의심스러워 보인다.
	 */
	@Override
	public void remove(ServerPlayer player) {
		OnSwapEffect.revokeAll(player, grants);
	}
}
