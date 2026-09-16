package com.sharedfate.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 팀 공유 인벤토리의 추가 칸 하나.
 *
 * <h2>⚠ 잠긴 칸 판정을 이 클래스가 들고 있어야 한다</h2>
 *
 * <p>추가 칸은 언제나 27개가 만들어져 있고 팀이 연 만큼만 보인다. 잠긴 칸은 화면
 * 밖({@link ExpandedInventoryManager#HIDDEN_Y})으로 치우는데, <b>쉬프트 클릭은 좌표를 보지 않고
 * 목적지를 고르므로</b> 숨기는 것만으로는 물건이 들어가는 것을 막지 못한다.
 *
 * <p>그 막이가 예전에는 {@code Slot.mayPlace} 에 주입하는 믹스인이었는데, <b>이 클래스가
 * {@code mayPlace} 를 재정의해서 그 믹스인이 한 번도 실행되지 않았다.</b> 추가 칸을 가진 슬롯은
 * 이 클래스뿐이라 막이가 통째로 죽어 있었다.
 *
 * <p>그래서 실제로 이런 일이 있었다 — <b>화로나 제작대의 결과 칸을 쉬프트 클릭했는데 인벤토리와
 * 핫바가 꽉 차 있으면, 물건이 화면 밖 칸으로 빨려 들어가 사라진 것처럼 보였다.</b> 바닐라
 * {@code quickMoveStack} 이 {@code moveItemStackTo(플레이어 시작, +36)} 으로 부르고, 그것을
 * {@code ExpandedStandardMenuMixin} 이 추가 27칸까지 포함한 목록으로 바꿔 넘기기 때문이다.
 *
 * <h2>꺼내는 것은 막지 않는다</h2>
 * <p>잠긴 칸에 이미 물건이 있으면 꺼낼 수 있어야 한다. 실제로는
 * {@code PerkInventorySlots.unlockedFor} 가 「물건이 든 칸까지는 반드시 연다」를 보장하지만,
 * 여기서도 안전한 쪽을 고른다.
 */
public final class ExpandedInventorySlot extends Slot implements SelfPaintedSlot {
	private final ExpandedInventoryContainer extra;

	public ExpandedInventorySlot(ExpandedInventoryContainer extra, int slot, int x, int y) {
		super(extra, slot, x, y);
		this.extra = extra;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return extra.active() && !locked();
	}

	/** 이 칸이 아직 안 열렸는가. 열린 칸 수는 팀의 증강과 「물건이 든 마지막 칸」이 정한다. */
	private boolean locked() {
		return getContainerSlot() >= ExpandedInventoryManager.unlockedFor(extra.owner());
	}

	@Override
	public boolean mayPickup(Player player) {
		return extra.active();
	}

	@Override
	public boolean isActive() {
		return extra.active();
	}
}
