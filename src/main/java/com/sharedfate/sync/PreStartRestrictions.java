package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkWorldRules;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 회차가 시작되기 전에만 걸리는 제한들.
 *
 * <p>{@link GameStartManager} 가 이미 멈춰 둔 것들 — 증강 구간, 위치 교환, 난이도 상승, 전멸 판정
 * ({@link GameStartManager#blocksDamage}) — 에 더해, 시작 전에 팀원이 스폰을 벗어나거나 채집을
 * 시작하거나 밤이 오거나 배가 고파지는 일까지 막는다.
 *
 * <ul>
 *   <li>{@link #applySpawnBorder} — 오버월드 월드보더를 스폰 반경 50칸으로 좁힌다.</li>
 *   <li>{@link #blocksHostileSpawns} — 적대 몹이 한 마리도 생기지 않는다.</li>
 *   <li>{@link #onBeforeBlockBreak} — 블록 파괴를 막고 이유를 액션바로 알린다.</li>
 *   <li>{@link #applyMorningLock} — 오버월드 시각을 1일차 아침(누적 0틱)에 붙든다.</li>
 *   <li>{@link #freezeHunger} — 공유 허기를 가득 찬 값(20 / 5.0F)에 붙든다.</li>
 * </ul>
 *
 * <h2>기준은 「회차가 시작됐는가」 하나다 — 팀이 없어도 걸린다</h2>
 * <p>⚠ <b>{@link GameStartManager#waiting} 을 그대로 쓰면 안 된다.</b> 그 물음은 팀이 없으면
 * 거짓을 돌려준다 — 「대기 중인 회차」라는 것이 없기 때문이다. <b>팀을 아직 만들지 않은 사람도
 * 회차를 시작하지 않은 것</b>이므로 똑같이 걸려야 한다. 그러지 않으면 팀을 만들기 전까지는
 * 스폰 밖으로 나가 땅을 파도 아무도 막지 않는다. 시작 전 규칙은 예외 없이
 * {@link GameStartManager#preStart} 를 본다 — <b>막는 것과 지켜 주는 것(무적)이 같은 물음을
 * 봐야 한다.</b> 한때 이 둘이 어긋나 갇힌 채로 떨어져 죽는 구멍이 났다.
 *
 * <p>그래서 판정이 둘이다.
 * <ul>
 *   <li>{@link #blocksPreStartAction} — <b>사람 하나</b>에 대한 물음. 팀이 없으면 참이다.
 *       블록 파괴와 허기가 이것을 쓴다.</li>
 *   <li>{@link #runNotStarted} — <b>서버 전체</b>에 대한 물음. 팀이 하나도 없어도 참이다.
 *       서버에 하나뿐인 월드보더와 시계가 이것을 쓴다.</li>
 * </ul>
 *
 * <h2>월드보더·시계는 서버에 하나뿐이다</h2>
 * <p>{@link #applySpawnBorder} 와 {@link #applyMorningLock} 모두 <b>아무도 회차를 시작하지
 * 않았으면</b> 오버월드 전체에 건다. 뒤집어 말하면 <b>한 팀이라도 시작하면 풀린다.</b> 이 모드는
 * {@code singleTeamOnly} 로 팀을 하나만 만들게 하므로({@code TeamManager.canCreateNewTeam})
 * 그 설정을 켜 둔 서버에서는 갈릴 일이 없다. 끈 서버에서는 먼저 시작한 팀이 서버 전체의 보더와
 * 시계를 풀어 주고, 아직 시작하지 않은 팀은 블록 파괴와 허기만 묶인 채로 남는다. 시각 쪽은
 * 이미 같은 성질의 선례가 있다 — {@code GameStartManager.resetWorldClock} 의 주석이 시계가
 * 서버에 하나뿐이라 {@code time_lock} 증강과 부딪힐 수 있음을 그대로 적어 뒀다. 월드보더는 이
 * 기능이 처음이라 선례가 없다는 점을 여기 남겨 둔다.
 */
public final class PreStartRestrictions {
	private PreStartRestrictions() {
	}

	/** 서버가 멈출 때 틱 카운터와 알림 쿨다운 기록을 비운다. {@code PerkWorldRules.reset()} 과 같은 자리다. */
	public static void reset() {
		timeCheckTickCounter = 0;
		lastBlockBreakNotice.clear();
	}

	// ================================================================== ① 월드보더

	/** 시작 대기 중 스폰에서 벗어날 수 있는 반경(블록). */
	public static final int SPAWN_LOCK_RADIUS_BLOCKS = 50;

	/**
	 * 스폰 중심·특정 반경으로 월드보더를 걸었을 때와 바닐라 기본값, 둘을 같은 모양으로 담는다.
	 *
	 * <p>{@code size} 는 월드보더 API 그대로 <b>지름</b>이다. 반경만 다루다가 이 값을 그대로
	 * {@code WorldBorder.setSize} 에 넘기면 반경이 절반(25)이 되는 사고가 나므로,
	 * {@link #spawnLockTarget} 한 곳에서만 반경을 지름으로 바꾸고 그 뒤로는 전부 지름으로 다닌다.
	 */
	record BorderTarget(double size, double centerX, double centerZ) {
	}

	/**
	 * 반경(블록)을 월드보더 {@code size}(지름)로 바꾼다.
	 *
	 * <p><b>이 곱셈 하나가 이 기능 전체에서 가장 틀리기 쉬운 자리다.</b> 반경 50을 그대로
	 * {@code setSize} 에 넘기면 지름 50 — 반경 25 — 짜리 보더가 걸린다.
	 */
	static double lockDiameterBlocks(int radiusBlocks) {
		return radiusBlocks * 2.0;
	}

	/** 스폰 {@code (spawnX, spawnZ)} 를 중심으로 {@link #SPAWN_LOCK_RADIUS_BLOCKS} 반경만큼 좁힌 보더. */
	static BorderTarget spawnLockTarget(double spawnX, double spawnZ) {
		return new BorderTarget(lockDiameterBlocks(SPAWN_LOCK_RADIUS_BLOCKS), spawnX, spawnZ);
	}

	/**
	 * 되돌릴 「바닐라 기본값」.
	 *
	 * <p>{@code WorldBorder.Settings.DEFAULT} 가 그 값을 들고 있다 — javap 로 정적 초기화 블록을
	 * 확인해 보면 센터 (0, 0), 지름 {@code 5.9999968E7} 로 만들어진다. 새로 만든 월드의 기본
	 * 보더와 같은 값이라 여기서 다시 정의하지 않고 그대로 가져다 쓴다.
	 *
	 * <p><b>이 값은 「운영자가 손으로 정해 둔 보더」를 기억하지 못한다.</b> {@code /worldborder} 로
	 * 미리 좁혀 둔 서버에서 회차를 시작하면 그 값이 아니라 바닐라 기본값으로 돌아간다.
	 * {@code GameStartManager.resetWorldClock} 도 같은 태도다 — 시계를 되돌릴 때 「이전에 몇 시였는지」를
	 * 기억하지 않고 그냥 0(아침)으로 정한다. 운영자가 보더를 따로 쓰는 서버라면 회차 시작 이후에
	 * 다시 좁혀야 한다.
	 */
	static BorderTarget vanillaDefaultTarget() {
		WorldBorder.Settings defaults = WorldBorder.Settings.DEFAULT;
		return new BorderTarget(defaults.size(), defaults.centerX(), defaults.centerZ());
	}

	/** 지금 보더 값이 목표와 다른가. 셋 중 하나라도 다르면 참. */
	static boolean bordersDiffer(double currentSize, double currentCenterX, double currentCenterZ,
			BorderTarget target) {
		return currentSize != target.size()
				|| currentCenterX != target.centerX()
				|| currentCenterZ != target.centerZ();
	}

	/**
	 * 이 서버에서 회차가 <b>아직 시작되지 않았는가.</b> 월드보더와 시각처럼 서버에 하나뿐인
	 * 것들이 이 물음으로 갈린다.
	 *
	 * <p><b>팀이 하나도 없으면 참이다</b> — 아무도 시작하지 않았다는 뜻이 맞다. 팀이 여럿이면
	 * 하나라도 시작한 순간 거짓이 된다. 시계도 보더도 서버에 하나뿐이라 팀마다 다르게 둘 수
	 * 없고, 이미 굴러가는 회차가 있는데 그쪽 세계를 스폰 50칸으로 조이는 쪽이 훨씬 나쁘다.
	 *
	 * <p>판정 자체는 {@link GameStartManager#anyTeamStarted} 에 있다. 같은 사실을 두 곳에서
	 * 따로 세면 언젠가 서로 어긋난다.
	 */
	static boolean runNotStarted(@Nullable MinecraftServer server) {
		return !GameStartManager.anyTeamStarted(server);
	}

	/**
	 * 회차가 아직 시작되지 않았으면 오버월드 월드보더를 스폰 반경
	 * {@value #SPAWN_LOCK_RADIUS_BLOCKS}칸으로 좁히고, 시작했으면 바닐라 기본값으로 되돌린다.
	 *
	 * <p><b>팀이 하나도 없어도 좁힌다</b>({@link #runNotStarted}). 팀을 만들기 전이라고 해서
	 * 스폰 밖으로 나갈 수 있으면 「시작 전에는 스폰 근처에 머문다」는 규칙에 구멍이 생긴다.
	 *
	 * <p>26.2 의 월드보더는 피해만 주는 것이 아니라 실제 충돌 형상({@code WorldBorder.getCollisionShape})을
	 * 가져 이동 자체를 막는다. {@link GameStartManager#blocksDamage} 가 대기 중 피해를 통째로
	 * 버려도 이 벽은 그것과 무관하게 실제로 못 나가게 막는다 — 화면에 빨간 벽만 보이는 장식이
	 * 아니다.
	 *
	 * <h2>값이 이미 맞으면 아무것도 하지 않는다</h2>
	 * <p>{@code WorldBorder.setSize} 와 {@code setCenter} 는 부를 때마다 저장 데이터를 dirty로
	 * 찍고 접속 중인 전원에게 갱신 패킷을 보낸다({@code BorderChangeListener}). 매 틱 무조건 부르면
	 * 그 일이 초당 20번 벌어지므로, {@link #bordersDiffer} 로 먼저 확인하고 다를 때만 쓴다. 값이
	 * 한 번 맞고 나면 그 뒤로는 매 틱 불러도 비교 두 번만 하고 끝난다 — {@code PerkWorldRules.tick}
	 * 이 20틱마다로 미루는 것과 달리 여기서는 그럴 필요가 없다. 그쪽은 시계가 매 틱 저절로
	 * 흘러 값이 계속 달라지지만, 보더는 우리가 정한 값 그대로 가만히 있어 한 번 맞추면 계속
	 * 맞아 있기 때문이다.
	 *
	 * <p>서버를 껐다 켜도 이 메서드가 매 틱 다시 불리므로, 대기 중이던 팀은 재시작 뒤 첫 틱에
	 * 다시 보더에 걸린다.
	 */
	public static void applySpawnBorder(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		try {
			ServerLevel overworld = server.overworld();
			BorderTarget target;
			if (runNotStarted(server)) {
				LevelData.RespawnData spawn = overworld.getRespawnData();
				BlockPos pos = spawn.pos();
				target = spawnLockTarget(pos.getX() + 0.5, pos.getZ() + 0.5);
			} else {
				target = vanillaDefaultTarget();
			}
			WorldBorder border = overworld.getWorldBorder();
			if (bordersDiffer(border.getSize(), border.getCenterX(), border.getCenterZ(), target)) {
				border.setCenter(target.centerX(), target.centerZ());
				border.setSize(target.size());
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("시작 대기 중 월드보더를 맞추지 못했습니다.", error);
		}
	}

	// ================================================================== ①-2 적대 몹 스폰 금지

	/**
	 * 지금 적대 몹의 자연 스폰을 막아야 하는가. <b>회차가 시작되기 전에는 한 마리도 생기지
	 * 않는다.</b>
	 *
	 * <p>{@code NaturalSpawnerRateMixin} 이 청크마다 이 물음을 한 번씩 던진다. 시작 전에는
	 * 아무도 죽지 않고({@link GameStartManager#blocksDamage}) 스폰 반경 50칸을 벗어날 수도
	 * 없으므로, 그 안에 몹이 쌓이기만 한다. 「게임 시작」을 누르는 순간 준비도 안 된 팀이
	 * 그동안 모인 몹에 둘러싸이는 것이 이 규칙이 없을 때의 모습이다.
	 *
	 * <h2>막는 것은 적대 몹뿐이다</h2>
	 * <p>{@code MobCategory.MONSTER} 만 걸린다. 소·양·주민({@code CREATURE})과 물속 몹은 그대로
	 * 생긴다 — 시작 전에 주변을 둘러보고 자리를 고르는 것이 이 시간에 할 일이고, 동물이 없으면
	 * 그 판단을 할 수 없다. 막는 자리 자체가 갈래를 인자로 받으므로 참조 비교 한 번으로 갈린다.
	 *
	 * <p><b>이미 생겨 있는 몹은 지우지 않는다.</b> 월드가 만들어질 때 생긴 것들은 남아 있고,
	 * 플레이어에게서 멀어지면 바닐라 규칙대로 사라진다. 여기서 막는 것은 「새로 생기는 것」뿐이다.
	 */
	public static boolean blocksHostileSpawns(@Nullable MinecraftServer server) {
		return runNotStarted(server);
	}

	// ================================================================== ② 블록 파괴 금지

	/** 블록 파괴가 막혔을 때 액션바에 뜨는 한 줄. {@code PerkWorldRules.SLEEP_DENIED} 와 같은 자리다. */
	private static final Component BLOCK_BREAK_DENIED =
			Component.literal("게임이 아직 시작되지 않아 블록을 부술 수 없습니다.");

	/**
	 * 같은 사람에게 이 알림을 다시 띄우기까지 최소한으로 기다리는 시간(틱).
	 *
	 * <p>곡괭이를 계속 두드리면 {@code PlayerBlockBreakEvents.BEFORE} 가 그때마다 온다. 매번
	 * 액션바를 새로 보내면 문구가 깜빡이며 도배된다. 1초(20틱)면 "왜 안 부서지지"를 알아차리기에
	 * 충분하고, 그 뒤로 계속 두드려도 초당 한 번만 다시 뜬다.
	 */
	static final long BLOCK_BREAK_NOTICE_COOLDOWN_TICKS = 20;

	/** 플레이어 UUID → 마지막으로 액션바를 띄운 게임 시각(틱). */
	private static final Map<UUID, Long> lastBlockBreakNotice = new HashMap<>();

	/**
	 * 이 사람이 시작 전 제한에 걸리는가. <b>팀이 없어도 걸린다.</b>
	 *
	 * <p>판정은 {@link GameStartManager#preStart} 하나뿐이고 여기서는 이름만 빌려 준다.
	 * <b>같은 사실을 두 곳에서 따로 세면 언젠가 어긋난다</b> — 실제로 한 번 어긋나서, 제한 넷은
	 * 팀 없는 사람까지 막는데 무적만 막아 주지 않아 <b>갇힌 채로 떨어져 죽는</b> 상태가 됐다.
	 * 막는 규칙과 지켜 주는 규칙은 반드시 같은 물음을 봐야 한다.
	 */
	static boolean blocksPreStartAction(@Nullable TeamState state) {
		return GameStartManager.preStart(state);
	}

	/**
	 * 마지막 알림({@code lastNoticeTick})으로부터 {@code cooldownTicks} 이상 지났으면 다시 띄운다.
	 *
	 * <p>{@code nowTick} 이 {@code lastNoticeTick} 보다 작아지는 일(서버 시각이 되감기는 이상 현상)이
	 * 생겨도 무한히 억눌리지 않도록 참으로 본다 — 잘못 억누르는 것보다 한 번 더 띄우는 편이 낫다.
	 */
	static boolean shouldNotifyBlockedBreak(long lastNoticeTick, long nowTick, long cooldownTicks) {
		return nowTick - lastNoticeTick >= cooldownTicks || nowTick < lastNoticeTick;
	}

	/**
	 * {@code PlayerBlockBreakEvents.BEFORE} 가 부르는 자리. 통과시키면 {@code true}.
	 *
	 * <p>Fabric API 0.156.0(26.2)의 시그니처는
	 * {@code boolean beforeBlockBreak(Level, Player, BlockPos, BlockState, @Nullable BlockEntity)} 다.
	 * {@code false} 를 돌려주면 그 블록은 부서지지 않는다.
	 */
	public static boolean onBeforeBlockBreak(Level level, Player player, BlockPos pos,
			BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		if (!blocksPreStartAction(TeamLookup.stateOf(serverPlayer.getUUID()))) {
			return true;
		}
		notifyBlockedBreak(serverPlayer);
		return false;
	}

	private static void notifyBlockedBreak(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = lastBlockBreakNotice.get(player.getUUID());
		if (last != null && !shouldNotifyBlockedBreak(last, now, BLOCK_BREAK_NOTICE_COOLDOWN_TICKS)) {
			return;
		}
		lastBlockBreakNotice.put(player.getUUID(), now);
		TitleMessenger.showActionBar(player, BLOCK_BREAK_DENIED);
	}

	// ================================================================== ③ 시각 고정(아침)

	/**
	 * 시각을 되돌리는 주기. {@link PerkWorldRules#CHECK_INTERVAL_TICKS} 와 같은 20틱이다 — 같은
	 * 이유다. 매 틱 {@code ServerClockManager.setTotalTicks} 를 부르면 접속 중인 전원에게 시각
	 * 패킷이 초당 20번 나가는데, 20틱마다면 시각이 목표와 목표+20 사이(하루의 0.083%)만 오가서
	 * 눈에 보이지 않는다. 값이 이미 맞으면 그마저도 부르지 않는다.
	 */
	private static final int TIME_CHECK_INTERVAL_TICKS = PerkWorldRules.CHECK_INTERVAL_TICKS;

	private static int timeCheckTickCounter;

	/**
	 * 지금 누적 틱이 {@code current} 일 때, 날짜는 그대로 두고 아침(하루 안 시각 0)으로 돌려놓을 값.
	 *
	 * <p>직접 계산하지 않고 {@link PerkWorldRules#lockedTotalTicks} 를 그대로 부른다 — 그 메서드가
	 * 이미 "지난 날짜는 두고 하루 안 위치만 옮긴다"를 해결해 뒀고({@code time_lock} 증강), 아침은
	 * 그 계산에서 시각을 0으로 넣은 것과 같다. 날짜를 함께 날리면 달 위상이 매번 초승달로
	 * 되돌아간다 — 그 사고를 막는 계산을 여기서 다시 베끼지 않는다.
	 */
	static long lockedMorningTicks(long current) {
		return PerkWorldRules.lockedTotalTicks(current, 0);
	}

	/**
	 * 회차가 시작되지 않았으면 {@value #TIME_CHECK_INTERVAL_TICKS}틱마다 오버월드 시각을 아침으로
	 * 되돌린다. {@code SharedFateMod} 의 서버 틱에 붙는다. <b>팀이 하나도 없어도 붙든다.</b>
	 *
	 * <p>회차가 시작된 뒤에는 카운터만 쌓이고 시계는 건드리지 않는다 — 시간이 저절로 다시 흐른다.
	 */
	public static void applyMorningLock(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++timeCheckTickCounter < TIME_CHECK_INTERVAL_TICKS) {
			return;
		}
		timeCheckTickCounter = 0;

		if (!runNotStarted(server)) {
			return;
		}
		try {
			Holder<WorldClock> clock = server.registryAccess().get(WorldClocks.OVERWORLD).orElse(null);
			if (clock == null) {
				return;
			}
			ServerClockManager clocks = server.clockManager();
			long current = clocks.getInstance(clock).totalTicks();
			long target = lockedMorningTicks(current);
			if (current == target) {
				// 이미 아침이면 방송도 캐시 무효화도 하지 않는다.
				return;
			}
			clocks.setTotalTicks(clock, target);
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("시작 대기 중 시각을 아침으로 맞추지 못했습니다.", error);
		}
	}

	// ================================================================== ④ 허기 고정

	/** 시작 대기 중 붙들어 둘 허기 값. {@code GameStartManager.resetRunProgress} 의 회차 시작 값과 같다. */
	public static final int HUNGER_FOOD_LEVEL = 20;
	public static final float HUNGER_SATURATION = 5.0F;

	/** 지금 값이 목표(가득 참)와 다른가. 다를 때만 실제로 손댄다. */
	static boolean needsHungerReset(int foodLevel, float saturation) {
		return foodLevel != HUNGER_FOOD_LEVEL || saturation != HUNGER_SATURATION;
	}

	/**
	 * 회차가 시작되기 전까지 허기를 가득 찬 값으로 붙든다. <b>팀이 없는 사람도 붙든다.</b>
	 *
	 * <p><b>「소모도를 0으로 되돌리는 길」이 아니라 「값을 다시 채우는 길」을 골랐다.</b>
	 * {@code FoodData} 는 소모도(exhaustion)에 {@code addExhaustion(float)} 만 공개하고 되돌리는
	 * 공개 메서드가 없어, 0으로 되돌리려면 {@code Player.causeFoodExhaustion} 에 새 Mixin 을
	 * 걸어야 한다. 배를 채우는 쪽은 공개 API 만으로 끝나고 Mixin 이 하나도 늘지 않는다.
	 *
	 * <h2>{@code StatMirror} 가 팀 공유 값과 개인 값을 잇는다</h2>
	 * <p>{@code StatMirror.tick} 은 이 메서드와 별개로 매 틱 돌면서 팀원 개인의 허기가 지난 틱보다
	 * 얼마나 줄었는지를 보고 그 변화량을 공유 풀에도 반영한다 — {@code runStarted} 여부를 보지
	 * 않는다. 즉 대기 중에도 누군가 배가 고파지면 공유 허기가 함께 줄어든다. 여기서 그 뒤에 다시
	 * 20 / 5.0F 로 맞추고 {@link StatMirror#syncPlayerNow} 로 <b>팀 공유 값과 개인 값을 같은
	 * 호출로</b> 되돌리는 이유가 그것이다 — 팀 공유 값만 고치고 개인 값(플레이어의 실제
	 * {@code FoodData})을 그대로 두면 화면과 실제가 어긋난다.
	 *
	 * <h2>⚠ 인원수만큼 곱해지는 함정을 피했다</h2>
	 * <p>이 코드베이스에서 반복해 터진 자리는 「팀원 각자에게 같은 값을 한 번씩 <b>더할 때</b>」다
	 * ({@code MaxHealthAttribute} 가 잘린 체력을 인원수만큼 합산해 버렸던 사고, 자세한 것은
	 * {@link StatMirror#healthDelta} 의 주석 참고). 여기서는 공유 풀에 <b>더하지 않는다</b> —
	 * {@code state.foodLevel = HUNGER_FOOD_LEVEL} 로 팀 전체에 대해 <b>한 번만 대입</b>하고,
	 * 팀원별로 도는 반복문은 그 확정된 값을 각자에게 <b>내보내기만</b> 한다. 반복문 안에서 값을
	 * 더하거나 계산하는 코드가 없으므로 인원수가 몇 명이든 결과가 달라지지 않는다.
	 */
	public static void freezeHunger(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		// ① 아직 시작하지 않은 팀 — 허기는 팀이 함께 쓰는 값이라 팀 상태를 되돌린다.
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !blocksPreStartAction(state)) {
				continue;
			}
			if (!needsHungerReset(state.foodLevel, state.saturation)) {
				// 이미 가득 차 있으면 아무것도 하지 않는다. 대기 중에는 이쪽이 거의 모든 틱이다.
				continue;
			}
			// 대입은 팀당 한 번뿐이다 — 아래 반복문은 이 값을 내보내기만 한다. 팀원마다 더하면
			// 인원수만큼 곱해진다. 이 코드베이스가 반복해서 빠졌던 함정이다.
			state.foodLevel = HUNGER_FOOD_LEVEL;
			state.saturation = HUNGER_SATURATION;
			manager.setDirty();
			// 되돌린 틱에만 내보낸다. StatMirror.tick 이 이미 매 틱 writeBack 을 하므로, 값이
			// 그대로인 틱까지 여기서 또 쓰면 같은 일을 두 번 하는 것이 된다.
			for (ServerPlayer player : onlineMembers(server, team)) {
				StatMirror.syncPlayerNow(team.teamId(), state, player);
			}
		}

		// ② 아직 팀이 없는 사람 — 되돌릴 공유 값이 없으므로 개인 허기를 직접 채운다.
		//
		// 팀에 든 사람은 ① 이 이미 처리했다. 여기서 또 건드리면 공유 값과 개인 값을 서로 다른
		// 자리에서 쓰게 되어 어느 쪽이 이겼는지 알 수 없어진다 — 그래서 팀이 있으면 건너뛴다.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (TeamLookup.stateOf(player.getUUID()) != null) {
				continue;
			}
			FoodData food = player.getFoodData();
			if (!needsHungerReset(food.getFoodLevel(), food.getSaturationLevel())) {
				continue;
			}
			food.setFoodLevel(HUNGER_FOOD_LEVEL);
			food.setSaturation(HUNGER_SATURATION);
		}
	}

	// ================================================================== 공통

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null && !player.isRemoved()) {
				online.add(player);
			}
		}
		return online;
	}
}
