package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.OreExchangeEffect;
import com.sharedfate.team.SharedItemList;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@code ore_exchange} 증강(실버 「나무꾼의 욕심」)의 집행부.
 *
 * <p>나무 도끼를 주 손에 들고 우클릭(빈 허공)하면, 팀 공유 인벤토리에서 종류를 가리지 않고
 * 나무 {@value com.sharedfate.perk.effect.OreExchangeEffect#WOOD_COST}개를 소모하고 무작위 광물
 * 하나를 준다. 그 대가로 쓴 사람에게 허기 V·독 I 을 10초간 건다.
 *
 * <h2>등록 지점</h2>
 * <p>{@code UseItemCallback.EVENT}에 붙는다. 이 사건은 블록이 아니라 <b>허공</b>을 향해
 * 우클릭했을 때만 발화한다({@code UseBlockCallback}과는 다른 자리다). 나무 도끼는 바닐라에서
 * 허공 우클릭에 아무 동작이 없으므로 이 갈래를 가로채도 다른 동작과 부딪히지 않는다.
 *
 * <h2>나무를 세고 빼는 곳</h2>
 * <p>공유 인벤토리({@link TeamState#mainItems}, 확장이 켜져 있으면
 * {@link TeamState#extraItems}까지)를 그대로 훑는다. 개인 인벤토리가 아니라 이 목록이 곧 팀
 * 전원의 인벤토리이기 때문이다({@link PerkBlockBreaks}의 문서와 같은 원칙). 아이템을 직접 옮기는
 * 자리라 {@code TeamManager.markDirtyIfActive}가 매 틱 저장을 표시해 주므로 여기서 따로
 * {@code setDirty}를 부르지 않아도 된다.
 */
public final class PerkOreExchange {
	/** "나무"로 칠 아이템 태그. {@code #minecraft:logs} — 원목·나무·벗긴 것·네더 줄기까지. */
	private static final TagKey<Item> WOOD_TAG =
			TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("logs"));

	private static volatile boolean warned;

	private PerkOreExchange() {
	}

	/** {@code UseItemCallback.EVENT}에 붙는 지점. */
	public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		try {
			return handle(player, level, hand);
		} catch (RuntimeException error) {
			warnOnce(error);
			return InteractionResult.PASS;
		}
	}

	private static InteractionResult handle(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || level == null || level.isClientSide()
				|| !(player instanceof ServerPlayer breaker)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!matchesTool(held)) {
			return InteractionResult.PASS;
		}

		TeamState state = TeamLookup.stateOf(breaker.getUUID());
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty() || !hasOreExchange(state)) {
			return InteractionResult.PASS;
		}

		int cost = OreExchangeEffect.WOOD_COST;
		int available = countWood(state);
		if (available < cost) {
			breaker.sendSystemMessage(Component.literal(
					"[증강] 나무가 부족합니다 (" + available + "/" + cost + ")."));
			return InteractionResult.FAIL;
		}

		deductWood(state, cost);
		Identifier resultId = OreExchangeEffect.rollResult(breaker.getRandom());
		String resultName = grant(state, resultId);
		applyPenalty(breaker);
		refreshScreen(breaker);

		breaker.sendSystemMessage(Component.literal(
				"[증강] 나무 " + cost + "개를 " + (resultName == null ? "광물" : resultName) + "(으)로 바꿨습니다."));
		return InteractionResult.SUCCESS;
	}

	/** 나무 도끼(정확히 그 아이템)를 들었는가. 테스트가 직접 부른다. */
	static boolean matchesTool(@Nullable ItemStack held) {
		if (held == null || held.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
		return OreExchangeEffect.TOOL.equals(id);
	}

	private static boolean hasOreExchange(TeamState state) {
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof OreExchangeEffect) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ 나무 세고 빼기

	/** 공유 인벤토리에 있는 "나무"의 총 개수. */
	static int countWood(TeamState state) {
		int total = countWood(state.mainItems);
		if (ExpandedInventoryManager.enabled()) {
			total += countWood(state.extraItems);
		}
		return total;
	}

	private static int countWood(SharedItemList items) {
		int total = 0;
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty() && stack.is(WOOD_TAG)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** 정확히 {@code amount}개를 뺀다. 호출 전에 {@link #countWood}로 충분함을 확인해야 한다. */
	static void deductWood(TeamState state, int amount) {
		int remaining = deductWood(state.mainItems, amount);
		if (remaining > 0 && ExpandedInventoryManager.enabled()) {
			deductWood(state.extraItems, remaining);
		}
	}

	private static int deductWood(SharedItemList items, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < items.size() && remaining > 0; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty() || !stack.is(WOOD_TAG)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			if (stack.isEmpty()) {
				items.set(slot, ItemStack.EMPTY);
			}
			remaining -= take;
		}
		return remaining;
	}

	// ------------------------------------------------------------------ 지급과 대가

	/** 광물 하나를 공유 인벤토리에 넣는다. 넣은 아이템의 이름(찾지 못했으면 null). */
	private static @Nullable String grant(TeamState state, Identifier itemId) {
		Item item = BuiltInRegistries.ITEM.get(itemId).map(reference -> reference.value()).orElse(null);
		if (item == null || item == Items.AIR) {
			SharedFateMod.LOGGER.warn("나무꾼의 욕심이 주려는 광물을 찾을 수 없습니다: {}", itemId);
			return null;
		}
		ItemStack stack = new ItemStack(item, 1);
		state.overflowItems.add(stack);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		return stack.getHoverName().getString();
	}

	/** 대가: 허기 V · 독 I, 10초. */
	private static void applyPenalty(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, OreExchangeEffect.PENALTY_TICKS,
				OreExchangeEffect.HUNGER_AMPLIFIER, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.POISON, OreExchangeEffect.PENALTY_TICKS,
				OreExchangeEffect.POISON_AMPLIFIER, false, true, true));
	}

	private static void refreshScreen(ServerPlayer player) {
		if (player.containerMenu != null) {
			player.containerMenu.broadcastChanges();
		}
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"나무꾼의 욕심 처리에 실패했습니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		warned = false;
	}
}
