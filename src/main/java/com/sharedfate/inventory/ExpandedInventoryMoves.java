package com.sharedfate.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 바닐라 27칸과 추가 27칸을 <b>하나로 이어진 공간</b>처럼 다루는 쉬프트 클릭 이동입니다.
 *
 * <h2>왜 바닐라 {@code moveItemStackTo} 를 쓸 수 없는가</h2>
 *
 * <p>바닐라는 「몇 번부터 몇 번까지」라는 <b>연속 구간 하나</b>만 받습니다. 그런데 추가
 * 27칸은 메뉴 번호로 보면 핫바 <b>뒤</b>에 붙어 있고 화면으로 보면 인벤토리 세 줄과 핫바
 * <b>사이</b>에 있습니다. 번호가 이어져 있지 않으므로 구간 하나로는 표현할 수 없습니다.
 *
 * <p>구간을 나눠 바닐라를 두 번 부르면 <b>순서가 달라집니다.</b> 바닐라는 「구간 전체에서
 * 합칠 곳을 먼저 다 찾고, 그다음에 빈칸을 채우는」 두 벌 훑기인데, 두 번 나눠 부르면
 * 앞 구간의 빈칸이 뒤 구간의 합칠 자리보다 먼저 쓰입니다. 그러면 같은 아이템이 한 칸에
 * 모이지 않고 흩어져, 사람 눈에는 「위아래가 따로 논다」로 보입니다.
 *
 * <p>그래서 바닐라와 <b>같은 두 벌 훑기</b>를 하되 번호 목록을 받도록 다시 썼습니다.
 */
public final class ExpandedInventoryMoves {
	private ExpandedInventoryMoves() {
	}

	/**
	 * {@code order} 에 적힌 순서대로 훑으며 아이템을 옮깁니다.
	 *
	 * @param order   화면에 보이는 순서대로 적은 메뉴 슬롯 번호
	 * @param reverse 참이면 목록을 <b>뒤에서부터</b> 훑습니다 (바닐라의 역방향과 같습니다)
	 * @return 한 칸이라도 옮겼으면 참
	 */
	public static boolean move(
			AbstractContainerMenu menu, ItemStack stack, int[] order, boolean reverse) {
		boolean moved = false;

		// 1벌 — 이미 같은 아이템이 있는 칸에 합칩니다.
		if (stack.isStackable()) {
			for (int step = 0; step < order.length && !stack.isEmpty(); step++) {
				Slot slot = menu.getSlot(at(order, step, reverse));
				if (!slot.isActive()) {
					continue;
				}
				ItemStack existing = slot.getItem();
				if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(stack, existing)) {
					continue;
				}
				int total = existing.getCount() + stack.getCount();
				int maximum = slot.getMaxStackSize(existing);
				if (total <= maximum) {
					stack.setCount(0);
					existing.setCount(total);
					slot.setChanged();
					moved = true;
				} else if (existing.getCount() < maximum) {
					stack.shrink(maximum - existing.getCount());
					existing.setCount(maximum);
					slot.setChanged();
					moved = true;
				}
			}
		}

		// 2벌 — 남은 것을 빈칸 하나에 넣습니다. 바닐라와 같이 첫 빈칸에서 멈춥니다.
		if (!stack.isEmpty()) {
			for (int step = 0; step < order.length; step++) {
				Slot slot = menu.getSlot(at(order, step, reverse));
				if (!slot.isActive() || !slot.getItem().isEmpty() || !slot.mayPlace(stack)) {
					continue;
				}
				int maximum = slot.getMaxStackSize(stack);
				slot.setByPlayer(stack.split(Math.min(stack.getCount(), maximum)));
				slot.setChanged();
				moved = true;
				break;
			}
		}
		return moved;
	}

	/** 이어 붙인 구간들을 화면 순서대로 늘어놓은 번호 목록으로 만듭니다. */
	public static int[] order(int... startAndLengthPairs) {
		int total = 0;
		for (int pair = 1; pair < startAndLengthPairs.length; pair += 2) {
			total += startAndLengthPairs[pair];
		}
		int[] order = new int[total];
		int index = 0;
		for (int pair = 0; pair + 1 < startAndLengthPairs.length; pair += 2) {
			int start = startAndLengthPairs[pair];
			int length = startAndLengthPairs[pair + 1];
			for (int offset = 0; offset < length; offset++) {
				order[index++] = start + offset;
			}
		}
		return order;
	}

	private static int at(int[] order, int step, boolean reverse) {
		return order[reverse ? order.length - 1 - step : step];
	}
}
