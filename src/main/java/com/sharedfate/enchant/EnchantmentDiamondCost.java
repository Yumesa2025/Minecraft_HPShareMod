package com.sharedfate.enchant;

import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.effect.EnchantCostEffect;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 인챈트 탁자의 대가를 경험치 레벨 대신 <b>다이아몬드</b>로 받는다.
 *
 * <p>바닐라는 칸마다 요구 레벨이 있었지만 이 모드는 <b>레벨을 아예 보지 않는다.</b>
 * 레벨 0으로도 세 칸 모두 인챈트할 수 있고, 대신 다이아몬드를 낸다. 청금석 요구량은
 * 바닐라 그대로 1·2·3 이다.
 *
 * <h2>다이아몬드는 「칸」에서 받는다</h2>
 *
 * <p>인벤토리를 뒤져 걷지 않는다. 청금석 칸
 * 아래에 <b>진짜 칸</b>({@link EnchantmentDiamondSlot})을 두고 거기 있는 것만 센다.
 *
 * <h2>값을 바꾸려면</h2>
 *
 * <ul>
 *   <li>세 칸이 모두 같은 기본값이면 {@link #DIAMONDS_PER_ENCHANT} 하나만 고친다.</li>
 *   <li>칸마다 다르게 하려면 {@link #forSlot(Player, int)} 하나만 고친다.
 *       단추 표시·툴팁·차감·검사가 모두 이 메서드 하나를 보므로 다른 곳은 손댈 필요가
 *       없다.</li>
 * </ul>
 *
 * <h2>팀마다 값이 다르다 — {@code enchant_cost} 증강</h2>
 *
 * <p>{@link EnchantCostEffect} 를 가진 팀은 기본값 대신 그 증강이 적은 개수를 낸다. 증강이
 * 없는 팀은 {@value #DIAMONDS_PER_ENCHANT} 개 그대로다.
 *
 * <p><b>팀은 서버만 안다.</b> 그런데 단추의 숫자와 툴팁은 클라이언트가 그리므로, 클라이언트도
 * 같은 숫자를 알아야 한다. 그래서 인챈트
 * 메뉴에 데이터 칸({@link EnchantmentCostDataSlot})을 하나 더 달아 서버가 계산한 개수를
 * 내려보내고, 클라이언트는 그 값을 {@link #rememberShown} 으로 받아 둔다. 화면 쪽 경로
 * ({@link #forSlot(int)}, {@link #displayCosts}, {@link EnchantmentDiamondTooltip})는 전부 그
 * 값을 본다.
 *
 * <p>서버 쪽 경로({@link #canAfford}, {@link #consume})는 받아 둔 값을 쓰지 않고 <b>플레이어의
 * 팀을 직접</b> 본다. 실제로 걷는 개수가 클라이언트가 보낸 숫자에 좌우되면 안 된다.
 */
public final class EnchantmentDiamondCost {
	/** 인챈트 칸 수. 바닐라 {@code EnchantmentMenu.costs} 배열 길이와 같다. */
	public static final int SLOT_COUNT = 3;

	/** 인챈트 한 번에 드는 다이아몬드 개수. {@code enchant_cost} 증강이 없을 때의 값이다. */
	public static final int DIAMONDS_PER_ENCHANT = 5;

	/**
	 * 화면이 그릴 개수. 서버가 메뉴의 데이터 칸으로 내려보낸 값이다.
	 *
	 * <p>한 클라이언트가 인챈트 창을 둘 열 수는 없으므로 정적 값 하나로 충분하다. 창을 열 때
	 * {@code sendAllDataToRemote} 가 반드시 한 번 내려보내므로, 창이 열려 있는 동안 이 값은
	 * 언제나 그 창의 것이다. 전용 서버에서는 아무도 이 값을 쓰지 않는다.
	 */
	private static volatile int shownDiamonds = DIAMONDS_PER_ENCHANT;

	private EnchantmentDiamondCost() {
	}

	// ------------------------------------------------------------------ 팀별 개수

	/**
	 * 이 팀이 인챈트 한 번에 내는 다이아몬드 개수다.
	 *
	 * <p>{@code enchant_cost} 를 여럿 가졌으면 <b>가장 작은 값</b>이 이긴다.
	 *
	 * <p>증강을 꺼 두었거나 가진 증강이 없으면 팀 상태 두 번만 보고 곧바로 기본값이다.
	 */
	public static int forState(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return DIAMONDS_PER_ENCHANT;
		}
		int cheapest = DIAMONDS_PER_ENCHANT;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof EnchantCostEffect cost) {
					cheapest = Math.min(cheapest, cost.diamonds());
				}
			}
		}
		return cheapest;
	}

	/**
	 * 이 사람이 인챈트 한 번에 내는 다이아몬드 개수다.
	 *
	 * <p>서버의 팀원일 때만 팀을 본다. 클라이언트 쪽 플레이어는 팀 상태를 볼 수 없으므로
	 * 서버가 내려보낸 {@link #shownDiamonds} 를 쓴다. {@code clickMenuButton} 은 클라이언트에서도
	 * 그대로 도는 자리라, 여기서 무턱대고 기본값을 돌려주면 값이 1인 팀이 다이아몬드 2개를 들고도
	 * 단추를 누르지 못한다.
	 */
	public static int forPlayer(@Nullable Player player) {
		if (player instanceof ServerPlayer) {
			return forState(TeamLookup.stateOf(player.getUUID()));
		}
		return shownDiamonds();
	}

	/** 서버가 내려보낸 개수를 받아 둔다. {@link EnchantmentCostDataSlot} 만 부른다. */
	public static void rememberShown(int diamonds) {
		if (diamonds < 0 || diamonds > EnchantCostEffect.MAX_DIAMONDS) {
			// 우리가 보낸 값이 아니다. 기본값으로 물러난다.
			shownDiamonds = DIAMONDS_PER_ENCHANT;
			return;
		}
		shownDiamonds = diamonds;
	}

	/** 화면이 그릴 개수. 아직 아무것도 받지 못했으면 기본값이다. */
	public static int shownDiamonds() {
		return shownDiamonds;
	}

	/** 받아 둔 값을 기본값으로 되돌린다. 월드에서 나갈 때와 시험이 쓴다. */
	public static void resetShown() {
		shownDiamonds = DIAMONDS_PER_ENCHANT;
	}

	// ------------------------------------------------------------------ 칸별 개수

	/**
	 * 칸 하나에 드는 다이아몬드 개수다. <b>화면용</b>이며 서버가 내려보낸 값을 본다.
	 *
	 * @param slot 인챈트 칸 번호 (0 = 맨 위)
	 */
	public static int forSlot(int slot) {
		if (slot < 0 || slot >= SLOT_COUNT) {
			return 0;
		}
		return shownDiamonds();
	}

	/**
	 * 칸 하나에 드는 다이아몬드 개수다. 칸마다 다른 값을 주려면 여기만 고친다.
	 *
	 * @param player 인챈트하려는 사람. 팀을 알아내는 데 쓴다
	 * @param slot   인챈트 칸 번호 (0 = 맨 위)
	 */
	public static int forSlot(@Nullable Player player, int slot) {
		if (slot < 0 || slot >= SLOT_COUNT) {
			return 0;
		}
		return forPlayer(player);
	}

	/**
	 * 단추에 그릴 숫자다. 바닐라 요구 레벨 배열을 다이아몬드 개수 배열로 바꾼다.
	 *
	 * <p>바닐라가 0 으로 둔 칸은 <b>인챈트 후보가 없다</b>는 뜻이고 화면도 그 칸을
	 * 빈칸으로 그리므로 0 을 그대로 남긴다.
	 */
	public static int[] displayCosts(int[] vanillaCosts) {
		int[] shown = new int[vanillaCosts.length];
		for (int slot = 0; slot < vanillaCosts.length; slot++) {
			shown[slot] = vanillaCosts[slot] == 0 ? 0 : forSlot(slot);
		}
		return shown;
	}

	/** 메뉴에 달린 다이아몬드 칸. 없으면 {@code null}. */
	public static Container containerOf(Object menu) {
		return menu instanceof EnchantmentDiamondAccess access
				? access.sharedfate$diamondContainer()
				: null;
	}

	/** 다이아몬드 칸에 들어 있는 개수. */
	public static int count(Container diamonds) {
		if (diamonds == null) {
			return 0;
		}
		int found = 0;
		for (int slot = 0; slot < diamonds.getContainerSize(); slot++) {
			ItemStack stack = diamonds.getItem(slot);
			if (stack.is(Items.DIAMOND)) {
				found += stack.getCount();
			}
		}
		return found;
	}

	/** 메뉴에 든 다이아몬드 개수. 화면이 단추 숫자를 정할 때 쓴다. */
	public static int countIn(Object menu) {
		return count(containerOf(menu));
	}

	/** 한 칸을 쓸 수 있는지. 크리에이티브는 다이아몬드 없이도 된다. */
	public static boolean canAfford(Player player, Container diamonds, int slot) {
		if (player == null) {
			return false;
		}
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return count(diamonds) >= forSlot(player, slot);
	}

	/**
	 * 다이아몬드 칸에서 실제로 걷는다.
	 *
	 * <p>반드시 {@code ContainerLevelAccess.execute(...)} 람다 안에서만 불러야 한다.
	 * 클라이언트의 접근자는 {@code NULL} 이라 람다가 돌지 않는데, 람다 밖에서 깎으면
	 * 클라이언트에서도 아이템이 사라져 서버와 즉시 어긋난다.
	 *
	 * @return 실제로 걷은 개수
	 */
	public static int consume(Player player, Container diamonds, int slot) {
		if (player != null && player.hasInfiniteMaterials()) {
			return 0;
		}
		if (diamonds == null) {
			return 0;
		}
		int wanted = forSlot(player, slot);
		int taken = 0;
		for (int index = 0; index < diamonds.getContainerSize() && taken < wanted; index++) {
			ItemStack stack = diamonds.getItem(index);
			if (!stack.is(Items.DIAMOND)) {
				continue;
			}
			int fromHere = Math.min(stack.getCount(), wanted - taken);
			stack.shrink(fromHere);
			taken += fromHere;
			if (stack.isEmpty()) {
				diamonds.setItem(index, ItemStack.EMPTY);
			}
		}
		if (taken > 0) {
			diamonds.setChanged();
		}
		return taken;
	}
}
