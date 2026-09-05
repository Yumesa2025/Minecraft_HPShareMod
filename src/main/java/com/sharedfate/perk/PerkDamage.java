package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.DamageTakenBlockingEffect;
import com.sharedfate.perk.effect.DamageTakenFromEffect;
import com.sharedfate.perk.effect.ShieldFallImmunityEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 증강의 피해 배율을 실제 피해량에 반영하는 계산기.
 *
 * <p>{@code LivingEntityPerkDamageMixin} 이 {@code LivingEntity.hurtServer} 진입 시점에
 * 이 클래스를 부른다. 한 번의 피해 이벤트에 대해 딱 한 번만 곱해지고, 그 뒤의 방패·방어구·흡수
 * 계산과 {@code StatMirror} 의 공유 체력 반영은 이미 배율이 반영된 수치를 보게 된다.
 *
 * <p>배율이 정확히 1.0 이면 원래 값을 그대로 돌려준다. 증강 풀이 비어 있는 동안에는 항상 이
 * 경로를 타므로 바닐라 피해 계산과 100% 같다.
 */
public final class PerkDamage {
	/** 배율을 곱한 뒤의 상한. 무한대·NaN 이 바닐라 계산으로 새어나가지 않게 막는다. */
	static final float MAX_DAMAGE = 1.0e9F;

	/** 조회가 한 번 터지면 매 피해마다 로그가 쌓이므로 한 번만 남긴다. */
	private static volatile boolean warned;

	private PerkDamage() {
	}

	/**
	 * 피해량에 "주는 피해"·"받는 피해" 배율을 반영한다.
	 *
	 * @param victim 피해를 받는 대상. 팀원이면 받는 피해 배율이 걸리고, 방패로 막는 중이면
	 *               {@code damage_taken_blocking} 배율까지 함께 걸린다.
	 * @param source 피해원. 가해자가 팀원이면 주는 피해 배율이, 몹이면 {@code mob_damage}
	 *               배율이 걸린다.
	 * @param amount {@code hurtServer} 가 받은 원래 피해량
	 * @return 배율을 반영한 피해량. 반영할 게 없으면 {@code amount} 그대로
	 */
	public static float scale(@Nullable Entity victim, @Nullable DamageSource source, float amount) {
		if (!(amount > 0.0F) || !Float.isFinite(amount)) {
			return amount;
		}
		double factor;
		try {
			// 조회 실패가 피해 처리를 막으면 안 된다. 어떤 예외든 원래 값으로 돌아간다.
			Entity attacker = source == null ? null : source.getEntity();
			factor = dealtFactor(attacker) * takenFactor(victim) * mobDealtFactor(attacker)
					* takenSourceFactor(victim, source) * takenBlockingFactor(victim);
		} catch (RuntimeException error) {
			warnOnce(error);
			return amount;
		}
		return combine(amount, factor);
	}

	/** 가해자가 증강을 가진 팀원일 때만 주는 피해 배율을 읽는다. */
	private static double dealtFactor(@Nullable Entity attacker) {
		if (!(attacker instanceof ServerPlayer player) || !perksActive(player)) {
			return 1.0;
		}
		return PerkManager.damageDealtMultiplier(player);
	}

	/**
	 * 가해자가 몹일 때 {@code mob_damage} 증강의 배율을 읽는다.
	 *
	 * <p>{@link #dealtFactor}와는 배타적이다. 가해자는 팀원이거나 몹이지 둘 다일 수 없다.
	 * 화살·불덩이처럼 던진 것에 맞은 경우에도 {@code DamageSource.getEntity()}는 쏜 몹을
	 * 가리키므로 여기서 잡힌다.
	 */
	private static double mobDealtFactor(@Nullable Entity attacker) {
		return MobPerkModifiers.damageMultiplier(attacker);
	}

	/**
	 * 화면이 「받는 피해」 줄에 적을 배율.
	 *
	 * <p>피해 계산이 실제로 쓰는 {@link #takenFactor} 를 그대로 부른다. 값을 여기서 따로
	 * 세면 화면과 실제가 갈라진다 — 특히 「증강을 껐다」·「아직 아무 증강도 없다」 같은
	 * 조건이 두 벌이 되면 그중 하나만 고쳐지는 사고가 난다.
	 *
	 * <p>피해 종류를 가리는 {@code damage_taken_from} 은 들어 있지 않다. 그쪽은 불·폭발·몹처럼
	 * <b>맞은 것이 무엇이냐에 따라 달라지는</b> 배율이라 한 줄로 적을 수 없고, 종류마다 한
	 * 줄씩 적으면 능력치 표시가 그 표에 잡아먹힌다. 모든 피해에 공통으로 걸리는 배율만 적는다.
	 *
	 * <p>같은 이유로 {@code damage_taken_blocking} 도 빠져 있다. 그쪽은 <b>지금 막는 중이냐에
	 * 따라 달라지는</b> 값이라 한 줄로 적으면 방패를 내린 순간 거짓말이 된다.
	 */
	public static double takenMultiplier(@Nullable ServerPlayer player) {
		return takenFactor(player);
	}

	/** 피해자가 증강을 가진 팀원일 때만 받는 피해 배율을 읽는다. */
	private static double takenFactor(@Nullable Entity victim) {
		if (!(victim instanceof ServerPlayer player) || !perksActive(player)) {
			return 1.0;
		}
		return PerkManager.damageTakenMultiplier(player);
	}

	/**
	 * 피해 종류를 가려서 걸리는 {@code damage_taken_from} 배율을 읽는다.
	 *
	 * <p>{@link #takenFactor} 와 따로 도는 이유는 {@link PerkEffect#damageTakenMultiplier()} 에
	 * 피해원이 넘어오지 않기 때문이다. 그 자리를 고치면 조건을 모르는 옛 경로가 "불 피해 전용"
	 * 배율을 모든 피해에 곱하게 되므로, 피해원을 아는 여기서 따로 훑는다.
	 *
	 * <p>증강의 <b>최상위 효과만</b> 본다. {@code periodic}·{@code conditional} 안에 들어간
	 * {@code damage_taken_from} 은 여기 걸리지 않는데, 그런 정의는 애초에
	 * {@link com.sharedfate.perk.effect.DamageTakenFromEffect} 가 읽는 시점에 버린다.
	 */
	private static double takenSourceFactor(@Nullable Entity victim, @Nullable DamageSource source) {
		if (source == null || !(victim instanceof ServerPlayer player) || !perksActive(player)) {
			return 1.0;
		}
		return takenSourceMultiplier(TeamLookup.stateOf(player.getUUID()), source);
	}

	// ------------------------------------------------------------------ 막는 중일 때의 배율

	/**
	 * 방패로 막는 중일 때만 걸리는 {@code damage_taken_blocking} 배율을 읽는다.
	 *
	 * <p>{@link #takenSourceFactor} 와 같은 구도다. {@link PerkEffect#damageTakenMultiplier()} 에는
	 * 피해자의 <b>자세가 넘어오지 않기</b> 때문에, 자세를 아는 여기서 따로 훑는다.
	 *
	 * <p>자세를 <b>가장 먼저</b> 본다. 막고 있지 않은 피해는 팀 상태를 찾아보지도 않고 곧바로
	 * 빠져나가므로, 이 효과를 아무도 갖고 있지 않은 서버에서도 피해 경로에 얹히는 비용이 거의
	 * 없다. {@code isBlocking()} 은 방패를 <b>손에 들고만</b> 있을 때는 거짓이고 실제로
	 * 우클릭으로 막는 중일 때만 참이다 — {@link #blocksFallDamage} 가 보는 것과 같은 값이다.
	 */
	private static double takenBlockingFactor(@Nullable Entity victim) {
		if (!(victim instanceof ServerPlayer player) || !player.isBlocking() || !perksActive(player)) {
			return 1.0;
		}
		return blockingMultiplier(TeamLookup.stateOf(player.getUUID()), true);
	}

	/**
	 * 이 팀이 가진 {@code damage_taken_blocking} 중 지금 자세에 걸리는 것들의 배율을 모두 곱한 값.
	 *
	 * <p><b>보유 증강과 세트를 둘 다 훑는다.</b> {@code ownedPerks} 만 훑고 세트를 빠뜨리면 방어
	 * 2단계가 통째로 무동작이 되는데, 빌드도 통과하고 로그도 남지 않는다.
	 */
	static double blockingMultiplier(@Nullable TeamState state, boolean blocking) {
		if (state == null || !blocking || state.ownedPerks.isEmpty()) {
			return 1.0;
		}
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			total *= blockingMultiplierOf(perk.effects(), blocking);
		}
		total *= blockingMultiplierOf(PerkSetEffects.activeEffectsOf(state), blocking);
		// 배율 0 은 "막는 동안 완전 면역"이라는 뜻이라 그대로 살려 둔다. 음수와 무한대만 물러난다.
		return Double.isFinite(total) && total >= 0.0 ? total : 1.0;
	}

	/**
	 * 효과 목록만 보는 순수 계산.
	 *
	 * <p>살아 있는 팀이나 레지스트리 없이 시험할 수 있게 떼어 두었다. 막고 있지 않으면 목록을
	 * 훑지도 않는다.
	 */
	static double blockingMultiplierOf(@Nullable Iterable<PerkEffect> effects, boolean blocking) {
		if (effects == null || !blocking) {
			return 1.0;
		}
		double total = 1.0;
		for (PerkEffect effect : effects) {
			if (effect instanceof DamageTakenBlockingEffect blockingEffect) {
				total *= blockingEffect.multiplierFor(blocking);
			}
		}
		return total;
	}

	// ------------------------------------------------------------------ 낙하 피해 면역

	/**
	 * 이 피해를 통째로 버려야 하는가. {@code shield_fall_immunity} 만 본다.
	 *
	 * <p>{@code LivingEntityPerkDamageMixin} 이 {@code hurtServer} 진입점에서 부른다. 참이면
	 * 그 자리에서 {@code false} 를 돌려주므로 체력·무적시간·피격 애니메이션 어느 것도 움직이지
	 * 않는다. 배율 쪽({@link #scale})과 달리 값을 깎는 것이 아니라 사건 자체를 없앤다.
	 *
	 * <p>피해 종류를 <b>가장 먼저</b> 본다. 낙하가 아닌 피해는 팀 상태를 찾아보지도 않고 곧바로
	 * 빠져나가므로, 이 증강을 아무도 갖고 있지 않은 서버에서도 피해 경로에 얹히는 비용이 거의
	 * 없다.
	 */
	public static boolean blocksFallDamage(@Nullable Entity victim, @Nullable DamageSource source) {
		if (source == null || !(victim instanceof ServerPlayer player)) {
			return false;
		}
		try {
			if (!source.is(DamageTypes.FALL)) {
				return false;
			}
			return ShieldFallImmunityEffect.blocks(
					TeamLookup.stateOf(player.getUUID()), true, player.isBlocking());
		} catch (RuntimeException error) {
			warnOnce(error);
			return false;
		}
	}

	/**
	 * 이 팀이 가진 {@code damage_taken_from} 중 이 피해원에 걸리는 것들의 배율을 모두 곱한 값.
	 *
	 * <p><b>보유 증강과 세트를 둘 다 훑는다.</b> 세트에서 이 타입을 쓰는 단계는 아직 없지만,
	 * 훑는 자리를 한 곳으로 맞춰 두어야 나중에 넣는 사람이 「빌드는 통과하는데 아무 일도 안
	 * 일어나는」 함정에 빠지지 않는다.
	 */
	static double takenSourceMultiplier(@Nullable TeamState state, @Nullable DamageSource source) {
		if (state == null || source == null || state.ownedPerks.isEmpty()) {
			return 1.0;
		}
		// 「화살막이」의 폭발 피해 ×1.2 가 이 길로 들어온다. 세트 「방어 3단계」를 켠 팀에서는
		// 그 한 줄만 빠지고 ×0.5 쪽은 그대로 남는다.
		PerkDrawbacks.Waiver waiver = PerkDrawbacks.waiverFor(state);
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			total *= takenSourceMultiplierOf(perk.effects(), source, waiver, perk);
		}
		// 세트가 건 배율에는 대가가 없다. 걸러 낼 것이 없으므로 판정기를 넘기지 않는다.
		total *= takenSourceMultiplierOf(PerkSetEffects.activeEffectsOf(state), source, null, null);
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	/**
	 * 효과 목록만 보는 순수 계산.
	 *
	 * @param waiver 대가를 건너뛸지 판정할 그릇. {@code null} 이면 전부 센다
	 * @param owner  이 목록을 가진 증강. 판정을 빠르게 하려고 함께 넘긴다
	 */
	private static double takenSourceMultiplierOf(@Nullable Iterable<PerkEffect> effects,
			@Nullable DamageSource source, PerkDrawbacks.@Nullable Waiver waiver,
			@Nullable Perk owner) {
		if (effects == null || source == null) {
			return 1.0;
		}
		double total = 1.0;
		for (PerkEffect effect : effects) {
			if (effect instanceof DamageTakenFromEffect from) {
				if (waiver != null && waiver.waives(owner, effect)) {
					continue;
				}
				total *= from.multiplierFor(source);
			}
		}
		return total;
	}

	/**
	 * 배율을 따질 값어치가 있는 플레이어인지 본다.
	 *
	 * <p>팀 미소속, 증강을 끈 팀, 아직 아무 증강도 없는 팀은 여기서 걸러진다. 피해 계산은 초당
	 * 수십 번 도는 자리라 이 빠른 경로가 곧 기본 경로다.
	 */
	private static boolean perksActive(ServerPlayer player) {
		TeamState state = TeamLookup.stateOf(player.getUUID());
		return state != null && state.perksEnabled && !state.ownedPerks.isEmpty();
	}

	/** 배율을 곱하고 안전한 범위로 자른다. 배율이 1.0 이면 원래 값을 그대로 돌려준다. */
	static float combine(float amount, double factor) {
		if (factor == 1.0 || !Double.isFinite(factor) || factor < 0.0) {
			return amount;
		}
		double scaled = (double) amount * factor;
		if (!Double.isFinite(scaled)) {
			return MAX_DAMAGE;
		}
		return (float) Math.max(0.0, Math.min(MAX_DAMAGE, scaled));
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"증강 피해 배율을 읽지 못해 이번 피해는 배율 없이 처리합니다. 이 경고는 한 번만 남습니다.",
				error);
	}

	/** 테스트가 경고 억제 상태를 되돌릴 때 쓴다. */
	static void resetWarnedForTesting() {
		warned = false;
	}
}
