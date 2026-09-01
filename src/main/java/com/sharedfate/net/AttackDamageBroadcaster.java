package com.sharedfate.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 접속 중인 사람에게 자기 공격력을 알려 주는 자리.
 *
 * <p>바닐라가 {@code minecraft:attack_damage} 를 클라이언트에 보내지 않는 사정은
 * {@link AttackDamagePayload} 에 적었다. 여기는 그것을 언제 어떻게 보낼지만 맡는다.
 *
 * <h2>얼마나 자주 보는가</h2>
 * <p>{@value #SCAN_INTERVAL_TICKS} 틱마다 훑고, <b>값이 달라진 사람에게만</b> 보낸다. 매 틱
 * 보내면 사람 수만큼의 패킷이 아무 뜻 없이 초당 20번 나간다. 반대로 「무엇이 공격력을 바꾸는
 * 사건인가」를 하나하나 잡는 길은 택하지 않았다 — 무기 교체·증강 획득·증강 상실·장비 교체·
 * 팀 해체·회차 리셋·다른 모드의 수정자까지 전부 걸어야 하고, 하나만 빠뜨려도 그 경우에만
 * 옛 값이 남는다. 결과값 하나를 견주면 <b>무엇이 바꿨든</b> 걸린다.
 *
 * <h2>무기를 바꾸면 따라온다</h2>
 * <p>{@code LivingEntity.detectEquipmentUpdates} 가 매 틱 손에 든 것의 속성 수정자를 갈아
 * 끼우므로, 무기를 바꾼 그 틱에 {@code getValue()} 가 이미 새 값이다. 여기서 다음 훑기에
 * 그것을 보므로 늦어야 {@value #SCAN_INTERVAL_TICKS} 틱 뒤에는 화면에 반영된다.
 *
 * <h2>다시 접속하면 반드시 다시 보낸다</h2>
 * <p>클라이언트는 월드에서 나갈 때 들고 있던 값을 버린다. 그래서 접속이 끊길 때
 * {@link #forget} 으로 「이미 보냈다」는 기록도 함께 버려야 한다. 그러지 않으면 공격력이
 * 그대로인 사람은 다시 들어와도 영영 값을 받지 못한다.
 */
public final class AttackDamageBroadcaster {
	/**
	 * 값이 달라졌는지 확인하는 주기.
	 *
	 * <p>0.25초다. 무기를 바꾸고 화면을 보는 사이에 이미 끝나 있을 만큼 짧으면서, 핫바를
	 * 휘저어도 초당 4번을 넘지 않을 만큼 길다.
	 */
	private static final int SCAN_INTERVAL_TICKS = 5;

	/** 사람별로 마지막에 보낸 값. 달라졌을 때만 다시 보낸다. */
	private static final Map<UUID, AttackDamagePayload> LAST_SENT = new HashMap<>();

	private static int scanCooldown;

	private AttackDamageBroadcaster() {
	}

	/** 매 틱 도는 지점. {@link #SCAN_INTERVAL_TICKS} 틱마다만 실제로 훑는다. */
	public static void flush(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++scanCooldown < SCAN_INTERVAL_TICKS) {
			return;
		}
		scanCooldown = 0;

		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			online.add(playerId);
			AttackDamagePayload now = of(player);
			if (now == null || !now.differsFrom(LAST_SENT.get(playerId))) {
				continue;
			}
			if (!ServerPlayNetworking.canSend(player, AttackDamagePayload.TYPE)) {
				// 이 패킷을 모르는 클라이언트다. 다음 훑기에서 다시 시도한다.
				continue;
			}
			ServerPlayNetworking.send(player, now);
			LAST_SENT.put(playerId, now);
		}
		// 나간 사람의 기록은 버린다.
		LAST_SENT.keySet().retainAll(online);
	}

	/**
	 * 이 사람이 지금 받아야 할 값. 속성을 찾지 못하면 null.
	 *
	 * <p>기준값과 지금 값을 화면의 다른 세 줄과 <b>같은 방법으로</b> 뽑는다 —
	 * {@code getBaseValue()} 가 바닐라 기본값, {@code getValue()} 가 증강·무기까지 얹힌 값이다.
	 * 증강이 거는 수정자는 임시(transient) 수정자라 기본값을 건드리지 않으므로, 왼쪽 값은
	 * 언제나 바닐라 그대로 남는다.
	 */
	static @Nullable AttackDamagePayload of(@Nullable ServerPlayer player) {
		if (player == null) {
			return null;
		}
		AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attribute == null) {
			return null;
		}
		return new AttackDamagePayload(
				(float) attribute.getBaseValue(), (float) attribute.getValue());
	}

	/** 한 사람의 기록을 버린다. 접속이 끊길 때 부른다. */
	public static void forget(@Nullable UUID playerId) {
		if (playerId != null) {
			LAST_SENT.remove(playerId);
		}
	}

	/** 서버가 멈출 때 들고 있던 기록을 버린다. */
	public static void reset() {
		LAST_SENT.clear();
		scanCooldown = 0;
	}
}
