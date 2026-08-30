package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sharedfate.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 팀원의 바닐라 사망 메시지를 막는다.
 *
 * <h2>왜 막나</h2>
 * <p>팀이 전멸하면 {@link com.sharedfate.sync.DeathHandler} 가 팀원 전원을 죽이므로 사망
 * 메시지가 <b>인원수만큼</b> 채팅에 쌓인다. 어느 설정에서도 읽을 것이 못 된다. 그래서
 * 채팅은 언제나 막고, "누가 죽었는가"는 사망 알림을 켠 팀에만 게임 오버 화면에 한 줄로
 * 보여 준다. 대신 「'X' 팀이 전멸했습니다」 안내는 그대로 나간다.
 *
 * <h2>왜 이 자리인가</h2>
 * <p>{@code ServerPlayer#die} 는 맨 앞에서 {@code SHOW_DEATH_MESSAGES} 를 한 번 읽고,
 * 그 값 하나가 두 가지를 함께 정한다. 참이면 사인 문구를 만들어 죽은 본인에게
 * {@code ClientboundPlayerCombatKillPacket} 으로 보내고 채팅에도 방송한다. 거짓이면 빈
 * 문구를 실은 같은 패킷만 보낸다. <b>이 한 호출만 가로채면 채팅과 사망 화면 사인 줄이
 * 함께 꺼진다.</b>
 *
 * <p>{@code die} 안에서 {@code GameRules#get} 은 두 번 불린다. 뒤쪽은
 * {@code FORGIVE_DEAD_PLAYERS} 라 건드리면 안 되므로 {@code ordinal = 0} 으로 첫 호출만
 * 잡는다.
 *
 * <p>게임룰 자체를 잠깐 껐다 되돌리는 방법은 쓰지 않는다. 게임룰은 {@code level.dat} 에
 * 저장되므로 그 사이에 서버가 죽으면 꺼진 채로 남는다.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMessageMixin {
	@WrapOperation(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/gamerules/GameRules;"
							+ "get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;",
					ordinal = 0))
	private Object sharedfate$hideTeamDeathMessage(GameRules rules, GameRule<?> rule,
			Operation<Object> original) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		MinecraftServer server = self.level().getServer();
		if (server != null && TeamManager.get(server).teamOf(self.getUUID()) != null) {
			return Boolean.FALSE;
		}
		return original.call(rules, rule);
	}
}
