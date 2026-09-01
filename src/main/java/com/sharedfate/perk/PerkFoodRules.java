package com.sharedfate.perk;

import com.sharedfate.perk.effect.FoodNutritionEffect;
import com.sharedfate.perk.effect.HungerDrainEffect;
import com.sharedfate.perk.effect.NoFoodHungerEffect;
import com.sharedfate.perk.effect.NoHungerDrainEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import org.jetbrains.annotations.Nullable;

/**
 * 먹기와 허기에 끼어드는 증강들의 판정부.
 *
 * <p>{@link com.sharedfate.mixin.FoodPropertiesMixin},
 * {@link com.sharedfate.mixin.PlayerSharedExhaustionMixin},
 * {@link com.sharedfate.mixin.FoodDataRegenExhaustionMixin} 이 처리 한가운데서 여기에 물어보고,
 * 답에 따라 값을 바꾸거나 건너뛴다. 판정을 mixin 밖에 떼어 둔 이유는 두 가지다. mixin 에는
 * 판단이 아니라 "어디서 끼어드는가"만 남는 편이 읽기 쉽고, 판정을 월드 없이 시험할 수 있다.
 *
 * <p>여기서 답하는 물음은 다섯이다.
 *
 * <ul>
 *   <li>{@link #blocksFoodHunger} — 음식으로 허기를 얻지 못하는가 ({@code no_food_hunger})</li>
 *   <li>{@link #nutritionMultiplier} — 음식이 채워 주는 양에 곱할 배율 ({@code food_nutrition})</li>
 *   <li>{@link #exhaustionMultiplier} — 허기 소모도에 곱할 배율
 *       ({@code hunger_drain} / {@code no_hunger_drain})</li>
 *   <li>{@link #grantEatingEffects} — 먹는 순간에 얹을 효과가 있는가 ({@code food_nutrition})</li>
 *   <li>{@link #blocksNaturalRegenExhaustion} — 자연 회복의 대가까지 면제하는가
 *       ({@code no_hunger_drain} 의 {@code includeNaturalRegen})</li>
 * </ul>
 *
 * <p>보유 증강이 하나도 없으면 어느 물음도 팀 상태 두 번만 보고 곧바로 "해당 없음"이다. 증강을
 * 쓰지 않는 팀의 먹기·허기 경로에는 사실상 아무 부담도 얹히지 않고, 답이 전부 "해당 없음"일 때
 * 두 mixin 은 바닐라와 완전히 같은 길을 지난다.
 *
 * <h2>{@code no_food_hunger} 와 {@code food_nutrition} 이 함께 걸렸을 때</h2>
 * <p><b>막는 쪽이 이긴다.</b> 배수의 대상 자체가 0 이므로 몇 배를 곱해도 0 이다. 부르는 쪽이
 * 순서를 헷갈리지 않도록 {@link #blocksFoodHunger} 를 먼저 묻고, 참이면
 * {@link #nutritionMultiplier} 는 아예 쓰지 않는 것이 규칙이다.
 *
 * <p>다만 {@link #grantEatingEffects} 는 막혔든 아니든 그대로 걸린다. 먹는 순간의 효과는
 * "회복량"의 대가가 아니라 "먹는 행위"의 대가라, 회복이 막혔다고 대가까지 면제될 이유가 없다.
 */
public final class PerkFoodRules {
	private PerkFoodRules() {
	}

	/**
	 * 이 대상이 지금 음식으로 허기를 얻지 못하는가.
	 *
	 * <p>서버의 팀원일 때만 참이 될 수 있다. 클라이언트 쪽 플레이어는 팀 상태를 볼 수 없으므로
	 * 언제나 거짓이고, 그래서 먹은 직후 한 틱 동안은 화면의 허기 막대가 잠깐 올라갔다가
	 * {@link #resyncFoodDisplay} 가 부르는 동기화로 제자리를 찾는다.
	 */
	public static boolean blocksFoodHunger(@Nullable LivingEntity entity) {
		return blocks(teamStateOf(entity));
	}

	/** 이 팀 상태가 {@code no_food_hunger} 를 갖고 있는가. */
	public static boolean blocks(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return false;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoFoodHungerEffect) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 회복량 배율

	/** 이 대상이 먹을 음식에 곱할 배율. 해당 없으면 1.0. */
	public static double nutritionMultiplier(@Nullable LivingEntity entity) {
		return nutritionMultiplier(teamStateOf(entity));
	}

	/**
	 * 이 팀이 가진 {@code food_nutrition} 배율을 모두 곱한 값.
	 *
	 * <p>여러 개를 가졌으면 전부 곱한다. 서로 다른 증강이 각각 약속한 배율이라 하나만 골라
	 * 줄 이유가 없다. {@code damage_dealt} 배율을 모으는 규칙과 같다.
	 */
	public static double nutritionMultiplier(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return 1.0;
		}
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof FoodNutritionEffect nutrition) {
					total *= nutrition.multiplier();
				}
			}
		}
		return Double.isFinite(total) && total >= 0.0 ? total : 1.0;
	}

	/**
	 * 배율을 먹인 음식 정의.
	 *
	 * <p>배율이 1 이면 받은 것을 <b>그대로</b> 돌려준다. 증강이 없는 팀의 먹기 경로에는 새
	 * 객체가 하나도 생기지 않고, 부르는 쪽은 {@code ==} 로 "손대지 않았음"을 알 수 있다.
	 */
	public static FoodProperties scaleNutrition(FoodProperties properties,
			@Nullable LivingEntity entity) {
		return FoodNutritionEffect.scale(properties, nutritionMultiplier(entity));
	}

	/**
	 * 먹은 사람에게 {@code food_nutrition} 의 하위 효과를 얹는다.
	 *
	 * <p>하위 효과가 없는 정의가 대부분이라 이 순회는 대개 아무 일도 하지 않는다.
	 */
	public static void grantEatingEffects(@Nullable LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		TeamState state = teamStateOf(player);
		if (state == null) {
			return;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof FoodNutritionEffect nutrition) {
					nutrition.grantOnEat(player);
				}
			}
		}
	}

	// ------------------------------------------------------------------ 허기 소모 배율

	/**
	 * 지금 쌓이려는 허기 소모도에 곱할 배율.
	 *
	 * <p>{@code no_hunger_drain} 이 있으면 다른 값과 무관하게 0 이다. "떨어지지 않는다"는
	 * 절대적인 약속이라, 함께 걸린 {@code hunger_drain} 이 몇 배든 결과는 0 이어야 한다.
	 */
	public static double exhaustionMultiplier(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return 1.0;
		}
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoHungerDrainEffect) {
					return 0.0;
				}
				if (effect instanceof HungerDrainEffect drain) {
					total *= drain.multiplier();
				}
			}
		}
		return Double.isFinite(total) && total >= 0.0 ? total : 1.0;
	}

	/**
	 * 배율을 먹인 소모도.
	 *
	 * <p>배율이 1 이면 받은 값을 그대로 돌려주므로, 증강이 없을 때 소모도는 비트 하나도 달라지지
	 * 않는다. 음수나 무한이 흘러들어오면 손대지 않고 바닐라에 그대로 넘긴다. 그런 값은 우리가
	 * 만든 것이 아니라 다른 모드가 넣은 것이고, 여기서 판단해 고칠 일이 아니다.
	 */
	public static float scaleExhaustion(@Nullable LivingEntity entity, float exhaustion) {
		if (!Float.isFinite(exhaustion) || exhaustion <= 0.0F) {
			return exhaustion;
		}
		return applyExhaustionMultiplier(exhaustionMultiplier(teamStateOf(entity)), exhaustion);
	}

	/**
	 * 곱셈 규칙만 떼어 놓은 것. 월드 없이 시험하려고 나눠 뒀다.
	 *
	 * <p>자연 회복이 스스로 치르는 대가라면 배율을 걸지 않고 받은 값을 그대로 돌려준다.
	 * 까닭은 {@link #addNaturalRegenExhaustion} 에 적어 뒀다.
	 */
	static float applyExhaustionMultiplier(double multiplier, float exhaustion) {
		if (multiplier == 1.0 || payingNaturalRegen) {
			return exhaustion;
		}
		float scaled = (float) (exhaustion * multiplier);
		return Float.isFinite(scaled) ? Math.max(0.0F, scaled) : exhaustion;
	}

	// ------------------------------------------------------------ 자연 회복이 치르는 대가

	/**
	 * 지금 자연 회복의 대가를 치르는 중인가.
	 *
	 * <p>{@link #addNaturalRegenExhaustion} 이 도는 동안에만 참이다. 서버 스레드 하나에서만
	 * 오가는 값이라 정적 필드로 충분하다. {@code FoodData.tick} 은 서버에서만 돈다.
	 */
	private static boolean payingNaturalRegen;

	/**
	 * 이 팀이 자연 회복의 대가까지 면제하는가.
	 *
	 * <p>{@code no_hunger_drain} 을 가졌다고 무조건 참은 아니다. 대부분의 팀은 달리기·채굴·
	 * 점프 같은 <b>행동</b>의 소모도만 면제받고, 체력을 돌려주는 대가인 자연 회복의 소모도는
	 * 그대로 치른다 — 안 그러면 체력이 공짜로 무한히 차오른다. 고행자만 예외다. 최대 체력이
	 * 10 으로 영영 고정된다는 대가가 이미 있어 자연 회복까지 공짜로 만들어도 되므로,
	 * {@code no_hunger_drain} 에 {@code includeNaturalRegen: true} 를 얹어 이 물음에 참으로
	 * 답하게 했다. 자세한 사정은 {@link NoHungerDrainEffect} 에 적어 뒀다.
	 */
	public static boolean blocksNaturalRegenExhaustion(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return false;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoHungerDrainEffect noHungerDrain
						&& noHungerDrain.includeNaturalRegen()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * {@code FoodData.tick} 의 자연 회복 갈래가 치르는 소모도를 쌓는다.
	 *
	 * <p>{@link com.sharedfate.mixin.FoodDataRegenExhaustionMixin} 이 부른다. {@code entity} 는
	 * 이 {@code foodData} 의 주인이다 — {@link #blocksNaturalRegenExhaustion} 으로 그 팀이
	 * 자연 회복까지 공짜로 만드는지 먼저 묻고, 참이면 소모도를 아예 쌓지 않고 끝낸다.
	 *
	 * <h2>왜 구분해야 하는가</h2>
	 * <p>고행자의 "허기가 떨어지지 않습니다"는 기본적으로 <b>달리기·채굴·점프 같은 행동</b>의
	 * 대가를 면제해 준다는 뜻이다. 자연 회복은 다르다. 마인크래프트는 체력을 회복해 주는 대가로
	 * 그 자리에서 소모도를 치르게 하는데, 그 대가까지 0 이 되면 체력이 공짜로 무한히 차오른다.
	 * 그래서 {@code includeNaturalRegen} 이 거짓인 팀에서는 이 경로의 소모도가 배율을 타지 않고
	 * 그대로 지나간다. 고행자처럼 참인 팀에서만 이 경로도 함께 면제된다 — 최대 체력 10 고정이
	 * 그 대가를 대신 치르고 있기 때문이다.
	 *
	 * <h2>26.2 에서 실제로 어떻게 갈리는가</h2>
	 * <p>javap 로 {@code FoodData.tick} 을 확인해 보면 자연 회복 두 갈래 모두
	 * {@code player.causeFoodExhaustion(..)} 이 아니라 <b>{@code this.addExhaustion(..)}</b> 를
	 * 부른다. 즉 26.2 에서는 자연 회복의 대가가 배율이 걸리는 자리({@code causeFoodExhaustion})를
	 * 애초에 지나가지 않는다. 반대로 달리기·채굴·점프·수영·허기 상태이상은 모두
	 * {@code causeFoodExhaustion} 을 지난다. 그래서 이 갈림은 지금 이미 성립해 있다.
	 *
	 * <p>다만 그건 <b>우연히</b> 성립한 것이고, 마인크래프트가 이 두 줄을
	 * {@code causeFoodExhaustion} 으로 바꾸는 순간 조용히 무너진다. {@code payingNaturalRegen}
	 * 표시가 그 경우를 막는다. 자연 회복이 어느 통로로 값을 치르든(면제 대상이 아닌 한) 배율은
	 * 걸리지 않는다. 지금은 아무 일도 하지 않고, 통로가 바뀌어도 뜻이 그대로 남는다.
	 */
	public static void addNaturalRegenExhaustion(FoodData foodData, float exhaustion,
			@Nullable LivingEntity entity) {
		if (foodData == null) {
			return;
		}
		if (blocksNaturalRegenExhaustion(teamStateOf(entity))) {
			return;
		}
		boolean previous = payingNaturalRegen;
		payingNaturalRegen = true;
		try {
			foodData.addExhaustion(exhaustion);
		} finally {
			payingNaturalRegen = previous;
		}
	}

	// ------------------------------------------------------------------ 공통

	/**
	 * 막아 놓고 나서 화면의 허기 막대를 서버 값으로 되돌린다.
	 *
	 * <p>클라이언트는 먹기를 미리 그려 두므로 허기 막대가 혼자 올라가 있다. 그런데 서버 쪽
	 * 허기는 우리가 막아서 <em>바뀌지 않았고</em>, 바닐라는 값이 달라졌을 때만 동기화 꾸러미를
	 * 보내므로 가만히 두면 틀린 막대가 다음 변화까지 그대로 남는다.
	 * {@code resetSentInfo} 로 "마지막으로 보낸 값"을 지워 두면 다음 틱에 반드시 다시 보낸다.
	 *
	 * <p>배율만 걸렸을 때는 부를 필요가 없다. 서버 허기가 <em>실제로</em> 달라지므로 바닐라가
	 * 알아서 새 값을 보낸다.
	 */
	public static void resyncFoodDisplay(@Nullable LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			player.resetSentInfo();
		}
	}

	/**
	 * 이 대상이 속한 팀의 상태. 증강을 쓰지 않으면 null.
	 *
	 * <p>서버의 팀원이고, 증강을 켰고, 가진 증강이 하나라도 있어야 한다. 클라이언트 쪽
	 * 플레이어는 팀 상태를 볼 수 없어 언제나 null 이므로, 여기에 기대는 판정은 클라이언트에서
	 * 전부 "해당 없음"으로 떨어진다.
	 */
	private static @Nullable TeamState teamStateOf(@Nullable LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return null;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return null;
		}
		return state;
	}
}
