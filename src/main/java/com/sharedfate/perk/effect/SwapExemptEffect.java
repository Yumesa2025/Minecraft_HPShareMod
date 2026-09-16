package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkBlessingSet;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.TemporaryPerkGrants;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 「열외」 — 이 증강을 고른 사람은 위치 교환 대상에서 빠지고, 팀이 교환될 때마다 그 사람만
 * 잠깐 빨라진다.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "swap_exempt", "on_swap_seconds": 10, "speed_bonus": 0.15 }
 * </pre>
 *
 * <p>두 값 모두 생략할 수 있고, 생략하면 {@link #DEFAULT_ON_SWAP_SECONDS}초·
 * {@link #DEFAULT_SPEED_BONUS} 다. 범위를 벗어난 값은 정의를 버리지 않고 범위 안으로 자른다
 * ({@link RallyShardEffect} 와 같은 규칙이다). 카멜케이스({@code onSwapSeconds}·
 * {@code speedBonus})로 적어도 읽는다.
 *
 * <h2>「고른 사람」만이다</h2>
 * <p>이 효과는 팀 전체가 아니라 <b>한 사람</b>에게 걸린다. 그 사람이 누구인지는
 * {@link TeamState#perkOwners}(증강 id → 고른 사람의 UUID)가 들고 있다. 그래서 고른 사람을 알
 * 수 없는 경로로 들어온 증강(「숨은 재능」처럼 덤으로 받은 것)은 <b>열외가 성립하지 않는다</b> —
 * 주인이 없으면 아무도 빠지지 않고, 조용히 팀 전원이 빠지는 일도 없다. 세트 효과로 켜진
 * {@code swap_exempt} 도 같은 이유로 주인이 없어 여기 걸리지 않는다.
 *
 * <h2>⚠ 최소 인원에 걸리면 교환 자체가 멈춘다</h2>
 * <p>위치 교환에는 두 명이 필요하다. 열외로 한 명이 빠져 남은 사람이 둘 미만이 되면
 * {@code TeamState.advancePositionSwapTick} 이 <b>교환을 일으키지 않고</b> 남은 시간을 1초
 * ({@code PositionSwapLimits.RETRY_TICKS})로 되돌려 계속 다시 시도한다. 예고 카운트다운도
 * 나오지 않고, 교환이 없으니 열외 보너스도 붙지 않는다. 두 명짜리 팀에서 한 명이 이 증강을
 * 고르면 그 회차의 위치 교환은 사실상 꺼진 것과 같다는 뜻이다.
 *
 * <h2>왜 상태이상이 아니라 속성 수정자인가</h2>
 * <p>바닐라 신속은 한 단계가 +20% 로 고정이라 15% 를 표현할 수 없다. 그래서
 * {@code minecraft:movement_speed} 에 수정자를 직접 붙이고, 정해진 시간 뒤에 떼는 일은 이미
 * 있는 {@link TemporaryPerkGrants}(속성은 {@code TimedPerkEffects} 로 넘어간다)에 그대로
 * 맡긴다. 수정자는 임시(transient)라 저장되지 않는다.
 *
 * <p>연산은 {@code ADD_MULTIPLIED_TOTAL} 이다. 이 모드의 다른 이동 속도 증감(정의 파일의
 * {@code add_multiplied_total})과 바닐라 신속이 쓰는 방식이 그것이라, 달리기까지 포함한 최종
 * 속도에 곱해져 「15% 빨라진다」가 말 그대로 성립한다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 값과 「누가 주인인가」만 아는 자료 그릇이다. 교환 명단에서 실제로 빼는 일은
 * {@link com.sharedfate.sync.PositionSwapManager} 가,
 * 그 둘을 이어 주는 물음은 {@link com.sharedfate.perk.PerkSwapRules} 가 맡는다.
 */
public final class SwapExemptEffect implements PerkEffect, OwnerBoundEffect {
	/** 교환 때마다 빨라지는 기본 시간(초). */
	public static final int DEFAULT_ON_SWAP_SECONDS = 10;
	public static final int MIN_ON_SWAP_SECONDS = 1;
	/** 상한(초). 이보다 길면 「교환 때마다 잠깐」이 아니라 상시나 다름없다. */
	public static final int MAX_ON_SWAP_SECONDS = 60;

	/** 기본 이동 속도 증가분. 0.15 면 +15% 다. */
	public static final double DEFAULT_SPEED_BONUS = 0.15;
	/** 0 이면 열외만 하고 보너스는 주지 않는다. 음수는 뜻이 없으므로 0 으로 접는다. */
	public static final double MIN_SPEED_BONUS = 0.0;
	/** 상한. +200% 를 넘으면 사람이 조종할 수 없는 속도가 된다. */
	public static final double MAX_SPEED_BONUS = 2.0;

	private static final int TICKS_PER_SECOND = 20;

	/** 보너스를 얹는 속성. 26.2 에 있는 바닐라 이름이다. */
	public static final Identifier MOVEMENT_SPEED =
			Identifier.withDefaultNamespace("movement_speed");

	private final int onSwapTicks;
	private final double speedBonus;
	/** 교환 시점에 얹을 것. 보너스가 0 이면 얹을 것이 없어 {@code null} 이다. */
	private final @Nullable TemporaryPerkGrants.Window window;
	/** 「가호 3」이 켜졌을 때의 이동 속도 증가분. */
	private final double amplifiedSpeedBonus;
	/** 강화용으로 미리 만들어 둔 것. 수정자 이름은 평소 것과 같아 둘이 겹치지 않는다. */
	private final @Nullable TemporaryPerkGrants.Window amplifiedWindow;

	/**
	 * @param perkId      이 효과를 가진 증강의 id. 속성 수정자 이름을 고유하게 만드는 데 쓴다
	 * @param index       그 증강 안에서 이 효과가 몇 번째인지. 같은 이유다
	 * @param onSwapTicks 교환 때마다 빨라지는 시간(틱)
	 * @param speedBonus  이동 속도 증가분(비율)
	 */
	public SwapExemptEffect(String perkId, int index, int onSwapTicks, double speedBonus) {
		this(perkId, index, onSwapTicks, speedBonus, speedBonus);
	}

	/**
	 * @param amplifiedSpeedBonus 「가호 3」이 켜졌을 때의 증가분. 안 적으면 평소와 같다
	 */
	public SwapExemptEffect(String perkId, int index, int onSwapTicks, double speedBonus,
			double amplifiedSpeedBonus) {
		this.onSwapTicks = onSwapTicks;
		this.speedBonus = speedBonus;
		this.amplifiedSpeedBonus = amplifiedSpeedBonus;
		this.window = windowOf(perkId, index, onSwapTicks, speedBonus);
		this.amplifiedWindow = amplifiedSpeedBonus == speedBonus
				? this.window
				: windowOf(perkId, index, onSwapTicks, amplifiedSpeedBonus);
	}

	/**
	 * 교환 시점에 얹을 것 하나. 보너스가 0 이면 얹을 것이 없어 {@code null} 이다.
	 *
	 * <p>수정자 이름을 평소 것과 강화 것이 <b>똑같이</b> 쓴다. 그래야 가호 단계가 오르내려도
	 * 두 개가 겹쳐 붙지 않고 나중 것이 앞의 것을 대신한다.
	 */
	private static @Nullable TemporaryPerkGrants.Window windowOf(String perkId, int index,
			int onSwapTicks, double bonus) {
		return bonus > 0.0
				? new TemporaryPerkGrants.Window(onSwapTicks, List.of(new AttributeEffect(
						MOVEMENT_SPEED, AttributeEffect.modifierId(perkId, index),
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, bonus)))
				: null;
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>값이 범위를 벗어나면 경고만 남기고 잘라 쓴다. 숫자 하나가 틀렸다고 증강 전체를 버리면
	 * 손해가 더 크다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int seconds = clampSeconds(perkId,
				PerkEffectType.readInt(json, secondsKey(json), DEFAULT_ON_SWAP_SECONDS));
		Double raw = PerkEffectType.readDouble(json, bonusKey(json));
		double bonus = clampBonus(perkId, raw == null ? DEFAULT_SPEED_BONUS : raw);
		Double rawAmplified = PerkEffectType.readDouble(json, amplifiedBonusKey(json));
		double amplified = clampBonus(perkId, rawAmplified == null ? bonus : rawAmplified);
		return new SwapExemptEffect(perkId, index, seconds * TICKS_PER_SECOND, bonus, amplified);
	}

	/** 카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다. */
	private static String secondsKey(JsonObject json) {
		return json != null && json.has("onSwapSeconds") ? "onSwapSeconds" : "on_swap_seconds";
	}

	private static String bonusKey(JsonObject json) {
		return json != null && json.has("speedBonus") ? "speedBonus" : "speed_bonus";
	}

	private static String amplifiedBonusKey(JsonObject json) {
		return json != null && json.has("amplifiedSpeedBonus")
				? "amplifiedSpeedBonus"
				: "amplified_speed_bonus";
	}

	private static int clampSeconds(String perkId, int value) {
		if (value >= MIN_ON_SWAP_SECONDS && value <= MAX_ON_SWAP_SECONDS) {
			return value;
		}
		int cut = Math.max(MIN_ON_SWAP_SECONDS, Math.min(MAX_ON_SWAP_SECONDS, value));
		SharedFateMod.LOGGER.warn(
				"증강 {}: swap_exempt 의 on_swap_seconds 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, MIN_ON_SWAP_SECONDS, MAX_ON_SWAP_SECONDS, cut, value);
		return cut;
	}

	private static double clampBonus(String perkId, double value) {
		if (Double.isFinite(value) && value >= MIN_SPEED_BONUS && value <= MAX_SPEED_BONUS) {
			return value;
		}
		double cut = Double.isFinite(value)
				? Math.max(MIN_SPEED_BONUS, Math.min(MAX_SPEED_BONUS, value))
				: DEFAULT_SPEED_BONUS;
		SharedFateMod.LOGGER.warn(
				"증강 {}: swap_exempt 의 speed_bonus 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, MIN_SPEED_BONUS, MAX_SPEED_BONUS, cut, value);
		return cut;
	}

	/** 교환 때마다 빨라지는 시간(틱). */
	public int onSwapTicks() {
		return onSwapTicks;
	}

	/** 교환 때마다 빨라지는 시간(초). */
	public int onSwapSeconds() {
		return onSwapTicks / TICKS_PER_SECOND;
	}

	/** 이동 속도 증가분. 0.15 면 +15% 다. */
	public double speedBonus() {
		return speedBonus;
	}

	/** 「가호 3」이 켜졌을 때의 증가분. 안 적으면 {@link #speedBonus()} 와 같다. */
	public double amplifiedSpeedBonus() {
		return amplifiedSpeedBonus;
	}

	/**
	 * 「가호 4」로 <b>팀 전원</b>에게 걸린 열외. 그런 것이 없으면 {@code null}.
	 *
	 * <p>여기서 값이 나오면 그 팀에서는 <b>아무도 자리를 바꾸지 않는다.</b> 위치 교환에는 두
	 * 명이 필요하므로 교환이 사실상 꺼진 것과 같다 — 가호를 넷까지 모은 팀이 무는 대가이고,
	 * 의도한 모양이다. 「고른 사람」이 없어도(덤으로 받은 증강이어도) 전원에게 걸린다.
	 *
	 * <p>{@link #ownersIn} 과 달리 주인을 보지 않는다. 부르는 자리는
	 * {@code PerkSwapRules} 셋뿐이다.
	 */
	public static @Nullable SwapExemptEffect everyoneIn(@Nullable TeamState state) {
		return everyoneIn(state, perkId -> PerkRegistry.byId(perkId).orElse(null));
	}

	/** 정의를 어디서 찾을지 바깥에서 넘기는 형태. 시험이 보는 자리다. */
	public static @Nullable SwapExemptEffect everyoneIn(@Nullable TeamState state,
			Function<String, Perk> lookup) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty() || lookup == null) {
			return null;
		}
		for (String perkId : state.ownedPerks) {
			if (PerkBlessingSet.modeFor(state, perkId) != HolderEffect.HolderMode.EVERYONE) {
				continue;
			}
			Perk perk = lookup.apply(perkId);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof SwapExemptEffect exempt) {
					return exempt;
				}
			}
		}
		return null;
	}

	/**
	 * 이 사람에게 교환 보너스를 얹는다. 보너스가 0 이면 아무 일도 하지 않는다.
	 *
	 * <p>연달아 부르면 다시 붙이지 않고 만료 시각만 미룬다({@link TemporaryPerkGrants} →
	 * {@code TimedPerkEffects}). 교환 주기가 보너스보다 짧아도 수정자가 겹치지 않는다.
	 */
	public void grantTo(@Nullable ServerPlayer player) {
		grantTo(player, false);
	}

	/**
	 * 같은 일을 「가호 3」 여부와 함께 한다.
	 *
	 * @param amplified 참이면 강화 증가분을 얹는다
	 */
	public void grantTo(@Nullable ServerPlayer player, boolean amplified) {
		TemporaryPerkGrants.Window chosen = amplified ? amplifiedWindow : window;
		if (chosen == null || player == null) {
			return;
		}
		try {
			TemporaryPerkGrants.grant(player, chosen);
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("열외의 이동 속도 보너스를 얹지 못했습니다", error);
		}
	}

	/**
	 * 증강을 잃으면 마침 걸려 있던 보너스도 걷어낸다. {@link OnSwapEffect} 와 같은 처리다.
	 *
	 * <p>상시로 붙는 것은 없으므로 {@link #apply} 는 재정의하지 않는다.
	 */
	@Override
	public void remove(ServerPlayer player) {
		if (player == null) {
			return;
		}
		if (window != null) {
			TemporaryPerkGrants.revoke(player, window);
		}
		// 강화 쪽으로 얹혀 있었을 수도 있다. 수정자 이름이 같아 대개 한 번으로 끝나지만,
		// 보너스가 0 이라 평소 것이 없는 정의에서는 이쪽만 남는다.
		if (amplifiedWindow != null && amplifiedWindow != window) {
			TemporaryPerkGrants.revoke(player, amplifiedWindow);
		}
	}

	/**
	 * 이 팀에서 열외인 사람들과 각자에게 걸린 정의.
	 *
	 * <p>증강 id 별로 <b>고른 사람</b>을 찾아 짝지어 준다. 한 팀이 열외 증강을 여럿 가질 수도
	 * 있고 그때는 사람마다 자기 정의를 받는다. 같은 사람이 둘을 고른 경우에는 먼저 만난 것이
	 * 남는다 — 어차피 빠지는 것은 한 번이고, 보너스가 둘 다 붙으면 정의에 없는 세기가 된다.
	 *
	 * <p>팀이 없거나, 증강을 껐거나, 아직 아무 증강도 없거나, 「누가 골랐는지」 기록이 비어
	 * 있으면 곧바로 빈 결과다. 풀에서 사라진 id 는 건너뛴다 — 정의를 손으로 고칠 수 있는 이상
	 * 저장에만 남은 id 는 언제든 생기고, 정의가 없는 증강은 아무 효과도 없는 것으로 본다.
	 *
	 * @return 고른 사람의 UUID → 그 사람에게 걸린 열외 정의
	 */
	public static Map<UUID, SwapExemptEffect> ownersIn(@Nullable TeamState state) {
		return ownersIn(state, perkId -> PerkRegistry.byId(perkId).orElse(null));
	}

	/**
	 * 증강 정의를 어디서 찾을지 바깥에서 넘기는 형태. {@link #ownersIn(TeamState)} 의 알맹이다.
	 *
	 * <p>보관소를 인자로 뺀 이유는 시험 때문이다. 이 계산에 필요한 것은 「보유 증강 → 효과」와
	 * 「증강 → 고른 사람」 둘뿐이라, 살아 있는 서버도 정의 파일도 없이 확인할 수 있어야 한다.
	 *
	 * @param lookup 증강 id 로 정의를 찾는 방법. 없는 id 에는 {@code null} 을 돌려주면 된다
	 */
	public static Map<UUID, SwapExemptEffect> ownersIn(@Nullable TeamState state,
			Function<String, Perk> lookup) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()
				|| state.perkOwners.isEmpty() || lookup == null) {
			return Map.of();
		}
		Map<UUID, SwapExemptEffect> owners = null;
		for (String perkId : state.ownedPerks) {
			UUID owner = state.perkOwners.get(perkId);
			if (owner == null) {
				continue;
			}
			Perk perk = lookup.apply(perkId);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (!(effect instanceof SwapExemptEffect exempt)) {
					continue;
				}
				if (owners == null) {
					owners = new LinkedHashMap<>();
				}
				owners.putIfAbsent(owner, exempt);
				break;
			}
		}
		return owners == null ? Map.of() : Map.copyOf(owners);
	}
}
