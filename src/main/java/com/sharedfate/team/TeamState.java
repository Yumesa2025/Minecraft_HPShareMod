package com.sharedfate.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

	public static final Codec<TeamState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
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
