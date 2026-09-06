package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.effect.OreExchangeEffect;
import com.sharedfate.team.SharedItemList;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code ore_exchange} 증강(실버 「나무꾼의 욕심」)의 집행부.
 *
 * <p>나무 도끼를 주 손에 들고 우클릭(빈 허공)하면, 팀 공유 인벤토리에서 종류를 가리지 않고
 * 나무 {@value com.sharedfate.perk.effect.OreExchangeEffect#WOOD_COST}개를 소모하고 무작위 광물을
 * 준다. 대개 1개지만 2%로 「잭팟」이 터져 다이아몬드 두 스택이 나온다. <b>상태이상 같은 부작용은
 * 걸지 않는다.</b>
 *
 * <h2>등록 지점</h2>
 * <p>{@code UseItemCallback.EVENT}에 붙는다. 이 사건은 블록이 아니라 <b>허공</b>을 향해
 * 우클릭했을 때만 발화한다({@code UseBlockCallback}과는 다른 자리다). 나무 도끼는 바닐라에서
 * 허공 우클릭에 아무 동작이 없으므로 이 갈래를 가로채도 다른 동작과 부딪히지 않는다.
 *
 * <h2>나무를 세고 빼는 곳</h2>
 * <p>공유 인벤토리({@link TeamState#mainItems}, 확장이 켜져 있으면
 * {@link TeamState#extraItems}까지)를 그대로 훑는다. 개인 인벤토리가 아니라 이 목록이 곧 팀
 * 전원의 인벤토리이기 때문이다. 아이템을 직접 옮기는
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
		OreExchangeEffect.Result result = OreExchangeEffect.rollResult(breaker.getRandom());
		Component resultName = grant(state, result);
		refreshScreen(breaker);

		breaker.sendSystemMessage(exchangeMessage(cost, result, resultName));
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

	/**
	 * 뽑힌 결과를 공유 인벤토리에 넣는다.
	 *
	 * <p>넘침 목록에 얹고 {@link TeamState#restoreOverflow} 를 부른다. 자리가 없으면 바닥에
	 * 떨어뜨리지 않고 넘침 목록에 남아, 칸이 비는 대로 저절로 들어온다.
	 *
	 * <p><b>이름은 넣기 <em>전에</em> 확보한다.</b> {@code restoreOverflow} 는 묶음을 새로 만들지
	 * 않고 제자리에서 개수를 깎으므로, 다 들어가고 나면 우리가 넘긴 그 {@link ItemStack} 의 개수가
	 * 0이 된다. 개수가 0인 묶음은 {@code getItem()} 이 {@code AIR} 를 돌려주도록 되어 있어
	 * {@code getHoverName()} 이 「Air」가 된다.
	 *
	 * <p>테스트가 직접 부른다.
	 *
	 * @return 넣은 아이템의 이름. 아이템을 찾지 못했으면 null
	 */
	static @Nullable Component grant(TeamState state, OreExchangeEffect.Result result) {
		Item item = BuiltInRegistries.ITEM.get(result.itemId()).map(reference -> reference.value()).orElse(null);
		if (item == null || item == Items.AIR) {
			SharedFateMod.LOGGER.warn("나무꾼의 욕심이 주려는 광물을 찾을 수 없습니다: {}", result.itemId());
			return null;
		}
		List<ItemStack> stacks = splitIntoStacks(item, result.count());
		Component name = stacks.getFirst().getHoverName();

		state.overflowItems.addAll(stacks);
		state.restoreOverflow(ExpandedInventoryManager.enabled());
		state.overflowItems.removeIf(ItemStack::isEmpty);
		return name;
	}

	/**
	 * {@code count} 개를 스택 한도에 맞춰 여러 묶음으로 나눈다.
	 *
	 * <p>「잭팟」은 다이아몬드 {@value com.sharedfate.perk.effect.OreExchangeEffect#JACKPOT_COUNT}개라
	 * 한 묶음에 담기지 않는다. {@code restoreOverflow} 는 빈 칸에 넣을 때 알아서 한도만큼만 떼어
	 * 가므로 인벤토리에 자리만 있으면 한 묶음으로 얹어도 잘 들어간다. 문제는 자리가 없을 때다 —
	 * 한도를 넘긴 묶음이 넘침 목록에 그대로 남고, 그 목록은 {@code ItemStack} 코덱으로 저장되는데
	 * 이 코덱은 한도를 넘는 개수를 오류로 되돌린다. 그러면 서버를 껐다 켜는 순간 잭팟이 통째로
	 * 사라진다. 그래서 얹기 전에 미리 나눈다.
	 *
	 * <p>한도는 아이템에서 직접 읽는다. 64로 못박아 두면 나중에 한도가 다른 아이템을 보상표에
	 * 넣었을 때 같은 함정을 다시 밟게 된다.
	 */
	private static List<ItemStack> splitIntoStacks(Item item, int count) {
		List<ItemStack> stacks = new ArrayList<>();
		int limit = Math.max(1, new ItemStack(item, 1).getMaxStackSize());
		int remaining = Math.max(1, count);
		while (remaining > 0) {
			int piece = Math.min(remaining, limit);
			stacks.add(new ItemStack(item, piece));
			remaining -= piece;
		}
		return stacks;
	}

	// ------------------------------------------------------------------ 알림

	/**
	 * 교환 결과를 알리는 채팅 문구.
	 *
	 * <p>아이템 이름은 {@link Component} 그대로 이어 붙인다 — 번역 키를 살려 두어야 플레이어의
	 * 언어 설정대로 「다이아몬드」로 보인다. 문자열로 풀어 버리면 <b>서버</b>의 언어로 굳어
	 * 「Diamond」가 나온다.
	 *
	 * <p>개수가 1개일 때는 예전 문구를 그대로 쓴다(「… 원석 구리(으)로 바꿨습니다.」). 여럿일
	 * 때만 개수를 붙여 「… 다이아몬드 128개로 바꿨습니다.」가 된다 — 개수가 붙으면 받침이 언제나
	 * 「개」로 끝나므로 「(으)로」를 쓸 일이 없다.
	 *
	 * <p>잭팟은 앞에 금색 굵은 표시를 단다.
	 */
	static Component exchangeMessage(int cost, OreExchangeEffect.Result result,
			@Nullable Component itemName) {
		MutableComponent message = Component.literal("[증강] ");
		if (OreExchangeEffect.isJackpot(result)) {
			message.append(Component.literal("★잭팟★ ")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
		message.append(Component.literal("나무 " + cost + "개를 "))
				.append(itemName == null ? Component.literal("광물") : itemName);
		return result.count() > 1
				? message.append(Component.literal(" " + result.count() + "개로 바꿨습니다."))
				: message.append(Component.literal("(으)로 바꿨습니다."));
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
