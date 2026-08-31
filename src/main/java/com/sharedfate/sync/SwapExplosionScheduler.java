package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.SwapExplosionEffect;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 「폭발 교환」의 폭발을 위치 교환과 같은 틱이 아니라 {@value #DELAY_TICKS}틱(0.5초) 뒤로
 * 미루고, 그동안 <b>이 교환에 참여한 사람 전원</b>을 이 폭발들에서 면역으로 둔다.
 *
 * <h2>왜 미루는가 — "바뀌자마자 죽는" 사고</h2>
 * <p>예전에는 위치 교환과 같은 틱에 즉시 터졌고, 면역은 "그 자리를 방금 떠난 사람" 하나뿐이었다.
 * 위치 교환은 고정점 없는 완전 순열이라 누군가 방금 비운 자리는 언제나 다른 누군가의 새 자리이기도
 * 하다. 그래서 그 자리로 막 <b>도착한</b> 사람은 면역이 아니었고, 도착과 거의 동시에 그 자리가
 * 터지며 즉사하는 사고가 났다.
 *
 * <p>고친 방법은 둘이다. 폭발을 {@value #DELAY_TICKS}틱 미루고, 그동안 이 교환에 참여한 사람
 * 전원(방금 떠난 사람 + 새로 도착한 사람 + 그 교환에 같이 있던 나머지 팀원)을 면역으로 둔다.
 * 도착한 사람도 이제 이 한 발은 맞지 않는다.
 *
 * <h2>면역은 시간을 따로 추적하지 않는다</h2>
 * <p>면역 대상 집합은 폭발을 예약하는 순간 함께 담아 둔다. {@value #DELAY_TICKS}(0.5초)가
 * "교환 시점부터 1초"라는 면역 창보다 항상 짧으므로, 예약된 폭발은 그 면역 대상들이 아직
 * 면역인 동안 터진다. 즉 이 면역은 <b>그 교환이 만든 폭발들에만</b> 통하고, 다른 원인의 피해나
 * 다른 팀의 폭발 교환에는 영향이 없다.
 */
public final class SwapExplosionScheduler {
	/** 폭발까지 미루는 시간. 0.5초. */
	static final int DELAY_TICKS = 10;

	private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();

	private SwapExplosionScheduler() {
	}

	/** 서버가 멈출 때 예약된 폭발을 모두 지운다. */
	public static void reset() {
		PENDING.clear();
	}

	/** 지금 예약된 채로 기다리는 폭발 수. 시험·진단용. */
	public static int pendingCount() {
		return PENDING.size();
	}

	/**
	 * 폭발 하나를 {@value #DELAY_TICKS}틱 뒤로 예약한다.
	 *
	 * @param origin              터질 자리(비운 자리)
	 * @param immuneParticipants  이 폭발이 터질 때 면역일 사람들(이 교환에 참여한 전원)
	 * @param definition          반경·피해 배율·블록 파괴 여부
	 */
	public static void schedule(PositionSwapManager.Position origin, Set<UUID> immuneParticipants,
			SwapExplosionEffect definition) {
		PENDING.add(new Pending(origin, Set.copyOf(immuneParticipants), definition, DELAY_TICKS));
	}

	/** 예약된 폭발들을 한 틱씩 줄이고, 다 된 것은 터뜨린다. */
	public static void tick(MinecraftServer server) {
		if (server == null || PENDING.isEmpty()) {
			return;
		}
		for (Pending pending : PENDING) {
			if (pending.tick() > 0) {
				continue;
			}
			PENDING.remove(pending);
			try {
				PositionSwapManager.detonateSwapExplosion(
						pending.origin, pending.immune, pending.definition);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("미뤄 둔 폭발 교환을 터뜨리다가 실패했습니다.", error);
			}
		}
	}

	/** 예약된 폭발 하나. 저장하지 않는다 — 서버가 다시 뜨면 처음부터 시작해도 된다. */
	private static final class Pending {
		final PositionSwapManager.Position origin;
		final Set<UUID> immune;
		final SwapExplosionEffect definition;
		int ticksLeft;

		Pending(PositionSwapManager.Position origin, Set<UUID> immune, SwapExplosionEffect definition,
				int ticksLeft) {
			this.origin = origin;
			this.immune = immune;
			this.definition = definition;
			this.ticksLeft = ticksLeft;
		}

		/** 한 틱 줄이고 남은 틱을 돌려준다. */
		int tick() {
			return --ticksLeft;
		}
	}
}
