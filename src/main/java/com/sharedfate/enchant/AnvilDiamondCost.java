package com.sharedfate.enchant;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 모루의 대가를 경험치 레벨 대신 <b>다이아몬드</b>로 받는다.
 *
 * <p>인챈트 탁자({@link EnchantmentDiamondCost})와 같은 모양이지만 <b>더 단순하다.</b>
 * 인챈트 쪽은 {@code enchant_cost} 증강으로 팀마다 값이 달라져 서버가 계산한 값을 데이터
 * 칸으로 내려보내야 했다. 모루는 <b>언제나 같은 값</b>이라 그럴 필요가 없다 — 같은 모드
 * jar 를 쓰는 이상 클라이언트도 서버도 이 상수를 그대로 안다.
 *
 * <h2>바닐라 비용 계산은 그대로 둔다</h2>
 *
 * <p>{@code AnvilMenu.createResult()} 가 계산하는 「진짜」 비용(합치기·이름표·수선의
 * 복잡도)은 손대지 않는다. 그 값은 여전히 다이아몬드가 아니라 <b>바닐라 단위(레벨)</b>로
 * 계산되고, 결과가 있는지 없는지({@code cost > 0})를 가리는 데만 쓰인다. 실제로 걷는
 * 개수는 이 클래스의 {@value #DIAMONDS_PER_REPAIR} 하나뿐이다.
 *
 * <h2>「너무 비쌉니다」 상한을 없앤 이유</h2>
 *
 * <p>바닐라는 계산된 비용이 40레벨을 넘으면(크리에이티브 제외) 결과물 자체를 지워
 * 「Too Expensive!」를 띄운다. 그 상한은 <b>레벨을 걷는 경제에서만</b> 뜻이 있다 — 비용이
 * 곧 대가이기 때문이다. 이 모드는 대가를 다이아몬드 {@value #DIAMONDS_PER_REPAIR}개로
 * 고정했으므로, 계산값이 아무리 커도 실제로 내는 것은 똑같다. 그런데도 상한을 그대로
 * 두면 <b>「할 수 있는 조합인데 표시만으로 막히는」</b> 상황이 생긴다. 그래서
 * {@code AnvilMenuDiamondMixin} 이 그 상한만 무력화한다.
 */
public final class AnvilDiamondCost {
	/** 모루 한 번에 드는 다이아몬드 개수. 바닐라처럼 작업 내용에 따라 오르내리지 않는다. */
	public static final int DIAMONDS_PER_REPAIR = 10;

	private AnvilDiamondCost() {
	}

	/** 메뉴에 달린 다이아몬드 칸. 없으면 {@code null}. */
	@Nullable
	public static Container containerOf(AnvilMenu menu) {
		return menu instanceof AnvilDiamondAccess access
				? access.sharedfate$diamondContainer()
				: null;
	}

	/** 다이아몬드 칸에 들어 있는 개수. */
	public static int count(@Nullable Container diamonds) {
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

	/** 다이아몬드가 모자라지 않은지. 크리에이티브는 다이아몬드 없이도 된다. */
	public static boolean canAfford(@Nullable Player player, @Nullable Container diamonds) {
		if (player == null) {
			return false;
		}
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return count(diamonds) >= DIAMONDS_PER_REPAIR;
	}

	/**
	 * 다이아몬드 칸에서 실제로 걷는다.
	 *
	 * <p>반드시 서버에서만 뜻이 있는 자리에서 불러야 한다 — 인챈트 탁자와 달리 모루의
	 * {@code onTake} 는 {@code access.execute(...)} 밖에서 경험치를 깎으므로, 여기도 그
	 * 자리를 그대로 대신한다. 클라이언트에서 불려도 제 화면의 그릇만 줄어들 뿐 서버 상태와는
	 * 무관하고, 뒤이은 칸 동기화가 그 예측을 덮어쓴다.
	 *
	 * @return 실제로 걷은 개수
	 */
	public static int consume(@Nullable Player player, @Nullable Container diamonds) {
		if (player != null && player.hasInfiniteMaterials()) {
			return 0;
		}
		if (diamonds == null) {
			return 0;
		}
		int wanted = DIAMONDS_PER_REPAIR;
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

	/**
	 * 화면에 보여 줄 비용이다. 결과가 없으면({@code vanillaCost <= 0}) 0을 돌려줘 화면이
	 * 아무 글자도 그리지 않게 한다. 결과가 있으면 계산값과 무관하게 언제나
	 * {@value #DIAMONDS_PER_REPAIR}다.
	 */
	public static int displayCost(int vanillaCost) {
		return vanillaCost > 0 ? DIAMONDS_PER_REPAIR : 0;
	}
}
