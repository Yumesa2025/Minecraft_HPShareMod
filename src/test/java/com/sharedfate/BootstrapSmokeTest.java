package com.sharedfate;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapSmokeTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 빈_스택은_비어있다() {
		assertTrue(ItemStack.EMPTY.isEmpty());
	}

	@Test
	void 아이템_스택을_만들_수_있다() {
		ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
		assertEquals(1, stack.getCount());
		assertTrue(stack.is(Items.DIAMOND_PICKAXE));
	}
}
