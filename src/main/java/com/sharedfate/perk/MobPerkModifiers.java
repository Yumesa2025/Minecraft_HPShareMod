package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.MobDamageEffect;
import com.sharedfate.perk.effect.MobHealthEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 몹에게 걸리는 증강 효과({@code mob_health}, {@code mob_damage})의 실행부.
 *
 * <p>다른 효과들은 {@link PerkEffect#apply}로 팀원 한 명에게 붙였다 떼면 끝나지만, 이 둘은
 * 대상이 팀원이 아니라 월드의 몹이다. 그래서 효과 객체는 "무엇을 얼마나" 만 들고 있고,
 * "언제 누구에게" 는 이 클래스가 전부 맡는다.
 *
 * <ul>
 *   <li>{@code mob_health} 는 몹이 월드에 올라올 때({@code ServerEntityEvents.ENTITY_LOAD})
 *       최대 체력 속성에 임시 수정자를 붙인다. 임시 수정자는 저장되지 않으므로 서버를 껐다
 *       켜면 저절로 사라지고, 다시 올라올 때 그때의 증강 구성으로 새로 계산된다.</li>
 *   <li>{@code mob_damage} 는 {@link PerkDamage} 가 피해 계산 시점에 배율만 조회해 간다.
 *       몹에게 아무것도 붙이지 않으므로 정리할 상태가 없다.</li>
 * </ul>
 *
 * <h2>어느 팀의 증강을 몹에게 적용하는가</h2>
 * <p>몹은 어느 팀에도 속하지 않는다. 한 팀 안에서는 여러 증강의 배율을 곱하고, 팀이 여럿이면
 * 그중 <b>1.0 에서 가장 멀리 떨어진 배율 하나</b>만 고른다 (같으면 작은 쪽). 팀 목록을 훑는
 * 순서에 결과가 좌우되지 않고, 팀 수가 늘어도 배율이 누적되지 않으며, 어느 팀이 실제로 고른
 * 증강이 다른 팀 때문에 조용히 무효가 되지도 않는다. 실제 운영에서는 팀이 하나뿐이라
 * "보유한 팀이 있으면 그 배율" 과 같은 결과가 된다.
 *
 * <h2>증강 구성이 바뀌었을 때</h2>
 * <p>1초에 한 번 팀들의 보유 증강을 훑어 지문(signature)을 만든다. 지문이 달라졌을 때만
 * 캐시를 비우고 이미 올라와 있는 몹 전체를 다시 계산한다. 회차 리셋으로 증강을 잃으면 배율이
 * 1.0 이 되고, 그 순간 살아 있던 몹에 붙어 있던 수정자도 이 훑기에서 걷힌다.
 */
public final class MobPerkModifiers {
	/** 최대 체력 수정자의 식별자. 여러 증강의 배율을 하나로 합쳐 붙이므로 고정값 하나면 된다. */
	public static final Identifier HEALTH_MODIFIER_ID = SharedFateMod.id("perk/mob_health");

	/** 증강 구성이 바뀌었는지 보는 주기. 매 틱 볼 필요가 없다. */
	private static final int CHECK_INTERVAL_TICKS = 20;
	/** 최대 체력이 0 이 되면 몹이 존재할 수 없으므로 하한을 둔다. */
	static final double MIN_HEALTH_MULTIPLIER = 0.01;
	/** 합쳐진 배율의 상한. 무한대가 속성이나 피해 계산으로 새어나가지 않게 막는다. */
	static final double MAX_MULTIPLIER = 1.0e4;

	/**
	 * 몹 종류별로 계산해 둔 배율. 피해 계산은 초당 수십 번 도는 자리라 매번 팀을 훑을 수 없다.
	 * 증강 구성이 바뀌면 통째로 비운다.
	 */
	private static final Map<EntityType<?>, Double> HEALTH_CACHE = new ConcurrentHashMap<>();
	private static final Map<EntityType<?>, Double> DAMAGE_CACHE = new ConcurrentHashMap<>();

	private static int tickCounter;
	private static int signature;
	private static boolean signatureKnown;
	private static volatile boolean warned;

	private MobPerkModifiers() {
	}

	// ------------------------------------------------------------------ 등록 지점

	/**
	 * 몹이 월드에 올라올 때 최대 체력 수정자를 맞춘다.
	 *
	 * <p>새로 스폰될 때든 청크가 다시 읽힐 때든 같은 자리를 지나므로, 여기 한 곳만 잡으면
	 * mixin 없이 모든 몹을 덮는다.
	 */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		applyHealth(entity);
	}

	/** 증강 구성이 바뀌었으면 캐시를 비우고 이미 올라와 있는 몹을 다시 계산한다. */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++tickCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		int current;
		try {
			current = computeSignature(server);
		} catch (RuntimeException error) {
			warnOnce(error);
			return;
		}
		if (signatureKnown && current == signature) {
			return;
		}
		signature = current;
		signatureKnown = true;
		HEALTH_CACHE.clear();
		DAMAGE_CACHE.clear();
		sweep(server);
	}

	/**
	 * 다음 틱을 기다리지 않고 곧바로 다시 계산한다.
	 *
	 * <p>증강을 고른 직후처럼 구성이 바뀐 시점을 이미 아는 곳에서 부른다. 지문 감시만으로도
	 * 결국 따라잡지만 최대 1초가 걸리므로, 고르자마자 몹이 달라지는 편이 자연스럽다.
	 */
	public static void invalidateNow(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickCounter = CHECK_INTERVAL_TICKS - 1;
		signatureKnown = false;
		tick(server);
	}

	/** 서버가 멈출 때 캐시와 지문을 비운다. 다음 월드의 증강 구성을 물려받지 않기 위해서다. */
	public static void reset() {
		HEALTH_CACHE.clear();
		DAMAGE_CACHE.clear();
		tickCounter = 0;
		signature = 0;
		signatureKnown = false;
	}

	// ------------------------------------------------------------------ 조회

	/**
	 * 이 몹이 주는 피해에 곱할 배율. 몹이 아니면(플레이어 포함) 항상 1.0 이다.
	 *
	 * <p>{@link PerkDamage} 가 피해 계산 시점에 부른다.
	 */
	public static double damageMultiplier(@Nullable Entity attacker) {
		if (!(attacker instanceof Mob mob)) {
			return 1.0;
		}
		return lookup(DAMAGE_CACHE, mob, false);
	}

	/** 이 몹의 최대 체력에 곱할 배율. */
	public static double healthMultiplier(Mob mob) {
		return lookup(HEALTH_CACHE, mob, true);
	}

	private static double lookup(Map<EntityType<?>, Double> cache, Mob mob, boolean health) {
		try {
			Double cached = cache.get(mob.getType());
			if (cached != null) {
				return cached;
			}
			double value = compute(mob, health);
			cache.put(mob.getType(), value);
			return value;
		} catch (RuntimeException error) {
			warnOnce(error);
			return 1.0;
		}
	}

	/**
	 * 팀별 배율을 구해 하나로 합친다.
	 *
	 * <p>한 팀 안에서는 곱하고, 팀 사이에서는 {@link #stronger} 로 하나를 고른다.
	 */
	private static double compute(Mob mob, boolean health) {
		MinecraftServer server = mob.level().getServer();
		if (server == null) {
			return 1.0;
		}
		return computeFor(server, mob.getType(), mob instanceof Enemy, health);
	}

	/**
	 * 화면에 적을 <b>대표 배율</b>. 능력치 표시가 「지금 판이 얼마나 험한가」로 쓴다.
	 *
	 * <p>기준으로 삼는 몹은 좀비다. 대상을 따로 적지 않은 증강은 <b>적대적 몹 전체</b>에
	 * 걸리므로 그 경우 이 값이 곧 모든 몹의 배율이다. {@code targets} 로 몹을 골라 잡은
	 * 증강만은 그 몹에게만 걸려 이 줄과 다를 수 있는데, 화면에 몹 종류별 표를 그릴 수는
	 * 없으므로 <b>가장 흔한 경우를 대표로</b> 적는다.
	 *
	 * <p>「난이도 상승」의 배율은 여기 들어 있지 않다. 그쪽은 속성 수정자를 따로 붙여 곱하므로
	 * ({@code DifficultyEscalation}) 부르는 쪽에서 곱한다.
	 */
	public static double representativeMultiplier(@Nullable MinecraftServer server, boolean health) {
		if (server == null) {
			return 1.0;
		}
		try {
			return computeFor(server, EntityTypes.ZOMBIE, true, health);
		} catch (RuntimeException error) {
			warnOnce(error);
			return 1.0;
		}
	}

	private static double computeFor(MinecraftServer server, EntityType<?> type, boolean hostile,
			boolean health) {
		TeamManager manager = TeamManager.get(server);
		double chosen = 1.0;
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
				continue;
			}
			chosen = stronger(chosen, teamMultiplier(state, type, hostile, health));
		}
		return health ? sanitizeHealth(chosen) : sanitizeDamage(chosen);
	}

	/** 한 팀이 보유한 증강들의 배율을 모두 곱한 값. */
	private static double teamMultiplier(TeamState state, EntityType<?> type, boolean hostile,
			boolean health) {
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				total *= contribution(effect, type, hostile, health);
			}
		}
		return total;
	}

	private static double contribution(PerkEffect effect, EntityType<?> type, boolean hostile,
			boolean health) {
		if (health) {
			return effect instanceof MobHealthEffect mobHealth && mobHealth.appliesTo(type, hostile)
					? mobHealth.multiplierFor() : 1.0;
		}
		return effect instanceof MobDamageEffect mobDamage && mobDamage.appliesTo(type, hostile)
				? mobDamage.multiplierFor() : 1.0;
	}

	/**
	 * 두 배율 중 1.0 에서 더 멀리 떨어진 쪽을 고른다. 거리가 같으면 작은 쪽(플레이어에게
	 * 유리한 쪽)을 고른다.
	 *
	 * <p>비율은 로그 눈금으로 봐야 대칭이다. 2배와 0.5배는 방향만 반대고 세기가 같다.
	 */
	static double stronger(double first, double second) {
		double firstDistance = Math.abs(Math.log(first));
		double secondDistance = Math.abs(Math.log(second));
		if (secondDistance > firstDistance) {
			return second;
		}
		if (firstDistance > secondDistance) {
			return first;
		}
		return Math.min(first, second);
	}

	/** 최대 체력 배율은 0 이 될 수 없다. 이상한 값은 1.0 으로 물러난다. */
	static double sanitizeHealth(double value) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return 1.0;
		}
		return Math.max(MIN_HEALTH_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
	}

	/** 피해 배율은 0 까지 허용한다. 이상한 값은 1.0 으로 물러난다. */
	static double sanitizeDamage(double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			return 1.0;
		}
		return Math.min(MAX_MULTIPLIER, value);
	}

	// ------------------------------------------------------------------ 최대 체력 반영

	/**
	 * 몹 하나의 최대 체력 수정자를 지금 있어야 할 모습으로 맞춘다.
	 *
	 * <p>여러 번 불려도 결과가 같다. 배율이 1.0 이면 붙어 있던 수정자를 떼고 끝내므로,
	 * 증강 풀이 비어 있거나 회차 리셋으로 증강을 잃은 뒤에는 바닐라와 완전히 같아진다.
	 *
	 * <p>몹 스폰 경로 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다.
	 */
	private static void applyHealth(@Nullable Entity entity) {
		// Mob 이 아닌 것은 손대지 않는다. 플레이어는 Mob 이 아니므로 여기서 이미 걸러진다.
		if (!(entity instanceof Mob mob)) {
			return;
		}
		try {
			AttributeInstance instance = mob.getAttribute(Attributes.MAX_HEALTH);
			if (instance == null) {
				return;
			}
			double multiplier = healthMultiplier(mob);
			// ADD_MULTIPLIED_TOTAL 은 다른 수정자까지 계산한 뒤 (1 + amount) 를 곱한다.
			double amount = multiplier - 1.0;
			AttributeModifier existing = instance.getModifier(HEALTH_MODIFIER_ID);

			if (multiplier == 1.0) {
				if (existing == null) {
					return;
				}
				float before = mob.getMaxHealth();
				instance.removeModifier(HEALTH_MODIFIER_ID);
				settleHealth(mob, before);
				return;
			}
			if (existing != null && existing.amount() == amount) {
				return;
			}
			float before = mob.getMaxHealth();
			instance.addOrUpdateTransientModifier(new AttributeModifier(
					HEALTH_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			settleHealth(mob, before);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/**
	 * 최대 체력이 바뀐 뒤 현재 체력을 정리한다.
	 *
	 * <p>바뀌기 전에 체력이 가득 차 있었으면 새 최대치로 다시 채운다. 갓 스폰된 몹이 여기
	 * 해당하므로, 배율이 1보다 크면 늘어난 만큼을 실제로 얻는다. 가득 차 있지 않았으면
	 * 넘치는 만큼만 깎는다. 이미 죽어가는 개체는 건드리지 않는다.
	 */
	private static void settleHealth(Mob mob, float previousMax) {
		float health = mob.getHealth();
		if (!(health > 0.0F)) {
			return;
		}
		float max = mob.getMaxHealth();
		if (!Float.isFinite(max) || max <= 0.0F) {
			return;
		}
		boolean wasFull = health >= previousMax;
		if (wasFull || health > max) {
			mob.setHealth(max);
		}
	}

	/** 이미 올라와 있는 몹 전체를 다시 계산한다. 증강 구성이 바뀐 순간에만 돈다. */
	private static void sweep(MinecraftServer server) {
		try {
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity entity : level.getAllEntities()) {
					applyHealth(entity);
				}
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/**
	 * 팀들의 보유 증강을 요약한 값. 이 값이 그대로면 배율도 그대로다.
	 *
	 * <p>해시라 이론적으로는 서로 다른 구성이 같은 값을 낼 수 있다. 그 경우에도 최악은
	 * "이미 올라와 있는 몹의 수정자가 다음 변경까지 옛날 값으로 남는" 것뿐이라, 스폰이나
	 * 피해 처리가 깨지지는 않는다.
	 */
	private static int computeSignature(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		int hash = 1;
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled) {
				continue;
			}
			for (String perkId : state.ownedPerks) {
				hash = hash * 31 + String.valueOf(perkId).hashCode();
			}
			hash = hash * 31 + 17;
		}
		return hash;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"몹 증강 효과를 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		reset();
		warned = false;
	}

	// ------------------------------------------------------------------ 대상 한정

	/**
	 * {@code targets} / {@code excludes} 로 적은 대상 한정.
	 *
	 * <ul>
	 *   <li>{@code targets} 를 적으면 거기 적힌 종류에만 적용한다.</li>
	 *   <li>{@code excludes} 만 적으면 적대적 몹 전체에서 그 종류만 뺀다.</li>
	 *   <li>둘 다 없으면 적대적 몹 전체에 적용한다. 주민·소·팀원은 여기 들어가지 않는다.</li>
	 * </ul>
	 *
	 * <p>레지스트리에 없는 엔티티 이름은 경고를 남기고 그 이름만 버린다. 증강 자체는 살린다.
	 * 확인은 만들 때가 아니라 처음 쓸 때 한다. 증강 정의를 읽는 시점에는 엔티티 레지스트리가
	 * 아직 준비되지 않았을 수 있기 때문이다.
	 */
	public static final class Targets {
		/** 아무 한정도 없는 기본값. 적대적 몹 전체를 뜻한다. */
		public static final Targets ALL_HOSTILE = new Targets("", false, Set.of(), Set.of());

		private final String perkId;
		private final boolean hasTargetList;
		private Set<Identifier> targets;
		private Set<Identifier> excludes;
		private boolean verified;

		Targets(String perkId, boolean hasTargetList, Set<Identifier> targets,
				Set<Identifier> excludes) {
			this.perkId = perkId;
			this.hasTargetList = hasTargetList;
			this.targets = Set.copyOf(targets);
			this.excludes = Set.copyOf(excludes);
		}

		/** 이 효과가 해당 종류에 걸리는지. */
		public synchronized boolean matches(EntityType<?> type, boolean hostile) {
			if (type == null) {
				return false;
			}
			verify();
			// 팀원에게는 어떤 경우에도 걸리지 않는다. targets 에 직접 적어도 마찬가지다.
			if (type == EntityTypes.PLAYER) {
				return false;
			}
			if (hasTargetList) {
				return targets.contains(EntityType.getKey(type));
			}
			if (excludes.contains(EntityType.getKey(type))) {
				return false;
			}
			return hostile;
		}

		/** {@code targets} 필드를 적었는지. 적었는데 남은 게 없으면 아무에게도 안 걸린다. */
		public boolean hasTargetList() {
			return hasTargetList;
		}

		public synchronized Set<Identifier> targets() {
			return targets;
		}

		public synchronized Set<Identifier> excludes() {
			return excludes;
		}

		/** 레지스트리에 없는 이름을 처음 쓸 때 한 번 걸러낸다. */
		private void verify() {
			if (verified) {
				return;
			}
			verified = true;
			targets = keepKnown(targets, "targets");
			excludes = keepKnown(excludes, "excludes");
			if (hasTargetList && targets.isEmpty()) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: targets 에 쓸 수 있는 엔티티가 하나도 없어 이 효과는 아무에게도 걸리지 않습니다",
						perkId);
			}
		}

		private Set<Identifier> keepKnown(Set<Identifier> ids, String field) {
			if (ids.isEmpty()) {
				return ids;
			}
			Set<Identifier> kept = new LinkedHashSet<>(ids.size());
			for (Identifier id : ids) {
				boolean known;
				try {
					known = BuiltInRegistries.ENTITY_TYPE.containsKey(id);
				} catch (RuntimeException error) {
					// 레지스트리를 못 읽는 상황이면 걸러내지 않고 그대로 둔다.
					return ids;
				}
				if (known) {
					kept.add(id);
				} else {
					SharedFateMod.LOGGER.warn("증강 {}: {} 의 알 수 없는 엔티티 {} 를 무시합니다",
							perkId, field, id);
				}
			}
			return Set.copyOf(kept);
		}
	}

	/**
	 * 효과 JSON 에서 {@code targets} / {@code excludes} 를 읽는다.
	 *
	 * <p>이름 모양이 틀렸거나 레지스트리에 없는 값은 경고 후 그 항목만 버린다. 증강 전체를
	 * 버리지 않으므로 오타 하나가 증강을 통째로 날리지는 않는다.
	 */
	public static Targets parseTargets(String perkId, JsonObject json) {
		List<String> rawTargets = PerkEffectType.readStringList(json, "targets");
		List<String> rawExcludes = PerkEffectType.readStringList(json, "excludes");
		if (rawTargets == null && rawExcludes == null) {
			return Targets.ALL_HOSTILE;
		}
		Set<Identifier> targets = toIdentifiers(perkId, "targets", rawTargets);
		Set<Identifier> excludes = toIdentifiers(perkId, "excludes", rawExcludes);
		if (rawTargets != null && rawExcludes != null && !excludes.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: targets 와 excludes 를 함께 적었습니다. targets 만 씁니다", perkId);
		}
		return new Targets(perkId, rawTargets != null, targets, excludes);
	}

	private static Set<Identifier> toIdentifiers(String perkId, String field,
			@Nullable List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return Set.of();
		}
		List<Identifier> parsed = new ArrayList<>(raw.size());
		for (String value : raw) {
			Identifier id = Identifier.tryParse(value);
			if (id == null) {
				SharedFateMod.LOGGER.warn("증강 {}: {} 의 엔티티 이름 {} 이(가) 올바르지 않아 무시합니다",
						perkId, field, value);
				continue;
			}
			parsed.add(id);
		}
		return Set.copyOf(new LinkedHashSet<>(parsed));
	}
}
