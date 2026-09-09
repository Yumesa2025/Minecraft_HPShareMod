package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.perk.PerkDamage;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.effect.SpreadDamageEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프리즘 「완충」({@code spread_damage})의 실행부. 받는 피해를 그 자리에서 넣지 않고 미뤄 두었다가
 * 몇 초에 걸쳐 나누어 넣는다.
 *
 * <p>{@link SpreadDamageEffect} 는 「몇 초에 걸쳐 나눌 것인가」만 알고, 지금 누가 얼마를 미뤄
 * 두었고 다음 몫이 언제 들어가는지는 여기서 센다.
 *
 * <h2>큐는 팀에 하나다</h2>
 * <p>이 모드는 체력이 팀 공유다. 큐를 사람마다 두면 4인 팀에서 <b>네 갈래가 같은 체력 통에
 * 동시에 흘러든다</b>. 초당 들어가는 양이 팀 인원만큼 곱해지는 것이고, 이 모드에서 되풀이해
 * 터졌던 버그 모양 그대로다. 그래서 키는 언제나 {@code teamId} 이고, 팀원 누가 맞았든 몫은 한
 * 큐에 모인다.
 *
 * <h2>겹치면 합친다 — 큐를 쌓지 않는다</h2>
 * <p>분산 중에 또 맞으면 새 큐를 만들지 않고 <b>남은 몫에 더한 뒤 남은 횟수로 다시 나눈다</b>.
 * 남은 시간은 늘리지 않는다. 맞을 때마다 시간을 늘리면 전투가 이어지는 동안 큐가 영원히 끝나지
 * 않아 회복 금지도 영원해지고, 큐를 여러 개 쌓으면 초당 피해가 한없이 커진다.
 *
 * <h2>미뤄 둔 몫을 다시 넣을 때 또 분산하지 않는다</h2>
 * <p>몫을 다시 넣는 자리는 {@code DELIVERING} 표시를 켜 두고 {@code hurtServer} 를 부른다.
 * {@link com.sharedfate.mixin.LivingEntityPerkDamageMixin} 이 그 표시를 보고 이번 피해는 건드리지
 * 않고 지나 보낸다. 표시가 없으면 다시 넣은 몫이 또 미뤄져 영원히 끝나지 않는다.
 *
 * <p>전용 {@code DamageSource} 를 만들지 않고 표시(플래그)를 고른 이유는 두 가지다. 첫째,
 * 26.2 에서 새 피해 종류는 데이터팩 레지스트리({@code damage_type})에 등록해야 하므로 등록
 * 파일이 늘어난다. 둘째, 그렇게 하면 <b>원래 피해원을 잃는다</b> — 사망 메시지가 「좀비에게
 * 당함」이 아니라 정체불명이 되고, 불·낙하 같은 종류 태그를 보는 다른 증강
 * ({@code damage_taken_from})이 미뤄 둔 몫을 다른 종류로 오해한다. 표시는 원래 피해원을 그대로
 * 들고 다시 넣을 수 있다.
 *
 * <h2>무적시간을 흉내 낸다 — 여기를 빠뜨리면 피해가 몇 배가 된다</h2>
 * <p>가로채는 자리가 {@code hurtServer} 의 <b>맨 앞</b>이라, 바닐라가 「이 피해는 무적시간에
 * 막힌다」고 판단하기 <b>전</b>이다. 그대로 큐에 넣으면 좀비 셋에게 같은 틱에 맞았을 때 바닐라는
 * 한 대만 세는데 큐는 세 대를 전부 센다. 그래서 {@link #gate} 가 바닐라의 판정을 그대로
 * 흉내 내어, <b>바닐라가 실제로 넣었을 몫만</b> 큐에 넣는다. 26.2 의 규칙은 이렇다.
 *
 * <pre>
 *   무적시간 &gt; 10 이고 피해 종류가 bypasses_cooldown 이 아니면
 *       직전 피해량보다 큰 만큼만 들어가고, 무적시간은 그대로다
 *   그렇지 않으면
 *       전부 들어가고, 직전 피해량을 갱신하며 무적시간이 20 으로 찬다
 * </pre>
 *
 * <p>흉내 낸 무적시간을 따로 들고 다니는 이유는, 미뤄 둔 몫을 다시 넣을 때마다 <b>진짜</b>
 * 무적시간이 흔들리기 때문이다. 진짜 값을 그대로 믿으면 몫을 넣을 때마다 무적시간이 새로 차서
 * 분산 중에는 몹에게 거의 맞지 않는 증강이 된다.
 *
 * <h2>회복은 분산이 끝날 때까지 막는다</h2>
 * <p>{@link #blocksHealing} 이 {@code LivingEntity.heal} 진입 시점에 불린다. 자연 회복도 재생
 * 상태이상도 금사과도 그 한 지점을 지나므로 여기서 함께 막힌다. <b>큐가 살아 있는 동안, 팀원
 * 전원에게</b> 걸린다 — 체력이 공유라 누가 회복해도 미뤄 둔 몫이 지워지기 때문이다.
 *
 * <h2>알면서 받아들인 어긋남</h2>
 * <ul>
 *   <li>가로챈 피해는 {@code hurtServer} 에 0 으로 넘어가므로 그 호출이 {@code false} 를
 *       돌려준다. 때린 몹 입장에서는 「맞지 않았다」라서 <b>넉백이 걸리지 않는다</b>. 0 대신
 *       아주 작은 값을 넘기면 넉백은 살릴 수 있지만, 그 값만큼 공유 체력과 흡수가 미세하게
 *       깎이고 장비 내구도가 한 번 더 닳는다. 미뤄 둔 피해는 <b>아직 도착하지 않은 것</b>이므로
 *       0 이 맞다고 보았다.</li>
 *   <li>몫을 넣을 때마다 방어구 내구도가 한 번씩 닳는다. 나눈 횟수만큼 닳는다는 뜻이다. 몫을
 *       1초 간격으로만 넣는 이유가 여기에도 있다.</li>
 *   <li>몫마다 방어구 계산을 다시 지나므로, 한 번에 맞았을 때보다 방어구가 조금 더 많이
 *       깎아 준다. 바닐라의 방어 공식이 작은 피해에 더 후하기 때문이다.</li>
 * </ul>
 */
public final class SpreadDamageManager {
	/** 바닐라가 「아직 무적시간 안이다」로 보는 경계. {@code invulnerableTime > 10} 이다. */
	public static final int INVULNERABLE_GATE_TICKS = 10;
	/** 피해가 들어갔을 때 채워지는 무적시간(틱). */
	public static final int INVULNERABLE_TICKS = 20;

	/** 팀마다 하나뿐인 큐. 키는 {@code teamId} 다. */
	private static final Map<UUID, Spread> ACTIVE = new ConcurrentHashMap<>();

	/**
	 * 지금 미뤄 둔 몫을 다시 넣는 중인가.
	 *
	 * <p>피해 처리는 서버 스레드에서만 돌지만, 표시를 잘못 남기면 모든 피해가 조용히 통과하므로
	 * 스레드마다 따로 두어 새어나갈 길을 없앤다.
	 */
	private static final ThreadLocal<Boolean> DELIVERING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private static boolean warned;

	private SpreadDamageManager() {
	}

	// ------------------------------------------------------------------ 가로채기

	/**
	 * 지금 들어온 피해를 미뤄 둘 것인지 정한다.
	 *
	 * <p>{@link com.sharedfate.mixin.LivingEntityPerkDamageMixin} 이 {@code hurtServer} 진입
	 * 시점에 부른다. 넘어오는 값은 증강 배율과 난이도 배율이 <b>이미 반영된</b> 피해량이다. 배율을
	 * 미리 먹여 두어야 미뤄 둔 몫을 다시 넣을 때 배율을 두 번 곱하지 않는다.
	 *
	 * @return 이번에 실제로 넣을 피해량. 미뤄 두었으면 0, 관여하지 않으면 {@code amount} 그대로
	 */
	public static float intercept(@Nullable Entity victim, @Nullable DamageSource source,
			float amount) {
		if (!(amount > 0.0F) || !Float.isFinite(amount) || isDeliveringSlice()) {
			return amount;
		}
		try {
			return capture(victim, source, amount);
		} catch (RuntimeException error) {
			// 가로채기가 터졌다고 피해 처리가 멈추면 안 된다. 원래 값으로 돌아간다.
			warnOnce(error);
			return amount;
		}
	}

	private static float capture(@Nullable Entity victim, @Nullable DamageSource source,
			float amount) {
		if (!(victim instanceof ServerPlayer player)) {
			return amount;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return amount;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		if (team == null) {
			return amount;
		}
		TeamState state = manager.stateByTeamId(team.teamId());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return amount;
		}
		int slices = sliceCountOf(state);
		if (slices <= 0) {
			return amount;
		}
		// /kill 과 공허는 미루지 않는다. 무적시간조차 무시하는 「관리용」 피해라 늦추면 4초 동안
		// 죽지 않는 플레이어가 생긴다.
		if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		// 어차피 통째로 버려질 피해는 큐에 넣지 않는다. 버리는 판정은 같은 진입점의 @Inject 가
		// 하는데 HEAD 에 붙은 두 주입의 순서는 정해져 있지 않아, 여기서 먼저 물어봐야 「버려질
		// 피해가 4초 뒤에 되살아나는」 일이 없다.
		if (discarded(player, source)) {
			return amount;
		}

		Spread spread = ACTIVE.get(team.teamId());
		Guard guard = spread == null ? null : spread.guards.get(player.getUUID());
		boolean bypassesCooldown = source != null && source.is(DamageTypeTags.BYPASSES_COOLDOWN);
		Gate gate = gate(amount, guard == null ? 0.0F : guard.lastAmount,
				guard == null ? 0 : guard.invulnerableTicks, bypassesCooldown);
		if (!(gate.accepted() > 0.0F)) {
			// 바닐라였어도 들어가지 않았을 피해다. 흉내 낸 상태도 그대로 둔다.
			return 0.0F;
		}

		if (spread == null) {
			spread = new Spread();
			ACTIVE.put(team.teamId(), spread);
		}
		spread.add(player, source, gate, slices);
		return 0.0F;
	}

	/**
	 * 바닐라의 무적시간 판정을 그대로 흉내 낸 순수 계산.
	 *
	 * <p>26.2 {@code LivingEntity.hurtServer} 의 판정과 한 줄씩 대응한다. 월드도 엔티티도 보지
	 * 않으므로 이 규칙만 따로 시험할 수 있다.
	 *
	 * @param amount             이번에 들어온 피해량
	 * @param lastAmount         직전에 받아들인 피해량({@code lastHurt} 에 해당)
	 * @param invulnerableTicks  남은 무적시간({@code invulnerableTime} 에 해당)
	 * @param bypassesCooldown   피해 종류가 {@code bypasses_cooldown} 인가
	 */
	public static Gate gate(float amount, float lastAmount, int invulnerableTicks,
			boolean bypassesCooldown) {
		if (invulnerableTicks > INVULNERABLE_GATE_TICKS && !bypassesCooldown) {
			if (!(amount > lastAmount)) {
				// 바닐라는 여기서 false 를 돌려주고 아무것도 바꾸지 않는다.
				return new Gate(0.0F, lastAmount, invulnerableTicks);
			}
			// 넘치는 만큼만 들어간다. 무적시간은 다시 차지 않는다.
			return new Gate(amount - lastAmount, amount, invulnerableTicks);
		}
		return new Gate(amount, amount, INVULNERABLE_TICKS);
	}

	/**
	 * 남은 몫을 남은 횟수로 나눈 이번 몫.
	 *
	 * <p>마지막 한 번은 나눗셈 오차가 남지 않도록 남은 전부를 넣는다. 그래서 나누어 넣은 합계는
	 * 미뤄 둔 총량과 정확히 같다.
	 */
	public static float sliceAmount(float remaining, int slicesLeft) {
		// 음수·NaN·무한대는 넣을 수 있는 몫이 아니다. 그대로 흘려보내면 바닐라 피해 계산으로
		// 새어나간다.
		if (!(remaining > 0.0F) || !Float.isFinite(remaining)) {
			return 0.0F;
		}
		if (slicesLeft <= 1) {
			return remaining;
		}
		return remaining / slicesLeft;
	}

	/** 이 팀이 가진 {@code spread_damage} 중 가장 긴 것의 몫 개수. 없으면 0. */
	static int sliceCountOf(@Nullable TeamState state) {
		int slices = 0;
		for (SpreadDamageEffect effect : spreadsOf(state)) {
			slices = Math.max(slices, effect.sliceCount());
		}
		return slices;
	}

	/**
	 * 이 팀이 가진 {@code spread_damage} 정의들. 없으면 빈 목록.
	 *
	 * <p>보유 증강과 <b>켜진 세트 효과</b>를 함께 펼친다. {@code PerkSwapRules.effectsOf} 가 팀에게
	 * 효과를 물을 때 쓰는 규칙 그대로다. 세트를 빠뜨리면 세트로 얻은 「완충」이 통째로 무동작이
	 * 되는데, 빌드도 통과하고 로그도 남지 않는다.
	 */
	private static List<SpreadDamageEffect> spreadsOf(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return List.of();
		}
		List<SpreadDamageEffect> found = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			// 풀에서 사라진 id 는 건너뛴다. 증강 정의를 손으로 고칠 수 있는 이상 저장에만 남은
			// id 는 언제든 생긴다.
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof SpreadDamageEffect spread) {
					found.add(spread);
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof SpreadDamageEffect spread) {
				found.add(spread);
			}
		}
		return found;
	}

	/**
	 * 같은 진입점의 다른 처리가 이 피해를 통째로 버릴 것인가.
	 *
	 * <p>버릴 피해를 큐에 넣으면 「없던 피해가 4초 뒤에 생기는」 꼴이 된다. 네 판정 모두 첫 줄에서
	 * 곧바로 빠져나가는 빠른 경로를 갖고 있어, 실제로 「완충」을 가진 팀의 피해에만 얹힌다.
	 */
	private static boolean discarded(ServerPlayer victim, @Nullable DamageSource source) {
		return PerkChoiceSession.blocksDamage(victim)
				|| GameStartManager.blocksDamage(victim)
				|| PerkDamage.blocksFallDamage(victim, source)
				|| SharedEffectDamage.isDuplicateEffectDamage(victim);
	}

	// ------------------------------------------------------------------ 진행

	/** 미뤄 둔 몫이 있는 팀들을 한 틱씩 밀어 준다. */
	public static void tick(@Nullable MinecraftServer server) {
		// 증강 선택 중과 게임 오버 카운트다운 동안에는 진행하지 않는다. 시간이 멈춰 있고 팀원은
		// 창에 갇혀 있어 피할 수도 없다. 남은 몫은 <b>줄지 않고 그대로</b> 기다린다.
		if (server == null || ACTIVE.isEmpty() || PerkChoiceSession.isActive()
				|| WorldResetCoordinator.countingDown()) {
			return;
		}
		try {
			for (UUID teamId : List.copyOf(ACTIVE.keySet())) {
				Spread spread = ACTIVE.get(teamId);
				if (spread != null) {
					stepTeam(server, teamId, spread);
				}
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void stepTeam(MinecraftServer server, UUID teamId, Spread spread) {
		spread.tickGuards();
		// 넣을 몫이 없어도 흉내 낸 무적시간이 남아 있으면 표를 들고 있는다. 큐가 닫히는 순간
		// 그 기억까지 버리면, 큐가 끝나는 그 몇 틱 사이에 맞은 피해가 무적시간을 무시하고
		// 통째로 쌓인다.
		if (!spread.pending()) {
			if (spread.guards.isEmpty()) {
				ACTIVE.remove(teamId);
			}
			return;
		}
		if (--spread.ticksToNextSlice > 0) {
			return;
		}

		ShareTeam team = TeamManager.get(server).teamById(teamId);
		ServerPlayer victim = team == null ? null : pickVictim(server, team, spread);
		if (victim == null) {
			// 받을 사람이 아무도 없다. 남은 몫을 들고 기다리면 다음 접속 때 영문 모를 피해가
			// 되므로 여기서 버린다.
			ACTIVE.remove(teamId);
			return;
		}

		float slice = sliceAmount(spread.remaining, spread.slicesLeft);
		spread.remaining -= slice;
		spread.slicesLeft--;
		deliver(victim, spread.source, slice);

		if (!spread.pending()) {
			spread.closeQueue();
			return;
		}
		spread.ticksToNextSlice = SpreadDamageEffect.SLICE_PERIOD_TICKS;
	}

	/**
	 * 이번 몫을 받을 사람.
	 *
	 * <p>맞은 사람 본인이 첫 번째다. 체력은 어차피 공유라 누가 받아도 팀 체력은 같이 줄지만,
	 * 넉백·방어구 내구도·피격 연출은 받는 사람 것이라 원래 맞은 사람에게 몰아 주는 편이 자연스럽다.
	 * 그 사람이 나가거나 죽었으면 접속해 있는 팀원 아무나로 물러선다.
	 */
	private static @Nullable ServerPlayer pickVictim(MinecraftServer server, ShareTeam team,
			Spread spread) {
		ServerPlayer remembered = spread.victimId == null
				? null : server.getPlayerList().getPlayer(spread.victimId);
		if (alive(remembered)) {
			return remembered;
		}
		for (UUID memberId : team.members()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberId);
			if (alive(member)) {
				return member;
			}
		}
		return null;
	}

	private static boolean alive(@Nullable ServerPlayer player) {
		return player != null && !player.isRemoved() && !player.isDeadOrDying()
				&& !player.isSpectator();
	}

	/**
	 * 미뤄 둔 몫 하나를 실제로 넣는다.
	 *
	 * <p>넣기 직전에 무적시간을 0 으로 만들었다가 <b>원래 값으로 되돌린다</b>. 0 으로 만드는 것은
	 * 이 몫이 직전 피격의 무적시간에 삼켜지지 않게 하기 위해서고, 되돌리는 것은 몫을 넣을 때마다
	 * 무적시간이 새로 차서 「분산 중에는 몹에게 맞지 않는다」가 되지 않게 하기 위해서다.
	 */
	private static void deliver(ServerPlayer victim, @Nullable DamageSource source, float amount) {
		if (!(amount > 0.0F)) {
			return;
		}
		ServerLevel level = victim.level();
		DamageSource actual = source != null ? source : victim.damageSources().generic();
		int saved = victim.invulnerableTime;
		// 피격 표시도 함께 되돌린다. 한 번 맞은 것이 여러 몫으로 나뉘어 들어오는데 몫마다
		// 화면이 붉어지고 소리가 나면 여덟 번 맞은 것처럼 보인다. 소리는 값을 되돌리는 것으로
		// 막을 수 없어 LivingEntityHurtSoundMixin 이 따로 삼킨다.
		int savedHurtTime = victim.hurtTime;
		int savedHurtDuration = victim.hurtDuration;
		DELIVERING.set(Boolean.TRUE);
		try {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, actual, amount);
		} finally {
			DELIVERING.set(Boolean.FALSE);
			victim.invulnerableTime = saved;
			victim.hurtTime = savedHurtTime;
			victim.hurtDuration = savedHurtDuration;
		}
	}

	/** 지금 미뤄 둔 몫을 다시 넣는 중인가. 큐가 하나도 없으면 첫 줄에서 곧바로 거짓이다. */
	public static boolean isDeliveringSlice() {
		return !ACTIVE.isEmpty() && Boolean.TRUE.equals(DELIVERING.get());
	}

	// ------------------------------------------------------------------ 회복 금지

	/**
	 * 이 대상은 지금 회복되지 않는가. {@code LivingEntity.heal} 진입 시점마다 불린다.
	 *
	 * <p>큐가 살아 있는 팀의 <b>팀원 전원</b>이 막힌다. 체력이 공유라 누가 회복해도 미뤄 둔 몫이
	 * 지워지기 때문이다. 자연 회복·재생 상태이상·금사과가 모두 {@code heal} 한 지점을 지나므로
	 * 여기 하나만 막으면 「나뉘어 들어오는 동안 회복되지 않는다」가 성립한다.
	 *
	 * <p>미뤄 둔 몫이 하나도 없으면 맵이 비었는지만 보고 곧바로 거짓이라, 평소 회복 경로에는
	 * 사실상 아무 부담도 얹히지 않는다.
	 *
	 * <p><b>{@code FoodData.tick} 의 자연 회복은 소모도를 먼저 쌓고 회복을 부른다.</b> 회복만
	 * 막으므로 그 4초 동안은 회복 없이 배만 고파진다. {@code no_natural_regen} 이 {@code isHurt}
	 * 를 가로채 그 문제를 피한 것과 다른 점인데, 그러려면 mixin 이 하나 더 필요하고 재생
	 * 상태이상은 또 따로 막아야 한다. 4초짜리 대가에는 지나친 값이라고 보았다.
	 */
	public static boolean blocksHealing(@Nullable LivingEntity entity) {
		if (ACTIVE.isEmpty() || !(entity instanceof ServerPlayer player)) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		ShareTeam team = TeamManager.get(server).teamOf(player.getUUID());
		return team != null && isSpreading(team.teamId());
	}

	/**
	 * 이 팀이 지금 피해를 나누어 받는 중인가.
	 *
	 * <p>표가 남아 있는 것만으로는 참이 아니다. 넣을 몫이 다 들어간 뒤에도 흉내 낸 무적시간이
	 * 다 흐를 때까지는 표가 남아 있는데, 그동안 회복까지 막으면 대가가 약속보다 길어진다.
	 */
	public static boolean isSpreading(@Nullable UUID teamId) {
		Spread spread = teamId == null ? null : ACTIVE.get(teamId);
		return spread != null && spread.pending();
	}

	/** 이 팀이 아직 넣지 않은 피해의 합계. 없으면 0. 화면 표시와 시험에 쓴다. */
	public static float remaining(@Nullable UUID teamId) {
		Spread spread = teamId == null ? null : ACTIVE.get(teamId);
		return spread == null || !spread.pending() ? 0.0F : spread.remaining;
	}

	// ------------------------------------------------------------------ 정리

	/** 서버가 멈출 때 미뤄 둔 몫을 모두 지운다. 다음 월드로 넘어가지 않게 한다. */
	public static void reset() {
		ACTIVE.clear();
		DELIVERING.remove();
		warned = false;
	}

	/** 팀이 전멸·해체될 때 그 팀의 몫만 지운다. */
	public static void forget(@Nullable UUID teamId) {
		if (teamId != null) {
			ACTIVE.remove(teamId);
		}
	}

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DEATH} 에 붙는 지점.
	 *
	 * <p>죽음은 이 모드에서 팀 전멸로 이어진다. 미뤄 둔 몫을 그대로 두면 다음 회차의 첫 몇 초를
	 * 지난 회차의 피해로 시작하게 되므로 통째로 지운다.
	 */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		ShareTeam team = TeamManager.get(server).teamOf(player.getUUID());
		if (team != null) {
			forget(team.teamId());
		}
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"피해 분산을 처리하지 못해 이번 피해는 그대로 들어갑니다. 이 경고는 한 번만 남습니다.",
				error);
	}

	/** 시험이 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		reset();
	}

	// ------------------------------------------------------------------ 자료

	/**
	 * 무적시간 판정의 결과.
	 *
	 * @param accepted          이번에 큐에 넣을 몫. 0 이면 바닐라였어도 들어가지 않았을 피해다
	 * @param lastAmount        판정 뒤의 「직전 피해량」
	 * @param invulnerableTicks 판정 뒤의 남은 무적시간
	 */
	public record Gate(float accepted, float lastAmount, int invulnerableTicks) {
	}

	/**
	 * 팀 하나가 미뤄 둔 몫. 저장하지 않는다 — 서버가 다시 뜨면 미뤄 둔 피해는 사라진다.
	 *
	 * <p>필드를 건드리는 것은 피해 처리와 서버 틱뿐이고 둘 다 서버 스레드라, 안쪽에는 평범한
	 * 자료구조를 쓴다. 팀 사이의 경합만 바깥의 {@code ACTIVE} 가 막는다.
	 */
	private static final class Spread {
		/** 사람마다 흉내 내고 있는 무적시간. 팀 인원만큼만 자란다. */
		final Map<UUID, Guard> guards = new HashMap<>();
		/** 마지막으로 맞은 사람. 몫을 받을 첫 후보다. */
		@Nullable UUID victimId;
		/** 마지막 피해원. 사망 메시지와 피해 종류가 마지막 한 방을 따라간다. */
		@Nullable DamageSource source;
		/** 아직 넣지 않은 몫의 합계. */
		float remaining;
		/** 남은 횟수. 맞을 때마다 늘어나지 않는다 — 남은 시간 안에서 다시 나눌 뿐이다. */
		int slicesLeft;
		int ticksToNextSlice;

		/** 아직 넣을 몫이 남아 있는가. 표가 남아 있는 것과는 다른 물음이다. */
		boolean pending() {
			return slicesLeft > 0 && remaining > 0.0F;
		}

		/**
		 * 받아들인 몫을 더하고 흉내 낸 무적시간을 갱신한다.
		 *
		 * <p>이미 나누는 중이면 <b>남은 횟수와 다음 차례를 그대로 둔다</b>. 그래야 「남은 몫과
		 * 합쳐 남은 시간에 다시 나눈다」가 되고, 맞을 때마다 시간이 늘어나지 않는다.
		 */
		void add(ServerPlayer victim, @Nullable DamageSource damageSource, Gate gate, int slices) {
			if (!pending()) {
				remaining = 0.0F;
				slicesLeft = Math.max(1, slices);
				ticksToNextSlice = SpreadDamageEffect.SLICE_PERIOD_TICKS;
			}
			remaining += gate.accepted();
			victimId = victim.getUUID();
			source = damageSource;
			Guard guard = guards.computeIfAbsent(victim.getUUID(), key -> new Guard());
			guard.lastAmount = gate.lastAmount();
			guard.invulnerableTicks = gate.invulnerableTicks();
		}

		/**
		 * 몫을 다 넣었다. 나눗셈에서 남은 부스러기를 털고 피해원 참조를 놓아 준다.
		 *
		 * <p>표 자체는 흉내 낸 무적시간이 다 흐를 때까지 남는다.
		 */
		void closeQueue() {
			remaining = 0.0F;
			slicesLeft = 0;
			ticksToNextSlice = 0;
			source = null;
			victimId = null;
		}

		/** 흉내 낸 무적시간을 한 틱 흘린다. 다 흐른 사람은 표에서 뺀다. */
		void tickGuards() {
			guards.values().removeIf(guard -> --guard.invulnerableTicks <= 0);
		}
	}

	/** 한 사람의 흉내 낸 무적시간. */
	private static final class Guard {
		float lastAmount;
		int invulnerableTicks;
	}
}
