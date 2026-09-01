package com.sharedfate.client;

import com.sharedfate.net.AttackDamagePayload;
import org.jetbrains.annotations.Nullable;

/**
 * 클라이언트가 보관하는 「내 공격력」.
 *
 * <p>{@link AttackDamagePayload} 로 갱신되며 월드에서 나가면 {@link #clear()} 로 비운다.
 * 바닐라가 {@code minecraft:attack_damage} 만은 클라이언트에 보내지 않아, 이 값만은 화면이
 * 플레이어의 속성에서 읽지 못하고 여기서 읽는다.
 *
 * <h2>서버가 말해 주기 전에는 아무 줄도 그리지 않는다</h2>
 * <p>{@link #known()} 이 거짓인 동안 「능력치」 탭은 공격력 줄 자체를 건너뛴다. 맨손 기본값
 * 1.0 을 대신 그리면 <b>없는 사실을 만들어 내는</b> 셈이고, 그것이 바로 이 패킷을 만든 이유다.
 * 값은 접속하고 늦어야 몇 틱 안에 오므로 창을 열었을 때 비어 있는 일은 실제로는 없다.
 *
 * <p>읽는 자리는 화면 그리기이고 쓰는 자리는 패킷 수신인데, 수신 쪽을
 * {@code client.execute(...)} 로 클라이언트 본 스레드에 올려 두었으므로 둘은 같은 스레드다.
 * {@link com.sharedfate.client.perk.ClientPerkFeatures} 와 같은 구도다.
 */
public final class ClientAttackDamage {
	private static boolean known;
	private static double base;
	private static double current;

	private ClientAttackDamage() {
	}

	/** {@link AttackDamagePayload} 를 받았을 때 부른다. */
	public static void update(@Nullable AttackDamagePayload payload) {
		if (payload == null) {
			clear();
			return;
		}
		known = true;
		base = payload.base();
		current = payload.current();
	}

	/** 월드에서 나갈 때 부른다. */
	public static void clear() {
		known = false;
		base = 0.0;
		current = 0.0;
	}

	/** 서버가 값을 알려 준 적이 있는가. 거짓이면 화면은 공격력 줄을 그리지 않는다. */
	public static boolean known() {
		return known;
	}

	/** 바닐라 기본값. 플레이어는 1.0 이다. */
	public static double base() {
		return base;
	}

	/** 증강과 지금 든 무기까지 얹힌 값. */
	public static double current() {
		return current;
	}
}
