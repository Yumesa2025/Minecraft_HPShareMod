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

	/** 증강 사용 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸는 경로가 없다. */
	public boolean perksEnabled;
	/** 피격 알림 표시 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸는 경로가 없다. */
	public boolean damageAlertEnabled;
	/** 사망 알림 표시 여부. 팀을 만들 때 정하고 그 뒤로는 바꾸는 경로가 없다. */
	public boolean deathAlertEnabled;
	/**
	 * 시간이 흐를수록 적대적 몹이 강해지는가. 팀을 만들 때 정하고 그 뒤로는 바꾸는 경로가 없다.
	 * 실제 계산은 {@code com.sharedfate.sync.DifficultyEscalation} 이 한다.
	 */
	public boolean difficultyEscalationEnabled;
	/**
	 * 이 회차에서 <b>팀원이 한 명이라도 접속해 있던</b> 시간(틱). 난이도 상승 단계를 이걸로 센다.
	 *
	 * <p>월드 저장에 들어가므로 서버를 껐다 켜도 이어지고, <b>서버가 꺼져 있던 시간은 세지
	 * 않는다.</b> 아무도 접속하지 않은 시간도 마찬가지다. 회차가 넘어가면 팀 상태를 새로 만들어
	 * 0 에서 다시 시작한다 — 「회차가 시작된 뒤 흐른 시간」이 이 값의 뜻이다.
	 */
	public int difficultyElapsedTicks;
	/**
	 * 이 팀의 회차가 <b>실제로 시작되었는가.</b> 리더가 「게임 시작」을 누르면 참이 된다.
	 *
	 * <p>회차의 시작점은 팀을 만든 순간이 아니라 <b>이것이 참이 되는 순간</b>이다. 팀을 만들고
	 * 팀원을 부르고 설정을 확인하는 동안에도 게임은 돌아가는데, 그 시간까지 회차에 넣으면
	 * 난이도 상승도 위치 교환도 증강 구간도 아무도 시작하지 않은 회차에서 굴러간다.
	 * 시작 전에 멈춰 있는 것들은 {@code com.sharedfate.sync.GameStartManager} 에 모아 뒀다.
	 *
	 * <p><b>저장에 이 항목이 없으면 「이미 시작했다」로 읽는다.</b> 이 기능이 생기기 전의 월드에는
	 * 항목이 없는데, 그 월드의 팀은 실제로 회차를 진행하던 중이다. 거짓으로 읽으면 이미 몇 시간을
	 * 플레이한 팀이 갑자기 「시작 대기」가 되고, 그 상태에서 「게임 시작」을 누르면 아이템이 전부
	 * 사라진다. 반대로 새로 만든 팀은 {@link #fresh} 가 거짓으로 두고 저장에도 그대로 적히므로
	 * 이 기본값에 걸리지 않는다.
	 */
	public boolean runStarted;
	/**
	 * 이 회차에서 증강 시험 명령({@code /shareteam perktest ...})을 한 번이라도 썼는가.
	 *
	 * <p>한 번 켜지면 이 회차가 끝날 때까지 <b>다시 꺼지지 않는다.</b> 증강을 손으로 넣고 뺀
	 * 회차는 정상 회차가 아니고, 그 사실이 나중에 「어떻게 이겼더라」를 따질 때 남아 있어야
	 * 한다. 회차가 넘어가면 팀 상태를 새로 만들면서 저절로 지워진다.
	 */
	public boolean perkTestUsed;
	/**
	 * 이 팀이 <b>한 회차에</b> 쓸 수 있는 증강 다시 뽑기 횟수. 팀을 만들 때 정하고 그 뒤로는
	 * 바꾸는 경로가 없다.
	 *
	 * <p>{@link #rerollsRemaining} 과 나눠 둔 이유는 {@link #baseMaxHealth} 와 {@link #maxHealth}
	 * 를 나눈 것과 같다. "원래 몇 번이었는가"가 남아 있어야 회차가 넘어갈 때 그 값으로 다시
	 * 채울 수 있다. 회차를 넘겨 이어 가는 일은 {@code TeamRosterStore} 와
	 * {@link TeamManager#restoreFreshRoster} 가 맡는다.
	 */
	public int rerollAllowance;
	/**
	 * <b>이번 회차에</b> 아직 남은 다시 뽑기 횟수. 0 이면 더 못 쓴다.
	 *
	 * <p>회차마다 다시 차는 값이라 월드 저장에만 들어가고 팀 명단 파일에는 들어가지 않는다.
	 * 전멸로 팀 상태를 새로 만들면 {@link #rerollAllowance} 로 가득 찬 채 시작한다.
	 */
	public int rerollsRemaining;
	/** 마지막으로 처리한 레벨 구간 (0, 3, 6, …, 36). */
	public int lastPerkMilestone;
	/** 팀이 보유한 증강의 id. 중첩이 없으므로 같은 id 가 두 번 들어가지 않는다. */
	public final List<String> ownedPerks = new ArrayList<>();
	/** 아직 고르지 않은 선택권. 구간 순서대로 쌓이며 여러 개일 수 있다. */
	public final List<PendingOffer> pending = new ArrayList<>();
	/**
	 * 「유산」처럼 증강을 고른 순간 몰수했다가, 팀이 전멸하면 다음 회차 시작 인벤토리로
	 * 돌려주기로 예약된 아이템들. {@link #resetAfterDeath}에서도 일부러 비우지 않는다 —
	 * 전멸을 넘겨야 뜻이 있는 값이고, 실제로 넘기는 일은 {@code TeamRosterStore}와
	 * {@code TeamManager#restoreFreshRoster}가 맡는다.
	 */
	public final List<ItemStack> legacyGear = new ArrayList<>();

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
		// 다시 뽑기는 팀을 만들 때 정하지만, 그 길을 거치지 않고 만들어진 상태(기존 월드·시험)도
		// 기본값으로 굴러가야 한다. 저장에서 읽어 온 경우에만 CODEC 이 뒤에서 덮어쓴다.
		this.rerollAllowance = TeamCreationSettings.DEFAULT_REROLL_COUNT;
		this.rerollsRemaining = TeamCreationSettings.DEFAULT_REROLL_COUNT;
		// 새로 만들어지는 팀 상태는 언제나 「시작 대기」다. 팀을 만드는 것도, 전멸 뒤 새 월드에
		// 명단을 되살리는 것도 이 생성자를 지나므로 매 회차 「게임 시작」을 눌러야 한다.
		// 저장에서 읽어 온 경우에만 CODEC 이 뒤에서 덮어쓴다.
		this.runStarted = false;
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
	 *
	 * <p>{@link #runStarted} 는 <b>반드시 내린다.</b> 전멸은 회차의 끝이므로 다음 회차는 다시
	 * 「게임 시작」에서 시작해야 한다. 월드를 초기화하는 서버에서는 어차피 팀 상태가 통째로 새로
	 * 만들어지지만, 월드 초기화를 끈 서버({@code resetWorldOnTeamDeath=false})에서는 이 자리가
	 * 회차를 대기 상태로 되돌리는 유일한 길이다.
	 */
	public void resetAfterDeath(float maxHealth, boolean keepExperience) {
		runStarted = false;
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
		legacyGear.removeIf(ItemStack::isEmpty);
		// 상한은 DifficultyEscalation 이 자기 계산에서 다시 자른다. 여기서는 음수만 막는다.
		difficultyElapsedTicks = Math.max(0, difficultyElapsedTicks);
		// 이번 회차에 남은 횟수가 회차당 허용치보다 클 수는 없다. 손상된 저장을 여기서 접는다.
		rerollAllowance = TeamCreationSettings.sanitizeRerollCount(rerollAllowance);
		rerollsRemaining = Math.max(0, Math.min(rerollAllowance, rerollsRemaining));
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

	/**
	 * 난이도 상승 저장 묶음.
	 *
	 * <p>{@link AlertSection} 과 같은 이유로 따로 묶는다 — {@code BASE_CODEC} 이 이미
	 * {@code RecordCodecBuilder.group} 인자 상한(P16)을 다 써서 새 항목은 바깥쪽에만 붙는다.
	 *
	 * <p>두 항목 모두 {@code optionalFieldOf} 이고 묶음 자체도 선택 항목이라, 이 기능을 끈
	 * 팀은 저장할 때 묶음이 통째로 빠져 예전과 형태가 같고, 이 항목이 없는 기존 월드는
	 * {@link #OFF} 로 읽힌다.
	 *
	 * @param escalation   시간이 흐를수록 몹이 강해지는가
	 * @param elapsedTicks 이 회차에서 팀원이 접속해 있던 시간(틱)
	 */
	public record DifficultySection(boolean escalation, int elapsedTicks) {
		/** 꺼져 있고 아직 아무 시간도 세지 않은 상태. 기존 월드를 읽을 때의 값이다. */
		public static final DifficultySection OFF = new DifficultySection(false, 0);

		public static final Codec<DifficultySection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("escalation", false).forGetter(DifficultySection::escalation),
				Codec.INT.optionalFieldOf("elapsedTicks", 0).forGetter(DifficultySection::elapsedTicks)
		).apply(instance, DifficultySection::new));
	}

	/**
	 * 증강 다시 뽑기 저장 묶음.
	 *
	 * <p>{@link DifficultySection} 과 같은 이유로 바깥쪽에 따로 붙인다. 다만 기본값이 「꺼짐」이
	 * 아니라 {@linkplain TeamCreationSettings#DEFAULT_REROLL_COUNT 회차당 3회}라는 점이 다르다.
	 * 기본값 그대로인 팀은 저장할 때 이 묶음이 통째로 빠지고, 이 항목이 없는 기존 월드도
	 * {@link #DEFAULT} — 3회 전부 남아 있는 상태 — 로 읽힌다.
	 *
	 * @param allowance 이 팀이 회차당 쓸 수 있는 횟수
	 * @param remaining 이번 회차에 아직 남은 횟수
	 */
	public record RerollSection(int allowance, int remaining) {
		/** 팀이 아무것도 안 정했을 때의 값. 기존 월드를 읽을 때도 이 값이다. */
		public static final RerollSection DEFAULT = new RerollSection(
				TeamCreationSettings.DEFAULT_REROLL_COUNT,
				TeamCreationSettings.DEFAULT_REROLL_COUNT);

		public static final Codec<RerollSection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("allowance", TeamCreationSettings.DEFAULT_REROLL_COUNT)
						.forGetter(RerollSection::allowance),
				Codec.INT.optionalFieldOf("remaining", TeamCreationSettings.DEFAULT_REROLL_COUNT)
						.forGetter(RerollSection::remaining)
		).apply(instance, RerollSection::new));
	}

	/** 현재 다시 뽑기 상태를 저장용 묶음으로 뽑아낸다. */
	public RerollSection rerollSection() {
		return new RerollSection(rerollAllowance, rerollsRemaining);
	}

	/** 저장에서 읽은 다시 뽑기 묶음을 이 상태에 채운다. */
	public void applyRerollSection(RerollSection section) {
		rerollAllowance = TeamCreationSettings.sanitizeRerollCount(section.allowance());
		rerollsRemaining = Math.max(0, Math.min(rerollAllowance, section.remaining()));
	}

	/** 현재 난이도 상승 상태를 저장용 묶음으로 뽑아낸다. */
	public DifficultySection difficultySection() {
		return new DifficultySection(difficultyEscalationEnabled, difficultyElapsedTicks);
	}

	/** 저장에서 읽은 난이도 묶음을 이 상태에 채운다. */
	public void applyDifficultySection(DifficultySection section) {
		difficultyEscalationEnabled = section.escalation();
		difficultyElapsedTicks = Math.max(0, section.elapsedTicks());
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
					.<TeamState>forGetter(TeamState::alertSection),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("legacyGear", List.of())
					.<TeamState>forGetter(state -> List.copyOf(state.legacyGear)),
			DifficultySection.CODEC.optionalFieldOf("difficulty", DifficultySection.OFF)
					.<TeamState>forGetter(TeamState::difficultySection),
			Codec.BOOL.optionalFieldOf("perkTestUsed", false)
					.<TeamState>forGetter(state -> state.perkTestUsed),
			RerollSection.CODEC.optionalFieldOf("reroll", RerollSection.DEFAULT)
					.<TeamState>forGetter(TeamState::rerollSection),
			// 기본값이 참인 유일한 항목이다. 까닭은 runStarted 필드 문서에 적어 뒀다 —
			// 이 항목이 없는 예전 월드의 팀은 실제로 진행 중이던 팀이다.
			Codec.BOOL.optionalFieldOf("runStarted", true)
					.<TeamState>forGetter(state -> state.runStarted)
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
			Optional<Float> baseMaxHealth, AlertSection alerts, List<ItemStack> legacyGear,
			DifficultySection difficulty, boolean perkTestUsed, RerollSection reroll,
			boolean runStarted) {
		state.runStarted = runStarted;
		state.applyPerkSection(perks);
		state.applyAlertSection(alerts);
		state.applyDifficultySection(difficulty);
		state.applyRerollSection(reroll);
		state.perkTestUsed = perkTestUsed;
		baseMaxHealth.ifPresent(value -> state.baseMaxHealth = sanitizeMaximum(value, state.maxHealth));
		state.legacyGear.clear();
		legacyGear.stream().filter(stack -> !stack.isEmpty()).forEach(state.legacyGear::add);
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
