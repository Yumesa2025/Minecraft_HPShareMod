package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.effect.AbsorptionRechargeEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code absorption_recharge} 증강(프리즘 「흡혈귀」)의 실행부. 주기마다 팀의 공유 흡수 보호막을
 * 꽉 채운다.
 *
 * <p>{@link AbsorptionRechargeEffect} 는 「얼마짜리 방패를 얼마마다」만 알고, 지금이 그 주기인지·
 * 누구에게 상태이상을 붙일지·팀 공유 풀에 얼마를 적을지는 여기서 정한다.
 *
 * <h2>⚠ 흡수는 팀 하나의 공유 자원이다 — 이것이 이 클래스의 핵심이다</h2>
 * <p>{@link TeamState#absorption} 은 <b>팀 전체가 나눠 쓰는 값 하나</b>이고,
 * {@link StatMirror} 가 매 틱 그 값을 접속한 팀원 전원에게 똑같이 써 준다. 그래서 「4하트를
 * 준다」를 팀원마다 한 번씩 더하면 4인 팀에서 32 가 된다. 이 저장소가 되풀이해 당한 모양이다.
 * 그래서 곱해지는 길을 <b>세 겹</b>으로 막는다.
 *
 * <ol>
 *   <li><b>{@link #fill} 은 팀당 정확히 한 번 불린다.</b> {@link #tickTeam} 안에서 팀원 순회
 *       바깥에 있다. 팀원 목록을 도는 곳은 상태이상을 붙이는 자리뿐이고, 거기서는 공유 풀에
 *       손대지 않는다.</li>
 *   <li><b>{@link #fill} 은 더하지 않고 {@code max} 를 쓴다.</b> 「채운다」는 「더한다」가 아니라
 *       「최대치로 맞춘다」이다. 그래서 실수로 팀원 수만큼 불려도 답이 8 그대로다. 덤으로 황금
 *       사과가 준 더 큰 보호막을 깎아 내리지도 않는다.</li>
 *   <li><b>{@link StatMirror#fold} 는 흡수 <em>획득</em>을 합산하지 않고 최댓값만 취한다.</b>
 *       팀원 넷의 흡수량이 동시에 0 → 8 로 뛰어도 공유 풀에 들어가는 획득은 8 이다. 우리가
 *       상태이상을 붙일 때 바닐라 {@code onEffectStarted} 가 각자의 흡수량을 8 로 올리는데,
 *       그 네 번이 32 가 되지 않는 것이 이 규칙 덕분이다.</li>
 * </ol>
 *
 * <h2>왜 {@link StatMirror#tick} <b>뒤</b>에 도는가</h2>
 * <p>{@code StatMirror} 는 「팀원 각자의 흡수량이 지난 틱에 비해 얼마나 변했는가」를 재서 공유
 * 풀에 접어 넣는 사람이다. 우리가 그 앞에서 흡수량을 바꾸면 {@code StatMirror} 는 그것을
 * <b>이번 틱에 새로 얻은 보호막</b>으로 착각하고 공유 풀에 <em>한 번 더</em> 더한다. 8 로
 * 채워 놓은 값에 획득 8 이 또 얹히는 꼴이라, 지금은 최대 흡수량 클램프가 우연히 막아 주지만
 * 그 클램프는 흡수 등급이 바뀌면 함께 커지는 값이라 기댈 것이 못 된다.
 *
 * <p>뒤에 서면 그 착각이 아예 생기지 않는다. 채운 직후에
 * {@link StatMirror#syncPlayerNow} 를 불러 <b>팀 값을 사람에게 써 주고 동시에
 * {@code StatMirror} 의 직전 스냅샷까지 갱신</b>하기 때문이다. 다음 틱의 변화량은 정확히 0 이
 * 되어 이중 계산이 원천적으로 사라진다. 스냅샷을 갱신하지 않으면 재접속한 팀원 한 명 때문에
 * 보호막이 슬금슬금 불어난다 — {@code writeBack} 이 적어 둔 0 과 우리가 써 준 값의 차이가
 * 다음 틱에 「획득」으로 잡히기 때문이다.
 *
 * <p>같은 이유로 이 틱은 피해 판정보다 뒤다. 경계 틱에 맞은 피해는 <b>낡은 보호막</b>이
 * 받아 내고, 새 보호막은 그 위에 얹힌다.
 *
 * <h2>상태이상을 먼저, 흡수량을 나중에</h2>
 * <p>26.2 의 {@code LivingEntity.setAbsorptionAmount} 는 값을
 * {@code [0, getMaxAbsorption()]} 으로 자르고, {@code max_absorption} 속성의 유일한 공급원이
 * 흡수 상태이상이다. 상태이상 없이 흡수량만 올리면 <b>조용히 0 이 된다.</b> 그래서 언제나
 * 상태이상 → 공유 풀 → 되쓰기 순서다. 자세한 근거는 {@link AbsorptionRechargeEffect} 문서에 있다.
 *
 * <h2>다 닳으면 다시 붙이지 않는다</h2>
 * <p>{@code AbsorptionMobEffect} 는 흡수량이 0 이 되면 {@code applyEffectTick} 이 false 를
 * 돌려 스스로 사라진다. 우리는 그 자연스러운 소멸을 <b>이용</b>한다 — 보호막이 다 닳으면
 * 상태이상이 알아서 벗겨지고, 우리는 다음 주기 경계까지 다시 붙이지 않는다. 그래서
 * {@code AbsorptionRechargeEffect.apply} 가 일부러 아무 일도 하지 않는다. 거기서 붙이면
 * 접속·부활·상태이상 재적용마다 보호막이 되살아난다.
 *
 * <p>{@link #tickTeam} 의 보정 갈래가 붙이는 경우는 하나뿐이다 — <b>공유 보호막이 아직 남아
 * 있는데</b> 어떤 팀원에게만 상태이상이 없을 때다. 재접속 직후나
 * {@code EffectSync.refreshPlayer} 가 {@code removeAllEffects} 로 싹 지운 직후가 그렇다.
 * 그 사람만 {@code max_absorption} 이 0 이라 팀 값이 그에게만 0 으로 잘린다.
 *
 * <h2>주기의 기준 시각</h2>
 * <p>오버월드 게임 시간이다. {@link com.sharedfate.perk.PeriodicPerkManager} · {@link ShockwaveManager}
 * 와 같은 이유다 — 월드에 하나뿐이라 팀원끼리 어긋나지 않고, {@code level.dat} 에 저장돼
 * 서버를 껐다 켜도 이어진다.
 *
 * <p>{@link ShockwaveManager} 와 <b>딱 한 가지가 다르다.</b> 저쪽은 팀을 처음 보는 틱에
 * 터뜨리지 않지만, 여기서는 <b>처음 보는 틱에 곧바로 채운다.</b> 방패는 「받는 것」이라
 * 증강을 고르고 5분을 빈손으로 기다리면 증강이 고장 난 것으로 보인다. 대신 서버를 다시 켜면
 * 기억이 비어 있어 한 번 채워지는데, 재시작은 팀원을 모두 내보내는 일이라 이득이라 할 것이
 * 못 된다.
 */
public final class AbsorptionRechargeManager {

	/**
	 * 팀마다 마지막으로 본 주기 번호. <b>이번 세션에만 쓰는 값이라 저장하지 않는다.</b>
	 *
	 * @param periodTicks 그때 쓰던 주기. 값이 바뀌면 번호의 뜻이 달라지므로 번호만 다시 잡는다
	 * @param cycle       마지막으로 본 주기 번호
	 */
	private record Schedule(int periodTicks, long cycle) {
	}

	/** 팀별 주기 기억. 열쇠는 팀 id 다. */
	private static final Map<UUID, Schedule> SCHEDULES = new HashMap<>();

	private static boolean warned;

	private AbsorptionRechargeManager() {
	}

	// ------------------------------------------------------------------ 주기 계산

	/**
	 * 이 시각이 몇 번째 주기인가. <b>순수 함수다.</b>
	 *
	 * <p>{@code Math.floorDiv} 를 쓴다. {@code /} 는 음수에서 0 쪽으로 자르므로 게임 시간이
	 * 음수로 조작된 월드에서 경계가 한 칸 어긋난다.
	 */
	public static long cycleAt(long time, int periodTicks) {
		if (periodTicks <= 0) {
			return 0L;
		}
		return Math.floorDiv(time, periodTicks);
	}

	/**
	 * 주기 번호를 한 걸음 옮기고 <b>이번 틱에 채워야 하는지</b> 답한다.
	 *
	 * <p>답이 무엇이든 <b>기억은 반드시 갱신된다.</b> 그래야 같은 주기에 두 번 채우지 않는다.
	 *
	 * <p>처음 보는 팀이면 {@code true} 다({@link ShockwaveManager#advance} 와 갈리는 지점).
	 * 주기 길이가 달라졌을 때도 번호의 뜻이 달라진 것이므로 새로 잡으며 한 번 채운다.
	 */
	public static synchronized boolean advance(UUID teamId, int periodTicks, long time) {
		long cycle = cycleAt(time, periodTicks);
		Schedule previous = SCHEDULES.put(teamId, new Schedule(periodTicks, cycle));
		if (previous == null || previous.periodTicks() != periodTicks) {
			return true;
		}
		return cycle != previous.cycle();
	}

	/** 이 팀의 주기 기억이 남아 있는가. 시험과 정리 판단에 쓴다. */
	public static synchronized boolean tracks(@Nullable UUID teamId) {
		return teamId != null && SCHEDULES.containsKey(teamId);
	}

	/** 팀이 전멸·해체될 때 그 팀의 기억만 지운다. */
	public static synchronized void forget(@Nullable UUID teamId) {
		if (teamId != null) {
			SCHEDULES.remove(teamId);
		}
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드로 넘어가지 않게 한다. */
	public static synchronized void reset() {
		SCHEDULES.clear();
		warned = false;
	}

	// ------------------------------------------------------------------ 후보 고르기

	/**
	 * 이 팀에서 후보가 되는 효과 전부. 보유 증강과 켜진 세트 효과를 함께 펼친다.
	 *
	 * <p>세트 쪽을 빠뜨리면 나중에 세트가 이 효과를 쓰게 됐을 때 아무 일도 일어나지 않는다.
	 * {@link ShockwaveManager#candidatesOf} 와 같은 규칙이다.
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
				if (effect instanceof AbsorptionRechargeEffect) {
					candidates.add(effect);
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof AbsorptionRechargeEffect) {
				candidates.add(effect);
			}
		}
		return candidates;
	}

	/**
	 * 후보 중 실제로 돌 것 <b>하나</b>를 고른다. 없으면 null.
	 *
	 * <p>여럿을 전부 돌리면 주기가 뒤엉켜 보호막이 아무 때나 찬다. 고르는 순서는 「방패가 큰
	 * 것 → 주기가 짧은 것 → 목록에서 나중에 온 것」이고, 규칙이 무엇이든 <b>답이 언제나
	 * 하나</b>라는 점이 핵심이다.
	 */
	public static @Nullable AbsorptionRechargeEffect select(
			@Nullable Collection<? extends PerkEffect> effects) {
		if (effects == null || effects.isEmpty()) {
			return null;
		}
		AbsorptionRechargeEffect best = null;
		for (PerkEffect effect : effects) {
			if (!(effect instanceof AbsorptionRechargeEffect candidate)) {
				continue;
			}
			if (best == null || beats(candidate, best)) {
				best = candidate;
			}
		}
		return best;
	}

	private static boolean beats(AbsorptionRechargeEffect candidate, AbsorptionRechargeEffect best) {
		if (candidate.shieldAmount() != best.shieldAmount()) {
			return candidate.shieldAmount() > best.shieldAmount();
		}
		return candidate.periodTicks() <= best.periodTicks();
	}

	/** 이 팀에서 지금 실제로 도는 보호막. 없으면 null. */
	public static @Nullable AbsorptionRechargeEffect activeFor(@Nullable TeamState state) {
		return select(candidatesOf(state));
	}

	// ------------------------------------------------------------------ 공유 풀 셈

	/**
	 * 팀의 공유 보호막을 가득 채운다. <b>팀당 한 번만 부른다.</b>
	 *
	 * <p>더하지 않고 {@code max} 를 쓰는 것이 이 함수의 전부다. 「채운다」는 「최대치로 맞춘다」
	 * 이지 「보태 준다」가 아니다. 덕분에
	 *
	 * <ul>
	 *   <li>절반(4)만 남아 있어도 8 로 맞춰지고,</li>
	 *   <li>이미 8 이면 아무 일도 없으며,</li>
	 *   <li>황금 사과로 12 를 두르고 있으면 <b>깎아 내리지 않고</b> 그대로 두고,</li>
	 *   <li>실수로 팀원 수만큼 불려도 답이 8 그대로다.</li>
	 * </ul>
	 *
	 * @return 값이 실제로 달라졌으면 true
	 */
	public static boolean fill(@Nullable TeamState state, float shieldAmount) {
		if (state == null || !Float.isFinite(shieldAmount) || shieldAmount <= 0.0F) {
			return false;
		}
		float current = Float.isFinite(state.absorption) ? state.absorption : 0.0F;
		float target = Math.max(current, shieldAmount);
		if (target == state.absorption) {
			return false;
		}
		state.absorption = target;
		return true;
	}

	/**
	 * 공유 보호막을 지금 팀이 감당할 수 있는 최대치까지 내린다.
	 *
	 * <p>증강을 잃어 상태이상을 걷어낸 직후에 부른다. {@code max_absorption} 이 사라졌는데
	 * 공유 풀에 숫자만 남아 있으면, 팀 화면에는 노란 하트가 보이는데 실제로는 피해를 하나도
	 * 막지 못하는 상태가 된다. {@link StatMirror#applyDeltas} 가 다음 틱에 어차피 같은 일을
	 * 하지만, 그 한 틱을 기다릴 이유가 없고 여기서 하면 이유가 코드에 남는다.
	 *
	 * @param maxAbsorption 접속 중인 팀원의 {@code getMaxAbsorption()} 중 가장 큰 값
	 * @return 값이 실제로 달라졌으면 true
	 */
	public static boolean trimToCapacity(@Nullable TeamState state, float maxAbsorption) {
		if (state == null) {
			return false;
		}
		float capacity = Float.isFinite(maxAbsorption) ? Math.max(0.0F, maxAbsorption) : 0.0F;
		float current = Float.isFinite(state.absorption) ? state.absorption : 0.0F;
		float target = Math.min(current, capacity);
		if (target == state.absorption) {
			return false;
		}
		state.absorption = target;
		return true;
	}

	// ------------------------------------------------------------------ 매 틱

	/**
	 * 팀마다 주기를 재어 경계를 넘었으면 보호막을 채운다.
	 *
	 * <p>서버 틱 한가운데서 불리므로 어떤 예외도 밖으로 내보내지 않는다. 실제로 손을 대는 것은
	 * 경계를 넘은 틱과 상태이상이 어긋난 팀원이 있는 틱뿐이고, 나머지 틱에는 팀 → 후보 →
	 * 주기 번호 비교로 끝난다.
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
				AbsorptionRechargeEffect effect =
						state != null && state.perksEnabled ? activeFor(state) : null;
				if (effect == null) {
					// 증강을 잃었거나 증강을 껐다. 사람에게 붙어 있는 보호막을 여기서 확실히
					// 걷어낸다 — 팀 상태만 버리면 상태이상은 사람 쪽에 그대로 남는다.
					if (SCHEDULES.remove(team.teamId()) != null) {
						release(server, team, state);
					}
					continue;
				}
				tickTeam(server, team, state, effect, time);
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/**
	 * 팀 하나를 한 번 본다.
	 *
	 * <p>팀원 목록을 도는 곳이 둘 있는데 <b>둘 다 공유 풀에 손대지 않는다.</b> 하나는 상태이상을
	 * 붙이는 자리고 하나는 팀 값을 사람에게 써 주는 자리다. 공유 풀을 바꾸는 {@link #fill} 은
	 * 그 바깥에 한 번만 있다.
	 */
	private static void tickTeam(MinecraftServer server, ShareTeam team, TeamState state,
			AbsorptionRechargeEffect effect, long time) {
		List<ServerPlayer> online = sharingMembers(server, team);
		if (online.isEmpty()) {
			// 아무도 없으면 주기 번호도 그대로 둔다. 돌아왔을 때 「그동안 지나간 주기」가
			// 경계로 잡혀 한 번 채워지는 편이 맞다.
			return;
		}

		boolean touched = false;
		if (advance(team.teamId(), effect.periodTicks(), time)) {
			// 상태이상이 먼저다. max_absorption 이 올라가기 전에는 흡수량이 0 으로 잘린다.
			touched |= ensureCapacity(online, effect, effect.shieldAmount());
			// 팀 하나에 한 번. 이 줄이 팀원 순회 안으로 들어가면 4인 팀에서 흡수가 32가 된다.
			touched |= fill(state, effect.shieldAmount());
		} else if (state.absorption > 0.0F) {
			// 보호막이 아직 남아 있는데 상태이상만 없는 사람을 메운다. 재접속이나
			// removeAllEffects 뒤가 그렇다. 남은 보호막이 0 이면 아무것도 붙이지 않는다 —
			// 다 닳은 방패는 다음 주기까지 돌아오지 않는다.
			touched |= ensureCapacity(online, effect, state.absorption);
		}

		if (!touched) {
			return;
		}
		// 팀 값을 사람에게 써 주고, StatMirror 의 직전 스냅샷까지 함께 갱신한다. 상태이상을
		// 붙이는 순간 바닐라가 그 사람의 흡수량을 최대치로 올려 둔 것도 여기서 팀 값으로
		// 되돌아온다. 스냅샷이 갱신되므로 다음 틱의 변화량은 0 이다.
		for (ServerPlayer player : online) {
			StatMirror.syncPlayerNow(team.teamId(), state, player);
		}
	}

	/**
	 * 이만큼의 흡수를 담을 그릇이 없는 팀원에게 보호막 상태이상을 붙인다.
	 *
	 * <p>판단 기준을 「상태이상이 있는가」가 아니라 <b>「담을 수 있는가」</b>로 잡은 이유가 있다.
	 * 황금 사과처럼 <b>더 센 유한 흡수</b>가 이미 걸려 있으면 바닐라
	 * {@code MobEffectInstance.update} 가 우리 것을 그 밑에 숨겨 버린다. 겉에서는 여전히
	 * 「우리 것이 없다」로 보이므로 매 틱 다시 붙이게 되고, 그때마다 {@code EffectSync} 가
	 * 그 추가를 팀 전원에게 퍼뜨린다. 담을 그릇이 이미 충분한지를 보면 그 되풀이가 사라진다.
	 *
	 * <p>{@code getMaxAbsorption()} 은 {@code minecraft:max_absorption} 속성값 그대로이고,
	 * {@code setAbsorptionAmount} 이 값을 자를 때 쓰는 바로 그 상한이다. 곧 이 비교는
	 * 「이 사람에게 팀 값을 써 주면 잘리는가」와 같은 뜻이다.
	 *
	 * @return 한 명이라도 새로 붙였으면 true
	 */
	private static boolean ensureCapacity(List<ServerPlayer> online,
			AbsorptionRechargeEffect effect, float needed) {
		boolean granted = false;
		for (ServerPlayer player : online) {
			if (!effect.hasShield(player) && player.getMaxAbsorption() < needed) {
				granted |= effect.grantShield(player);
			}
		}
		return granted;
	}

	/** 증강이 사라진 팀에서 보호막 상태이상과 남은 공유 흡수를 정리한다. */
	private static void release(MinecraftServer server, ShareTeam team, @Nullable TeamState state) {
		List<ServerPlayer> online = sharingMembers(server, team);
		float capacity = 0.0F;
		for (ServerPlayer player : online) {
			AbsorptionRechargeEffect.stripAnyShield(player);
			capacity = Math.max(capacity, player.getMaxAbsorption());
		}
		if (state == null || !trimToCapacity(state, capacity)) {
			return;
		}
		for (ServerPlayer player : online) {
			StatMirror.syncPlayerNow(team.teamId(), state, player);
		}
	}

	/**
	 * 지금 공유 풀에 참여하고 있는 팀원.
	 *
	 * <p>{@code StatMirror} 가 보는 것과 같은 기준이어야 한다. 죽어 있는 사람에게 보호막을
	 * 붙여 봐야 {@code StatMirror} 는 그를 세지 않아 팀 값과 어긋나기만 한다.
	 */
	private static List<ServerPlayer> sharingMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> online = new ArrayList<>();
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null && !player.isRemoved() && !player.isDeadOrDying()) {
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
				"흡수 보호막 주기를 처리하지 못해 이번 틱은 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}
}
