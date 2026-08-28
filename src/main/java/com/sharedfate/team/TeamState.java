package com.sharedfate.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sharedfate.perk.PendingOffer;
import com.sharedfate.perk.PerkMilestones;
import com.sharedfate.perk.PerkStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TeamState {
	public static final int MAIN_SIZE = 36;
	public static final int EXTRA_SIZE = 27;
	public static final int ENDER_SIZE = 27;

	public final SharedItemList mainItems;
	public final SharedItemList extraItems;
	public final PlayerEnderChestContainer enderContainer;
	public final SharedEquipmentStore equipment;
	public final List<ItemStack> overflowItems;

	public float maxHealth;
	public float health;
	public float absorption;
	public int foodLevel;
	public float saturation;
	public int xpLevel;
	public float xpProgress;
	public int totalExperience;
	public final List<MobEffectInstance> effects;
	public int positionSwapIntervalTicks;
	public int positionSwapRemainingTicks;

	/** 증강 시스템 사용 여부. 팀 생성 시 리더가 정하고 그 뒤로는 바뀌지 않는다. */
	public boolean perksEnabled;
	/** 마지막으로 처리한 레벨 구간 (0, 3, 6, …, 36). */
	public int lastPerkMilestone;
	/** 팀이 보유한 증강. 중첩은 {@link PerkStack#count()}로 표현한다. */
	public final List<PerkStack> ownedPerks = new ArrayList<>();
	/** 아직 고르지 않은 선택권. 구간 순서대로 쌓이며 여러 개일 수 있다. */
	public final List<PendingOffer> pending = new ArrayList<>();

	public TeamState(SharedItemList mainItems, SharedItemList extraItems,
			PlayerEnderChestContainer enderContainer,
			SharedEquipmentStore equipment, List<ItemStack> overflowItems,
			float maxHealth, float health, float absorption, int foodLevel, float saturation,
			int xpLevel, float xpProgress, int totalExperience, List<MobEffectInstance> effects,
			int positionSwapIntervalTicks, int positionSwapRemainingTicks) {
		this.mainItems = mainItems;
		this.extraItems = extraItems;
		this.enderContainer = enderContainer;
		this.equipment = equipment;
		this.overflowItems = new ArrayList<>();
		overflowItems.stream().filter(stack -> !stack.isEmpty()).forEach(this.overflowItems::add);
		this.maxHealth = maxHealth;
		this.health = health;
		this.absorption = absorption;
		this.foodLevel = foodLevel;
		this.saturation = saturation;
		this.xpLevel = xpLevel;
		this.xpProgress = xpProgress;
		this.totalExperience = totalExperience;
		this.effects = new ArrayList<>();
		effects.forEach(effect -> this.effects.add(new MobEffectInstance(effect)));
		this.positionSwapIntervalTicks = positionSwapIntervalTicks;
		this.positionSwapRemainingTicks = positionSwapRemainingTicks;
	}

	public static TeamState fresh(float maxHealth) {
		return new TeamState(
				SharedItemList.ofSize(MAIN_SIZE),
				SharedItemList.ofSize(EXTRA_SIZE),
				new PlayerEnderChestContainer(),
				new SharedEquipmentStore(),
				List.of(),
				maxHealth, maxHealth, 0.0F, 20, 5.0F, 0, 0.0F, 0, List.of(),
				0, 0
		);
	}

	private static final Codec<PlayerEnderChestContainer> ENDER_CODEC =
			ItemStack.OPTIONAL_CODEC.listOf().xmap(
					stacks -> {
						PlayerEnderChestContainer container = new PlayerEnderChestContainer();
						for (int i = 0; i < Math.min(ENDER_SIZE, stacks.size()); i++) {
							container.setItem(i, stacks.get(i));
						}
						return container;
					},
					container -> List.copyOf(container.getItems())
			);

	public void resetAfterDeath(float maxHealth, boolean keepExperience) {
		this.maxHealth = sanitizeMaximum(maxHealth, 20.0F);
		health = this.maxHealth;
		absorption = 0.0F;
		foodLevel = 20;
		saturation = 5.0F;
		if (!keepExperience) {
			xpLevel = 0;
			xpProgress = 0.0F;
			totalExperience = 0;
		}
	}

	public void sanitize(float maxHealth) {
		float safeMaximum = sanitizeMaximum(this.maxHealth, maxHealth);
		this.maxHealth = safeMaximum;
		health = Float.isFinite(health) ? Math.max(0.0F, Math.min(safeMaximum, health)) : safeMaximum;
		absorption = Float.isFinite(absorption) ? Math.max(0.0F, Math.min(1024.0F, absorption)) : 0.0F;
		foodLevel = Math.max(0, Math.min(20, foodLevel));
		saturation = Float.isFinite(saturation)
				? Math.max(0.0F, Math.min(foodLevel, saturation)) : 0.0F;
		totalExperience = Math.max(0, totalExperience);
		xpLevel = Math.max(0, xpLevel);
		xpProgress = Float.isFinite(xpProgress)
				? Math.max(0.0F, Math.min(1.0F, xpProgress)) : 0.0F;
		overflowItems.removeIf(ItemStack::isEmpty);
		if (positionSwapIntervalTicks < 0 || positionSwapIntervalTicks > PositionSwapLimits.MAX_INTERVAL_TICKS) {
			positionSwapIntervalTicks = 0;
		}
		if (positionSwapIntervalTicks == 0) {
			positionSwapRemainingTicks = 0;
		} else {
			positionSwapRemainingTicks = Math.max(0,
					Math.min(positionSwapIntervalTicks, positionSwapRemainingTicks));
		}
	}

	private static float sanitizeMaximum(float value, float fallback) {
		float safeFallback = Float.isFinite(fallback)
				? Math.max(1.0F, Math.min(1024.0F, fallback)) : 20.0F;
		return Float.isFinite(value) ? Math.max(1.0F, Math.min(1024.0F, value)) : safeFallback;
	}

	public void enablePositionSwap(int minutes) {
		if (minutes < PositionSwapLimits.MIN_MINUTES || minutes > PositionSwapLimits.MAX_MINUTES) {
			throw new IllegalArgumentException("위치 교환 주기는 1~120분이어야 합니다.");
		}
		positionSwapIntervalTicks = minutes * PositionSwapLimits.TICKS_PER_MINUTE;
		positionSwapRemainingTicks = positionSwapIntervalTicks;
	}

	public void disablePositionSwap() {
		positionSwapIntervalTicks = 0;
		positionSwapRemainingTicks = 0;
	}

	public boolean positionSwapEnabled() {
		return positionSwapIntervalTicks > 0;
	}

	public boolean advancePositionSwapTick(boolean enoughOnlineMembers) {
		if (!positionSwapEnabled()) {
			return false;
		}
		if (positionSwapRemainingTicks > 0) {
			positionSwapRemainingTicks--;
		}
		if (positionSwapRemainingTicks > 0) {
			return false;
		}
		if (!enoughOnlineMembers) {
			positionSwapRemainingTicks = PositionSwapLimits.RETRY_TICKS;
			return false;
		}
		positionSwapRemainingTicks = positionSwapIntervalTicks;
		return true;
	}

	public int positionSwapIntervalMinutes() {
		return positionSwapIntervalTicks / PositionSwapLimits.TICKS_PER_MINUTE;
	}

	public static final class PositionSwapLimits {
		public static final int MIN_MINUTES = 1;
		public static final int MAX_MINUTES = 120;
		public static final int TICKS_PER_MINUTE = 20 * 60;
		public static final int RETRY_TICKS = 20;
		private static final int MAX_INTERVAL_TICKS = MAX_MINUTES * TICKS_PER_MINUTE;

		private PositionSwapLimits() {
		}
	}

	public boolean hasSharedItems() {
		return mainItems.stream().anyMatch(stack -> !stack.isEmpty())
				|| extraItems.stream().anyMatch(stack -> !stack.isEmpty())
				|| !equipment.isEmpty()
				|| overflowItems.stream().anyMatch(stack -> !stack.isEmpty())
				|| !enderContainer.isEmpty();
	}

	public void restoreOverflow(boolean includeExtra) {
		for (var iterator = overflowItems.iterator(); iterator.hasNext();) {
			ItemStack stack = iterator.next();
			insertInto(mainItems, stack);
			if (includeExtra && !stack.isEmpty()) {
				insertInto(extraItems, stack);
			}
			if (stack.isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static void insertInto(SharedItemList items, ItemStack stack) {
		for (ItemStack existing : items) {
			if (stack.isEmpty()) {
				return;
			}
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				if (moved > 0) {
					existing.grow(moved);
					stack.shrink(moved);
				}
			}
		}
		for (int slot = 0; slot < items.size() && !stack.isEmpty(); slot++) {
			if (items.get(slot).isEmpty()) {
				int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
				items.set(slot, stack.copyWithCount(moved));
				stack.shrink(moved);
			}
		}
	}

	/**
	 * 증강 관련 저장 묶음.
	 *
	 * <p>{@link TeamState#CODEC}의 기존 필드가 이미 16개라 {@code RecordCodecBuilder.group}의 인자 상한
	 * (Products.P16)에 걸린다. 그래서 증강 필드 4개는 이 하위 Codec 하나로 묶어
	 * {@code "perks"} 한 항목으로 붙인다.
	 *
	 * <p>네 항목 모두 {@code optionalFieldOf}이고 묶음 자체도 선택 항목이라, 증강 필드가 없는
	 * 기존 월드는 {@link #EMPTY}로 읽힌다. 반대로 증강을 쓰지 않는 팀은 저장할 때 이 항목이
	 * 통째로 빠지므로 예전 서버 저장과 형태가 같다.
	 */
	public record PerkSection(boolean enabled, int lastMilestone,
			List<PerkStack> owned, List<PendingOffer> pending) {
		/** 증강을 쓰지 않는 상태. 기존 월드를 읽을 때의 기본값이다. */
		public static final PerkSection EMPTY = new PerkSection(false, 0, List.of(), List.of());

		public static final Codec<PerkSection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("enabled", false).forGetter(PerkSection::enabled),
				Codec.INT.optionalFieldOf("lastMilestone", 0).forGetter(PerkSection::lastMilestone),
				PerkStack.CODEC.listOf().optionalFieldOf("owned", List.of())
						.forGetter(PerkSection::owned),
				PendingOffer.CODEC.listOf().optionalFieldOf("pending", List.of())
						.forGetter(PerkSection::pending)
		).apply(instance, PerkSection::new));

		public PerkSection {
			owned = List.copyOf(owned);
			pending = List.copyOf(pending);
		}
	}

	/** 현재 증강 상태를 저장용 묶음으로 뽑아낸다. */
	public PerkSection perkSection() {
		return new PerkSection(perksEnabled, lastPerkMilestone, ownedPerks, pending);
	}

	/** 저장에서 읽은 증강 묶음을 이 상태에 채운다. */
	public void applyPerkSection(PerkSection section) {
		perksEnabled = section.enabled();
		lastPerkMilestone = section.lastMilestone();
		ownedPerks.clear();
		ownedPerks.addAll(section.owned());
		pending.clear();
		pending.addAll(section.pending());
		sanitizePerks();
	}

	/**
	 * 증강 필드를 안전한 범위로 되돌린다.
	 *
	 * <p>증강 시스템의 손상된 저장이 본 게임을 막으면 안 되므로 예외를 던지지 않고 조용히 고친다.
	 */
	public void sanitizePerks() {
		lastPerkMilestone = PerkMilestones.clampMilestone(lastPerkMilestone);
		ownedPerks.removeIf(stack -> stack == null || stack.perkId() == null || stack.perkId().isBlank());
		pending.removeIf(offer -> offer == null || offer.optionIds().isEmpty());
	}

	private static final MapCodec<TeamState> BASE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			SharedItemList.codec(MAIN_SIZE).fieldOf("mainItems").forGetter(state -> state.mainItems),
			SharedItemList.codec(EXTRA_SIZE).optionalFieldOf("extraItems")
					.forGetter(state -> Optional.of(state.extraItems)),
			ENDER_CODEC.fieldOf("enderItems").forGetter(state -> state.enderContainer),
			SharedEquipmentStore.CODEC.fieldOf("equipment").forGetter(state -> state.equipment),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("overflowItems")
					.forGetter(state -> Optional.of(state.overflowItems)),
			Codec.FLOAT.optionalFieldOf("maxHealth")
					.forGetter(state -> Optional.of(state.maxHealth)),
			Codec.FLOAT.fieldOf("health").forGetter(state -> state.health),
			Codec.FLOAT.optionalFieldOf("absorption", 0.0F).forGetter(state -> state.absorption),
			Codec.INT.fieldOf("foodLevel").forGetter(state -> state.foodLevel),
			Codec.FLOAT.fieldOf("saturation").forGetter(state -> state.saturation),
			Codec.INT.fieldOf("xpLevel").forGetter(state -> state.xpLevel),
			Codec.FLOAT.fieldOf("xpProgress").forGetter(state -> state.xpProgress),
			Codec.INT.fieldOf("totalExperience").forGetter(state -> state.totalExperience),
			MobEffectInstance.CODEC.listOf().optionalFieldOf("effects", List.of())
					.forGetter(state -> state.effects),
			Codec.INT.optionalFieldOf("positionSwapIntervalTicks", 0)
					.forGetter(state -> state.positionSwapIntervalTicks),
			Codec.INT.optionalFieldOf("positionSwapRemainingTicks", 0)
					.forGetter(state -> state.positionSwapRemainingTicks)
	).apply(instance, TeamState::fromCodec));

	/**
	 * 기존 16개 필드는 {@link #BASE_CODEC}이 그대로 같은 depth 에 펼쳐 쓰고, 그 옆에
	 * {@code "perks"} 한 항목만 덧붙인다. 저장 형태가 기존과 동일해 하위호환이 유지된다.
	 */
	public static final Codec<TeamState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BASE_CODEC.<TeamState>forGetter(state -> state),
			PerkSection.CODEC.optionalFieldOf("perks", PerkSection.EMPTY)
					.<TeamState>forGetter(TeamState::perkSection)
	).apply(instance, TeamState::withPerkSection));

	private static TeamState withPerkSection(TeamState state, PerkSection perks) {
		state.applyPerkSection(perks);
		return state;
	}

	private static TeamState fromCodec(SharedItemList mainItems, Optional<SharedItemList> extraItems,
			PlayerEnderChestContainer enderContainer, SharedEquipmentStore equipment,
			Optional<List<ItemStack>> overflowItems,
			Optional<Float> maxHealth, float health, float absorption, int foodLevel, float saturation,
			int xpLevel, float xpProgress, int totalExperience, List<MobEffectInstance> effects,
			int positionSwapIntervalTicks, int positionSwapRemainingTicks) {
		float fallbackMaximum = com.sharedfate.SharedFateMod.config == null
				? 20.0F : (float) com.sharedfate.SharedFateMod.config.sharedMaxHealth;
		TeamState state = new TeamState(mainItems,
				extraItems.orElseGet(() -> SharedItemList.ofSize(EXTRA_SIZE)),
				enderContainer, equipment, overflowItems.orElseGet(List::of),
				maxHealth.orElse(fallbackMaximum), health, absorption, foodLevel, saturation,
				xpLevel, xpProgress, totalExperience, effects,
				positionSwapIntervalTicks, positionSwapRemainingTicks);
		state.sanitize(fallbackMaximum);
		return state;
	}
}
