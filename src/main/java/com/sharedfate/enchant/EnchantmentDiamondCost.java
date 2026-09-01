package com.sharedfate.enchant;

import com.sharedfate.inventory.ExpandedInventoryManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 인챈트 탁자의 대가를 경험치 레벨 대신 <b>다이아몬드</b>로 받습니다.
 *
 * <p>바닐라는 칸마다 요구 레벨이 있었지만 이 모드는 <b>레벨을 아예 보지 않습니다.</b>
 * 레벨 0으로도 세 칸 모두 인챈트할 수 있고, 대신 다이아몬드를 냅니다. 청금석 요구량은
 * 바닐라 그대로 1·2·3 입니다.
 *
 * <h2>값을 바꾸려면</h2>
 *
 * <ul>
 *   <li>세 칸이 모두 같은 값이면 {@link #DIAMONDS_PER_ENCHANT} 하나만 고칩니다.</li>
 *   <li>칸마다 다르게 하려면 {@link #forSlot(int)} 하나만 고칩니다. 예를 들어
 *       5·6·7 로 만들려면 {@code return DIAMONDS_PER_ENCHANT + slot;} 로 바꾸면 됩니다.
 *       단추 표시·툴팁·차감·검사가 모두 이 메서드 하나를 보므로 다른 곳은 손댈 필요가
 *       없습니다.</li>
 * </ul>
 */
public final class EnchantmentDiamondCost {
	/** 인챈트 칸 수. 바닐라 {@code EnchantmentMenu.costs} 배열 길이와 같습니다. */
	public static final int SLOT_COUNT = 3;

	/** 인챈트 한 번에 드는 다이아몬드 개수. 지금은 세 칸이 모두 같습니다. */
	public static final int DIAMONDS_PER_ENCHANT = 5;

	private EnchantmentDiamondCost() {
	}

	/**
	 * 칸 하나에 드는 다이아몬드 개수입니다. 칸마다 다른 값을 주려면 여기만 고칩니다.
	 *
	 * @param slot 인챈트 칸 번호 (0 = 맨 위)
	 */
	public static int forSlot(int slot) {
		if (slot < 0 || slot >= SLOT_COUNT) {
			return 0;
		}
		return DIAMONDS_PER_ENCHANT;
	}

	/**
	 * 단추에 그릴 숫자입니다. 바닐라 요구 레벨 배열을 다이아몬드 개수 배열로 바꿉니다.
	 *
	 * <p>바닐라가 0 으로 둔 칸은 <b>인챈트 후보가 없다</b>는 뜻이고 화면도 그 칸을
	 * 빈칸으로 그리므로 0 을 그대로 남깁니다.
	 */
	public static int[] displayCosts(int[] vanillaCosts) {
		int[] shown = new int[vanillaCosts.length];
		for (int slot = 0; slot < vanillaCosts.length; slot++) {
			shown[slot] = vanillaCosts[slot] == 0 ? 0 : forSlot(slot);
		}
		return shown;
	}

	/** 공유 인벤토리 36칸과 확장 패널 27칸에 있는 다이아몬드를 모두 셉니다. */
	public static int count(Player player) {
		if (player == null) {
			return 0;
		}
		return count(mainItems(player)) + count(extraItems(player));
	}

	/** 한 칸을 쓸 수 있는지. 크리에이티브는 다이아몬드 없이도 됩니다. */
	public static boolean canAfford(Player player, int slot) {
		if (player == null) {
			return false;
		}
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return count(player) >= forSlot(slot);
	}

	/**
	 * 다이아몬드를 실제로 걷습니다. 공유 인벤토리를 먼저 비우고 모자라면 확장 패널에서
	 * 채웁니다.
	 *
	 * <p>반드시 {@code ContainerLevelAccess.execute(...)} 람다 안에서만 불러야 합니다.
	 * 클라이언트의 접근자는 {@code NULL} 이라 람다가 돌지 않는데, 람다 밖에서 깎으면
	 * 클라이언트에서도 아이템이 사라져 서버와 즉시 어긋납니다.
	 *
	 * @return 실제로 걷은 개수
	 */
	public static int consume(Player player, int slot) {
		if (player == null || player.hasInfiniteMaterials()) {
			return 0;
		}
		int wanted = forSlot(slot);
		int taken = remove(mainItems(player), wanted);
		if (taken < wanted) {
			taken += remove(extraItems(player), wanted - taken);
		}
		return taken;
	}

	static int count(List<ItemStack> items) {
		int found = 0;
		for (ItemStack stack : items) {
			if (stack.is(Items.DIAMOND)) {
				found += stack.getCount();
			}
		}
		return found;
	}

	static int remove(List<ItemStack> items, int wanted) {
		int taken = 0;
		for (int index = 0; index < items.size() && taken < wanted; index++) {
			ItemStack stack = items.get(index);
			if (!stack.is(Items.DIAMOND)) {
				continue;
			}
			int fromHere = Math.min(stack.getCount(), wanted - taken);
			stack.shrink(fromHere);
			taken += fromHere;
			if (stack.isEmpty()) {
				items.set(index, ItemStack.EMPTY);
			}
		}
		return taken;
	}

	private static List<ItemStack> mainItems(Player player) {
		return player.getInventory().getNonEquipmentItems();
	}

	private static List<ItemStack> extraItems(Player player) {
		if (!ExpandedInventoryManager.enabled()) {
			return List.of();
		}
		return ExpandedInventoryManager.extraFor(player).getItems();
	}
}
