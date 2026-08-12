package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamEnderChestViewTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 두_플레이어_뷰는_같은_아이템_저장소만_공유한다() {
		PlayerEnderChestContainer backing = new PlayerEnderChestContainer();
		TeamEnderChestView first = new TeamEnderChestView(() -> backing);
		TeamEnderChestView second = new TeamEnderChestView(() -> backing);

		first.setItem(4, new ItemStack(Items.ENDER_PEARL, 8));
		assertEquals(8, second.getItem(4).getCount());
		assertTrue(second.removeItem(4, 3).is(Items.ENDER_PEARL));
		assertEquals(5, first.getItem(4).getCount());
	}

	@Test
	void 상속된_일괄_연산도_실제_공유_저장소를_사용한다() {
		PlayerEnderChestContainer backing = new PlayerEnderChestContainer();
		TeamEnderChestView view = new TeamEnderChestView(() -> backing);
		backing.setItem(0, new ItemStack(Items.DIAMOND, 2));

		var removed = view.removeAllItems();

		assertEquals(2, removed.getFirst().getCount());
		assertTrue(backing.isEmpty());
	}
}
