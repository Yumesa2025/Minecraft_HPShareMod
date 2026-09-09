package com.sharedfate.perk;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「호위」({@code damage_ward})의 쿨타임을 사람마다 기억한다.
 *
 * <p>기억하는 것은 <b>마지막으로 막은 시각</b> 하나뿐이다. 남은 시간을 매 틱 깎지 않으므로
 * 틱 처리에 얹히는 비용이 없고, 서버가 잠깐 멈췄다 돌아와도 계산이 어긋나지 않는다.
 *
 * <h2>저장하지 않는다</h2>
 * <p>쿨타임은 길어야 2분이다. 서버를 껐다 켜는 데 그보다 짧게 걸리는 일은 없으므로, 저장하지
 * 않아도 사람이 느낄 수 있는 차이는 「재시작 직후 한 번 더 막힌다」뿐이고 그것도 정상적으로는
 * 이미 차 있어야 할 쿨타임이다. 반대로 저장하면 회차 리셋·팀 재구성 때마다 지워 줄 자리를
 * 하나 더 만드는 셈이라 잃는 쪽이 크다.
 *
 * <h2>시각은 게임 시간(틱)으로 받는다</h2>
 * <p>{@code ServerLevel#getGameTime()} 이다. 이 모드의 다른 주기 계산
 * ({@code PeriodicPerkManager}·{@code PerkSupplyDrops})이 쓰는 것과 같은 눈금이라, 서로 다른
 * 시간축이 섞이지 않는다. 시각을 밖에서 받으므로 판정 자체는 살아 있는 월드 없이 시험할 수 있다.
 */
public final class DamageWardTracker {
	/**
	 * 사람마다 마지막으로 막은 게임 시간(틱).
	 *
	 * <p>피해 처리는 서버 스레드 한 곳에서만 도는 것이 정상이지만, 이 자리가 피해 경로 한가운데라
	 * 다른 스레드에서 들어오더라도 자료구조가 깨지지는 않게 해 둔다.
	 */
	private static final Map<UUID, Long> LAST_BLOCK = new ConcurrentHashMap<>();

	/**
	 * 이만큼 쌓이면 이미 쿨타임이 다 찬 기록을 걷어낸다.
	 *
	 * <p>쿨타임이 찬 기록은 남아 있든 없든 답이 같아서 버려도 아무 일도 일어나지 않는다.
	 * 접속했다 나간 사람의 기록이 영원히 쌓이는 것만 막으면 되므로 문턱은 넉넉해도 된다.
	 */
	private static final int PRUNE_THRESHOLD = 256;

	private DamageWardTracker() {
	}

	/**
	 * 지금 한 번 막을 수 있으면 막았다고 적고 {@code true} 를 돌려준다.
	 *
	 * <p><b>참을 돌려준 순간 쿨타임이 시작된다.</b> 그러니 실제로 피해를 버릴 것이 확실한
	 * 자리에서만 불러야 한다. 부르는 쪽({@code PerkDamage.blocksMobDamage})이 몹 피해인지,
	 * 이 사람이 고른 증강인지, 어차피 안 들어갈 피해는 아닌지를 모두 확인한 뒤에 마지막으로
	 * 이것을 부른다.
	 *
	 * @param player        막으려는 사람
	 * @param nowTick       지금 게임 시간(틱)
	 * @param cooldownTicks 이 사람이 가진 「호위」의 쿨타임(틱)
	 */
	public static boolean tryConsume(@Nullable UUID player, long nowTick, int cooldownTicks) {
		if (player == null) {
			return false;
		}
		if (!ready(LAST_BLOCK.get(player), nowTick, cooldownTicks)) {
			return false;
		}
		if (LAST_BLOCK.size() >= PRUNE_THRESHOLD) {
			prune(nowTick, cooldownTicks);
		}
		LAST_BLOCK.put(player, nowTick);
		return true;
	}

	/**
	 * 남은 쿨타임(틱). 다 찼으면 0.
	 *
	 * <p>아무것도 바꾸지 않는다. 화면이나 시험이 상태를 물어볼 때 쓴다.
	 */
	public static long remainingTicks(@Nullable UUID player, long nowTick, int cooldownTicks) {
		if (player == null) {
			return 0L;
		}
		return remaining(LAST_BLOCK.get(player), nowTick, cooldownTicks);
	}

	/**
	 * 지금 막을 수 있는가. 시각 계산만 하는 순수 판정이다.
	 *
	 * <p>세 가지를 본다.
	 *
	 * <ul>
	 *   <li>한 번도 막은 적이 없으면 언제나 막을 수 있다.</li>
	 *   <li>지난 시간이 쿨타임 이상이면 막을 수 있다.</li>
	 *   <li><b>시각이 되감겼으면</b> 막을 수 있다. 회차마다 월드를 새로 만드는 모드라 게임 시간이
	 *       과거로 돌아가는 일이 실제로 생긴다. 이때 되감긴 만큼을 기다리게 하면 「호위」가 몇
	 *       시간씩 잠긴다.</li>
	 * </ul>
	 *
	 * @param lastTick 마지막으로 막은 시각. 한 번도 막은 적이 없으면 {@code null}
	 */
	static boolean ready(@Nullable Long lastTick, long nowTick, int cooldownTicks) {
		return remaining(lastTick, nowTick, cooldownTicks) <= 0L;
	}

	/** 남은 쿨타임(틱)을 세는 순수 계산. 다 찼으면 0. */
	static long remaining(@Nullable Long lastTick, long nowTick, int cooldownTicks) {
		if (lastTick == null || cooldownTicks <= 0) {
			return 0L;
		}
		long elapsed = nowTick - lastTick;
		if (elapsed < 0L || elapsed >= cooldownTicks) {
			return 0L;
		}
		return cooldownTicks - elapsed;
	}

	/** 한 사람의 기록만 지운다. 팀에서 빠지거나 증강을 잃었을 때 쓸 수 있다. */
	public static void forget(@Nullable UUID player) {
		if (player != null) {
			LAST_BLOCK.remove(player);
		}
	}

	/** 기록을 통째로 비운다. 서버가 멈출 때와 시험이 상태를 격리할 때 쓴다. */
	public static void reset() {
		LAST_BLOCK.clear();
	}

	/** 쿨타임이 이미 다 찬 기록만 걷어낸다. 답이 달라지지 않는 청소다. */
	private static void prune(long nowTick, int cooldownTicks) {
		Iterator<Map.Entry<UUID, Long>> entries = LAST_BLOCK.entrySet().iterator();
		while (entries.hasNext()) {
			if (ready(entries.next().getValue(), nowTick, cooldownTicks)) {
				entries.remove();
			}
		}
	}
}
