package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시간이 흐를수록 적대적 몹이 강해지는 「난이도 상승」.
 *
 * <p>팀이 이 설정을 켜고 시작하면, 회차가 흐르는 동안 <b>30분마다 적대적 몹의 최대 체력과
 * 공격력이 4%p 씩</b> 올라간다. 팀을 만들 때만 켤 수 있고 그 뒤로는 못 바꾼다.
 *
 * <p>배율은 {@code 1 + 0.04 × 단계} 다. 30분이면 +4%, 1시간이면 +8%, 5시간이면 +40%.
 * {@link #MAX_STEPS} 단계, 즉 <b>+100%(두 배)</b> 에서 멈춘다.
 *
 * <h2>대상</h2>
 * <p>{@link Enemy} 인 몹만이다. 주민·동물·골렘은 해당하지 않는다.
 * <b>엔더 드래곤은 반드시 뺀다</b> — 드래곤은 회차를 끝내는 조건이라, 여기에 배율이 붙으면
 * 오래 끈 회차일수록 끝낼 수 없게 되어 이 기능이 게임을 잠가 버린다.
 * 반면 <b>위더·워든·엘더 가디언 같은 다른 보스는 포함</b>한다.
 *
 * <h2>증강의 몹 배율과 어떻게 합쳐지는가</h2>
 * <ul>
 *   <li><b>최대 체력</b> — {@code MobPerkModifiers} 와 <b>다른 식별자</b>({@link #HEALTH_MODIFIER_ID})
 *       로 속성 수정자를 따로 붙인다. 바닐라는 {@code ADD_MULTIPLIED_TOTAL} 수정자를 차례로
 *       곱하므로 결과는 {@code 증강배율 × 난이도배율} 이고, 서로 덮어쓰지 않는다.</li>
 *   <li><b>공격력</b> — {@code LivingEntityPerkDamageMixin} 이 증강 배율을 먹인 값에 이어서
 *       곱한다. 역시 곱셈이라 순서가 결과를 바꾸지 않는다.</li>
 * </ul>
 *
 * <h2>단계가 오르면 알린다</h2>
 * <p>단계가 오르는 그 순간 팀 전원에게 <b>빨간 글씨</b>로 채팅에 지금 몹이 얼마나 세졌는지
 * 알린다.
 *
 * <p>상한(+100%)에 닿았을 때는 <b>그 사실만 한 번</b> 알리고 그 뒤로는 조용히 있는다.
 *
 * <h2>시간을 어떻게 세는가</h2>
 * <p>{@code TeamState.difficultyElapsedTicks} 를 <b>회차가 시작되었고 팀원이 한 명이라도 접속해
 * 있는 틱에만</b> 올린다. 리더가 「게임 시작」({@link GameStartManager})을 누르기 전에는 아예
 * 세지 않고, 누르는 순간 0 에서 다시 시작한다. 그래서 서버가 꺼져 있던 시간도, 아무도 없던 시간도 세지 않는다. 값은 월드 저장에
 * 들어가므로 재시작을 넘어 이어지고, 회차가 넘어가면 팀 상태를 새로 만들면서 0 이 된다.
 * 켜고 끄기만 {@code TeamRosterStore} 를 타고 다음 회차로 이어진다.
 */
public final class DifficultyEscalation {
	/** 최대 체력 수정자의 식별자. 증강 쪽({@code perk/mob_health})과 반드시 달라야 한다. */
	public static final Identifier HEALTH_MODIFIER_ID = SharedFateMod.id("difficulty/mob_health");

	/** 한 단계가 오르는 데 걸리는 시간(분). */
	public static final int STEP_MINUTES = 30;
	public static final int STEP_TICKS = STEP_MINUTES * 60 * 20;
	/** 한 단계마다 더해지는 몫. 0.04 = 4%p. */
	public static final double STEP_BONUS = 0.04;
	/** 상한 단계. 25단계 = +100% = 두 배. */
	public static final int MAX_STEPS = 25;
	/** 세어 둘 필요가 있는 최대 시간. 이 위로는 단계가 더 오르지 않으므로 세지 않는다. */
	public static final int MAX_ELAPSED_TICKS = MAX_STEPS * STEP_TICKS;

	/** 단계가 바뀌었는지 보는 주기. 30분에 한 번 바뀌는 값이라 매 틱 볼 필요가 없다. */
	private static final int CHECK_INTERVAL_TICKS = 20;

	/**
	 * 지금 걸려 있는 배율. 피해 계산은 초당 수십 번 도는 자리라 매번 팀을 훑을 수 없어
	 * 틱마다 갱신해 둔 값을 읽는다.
	 */
	private static volatile double currentMultiplier = 1.0;

	private static int tickCounter;
	private static int appliedSteps;
	private static boolean appliedStepsKnown;
	private static volatile boolean warned;

	/**
	 * 팀별로 마지막으로 알린 단계.
	 *
	 * <p>{@link #appliedSteps} 는 <b>서버 전체에서 가장 높은</b> 단계이고 몹 속성을 다시
	 * 계산할지만 정하는 값이라, 팀별 알림의 기준으로 쓸 수 없다. 이 값은 저장하지 않으므로,
	 * 처음 보는 팀은 <b>지금 단계를 알림 없이 그대로 기준으로 삼는다</b>.
	 */
	private static final Map<UUID, Integer> ANNOUNCED_STEPS = new ConcurrentHashMap<>();

	private DifficultyEscalation() {
	}

	// ------------------------------------------------------------------ 순수 계산

	/** 흐른 시간이 몇 단계인가. 상한에서 멈춘다. */
	public static int stepsFor(int elapsedTicks) {
		if (elapsedTicks <= 0) {
			return 0;
		}
		return Math.min(MAX_STEPS, elapsedTicks / STEP_TICKS);
	}

	/** 그 단계의 배율. 0단계면 정확히 1.0 이라 아무것도 하지 않는 경로를 탄다. */
	public static double multiplierForSteps(int steps) {
		int safe = Math.max(0, Math.min(MAX_STEPS, steps));
		return 1.0 + STEP_BONUS * safe;
	}

	public static double multiplierFor(int elapsedTicks) {
		return multiplierForSteps(stepsFor(elapsedTicks));
	}

	/** 사람에게 보여 줄 상승폭(%). 4, 8, 12 … 100. */
	public static int percentFor(int elapsedTicks) {
		return (int) Math.round((multiplierFor(elapsedTicks) - 1.0) * 100.0);
	}

	/** 다음 단계까지 남은 틱. 이미 상한이면 {@code -1}. */
	public static int ticksToNextStep(int elapsedTicks) {
		if (stepsFor(elapsedTicks) >= MAX_STEPS) {
			return -1;
		}
		int safe = Math.max(0, elapsedTicks);
		return STEP_TICKS - (safe % STEP_TICKS);
	}

	/**
	 * 한 팀의 지금 상태를 사람이 읽을 한 줄로 만든다. {@code /shareteam status} 가 쓴다.
	 */
	public static String describe(TeamState state) {
		if (state == null || !state.difficultyEscalationEnabled) {
			return "끔";
		}
		if (!state.runStarted) {
			return "켬 (게임을 시작하면 그때부터 셉니다)";
		}
		int percent = percentFor(state.difficultyElapsedTicks);
		int remaining = ticksToNextStep(state.difficultyElapsedTicks);
		String next = remaining < 0
				? "상한(+" + (int) Math.round(STEP_BONUS * MAX_STEPS * 100.0) + "%)에 도달"
				: "다음 상승까지 약 " + Math.max(1, (remaining + 1199) / 1200) + "분";
		return "켬 (지금 적대적 몹 +" + percent + "%, " + next + ")";
	}

	/**
	 * 지금 적대적 몹에게 걸려 있는 난이도 배율.
	 *
	 * <p>최대 체력과 공격력에 <b>똑같이</b> 걸린다. 능력치 표시가 증강 배율에 이 값을 곱해
	 * 「몹 체력 100% → 115%」 같은 한 줄을 만든다. 30분마다 저절로 올라가므로, 화면은 값을
	 * 다시 읽기만 하면 그 변화를 따라간다.
	 */
	public static double multiplier() {
		return currentMultiplier;
	}

	/** 이 몹에게 난이도 배율이 걸리는가. 엔더 드래곤만 예외로 뺀다. */
	public static boolean appliesTo(@Nullable Entity entity) {
		if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)) {
			return false;
		}
		return mob.getType() != EntityTypes.ENDER_DRAGON;
	}

	// ------------------------------------------------------------------ 서버 훅

	/**
	 * 팀별로 흐른 시간을 올리고, 단계가 바뀌었으면 이미 올라와 있는 몹을 다시 계산한다.
	 *
	 * <p>팀이 여럿이면 <b>가장 높은 단계</b>를 쓴다. 몹은 어느 팀에도 속하지 않아 팀별로 다른
	 * 체력을 줄 수 없기 때문이다. {@code singleTeamOnly} 를 켜 둔 서버는 팀이 하나뿐이라 그 팀의
	 * 값이 그대로 쓰인다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		// 게임 오버 카운트다운 5초 동안은 시간을 세지 않는다. 회차가 이미 끝났으므로 그 5초는
		// 「회차가 시작된 뒤 흐른 시간」이 아니다.
		if (WorldResetCoordinator.countingDown()) {
			return;
		}
		int highest;
		try {
			highest = advanceAndFindHighest(server);
		} catch (RuntimeException error) {
			warnOnce(error);
			return;
		}
		currentMultiplier = multiplierForSteps(highest);

		if (++tickCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;
		if (appliedStepsKnown && highest == appliedSteps) {
			return;
		}
		appliedSteps = highest;
		appliedStepsKnown = true;
		sweep(server);
	}

	private static int advanceAndFindHighest(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		int highest = 0;
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			// 「게임 시작」을 누르기 전에는 세지 않는다. 이 값의 뜻이 「회차가 시작된 뒤 흐른
			// 시간」이므로, 회차가 아직 시작되지 않았으면 셀 것이 없다.
			if (state == null || !state.difficultyEscalationEnabled || !state.runStarted) {
				continue;
			}
			// 아무도 없는 동안에는 시간이 흐르지 않는다. 하룻밤 자고 왔더니 몹이 두 배가 되어
			// 있는 일을 막는 것이 이 조건의 전부다.
			if (state.difficultyElapsedTicks < MAX_ELAPSED_TICKS && anyOnline(server, team)) {
				state.difficultyElapsedTicks++;
			}
			int steps = stepsFor(state.difficultyElapsedTicks);
			announceIfRaised(server, team, steps);
			highest = Math.max(highest, steps);
		}
		return highest;
	}

	// ------------------------------------------------------------------ 단계 상승 알림

	/**
	 * 이 팀의 단계가 방금 올랐으면 팀 전원에게 알린다.
	 *
	 * <p>매 틱 지나는 자리라 값이 그대로면 첫 줄에서 되돌아간다. 처음 보는 팀은 지금 단계를
	 * 기준으로만 삼고 알리지 않는다 — 서버를 다시 켰을 때 지나온 단계가 한꺼번에 쏟아지는 것을
	 * 막기 위해서다.
	 */
	private static void announceIfRaised(MinecraftServer server, ShareTeam team, int steps) {
		Integer previous = ANNOUNCED_STEPS.get(team.teamId());
		if (previous != null && previous.intValue() == steps) {
			return;
		}
		ANNOUNCED_STEPS.put(team.teamId(), steps);
		if (previous == null || steps <= previous.intValue()) {
			// 처음 보는 팀이거나(재시작 직후) 아직 안 올랐다. 회차가 새로 시작돼 단계가 0 으로
			// 되돌아간 경우도 여기로 떨어져 조용히 기준만 다시 잡는다.
			return;
		}
		Component message = Component.literal(announcementFor(steps))
				.withStyle(ChatFormatting.RED);
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null) {
				online.sendSystemMessage(message);
			}
		}
	}

	/**
	 * 단계가 올랐을 때 띄울 한 줄.
	 *
	 * <p>상한에 닿은 순간에는 <b>더 오르지 않는다는 사실</b>을 함께 적는다. 이 줄이 뜬 뒤로는
	 * {@link #announceIfRaised} 가 다시는 알리지 않는다 — 단계가 더 오르지 않으므로 조건 자체가
	 * 성립하지 않기 때문이다.
	 */
	static String announcementFor(int steps) {
		int percent = (int) Math.round((multiplierForSteps(steps) - 1.0) * 100.0);
		if (steps >= MAX_STEPS) {
			return "몹이 강해졌습니다 — 체력과 공격력 +" + percent + "% (" + steps
					+ "단계, 상한). 더는 강해지지 않습니다.";
		}
		return "몹이 강해졌습니다 — 체력과 공격력 +" + percent + "% (" + steps + "단계)";
	}

	private static boolean anyOnline(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			if (server.getPlayerList().getPlayer(member) != null) {
				return true;
			}
		}
		return false;
	}

	/** 몹이 월드에 올라올 때 최대 체력 수정자를 맞춘다. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		applyHealth(entity);
	}

	/**
	 * 몹이 주는 피해에 난이도 배율을 곱한다.
	 *
	 * <p>{@code LivingEntityPerkDamageMixin} 이 증강 배율을 먹인 <b>뒤에</b> 부른다. 둘 다
	 * 곱셈이라 순서는 결과를 바꾸지 않는다.
	 *
	 * <p>화살·불덩이처럼 던진 것에 맞은 경우에도 {@code DamageSource.getEntity()} 는 쏜 몹을
	 * 가리키므로 여기서 잡힌다.
	 */
	public static float scaleDamage(@Nullable DamageSource source, float amount) {
		double multiplier = currentMultiplier;
		if (multiplier == 1.0 || !(amount > 0.0F) || !Float.isFinite(amount)) {
			return amount;
		}
		try {
			if (!appliesTo(source == null ? null : source.getEntity())) {
				return amount;
			}
		} catch (RuntimeException error) {
			warnOnce(error);
			return amount;
		}
		double scaled = (double) amount * multiplier;
		if (!Double.isFinite(scaled)) {
			return amount;
		}
		return (float) Math.min(scaled, 1.0e9);
	}

	/** 서버가 멈출 때 캐시를 비운다. 다음 월드의 단계를 물려받지 않기 위해서다. */
	public static void reset() {
		currentMultiplier = 1.0;
		tickCounter = 0;
		appliedSteps = 0;
		appliedStepsKnown = false;
		ANNOUNCED_STEPS.clear();
	}

	// ------------------------------------------------------------------ 최대 체력 반영

	/**
	 * 몹 하나의 최대 체력 수정자를 지금 있어야 할 모습으로 맞춘다. 여러 번 불려도 결과가 같다.
	 *
	 * <p>몹 스폰 경로 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다.
	 */
	private static void applyHealth(@Nullable Entity entity) {
		if (!appliesTo(entity)) {
			return;
		}
		Mob mob = (Mob) entity;
		try {
			AttributeInstance instance = mob.getAttribute(Attributes.MAX_HEALTH);
			if (instance == null) {
				return;
			}
			double multiplier = currentMultiplier;
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
			// ADD_MULTIPLIED_TOTAL 은 다른 수정자를 모두 계산한 뒤 (1 + amount) 를 곱한다.
			// 증강 쪽 수정자도 같은 연산이라, 둘이 함께 붙으면 두 배율이 곱해진다.
			instance.addOrUpdateTransientModifier(new AttributeModifier(
					HEALTH_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			settleHealth(mob, before);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/**
	 * 최대 체력이 바뀐 뒤 현재 체력을 정리한다. 가득 차 있었으면 새 최대치로 다시 채우고,
	 * 아니면 넘치는 만큼만 깎는다. 이미 죽어가는 개체는 건드리지 않는다.
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
		if (health >= previousMax || health > max) {
			mob.setHealth(max);
		}
	}

	/** 이미 올라와 있는 몹 전체를 다시 계산한다. 30분에 한 번, 단계가 바뀔 때만 돈다. */
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

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"난이도 상승을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		reset();
		warned = false;
	}
}
