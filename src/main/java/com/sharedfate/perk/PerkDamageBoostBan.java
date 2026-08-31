package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.TeamState;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code no_damage_boost} 증강(프리즘 「삽질의 대가」의 대가)의 집행부.
 *
 * <p>이 사람의 {@code minecraft:attack_damage} 속성에서, <b>손에 든 무기 자체가 얹는 값</b>과
 * <b>이 증강 자신({@link PerkWeaponDamage#MODIFIER_ID})이 얹는 값</b>을 뺀 나머지 수정자 중
 * <b>양수인 것만</b> 상쇄한다. 음수(공격력 감소)는 원천에 상관없이 그대로 둔다.
 *
 * <h2>상쇄 수정자가 셋인 이유</h2>
 * <p>바닐라 속성 계산은 {@code 기본값 + Σ(ADD_VALUE)} 를 구한 뒤 {@code ×(1+Σ(ADD_MULTIPLIED_BASE))},
 * 마지막으로 {@code ×∏(1+ADD_MULTIPLIED_TOTAL)} 순서로 세 단계를 거친다. 막힌 양수 수정자들을
 * 정확히 되돌리려면 그 단계마다 하나씩, 총 세 개의 상쇄용 수정자가 필요하다. 자세한 계산은
 * {@link #compute}에 있다.
 *
 * <h2>매 틱 다시 계산한다</h2>
 * <p>{@link PerkGearManager#tick}이 {@link PerkWeaponDamage#refresh}와 같은 자리에서 이 클래스의
 * {@link #refresh}도 부른다. 손에 든 무기와 걸려 있는 상태이상이 매 틱 바뀔 수 있으므로 저장해
 * 두지 않고 그때그때 다시 본다.
 */
public final class PerkDamageBoostBan {
	/** 더하기 단계를 상쇄하는 수정자. */
	static final Identifier ADD_VALUE_ID = SharedFateMod.id("perk/damage_boost_ban/add_value");
	/** 기본값에 곱하기 단계를 상쇄하는 수정자. */
	static final Identifier ADD_MULTIPLIED_BASE_ID =
			SharedFateMod.id("perk/damage_boost_ban/add_multiplied_base");
	/** 전체에 곱하기 단계를 상쇄하는 수정자. */
	static final Identifier ADD_MULTIPLIED_TOTAL_ID =
			SharedFateMod.id("perk/damage_boost_ban/add_multiplied_total");

	/** 값의 흔들림으로 수정자를 매 틱 다시 붙이지 않도록 두는 허용 오차. */
	private static final double EPSILON = 1.0e-6;
	/** 수정자 값의 상한. {@link PerkWeaponDamage}와 같은 값이다. */
	private static final double MAX_ABS_AMOUNT = 4096.0;

	private static volatile boolean warned;

	private PerkDamageBoostBan() {
	}

	// ------------------------------------------------------------------ 적용

	/** 이 플레이어의 상쇄 수정자 셋을 지금 상태에 맞춘다. */
	public static void refresh(@Nullable ServerPlayer player, @Nullable TeamState state) {
		if (player == null) {
			return;
		}
		try {
			AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (attribute == null) {
				return;
			}
			TeamState active = PerkGearRules.activeState(state);
			if (active == null || !PerkGearRules.hasNoDamageBoost(active)) {
				clear(player);
				return;
			}
			Compensation compensation =
					compute(attribute.getModifiers(), exemptIds(player.getMainHandItem()));
			applyOrRemove(attribute, ADD_VALUE_ID,
					compensation.addValue(), AttributeModifier.Operation.ADD_VALUE);
			applyOrRemove(attribute, ADD_MULTIPLIED_BASE_ID,
					compensation.addMultipliedBase(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			applyOrRemove(attribute, ADD_MULTIPLIED_TOTAL_ID,
					compensation.addMultipliedTotal(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	/** 상쇄 수정자 셋을 무조건 걷어낸다. 팀에서 빠졌거나 증강을 잃었을 때 쓴다. */
	public static void clear(@Nullable ServerPlayer player) {
		if (player == null) {
			return;
		}
		try {
			AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (attribute == null) {
				return;
			}
			attribute.removeModifier(ADD_VALUE_ID);
			attribute.removeModifier(ADD_MULTIPLIED_BASE_ID);
			attribute.removeModifier(ADD_MULTIPLIED_TOTAL_ID);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	// ------------------------------------------------------------------ 계산

	/** 세 상쇄용 수정자에 넣을 값. 플레이어를 읽지 않는 순수 계산이라 살아 있는 서버 없이 시험할 수 있다. */
	public record Compensation(double addValue, double addMultipliedBase, double addMultipliedTotal) {
	}

	/**
	 * 지금 붙어 있는 수정자들 중 {@code exemptIds}에 없는 양수 수정자들을 골라, 그것들을 정확히
	 * 상쇄할 세 값을 구한다.
	 *
	 * @param currentModifiers 지금 속성에 붙어 있는 수정자 전부(우리 상쇄용 수정자도 포함해도 된다 —
	 *                         {@code exemptIds}에 넣어 두면 스스로를 다시 상쇄하는 일이 없다)
	 * @param exemptIds        "정상"으로 치고 건드리지 않을 수정자 id들
	 */
	public static Compensation compute(Collection<AttributeModifier> currentModifiers,
			Set<Identifier> exemptIds) {
		double addValueSum = 0.0;
		double addMultipliedBaseSum = 0.0;
		double multipliedTotalFactor = 1.0;
		for (AttributeModifier modifier : currentModifiers) {
			if (modifier == null || exemptIds.contains(modifier.id())) {
				continue;
			}
			double amount = modifier.amount();
			if (!(amount > 0.0)) {
				// 0 이하(효과 없음이거나 감소)는 막지 않는다.
				continue;
			}
			switch (modifier.operation()) {
				case ADD_VALUE -> addValueSum += amount;
				case ADD_MULTIPLIED_BASE -> addMultipliedBaseSum += amount;
				case ADD_MULTIPLIED_TOTAL -> multipliedTotalFactor *= (1.0 + amount);
			}
		}
		double totalCompensation = multipliedTotalFactor > 0.0 ? 1.0 / multipliedTotalFactor - 1.0 : 0.0;
		return new Compensation(-addValueSum, -addMultipliedBaseSum, totalCompensation);
	}

	/** 지금 손에 든 것 자체가 얹는 수정자 id들 + 이 판정이 손대면 안 되는 것들. */
	public static Set<Identifier> exemptIds(@Nullable ItemStack mainHand) {
		Set<Identifier> ids = new HashSet<>();
		ids.add(ADD_VALUE_ID);
		ids.add(ADD_MULTIPLIED_BASE_ID);
		ids.add(ADD_MULTIPLIED_TOTAL_ID);
		ids.add(PerkWeaponDamage.MODIFIER_ID);
		if (mainHand == null || mainHand.isEmpty()) {
			return ids;
		}
		try {
			Attribute attackDamage = Attributes.ATTACK_DAMAGE.value();
			mainHand.forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
				if (isSame(holder, attackDamage)) {
					ids.add(modifier.id());
				}
			});
		} catch (RuntimeException error) {
			warnOnce(error);
		}
		return ids;
	}

	private static boolean isSame(@Nullable Holder<Attribute> holder, Attribute attackDamage) {
		try {
			return holder != null && holder.value() == attackDamage;
		} catch (RuntimeException error) {
			return false;
		}
	}

	private static void applyOrRemove(AttributeInstance attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeModifier current = attribute.getModifier(id);
		if (Math.abs(amount) < EPSILON) {
			if (current != null) {
				attribute.removeModifier(id);
			}
			return;
		}
		double clamped = clamp(amount);
		AttributeModifier desired = new AttributeModifier(id, clamped, operation);
		if (desired.equals(current)) {
			return;
		}
		if (current != null) {
			attribute.removeModifier(id);
		}
		attribute.addTransientModifier(desired);
	}

	private static double clamp(double amount) {
		if (!Double.isFinite(amount)) {
			return 0.0;
		}
		return Math.max(-MAX_ABS_AMOUNT, Math.min(MAX_ABS_AMOUNT, amount));
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"증강의 공격력 증가 차단을 맞추지 못했습니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 경고 억제 상태를 되돌릴 때 쓴다. */
	static void resetWarnedForTesting() {
		warned = false;
	}
}
