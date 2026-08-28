package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PeriodicPerkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 일정 주기로 켜졌다 꺼지는 효과.
 *
 * <p>한 주기({@code period_ticks})를 {@code phases} 로 나눠 순서대로 돈다. 구간의 {@code ticks}
 * 합이 주기보다 짧으면 남는 시간은 구간이 없는 시간이고, 그동안에는 {@code base} 만 걸린다.
 * 정의 예시는 다음과 같다.
 *
 * <pre>{@code
 * { "type": "periodic", "period_ticks": 600,
 *   "phases": [ { "ticks": 200, "effects": [ ...신속 I... ] },
 *               { "ticks": 100, "effects": [ ...구속 I... ] } ],
 *   "base":   [ ...상시 효과... ] }
 * }</pre>
 *
 * <h2>주기의 기준 시각</h2>
 * <p>구간 판정은 오버월드의 게임 시간({@code getGameTime()})만 본다. 팀원마다 따로 도는 일이
 * 없어야 하므로 사람 단위 기준점은 쓸 수 없고, 서버를 껐다 켜면 0으로 돌아가는
 * {@code MinecraftServer#getTickCount()} 도 쓸 수 없다. 게임 시간은 월드에 저장돼 재시작 후에도
 * 이어지고, 모든 차원과 모든 팀원이 같은 값을 본다. 실제 시각 계산은
 * {@link PeriodicPerkManager} 가 맡고 이 클래스는 받은 시각으로 판단만 한다.
 *
 * <h2>base 와 phase 가 겹칠 때</h2>
 * <p>구간이 이긴다. 구간이 어떤 갈래의 효과를 하나라도 가지고 있으면 그 갈래의 {@code base}
 * 효과는 그 구간 동안 꺼진다. 갈래는 {@link #conflictKey} 가 정하는데, 상태이상은 종류를 가리지
 * 않고 하나의 갈래로 묶고 속성은 같은 속성일 때만 겹친 것으로 본다. 힘↔나약함, 신속↔구속처럼
 * 상태이상은 서로 반대되는 짝이 있어 종류가 달라도 함께 걸리면 뜻이 어긋나지만, 속성은 서로
 * 독립이라 같은 속성이 아니면 함께 있어도 된다. 이 규칙 덕분에 "힘 II·신속 II 상시, 30초마다
 * 5초간 나약함 III·구속 III" 같은 증강이 base + phase 한 줄로 그대로 적힌다.
 *
 * <h2>왜 매 틱 붙였다 떼지 않는가</h2>
 * <p>플레이어별로 마지막에 적용한 구간 번호를 기억해 두고 구간이 바뀐 틱에만 손을 댄다.
 * 매 틱 수정자를 뗐다 붙이면 최대 체력 수정자가 흔들려 현재 체력이 깎이고, 상태이상 추가·제거
 * 이벤트가 초당 20번 돌아 상태이상 공유까지 요동친다.
 */
public final class PeriodicEffect implements PerkEffect {
	/** 어떤 구간에도 들지 않는 시간. 이때는 {@code base} 만 걸린다. */
	public static final int NO_PHASE = -1;

	/** 주기 상한. 한 시간이면 어떤 증강이라도 충분하고, 실수로 적은 큰 값을 걸러 준다. */
	private static final int MAX_PERIOD_TICKS = 72_000;
	/** 한 주기 안의 구간 개수 상한. */
	private static final int MAX_PHASES = 16;
	/**
	 * 하위 효과에 줄 순번의 간격.
	 *
	 * <p>속성 수정자 식별자가 {@code perkId + index} 로 만들어지므로
	 * ({@link AttributeEffect#modifierId}) 하위 효과의 순번은 부모나 형제와 절대 겹치면 안 된다.
	 * 부모 순번 p 의 n번째 하위 효과에 {@code (p + 1) * 100 + n} 을 준다. {@code base} 와 모든
	 * 구간이 n 하나를 나눠 쓰므로 구간끼리도 겹치지 않고, {@code +1} 덕분에 0번 효과의 자식이
	 * 최상위 순번(0,1,2,…)과 겹치지 않는다.
	 *
	 * <p>{@link ConditionalEffect} 와 같은 식·같은 간격을 쓴다. 두 효과가 한 증강에 함께 있어도
	 * 부모 순번이 다르면 자식 순번 묶음도 달라 서로 침범하지 않는다. 최상위 효과 100개,
	 * 하위 효과 100개를 넘기면 이 보장이 깨지므로 그 앞에서 정의를 버린다.
	 */
	private static final int CHILD_INDEX_STRIDE = 100;

	/** 한 주기 안의 구간 하나. */
	public record Phase(int ticks, List<PerkEffect> effects) {
		public Phase {
			effects = List.copyOf(effects);
		}
	}

	private final int periodTicks;
	private final List<Phase> phases;
	private final List<PerkEffect> base;

	/** 구간 번호별로 그 시간에 실제로 걸려 있어야 할 효과들. base 억제까지 미리 반영해 둔다. */
	private final List<List<PerkEffect>> activeByPhase;
	/** base 와 모든 구간의 효과를 한데 모은 것. 구간이 바뀔 때 걷어낼 후보다. */
	private final List<PerkEffect> allEffects;

	/**
	 * 플레이어별로 마지막에 적용한 구간 번호.
	 *
	 * <p>구간이 그대로면 아무 일도 하지 않기 위한 기억이다. 접속 중인 사람만 들어가고,
	 * 나간 사람의 자리는 {@link #forgetIf} 로 정리한다.
	 */
	private final Map<UUID, Integer> appliedPhase = new ConcurrentHashMap<>();

	public PeriodicEffect(int periodTicks, List<Phase> phases, List<PerkEffect> base) {
		this.periodTicks = periodTicks;
		this.phases = List.copyOf(phases);
		this.base = List.copyOf(base);

		List<List<PerkEffect>> active = new ArrayList<>(this.phases.size());
		for (Phase phase : this.phases) {
			List<PerkEffect> merged = new ArrayList<>(phase.effects());
			for (PerkEffect baseEffect : this.base) {
				if (!suppressedBy(baseEffect, phase.effects())) {
					merged.add(baseEffect);
				}
			}
			active.add(List.copyOf(merged));
		}
		this.activeByPhase = List.copyOf(active);

		List<PerkEffect> everything = new ArrayList<>(this.base);
		for (Phase phase : this.phases) {
			everything.addAll(phase.effects());
		}
		this.allEffects = List.copyOf(everything);
	}

	// ------------------------------------------------------------------ 정의 읽기

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int periodTicks = PerkEffectType.readInt(json, "period_ticks", 0);
		if (periodTicks <= 0 || periodTicks > MAX_PERIOD_TICKS) {
			SharedFateMod.LOGGER.warn("증강 {}: periodic 효과의 period_ticks 가 없거나 범위를 벗어났습니다 ({})",
					perkId, periodTicks);
			return null;
		}
		// 순번이 이 범위를 넘었다는 것은 다른 효과의 하위로 들어갔다는 뜻이다. 그렇게 들어간
		// 주기는 아무도 틱을 돌려 주지 않아 영영 멈춰 있으므로 여기서 거른다.
		if (index < 0 || index >= CHILD_INDEX_STRIDE) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: periodic 효과는 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}

		JsonElement phasesElement = json.get("phases");
		if (phasesElement == null || !phasesElement.isJsonArray()
				|| phasesElement.getAsJsonArray().isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: periodic 효과에 phases 가 비어 있습니다", perkId);
			return null;
		}
		JsonArray phasesArray = phasesElement.getAsJsonArray();
		if (phasesArray.size() > MAX_PHASES) {
			SharedFateMod.LOGGER.warn("증강 {}: periodic 효과의 구간이 너무 많습니다 ({})",
					perkId, phasesArray.size());
			return null;
		}

		// base 와 모든 구간이 하나의 순번 흐름을 나눠 쓴다. 이러면 하위 효과끼리도 겹치지 않는다.
		int[] childCounter = {0};

		List<PerkEffect> base = List.of();
		JsonElement baseElement = json.get("base");
		if (baseElement != null && !baseElement.isJsonNull()) {
			if (!baseElement.isJsonArray()) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과의 base 가 배열이 아닙니다", perkId);
				return null;
			}
			base = parseEffects(perkId, index, childCounter, baseElement.getAsJsonArray(), "base");
			if (base == null) {
				return null;
			}
		}

		List<Phase> phases = new ArrayList<>(phasesArray.size());
		long total = 0;
		for (int i = 0; i < phasesArray.size(); i++) {
			JsonElement raw = phasesArray.get(i);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과의 {}번째 구간이 객체가 아닙니다", perkId, i);
				return null;
			}
			JsonObject phaseJson = raw.getAsJsonObject();
			int ticks = PerkEffectType.readInt(phaseJson, "ticks", 0);
			if (ticks <= 0 || ticks > periodTicks) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: periodic 효과의 {}번째 구간 ticks 가 없거나 범위를 벗어났습니다 ({})",
						perkId, i, ticks);
				return null;
			}
			total += ticks;
			if (total > periodTicks) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: periodic 효과의 구간 길이 합({})이 period_ticks({})를 넘습니다",
						perkId, total, periodTicks);
				return null;
			}

			JsonElement effectsElement = phaseJson.get("effects");
			if (effectsElement == null || !effectsElement.isJsonArray()
					|| effectsElement.getAsJsonArray().isEmpty()) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과의 {}번째 구간에 effects 가 비어 있습니다",
						perkId, i);
				return null;
			}
			List<PerkEffect> effects = parseEffects(perkId, index, childCounter,
					effectsElement.getAsJsonArray(), i + "번째 구간");
			if (effects == null) {
				return null;
			}
			phases.add(new Phase(ticks, effects));
		}

		return new PeriodicEffect(periodTicks, phases, base);
	}

	/**
	 * 하위 효과 목록을 읽는다. 하나라도 잘못됐으면 {@code null} 을 돌려 증강 전체를 버린다.
	 *
	 * <p>{@link PerkEffectType} 을 그대로 다시 부르므로 하위 효과에는 어떤 타입이든 적을 수 있다.
	 * 다만 {@code periodic} 만은 막는다. 안쪽 주기는 아무도 틱을 돌려 주지 않아 영영 멈춰 있게
	 * 되므로, 조용히 동작하지 않는 것보다 정의를 읽을 때 걸러 내는 편이 낫다.
	 */
	private static @Nullable List<PerkEffect> parseEffects(String perkId, int parentIndex,
			int[] childCounter, JsonArray array, String where) {
		List<PerkEffect> effects = new ArrayList<>(array.size());
		for (JsonElement raw : array) {
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과 {} 의 하위 효과가 객체가 아닙니다",
						perkId, where);
				return null;
			}
			JsonObject effectJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(effectJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과 {} 의 알 수 없는 하위 type 입니다 ({})",
						perkId, where, typeId);
				return null;
			}
			if (type == PerkEffectType.PERIODIC) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과 안에 periodic 을 넣을 수 없습니다", perkId);
				return null;
			}
			if (childCounter[0] >= CHILD_INDEX_STRIDE) {
				SharedFateMod.LOGGER.warn("증강 {}: periodic 효과의 하위 효과가 너무 많습니다", perkId);
				return null;
			}
			int childIndex = (parentIndex + 1) * CHILD_INDEX_STRIDE + childCounter[0]++;
			PerkEffect effect = type.create(perkId, childIndex, effectJson);
			if (effect == null) {
				return null;
			}
			effects.add(effect);
		}
		return effects;
	}

	// ------------------------------------------------------------------ 구간 판단

	/** 이 시각에 해당하는 구간 번호. 어떤 구간에도 들지 않으면 {@link #NO_PHASE}. */
	public int phaseAt(long time) {
		if (periodTicks <= 0 || phases.isEmpty()) {
			return NO_PHASE;
		}
		long offset = Math.floorMod(time, (long) periodTicks);
		long start = 0;
		for (int i = 0; i < phases.size(); i++) {
			long end = start + phases.get(i).ticks();
			if (offset < end) {
				return i;
			}
			start = end;
		}
		return NO_PHASE;
	}

	/** 그 구간에 실제로 걸려 있어야 할 효과들. {@link #NO_PHASE} 면 base 뿐이다. */
	public List<PerkEffect> activeFor(int phase) {
		if (phase < 0 || phase >= activeByPhase.size()) {
			return base;
		}
		return activeByPhase.get(phase);
	}

	/** 이 시각에 걸려 있어야 할 효과들. */
	public List<PerkEffect> activeAt(long time) {
		return activeFor(phaseAt(time));
	}

	// ------------------------------------------------------------------ 적용

	/**
	 * 지금 있어야 할 모습으로 맞춘다.
	 *
	 * <p>구간이 바뀌지 않았는지 보지 않고 무조건 다시 맞춘다. 접속·부활 직후처럼 플레이어 쪽
	 * 상태를 믿을 수 없는 자리에서 불리기 때문이다.
	 */
	@Override
	public void apply(ServerPlayer player, int stacks) {
		if (player == null) {
			return;
		}
		reconcile(player, stacks, phaseAt(gameTime(player)));
	}

	/**
	 * 한 틱 지났다. 구간이 그대로면 아무 일도 하지 않는다.
	 *
	 * @param time {@link PeriodicPerkManager} 가 읽어 온 오버월드 게임 시간
	 */
	public void tick(ServerPlayer player, int stacks, long time) {
		if (player == null) {
			return;
		}
		int phase = phaseAt(time);
		Integer previous = appliedPhase.get(player.getUUID());
		if (previous != null && previous == phase) {
			return;
		}
		reconcile(player, stacks, phase);
	}

	@Override
	public void remove(ServerPlayer player) {
		if (player == null) {
			return;
		}
		for (PerkEffect effect : allEffects) {
			safeRemove(effect, player);
		}
		appliedPhase.remove(player.getUUID());
	}

	/**
	 * 이 구간에 없는 효과를 떼고 있어야 할 효과를 붙인다.
	 *
	 * <p>떼는 대상은 "이전 구간의 효과"가 아니라 "이 정의에 등장하는 모든 효과 중 이번 구간에
	 * 없는 것"이다. 붙어 있지 않은 것을 떼는 일은 아무 일도 하지 않으므로 손해가 없고, 대신
	 * 서버를 껐다 켠 뒤나 다른 구간에서 접속을 끊었다 돌아온 뒤처럼 이전 구간을 알 수 없는
	 * 상황에서도 남은 효과가 영영 붙어 있는 일이 없다.
	 */
	private void reconcile(ServerPlayer player, int stacks, int phase) {
		List<PerkEffect> target = activeFor(phase);
		for (PerkEffect effect : allEffects) {
			if (!containsSame(target, effect)) {
				safeRemove(effect, player);
			}
		}
		for (PerkEffect effect : target) {
			safeApply(effect, player, stacks);
		}
		appliedPhase.put(player.getUUID(), phase);
	}

	/** 접속을 끊은 사람의 기억을 지운다. 다시 들어오면 그때 구간으로 새로 맞춘다. */
	public void forgetIf(Predicate<UUID> gone) {
		appliedPhase.keySet().removeIf(gone);
	}

	/** 이 플레이어에게 마지막으로 적용한 구간 번호. 아직 없으면 null. */
	public @Nullable Integer appliedPhase(UUID player) {
		return player == null ? null : appliedPhase.get(player);
	}

	// ------------------------------------------------------------------ 피해 배율

	@Override
	public double damageDealtMultiplier(int stacks) {
		return multiplierAt(PeriodicPerkManager.currentTick(), stacks, true);
	}

	@Override
	public double damageTakenMultiplier(int stacks) {
		return multiplierAt(PeriodicPerkManager.currentTick(), stacks, false);
	}

	/** 그 시각에 활성인 하위 효과들의 피해 배율을 모두 곱한 값. */
	public double multiplierAt(long time, int stacks, boolean dealt) {
		double total = 1.0;
		for (PerkEffect effect : activeAt(time)) {
			total *= dealt ? effect.damageDealtMultiplier(stacks) : effect.damageTakenMultiplier(stacks);
		}
		return Double.isFinite(total) && total >= 0.0 ? total : 1.0;
	}

	// ------------------------------------------------------------------ 조회

	public int periodTicks() {
		return periodTicks;
	}

	public List<Phase> phases() {
		return phases;
	}

	public List<PerkEffect> base() {
		return base;
	}

	/**
	 * 이 효과가 품고 있는 상태이상 하위 효과 전부.
	 *
	 * <p>{@link com.sharedfate.perk.PerkStatusEffects} 가 "증강이 건 상태이상"을 가려낼 때 쓴다.
	 * 그쪽에서 최상위 효과만 보면 여기 들어 있는 상태이상이 팀 공유 대상으로 새어 나간다.
	 */
	public List<StatusEffectPerk> statusEffects() {
		List<StatusEffectPerk> found = new ArrayList<>();
		for (PerkEffect effect : allEffects) {
			if (effect instanceof StatusEffectPerk status) {
				found.add(status);
			}
		}
		return List.copyOf(found);
	}

	// ------------------------------------------------------------------ 도우미

	/**
	 * 이 base 효과가 구간 효과에 밀려 꺼지는가.
	 *
	 * <p>같은 갈래를 구간이 하나라도 들고 있으면 구간이 이긴다.
	 */
	private static boolean suppressedBy(PerkEffect baseEffect, List<PerkEffect> phaseEffects) {
		String key = conflictKey(baseEffect);
		if (key == null) {
			return false;
		}
		for (PerkEffect effect : phaseEffects) {
			if (key.equals(conflictKey(effect))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 겹침을 판단하는 갈래 이름. 갈래가 없는 효과는 null 이고 절대 밀려나지 않는다.
	 *
	 * <p>상태이상은 종류를 나누지 않고 통째로 한 갈래다. 힘과 나약함처럼 서로 상쇄되는 짝이
	 * 있어서, 종류가 다르다고 함께 걸어 두면 "상시 힘 II, 잠깐 나약함 III" 같은 정의가 뜻대로
	 * 동작하지 않는다. 속성은 서로 독립이므로 같은 속성일 때만 겹친 것으로 본다.
	 */
	private static @Nullable String conflictKey(PerkEffect effect) {
		if (effect instanceof StatusEffectPerk) {
			return "status_effect";
		}
		if (effect instanceof AttributeEffect attribute) {
			return "attribute:" + attribute.attributeId();
		}
		if (effect instanceof DamageDealtEffect) {
			return "damage_dealt";
		}
		if (effect instanceof DamageTakenEffect) {
			return "damage_taken";
		}
		return null;
	}

	/** 목록에 바로 그 효과 객체가 들어 있는가. 값이 같은 다른 효과는 남남으로 본다. */
	private static boolean containsSame(List<PerkEffect> effects, PerkEffect target) {
		for (PerkEffect effect : effects) {
			if (effect == target) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 이 플레이어가 볼 시각.
	 *
	 * <p>플레이어가 어느 차원에 있든 오버월드의 게임 시간을 본다. 팀원끼리 구간이 어긋나면
	 * 안 되기 때문이다.
	 */
	private static long gameTime(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return PeriodicPerkManager.currentTick();
		}
		return server.overworld().getGameTime();
	}

	private static void safeApply(PerkEffect effect, ServerPlayer player, int stacks) {
		try {
			effect.apply(player, stacks);
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("periodic 효과의 하위 효과를 적용하지 못했습니다", error);
		}
	}

	private static void safeRemove(PerkEffect effect, ServerPlayer player) {
		try {
			effect.remove(player);
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("periodic 효과의 하위 효과를 걷어내지 못했습니다", error);
		}
	}
}
