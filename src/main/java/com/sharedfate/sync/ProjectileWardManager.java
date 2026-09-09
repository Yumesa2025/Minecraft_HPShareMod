package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.effect.ProjectileWardEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code projectile_ward} 증강(골드 「방패벽」)의 실행부. 뭉쳐 있는 팀에게 날아오는 몹의
 * 투사체를 팀원 곁에서 지운다.
 *
 * <p>{@link ProjectileWardEffect} 는 「얼마나 뭉쳐야 하고 얼마나 넓게 지우는가」만 알고, 지금
 * 누가 뭉쳐 있고 무엇이 날아오고 있는지는 여기서 본다. 뼈대는 {@link AuraDamageManager} 와
 * 같다 — 서버 틱에서 팀원 주위를 훑어 처리하고, 어떤 예외도 밖으로 내보내지 않는다.
 *
 * <h2>일을 둘로 갈라 두었다 — 이것이 이 클래스의 핵심이다</h2>
 * <p>「누가 벽을 켜고 있는가」와 「무엇을 지울 것인가」는 알맞은 주기가 서로 다르다.
 *
 * <ul>
 *   <li><b>켜짐 판정은 1초에 한 번</b>({@link #REFRESH_INTERVAL_TICKS}). 이 판정이 기대는
 *       {@link TeamProximity} 자체가 1초마다 한 번만 거리를 재므로, 더 자주 물어봐야 같은 답이
 *       돌아온다. 증강 보유 목록을 펼치는 일도 여기서만 한다.</li>
 *   <li><b>지우는 일은 두 틱에 한 번</b>({@link #SWEEP_INTERVAL_TICKS}). 투사체는 빠르다.
 *       1초에 한 번만 보면 화살이 반경을 통째로 지나쳐 팀원에게 꽂힌 뒤에야 훑게 된다. 반대로
 *       매 틱 도는 것은 얻는 것에 비해 조회가 잦다 — 몹이 쏘는 것 가운데 가장 빠른 축인
 *       스켈레톤 화살도 두 틱이면 반경 8칸을 넘지 못한다.</li>
 * </ul>
 *
 * <p>가른 덕분에 <b>이 증강을 아무도 안 가진 서버는 {@link #ACTIVE} 가 늘 비어 있어 곧바로
 * 빠져나간다.</b> 팀 목록도 증강 목록도 펼치지 않는다.
 *
 * <h2>지우는 것과 지우지 않는 것</h2>
 * <p>가려내는 규칙은 {@link #shotByMob} 하나에 모아 두었다.
 *
 * <ul>
 *   <li><b>쏜 주체가 몹인 것만</b> 지운다. 팀원이 쏜 화살·삼지창은 그대로 날아가야 한다.
 *       주인이 없는 것(발사기가 쏜 화살)도 몹이 쏜 것이 아니므로 그대로 둔다.</li>
 *   <li><b>엔더 드래곤은 뺀다.</b> 드래곤은 회차를 끝내는 조건이라, 뭉쳐 서 있는 것만으로
 *       숨결과 화염구가 지워지면 마지막 싸움이 사라진다. {@link AuraDamageManager#hostile} 이
 *       드래곤을 빼는 것과 같은 이유다.</li>
 *   <li><b>멈춰 있는 것은 지우지 않는다.</b> 땅이나 벽에 박힌 화살은 날아오는 위협이 아니라
 *       주워 쓸 수 있는 물건이다. 지나갈 때마다 발밑의 화살이 연기가 되어 사라지면 벽이 켜진
 *       것이 아니라 고장 난 것으로 보인다.</li>
 * </ul>
 *
 * <h2>왜 흔적을 남기는가</h2>
 * <p>{@link #erase} 가 연기와 작은 소리를 낸다. 아무 표시가 없으면 화살이 왜 안 오는지 알 수
 * 없어, 증강이 걸린 것인지 몹이 안 쏘는 것인지 구분할 방법이 없다.
 *
 * <h2>왜 Mixin 이 아닌가</h2>
 * <p>투사체가 태어나는 자리를 가로채면 「쏜 순간」에 팀이 뭉쳐 있는지만 보게 된다. 이 증강은
 * <b>지금 뭉쳐 있는 동안</b> 서는 벽이라, 쏜 뒤에 뭉쳐도 막아야 하고 쏜 뒤에 흩어지면 뚫려야
 * 한다. 날아오는 도중을 보아야 하므로 주기적으로 훑는 쪽이 규칙과 그대로 맞는다.
 */
public final class ProjectileWardManager {
	/** 켜짐 판정을 다시 하는 주기. 1초다. {@link TeamProximity} 의 갱신 주기와 같은 값이다. */
	static final int REFRESH_INTERVAL_TICKS = 20;

	/** 실제로 투사체를 훑는 주기. 두 틱이다. */
	static final int SWEEP_INTERVAL_TICKS = 2;

	/**
	 * 멈춘 것으로 보는 속도의 제곱((칸/틱)²).
	 *
	 * <p>0.01칸/틱이면 한 칸 가는 데 100틱이다. 그 아래는 어느 것도 「날아오고 있다」고 할 수
	 * 없다. 땅에 박힌 화살은 바닐라가 속도를 0으로 만들어 두므로 여기에 걸린다.
	 */
	private static final double MIN_SPEED_SQUARED = 1.0e-4;

	/** 지워질 때 뿌리는 연기 알갱이 수와 퍼지는 폭. 자리를 알아볼 만큼만 작게 낸다. */
	private static final int PARTICLE_COUNT = 6;
	private static final double PARTICLE_SPREAD = 0.15;

	/** 지워질 때 나는 소리의 크기와 음높이. 화살이 잦게 오면 계속 나므로 작고 짧게 둔다. */
	private static final float SOUND_VOLUME = 0.3F;
	private static final float SOUND_PITCH = 1.6F;

	/**
	 * 지금 벽을 켜고 있는 팀. 팀 id → 지우는 반경(칸).
	 *
	 * <p>{@link #refresh} 가 1초마다 통째로 다시 채운다. 비어 있으면 훑을 팀이 하나도 없다는
	 * 뜻이라 {@link #tick} 이 곧바로 빠져나간다.
	 */
	private static final Map<UUID, Double> ACTIVE = new ConcurrentHashMap<>();

	/** 자체 틱 카운터. {@link #tick} 이 부를 때마다 1씩 오른다. */
	private static volatile long now;

	private static boolean warned;

	private ProjectileWardManager() {
	}

	/**
	 * 벽을 켜고 있는 팀을 다시 재고, 켜져 있는 팀 곁의 투사체를 지운다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		// 증강 선택 중에는 시간이 멈춰 있고 팀원은 창에 갇혀 있다. 게임 오버 카운트다운 5초도
		// 이미 끝난 회차다. AuraDamageManager 와 같은 판단이다.
		if (server == null || PerkChoiceSession.isActive() || WorldResetCoordinator.countingDown()) {
			return;
		}
		long time = ++now;
		if (time % REFRESH_INTERVAL_TICKS == 0) {
			refreshSafely(server);
		}
		// 이 증강을 아무도 안 가졌거나 아무도 뭉쳐 있지 않으면 여기서 끝난다. 팀 목록조차 펼치지
		// 않는 자리가 이곳이다.
		if (ACTIVE.isEmpty() || time % SWEEP_INTERVAL_TICKS != 0) {
			return;
		}
		sweepSafely(server);
		// 벽이 서 있는 것을 눈에 보이게 한다. 화살이 안 날아오는 것이 막힌 것인지 애초에 안
		// 쏜 것인지 알 방법이 없었다.
		if (time % RING_INTERVAL_TICKS == 0) {
			drawRingsSafely(server);
		}
	}

	/** 고리를 그리는 주기(틱). 0.5초다. 파티클이 그보다 오래 남지 않아 끊겨 보이면 안 된다. */
	private static final int RING_INTERVAL_TICKS = 10;

	private static void drawRingsSafely(MinecraftServer server) {
		try {
			for (ShareTeam team : TeamManager.get(server).allTeams()) {
				Double radius = ACTIVE.get(team.teamId());
				if (radius == null) {
					continue;
				}
				for (UUID memberId : team.members()) {
					ServerPlayer member = server.getPlayerList().getPlayer(memberId);
					// 사람마다 자기 둘레에 고리가 선다. 판정도 사람마다 반경을 재므로 보이는
					// 것과 실제로 막히는 범위가 정확히 같다.
					AuraRing.draw(member, radius, ParticleTypes.ELECTRIC_SPARK, now);
				}
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드로 넘어가지 않게 한다. */
	public static void reset() {
		ACTIVE.clear();
		now = 0;
		warned = false;
	}

	/** 이 팀이 지금 켜고 있는 벽의 반경(칸). 안 켜져 있으면 0. 시험과 진단용이다. */
	static double activeRadiusOf(@Nullable UUID teamId) {
		if (teamId == null) {
			return 0.0;
		}
		Double radius = ACTIVE.get(teamId);
		return radius == null ? 0.0 : radius;
	}

	// ------------------------------------------------------------------ 켜짐 판정

	private static void refreshSafely(MinecraftServer server) {
		try {
			refresh(server);
		} catch (RuntimeException error) {
			// 다시 재지 못했으면 지난 답을 그대로 쓰기보다 끄는 쪽이 안전하다. 없는 벽이 잠깐
			// 서는 것보다 있는 벽이 1초 늦게 서는 쪽이 덜 이상하다 — TeamProximity 와 같은 결이다.
			ACTIVE.clear();
			warnOnce(error);
		}
	}

	/**
	 * 어느 팀이 지금 벽을 켜고 있고 반경이 얼마인지 다시 잰다.
	 *
	 * <p>{@link #ACTIVE} 를 통째로 비우고 다시 채운다. 팀이 사라지거나 증강을 잃었을 때 지난
	 * 답이 남지 않게 하려는 것이다.
	 */
	private static void refresh(MinecraftServer server) {
		ACTIVE.clear();
		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
				continue;
			}
			List<ProjectileWardEffect> wards = wardsOf(state);
			if (wards.isEmpty()) {
				continue;
			}
			double radius = activeRadius(team.teamId(), wards);
			if (radius > 0.0) {
				ACTIVE.put(team.teamId(), radius);
			}
		}
	}

	/**
	 * 이 팀이 지금 켜고 있는 벽의 반경. 하나도 안 켜져 있으면 0.
	 *
	 * <p>정의마다 뭉침 거리가 달라 <b>켜짐 판정을 정의마다 따로</b> 한다. 조건을 채운 정의들
	 * 가운데 <b>가장 넓은 반경</b>이 이긴다 — 증강을 하나 더 얻은 것이 앞의 것을 깎아 먹으면
	 * 안 된다.
	 *
	 * <p>{@code distance} 도 {@code radius} 도 정의에 적힌 값 그대로 넘긴다. 세트 「결속 3」의
	 * 배율은 {@link TeamProximity} 가 양쪽 모두에 먹인다.
	 */
	private static double activeRadius(UUID teamId, List<ProjectileWardEffect> wards) {
		double widest = 0.0;
		for (ProjectileWardEffect ward : wards) {
			if (!TeamProximity.together(teamId, ward.distance())) {
				continue;
			}
			widest = Math.max(widest, TeamProximity.scaled(teamId, ward.radius()));
		}
		return widest;
	}

	/**
	 * 이 팀이 가진 {@code projectile_ward} 정의들. 없으면 빈 목록.
	 *
	 * <p>보유 증강과 <b>켜진 세트 효과</b>를 함께 펼친다. {@link AuraDamageManager} 가 살기를
	 * 모으는 방식 그대로다. 풀에서 사라진 id 는 건너뛴다 — 증강 정의를 손으로 고칠 수 있는 이상
	 * 저장에만 남은 id 는 언제든 생긴다.
	 */
	private static List<ProjectileWardEffect> wardsOf(TeamState state) {
		List<ProjectileWardEffect> found = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			found.addAll(ProjectileWardEffect.wardsOf(perk.effects()));
		}
		found.addAll(ProjectileWardEffect.wardsOf(PerkSetEffects.activeEffectsOf(state)));
		return found;
	}

	// ------------------------------------------------------------------ 지우기

	private static void sweepSafely(MinecraftServer server) {
		try {
			sweep(server);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void sweep(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : manager.allTeams()) {
			Double radius = ACTIVE.get(team.teamId());
			if (radius == null) {
				continue;
			}
			sweepTeam(server, team, radius);
		}
	}

	/**
	 * 팀원 한 명씩 곁을 훑어 몹이 쏜 투사체를 지운다.
	 *
	 * <p>팀 전체를 감싸는 상자 하나로 훑지 않는 이유는 {@link AuraDamageManager} 와 같다. 뭉침
	 * 조건 덕분에 팀원이 크게 벌어질 수는 없지만, 조회 비용은 상자가 지나는 구역 수에 비례하므로
	 * 좁은 상자 몇 개가 큰 상자 하나보다 언제나 싸다.
	 *
	 * <p>두 사람의 반경이 겹쳐 같은 투사체가 두 번 걸릴 수 있다. 먼저 지워진 것은
	 * {@link #shotByMob} 이 걸러 내고 {@link #erase} 가 한 번 더 확인하므로 흔적이 겹쳐 나오지
	 * 않는다.
	 */
	private static void sweepTeam(MinecraftServer server, ShareTeam team, double radius) {
		double radiusSquared = radius * radius;
		for (ServerPlayer member : onlineMembers(server, team)) {
			ServerLevel level = member.level();
			Vec3 center = member.position();
			// 상자는 조회를 좁히는 그물일 뿐이다. 실제 판정은 아래의 거리 비교로 하므로 「반경
			// 8칸」은 정육면체가 아니라 공 모양이다.
			AABB box = new AABB(center, center).inflate(radius);
			for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box,
					ProjectileWardManager::shotByMob)) {
				if (projectile.distanceToSqr(center) > radiusSquared) {
					continue;
				}
				erase(level, projectile);
			}
		}
	}

	/**
	 * 이 투사체를 지워도 되는가.
	 *
	 * <p>가려내는 규칙 전부가 여기에 있다. 클래스 설명의 「지우는 것과 지우지 않는 것」이 이
	 * 메서드를 풀어 쓴 것이다.
	 */
	static boolean shotByMob(@Nullable Projectile projectile) {
		if (projectile == null || projectile.isRemoved()) {
			return false;
		}
		// 땅에 박혀 멈춘 화살은 날아오는 위협이 아니라 주울 수 있는 물건이다.
		if (projectile.getDeltaMovement().lengthSqr() < MIN_SPEED_SQUARED) {
			return false;
		}
		// 주인이 몹이 아니면 그대로 날려 보낸다. 팀원이 쏜 화살·삼지창과 주인이 없는 발사기
		// 화살이 여기서 걸러진다.
		Entity owner = projectile.getOwner();
		if (!(owner instanceof Mob)) {
			return false;
		}
		// 드래곤은 회차를 끝내는 조건이다. 이 모드가 다른 곳에서도 따로 빼는 하나뿐인 예외다.
		return owner.getType() != EntityTypes.ENDER_DRAGON;
	}

	/**
	 * 투사체 하나를 지우고 흔적을 남긴다.
	 *
	 * <p>자리를 먼저 읽어 둔 뒤에 지운다. {@code discard()} 뒤의 좌표는 믿을 것이 못 된다.
	 */
	private static void erase(ServerLevel level, Projectile projectile) {
		if (projectile.isRemoved()) {
			return;
		}
		double x = projectile.getX();
		double y = projectile.getY();
		double z = projectile.getZ();
		projectile.discard();
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, PARTICLE_COUNT,
				PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD, 0.0);
		level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
				SOUND_VOLUME, SOUND_PITCH);
	}

	// ------------------------------------------------------------------ 도우미

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
				"방패벽 증강을 처리하지 못해 이번 점검은 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}
}
