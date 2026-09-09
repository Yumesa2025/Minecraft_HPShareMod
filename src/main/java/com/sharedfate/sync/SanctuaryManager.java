package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.effect.SanctuaryEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「이 몹이 지금 느려져야 하는가(혹은 빨라져야 하는가)」를 답하는 곳. 프리즘 「성역」의 실행부다.
 *
 * <p>{@link SanctuaryEffect} 는 「얼마나 가까워야 하고, 얼마나 넓고, 얼마나 느려지는가」만 알고,
 * 지금 팀이 뭉쳐 있는지와 어떤 몹이 반경 안에 있는지는 여기서 센다. 실제로 틱을 건너뛰는 자리는
 * {@code ServerLevelSanctuaryTickMixin} 이다.
 *
 * <h2>어떻게 가볍게 만들었는가 — 이 클래스의 핵심이다</h2>
 * <p>이 클래스의 물음은 <b>모든 몹이 매 틱 지나는 자리</b>에서 온다. 몹 200마리짜리 서버면
 * 초당 4000번이다. 그래서 세 겹으로 막았다.
 *
 * <ol>
 *   <li><b>깃발 하나.</b> {@link #active} 는 성역을 가진 팀이 하나라도 있을 때만 참이다.
 *       아무도 이 증강을 갖지 않은 서버에서 물음의 비용은 <b>{@code volatile boolean} 한 번
 *       읽기</b>가 전부다. 다른 모든 계산은 이 줄 아래에 있다.</li>
 *   <li><b>{@value #REFRESH_INTERVAL_TICKS} 틱마다 한 번만 팀을 훑는다.</b> 몹마다 팀 전체를
 *       훑으면 안 된다. 대신 팀을 훑는 일은 {@link #tick} 에서 0.5초에 한 번 하고, 그 결과를
 *       <b>차원별 구역 목록</b>({@link Zones})으로 굳혀 둔다. 몹이 묻는 순간에는 이미 굳어 있는
 *       숫자 배열만 본다. {@code MobPerkModifiers} 가 배율을 미리 계산해 두는 것과 같은 결이다.
 *       판정이 최대 0.5초 늦을 수 있는데, 성역은 「전원이 20칸 안에」라는 느린 조건이라 그 정도
 *       지연은 눈에 띄지 않는다.</li>
 *   <li><b>상자로 먼저 거른다.</b> 구역 목록에는 모든 구역을 감싸는 최소 상자를 함께 넣어 둔다.
 *       성역에서 멀리 떨어진 몹은 구역을 하나도 보지 않고 비교 여섯 번에 끝난다. 상자를 통과한
 *       몹만 구역마다 거리를 잰다 — 접속자 수만큼이라 보통 넷 이하다.</li>
 * </ol>
 *
 * <p>구역 목록은 <b>새로 만들어 통째로 갈아 끼운다</b>. 서버 스레드가 갱신하는 동안 몹이 읽는
 * 것은 언제나 완성된 옛 목록이라, 반쪽짜리 상태를 보는 일이 없다.
 *
 * <h2>느려지는 쪽이 이긴다</h2>
 * <p>팀이 여럿이면 한 몹이 A팀의 성역 안에 있으면서 B팀의 가속을 함께 받을 수 있다. 그때는
 * <b>성역이 이긴다</b>. 「지켜 주는 자리」라는 이름이 조건부가 되면 안 되기 때문이다. 감속끼리
 * 겹치면 가장 센 값 하나만 쓴다(더하지 않는다) — 증강은 중첩되지 않는다는 이 모드의 원칙이다.
 *
 * <h2>적대적인 몹만, 드래곤은 뺀다</h2>
 * <p>{@link AuraDamageManager#hostile} 을 그대로 쓴다. 살기와 같은 규칙이라 판정이 두 곳으로
 * 갈리지 않는다.
 *
 * <ul>
 *   <li><b>소·양·주민·길들인 늑대는 손대지 않는다.</b> 성역이 켜졌다고 내 늑대까지 40% 느려지면
 *       지켜 주는 효과가 아니라 방해가 된다. 반대쪽(가속)도 마찬가지다 — 흩어졌다고 닭이 빨라져
 *       봐야 아무 의미가 없다. 대가는 <b>위협에만</b> 걸려야 대가다.</li>
 *   <li><b>엔더 드래곤은 양쪽 모두 제외.</b> 회차를 끝내는 조건이라 느려지는 것도 빨라지는 것도
 *       판을 통째로 흔든다.</li>
 * </ul>
 */
public final class SanctuaryManager {

	/**
	 * 구역 목록을 다시 만드는 주기. 0.5초다.
	 *
	 * <p>{@link TeamProximity} 가 벌어진 정도를 재는 주기(1초)보다 짧게 잡았다. 뭉쳤는지 여부는
	 * 그쪽이 1초마다 재고, 여기서는 <b>팀원이 움직인 위치</b>를 따라잡아야 하기 때문이다. 사람이
	 * 0.5초에 걷는 거리는 2~3칸이라 반경 10칸짜리 구역의 가장자리가 그만큼 늦게 따라온다.
	 */
	static final int REFRESH_INTERVAL_TICKS = 10;

	/** 구역 배열에 한 구역이 차지하는 칸 수. (x, y, z, 반경제곱, 감속) */
	private static final int STRIDE = 5;

	/**
	 * 차원별 성역 구역. 성역을 가진 팀이 <b>뭉쳐 있을 때만</b> 들어 있다.
	 *
	 * <p>{@link #tick} 이 통째로 갈아 끼운다. 읽는 쪽은 절대 고치지 않는다.
	 */
	private static volatile Map<ResourceKey<Level>, Zones> zones = Map.of();

	/**
	 * 흩어져 있는 팀 때문에 걸린 가속 확률. 없으면 0.
	 *
	 * <p>이쪽에는 차원도 반경도 없다. 대가는 상시·전역이라는 약속 그대로다.
	 */
	private static volatile double hasteChance;

	/**
	 * 성역이 지금 어디엔가 걸려 있는가.
	 *
	 * <p><b>뜨거운 자리의 첫 줄이 읽는 깃발이다.</b> 거짓이면 아래의 어떤 계산도 하지 않는다.
	 */
	private static volatile boolean active;

	private static long now;
	private static boolean warned;

	private SanctuaryManager() {
	}

	// ------------------------------------------------------------------ 갱신

	/**
	 * {@code SharedFateMod} 가 서버 틱마다 부른다. 실제로 훑는 것은
	 * {@value #REFRESH_INTERVAL_TICKS} 틱에 한 번이다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++now % REFRESH_INTERVAL_TICKS != 0) {
			return;
		}
		try {
			refresh(server);
		} catch (RuntimeException error) {
			warnOnce(error);
			// 무엇이 잘못됐는지 모르는 채로 몹을 느리게 두지 않는다. 아무 일도 안 하는 쪽으로 물러난다.
			publish(Map.of(), 0.0);
		}
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드로 넘어가지 않게 한다. */
	public static void reset() {
		publish(Map.of(), 0.0);
		now = 0;
		warned = false;
	}

	private static void refresh(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		Map<ResourceKey<Level>, List<double[]>> building = new HashMap<>();
		double haste = 0.0;

		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
				continue;
			}
			List<SanctuaryEffect> found = sanctuariesOf(state);
			if (found.isEmpty()) {
				continue;
			}
			List<ServerPlayer> online = null;
			for (SanctuaryEffect effect : found) {
				// 정의에 적힌 거리를 그대로 넘긴다. 세트 「결속 3」의 배율은 저절로 먹는다.
				if (!TeamProximity.together(team.teamId(), effect.distance())) {
					haste = Math.max(haste, effect.hasteWhenApart());
					continue;
				}
				if (effect.slow() <= 0.0) {
					continue;
				}
				if (online == null) {
					online = onlineMembers(server, team);
				}
				addZones(building, online, team.teamId(), effect);
			}
		}
		publish(freeze(building), haste);
	}

	/** 팀원 한 명마다 구역 하나를 놓는다. 반경에도 결속 배율을 먹인다. */
	private static void addZones(Map<ResourceKey<Level>, List<double[]>> building,
			List<ServerPlayer> online, UUID teamId, SanctuaryEffect effect) {
		double radius = TeamProximity.scaled(teamId, effect.radius());
		if (!(radius > 0.0)) {
			return;
		}
		double radiusSquared = radius * radius;
		double slow = effect.slow();
		for (ServerPlayer member : online) {
			ServerLevel level = member.level();
			Vec3 center = member.position();
			building.computeIfAbsent(level.dimension(), key -> new ArrayList<>())
					.add(new double[] {center.x, center.y, center.z, radiusSquared, slow});
			// 구역을 놓는 바로 그 자리에서 고리도 그린다. 보이는 원과 실제로 느려지는 범위가
			// 어긋날 수 없다 — 둘이 같은 중심·같은 반경을 쓴다.
			AuraRing.draw(member, radius, ParticleTypes.SOUL_FIRE_FLAME, now);
		}
	}

	/** 모아 둔 구역을 읽기 전용 배열과 감싸는 상자로 굳힌다. */
	private static Map<ResourceKey<Level>, Zones> freeze(
			Map<ResourceKey<Level>, List<double[]>> building) {
		if (building.isEmpty()) {
			return Map.of();
		}
		Map<ResourceKey<Level>, Zones> frozen = new HashMap<>(building.size());
		for (Map.Entry<ResourceKey<Level>, List<double[]>> entry : building.entrySet()) {
			frozen.put(entry.getKey(), Zones.of(entry.getValue()));
		}
		return Map.copyOf(frozen);
	}

	/**
	 * 새 판정을 내건다.
	 *
	 * <p>{@link #active} 를 <b>맨 나중에</b> 쓴다. 다른 스레드가 깃발만 보고 들어왔을 때 값이
	 * 아직 안 바뀐 상태를 보지 않게 한다.
	 */
	private static void publish(Map<ResourceKey<Level>, Zones> next, double haste) {
		zones = next;
		hasteChance = haste;
		active = !next.isEmpty() || haste > 0.0;
	}

	// ------------------------------------------------------------------ 조회

	/**
	 * 이 몹이 이번 틱을 건너뛰어야 하는가. Mixin 이 <b>모든 엔티티마다 매 틱</b> 부른다.
	 *
	 * <p>성역을 가진 팀이 없으면 첫 줄에서 끝난다.
	 */
	public static boolean shouldSkipTick(@Nullable ServerLevel level, @Nullable Entity entity) {
		if (!active) {
			return false;
		}
		try {
			double slow = slowFor(level, entity);
			return slow > 0.0 && roll() < slow;
		} catch (RuntimeException error) {
			warnOnce(error);
			return false;
		}
	}

	/**
	 * 이 몹이 이번 틱을 한 번 더 돌아야 하는가(뭉쳐 있지 않을 때의 대가).
	 *
	 * <p>{@link #shouldSkipTick} 과 같은 자리에서 불린다. 첫 두 줄에서 대부분 끝난다.
	 */
	public static boolean shouldRunExtraTick(@Nullable ServerLevel level, @Nullable Entity entity) {
		if (!active) {
			return false;
		}
		double haste = hasteChance;
		if (!(haste > 0.0)) {
			return false;
		}
		try {
			if (!AuraDamageManager.hostile(entity)) {
				return false;
			}
			// 성역 안에 있으면 느려지는 쪽이 이긴다. 팀이 하나뿐이면 이 줄은 늘 0 이다.
			if (slowFor(level, entity) > 0.0) {
				return false;
			}
			return roll() < haste;
		} catch (RuntimeException error) {
			warnOnce(error);
			return false;
		}
	}

	/**
	 * 이 몹에게 걸린 감속 확률. 성역 밖이거나 대상이 아니면 0.
	 *
	 * <p>여러 성역이 겹치면 <b>가장 센 값 하나</b>다. 증강은 중첩되지 않는다.
	 */
	static double slowFor(@Nullable ServerLevel level, @Nullable Entity entity) {
		Map<ResourceKey<Level>, Zones> snapshot = zones;
		if (snapshot.isEmpty() || level == null) {
			return 0.0;
		}
		Zones found = snapshot.get(level.dimension());
		if (found == null || !AuraDamageManager.hostile(entity)) {
			return 0.0;
		}
		return found.strongestAt(entity.getX(), entity.getY(), entity.getZ());
	}

	/**
	 * 0 이상 1 미만의 난수.
	 *
	 * <p><b>월드나 몹의 난수를 쓰지 않는다.</b> 그쪽을 쓰면 전리품·스폰처럼 같은 난수열에
	 * 기대는 자리들이 이 증강 때문에 조용히 어긋난다.
	 */
	private static double roll() {
		return ThreadLocalRandom.current().nextDouble();
	}

	// ------------------------------------------------------------------ 도우미

	/**
	 * 이 팀이 가진 {@code sanctuary} 정의들. 없으면 빈 목록.
	 *
	 * <p>보유 증강과 <b>켜진 세트 효과</b>를 함께 펼친다. {@link AuraDamageManager} 가 살기를
	 * 찾는 방식 그대로다.
	 */
	private static List<SanctuaryEffect> sanctuariesOf(TeamState state) {
		List<SanctuaryEffect> found = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			// 풀에서 사라진 id 는 건너뛴다. 저장에만 남은 id 는 언제든 생긴다.
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof SanctuaryEffect sanctuary) {
					found.add(sanctuary);
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof SanctuaryEffect sanctuary) {
				found.add(sanctuary);
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
				"성역 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		reset();
	}

	/** 테스트가 구역을 직접 놓아 둘 때 쓴다. */
	static void putZoneForTesting(ResourceKey<Level> dimension, double x, double y, double z,
			double radius, double slow, double haste) {
		Map<ResourceKey<Level>, Zones> next = Map.of(dimension,
				Zones.of(List.of(new double[] {x, y, z, radius * radius, slow})));
		publish(next, haste);
	}

	/**
	 * 한 차원에 놓인 성역 구역들.
	 *
	 * <p>구역마다 객체를 만들지 않고 배열 하나에 {@value #STRIDE} 칸씩 담는다. 이 배열은 매 틱
	 * 몹 수만큼 훑히는 자리라 참조를 따라가는 횟수를 줄이는 편이 낫다.
	 *
	 * <p>{@code minX}~{@code maxZ} 는 모든 구역을 감싸는 최소 상자다. 성역에서 멀리 있는 몹은
	 * 여기서 비교 여섯 번에 걸러진다.
	 */
	private record Zones(double[] packed, double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {

		static Zones of(List<double[]> collected) {
			double[] packed = new double[collected.size() * STRIDE];
			double minX = Double.MAX_VALUE;
			double minY = Double.MAX_VALUE;
			double minZ = Double.MAX_VALUE;
			double maxX = -Double.MAX_VALUE;
			double maxY = -Double.MAX_VALUE;
			double maxZ = -Double.MAX_VALUE;
			int at = 0;
			for (double[] zone : collected) {
				System.arraycopy(zone, 0, packed, at, STRIDE);
				at += STRIDE;
				double radius = Math.sqrt(zone[3]);
				minX = Math.min(minX, zone[0] - radius);
				minY = Math.min(minY, zone[1] - radius);
				minZ = Math.min(minZ, zone[2] - radius);
				maxX = Math.max(maxX, zone[0] + radius);
				maxY = Math.max(maxY, zone[1] + radius);
				maxZ = Math.max(maxZ, zone[2] + radius);
			}
			return new Zones(packed, minX, minY, minZ, maxX, maxY, maxZ);
		}

		/** 이 자리에 걸린 감속 중 가장 센 값. 어느 구역에도 안 들어가면 0. */
		double strongestAt(double x, double y, double z) {
			if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
				return 0.0;
			}
			double best = 0.0;
			for (int at = 0; at < packed.length; at += STRIDE) {
				double dx = x - packed[at];
				double dy = y - packed[at + 1];
				double dz = z - packed[at + 2];
				if (dx * dx + dy * dy + dz * dz <= packed[at + 3] && packed[at + 4] > best) {
					best = packed[at + 4];
				}
			}
			return best;
		}
	}
}
