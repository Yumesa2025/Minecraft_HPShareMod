package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.sync.TitleMessenger;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

/**
 * {@code holder} 증강의 보유자를 정하고 넘기는 곳.
 *
 * <p>{@link HolderEffect} 는 "보유자에게 무엇을 붙이는가"만 알고, "지금 누가 보유자인가"와
 * "언제 누구에게 넘기는가"는 여기서 정한다. {@code periodic} 과 {@link PeriodicPerkManager} 의
 * 관계와 같은 구도다.
 *
 * <h2>보유자는 저장하지 않는다</h2>
 * <p>보유자는 이 클래스의 런타임 메모리에만 있다. {@code TeamState} 에 넣으면 세이브 형식이
 * 바뀌어 기존 월드와의 호환을 따져야 하는데, "지금 누가 버프를 들고 있는가"는 서버가 다시 뜨면
 * 새로 뽑아도 아무 문제가 없는 값이다. 대신 {@link #reset} 이 서버가 멈출 때 반드시 비운다.
 * 비우지 않으면 다음 월드에 이전 회차의 보유자가 남는다.
 *
 * <h2>기준 시각</h2>
 * <p>{@link PeriodicPerkManager} 와 달리 오버월드의 게임 시간을 쓰지 않는다. 순환은 팀에 하나뿐인
 * 상태를 옮기는 일이라 "월드 어디서나 같은 값"이 필요 없고, 오히려
 * {@link TimedPerkEffects} 처럼 자체 카운터를 쓰는 편이 낫다. 게임 시간은 강제 증강 선택 중에
 * 얼어 있어서, 그 시간을 기준으로 삼으면 선택창이 떠 있는 동안의 동작을 따로 설명해야 한다.
 *
 * <p>대신 <b>강제 증강 선택 세션이 살아 있는 동안에는 카운터 자체를 멈춘다.</b> 선택창이 떠 있는
 * 동안 팀원은 움직일 수도 맞을 수도 없으므로, 그동안 보유자가 바뀌면 아무도 그 사실을 겪지
 * 못한 채 버프만 옮겨 다닌다.
 *
 * <h2>보유자를 잃는 경우</h2>
 * <ul>
 *   <li>접속을 끊거나 죽으면 그 자리에서 다른 팀원에게 넘긴다.</li>
 *   <li>넘길 팀원이 없으면(혼자인 팀) 그대로 유지한다.</li>
 *   <li>아무도 접속해 있지 않으면 보유자를 비운다. 누군가 돌아오면 그때 새로 뽑는다.</li>
 * </ul>
 */
public final class PerkHolderManager {
	/** 보유자를 다시 살펴보는 주기. 반 초면 체감상 즉시 반응하는 것과 다르지 않다. */
	private static final int CHECK_INTERVAL_TICKS = 10;

	/** 넘어가지 않은 팀을 정리하는 주기. 자주 할 이유가 없다. */
	private static final int CLEANUP_INTERVAL_TICKS = 600;

	/**
	 * 보유자 하나를 가리키는 열쇠.
	 *
	 * <p>{@link HolderEffect} 객체는 증강 정의 하나당 하나뿐이라 객체 동일성으로 구분된다.
	 * 팀이 여럿이면 팀마다 보유자가 따로 있으므로 팀 식별자도 함께 묶는다.
	 */
	private record Key(HolderEffect effect, UUID teamId) {
	}

	/** 한 팀이 이 효과에 대해 들고 있는 상태. */
	private static final class Holding {
		/** 지금 보유자. 접속한 팀원이 하나도 없으면 null. */
		@Nullable UUID holder;
		/** 보유가 시작된 시각({@link #now} 기준). 순환과 최소 유지 시간의 기준점이다. */
		long since;
	}

	/** 한 증강의 최상위 {@code holder} 효과와 그 증강. 알림 문구에 증강 이름이 필요하다. */
	private record Owned(Perk perk, HolderEffect effect) {
	}

	private static final Map<Key, Holding> HOLDINGS = new ConcurrentHashMap<>();

	/** 자체 틱 카운터. {@link #tick} 이 실제로 진행한 횟수다. */
	private static volatile long now;

	private static int checkCounter;
	private static int cleanupCounter;
	private static volatile boolean warned;

	private PerkHolderManager() {
	}

	// ------------------------------------------------------------------ 조회

	/** 지금까지 센 틱 수. */
	public static long currentTick() {
		return now;
	}

	/**
	 * 이 사람이 이 효과의 보유자인가.
	 *
	 * <p>{@link HolderEffect#damageDealtMultiplier()} 가 부르는 자리다. 한 플레이어는 팀 하나에만
	 * 속하므로, 이 효과의 보유자 목록 어딘가에 그 UUID 가 있으면 곧 자기 팀의 보유자다.
	 * 팀 수만큼만 도는 순회라 비용이 없고, 보유자가 하나도 없는 서버에서는 첫 줄에서 되돌아간다.
	 */
	public static boolean isHolder(@Nullable HolderEffect effect, @Nullable UUID player) {
		if (effect == null || player == null || HOLDINGS.isEmpty()) {
			return false;
		}
		for (Map.Entry<Key, Holding> entry : HOLDINGS.entrySet()) {
			if (entry.getKey().effect() == effect && player.equals(entry.getValue().holder)) {
				return true;
			}
		}
		return false;
	}

	/** 이 팀에서 이 효과의 보유자. 아직 정해지지 않았으면 null. */
	public static @Nullable UUID holderOf(@Nullable HolderEffect effect, @Nullable UUID teamId) {
		if (effect == null || teamId == null) {
			return null;
		}
		Holding holding = HOLDINGS.get(new Key(effect, teamId));
		return holding == null ? null : holding.holder;
	}

	// ------------------------------------------------------------------ 보유자 선정 (순수 함수)

	/**
	 * 다음 보유자를 고른다.
	 *
	 * <p>보유자 선정 규칙 전체가 여기 한 곳에 있다. 마인크래프트 타입을 하나도 쓰지 않으므로
	 * 서버 없이 그대로 시험할 수 있다.
	 *
	 * <ul>
	 *   <li>후보가 없으면 null — 아무도 접속해 있지 않다는 뜻이다.</li>
	 *   <li>지금 보유자를 뺀 후보가 있으면 그중에서 무작위로 고른다. 같은 사람이 연달아
	 *       뽑히지 않게 반드시 뺀다.</li>
	 *   <li>뺐더니 아무도 남지 않으면(혼자인 팀) 지금 보유자를 그대로 유지한다.
	 *       단 지금 보유자가 후보에 없으면(나갔거나 죽었으면) null 이다.</li>
	 * </ul>
	 *
	 * @param current     지금 보유자. 아직 없으면 null
	 * @param candidates  고를 수 있는 사람들. 보통 접속 중인 팀원이다
	 * @param randomBelow {@code n} 을 받아 0 이상 {@code n} 미만의 수를 돌려주는 함수.
	 *                    null 이면 첫 후보를 고른다
	 */
	public static @Nullable UUID chooseNextHolder(@Nullable UUID current,
			@Nullable List<UUID> candidates, @Nullable IntUnaryOperator randomBelow) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		List<UUID> others = new ArrayList<>(candidates.size());
		for (UUID candidate : candidates) {
			if (candidate != null && !candidate.equals(current)) {
				others.add(candidate);
			}
		}
		if (others.isEmpty()) {
			// 넘길 곳이 없다. 지금 보유자가 아직 살아 있으면 그대로 두고, 아니면 비운다.
			return current != null && candidates.contains(current) ? current : null;
		}
		if (others.size() == 1 || randomBelow == null) {
			return others.getFirst();
		}
		// 밖에서 들어온 난수를 그대로 믿지 않는다. 범위를 벗어나도 반드시 후보 안에 떨어진다.
		return others.get(Math.floorMod(randomBelow.applyAsInt(others.size()), others.size()));
	}

	/**
	 * 최소 유지 시간을 채웠는가.
	 *
	 * <p>{@code pass_on_hurt} 로 넘기려면 참이어야 한다. 이 장치가 없으면 받자마자 한 대 맞고
	 * 곧바로 넘어가 버려 버프를 쓸 틈이 없다.
	 */
	public static boolean holdSatisfied(long since, long now, int minHoldTicks) {
		return minHoldTicks <= 0 || now - since >= minHoldTicks;
	}

	/** 순환할 때가 됐는가. {@code rotateTicks} 가 0 이면 시간으로는 절대 넘어가지 않는다. */
	public static boolean rotationDue(long since, long now, int rotateTicks) {
		return rotateTicks > 0 && now - since >= rotateTicks;
	}

	// ------------------------------------------------------------------ 주기 진행

	/**
	 * 보유자를 한 틱 살펴본다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다. 보유자를 실제로
	 * 갈아 끼우는 것은 순환 시각이 됐거나 보유자가 사라졌을 때뿐이고, 나머지 틱에는 카운터만
	 * 오른다. 증강 풀이 비어 있거나 {@code holder} 증강을 가진 팀이 없으면 곧바로 되돌아간다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		// 강제 증강 선택 중에는 시간이 얼어 있고 팀원은 움직일 수도 맞을 수도 없다.
		// 그동안 보유자가 바뀌면 아무도 그 사실을 겪지 못하므로 시계째로 멈춘다.
		if (PerkChoiceSession.isActive()) {
			return;
		}
		now++;
		if (++checkCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		checkCounter = 0;

		try {
			TeamManager manager = TeamManager.get(server);
			for (ShareTeam team : List.copyOf(manager.allTeams())) {
				TeamState state = manager.stateByTeamId(team.teamId());
				if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
					continue;
				}
				for (Owned owned : holdersOf(state)) {
					reconcile(server, team, owned);
				}
			}
			if (++cleanupCounter >= CLEANUP_INTERVAL_TICKS) {
				cleanupCounter = 0;
				forgetStale(manager);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/** 이 팀의 보유자가 아직 유효한지 보고, 아니면 넘기거나 새로 뽑는다. */
	private static void reconcile(MinecraftServer server, ShareTeam team, Owned owned) {
		HolderEffect effect = owned.effect();
		Holding holding = HOLDINGS.computeIfAbsent(
				new Key(effect, team.teamId()), ignored -> new Holding());
		List<UUID> online = onlineMembers(server, team);

		if (online.isEmpty()) {
			// 아무도 없다. 보유자를 비워 두고, 누군가 돌아오면 그때 새로 뽑는다.
			if (holding.holder != null) {
				holding.holder = null;
				holding.since = now;
			}
			return;
		}
		if (holding.holder == null) {
			assign(server, team, owned, holding, chooseNextHolder(null, online, randomOf(server)),
					null, false);
			return;
		}
		if (!online.contains(holding.holder)) {
			// 접속을 끊었다. 곧바로 넘긴다. 이미 자리를 뜬 사람에게 on_pass 를 얹지는 않는다.
			UUID previous = holding.holder;
			assign(server, team, owned, holding,
					chooseNextHolder(previous, online, randomOf(server)), previous, false);
			return;
		}
		if (!rotationDue(holding.since, now, effect.rotateTicks())) {
			return;
		}
		UUID previous = holding.holder;
		UUID next = chooseNextHolder(previous, online, randomOf(server));
		if (next == null || next.equals(previous)) {
			// 혼자인 팀이라 넘길 곳이 없다. 시계만 다시 감는다. 실제로 넘어가지 않았으므로
			// on_pass 도 걸지 않는다.
			holding.since = now;
			return;
		}
		assign(server, team, owned, holding, next, previous, true);
	}

	/**
	 * 보유자를 바꾸고 두 사람의 효과를 갈아 끼운다.
	 *
	 * <p>순서가 중요하다. 이전 보유자에게서 {@code on_holder} 를 먼저 걷어내고
	 * {@code on_others} 를 붙인 다음, 새 보유자를 반대로 바꾸고, 마지막에 {@code on_pass} 를
	 * 얹는다. 걷어내기를 빠뜨리면 속성 수정자가 영구히 남아 팀이 망가지고, {@code on_pass} 를
	 * 먼저 얹으면 갈아 끼우는 {@code remove} 가 그것을 도로 걷어낸다.
	 *
	 * @param grantPass 실제로 다른 사람에게 넘어갔을 때만 참. 이때만 직전 보유자가 {@code on_pass} 를 받는다
	 */
	private static void assign(MinecraftServer server, ShareTeam team, Owned owned, Holding holding,
			@Nullable UUID next, @Nullable UUID previous, boolean grantPass) {
		holding.holder = next;
		holding.since = now;

		HolderEffect effect = owned.effect();
		ServerPlayer previousPlayer = previous == null ? null : server.getPlayerList().getPlayer(previous);
		ServerPlayer nextPlayer = next == null ? null : server.getPlayerList().getPlayer(next);

		if (previousPlayer != null && !previousPlayer.getUUID().equals(next)) {
			effect.applyAs(previousPlayer, false);
		}
		if (nextPlayer != null) {
			effect.applyAs(nextPlayer, true);
		}
		if (grantPass && previousPlayer != null && !previousPlayer.getUUID().equals(next)) {
			effect.grantPassEffects(previousPlayer);
		}
		announce(server, team, owned, nextPlayer);
	}

	/**
	 * 보유자가 바뀌었음을 팀 전원에게 알린다.
	 *
	 * <p>액션바만 쓴다. 순환 주기가 짧은 증강에서는 채팅으로 알리면 1분마다 로그가 한 줄씩
	 * 쌓여 다른 안내를 밀어낸다. 액션바는 덮어써지므로 그런 일이 없다.
	 */
	private static void announce(MinecraftServer server, ShareTeam team, Owned owned,
			@Nullable ServerPlayer holder) {
		String name = holder == null ? null : holder.getPlainTextName();
		Component message = Component.literal(name == null
				? "[증강] " + owned.perk().name() + ": 보유자가 없습니다."
				: "[증강] " + owned.perk().name() + ": 이제 " + name + "님이 보유자입니다.");
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null) {
				TitleMessenger.showActionBar(online, message);
			}
		}
	}

	// ------------------------------------------------------------------ 사건

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DAMAGE} 에 붙는 지점.
	 *
	 * <p>{@code pass_on_hurt} 가 켜진 증강에서 보유자가 피해를 받으면 버프를 다른 팀원에게
	 * 넘긴다. 피해가 들어가는 모든 자리를 지나므로 어떤 예외도 밖으로 내보내지 않고, 보유자가
	 * 하나도 없으면 첫 몇 줄에서 되돌아 나간다.
	 */
	public static void onDamage(LivingEntity victim, DamageSource source,
			float baseDamageTaken, float damageTaken, boolean blocked) {
		try {
			passOnHurt(victim, damageTaken, blocked);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void passOnHurt(LivingEntity victim, float damageTaken, boolean blocked) {
		if (blocked || !(damageTaken > 0.0F) || HOLDINGS.isEmpty()
				|| !(victim instanceof ServerPlayer hurt)) {
			return;
		}
		// 강제 선택 중에는 무적이라 여기까지 오지 않지만, 다른 경로로 들어와도 순환은 멈춘다.
		if (PerkChoiceSession.isActive()) {
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

		for (Owned owned : holdersOf(state)) {
			HolderEffect effect = owned.effect();
			if (!effect.passOnHurt()) {
				continue;
			}
			Holding holding = HOLDINGS.get(new Key(effect, team.teamId()));
			if (holding == null || !hurt.getUUID().equals(holding.holder)) {
				continue;
			}
			if (!holdSatisfied(holding.since, now, effect.minHoldTicks())) {
				// 받자마자 넘어가지 않게 하는 장치다. 아직 이르면 그대로 둔다.
				continue;
			}
			UUID next = chooseNextHolder(holding.holder, onlineMembers(server, team), randomOf(server));
			if (next == null || next.equals(holding.holder)) {
				// 혼자인 팀이라 넘길 곳이 없다. 버프도 디버프도 움직이지 않는다.
				continue;
			}
			assign(server, team, owned, holding, next, holding.holder, true);
		}
	}

	/**
	 * {@code ServerPlayerEvents.LEAVE} 에 붙는 지점.
	 *
	 * <p>주기 점검이 반 초 뒤에 어차피 알아채지만, 그 반 초 동안 아무도 버프를 들고 있지 않은
	 * 상태가 된다. 여기서 즉시 넘긴다.
	 */
	public static void onPlayerLeave(@Nullable ServerPlayer player) {
		try {
			release(player);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/**
	 * {@code ServerLivingEntityEvents.AFTER_DEATH} 에 붙는 지점.
	 *
	 * <p>죽은 사람은 아직 접속해 있으므로 주기 점검이 알아채지 못한다. 여기서 명시적으로 뺀다.
	 */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		try {
			if (entity instanceof ServerPlayer player) {
				release(player);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/**
	 * 이 사람이 들고 있던 보유자 자리를 다른 팀원에게 넘긴다.
	 *
	 * <p>{@code on_pass} 는 걸지 않는다. 접속을 끊었으면 걸어 줄 대상이 없고, 죽었다면 이미
	 * 벌을 받은 셈이라 디버프를 더 얹을 이유가 없다. 팀원이 이 사람뿐이면 보유자는 비워진다.
	 */
	private static void release(@Nullable ServerPlayer player) {
		if (player == null || HOLDINGS.isEmpty()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		TeamState state = manager.stateOf(player.getUUID());
		if (team == null || state == null || state.ownedPerks.isEmpty()) {
			return;
		}

		UUID leaving = player.getUUID();
		List<UUID> candidates = onlineMembers(server, team);
		candidates.remove(leaving);
		for (Owned owned : holdersOf(state)) {
			Holding holding = HOLDINGS.get(new Key(owned.effect(), team.teamId()));
			if (holding == null || !leaving.equals(holding.holder)) {
				continue;
			}
			UUID next = chooseNextHolder(leaving, candidates, randomOf(server));
			if (next == null || next.equals(leaving)) {
				// 넘길 팀원이 없다. 그대로 둔다. 팀원이 한 명뿐인 팀에서는 이게 정답이고,
				// 접속 중인 팀원이 아예 없어지면 주기 점검이 보유자를 비운다.
				continue;
			}
			assign(server, team, owned, holding, next, leaving, false);
		}
	}

	// ------------------------------------------------------------------ 정리

	/**
	 * 서버가 멈출 때 보유자를 모두 비운다.
	 *
	 * <p>보유자는 저장되지 않는 런타임 값이므로, 남겨 두면 다음 월드에 이전 회차의 보유자가
	 * 그대로 딸려 들어간다. {@link PeriodicPerkManager#reset} 과 같은 자리에서 불린다.
	 */
	public static void reset() {
		HOLDINGS.clear();
		now = 0;
		checkCounter = 0;
		cleanupCounter = 0;
		warned = false;
	}

	/** 사라진 팀의 보유자 기록을 버린다. 팀이 해체돼도 자리가 남아 있지 않게 한다. */
	private static void forgetStale(TeamManager manager) {
		HOLDINGS.keySet().removeIf(key -> {
			TeamState state = manager.stateByTeamId(key.teamId());
			return state == null || !state.perksEnabled || state.ownedPerks.isEmpty();
		});
	}

	// ------------------------------------------------------------------ 도우미

	/** 이 팀이 보유한 증강의 최상위 {@code holder} 효과들. */
	private static List<Owned> holdersOf(TeamState state) {
		List<Owned> found = null;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof HolderEffect holder) {
					if (found == null) {
						found = new ArrayList<>(2);
					}
					found.add(new Owned(perk, holder));
				}
			}
		}
		return found == null ? List.of() : found;
	}

	/** 접속 중인 팀원. 부르는 쪽에서 걸러 내므로 고칠 수 있는 목록으로 돌려준다. */
	private static List<UUID> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<UUID> online = new ArrayList<>(team.size());
		for (UUID member : team.members()) {
			if (server.getPlayerList().getPlayer(member) != null) {
				online.add(member);
			}
		}
		return online;
	}

	/** 월드의 난수를 {@link #chooseNextHolder} 가 쓰는 모양으로 감싼다. */
	private static IntUnaryOperator randomOf(MinecraftServer server) {
		RandomSource random = server.overworld().getRandom();
		return bound -> bound <= 0 ? 0 : random.nextInt(bound);
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"보유자형 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	// ------------------------------------------------------------------ 테스트 지원

	/** 테스트가 보유자를 정해 두고 배율이나 조회를 확인할 때 쓴다. */
	static void setHolderForTesting(HolderEffect effect, UUID teamId, @Nullable UUID holder) {
		Holding holding = HOLDINGS.computeIfAbsent(new Key(effect, teamId), ignored -> new Holding());
		holding.holder = holder;
		holding.since = now;
	}

	/** 테스트가 시각을 정해 두고 순환·최소 유지 판정을 확인할 때 쓴다. */
	static void setCurrentTickForTesting(long time) {
		now = time;
	}
}
