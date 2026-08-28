package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.ConditionalEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 조건부 증강({@code conditional})의 주기 평가부.
 *
 * <p>다른 효과들은 {@link PerkEffect#apply} 로 한 번 붙이면 끝이지만, 조건부 효과는 허기나
 * 체력처럼 수시로 변하는 값을 본다. 그래서 누군가 주기적으로 "지금도 그 조건이 맞는지"를
 * 물어봐야 한다. 그 역할만 하는 곳이다.
 *
 * <p>매 틱 돌 필요는 없다. 조건이 바뀌었을 때 반 초 안에 따라붙으면 충분하고, 판정이 지난번과
 * 같으면 {@link ConditionalEffect#refresh} 가 아무 일도 하지 않으므로 부담도 거의 없다.
 * 증강을 하나도 갖고 있지 않은 팀은 아예 훑지 않는다.
 *
 * <h2>피해 배율 조회 대상</h2>
 * <p>{@link PerkEffect#damageDealtMultiplier} 에는 플레이어 인자가 없어서, 조건부 효과 혼자서는
 * 누구를 기준으로 판정할지 알 수 없다. 배율을 모으는 자리가 조회를 시작하면서
 * {@link #beginMultiplierLookup} 으로 대상 플레이어를 적어 두고, 조건부 효과가
 * {@link #multiplierContext} 로 그것을 읽는다. 서버 스레드에서만 오가고 조회할 때마다
 * 덮어쓰므로 남아 있는 값이 문제를 일으키지 않는다.
 */
public final class ConditionalPerkManager {
	/** 조건을 다시 보는 주기. 반 초면 체감상 즉시 반응하는 것과 다르지 않다. */
	private static final int CHECK_INTERVAL_TICKS = 10;

	private static int tickCounter;

	/** 지금 피해 배율을 모으고 있는 대상. 없으면 null. */
	private static volatile @Nullable UUID multiplierContext;

	/** 같은 경고로 로그를 채우지 않기 위한 표시. */
	private static volatile boolean warned;

	private ConditionalPerkManager() {
	}

	// ------------------------------------------------------------------ 주기 평가

	/** 접속 중인 플레이어들의 조건을 다시 본다. 바뀐 사람에게만 효과를 갈아 끼운다. */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++tickCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			refreshPlayer(player);
		}
	}

	/**
	 * 한 플레이어가 가진 조건부 효과를 모두 다시 본다.
	 *
	 * <p>팀이 없거나 증강이 꺼져 있거나 보유 증강이 없으면 아무 일도 하지 않는다.
	 * 증강 풀이 비어 있는 서버에서는 이 메서드가 곧바로 되돌아간다.
	 */
	public static void refreshPlayer(@Nullable ServerPlayer player) {
		if (player == null) {
			return;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return;
		}
		for (PerkStack stack : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(stack.perkId()).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (!(effect instanceof ConditionalEffect conditional)) {
					continue;
				}
				try {
					conditional.refresh(player, stack.count());
				} catch (RuntimeException error) {
					warnOnce(perk.id(), error);
				}
			}
		}
	}

	// ------------------------------------------------------------------ 피해 배율 문맥

	/** 이제부터 이 플레이어를 기준으로 배율을 모은다고 알린다. */
	public static void beginMultiplierLookup(@Nullable ServerPlayer player) {
		multiplierContext = player == null ? null : player.getUUID();
	}

	/** 지금 배율 조회의 대상. 알 수 없으면 null. */
	public static @Nullable UUID multiplierContext() {
		return multiplierContext;
	}

	// ------------------------------------------------------------------ 정리

	/** 서버가 멈출 때 주기 상태와 효과마다 기억해 둔 판정을 비운다. */
	public static void reset() {
		tickCounter = 0;
		multiplierContext = null;
		warned = false;
		for (Perk perk : PerkRegistry.all()) {
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof ConditionalEffect conditional) {
					conditional.forgetAll();
				}
			}
		}
	}

	private static void warnOnce(String perkId, RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn("조건부 증강 '{}' 을(를) 다시 보다가 실패했습니다.", perkId, error);
	}
}
