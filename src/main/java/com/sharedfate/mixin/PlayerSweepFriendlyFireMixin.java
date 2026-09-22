package com.sharedfate.mixin;

import com.sharedfate.perk.effect.NoSweepFriendlyFireEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 「휩쓸기」의 팀원 면제 — 옆으로 퍼지는 몫만 팀원을 비껴간다.
 *
 * <h2>왜 여기인가</h2>
 * <p>26.3 의 {@code Player.attack} 은 겨냥한 대상을 때린 뒤
 * {@code doSweepAttack(Entity, float, DamageSource, float)} 를 부르고, 그 안에서 주변
 * {@code LivingEntity} 를 훑어 하나씩
 * {@code LivingEntity.hurtServer(ServerLevel, DamageSource, float)} 를 부른다. 겨냥한 대상에게
 * 쓰는 피해원과 휩쓸기가 쓰는 피해원은 <b>같은 객체</b>다 — {@code attack} 이 만든 것을 그대로
 * 넘긴다. 그래서 "이 피해가 휩쓸기 몫인가"는 피해원으로는 절대 알 수 없고,
 * <b>휩쓸기 고리 안에서만</b> 알 수 있다.
 *
 * <p>고리 안의 {@code hurtServer} 호출을 가로채 거짓을 돌려주면 바닐라가 스스로 나머지를
 * 건너뛴다. 그 자리의 반환값은 곧바로 분기 조건이라, 거짓이면 밀치기도
 * {@code EnchantmentHelper.doPostAttackEffects} 도 돌지 않는다. 곧 <b>그 사람은 휩쓸기에
 * 걸리지 않은 것과 똑같아진다.</b> 겨냥해서 때리는 쪽은 {@code attack} 본문의
 * {@code hurtOrSimulate} 라 여기와 아무 상관이 없다 — 팀원을 직접 치면 그대로 아프다.
 *
 * <h2>대상 고르기</h2>
 * <p>{@code doSweepAttack} 은 {@code Player} 에 있는 <b>private</b> 메서드이고
 * {@code ServerPlayer} 도 {@code Avatar} 도 이것을 재정의하지 않는다(재정의할 수도 없다).
 * 부르는 곳도 {@code Player.attack} 한 곳뿐이라 {@code Player} 에 걸면 서버 플레이어의
 * 휩쓸기가 빠짐없이 지나간다.
 *
 * <p>또 {@code doSweepAttack} 은 {@code ServerLevel} 이 아니면 첫머리에서 되돌아가므로 이
 * 자리는 언제나 서버 쪽이다. 팀 조회를 마음 놓고 해도 된다.
 *
 * <h2>싸게 끝난다</h2>
 * <p>휩쓸기에 걸리는 것은 거의 언제나 몹이다. 사람이 아니면 {@code instanceof} 한 번으로
 * 끝나고, 팀이 없는 서버에서는 팀 조회 한 번이 더 붙을 뿐이다. 증강 목록을 훑는 데까지
 * 가는 것은 같은 팀 사람이 실제로 휩쓸기 범위에 서 있을 때뿐이다.
 */
@Mixin(Player.class)
public abstract class PlayerSweepFriendlyFireMixin {

	@Redirect(
			method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;F"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;"
							+ "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
							+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			)
	)
	private boolean sharedfate$sparePartnerFromSweep(LivingEntity victim, ServerLevel level,
			DamageSource source, float amount) {
		if (sharedfate$sweepBlocked(victim)) {
			// 거짓 = "안 맞았다". 바닐라가 밀치기와 마법부여 후처리까지 알아서 건너뛴다.
			return false;
		}
		return victim.hurtServer(level, source, amount);
	}

	/** 이 대상이 내 팀 사람이고, 내 팀이 휩쓸기 면제를 가졌는가. */
	private boolean sharedfate$sweepBlocked(LivingEntity victim) {
		if (!(victim instanceof Player)) {
			// 팀에 들 수 있는 것은 사람뿐이다. 몹이면 여기서 끝난다.
			return false;
		}
		Player self = (Player) (Object) this;
		MinecraftServer server = self.level().getServer();
		if (server == null) {
			return false;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(self.getUUID());
		if (team == null) {
			return false;
		}
		return NoSweepFriendlyFireEffect.blocksSweep(team, manager.stateOf(self.getUUID()),
				victim.getUUID());
	}
}
