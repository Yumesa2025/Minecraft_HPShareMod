package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.OnCriticalEffect;
import com.sharedfate.perk.effect.OnTeamHurtEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 사건이 일어났을 때 잠깐 효과를 얹는 증강들의 실행부.
 *
 * <p>{@code on_team_hurt} 와 {@code on_critical} 이 여기를 지난다. 각 효과 클래스는 "무엇을
 * 얼마 동안 얹는가"만 들고 있고, "언제 누구에게"는 전부 여기서 정한다. {@code on_kill} 과
 * {@link PerkKillRewards} 의 관계와 같은 구도다.
 *
 * <h2>공유 체력과의 관계</h2>
 * <p>여기서 얹는 것은 상태이상과 속성 수정자뿐이라 체력·허기 공유 풀을 직접 건드리지 않는다.
 * 팀원 넷에게 저항을 걸어도 늘어나는 것은 각자의 저항이지 공유 풀이 아니므로, 최근에 고친
 * "공유 상태이상이 팀 인원수만큼 배수로 들어가던" 문제와 같은 함정에 빠지지 않는다.
 *
 * <p>다만 여기서 회복이나 피해를 주는 상태이상(재생·독 등)을 얹으면 그때부터는 공유 상태이상의
 * 문제가 된다. 그 경우에도 {@code SharedEffectDamage} 가 상태이상 틱 구간에서 대표 한 명 것만
 * 남기므로 1인분으로 유지된다. 여기서 따로 할 일은 없다.
 *
 * <h2>증강이 없으면 바닐라 그대로</h2>
 * <p>두 진입점 모두 팀 → 증강 사용 여부 → 보유 증강 순으로 먼저 걸러 낸다. 팀에 속하지 않은
 * 플레이어와 증강을 쓰지 않는 팀은 첫 몇 줄에서 되돌아 나가므로 피해·공격 경로에 사실상
 * 아무 부담도 얹히지 않는다.
 */
public final class PerkTriggers {
	private static volatile boolean warned;

	private PerkTriggers() {
	}

	// ------------------------------------------------------------------ 팀원 피격

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DAMAGE} 에 붙는 지점.
	 *
	 * <p>피해가 들어가는 모든 자리를 지나므로 어떤 예외도 밖으로 내보내지 않는다.
	 *
	 * <p>{@code SharedHurtFeedback} 이 같은 이벤트에서 피격 연출을 팀에 뿌리는 것과 짝을 이룬다.
	 * 그쪽이 "누가 맞았는지 보여 주는" 일이라면 여기는 "누가 맞았을 때 무엇이 걸리는지"다.
	 */
	public static void onDamage(LivingEntity victim, DamageSource source,
			float baseDamageTaken, float damageTaken, boolean blocked) {
		try {
			teamHurt(victim, damageTaken, blocked);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void teamHurt(LivingEntity victim, float damageTaken, boolean blocked) {
		if (blocked || !(damageTaken > 0.0F) || !(victim instanceof ServerPlayer hurt)) {
			return;
		}
		MinecraftServer server = hurt.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(hurt.getUUID());
		TeamState state = manager.stateOf(hurt.getUUID());
		if (team == null || state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return;
		}

		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof OnTeamHurtEffect onHurt) {
					grantToTeam(server, team, hurt, onHurt);
				}
			}
		}
	}

	/**
	 * 접속해 있고 살아 있는 팀원 전원에게 얹는다.
	 *
	 * <p>맞은 본인을 포함할지는 정의가 정한다. 기본은 포함이다. 체력을 공유하므로 한 명이
	 * 맞으면 팀 전체의 체력이 깎이고, 그러면 맞은 본인에게도 "팀원이 맞았다"가 일어난 것이다.
	 */
	private static void grantToTeam(MinecraftServer server, ShareTeam team, ServerPlayer hurt,
			OnTeamHurtEffect effect) {
		for (UUID member : team.members()) {
			if (!effect.includesVictim() && member.equals(hurt.getUUID())) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null && !online.isRemoved() && !online.isDeadOrDying()) {
				effect.grantTo(online);
			}
		}
	}

	// ------------------------------------------------------------------ 치명타

	/**
	 * {@link com.sharedfate.mixin.ServerPlayerCritMixin} 이 부르는 지점.
	 *
	 * <p>치명타로 실제 피해를 입힌 순간이다. 때린 본인에게만 얹는다. 자세한 이유는
	 * {@link OnCriticalEffect} 에 적어 뒀다.
	 */
	public static void onCriticalHit(@Nullable ServerPlayer attacker) {
		try {
			critical(attacker);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void critical(@Nullable ServerPlayer attacker) {
		if (attacker == null || attacker.isRemoved()) {
			return;
		}
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(attacker.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof OnCriticalEffect onCritical) {
					onCritical.grantTo(attacker);
				}
			}
		}
	}

	// ------------------------------------------------------------------ 도우미

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"방아쇠형 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		warned = false;
	}
}
