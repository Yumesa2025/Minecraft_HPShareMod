package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 팀원의 <b>공격력을 깎는 효과</b>를 전부 무효로 만드는 표시.
 * 세트 「무기 3단계 — 깎이지 않는다」가 쓴다.
 *
 * <p>정의는 {@code { "type": "no_attack_damage_loss" }} 하나뿐이고 필드가 없다.
 *
 * <p>{@link PerkEffect#apply} 로 팀원에게 붙일 것이 없다. 「효과를 붙인다」가 아니라 「이미 있는
 * 효과 중 일부를 건너뛰게 한다」는 보상이라, 이 클래스는 「이 팀에 그 규칙이 켜졌는가」라는
 * 물음에만 답한다.
 *
 * <h2>{@code no_defense_drawbacks} 와 결정적으로 다른 점</h2>
 * <p>방어 3단계는 {@code PerkDrawbacks} 표에 올라 있는 <b>「대가」 표시가 붙은 효과</b> 중
 * <b>방어 유형 증강</b>의 것만 걷어낸다. 즉 무엇을 지울지는 정의 파일에 사람이 손으로 적어
 * 두었고, 코드는 그 표시만 읽는다.
 *
 * <p>이쪽은 그럴 수 없다. 지금 가진 증강뿐 아니라 <b>앞으로 새로 얻는 증강까지</b> 공격력을
 * 깎는 것이면 무엇이든 걸려야 하므로, 증강 id 목록도 {@code drawback} 표시도 판정 근거가
 * 될 수 없다. 그래서 이 클래스는 <b>효과 객체가 실제로 무엇을 하는지</b>를 보고 판정한다
 * ({@link #reduces}). 새 증강이 공격력을 깎는 순간 그 정의는 {@link AttributeEffect} 나
 * {@link DamageDealtEffect} 로 읽히므로, 등록도 목록 갱신도 없이 자동으로 걸린다.
 *
 * <p>대상이 유형과도 무관하다. 채굴 유형인 「익숙한 손목」·「굴착기」의 공격력 감소도 함께
 * 사라진다 — 세트가 약속하는 것은 「무기 증강의 대가가 사라진다」가 아니라 「공격력이 깎이지
 * 않는다」이기 때문이다.
 *
 * <h2>무엇을 「공격력 감소」로 보는가</h2>
 * <ul>
 *   <li>{@code attribute} 중 {@code minecraft:attack_damage} 에 <b>음수</b> 값을 거는 것.
 *       연산({@code add_value}·{@code add_multiplied_base}·{@code add_multiplied_total})은
 *       가리지 않는다 — 어느 쪽이든 음수면 결과가 줄어든다.</li>
 *   <li>{@code damage_dealt} 중 배율이 <b>1 미만</b>인 것.</li>
 * </ul>
 *
 * <p><b>올리는 쪽은 절대 건드리지 않는다.</b> 양수 {@code attribute} 와 1 이상인
 * {@code damage_dealt} 는 판정에서 곧바로 거짓이 되어 평소와 한 톨도 다르지 않게 붙는다.
 * 「포식자」처럼 한 증강 안에 증가와 감소가 함께 있는 정의에서도 감소 한 줄만 빠진다.
 *
 * <h2>몹 쪽 값은 건드리지 않는다</h2>
 * <p>{@code mob_damage} 는 판정 대상이 아니다. 그것은 팀원의 공격력이 아니라 <b>몹의 공격력</b>
 * 이고, 게다가 지금 쓰이는 방향은 전부 「몹이 약해진다」라 팀에게 이득이다. 무효화하면 오히려
 * 「불면의 파수꾼」의 보상이 사라진다. {@code mob_health}·{@code mob_speed} 도 같은 이유로
 * 손대지 않는다.
 *
 * <p><b>받는 피해가 늘어나는 효과</b>({@code damage_taken}·{@code damage_taken_from})도 대상이
 * 아니다. 이 세트가 막는 것은 「공격력 감소」 하나뿐이다.
 *
 * <h2>실제로 걷어내는 곳</h2>
 * <p>{@link Gate} 를 쓰는 자리들이다. 감소가 실제로 붙는 경로마다 한 곳씩 있다.
 *
 * <ul>
 *   <li>{@code PerkManager.refreshPlayer} — 최상위 {@code attribute} 감소. 붙이는 대신 걷어낸다</li>
 *   <li>{@code PerkManager.multiplier} — {@code damage_dealt} 배율. 곱하지 않고 건너뛴다</li>
 *   <li>{@code ConditionalPerkManager.refreshPlayer} — 위와 짝을 이루는 주기 재평가.
 *       여기서 걸러 내지 않으면 {@code refreshPlayer} 가 걷어낸 것을 반 초 뒤에 다시 붙인다.
 *       {@code conditional}·{@code holder}·{@code periodic} 처럼 <b>감싼 효과가 스스로</b>
 *       하위를 붙였다 떼는 경로도 이 주기가 뒤따라가 걷어낸다</li>
 *   <li>{@code TemporaryPerkGrants.grant} — {@code on_team_hurt}·{@code on_critical}·
 *       {@code on_swap} 의 하위 효과. 아예 얹지 않는다</li>
 *   <li>{@code PerkWeaponDamage.desired} — {@code weapon_damage} 의 {@code othersDamage} 가
 *       공격력을 <b>끌어내리는</b> 경우</li>
 * </ul>
 */
public final class NoAttackDamageLossEffect implements PerkEffect {
	/** 판정 기준이 되는 속성. */
	public static final Identifier ATTACK_DAMAGE_ID =
			Identifier.fromNamespaceAndPath("minecraft", "attack_damage");

	/**
	 * 감싼 효과를 파고드는 깊이 한계.
	 *
	 * <p>{@code conditional} 은 두 겹까지 중첩될 수 있고 그 안에 {@code holder} 가 또 들어갈 수
	 * 있다. 세 겹이면 지금 정의 파일이 만들 수 있는 모든 모양을 덮는다. 한계를 두는 것은 순환
	 * 참조로 무한히 도는 사고를 원천 봉쇄하기 위해서다.
	 */
	private static final int MAX_DEPTH = 3;

	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoAttackDamageLossEffect INSTANCE = new NoAttackDamageLossEffect();

	private NoAttackDamageLossEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}

	// ------------------------------------------------------------------ 세트 판정

	/**
	 * 이 팀이 이 표시를 가졌는가. 가졌으면 공격력 감소가 전부 사라진다.
	 *
	 * <p>팀이 없거나 증강을 껐으면 곧바로 거짓이다. 보유 증강과 켜진 세트를 <b>둘 다</b> 본다 —
	 * 지금 이 형을 태우는 것은 세트뿐이지만, 훑는 자리가 한쪽만 보면 나중에 증강에 붙였을 때
	 * 빌드도 통과하고 로그도 없이 무동작이 된다. {@link NoDefenseDrawbacksEffect#heldBy} 와 같은
	 * 모양이다.
	 */
	public static boolean heldBy(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled) {
			return false;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof NoAttackDamageLossEffect) {
					return true;
				}
			}
		}
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof NoAttackDamageLossEffect) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 효과 판정

	/**
	 * 이 효과가 팀원의 공격력을 깎는가.
	 *
	 * <p>세트가 켜졌는지는 보지 않는 <b>순수 판정</b>이다. 살아 있는 서버도 팀도 없이 시험할 수
	 * 있고, 「무엇을 감소로 보는가」라는 결정이 이 메서드 하나에만 있다.
	 *
	 * <p>감싼 효과({@code conditional}·{@code holder}·{@code periodic})는 스스로 공격력을 깎지
	 * 않으므로 언제나 거짓이다. 그 안에 든 감소는 {@link #childrenOf} 로 파고들어 찾는다.
	 */
	public static boolean reduces(@Nullable PerkEffect effect) {
		if (effect instanceof AttributeEffect attribute) {
			return ATTACK_DAMAGE_ID.equals(attribute.attributeId()) && attribute.amount() < 0.0;
		}
		if (effect instanceof DamageDealtEffect dealt) {
			return dealt.multiplier() < 1.0;
		}
		return false;
	}

	/** 목록에 공격력 감소가 하나라도 들어 있는가. 없으면 부르는 쪽이 팀 상태를 볼 필요조차 없다. */
	public static boolean anyReduction(@Nullable Iterable<PerkEffect> effects) {
		if (effects == null) {
			return false;
		}
		for (PerkEffect effect : effects) {
			if (reduces(effect)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 이 효과가 <b>스스로 붙였다 떼는</b> 하위 효과들. 감싼 효과가 아니면 빈 목록.
	 *
	 * <p>{@code conditional}·{@code holder}·{@code periodic} 은 자기 하위를 자기 일정에 맞춰
	 * 붙였다 뗀다. 그래서 부모를 건너뛰는 것만으로는 안에 든 공격력 감소를 막을 수 없고
	 * (그러면 같은 묶음에 든 <b>증가</b>까지 함께 사라진다), 붙은 뒤에 감소만 골라 걷어내야 한다.
	 *
	 * <p>{@code custom} 은 파고들지 않는다. 그쪽이 무엇을 붙이는지는 Java 핸들러만 알고 있어
	 * 정의를 읽어서는 알 수 없다.
	 */
	public static List<PerkEffect> childrenOf(@Nullable PerkEffect effect) {
		if (effect instanceof ConditionalEffect conditional) {
			return conditional.children();
		}
		if (effect instanceof HolderEffect holder) {
			return holder.children();
		}
		if (effect instanceof PeriodicEffect periodic) {
			List<PerkEffect> all = new ArrayList<>(periodic.base());
			for (PeriodicEffect.Phase phase : periodic.phases()) {
				all.addAll(phase.effects());
			}
			return all;
		}
		return List.of();
	}

	// ------------------------------------------------------------------ 면제 판정

	/** 이 팀을 기준으로 한 무효화 판정 그릇을 만든다. 판정 자체는 아직 하지 않는다. */
	public static Gate gateFor(@Nullable TeamState state) {
		return new Gate(state);
	}

	/**
	 * 한 번의 조회 동안 「이 팀에서 공격력 감소가 무효인가」를 <b>최대 한 번만</b> 계산해 들고
	 * 다니는 그릇.
	 *
	 * <p>효과를 훑는 자리는 피해 계산처럼 초당 여러 번 도는 곳도 있어서, 감소를 <b>실제로
	 * 만났을 때</b> 한 번만 판정하고 그 뒤로는 기억해 둔 값을 쓴다. 공격력 감소를 하나도 갖고
	 * 있지 않은 팀은 세트를 아예 보지 않는다.
	 *
	 * <p><b>{@code PerkDrawbacks.Waiver} 와는 다른 판정이다.</b> 그쪽은 (1) {@code drawback}
	 * 표시가 붙어 있고 (2) 그 효과를 가진 증강이 방어 유형일 때만 참이다. {@code Waiver} 를
	 * 고쳐 이쪽까지 통과시키면 방어 3단계가 함께 넓어져 「방어 증강의 대가만 사라진다」가
	 * 깨진다.
	 *
	 * <p>한 번의 조회 안에서만 쓰고 버린다. 서버 스레드에서만 오간다.
	 */
	public static final class Gate {
		private final @Nullable TeamState state;
		private @Nullable Boolean active;

		private Gate(@Nullable TeamState state) {
			this.state = state;
		}

		/**
		 * 지금 이 효과를 무효로 해야 하는가.
		 *
		 * <p>둘 다 참일 때만 참이다. (1) 효과가 팀원의 공격력을 깎고, (2) 이 팀에 무기 3단계가
		 * 켜져 있다. 하나라도 어긋나면 거짓이고, 그때 부르는 쪽은 평소와 똑같이 동작해야 한다.
		 */
		public boolean suppresses(@Nullable PerkEffect effect) {
			return reduces(effect) && active();
		}

		/**
		 * 감싼 효과가 방금 붙인 하위 중 공격력 감소만 골라 걷어낸다.
		 *
		 * <p>{@code conditional}·{@code holder}·{@code periodic} 의 {@code apply} 나
		 * {@code refresh} 를 부른 <b>바로 뒤</b>에 부른다. 먼저 부르면 그 뒤의 붙이기가 방금
		 * 걷어낸 것을 도로 붙인다.
		 *
		 * <p>같은 묶음에 든 다른 하위는 손대지 않는다 — 「포식자」의 이동 속도 감소도, 「무결점」의
		 * 공격력 <b>증가</b>도 그대로 남는다.
		 */
		public void stripChildren(@Nullable ServerPlayer player, @Nullable PerkEffect parent) {
			if (player == null || parent == null) {
				return;
			}
			strip(player, childrenOf(parent), 1);
		}

		private void strip(ServerPlayer player, List<PerkEffect> effects, int depth) {
			if (effects.isEmpty() || depth > MAX_DEPTH) {
				return;
			}
			for (PerkEffect effect : effects) {
				if (reduces(effect)) {
					if (!active()) {
						// 세트가 안 켜졌으면 이 조회 내내 켜지지 않는다. 더 볼 것이 없다.
						return;
					}
					try {
						effect.remove(player);
					} catch (RuntimeException error) {
						SharedFateMod.LOGGER.warn("공격력 감소를 걷어내지 못했습니다", error);
					}
					continue;
				}
				strip(player, childrenOf(effect), depth + 1);
			}
		}

		/** 세트 판정. 한 번만 계산하고 기억한다. */
		private boolean active() {
			if (active == null) {
				active = heldBy(state);
			}
			return active;
		}
	}
}
