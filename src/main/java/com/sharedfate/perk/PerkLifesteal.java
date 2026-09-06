package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.LifestealEffect;
import com.sharedfate.perk.effect.LifestealEfficiencyEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * {@code lifesteal} 증강의 실행부.
 *
 * <p>{@link LifestealEffect} 는 "준 피해의 몇 할인가"만 들고 있고, "누가 누구에게 얼마를
 * 입혔는가"와 그 회복을 어디에 넣는가는 전부 여기서 정한다.
 *
 * <p>흡혈률을 모으는 규칙은 두 단계다. {@code lifesteal} 은 <b>더하고</b>,
 * {@link LifestealEfficiencyEffect}({@code lifesteal_efficiency})는 그 총합에 <b>곱한다.</b>
 * 계산은 {@link #totalFraction} 한 곳에만 있다.
 *
 * <h2>어떤 피해가 세어지는가</h2>
 * <ul>
 *   <li>{@code ServerLivingEntityEvents.AFTER_DAMAGE} 를 본다. 방어구·저항·흡수를 모두 지난
 *       <b>실제로 들어간 피해</b>가 넘어오므로 "준 피해"라는 말과 정확히 맞는다.</li>
 *   <li>가해자가 접속 중이고 살아 있는 팀원이어야 한다. {@code DamageSource.getEntity()} 는
 *       화살을 쏜 사람도 가리키므로 원거리 공격도 함께 세어진다.</li>
 *   <li>맞은 쪽이 <b>같은 팀이 아니어야</b> 한다. 팀원끼리의 오폭이나 자해로 공유 체력을
 *       채우는 길을 막는다. 그걸 허용하면 팀원을 때려 체력을 무한히 불릴 수 있다.</li>
 *   <li>방패로 막혔거나 실제 피해가 0 이면 아무 일도 없다.</li>
 * </ul>
 *
 * <h2>회복이 두 번 들어가지 않게 하는 방법</h2>
 * <p>이 모드는 체력을 팀이 공유하고, {@code StatMirror} 는 매 틱 팀원 개인의 체력이
 * <em>얼마나 움직였는지</em>를 보고 그 변화량을 공유 풀에 합산한다. 그래서 여기서
 * {@code attacker.heal(...)} 을 부르면 안 된다. 개인 체력이 움직인 만큼 {@code StatMirror} 가
 * 그것을 회복으로 관측해 공유 풀에 <b>한 번 더</b> 더하기 때문이다.
 *
 * <p>그래서 이 클래스는 {@link TeamState#health} 만 직접 올리고 팀원 개인에게는 손대지 않는다.
 * 개인 체력이 그대로면 {@code StatMirror} 가 보는 변화량이 0 이라 공유 풀에 아무것도 더해지지
 * 않고, 같은 틱 끝의 {@code writeBack} 이 우리가 올려 둔 공유 값을 팀 전원에게 그대로 써 준다.
 * 결과는 "팀 전체의 체력이 찼다"이고 움직인 양은 정확히 한 번분이다.
 */
public final class PerkLifesteal {
	/** 한 번의 공격으로 되돌릴 수 있는 체력 상한. 터무니없는 값이 공유 풀로 새지 않게 막는다. */
	static final float MAX_HEAL_PER_HIT = 1024.0F;

	private static volatile boolean warned;

	private PerkLifesteal() {
	}

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DAMAGE} 에 붙는 지점.
	 *
	 * <p>피해가 들어가는 모든 자리를 지나므로 어떤 예외도 밖으로 내보내지 않는다.
	 */
	public static void onDamage(LivingEntity victim, DamageSource source,
			float baseDamageTaken, float damageTaken, boolean blocked) {
		try {
			steal(victim, source, damageTaken, blocked);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void steal(LivingEntity victim, DamageSource source, float damageTaken,
			boolean blocked) {
		if (blocked || !(damageTaken > 0.0F) || !Float.isFinite(damageTaken)) {
			return;
		}
		if (source == null || !(source.getEntity() instanceof ServerPlayer attacker)) {
			return;
		}
		if (attacker == victim || attacker.isRemoved() || attacker.isDeadOrDying()) {
			return;
		}
		MinecraftServer server = attacker.level().getServer();
		if (server == null) {
			return;
		}

		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(attacker.getUUID());
		TeamState state = manager.stateOf(attacker.getUUID());
		if (team == null || state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return;
		}
		// 같은 팀을 때려서 얻는 흡혈은 공유 체력을 무한히 불리는 길이 된다.
		if (victim != null && team.contains(victim.getUUID())) {
			return;
		}
		// 공유 체력이 이미 0 이면 전멸 처리가 도는 중이다. 그 위에 회복을 얹지 않는다.
		if (!(state.health > 0.0F)) {
			return;
		}

		float healing = healingFor(state, damageTaken);
		if (!(healing > 0.0F)) {
			return;
		}
		applyToPool(state, healing);
		manager.setDirty();
	}

	/**
	 * 이 팀이 가진 {@code lifesteal} 들이 이 피해로 되돌리는 체력의 합.
	 *
	 * <p>같은 팀이 {@code lifesteal} 증강을 여러 개 가졌으면 비율을 전부 더한다.
	 *
	 * <h2>더한 뒤에 곱한다</h2>
	 * <p>{@code lifesteal} 은 <b>더하고</b>, {@code lifesteal_efficiency} 는 그렇게 모인 총합에
	 * <b>곱한다.</b> 순서가 이래야 「효율 +30%」가 뜻대로 산다 — 5% 에 1.3 을 곱하면 6.5% 이고,
	 * 여기에 0.3 을 <b>더해</b> 35% 로 만들면 완전히 다른 것이 된다.
	 *
	 * <p>{@link #totalFraction} 이 그 두 단계를 한 번의 순회로 모으고, 여기서는 그 결과에 피해를
	 * 곱해 상한만 씌운다.
	 */
	public static float healingFor(@Nullable TeamState state, float damageDealt) {
		if (state == null || state.ownedPerks.isEmpty()
				|| !(damageDealt > 0.0F) || !Float.isFinite(damageDealt)) {
			return 0.0F;
		}
		double fraction = totalFraction(state);
		if (!(fraction > 0.0)) {
			return 0.0F;
		}
		double healing = (double) damageDealt * fraction;
		if (!Double.isFinite(healing)) {
			return MAX_HEAL_PER_HIT;
		}
		return (float) Math.min(MAX_HEAL_PER_HIT, healing);
	}

	/**
	 * 이 팀에 지금 걸려 있는 <b>최종 흡혈률</b>. 준 피해에 이 값을 곱하면 회복량이다.
	 *
	 * <p>보유 증강과 켜져 있는 세트 단계를 한 번씩 훑어 {@code lifesteal} 은 더하고
	 * {@code lifesteal_efficiency} 는 곱해 둔 뒤, 마지막에 한 번만 곱한다.
	 *
	 * <p>화력 3·4단계가 <b>둘 다</b> 켜져 있으면 두 단계의 {@code lifesteal} 이 모두 더해진다.
	 * 세트 단계는 누적이라 높은 단계 하나만 남는 것이 아니기 때문이다({@link PerkSets}). 그래서
	 * 4단계 「흡혈이 5% → 15%」는 <b>3단계를 대체하는 15% 가 아니라 3단계 위에 얹는 +10%</b> 로
	 * 적혀 있다. 0.05 + 0.10 = 0.15 다.
	 */
	public static double totalFraction(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return 0.0;
		}
		double fraction = 0.0;
		double efficiency = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof LifestealEffect lifesteal) {
					fraction += lifesteal.fractionFor();
				} else if (effect instanceof LifestealEfficiencyEffect boost) {
					efficiency *= boost.multiplierFor();
				}
			}
		}
		// 세트가 준 것도 같은 규칙으로 모은다. 화력 3·4단계와 회복 3단계가 이 길을 탄다.
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof LifestealEffect lifesteal) {
				fraction += lifesteal.fractionFor();
			} else if (effect instanceof LifestealEfficiencyEffect boost) {
				efficiency *= boost.multiplierFor();
			}
		}
		if (!(fraction > 0.0)) {
			// 흡혈이 없으면 효율만 있어도 0 이다. 곱셈 단계라 그것이 옳다.
			return 0.0;
		}
		if (!Double.isFinite(efficiency)) {
			efficiency = 1.0;
		}
		double total = fraction * efficiency;
		return Double.isFinite(total) ? Math.max(0.0, total) : fraction;
	}

	/**
	 * 회복량을 공유 풀에 더한다.
	 *
	 * <p>{@code StatMirror.applyDeltas} 와 같은 규칙으로 자른다. 팀 최대 체력을 넘지 않는다.
	 */
	static void applyToPool(TeamState state, float healing) {
		if (!(healing > 0.0F)) {
			return;
		}
		float combined = state.health + healing;
		if (!Float.isFinite(combined)) {
			combined = state.maxHealth;
		}
		state.health = Math.max(0.0F, Math.min(state.maxHealth, combined));
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"흡혈 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		warned = false;
	}
}
