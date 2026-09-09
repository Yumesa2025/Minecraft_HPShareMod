package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.effect.AuraDamageEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code aura_damage} 증강(프리즘 「살기」)의 실행부. 팀원 주위의 적대적 몹을 초당 한 번 벤다.
 *
 * <p>{@link AuraDamageEffect} 는 「얼마나 넓게, 얼마나 세게, 어디서 빼는가」만 알고, 지금 누가
 * 반경 안에 있고 몇 명이 겹쳤는지는 여기서 센다.
 *
 * <h2>1초에 한 번만 잰다</h2>
 * <p>{@link TeamGathering} 과 같은 방식이다. {@link #tick} 이 불릴 때마다 1씩 올리는 자체
 * 카운터를 두고 {@link #CHECK_INTERVAL_TICKS} 로 나누어떨어질 때만 실제로 훑는다. 「초당 2의
 * 피해」라는 약속 자체가 1초 주기이므로 이보다 자주 돌 이유도 없다.
 *
 * <h2>몹 하나당 한 방만 넣는다 — 이것이 이 클래스의 핵심이다</h2>
 * <p>바닐라는 피격 뒤 무적 시간이 있어서, 네 명이 각자 2씩 때리면 8이 아니라 <b>2</b>만
 * 들어간다. 그래서 팀원마다 따로 때리지 않는다. 먼저 <b>몹마다 반경 안에 있는 팀원 수를 세어</b>
 * 두었다가, 다 세고 난 뒤에 {@link AuraDamageEffect#stackedDamage(int)} 로 구한 합계를
 * <b>한 번에</b> 넣는다. 이 순서를 뒤집으면 「팀원이 모이면 겹친다」는 설계가 통째로 죽는다.
 *
 * <p>같은 이유로 팀이 {@code aura_damage} 를 둘 이상 가졌을 때도 정의마다 따로 때리지 않는다.
 * 모든 정의의 몫을 같은 표에 더해 두었다가 마지막에 한 번만 넣는다.
 *
 * <h2>왜 팀원마다 좁게 훑는가</h2>
 * <p>「팀마다 한 번」이 원칙이지만, 그것을 <b>팀 전체를 감싸는 상자 하나</b>로 읽으면 오히려
 * 느려진다. 팀원 둘이 500칸 떨어져 있으면 그 상자는 500칸짜리가 되고, 엔티티 조회 비용은
 * 상자가 지나는 구역 수에 비례하므로 사이의 빈 땅까지 전부 훑게 된다. 반경 10칸짜리 상자는
 * 어느 팀원 곁이든 구역 몇 개면 끝난다.
 *
 * <p>그래서 조회만 팀원마다 좁게 돌고, <b>세는 표와 실제 타격은 팀마다 한 번</b>이다. 같은
 * 몹이 여러 팀원의 반경에 걸리면 표에서 합쳐지므로 몹이 맞는 횟수는 초당 한 번 그대로다.
 *
 * <h2>피해의 출처를 플레이어로 두는 이유</h2>
 * <p>{@code DamageSources.playerAttack} 을 쓴다. 마법 피해로 두면 몹이 죽어도 <b>전리품도
 * 경험치도 나오지 않는다</b> — 바닐라가 「누가 죽였는가」를 피해 출처에서 찾기 때문이다.
 * 플레이어를 출처로 두면 {@code LivingEntity.hurtServer} 안의
 * {@code resolvePlayerResponsibleForDamage} 가 그 사람을 마지막 가해자로 기록하고, 전리품표가
 * 그 사람을 놓고 굴러가므로 <b>사냥 세트의 약탈 효과</b>({@code always_looting})까지 함께
 * 걸린다. 약탈을 읽는 자리가 {@code EnchantmentHelper.getEnchantmentLevel(Holder, LivingEntity)}
 * 하나이고, 거기 넘어오는 것이 바로 이 플레이어다.
 *
 * <p>여럿이 겹쳤을 때 출처가 되는 사람은 <b>그 몹에 가장 가까운 팀원</b>이다. 전리품 규칙은
 * 팀 단위라 누구를 골라도 결과가 같지만, 흔들리지 않는 기준을 두어야 같은 상황에서 늘 같은
 * 사람이 골라진다.
 *
 * <h2>때리는 대상</h2>
 * <p>{@link Enemy} 인 몹만이다. 소·양·닭·마을 주민·길들인 늑대와 말은 {@code Enemy} 가 아니라
 * 애초에 걸러진다 — 농장과 주민 거래를 살기가 갉아먹으면 안 된다. <b>엔더 드래곤은 뺀다</b>:
 * 드래곤은 회차를 끝내는 조건이라 가만히 서 있는 것만으로 깎이면 안 된다. 가려내는 규칙은
 * {@link DifficultyEscalation#appliesTo} 와 같다.
 */
public final class AuraDamageManager {
	/** 실제로 훑는 주기. 1초다. 「초당 피해」라는 약속과 같은 값이어야 한다. */
	static final int CHECK_INTERVAL_TICKS = 20;

	/** 자체 틱 카운터. {@link #tick} 이 부를 때마다 1씩 오른다. */
	private static volatile long now;

	private static boolean warned;

	private AuraDamageManager() {
	}

	/**
	 * 팀마다 살기가 닿는 몹을 세고 한 번씩 벤다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다. 판정 주기가 아닌
	 * 틱에는 카운터만 올리고 끝난다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		// 증강 선택 중에는 시간이 멈춰 있고 팀원은 창에 갇혀 있다. 그 사이에 몹을 깎을 이유가
		// 없다. 게임 오버 카운트다운 5초도 이미 끝난 회차라 마찬가지다.
		if (server == null || PerkChoiceSession.isActive() || WorldResetCoordinator.countingDown()) {
			return;
		}
		long time = ++now;
		if (time % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		try {
			TeamManager manager = TeamManager.get(server);
			for (ShareTeam team : manager.allTeams()) {
				TeamState state = manager.stateByTeamId(team.teamId());
				if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
					continue;
				}
				List<AuraDamageEffect> auras = aurasOf(state);
				if (auras.isEmpty()) {
					continue;
				}
				tickTeam(server, team, auras);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드로 넘어가지 않게 한다. */
	public static void reset() {
		now = 0;
		warned = false;
	}

	/** 지금까지 센 틱 수. 테스트와 진단용. */
	static long currentTick() {
		return now;
	}

	// ------------------------------------------------------------------ 팀 하나

	/**
	 * 팀 하나의 살기를 한 번 집행한다.
	 *
	 * <p>세기(표 채우기)와 때리기(표 비우기)를 반드시 갈라 둔다. 세는 도중에 때리면 같은 몹이
	 * 여러 번 맞고, 무적 시간에 먹혀 결국 한 명분만 들어간다.
	 */
	private static void tickTeam(MinecraftServer server, ShareTeam team,
			List<AuraDamageEffect> auras) {
		List<ServerPlayer> online = onlineMembers(server, team);
		if (online.isEmpty()) {
			return;
		}
		Map<Mob, Strike> strikes = new LinkedHashMap<>();
		for (AuraDamageEffect aura : auras) {
			collect(online, aura, strikes);
		}
		for (Map.Entry<Mob, Strike> entry : strikes.entrySet()) {
			entry.getValue().deliver(entry.getKey());
		}
	}

	/**
	 * 정의 하나가 닿는 몹들을 표에 더한다.
	 *
	 * <p>차원 제외는 <b>몹이 아니라 팀원이 서 있는 차원</b>으로 본다. 네더에 있는 팀원은 살기를
	 * 아예 뿜지 않고, 같은 순간 오버월드에 있는 팀원은 평소대로 뿜는다.
	 */
	private static void collect(List<ServerPlayer> online, AuraDamageEffect aura,
			Map<Mob, Strike> strikes) {
		double radius = aura.radius();
		double radiusSquared = aura.radiusSquared();
		float perMember = aura.stackedDamage(1);
		for (ServerPlayer member : online) {
			ServerLevel level = member.level();
			if (aura.excludes(level.dimension())) {
				continue;
			}
			Vec3 center = member.position();
			// 상자는 조회를 좁히는 그물일 뿐이다. 실제 판정은 아래의 거리 비교로 하므로
			// 「반경 10칸」은 정육면체가 아니라 공 모양이다.
			AABB box = new AABB(center, center).inflate(radius);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, AuraDamageManager::hostile)) {
				double distanceSquared = mob.distanceToSqr(center);
				if (distanceSquared > radiusSquared) {
					continue;
				}
				strikes.computeIfAbsent(mob, key -> new Strike())
						.add(member, distanceSquared, perMember);
			}
		}
	}

	/**
	 * 이 몹이 살기에 맞는가.
	 *
	 * <p>{@link DifficultyEscalation#appliesTo} 와 같은 규칙에 「살아 있는가」만 더했다. 이미
	 * 죽어 가는 몹을 한 번 더 때려 봐야 전리품 주인만 흔들린다.
	 */
	static boolean hostile(@Nullable Entity entity) {
		if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)) {
			return false;
		}
		// 드래곤은 회차를 끝내는 조건이다. 이 모드가 다른 곳에서도 따로 빼는 하나뿐인 예외다.
		if (mob.getType() == EntityTypes.ENDER_DRAGON) {
			return false;
		}
		return mob.isAlive();
	}

	// ------------------------------------------------------------------ 도우미

	/**
	 * 이 팀이 가진 {@code aura_damage} 정의들. 없으면 빈 목록.
	 *
	 * <p>보유 증강과 <b>켜진 세트 효과</b>를 함께 펼친다. {@code PerkSwapRules} 가 팀에게 효과를
	 * 물을 때 쓰는 규칙 그대로다.
	 */
	private static List<AuraDamageEffect> aurasOf(TeamState state) {
		List<AuraDamageEffect> found = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			// 풀에서 사라진 id 는 건너뛴다. 증강 정의를 손으로 고칠 수 있는 이상 저장에만 남은
			// id 는 언제든 생긴다.
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof AuraDamageEffect aura) {
					found.add(aura);
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof AuraDamageEffect aura) {
				found.add(aura);
			}
		}
		return found;
	}

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> result = new ArrayList<>();
		for (UUID memberId : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(memberId);
			if (player != null && !player.isRemoved() && !player.isDeadOrDying()
					&& !player.isSpectator()) {
				result.add(player);
			}
		}
		return result;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"살기 증강을 처리하지 못해 이번 점검은 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		reset();
	}

	/**
	 * 몹 하나가 이번 초에 받을 몫.
	 *
	 * <p>합계를 다 세기 전에는 아무도 때리지 않는다. 표를 다 채운 뒤 {@link #deliver} 가 딱 한 번
	 * 불린다.
	 */
	private static final class Strike {
		private @Nullable ServerPlayer attacker;
		private double nearestSquared = Double.MAX_VALUE;
		private float damage;

		/** 팀원 한 명의 몫을 더한다. 가장 가까운 사람이 피해의 출처가 된다. */
		void add(ServerPlayer member, double distanceSquared, float perMember) {
			damage += perMember;
			if (distanceSquared < nearestSquared) {
				nearestSquared = distanceSquared;
				attacker = member;
			}
		}

		/** 모아 둔 합계를 한 번에 넣는다. */
		void deliver(Mob mob) {
			ServerPlayer source = attacker;
			if (source == null || damage <= 0.0F || !mob.isAlive()) {
				return;
			}
			// 출처를 플레이어로 두어야 전리품·경험치가 나오고 약탈까지 걸린다.
			DamageSource damageSource = source.damageSources().playerAttack(source);
			mob.hurtServer(source.level(), damageSource, damage);
		}
	}
}
