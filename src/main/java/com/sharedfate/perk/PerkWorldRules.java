package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.NoSleepEffect;
import com.sharedfate.perk.effect.TimeLockEffect;
import com.sharedfate.sync.TitleMessenger;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

/**
 * 월드 자체에 끼어드는 증강들의 판정부.
 *
 * <p>{@link PerkFoodRules} 가 먹기·허기를, {@link PerkGearRules} 가 장비를 맡듯 여기는
 * <b>잠</b>과 <b>시각</b>을 맡는다. 판정을 이벤트 콜백 밖에 떼어 둔 이유도 같다. 등록부에는
 * "어디서 끼어드는가"만 남는 편이 읽기 쉽고, 판정은 월드 없이 시험할 수 있다.
 *
 * <p>여기서 답하는 물음은 둘이다.
 *
 * <ul>
 *   <li>{@link #blocksSleep} — 지금 이 팀이 잠들 수 없는가 ({@code no_sleep})</li>
 *   <li>{@link #lockedDayTime} — 오버월드 시각을 몇으로 붙들어야 하는가 ({@code time_lock})</li>
 * </ul>
 *
 * <p>보유 증강이 하나도 없으면 두 물음 모두 팀 상태 두 번만 보고 곧바로 "해당 없음"이다.
 * 증강을 쓰지 않는 서버에서는 잠자기 경로와 서버 틱에 사실상 아무 부담도 얹히지 않는다.
 *
 * <h2>{@code no_sleep} — 왜 mixin 이 아닌가</h2>
 * <p>Fabric API 의 {@code EntitySleepEvents.ALLOW_SLEEPING} 이
 * {@code Player.startSleepInBed} 한가운데에 이미 들어가 있다. 26.2 의 시그니처는
 * {@code BedSleepingProblem allowSleep(Player, BlockPos)} 이고, {@code null} 을 돌려주면 통과,
 * 문제를 돌려주면 거부다. 우리가 mixin 을 하나 더 얹을 이유가 없다.
 *
 * <p>돌려주는 값은 {@code BedSleepingProblem.OTHER_PROBLEM} 이다. 26.2 의
 * {@code BedSleepingProblem} 은 {@code Component message} 하나짜리 record 이고
 * {@code OTHER_PROBLEM} 만 그 값이 {@code null} 이다. {@code BedBlock} 은 메시지가 있을 때만
 * 액션바에 띄우므로, {@code OTHER_PROBLEM} 을 주면 바닐라 문구가 전혀 뜨지 않고 우리가 보낸
 * 한 줄만 남는다. 다른 값을 주면 "잠들 수 없습니다" 류의 바닐라 문구와 겹쳐 두 번 뜬다.
 *
 * <h2>{@code time_lock} — 왜 게임룰을 끄지 않는가</h2>
 * <p>{@code doDaylightCycle} 을 끄는 방법이 가장 싸다. 한 번 끄면 끝이고 매 틱 아무 일도 하지
 * 않는다. 그런데 게임룰은 <b>월드에 남는 상태</b>다. 증강을 잃거나, 서버가 죽거나, 모드를
 * 지웠을 때 누가 그것을 되돌려 주는지가 없다. 사용자가 {@code /gamerule} 로 직접 정해 둔 값을
 * 덮어쓰는 문제도 있다. "끄기 전 값을 어딘가에 저장했다가 복구한다"로 막을 수는 있지만, 그
 * 저장본이야말로 크래시 한 번에 어긋나는 상태다.
 *
 * <p>반대로 시계를 제자리에 돌려놓는 방식은 <b>남는 상태가 없다.</b> 증강을 잃으면 이 틱이
 * 아무 일도 하지 않게 되고, 그 다음 틱부터 시간은 저절로 다시 흐른다. 되돌릴 것이 없으므로
 * 되돌리기를 잊을 수도 없다. 그래서 이쪽을 골랐다.
 *
 * <h2>매 틱이 아니라 {@value #CHECK_INTERVAL_TICKS} 틱마다 한다</h2>
 * <p>26.2 의 {@code ServerClockManager.setTotalTicks} 는 값을 바꾸는 것으로 끝나지 않는다.
 * 안에서 {@code ClientboundSetTimePacket} 을 <b>접속 중인 전원에게 방송</b>하고, 모든 레벨의
 * {@code EnvironmentAttributeSystem} 캐시를 무효화하며, 저장 데이터를 dirty 로 찍는다.
 * 매 틱 부르면 초당 20번씩 그 일이 벌어진다.
 *
 * <p>{@value #CHECK_INTERVAL_TICKS} 틱마다 되돌리면 시각은 목표와 목표+20 사이를 오간다.
 * 하루가 24000틱이므로 20틱은 하루의 0.083%, 해가 0.3도 움직이는 정도다. 눈으로 구분할 수
 * 없다. 게다가 값이 이미 맞으면 {@code setTotalTicks} 를 아예 부르지 않으므로,
 * {@code doDaylightCycle} 을 사용자가 직접 꺼 둔 서버에서는 첫 한 번 말고는 방송이 없다.
 */
public final class PerkWorldRules {
	/** 시각을 되돌리는 주기. {@code PerkManager} 와 같은 값이다. */
	public static final int CHECK_INTERVAL_TICKS = 20;

	/** 침대에 누우려다 막혔을 때 액션바에 뜨는 한 줄. */
	private static final Component SLEEP_DENIED =
			Component.literal("증강 때문에 잠들 수 없습니다.");

	private static int tickCounter;

	private PerkWorldRules() {
	}

	/** 서버가 멈출 때 주기 상태를 비운다. */
	public static void reset() {
		tickCounter = 0;
	}

	// ------------------------------------------------------------------ 잠 막기

	/**
	 * 이 팀 상태가 {@code no_sleep} 을 갖고 있는가.
	 *
	 * <p>{@link PerkFoodRules#blocks} 와 같은 모양이다. 증강이 꺼져 있거나 하나도 없으면 곧바로
	 * 거짓이다.
	 */
	public static boolean blocksSleep(@Nullable TeamState state) {
		TeamState active = activeState(state);
		if (active == null) {
			return false;
		}
		for (String perkId : active.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoSleepEffect) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * {@code EntitySleepEvents.ALLOW_SLEEPING} 이 부르는 자리.
	 *
	 * <p>통과시킬 때는 {@code null} 을 돌려줘야 한다. 거부할 때만 문제를 돌려주고, 그와 함께
	 * 왜 막혔는지 액션바로 한 줄 알려 준다. 이유를 알려 주지 않으면 침대가 고장 난 줄 안다.
	 *
	 * <p>클라이언트 쪽 플레이어는 팀 상태를 볼 수 없어 언제나 통과다. 어차피 눕기 판정은
	 * 서버에서 다시 한 번 지나가므로 결과는 서버 판정이 정한다.
	 */
	public static @Nullable Player.BedSleepingProblem onAllowSleep(@Nullable Player player,
			@Nullable BlockPos bedPos) {
		if (!(player instanceof ServerPlayer server)) {
			return null;
		}
		if (!blocksSleep(TeamLookup.stateOf(server.getUUID()))) {
			return null;
		}
		TitleMessenger.showActionBar(server, SLEEP_DENIED);
		// 메시지가 null 인 유일한 값이라, 바닐라 문구가 겹쳐 뜨지 않는다.
		return Player.BedSleepingProblem.OTHER_PROBLEM;
	}

	// ------------------------------------------------------------------ 시각 고정

	/**
	 * 이 팀이 붙들어 둔 오버월드 시각. 해당 증강이 없으면 비어 있다.
	 *
	 * <p>여러 개를 가졌으면 <b>가장 작은 값</b>이 이긴다. 어느 쪽을 골라도 자의적이지만, 답이
	 * 보유 순서에 따라 달라지면 같은 팀이 같은 증강을 갖고도 다른 시각에 갇힌다.
	 * {@link PerkHealthRules#lockedMaxHealth} 와 같은 규칙을 써서 답을 하나로 못 박는다.
	 */
	public static OptionalInt lockedDayTime(@Nullable TeamState state) {
		TeamState active = activeState(state);
		if (active == null) {
			return OptionalInt.empty();
		}
		int locked = Integer.MAX_VALUE;
		boolean found = false;
		for (String perkId : active.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof TimeLockEffect lock) {
					locked = Math.min(locked, lock.time());
					found = true;
				}
			}
		}
		return found ? OptionalInt.of(locked) : OptionalInt.empty();
	}

	/**
	 * 지금 이 서버에서 시각을 붙들어야 하는가.
	 *
	 * <p>시계는 서버에 하나뿐이므로 팀이 여럿이면 어느 팀의 값을 쓸지 정해야 한다. 여기서도
	 * 가장 작은 값이 이긴다. 이 모드는 실질적으로 한 팀만 쓰지만, 두 팀이 서로 다른 시각을
	 * 요구할 때 답이 틱마다 흔들리는 것보다는 낫다.
	 */
	public static OptionalInt serverLockedDayTime(@Nullable MinecraftServer server) {
		if (server == null) {
			return OptionalInt.empty();
		}
		TeamManager manager = TeamManager.get(server);
		int locked = Integer.MAX_VALUE;
		boolean found = false;
		for (ShareTeam team : List.copyOf(manager.allTeams())) {
			OptionalInt teamLock = lockedDayTime(manager.stateByTeamId(team.teamId()));
			if (teamLock.isPresent()) {
				locked = Math.min(locked, teamLock.getAsInt());
				found = true;
			}
		}
		return found ? OptionalInt.of(locked) : OptionalInt.empty();
	}

	/**
	 * 지금 누적 틱이 {@code current} 일 때 시계를 돌려놓을 값.
	 *
	 * <p>26.2 의 시계는 하루 안의 시각이 아니라 <b>누적 틱</b>을 들고 있고, 하루 안의 시각은
	 * 그 값을 {@value TimeLockEffect#DAY_LENGTH_TICKS} 로 나눈 나머지다. 그래서 시각만 맞추면
	 * 되는 것이 아니라 "지난 날짜는 그대로 두고 하루 안의 위치만 옮긴" 값을 만들어야 한다.
	 * 날짜를 날려 버리면 그 위에 얹힌 달 위상이 매번 초승달로 되돌아간다.
	 *
	 * <p>바닐라 {@code /time set} 은 누적 틱을 통째로 덮어써 날짜를 지우지만, 그건 사람이 한 번
	 * 치는 명령이라 그래도 된다. 1초마다 도는 이 자리에서 같은 짓을 하면 날짜가 영영 0 에
	 * 붙박인다.
	 *
	 * <p>월드 없이 시험할 수 있게 산술만 떼어 뒀다.
	 */
	public static long lockedTotalTicks(long current, int time) {
		long day = Math.floorDiv(current, TimeLockEffect.DAY_LENGTH_TICKS);
		return day * TimeLockEffect.DAY_LENGTH_TICKS + time;
	}

	/**
	 * {@value #CHECK_INTERVAL_TICKS} 틱마다 오버월드 시계를 제자리로 돌려놓는다.
	 *
	 * <p>{@code SharedFateMod} 의 서버 틱에 붙는다. 붙들 필요가 없으면 시계를 아예 건드리지
	 * 않으므로, 증강을 잃은 순간부터 시간은 저절로 다시 흐른다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++tickCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		OptionalInt locked = serverLockedDayTime(server);
		if (locked.isEmpty()) {
			return;
		}
		try {
			Holder<WorldClock> clock = overworldClock(server);
			if (clock == null) {
				return;
			}
			ServerClockManager clocks = server.clockManager();
			long current = clocks.getTotalTicks(clock);
			long target = lockedTotalTicks(current, locked.getAsInt());
			if (current == target) {
				// 이미 맞아 있으면 방송도 캐시 무효화도 하지 않는다.
				return;
			}
			clocks.setTotalTicks(clock, target);
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("시간 고정 증강을 적용하지 못했습니다.", error);
		}
	}

	/**
	 * 오버월드 시계.
	 *
	 * <p>{@code minecraft:overworld} 시계 하나만 본다. 네더는 차원 정의에 {@code default_clock}
	 * 자체가 없고 엔드는 {@code minecraft:the_end} 를 쓰므로, 이 시계만 건드리면 다른 차원의
	 * 시간 개념은 손대지 않은 채로 남는다.
	 */
	private static @Nullable Holder<WorldClock> overworldClock(MinecraftServer server) {
		return server.registryAccess().get(WorldClocks.OVERWORLD).orElse(null);
	}

	// ------------------------------------------------------------------ 공통

	/** 증강을 켰고 가진 증강이 하나라도 있는 팀 상태. 아니면 null. */
	static @Nullable TeamState activeState(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return null;
		}
		return state;
	}
}
