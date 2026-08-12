package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySwapperTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 공유_상태_전체를_한_번씩_드랍하고_비운다() {
		TeamState state = TeamState.fresh(40.0F);
		state.mainItems.set(0, new ItemStack(Items.DIAMOND, 2));
		state.extraItems.set(5, new ItemStack(Items.EMERALD, 4));
		state.equipment.set(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		state.equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		state.enderContainer.setItem(4, new ItemStack(Items.ENDER_PEARL, 3));
		state.overflowItems.add(new ItemStack(Items.GOLD_INGOT, 6));
		List<ItemStack> dropped = new ArrayList<>();

		InventorySwapper.drainSharedItems(state, dropped::add);

		assertEquals(6, dropped.size());
		assertEquals(2, countOf(dropped, Items.DIAMOND));
		assertEquals(1, countOf(dropped, Items.DIAMOND_CHESTPLATE));
		assertEquals(1, countOf(dropped, Items.SHIELD));
		assertEquals(3, countOf(dropped, Items.ENDER_PEARL));
		assertEquals(4, countOf(dropped, Items.EMERALD));
		assertEquals(6, countOf(dropped, Items.GOLD_INGOT));
		assertTrue(state.mainItems.stream().allMatch(ItemStack::isEmpty));
		assertTrue(state.extraItems.stream().allMatch(ItemStack::isEmpty));
		assertTrue(state.equipment.isEmpty());
		assertTrue(state.enderContainer.isEmpty());
		assertTrue(state.overflowItems.isEmpty());
	}

	@Test
	void 커서_대기열은_공유_슬롯이_비면_자동으로_복원된다() {
		TeamState state = TeamState.fresh(40.0F);
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 70));

		state.restoreOverflow(false);

		assertEquals(64, state.mainItems.get(0).getCount());
		assertEquals(6, state.mainItems.get(1).getCount());
		assertTrue(state.overflowItems.isEmpty());
	}

	@Test
	void 사망_드랍은_엔더상자를_보존한다() {
		TeamState state = TeamState.fresh(40.0F);
		state.mainItems.set(0, new ItemStack(Items.DIAMOND));
		state.extraItems.set(0, new ItemStack(Items.EMERALD, 3));
		state.equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		state.enderContainer.setItem(0, new ItemStack(Items.ENDER_PEARL, 2));
		List<ItemStack> dropped = new ArrayList<>();

		InventorySwapper.drainDeathDrops(state, dropped::add);

		assertEquals(3, dropped.size());
		assertTrue(state.mainItems.stream().allMatch(ItemStack::isEmpty));
		assertTrue(state.extraItems.stream().allMatch(ItemStack::isEmpty));
		assertEquals(3, countOf(dropped, Items.EMERALD));
		assertTrue(state.equipment.isEmpty());
		assertEquals(2, state.enderContainer.getItem(0).getCount());
	}

	private static int countOf(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
		return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
