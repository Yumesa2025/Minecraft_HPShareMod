package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 사람을 <b>월드 스폰</b>으로 돌려보낸다.
 *
 * <h2>같은 일을 하는 자리가 셋이라 한곳에 모았다</h2>
 * <p>회차 시작({@code GameStartManager}) · 팀 해체 · 운영자 초기화가 모두 「빈손이 된 사람을
 * 처음 자리로 돌려놓는다」를 한다. 갈라 두면 한쪽만 고쳐져서, 어떤 길로 빈손이 되었느냐에 따라
 * 네더 한복판에 남는 사람이 생긴다.
 *
 * <h2>왜 스폰인가 — 침대가 아니라</h2>
 * <p>이 셋은 전부 <b>아이템도 경험치도 사라지는</b> 사건이다. 그 상태로 있던 자리에 두면 가장
 * 나쁜 경우 네더나 엔드에 빈손으로 갇힌다. 개인 침대로 보내면 「침대를 놓은 사람만」 살아남는
 * 차별이 생기고, 침대가 부서졌으면 어차피 스폰이다. 셋 다 「처음부터 다시」라는 뜻이므로
 * 처음 자리가 맞다.
 *
 * <h2>옮기지 못해도 하던 일은 계속한다</h2>
 * <p>여기서 예외를 올려보내면 <b>팀은 해체됐는데 절차가 중간에 멈추는</b> 상태가 된다. 그것이
 * 훨씬 나쁘다. 못 옮긴 사람은 로그에 남기고 넘어간다.
 */
public final class SpawnReturn {

	private SpawnReturn() {
	}

	/**
	 * 접속 중인 사람들을 월드 스폰으로 옮긴다.
	 *
	 * @param reason 로그에 적을 까닭. 「회차 시작」처럼 사람이 읽을 말
	 */
	public static void send(@Nullable MinecraftServer server,
			@Nullable Collection<ServerPlayer> players, String reason) {
		if (server == null || players == null || players.isEmpty()) {
			return;
		}
		try {
			ServerLevel overworld = server.overworld();
			LevelData.RespawnData spawn = overworld.getRespawnData();
			BlockPos pos = spawn.pos();
			double x = pos.getX() + 0.5;
			double y = pos.getY();
			double z = pos.getZ() + 0.5;
			for (ServerPlayer player : players) {
				if (player == null) {
					continue;
				}
				if (!player.teleportTo(overworld, x, y, z, Set.<Relative>of(),
						spawn.yaw(), spawn.pitch(), true)) {
					SharedFateMod.LOGGER.warn("{} 때 {} 를 스폰으로 옮기지 못했습니다.",
							reason, player.getPlainTextName());
				}
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("{} 때 스폰으로 옮기지 못했습니다.", reason, error);
		}
	}

	/** 한 사람만 옮기는 짧은 길. 접속 훅이 쓴다. */
	public static void send(@Nullable MinecraftServer server, @Nullable ServerPlayer player,
			String reason) {
		if (player != null) {
			send(server, List.of(player), reason);
		}
	}
}
