package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.ConditionalPerkManager;
import com.sharedfate.perk.PerkDrawbacks;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.sync.TeamProximity;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 팀 상태에 따라 서로 다른 효과를 거는 래퍼.
 *
 * <p>자기 자신은 아무 일도 하지 않는다. 조건이 참이면 {@code when_true} 에 적힌 효과들을 붙이고
 * {@code when_false} 쪽을 떼며, 거짓이면 반대로 한다. 즉 두 묶음 중 하나만 항상 붙어 있다.
 *
 * <pre>{@code
 * {
 *   "type": "conditional",
 *   "condition": "hunger_full",
 *   "when_true":  [ { "type": "attribute", ... } ],
 *   "when_false": [ { "type": "attribute", ... } ]
 * }
 * }</pre>
 *
 * <h2>조건은 대개 팀 공유 값을 본다</h2>
 * <p>이 모드는 체력과 허기를 팀이 공유하므로, 개인의 {@code player.getHealth()} 가 아니라
 * {@link TeamState} 에 담긴 공유 값을 본다. 같은 팀원은 언제나 같은 판정을 받는다.
 *
 * <p><b>예외가 있다.</b> {@link TeamState} 만 봐서는 답할 수 없는 조건이 둘 있고,
 * {@link Condition#perPlayer()} 가 그 갈래를 표시한다.
 *
 * <ul>
 *   <li>{@code in_water} — 팀이 공유하는 값이 아니라 그 사람이 지금 물에 몸을 담그고 있는지를
 *       보므로, 한 팀 안에서도 물에 있는 사람만 효과를 받는다.</li>
 *   <li>{@code team_nearby} — <b>답 자체는 팀 전체가 같다.</b> 그런데 어느 팀인지를 알아내려면
 *       플레이어가 있어야 한다({@link TeamState} 에는 팀 id 가 없다). 그래서 판정에 사람이
 *       필요하다는 점만 {@code in_water} 와 같은 갈래로 묶었다.</li>
 * </ul>
 *
 * <h2>조건은 계속 다시 본다</h2>
 * <p>허기와 체력은 수시로 변하므로 {@link #apply} 한 번으로는 끝나지 않는다.
 * {@link ConditionalPerkManager} 가 주기적으로 {@link #refresh} 를 불러 준다. 판정이 지난번과
 * 같으면 아무것도 하지 않는다. 매번 수정자를 뗐다 붙이면 성능도 나쁘고, 최대 체력을 건드리는
 * 수정자라면 현재 체력이 깎이는 부작용까지 생기기 때문이다.
 *
 * <h2>하위 효과의 순번</h2>
 * <p>속성 수정자의 식별자는 {@code 증강id + 효과순번} 으로 만들어지므로(
 * {@link AttributeEffect#modifierId}) 하위 효과의 순번이 형제 효과와 겹치면 서로를 덮어쓴다.
 * 그래서 하위 순번은 부모 순번에서 다음과 같이 파생한다.
 *
 * <pre>
 *   when_true  의 i 번째 → (부모순번 + 1) * 100 + i
 *   when_false 의 i 번째 → (부모순번 + 1) * 100 + 50 + i
 * </pre>
 *
 * <p>더하는 값이 100 미만이라 부모가 다르면 결과도 반드시 다르고, {@code +1} 덕분에 0번 효과의
 * 자식이 최상위 순번(0,1,2,…)과 겹치지 않는다. 한 묶음에 50개, 최상위 효과에 100개를 넘게 적으면
 * 이 보장이 깨지므로 그 앞에서 잘라낸다.
 */
public final class ConditionalEffect implements PerkEffect {
	/** 허기 최대치. 바닐라 값이자 {@link TeamState#sanitize} 가 잘라 두는 상한이다. */
	private static final int MAX_FOOD_LEVEL = 20;

	/** 실수 비교에 쓰는 허용 오차. 체력은 float 라 정확히 같기를 기대할 수 없다. */
	private static final double EPSILON = 1.0e-4;

	/** 한 묶음에 담을 수 있는 하위 효과 수. 순번 파생 규칙이 깨지지 않는 한계다. */
	private static final int MAX_BRANCH_EFFECTS = 50;

	/** 하위 순번을 만들 때 부모 순번에 곱하는 값. */
	private static final int CHILD_INDEX_STRIDE = 100;

	/** {@code when_false} 쪽 하위 순번에 더하는 값. 두 묶음이 겹치지 않게 한다. */
	private static final int FALSE_BRANCH_OFFSET = 50;

	/** conditional 안에 conditional 이 들어갈 수 있는 깊이. 무한 중첩을 막는다. */
	private static final int MAX_DEPTH = 2;

	/** 지금 몇 겹째를 읽고 있는지. 읽기는 재귀라 호출 스택을 따라 오르내린다. */
	private static final ThreadLocal<Integer> PARSE_DEPTH = ThreadLocal.withInitial(() -> 0);

	/** 뭉침 판정 경고를 이미 남겼는지. {@link #forgetAllWarnings} 가 회차마다 되돌린다. */
	private static volatile boolean proximityWarned;

	/** 무엇을 볼지. */
	public enum Condition {
		/** 팀 공유 허기가 최대치. */
		HUNGER_FULL("hunger_full", false),
		/** 팀 공유 체력이 최대치. */
		HEALTH_FULL("health_full", false),
		/** 팀 공유 체력이 기준 비율 이하. */
		HEALTH_BELOW("health_below", true),
		/** 팀 공유 체력이 기준 비율 초과. */
		HEALTH_ABOVE("health_above", true),
		/**
		 * <b>이 사람이</b> 물에 몸을 담그고 있다.
		 *
		 * <p>팀 공유 값이 아니라 <b>사람마다 답이 다른</b> 유일한 조건이다. 같은 팀 안에서도 물에
		 * 들어간 사람만 효과를 받는다({@code team_nearby} 도 사람을 거쳐 판정하지만 그쪽은 답이
		 * 팀 전체에 같다). 비를 맞는 것은 해당하지 않고({@code isInWaterOrRain} 이 아니라
		 * {@code isInWater} 를 쓴다), 머리까지 잠길 필요도 없다.
		 */
		IN_WATER("in_water", false, true),
		/**
		 * <b>팀원 전원이</b> {@code distance} 칸 안에 뭉쳐 있다.
		 *
		 * <p>「결속」 증강들이 쓰는 조건이다. 거리를 직접 재지 않고
		 * {@link TeamProximity#together} 에 물어본다 — 거기가 1초에 한 번 팀마다 한 번만 재고,
		 * 세트 「결속 3」의 반경 배율도 그 안에서 저절로 먹는다. 여기서 다시 재면 같은 계산을
		 * 증강 수만큼 되풀이하게 되고 배율도 놓친다.
		 *
		 * <p>{@code in_water} 와 달리 답은 팀 전체가 같다. 그런데도 {@code perPlayer} 갈래에
		 * 넣은 까닭은 {@link TeamState} 에 팀 id 가 없어 <b>사람을 거쳐야만</b> 팀을 찾을 수 있기
		 * 때문이다.
		 */
		TEAM_NEARBY("team_nearby", false, true, true);

		private final String id;
		private final boolean needsThreshold;
		private final boolean perPlayer;
		private final boolean needsDistance;

		Condition(String id, boolean needsThreshold) {
			this(id, needsThreshold, false);
		}

		Condition(String id, boolean needsThreshold, boolean perPlayer) {
			this(id, needsThreshold, perPlayer, false);
		}

		Condition(String id, boolean needsThreshold, boolean perPlayer, boolean needsDistance) {
			this.id = id;
			this.needsThreshold = needsThreshold;
			this.perPlayer = perPlayer;
			this.needsDistance = needsDistance;
		}

		public String id() {
			return id;
		}

		/** {@code threshold} 필드가 있어야 하는 조건인지. */
		public boolean needsThreshold() {
			return needsThreshold;
		}

		/**
		 * 팀 상태({@link TeamState})만으로는 판정할 수 없는 조건인지.
		 *
		 * <p>참이면 {@link ConditionalEffect#matches(TeamState)} 만으로는 답할 수 없고 플레이어가
		 * 함께 있어야 한다. {@code in_water} 는 사람마다 답이 다르기 때문이고,
		 * {@code team_nearby} 는 답은 팀 전체가 같지만 팀을 찾으려면 사람을 거쳐야 하기 때문이다.
		 */
		public boolean perPlayer() {
			return perPlayer;
		}

		/** {@code distance} 필드가 있어야 하는 조건인지. */
		public boolean needsDistance() {
			return needsDistance;
		}

		/** JSON 의 condition 문자열에 맞는 조건. 알 수 없는 값이면 null. */
		public static @Nullable Condition fromId(@Nullable String raw) {
			if (raw == null) {
				return null;
			}
			String normalized = raw.trim().toLowerCase(Locale.ROOT);
			for (Condition condition : values()) {
				if (condition.id.equals(normalized)) {
					return condition;
				}
			}
			return null;
		}
	}

	private final Condition condition;
	private final double threshold;
	/** {@code team_nearby} 가 쓰는 거리(블록). 다른 조건에서는 0 이고 아무도 보지 않는다. */
	private final double distance;
	private final List<PerkEffect> whenTrue;
	private final List<PerkEffect> whenFalse;

	/**
	 * 플레이어별로 지금 어느 쪽을 붙여 둔 상태인지.
	 *
	 * <p>{@link #refresh} 가 "바뀌지 않았으면 아무것도 하지 않는다"를 지키려면 직전 판정을
	 * 기억해야 한다. 팀 인원만큼만 자라므로 크기는 문제되지 않는다.
	 */
	private final Map<UUID, Boolean> applied = new ConcurrentHashMap<>();

	/** 거리를 쓰지 않는 조건용. {@code distance} 를 0 으로 둔다. */
	public ConditionalEffect(Condition condition, double threshold,
			List<PerkEffect> whenTrue, List<PerkEffect> whenFalse) {
		this(condition, threshold, 0.0, whenTrue, whenFalse);
	}

	public ConditionalEffect(Condition condition, double threshold, double distance,
			List<PerkEffect> whenTrue, List<PerkEffect> whenFalse) {
		this.condition = condition;
		this.threshold = threshold;
		this.distance = distance;
		this.whenTrue = List.copyOf(whenTrue);
		this.whenFalse = List.copyOf(whenFalse);
	}

	// ------------------------------------------------------------------ 읽기

	/**
	 * JSON 에서 만든다. 정의가 잘못됐으면 경고를 남기고 null.
	 *
	 * <p>하위 효과가 하나라도 잘못됐으면 이 효과 전체를 버린다. 절반만 살아남은 조건부 효과는
	 * 설명과 다르게 동작해 플레이어를 속이게 되기 때문이다. 버리면 증강 자체가 풀에서 빠진다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int depth = PARSE_DEPTH.get();
		if (depth >= MAX_DEPTH) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: conditional 이 {}겹을 넘게 중첩됐습니다. 더 읽지 않습니다", perkId, MAX_DEPTH);
			return null;
		}

		String rawCondition = PerkEffectType.readString(json, "condition");
		Condition condition = Condition.fromId(rawCondition);
		if (condition == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 알 수 없는 condition 입니다 ({})", perkId, rawCondition);
			return null;
		}

		double threshold = 0.0;
		if (condition.needsThreshold()) {
			Double raw = PerkEffectType.readDouble(json, "threshold");
			if (raw == null || raw < 0.0 || raw > 1.0) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: {} 조건의 threshold 가 없거나 0.0~1.0 을 벗어났습니다 ({})",
						perkId, condition.id(), raw);
				return null;
			}
			threshold = raw;
		}

		// 거리는 proximity 와 같은 범위로 받는다. 두 곳이 같은 「뭉침」을 재는데 받아들이는
		// 거리가 다르면, 같은 숫자를 적어도 한쪽만 켜지는 일이 생긴다.
		double distance = 0.0;
		if (condition.needsDistance()) {
			Double raw = PerkEffectType.readDouble(json, "distance");
			if (raw == null || raw < ProximityEffect.MIN_DISTANCE
					|| raw > ProximityEffect.MAX_DISTANCE) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: {} 조건의 distance 가 없거나 {}~{} 를 벗어났습니다 ({})",
						perkId, condition.id(),
						ProximityEffect.MIN_DISTANCE, ProximityEffect.MAX_DISTANCE, raw);
				return null;
			}
			distance = raw;
		}

		// 하위 효과를 읽는 동안만 깊이를 한 겹 올린다. 예외가 나도 반드시 되돌린다.
		PARSE_DEPTH.set(depth + 1);
		List<PerkEffect> whenTrue;
		List<PerkEffect> whenFalse;
		try {
			whenTrue = parseBranch(perkId, index, json, "when_true", 0);
			whenFalse = parseBranch(perkId, index, json, "when_false", FALSE_BRANCH_OFFSET);
		} finally {
			if (depth == 0) {
				PARSE_DEPTH.remove();
			} else {
				PARSE_DEPTH.set(depth);
			}
		}
		if (whenTrue == null || whenFalse == null) {
			return null;
		}
		if (whenTrue.isEmpty() && whenFalse.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: conditional 에 when_true 도 when_false 도 없습니다", perkId);
			return null;
		}

		return new ConditionalEffect(condition, threshold, distance, whenTrue, whenFalse);
	}

	/**
	 * 한 묶음을 읽는다. 필드가 없으면 빈 목록, 하나라도 잘못됐으면 null.
	 *
	 * @param branchOffset 이 묶음의 하위 순번에 더할 값
	 */
	private static @Nullable List<PerkEffect> parseBranch(String perkId, int index, JsonObject json,
			String key, int branchOffset) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 {}: conditional 의 {} 가 배열이 아닙니다", perkId, key);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		if (array.size() > MAX_BRANCH_EFFECTS) {
			SharedFateMod.LOGGER.warn("증강 {}: conditional 의 {} 에 효과가 너무 많습니다 ({} > {})",
					perkId, key, array.size(), MAX_BRANCH_EFFECTS);
			return null;
		}

		List<PerkEffect> effects = new ArrayList<>(array.size());
		for (int i = 0; i < array.size(); i++) {
			JsonElement raw = array.get(i);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: conditional 의 {} 중 {}번째가 객체가 아닙니다",
						perkId, key, i);
				return null;
			}
			JsonObject childJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(childJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: conditional 의 {} 에 알 수 없는 효과 type 입니다 ({})",
						perkId, key, typeId);
				return null;
			}
			// 하위에 붙은 대가 표시는 읽지 않는다. 여기서 조용히 무시하면 「빌드도 통과하고
			// 로그도 없이 아무 일도 안 하는」 정의가 되므로 반드시 말해 준다. 자세한 까닭은
			// PerkDrawbacks 머리말에 있다.
			if (childJson.has(PerkDrawbacks.FIELD)) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: conditional 의 {} 하위에는 {} 를 붙일 수 없습니다. "
								+ "조건부 효과 전체에 붙이십시오.", perkId, key, PerkDrawbacks.FIELD);
			}
			PerkEffect child = type.create(perkId, childIndex(index, branchOffset + i), childJson);
			if (child == null) {
				return null;
			}
			effects.add(child);
		}
		return effects;
	}

	/** 부모 순번과 묶음 안 순번으로 하위 효과의 순번을 만든다. 규칙은 클래스 설명 참고. */
	public static int childIndex(int parentIndex, int ordinal) {
		return (Math.max(0, parentIndex) + 1) * CHILD_INDEX_STRIDE + ordinal;
	}

	// ------------------------------------------------------------------ 판정

	/**
	 * 이 조건이 지금 참인지. 팀 공유 값만 보고 판정한다.
	 *
	 * <p>팀 상태를 모르면(팀이 없거나 아직 준비되지 않았으면) 거짓으로 본다.
	 *
	 * <p><b>{@link Condition#perPlayer()} 인 조건은 여기서 언제나 거짓이다.</b> 볼 사람이 없기
	 * 때문이다. 그런 조건은 {@link #matches(TeamState, ServerPlayer)} 를 써야 한다.
	 */
	public boolean matches(@Nullable TeamState state) {
		return matches(state, null);
	}

	/**
	 * 이 조건이 이 사람에게 지금 참인지.
	 *
	 * <p>팀 공유 조건은 {@code player} 를 보지 않으므로 null 을 넘겨도 결과가 같다.
	 * 사람을 거쳐야 하는 조건({@code in_water}·{@code team_nearby})만 {@code player} 를 쓰고,
	 * 없으면 거짓으로 본다.
	 */
	public boolean matches(@Nullable TeamState state, @Nullable ServerPlayer player) {
		if (state == null) {
			return false;
		}
		return switch (condition) {
			case HUNGER_FULL -> state.foodLevel >= MAX_FOOD_LEVEL;
			case HEALTH_FULL -> healthRatio(state) >= 1.0 - EPSILON;
			case HEALTH_BELOW -> healthRatio(state) <= threshold + EPSILON;
			case HEALTH_ABOVE -> healthRatio(state) > threshold + EPSILON;
			case IN_WATER -> player != null && player.isInWater();
			case TEAM_NEARBY -> teamNearby(player);
		};
	}

	/**
	 * 이 사람의 팀이 지금 {@link #distance} 안에 뭉쳐 있는가.
	 *
	 * <p>거리는 {@link TeamProximity} 가 1초마다 한 번 재 둔 값을 쓴다. 여기가 하는 일은 팀을
	 * 찾아 그 값을 물어보는 것뿐이라, {@link ConditionalPerkManager} 가 반 초마다 물어봐도
	 * 실제로 바뀌는 것은 1초에 한 번이다. 답이 같으면 {@link #refresh} 가 아무 일도 하지 않으므로
	 * 따로 주기를 두지 않았다 — 나머지 절반은 맵 조회 한 번으로 끝난다.
	 *
	 * <p>팀 상태를 읽는 중에 무슨 일이 생겨도 밖으로 내보내지 않는다. 이 판정은 서버 틱
	 * 한가운데서 불리므로, 예외 하나가 남은 증강들의 갱신까지 통째로 멈추면 안 된다.
	 */
	private boolean teamNearby(@Nullable ServerPlayer player) {
		if (player == null) {
			return false;
		}
		try {
			MinecraftServer server = player.level().getServer();
			if (server == null) {
				return false;
			}
			ShareTeam team = TeamManager.get(server).teamOf(player.getUUID());
			// 정의에 적힌 거리를 그대로 넘긴다. 세트 「결속 3」의 배율은 저쪽에서 먹인다.
			return team != null && TeamProximity.together(team.teamId(), distance);
		} catch (RuntimeException error) {
			warnProximityOnce(error);
			return false;
		}
	}

	/** 뭉침 판정이 실패했다고 한 번만 알린다. 매 판정마다 남기면 로그가 초당 수십 줄로 불어난다. */
	private static void warnProximityOnce(RuntimeException error) {
		if (proximityWarned) {
			return;
		}
		proximityWarned = true;
		SharedFateMod.LOGGER.warn(
				"team_nearby 조건이 팀의 뭉침을 확인하지 못했습니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 팀 공유 체력의 비율. 최대 체력이 이상한 값이면 0 으로 본다. */
	private static double healthRatio(TeamState state) {
		float max = state.maxHealth;
		if (!Float.isFinite(max) || max <= 0.0F) {
			return 0.0;
		}
		float health = Float.isFinite(state.health) ? state.health : 0.0F;
		return Math.max(0.0, Math.min(1.0, health / (double) max));
	}

	// ------------------------------------------------------------------ 적용

	/**
	 * 지금 조건을 보고 맞는 묶음을 붙인다.
	 *
	 * <p>기억해 둔 판정과 관계없이 무조건 다시 맞춘다. 접속이나 부활 직후처럼 수정자가 통째로
	 * 날아간 상태에서도 불리기 때문이다.
	 */
	@Override
	public void apply(ServerPlayer player) {
		if (player == null) {
			return;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			return;
		}
		boolean met = matches(state, player);
		switchTo(player, met);
		applied.put(player.getUUID(), met);
	}

	/** 두 묶음을 모두 걷어낸다. 어느 쪽이 붙어 있었는지 몰라도 안전하다. */
	@Override
	public void remove(ServerPlayer player) {
		if (player == null) {
			return;
		}
		removeAll(whenTrue, player);
		removeAll(whenFalse, player);
		applied.remove(player.getUUID());
	}

	/**
	 * 조건을 다시 보고, 지난번과 달라졌을 때만 묶음을 갈아 끼운다.
	 *
	 * <p>{@link ConditionalPerkManager} 가 주기적으로 부른다.
	 *
	 * @return 실제로 갈아 끼웠으면 true
	 */
	public boolean refresh(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			return false;
		}
		boolean met = matches(state, player);
		Boolean previous = applied.get(player.getUUID());
		if (previous != null && previous == met) {
			return false;
		}
		switchTo(player, met);
		applied.put(player.getUUID(), met);
		return true;
	}

	/** 이 플레이어에게 기억해 둔 판정. 아직 적용한 적이 없으면 null. */
	public @Nullable Boolean appliedCondition(UUID playerId) {
		return playerId == null ? null : applied.get(playerId);
	}

	/** 기억해 둔 판정을 모두 버린다. 서버가 멈출 때 다음 회차로 새어나가지 않게 한다. */
	public void forgetAll() {
		applied.clear();
	}

	/**
	 * 한 번만 남기기로 한 경고들을 다시 낼 수 있게 되돌린다.
	 *
	 * <p>서버가 멈출 때 {@link ConditionalPerkManager#reset} 이 부른다. 되돌리지 않으면 첫 회차에
	 * 한 번 실패한 뒤로는 다음 회차에서 같은 문제가 나도 영영 조용해진다.
	 */
	public static void forgetAllWarnings() {
		proximityWarned = false;
	}

	/** 지는 쪽을 먼저 떼고 이기는 쪽을 붙인다. 두 묶음이 같은 대상을 건드려도 순서가 안전하다. */
	private void switchTo(ServerPlayer player, boolean met) {
		removeAll(met ? whenFalse : whenTrue, player);
		applyAll(met ? whenTrue : whenFalse, player);
	}

	private void applyAll(List<PerkEffect> effects, ServerPlayer player) {
		for (PerkEffect effect : effects) {
			try {
				effect.apply(player);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("조건부 증강의 하위 효과를 적용하지 못했습니다", error);
			}
		}
	}

	private void removeAll(List<PerkEffect> effects, ServerPlayer player) {
		for (PerkEffect effect : effects) {
			try {
				effect.remove(player);
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("조건부 증강의 하위 효과를 걷어내지 못했습니다", error);
			}
		}
	}

	// ------------------------------------------------------------------ 피해 배율

	/**
	 * 지금 붙어 있는 묶음의 주는 피해 배율.
	 *
	 * <p>이 메서드에는 플레이어 인자가 없다. 그래서 누구를 위한 조회인지는
	 * {@link ConditionalPerkManager#multiplierContext()} 로 알아낸다. 배율을 모으는 자리
	 * ({@code PerkManager.multiplier})가 조회를 시작하면서 대상 플레이어를 적어 두기 때문에,
	 * 그 UUID 로 팀 상태를 찾아 조건을 그 자리에서 다시 본다.
	 *
	 * <p>대상을 알 수 없을 때는 {@link #apply}/{@link #refresh} 가 기억해 둔 판정으로 물러선다.
	 * 팀원끼리는 공유 값을 보므로 판정이 언제나 같고, 기억이 하나도 없거나 팀마다 판정이
	 * 엇갈리면 관여하지 않는다는 뜻으로 1.0 을 돌려준다.
	 */
	@Override
	public double damageDealtMultiplier() {
		Boolean met = resolveCondition();
		return met == null ? 1.0 : damageDealtMultiplier(met);
	}

	@Override
	public double damageTakenMultiplier() {
		Boolean met = resolveCondition();
		return met == null ? 1.0 : damageTakenMultiplier(met);
	}

	/** 조건 판정을 직접 주고 구하는 주는 피해 배율. */
	public double damageDealtMultiplier(boolean conditionMet) {
		return branchMultiplier(conditionMet ? whenTrue : whenFalse, true);
	}

	/** 조건 판정을 직접 주고 구하는 받는 피해 배율. */
	public double damageTakenMultiplier(boolean conditionMet) {
		return branchMultiplier(conditionMet ? whenTrue : whenFalse, false);
	}

	private static double branchMultiplier(List<PerkEffect> effects, boolean dealt) {
		double total = 1.0;
		for (PerkEffect effect : effects) {
			try {
				total *= dealt
						? effect.damageDealtMultiplier()
						: effect.damageTakenMultiplier();
			} catch (RuntimeException error) {
				SharedFateMod.LOGGER.warn("조건부 증강의 하위 피해 배율을 구하지 못했습니다", error);
				return 1.0;
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	/**
	 * 지금 조회의 대상이 누구인지 알아내 조건을 판정한다. 알 수 없으면 null.
	 *
	 * <p>배율 조회에는 {@code UUID} 만 오고 {@code ServerPlayer} 가 없다. 그래서 사람마다
	 * 판정하는 조건({@code in_water})은 다시 재지 못하고 <b>마지막 갱신 때 기억해 둔 판정</b>을
	 * 쓴다. 반 초마다 다시 보므로 그 사이의 어긋남은 최대 반 초다.
	 */
	private @Nullable Boolean resolveCondition() {
		UUID target = ConditionalPerkManager.multiplierContext();
		if (target != null) {
			if (!condition.perPlayer()) {
				TeamState state = TeamLookup.stateOf(target);
				if (state != null) {
					return matches(state);
				}
			}
			Boolean remembered = applied.get(target);
			if (remembered != null) {
				return remembered;
			}
		}
		return consensus();
	}

	/** 기억해 둔 판정이 전부 같으면 그 값. 비어 있거나 엇갈리면 null. */
	private @Nullable Boolean consensus() {
		Boolean agreed = null;
		for (Boolean value : applied.values()) {
			if (agreed == null) {
				agreed = value;
			} else if (!agreed.equals(value)) {
				return null;
			}
		}
		return agreed;
	}

	// ------------------------------------------------------------------ 조회

	public Condition condition() {
		return condition;
	}

	public double threshold() {
		return threshold;
	}

	/** {@code team_nearby} 가 요구하는 거리(블록). 다른 조건에서는 0 이다. */
	public double distance() {
		return distance;
	}

	public List<PerkEffect> whenTrue() {
		return whenTrue;
	}

	public List<PerkEffect> whenFalse() {
		return whenFalse;
	}

	/**
	 * 두 묶음의 하위 효과 전부.
	 *
	 * <p>지금 어느 쪽이 붙어 있든 둘 다 이 증강이 거는 효과라, 하위 효과까지 훑어야 하는 곳
	 * ({@code PerkStatusEffects} 처럼)에서는 양쪽을 모두 봐야 한다.
	 */
	public List<PerkEffect> children() {
		if (whenFalse.isEmpty()) {
			return whenTrue;
		}
		if (whenTrue.isEmpty()) {
			return whenFalse;
		}
		List<PerkEffect> all = new ArrayList<>(whenTrue.size() + whenFalse.size());
		all.addAll(whenTrue);
		all.addAll(whenFalse);
		return List.copyOf(all);
	}
}
