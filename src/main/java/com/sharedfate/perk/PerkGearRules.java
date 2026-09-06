package com.sharedfate.perk;

import com.sharedfate.perk.effect.DurabilityMultiplierEffect;
import com.sharedfate.perk.effect.EquipBanEffect;
import com.sharedfate.perk.effect.ItemBanEffect;
import com.sharedfate.perk.effect.NoDamageBoostEffect;
import com.sharedfate.perk.effect.OffhandLockEffect;
import com.sharedfate.perk.effect.WeaponDamageEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 장비·무기 관련 증강의 판정부.
 *
 * <p>mixin 과 집행부는 "어디서 막는가"만 갖고, "막아야 하는가"는 전부 여기에 물어본다.
 *
 * <h2>빠른 경로가 곧 기본 경로다</h2>
 * <p>모든 질문은 {@link #activeState} 로 시작한다. 팀에 속하지 않았거나, 증강을 끈 팀이거나,
 * 아직 아무 증강도 없으면 거기서 곧바로 돌아온다. <b>증강 풀이 비어 있는 서버에서는 이 클래스의
 * 모든 답이 "제한 없음"이므로 바닐라와 동작이 같다.</b>
 *
 * <h2>증강을 잃으면 제한도 사라진다</h2>
 * <p>여기 있는 것은 상태가 아니라 질문이다. 제한은 플레이어에게 붙어 있지 않고 그때그때
 * {@link TeamState#ownedPerks} 를 읽어 답한다. 그래서 회차 리셋이나 팀 해체로 보유 증강이
 * 사라지면 다음 질문부터 자동으로 "제한 없음"이 된다. 유일하게 흔적을 남기는 것은 공격력
 * 수정자인데, 그것도 {@link PerkGearManager} 가 매 점검마다 걷어낸다.
 */
public final class PerkGearRules {
	private PerkGearRules() {
	}

	// ------------------------------------------------------------------ 빠른 경로

	/**
	 * 장비 제한을 따질 값어치가 있는 팀 상태인가.
	 *
	 * @return 따질 만하면 그 상태, 아니면 null
	 */
	public static @Nullable TeamState activeState(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return null;
		}
		return state;
	}

	/** 이 플레이어가 속한 팀의 상태. 서버의 팀원이 아니면 null. */
	public static @Nullable TeamState activeState(@Nullable ServerPlayer player) {
		if (player == null) {
			return null;
		}
		return activeState(TeamLookup.stateOf(player.getUUID()));
	}

	// ------------------------------------------------------------------ 착용 제한

	/** 이 칸이 통째로 막혔는가. {@code equip_ban} 만 본다. */
	public static boolean slotBanned(@Nullable TeamState state, @Nullable EquipmentSlot slot) {
		if (slot == null) {
			return false;
		}
		return find(state, EquipBanEffect.class, effect -> effect.bans(slot)) != null;
	}

	/** 이 플레이어에게 그 칸이 막혔는가. */
	public static boolean slotBanned(@Nullable ServerPlayer player, @Nullable EquipmentSlot slot) {
		return slotBanned(activeState(player), slot);
	}

	/** 이 아이템이 무력해졌는가. {@code item_ban} 만 본다. */
	public static boolean itemBanned(@Nullable TeamState state, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return find(state, ItemBanEffect.class, effect -> effect.matches(stack)) != null;
	}

	/** 이 플레이어에게 그 아이템이 무력해졌는가. */
	public static boolean itemBanned(@Nullable ServerPlayer player, @Nullable ItemStack stack) {
		return itemBanned(activeState(player), stack);
	}

	/**
	 * 이 아이템이 무력화가 아니라 <b>자동 폐기</b> 대상인가.
	 *
	 * <p>{@code item_ban} 에 {@code discard: true} 가 적혀 있고 이 아이템이 그 무리에
	 * 들어갈 때만 참이다. {@link com.sharedfate.perk.PerkGearManager} 가 핫바와 방어구 칸을
	 * 훑을 때 이 답으로 "치워서 인벤토리로 보낼지"와 "그냥 떨어뜨릴지"를 가른다.
	 */
	public static boolean itemBanDiscards(@Nullable TeamState state, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return find(state, ItemBanEffect.class, effect -> effect.discard() && effect.matches(stack)) != null;
	}

	/** 이 플레이어에게 그 아이템이 자동 폐기 대상인가. */
	public static boolean itemBanDiscards(@Nullable ServerPlayer player, @Nullable ItemStack stack) {
		return itemBanDiscards(activeState(player), stack);
	}

	/**
	 * 지금 이 칸에 이 장비가 붙어 있으면 안 되는가.
	 *
	 * <p>칸이 막혔거나 아이템이 막혔으면 참이다. 이미 입고 있던 장비를 벗기는 판정에 쓴다.
	 */
	public static boolean equipmentBlocked(@Nullable TeamState state, @Nullable EquipmentSlot slot,
			@Nullable ItemStack stack) {
		return slotBanned(state, slot) || itemBanned(state, stack);
	}

	// ------------------------------------------------------------------ 내구도

	/**
	 * 이 아이템이 몇 배로 오래 가는가. 해당하는 규칙이 없으면 1.
	 *
	 * <p>여러 규칙이 겹치면 먼저 얻은 증강이 이긴다. 곱해서 쌓지 않는다.
	 */
	public static double durabilityMultiplier(@Nullable TeamState state, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 1.0;
		}
		DurabilityMultiplierEffect effect =
				find(state, DurabilityMultiplierEffect.class, rule -> rule.matches(stack));
		return effect == null ? 1.0 : effect.multiplier();
	}

	/** 이 플레이어에게 그 아이템이 몇 배로 오래 가는가. */
	public static double durabilityMultiplier(@Nullable ServerPlayer player,
			@Nullable ItemStack stack) {
		return durabilityMultiplier(activeState(player), stack);
	}

	/**
	 * 이번에 닳을 내구도를 배수만큼 깎는다. <b>이 계산이 내구도 증강의 전부다.</b>
	 *
	 * <p>내구도는 정수라 그냥 나누면 안 된다. 한 번에 1씩 닳는 것이 대부분인데 1을 3으로 나누면
	 * 0이 되어 <b>영영 닳지 않는 장비</b>가 된다. 그래서 몫은 그대로 깎고 <b>나머지는 확률로</b>
	 * 처리한다. 배수 3에 소모량 1이면 1/3 확률로 1이 닳는다 — 여러 번 반복하면 평균이 정확히
	 * 1/3 이라 내구도가 3배가 된다. 내구성 마법의 계산이 끝난 <b>뒤</b>에 이 깎기가 걸리므로
	 * 둘은 자연스럽게 곱해진다.
	 *
	 * @param amount 바닐라가 정한 소모량. 0 이하면 손대지 않는다
	 * @param multiplier 내구도 배수. 1 이하면 손대지 않는다
	 * @param roll 0 이상 1 미만의 난수
	 * @return 실제로 닳을 양
	 */
	public static int reduceDurabilityLoss(int amount, double multiplier, double roll) {
		if (amount <= 0 || multiplier <= 1.0 || !Double.isFinite(multiplier)) {
			return amount;
		}
		double scaled = amount / multiplier;
		int whole = (int) scaled;
		double fraction = scaled - whole;
		return roll < fraction ? whole + 1 : whole;
	}

	/**
	 * 이 플레이어가 이 아이템을 쓸 때 이번에 닳을 내구도.
	 *
	 * <p>증강이 없으면 {@code amount} 를 그대로 돌려준다. 그래서 증강 풀이 빈 서버에서는
	 * 바닐라와 완전히 같다.
	 */
	public static int reduceDurabilityLoss(@Nullable TeamState state, @Nullable ItemStack stack,
			int amount, @Nullable RandomSource random) {
		if (amount <= 0 || random == null) {
			return amount;
		}
		double multiplier = durabilityMultiplier(state, stack);
		if (multiplier <= 1.0) {
			return amount;
		}
		return reduceDurabilityLoss(amount, multiplier, random.nextDouble());
	}

	// ------------------------------------------------------------------ 왼손 고정

	/** 이 팀의 왼손 고정 규칙. 없으면 null. 여러 개면 먼저 얻은 것이 이긴다. */
	public static @Nullable OffhandLockEffect offhandLock(@Nullable TeamState state) {
		return find(state, OffhandLockEffect.class, effect -> true);
	}

	/** 이 플레이어의 왼손 고정 규칙. 없으면 null. */
	public static @Nullable OffhandLockEffect offhandLock(@Nullable ServerPlayer player) {
		return offhandLock(activeState(player));
	}

	/**
	 * 이 묶음을 왼손 칸에 놓을 수 있는가.
	 *
	 * <p>고정 규칙이 없으면 언제나 놓을 수 있다. 규칙이 있으면 지정 아이템만 놓을 수 있고,
	 * 빈 묶음(= 칸을 비우는 동작)은 막지 않는다. 비우는 것까지 막으면 아이템을 꺼내지 못해
	 * 인벤토리가 잠긴 것처럼 보인다. 비워도 곧 {@link PerkGearManager} 가 다시 채운다.
	 */
	public static boolean mayPlaceInOffhand(@Nullable TeamState state, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		OffhandLockEffect lock = offhandLock(state);
		return lock == null || lock.matches(stack);
	}

	/** 이 플레이어가 그 묶음을 왼손 칸에 놓을 수 있는가. */
	public static boolean mayPlaceInOffhand(@Nullable ServerPlayer player, @Nullable ItemStack stack) {
		return mayPlaceInOffhand(activeState(player), stack);
	}

	// ------------------------------------------------------------------ 무기 공격력

	/** 이 팀의 무기 공격력 규칙. 없으면 null. 여러 개면 먼저 얻은 것이 이긴다. */
	public static @Nullable WeaponDamageEffect weaponRule(@Nullable TeamState state) {
		return find(state, WeaponDamageEffect.class, effect -> true);
	}

	/** 이 플레이어의 무기 공격력 규칙. 없으면 null. */
	public static @Nullable WeaponDamageEffect weaponRule(@Nullable ServerPlayer player) {
		return weaponRule(activeState(player));
	}

	/** 이 팀이 "공격력 증가 효과를 못 받는다"({@code no_damage_boost})를 가졌는가. */
	public static boolean hasNoDamageBoost(@Nullable TeamState state) {
		return find(state, NoDamageBoostEffect.class, effect -> true) != null;
	}

	/** 이 플레이어에게 그 제한이 걸렸는가. */
	public static boolean hasNoDamageBoost(@Nullable ServerPlayer player) {
		return hasNoDamageBoost(activeState(player));
	}

	// ------------------------------------------------------------------ 공통

	/**
	 * 이 팀이 가진 증강 중 조건에 맞는 효과 하나를 찾는다.
	 *
	 * <p>풀에서 사라진 id 는 건너뛴다. 보유 목록에 남아 있어도 정의가 없으면 아무 제한도
	 * 걸지 않는다는 뜻이다.
	 */
	private static <T extends PerkEffect> @Nullable T find(@Nullable TeamState state,
			Class<T> type, Predicate<T> test) {
		TeamState active = activeState(state);
		if (active == null) {
			return null;
		}
		for (String perkId : active.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (type.isInstance(effect)) {
					T typed = type.cast(effect);
					if (test.test(typed)) {
						return typed;
					}
				}
			}
		}
		return null;
	}
}
