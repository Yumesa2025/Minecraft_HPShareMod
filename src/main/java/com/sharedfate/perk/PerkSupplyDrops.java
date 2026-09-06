package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.SupplyDropEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code supply_drop} 의 주기를 재고 실제로 보급을 내려 주는 곳.
 *
 * <p>{@link SupplyDropEffect} 는 "얼마나 자주 · 무엇을 · 어떤 확률로"만 알고, "지금 몇 시인가"와
 * "누구에게 넣을 것인가"는 여기서 정한다.
 *
 * <h2>이 매니저가 없으면 효과는 완전 무동작이다</h2>
 * <p>{@link PerkEffectType} 을 {@code switch} 로 소비하는 곳이 한 군데도 없어서, 효과 타입만
 * 만들고 소비자를 안 만들면 정의는 읽히고 목록에도 들어가는데 아무도 찾지 않는다. 빌드도
 * 통과하고 경고 로그도 없다. 그래서 타입과 이 매니저는 언제나 한 벌이다.
 *
 * <h2>⚠ 가장 높은 단계 하나만 실제로 돈다</h2>
 * <p>세트 단계는 <b>누적</b>이다({@link PerkSets#activeTiers}). 「보급」을 넷 모으면 2·3·4 단계가
 * 전부 켜지고 {@link PerkSetEffects#activeEffectsOf} 는 {@code supply_drop} 을 <b>셋</b> 돌려준다.
 * 그것을 그대로 돌리면 <b>보급이 세 번 온다.</b>
 *
 * <p>그래서 {@link #select} 가 후보 중 <b>정확히 하나</b>를 고르고, 한 팀은 한 주기에 그 하나만
 * 돈다. 고르는 규칙은 {@link #select} 에 적어 뒀다. 상위 단계 정의는 하위 단계의 내용을 모두
 * 품고 있어야 한다 — 4단계가 이기면 3단계 정의는 아예 돌지 않는다.
 *
 * <h2>기준 시각</h2>
 * <p>오버월드의 게임 시간({@code ServerLevel#getGameTime()})을 쓴다.
 *
 * <ul>
 *   <li><b>팀 단위</b> — 월드에 하나뿐인 값이라 다른 차원에 있는 팀원도 같은 값을 본다.</li>
 *   <li><b>재시작 후에도 이어짐</b> — {@code MinecraftServer#getTickCount()} 는 서버를 켤 때마다
 *       0부터 다시 센다. 그 값으로 주기를 재면 <b>재시작할 때마다 주기가 처음으로 되감겨</b>
 *       서버를 껐다 켜는 것만으로 보급을 앞당길 수 있다. 게임 시간은 {@code level.dat} 에
 *       저장돼 이어진다.</li>
 *   <li><b>저장할 것이 없음</b> — 주기의 경계는 {@link #cycleAt} 이 게임 시간만으로 계산하는
 *       절대적인 자리다(10분 주기면 12000틱의 배수). 그래서 「다음 보급이 언제인가」를 팀 상태에
 *       저장할 필요가 없고, 저장 형식을 건드리지 않아도 된다.</li>
 * </ul>
 *
 * <p>서버가 멈춰 있는 동안에는 게임 시간도 멈춘다. 즉 <b>꺼져 있던 시간만큼 보급이 밀릴 뿐
 * 어긋나지는 않는다.</b> 아무도 접속하지 않은 사이에 주기만 혼자 돌아 보급이 쌓이는 일도 없다.
 *
 * <h2>누구에게 주는가</h2>
 * <p><b>팀에 한 벌이다. 팀원 수만큼 곱하지 않는다.</b> 이 모드는 접속할 때
 * {@code InventorySwapper.finishJoin} 이 팀원의 인벤토리를 통째로 팀 공유 목록
 * ({@link TeamState#mainItems})으로 바꿔 끼운다. 즉 공유 목록 하나가 곧 팀원 모두의
 * 인벤토리이므로, 한 벌만 넣어도 접속 중인 팀원 전원의 화면에 똑같이 보인다. 사람 수만큼
 * 넣으면 인원수 배로 불어난다.
 *
 * <h2>인벤토리가 꽉 찼을 때</h2>
 * <p><b>바닥에 떨어뜨리지 않는다.</b> 자리가 없는 만큼은 {@link TeamState#overflowItems}(대기열)에
 * 남는다. 대기열은 월드와 함께 저장되고 {@code TeamManager.markDirtyIfActive} 가 공유 칸이 빌
 * 때마다 다시 밀어 넣어 주므로, 인벤토리를 정리하기만 하면 잃어버리지 않고 그대로 받는다.
 * 대기열로 간 것이 있으면 접속 중인 팀원에게 알린다.
 *
 * <h2>접속 안 한 사람</h2>
 * <p>두 가지를 갈라 둔다.
 *
 * <ul>
 *   <li><b>일부만 접속 중</b> — 보급은 공유 목록에 들어가므로 접속 안 한 팀원도 <b>나중에 들어와
 *       그대로 받는다.</b> 접속한 사람 수는 보급의 양에 아무 영향이 없다.</li>
 *   <li><b>한 명도 접속 안 함</b> — 그 회는 <b>건너뛴다.</b> 놓친 회를 쌓아 두지 않는다.</li>
 * </ul>
 *
 * <h2>무엇이 왔는지 알린다</h2>
 * <p>보급은 <b>공유 인벤토리에 조용히 얹히는</b> 것이라 받은 것을 전부 채팅으로 알리고,
 * <b>빈손이었다는 것도 알린다.</b> 문구를 만드는 일은 {@link SupplyDropAnnouncement} 가
 * 맡는다 — 거기에는 「넣기 전에 만들어야 한다」는 함정이 하나 적혀 있다.
 *
 * <h2>다음 보급까지 남은 시간</h2>
 * <p>HUD 가 「보급」 줄 옆에 남은 시간을 띄운다. 그런데 <b>남은 시간을 보내지 않는다.</b>
 * 세트 동기화 패킷에 싣는 것은 {@link #intervalTicksFor} 가 내놓는 <b>주기</b> 하나뿐이고,
 * 남은 시간은 클라이언트가 게임 시간으로 스스로 센다. 그 계산은
 * {@link com.sharedfate.ui.SupplyCountdown} 에 있다.
 */
public final class PerkSupplyDrops {

	/**
	 * 팀마다 마지막으로 본 주기 번호.
	 *
	 * <p>저장하지 않는다. 서버를 켜면 비어 있고, 그 팀을 처음 보는 틱에는 <b>지급하지 않고</b>
	 * 지금 번호를 적어 두기만 한다. 그러지 않으면 서버를 껐다 켜는 것만으로 보급을 한 벌씩
	 * 더 받을 수 있다. 주기의 경계 자체는 게임 시간으로 정해져 있으므로 이렇게 해도 다음 보급
	 * 시각은 어긋나지 않는다.
	 *
	 * @param intervalTicks 그때 쓰던 주기. 이 값이 바뀌면 번호의 뜻이 달라지므로 기준을 다시 잡는다
	 * @param cycle         마지막으로 본 주기 번호
	 */
	private record Schedule(int intervalTicks, long cycle) {
	}

	private static final Map<UUID, Schedule> SCHEDULES = new HashMap<>();

	private static boolean warned;

	private PerkSupplyDrops() {
	}

	/** 서버가 멈출 때 기억을 비운다. */
	public static synchronized void reset() {
		SCHEDULES.clear();
		warned = false;
	}

	// ------------------------------------------------------------------ 주기 계산

	/**
	 * 이 시각이 몇 번째 주기인가. <b>순수 함수다.</b>
	 *
	 * <p>게임 시간만으로 정해지므로 서버를 껐다 켜도, 다른 차원에 있어도, 나중에 이 값을 다시
	 * 계산해도 같은 답이 나온다. 경계는 언제나 {@code intervalTicks} 의 배수라 10분 주기면
	 * 12000·24000·36000틱이다.
	 *
	 * <p>{@code Math.floorDiv} 를 쓴다. 게임 시간이 음수가 되는 일은 없지만, {@code /} 는 음수에서
	 * 0 쪽으로 자르므로 경계가 한 칸 어긋난다.
	 */
	public static long cycleAt(long time, int intervalTicks) {
		if (intervalTicks <= 0) {
			return 0L;
		}
		return Math.floorDiv(time, intervalTicks);
	}

	/** 그 주기가 시작되는 게임 시간. {@link #cycleAt} 의 역이다. */
	public static long cycleStart(long cycle, int intervalTicks) {
		return intervalTicks <= 0 ? 0L : cycle * (long) intervalTicks;
	}

	// ------------------------------------------------------------------ 후보 고르기

	/**
	 * 후보 중 실제로 돌 것 <b>하나</b>를 고른다. 없으면 null.
	 *
	 * <p>고르는 순서는 이렇다.
	 *
	 * <ol>
	 *   <li>{@link SupplyDropEffect#priority()} 가 <b>가장 큰 것</b>. 「보급」 세트는 2·3·4 단계에
	 *       각각 2·3·4 를 적어 두었으므로 넷을 모으면 4단계만 돈다.</li>
	 *   <li>같으면 <b>주기가 짧은 것</b>. 우선순위를 적는 것을 잊어도 「더 자주 오는 쪽」이라는
	 *       상식적인 답이 나온다.</li>
	 *   <li>그래도 같으면 <b>목록에서 나중에 나온 것</b>. {@link PerkSets#activeTiers} 가 단계를
	 *       개수 오름차순으로 주므로 높은 단계가 이긴다.</li>
	 * </ol>
	 *
	 * <p>규칙이 무엇이든 <b>답이 언제나 하나</b>라는 점이 핵심이다. 여럿을 돌리면 보급이 여러 번
	 * 온다.
	 */
	public static @Nullable SupplyDropEffect select(@Nullable Collection<PerkEffect> effects) {
		if (effects == null || effects.isEmpty()) {
			return null;
		}
		SupplyDropEffect best = null;
		for (PerkEffect effect : effects) {
			if (!(effect instanceof SupplyDropEffect candidate)) {
				continue;
			}
			if (best == null || beats(candidate, best)) {
				best = candidate;
			}
		}
		return best;
	}

	/** {@code candidate} 가 {@code best} 를 이기는가. 비기면 나중에 온 쪽(=candidate)이 이긴다. */
	private static boolean beats(SupplyDropEffect candidate, SupplyDropEffect best) {
		if (candidate.priority() != best.priority()) {
			return candidate.priority() > best.priority();
		}
		return candidate.intervalTicks() <= best.intervalTicks();
	}

	/**
	 * 이 팀에서 후보가 되는 효과 전부.
	 *
	 * <p>보유 증강의 효과와 세트 효과를 <b>모두</b> 훑는다.
	 * {@link PerkSetEffects#activeEffectsOf} 를 이어 붙이는 자리가 바로 여기다 — 빠뜨리면 세트가
	 * 아무 일도 안 한다.
	 */
	public static List<PerkEffect> candidatesOf(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return List.of();
		}
		List<PerkEffect> candidates = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof SupplyDropEffect) {
					candidates.add(effect);
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof SupplyDropEffect) {
				candidates.add(effect);
			}
		}
		return candidates;
	}

	/** 이 팀에서 지금 실제로 도는 보급. 없으면 null. */
	public static @Nullable SupplyDropEffect activeFor(@Nullable TeamState state) {
		return select(candidatesOf(state));
	}

	/**
	 * 이 팀에서 지금 도는 보급의 주기(틱). 도는 것이 없으면 <b>0</b>.
	 *
	 * <p>화면에 「다음 보급까지 04:12」를 띄우려고 세트 동기화 패킷에 실어 보내는 값이다.
	 * <b>남은 시간이 아니라 주기</b>를 보내는 것이 핵심이다 — 경계는 게임 시간의 배수라
	 * ({@link #cycleAt}) 주기 하나만 알면 클라이언트가 스스로 남은 시간을 셀 수 있고, 게임
	 * 시간은 바닐라가 이미 초마다 내려보내고 있다. 남은 시간을 실으면 값이 초마다 달라져
	 * {@code PerkSetBroadcaster} 가 0.5초마다 패킷을 한 장씩 내보내게 된다.
	 *
	 * <p>0 이 「보급 세트가 없다」와 「도는 것이 없다」를 함께 뜻한다. 화면이 할 일은 둘 다
	 * 같다 — 시계를 안 그린다.
	 */
	public static int intervalTicksFor(@Nullable TeamState state) {
		SupplyDropEffect effect = activeFor(state);
		return effect == null ? 0 : effect.intervalTicks();
	}

	// ------------------------------------------------------------------ 매 틱

	/**
	 * 팀마다 주기를 재어 경계를 넘었으면 보급을 내린다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다. 실제로 무언가를 하는
	 * 것은 경계를 넘은 그 틱뿐이고, 나머지 틱에는 팀 → 후보 → 주기 번호 비교로 끝난다.
	 */
	public static synchronized void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		try {
			long time = server.overworld().getGameTime();
			TeamManager manager = TeamManager.get(server);
			for (ShareTeam team : manager.allTeams()) {
				TeamState state = manager.stateByTeamId(team.teamId());
				if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
					SCHEDULES.remove(team.teamId());
					continue;
				}
				tickTeam(server, team, state, time);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void tickTeam(MinecraftServer server, ShareTeam team, TeamState state,
			long time) {
		SupplyDropEffect effect = activeFor(state);
		if (effect == null) {
			// 세트가 꺼졌다. 기억을 지워 두면 다시 켜질 때 처음부터 기준을 잡는다.
			SCHEDULES.remove(team.teamId());
			return;
		}

		if (!advance(team.teamId(), effect.intervalTicks(), time)) {
			return;
		}

		List<ServerPlayer> online = onlineMembers(server, team);
		if (online.isEmpty()) {
			// 아무도 없으면 그 회는 건너뛴다.
			return;
		}
		deliver(server, state, effect, online);
	}

	/**
	 * 주기 번호를 한 걸음 옮기고 <b>이번 틱에 보급을 내려야 하는지</b> 답한다.
	 *
	 * <p>답이 무엇이든 <b>기억은 반드시 갱신된다.</b> 그래야 아래에서 무슨 일이 있어도 같은 회에
	 * 두 번 오지 않는다. 명령으로 시간을 크게 옮겨 여러 회를 한꺼번에 건너뛰었어도 보급은
	 * 한 벌이다.
	 *
	 * <p>다음 두 경우에는 <b>지급하지 않고 기준만 잡는다.</b>
	 *
	 * <ul>
	 *   <li><b>이 팀을 처음 본다</b> — 서버를 켠 직후다. 여기서 지급하면 서버를 껐다 켜는 것만으로
	 *       보급을 한 벌씩 더 받을 수 있다. 주기의 경계 자체는 게임 시간으로 정해져 있으므로
	 *       건너뛰어도 다음 보급 시각은 어긋나지 않는다.</li>
	 *   <li><b>주기가 바뀌었다</b> — 4단계가 켜져 10분이 5분이 된 경우다. 번호의 뜻 자체가
	 *       달라져 옛 번호와 비교하는 것이 무의미하다.</li>
	 * </ul>
	 *
	 * <p>{@code synchronized} 인 것은 {@link #reset} 과 겹칠 수 있어서다. 실제로는 서버 스레드
	 * 하나에서만 오간다.
	 */
	static synchronized boolean advance(UUID teamId, int intervalTicks, long time) {
		long cycle = cycleAt(time, intervalTicks);
		Schedule previous = SCHEDULES.put(teamId, new Schedule(intervalTicks, cycle));
		if (previous == null || previous.intervalTicks() != intervalTicks) {
			return false;
		}
		return cycle != previous.cycle();
	}

	/**
	 * 한 회의 보급을 실제로 넣는다.
	 *
	 * <p>난수는 오버월드의 것을 쓴다. 월드에 하나뿐이라 같은 틱에 여러 팀이 받아도 서로 다른
	 * 결과가 나온다.
	 */
	private static void deliver(MinecraftServer server, TeamState state, SupplyDropEffect effect,
			List<ServerPlayer> online) {
		RandomSource random = server.overworld().getRandom();
		List<ItemStack> drawn = effect.roll(random, server.registryAccess());
		if (drawn.isEmpty()) {
			// 꽝이다. 조용히 넘기면 「보급이 고장 났나」로 읽히므로 반드시 알린다.
			announce(online, SupplyDropAnnouncement.nothing());
			return;
		}

		// ⚠ 문구를 먼저 만든다. 아래 insert 가 묶음의 개수를 제자리에서 0 까지 깎으므로,
		// 넣은 뒤에 이름과 개수를 읽으면 「공기 0개」가 적힌다.
		Component received = SupplyDropAnnouncement.receivedFrom(drawn);

		int leftover = insert(state, drawn);
		SharedFateMod.LOGGER.info("[SET] 보급 지급 묶음={} 넘침={} 주기={}분",
				drawn.size(), leftover, effect.intervalMinutes());

		refreshScreens(online);
		announce(online, received);
		if (leftover > 0) {
			announce(online, SupplyDropAnnouncement.overflow(leftover));
		}
	}

	/**
	 * 공유 목록에 밀어 넣는다.
	 *
	 * <p>일단 대기열에 얹고 {@link TeamState#restoreOverflow} 를 부른다. 한 칸 최대치를
	 * 넘는 묶음도 그쪽이 칸 단위로 나눠 넣는다.
	 *
	 * @return 이번에 준 것 중 자리가 없어 대기열에 남은 묶음 수
	 */
	private static int insert(TeamState state, List<ItemStack> drawn) {
		state.overflowItems.addAll(drawn);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		state.overflowItems.removeIf(ItemStack::isEmpty);

		// 원래 있던 넘침까지 세면 안 되므로, 이번에 넣은 그 객체가 남았는지만 본다.
		// restoreOverflow 는 묶음을 새로 만들지 않고 제자리에서 깎으므로 동일성 비교가 성립한다.
		int leftover = 0;
		for (ItemStack stack : drawn) {
			for (ItemStack pending : state.overflowItems) {
				if (pending == stack) {
					leftover++;
					break;
				}
			}
		}
		return leftover;
	}

	/** 공유 목록을 직접 고쳤으니 접속 중인 팀원의 화면을 맞춰 준다. */
	private static void refreshScreens(List<ServerPlayer> online) {
		for (ServerPlayer player : online) {
			if (player.containerMenu != null) {
				player.containerMenu.broadcastChanges();
			}
		}
	}

	private static void announce(List<ServerPlayer> online, Component message) {
		for (ServerPlayer player : online) {
			player.sendSystemMessage(message);
		}
	}

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> online = new ArrayList<>(team.members().size());
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				online.add(player);
			}
		}
		return online;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"보급을 처리하지 못해 이번 틱은 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	// ------------------------------------------------------------------ 시험용

	/** 시험이 기억을 들여다볼 때 쓴다. 아직 본 적 없으면 null. */
	static synchronized @Nullable Long lastCycleForTesting(UUID teamId) {
		Schedule schedule = SCHEDULES.get(teamId);
		return schedule == null ? null : schedule.cycle();
	}
}
