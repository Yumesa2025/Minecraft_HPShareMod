package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.PairedMiningEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code paired_mining} 증강(실버 「공명」)의 실행부.
 *
 * <p>{@link PairedMiningEffect}는 값(기억 유효 시간·성급함 세기와 길이)만 들고 있고,
 * "누가 언제 무엇을 캤는가"는 전부 여기서 정한다.
 *
 * <h2>하는 일은 하나뿐이다</h2>
 * <p>{@link #onBreak}가 {@code PerkBlockBreaks}에서 캘 때마다 불린다. 플레이어별로 "마지막으로
 * 캔 블록·시각"만 기억해 뒀다가, 팀원 중 누군가가 5초 안에 같은 블록을 캤으면 둘 다 성급함 III
 * 을 5초 건다.
 *
 * <p><b>거리도 차원도 보지 않는다.</b>
 *
 * <h2>기억은 저장하지 않는다</h2>
 * <p>"마지막으로 캤다"는 서버가 켜져 있는 동안만 뜻이 있는 값이다.
 * 저장하지 않고, 서버가 멈추면 {@link #reset}이 비운다.
 */
public final class PerkResonantMining {
	/** 플레이어별 마지막으로 캔 블록 기록. */
	private static final Map<UUID, LastBreak> LAST_BREAKS = new ConcurrentHashMap<>();

	private static boolean warned;

	private PerkResonantMining() {
	}

	record LastBreak(Block block, long tick) {
	}

	// ------------------------------------------------------------------ 짝 성사

	/** {@code PerkBlockBreaks}가 캘 때마다 부르는 지점. */
	public static void onBreak(MinecraftServer server, ServerPlayer breaker, BlockState state, long gameTime) {
		try {
			handleOnBreak(server, breaker, state, gameTime);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void handleOnBreak(MinecraftServer server, ServerPlayer breaker, BlockState state,
			long gameTime) {
		Block block = state.getBlock();
		UUID breakerId = breaker.getUUID();

		ShareTeam team = TeamManager.get(server).teamOf(breakerId);
		if (team != null) {
			for (UUID memberId : team.members()) {
				if (memberId.equals(breakerId)) {
					continue;
				}
				if (!recordMatches(LAST_BREAKS.get(memberId), block, gameTime)) {
					continue;
				}
				ServerPlayer teammate = server.getPlayerList().getPlayer(memberId);
				if (teammate == null || teammate.isRemoved()) {
					// 기록만 남기고 나간 사람이다. 성급함을 걸어 줄 대상이 없으니 캔 사람만 받는다.
					grantHaste(breaker);
					break;
				}
				grantHaste(breaker);
				grantHaste(teammate);
				break; // 한 쌍만 성사시키면 이 사건의 뜻은 충분하다.
			}
		}

		LAST_BREAKS.put(breakerId, new LastBreak(block, gameTime));
	}

	/**
	 * 최근 캔 기록이 지금 캔 블록과 짝을 이룰 수 있는가.
	 *
	 * @param record    상대방의 마지막 채굴 기록. 없으면(아직 아무것도 안 캤으면) 거짓
	 * @param block     지금 이 사람이 캔 블록
	 * @param gameTime  지금 시각(틱)
	 */
	static boolean recordMatches(@Nullable LastBreak record, Block block, long gameTime) {
		if (record == null || record.block() != block) {
			return false;
		}
		return gameTime - record.tick() <= PairedMiningEffect.MEMORY_TICKS;
	}

	private static void grantHaste(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.HASTE,
				PairedMiningEffect.HASTE_TICKS, PairedMiningEffect.HASTE_AMPLIFIER, false, false, true));
	}

	// ------------------------------------------------------------------ 정리

	/** 서버가 멈출 때 기억을 비운다. */
	public static void reset() {
		LAST_BREAKS.clear();
		warned = false;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"공명 증강을 처리하지 못해 이번엔 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}
}
