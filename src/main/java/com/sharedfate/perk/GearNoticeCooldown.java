package com.sharedfate.perk;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 장비 제한 알림을 사람마다 재우는 시계.
 *
 * <p>{@link PerkGearManager} 의 점검은 {@value PerkGearManager#SWEEP_INTERVAL_TICKS} 틱마다
 * 돈다. 밀려난 아이템을 사람이 계속 핫바로 되가져오면 점검도 계속 되밀어내므로, 알림을 그대로
 * 내보내면 <b>초당 네 줄</b>이 쌓인다.
 *
 * <h2>재우는 것은 말뿐이다</h2>
 * <p>이 클래스는 <b>알림을 낼지</b>만 답한다. 밀어내기·버리기 같은 실제 동작은 이 답과 상관없이
 * 매 점검마다 그대로 일어난다. 알림을 재운다고
 * 제한이 느슨해지면 그 사이에 다이아몬드 검을 휘두를 수 있게 되므로, 동작과 말을 섞으면 안 된다.
 *
 * <h2>종류마다 따로 센다</h2>
 * <p>{@link Kind} 별로 시각을 따로 갖는다. 「인벤토리로 옮겼습니다」가 방금 나갔다는 이유로
 * 「버렸습니다」까지 삼켜지면 안 되기 때문이다. <b>버려지는 것은 실제 손실이라 반드시 보여야
 * 한다.</b> 옮기기는 되돌릴 수 있지만 바닥에 떨어진 것은 사라질 수 있다.
 *
 * <h2>시각은 밖에서 받는다</h2>
 * <p>{@code now} 를 인자로 받는다. 부르는
 * 쪽({@link PerkGearManager})이 자기 틱 카운터를 넘긴다.
 *
 * <h2>기억이 새지 않는가</h2>
 * <p>{@link #prune} 이 두 가지를 함께 버린다 — <b>접속 중이 아닌 사람</b>의 기록과, 접속
 * 중이더라도 <b>이미 다 식은</b> 기록이다. 뒤쪽 덕분에 알림을 한 번 받고 마는 사람의 기록은
 * 1초 뒤 점검에서 사라진다. 남는 항목은 "지금 이 순간 도배되고 있는 접속자" 뿐이므로 서버가
 * 며칠 돌아도 접속자 수를 넘지 않는다. {@link PerkGearManager#reset} 은 서버가 멈출 때
 * {@link #clear} 로 통째로 비운다.
 *
 * <h2>스레드</h2>
 * <p>서버 틱 스레드에서만 쓴다. 평범한
 * {@link HashMap} 을 쓴다.
 */
public final class GearNoticeCooldown {
	/**
	 * 같은 종류의 알림을 다시 내보내기까지 재우는 시간. 20틱 = 1초.
	 *
	 * <p>점검 주기
	 * ({@value PerkGearManager#SWEEP_INTERVAL_TICKS} 틱)보다 길기만 하면 도배는 잡힌다.
	 */
	public static final int NOTICE_COOLDOWN_TICKS = 20;

	/** 알림의 종류. 종류마다 시각을 따로 잰다. */
	public enum Kind {
		/** 핫바·장착 칸에 있던 금지 아이템을 보관 칸으로 밀어냈다. */
		RELOCATED,
		/** 옮길 자리가 없어 바닥에 버렸다. 실제로 잃는 쪽이라 다른 알림에 묻히면 안 된다. */
		DROPPED,
		/** 공유 인벤토리로 되돌렸다. 왼손 고정과 평범한 벗기기가 쓴다. */
		STOWED
	}

	private static final Kind[] KINDS = Kind.values();

	/**
	 * 사람마다, 종류마다 「다음에 알림을 낼 수 있는 시각」.
	 *
	 * <p>배열의 자리는 {@link Kind#ordinal()} 이다. 항목이 없거나 지금이 그 시각 이상이면 낼 수
	 * 있다.
	 */
	private final Map<UUID, long[]> nextAllowed = new HashMap<>();

	private final int cooldownTicks;

	public GearNoticeCooldown() {
		this(NOTICE_COOLDOWN_TICKS);
	}

	/** 재우는 시간을 직접 정한다. */
	GearNoticeCooldown(int cooldownTicks) {
		this.cooldownTicks = Math.max(0, cooldownTicks);
	}

	// ------------------------------------------------------------------ 판정

	/**
	 * 지금 이 사람에게 이 종류의 알림을 내보내도 되는가. <b>된다고 답할 때만</b> 시각을 갱신한다.
	 *
	 * <p>부르는 쪽은 참일 때만 채팅을 보내고, 거짓이어도 하던 동작은 그대로 끝낸다.
	 *
	 * @param playerId 알림을 받을 사람. null 이면 재우지 않는다 —
	 *                 누구인지 모르는 알림을 삼키면 원인을 영영 못 찾는다
	 * @param kind 알림의 종류. null 이면 재우지 않는다
	 * @param now 지금 시각(틱). 부르는 쪽의 단조 증가 카운터
	 * @return 내보내도 되면 true
	 */
	public boolean claim(@Nullable UUID playerId, @Nullable Kind kind, long now) {
		if (playerId == null || kind == null) {
			return true;
		}
		long[] slots = nextAllowed.computeIfAbsent(playerId, key -> new long[KINDS.length]);
		if (now < slots[kind.ordinal()]) {
			return false;
		}
		slots[kind.ordinal()] = now + cooldownTicks;
		return true;
	}

	// ------------------------------------------------------------------ 기억 버리기

	/**
	 * 남길 필요가 없어진 기록을 버린다. 점검마다 부른다.
	 *
	 * <p>접속 중이 아닌 사람은 통째로 버린다. 접속 중이라도 세 종류가 모두 식었으면 남겨 둘
	 * 이유가 없다 — 다시 도배가 시작되면 그때 새로 만들면 된다.
	 *
	 * @param online 지금 접속 중인 사람들
	 * @param now 지금 시각(틱)
	 */
	public void prune(@Nullable Set<UUID> online, long now) {
		if (nextAllowed.isEmpty()) {
			return;
		}
		nextAllowed.entrySet().removeIf(entry ->
				online == null || !online.contains(entry.getKey()) || cooled(entry.getValue(), now));
	}

	/** 이 사람의 모든 종류가 다 식었는가. */
	private static boolean cooled(long[] slots, long now) {
		for (long until : slots) {
			if (now < until) {
				return false;
			}
		}
		return true;
	}

	/** 서버가 멈출 때 통째로 비운다. */
	public void clear() {
		nextAllowed.clear();
	}

	/** 지금 기억하고 있는 사람 수. 기억이 새지 않는지 보는 시험이 쓴다. */
	int trackedPlayers() {
		return nextAllowed.size();
	}
}
