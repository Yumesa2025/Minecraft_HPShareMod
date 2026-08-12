package com.sharedfate;

import com.sharedfate.mixin.InventoryAccessor;
import com.sharedfate.team.SharedItemList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAccessorTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 같은_목록을_주입하면_두_인벤토리가_공유한다() {
		SharedItemList shared = SharedItemList.ofSize(36);
		Inventory first = inventoryUsing(shared);
		Inventory second = inventoryUsing(shared);

		first.setItem(4, new ItemStack(Items.NETHERITE_SWORD));

		assertSame(shared, first.getNonEquipmentItems());
		assertSame(shared, second.getNonEquipmentItems());
		assertSame(first.getItem(4), second.getItem(4));
		assertTrue(second.getItem(4).is(Items.NETHERITE_SWORD));
	}

	@Test
	void 한쪽의_스택_제자리_변형이_다른쪽에_반영된다() {
		SharedItemList shared = SharedItemList.ofSize(36);
		Inventory first = inventoryUsing(shared);
		Inventory second = inventoryUsing(shared);
		first.setItem(2, new ItemStack(Items.DIAMOND, 10));

		second.getItem(2).shrink(3);

		assertEquals(7, first.getItem(2).getCount());
	}

	private static Inventory inventoryUsing(SharedItemList shared) {
		Inventory inventory = new Inventory(null, new EntityEquipment());
		((InventoryAccessor) inventory).sharedfate$setItems(shared);
		return inventory;
	}
}
