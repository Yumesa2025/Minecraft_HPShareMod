package com.sharedfate.team;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sharedfate.perk.PendingOffer;
import com.sharedfate.perk.PerkMilestones;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class TeamState {
	public static final int MAIN_SIZE = 36;
	public static final int EXTRA_SIZE = 27;
	public static final int ENDER_SIZE = 27;

	public final SharedItemList mainItems;
	public final SharedItemList extraItems;
	public final PlayerEnderChestContainer enderContainer;
	public final SharedEquipmentStore equipment;
	public final List<ItemStack> overflowItems;

	/**
	 * 지금 실제로 걸려 있는 팀 공유 최대 체력.
	 *
	 * <p>공유 체력 풀의 상한이자 {@code MaxHealthAttribute} 가 팀원 전원의 {@code max_health}
	 * 속성을 맞추는 목표값이다. <b>증강 보너스가 이미 더해진 결과</b>라 직접 정하는 값이 아니다.
	 * 정하는 값은 {@link #baseMaxHealth} 쪽이고, 둘을 이어 주는 계산은
	 * {@link com.sharedfate.perk.PerkHealthRules#effectiveMaxHealth} 한 곳에만 있다.
	 */
	public float maxHealth;
	/**
	 * 팀이 정한 기본 최대 체력. {@code /shareteam health} 가 정하고 그 밖에는 아무도 바꾸지 않는다.
	 *
	 * <p>{@link #maxHealth} 와 나눠 둔 이유는 하나뿐이다. 최대 체력을 올리는 증강의 보너스를
	 * {@link #maxHealth} 에 직접 더하면 접속·부활·주기 점검마다 또 더해져 끝없이 불어난다.
	 * "원래 얼마였는가"를 여기에 남겨 두면 몇 번을 다시 계산해도 답이 {@code 기본값 + 보너스} 로
	 * 같고, 증강을 잃었을 때 명령으로 정해 둔 값이 그대로 돌아온다.
	 *
	 * <p>기존 월드에는 이 항목이 없다. 그때는 저장된 {@link #maxHealth} 가 곧 팀이 정한 값이므로
	 * 생성자가 둘을 같게 맞춰 두고, 저장에 값이 있을 때만 덮어쓴다.
	 */
	public float baseMaxHealth;
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

	public boolean perksEnabled;
	/** 피격 알림 표시 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸는 경로가 없다. */
	public boolean damageAlertEnabled;
	/** 사망 알림 표시 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸는 경로가 없다. */
	public boolean deathAlertEnabled;
	/** 마지막으로 처리한 레벨 구간 (0, 3, 6, …, 36). */
	public int lastPerkMilestone;
	/** 팀이 보유한 증강의 id. 중첩이 없으므로 같은 id 가 두 번 들어가지 않는다. */
	public final List<String> ownedPerks = new ArrayList<>();
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
		// 증강 보너스가 붙기 전이므로 기본값은 지금 상한과 같다. 저장에서 읽어 온 경우에만
		// TeamState.CODEC 이 뒤에서 따로 덮어쓴다.
		this.baseMaxHealth = maxHealth;
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

	/**
	 * 전멸 뒤 팀 상태를 되돌린다.
	 *
	 * <p>{@link #baseMaxHealth} 는 일부러 손대지 않는다. 여기서는 보유 증강이 그대로 남아 있어
	 * 상한도 그대로여야 하고, 회차 자체가 끝나 증강을 잃는 경로는 {@link #fresh} 로 팀 상태를
	 * 통째로 새로 만들기 때문에 이 자리를 지나지 않는다.
	 */
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
		// 기본값이 비어 있거나 망가졌으면 지금 상한을 그대로 쓴다. 증강이 없는 팀에서는 둘이
		// 어차피 같은 값이고, 기존 월드를 열 때도 이 길로 자연스럽게 채워진다.
		this.baseMaxHealth = sanitizeMaximum(this.baseMaxHealth, safeMaximum);
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
			List<String> owned, List<PendingOffer> pending) {
		/** 증강을 쓰지 않는 상태. 기존 월드를 읽을 때의 기본값이다. */
		public static final PerkSection EMPTY = new PerkSection(false, 0, List.of(), List.of());

		/**
		 * 보유 증강 하나의 저장 형식.
		 *
		 * <p>중첩 개념이 있던 시절에는 {@code {perkId, count}} 객체로 적었다. 이미 돌아가는
		 * 서버의 월드에 그 형태가 들어 있으므로 <b>읽을 때는 두 형태를 모두 받아들인다.</b>
		 * {@code count} 는 뜻이 사라졌으므로 읽고 버린다. 새로 저장할 때는 id 문자열만 적는다.
		 */
		private static final Codec<String> OWNED_CODEC = Codec.either(
						Codec.STRING, Codec.STRING.fieldOf("perkId").codec())
				.xmap(either -> either.map(Function.identity(), Function.identity()), Either::left);

		public static final Codec<PerkSection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("enabled", false).forGetter(PerkSection::enabled),
				Codec.INT.optionalFieldOf("lastMilestone", 0).forGetter(PerkSection::lastMilestone),
				OWNED_CODEC.listOf().optionalFieldOf("owned", List.of())
						.forGetter(PerkSection::owned),
				PendingOffer.CODEC.listOf().optionalFieldOf("pending", List.of())
						.forGetter(PerkSection::pending)
		).apply(instance, PerkSection::new));

		public PerkSection {
			owned = List.copyOf(owned);
			pending = List.copyOf(pending);
		}
	}

	/**
	 * 알림 설정 저장 묶음.
	 *
	 * <p>{@link PerkSection} 과 같은 이유로 따로 묶는다. {@code TeamState.CODEC} 의 본체는
	 * {@code BASE_CODEC} 이 이미 {@code RecordCodecBuilder.group} 인자 상한(P16)을 다 쓰고
	 * 있어, 새 항목은 바깥쪽에 묶음으로만 붙일 수 있다.
	 *
	 * <p>두 항목 모두 {@code optionalFieldOf} 이고 묶음 자체도 선택 항목이라, 이 항목이 없는
	 * 기존 월드는 {@link #NONE} — 둘 다 꺼짐 — 으로 읽힌다. 알림을 쓰지 않는 팀은 저장할 때
	 * 이 묶음이 통째로 빠지므로 저장 형태가 예전과 같다.
	 */
	public record AlertSection(boolean damage, boolean death) {
		/** 둘 다 꺼진 상태. 기본값이자 기존 월드를 읽을 때의 값이다. */
		public static final AlertSection NONE = new AlertSection(false, false);

		public static final Codec<AlertSection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("damage", false).forGetter(AlertSection::damage),
				Codec.BOOL.optionalFieldOf("death", false).forGetter(AlertSection::death)
		).apply(instance, AlertSection::new));
	}

	/** 현재 알림 설정을 저장용 묶음으로 뽑아낸다. */
	public AlertSection alertSection() {
		return new AlertSection(damageAlertEnabled, deathAlertEnabled);
	}

	/** 저장에서 읽은 알림 묶음을 이 상태에 채운다. */
	public void applyAlertSection(AlertSection section) {
		damageAlertEnabled = section.damage();
		deathAlertEnabled = section.death();
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
		// 중첩이 없으므로 같은 증강이 두 번 들어 있으면 안 된다. 중첩 시절 저장이나 손상된
		// 저장에서 흘러들어와도 여기서 한 개로 접어 둔다.
		Set<String> seen = new HashSet<>();
		ownedPerks.removeIf(perkId -> perkId == null || perkId.isBlank() || !seen.add(perkId));
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
	 * {@code "perks"} 와 {@code "baseMaxHealth"} 두 항목만 덧붙인다. 둘 다 선택 항목이라
	 * 저장 형태가 기존과 호환되고, 이 항목들을 모르는 예전 서버도 나머지를 그대로 읽는다.
	 *
	 * <p>{@code "baseMaxHealth"} 를 {@link #BASE_CODEC} 안에 넣지 않은 이유는
	 * {@link PerkSection} 과 같다. {@code RecordCodecBuilder.group} 의 인자 상한(P16)이 이미
	 * 다 찼다. {@code "alerts"} 도 마찬가지다.
	 */
	public static final Codec<TeamState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BASE_CODEC.<TeamState>forGetter(state -> state),
			PerkSection.CODEC.optionalFieldOf("perks", PerkSection.EMPTY)
					.<TeamState>forGetter(TeamState::perkSection),
			Codec.FLOAT.optionalFieldOf("baseMaxHealth")
					.<TeamState>forGetter(TeamState::storedBaseMaxHealth),
			AlertSection.CODEC.optionalFieldOf("alerts", AlertSection.NONE)
					.<TeamState>forGetter(TeamState::alertSection)
	).apply(instance, TeamState::withStoredSections));

	/**
	 * 저장에 남길 기본 최대 체력. 지금 상한과 같으면 아예 적지 않는다.
	 *
	 * <p>증강 보너스도 고정도 없는 팀에서는 둘이 언제나 같은 값이라, 그런 팀의 저장 형태는
	 * 이 필드가 생기기 전과 <b>비트 하나도 다르지 않다.</b> 읽을 때도 항목이 없으면 저장된
	 * {@code maxHealth} 를 기본값으로 삼으므로 결과가 같다.
	 */
	private Optional<Float> storedBaseMaxHealth() {
		return baseMaxHealth == maxHealth ? Optional.empty() : Optional.of(baseMaxHealth);
	}

	/**
	 * 뒤에 붙인 선택 항목들을 채운다.
	 *
	 * <p>{@code baseMaxHealth} 가 없는 <b>기존 월드</b>에서는 생성자가 맞춰 둔 값
	 * (= 저장된 {@code maxHealth})이 그대로 남는다. 그 월드에서 팀이 정한 값은 실제로
	 * {@code maxHealth} 였으므로 이게 정확한 복원이다.
	 */
	private static TeamState withStoredSections(TeamState state, PerkSection perks,
			Optional<Float> baseMaxHealth, AlertSection alerts) {
		state.applyPerkSection(perks);
		state.applyAlertSection(alerts);
		baseMaxHealth.ifPresent(value -> state.baseMaxHealth = sanitizeMaximum(value, state.maxHealth));
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
