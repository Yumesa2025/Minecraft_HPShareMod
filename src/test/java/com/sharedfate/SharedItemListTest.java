package com.sharedfate;

import com.sharedfate.team.SharedItemList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedItemListTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 지정한_크기로_빈_스택이_채워진다() {
		SharedItemList list = SharedItemList.ofSize(36);

		assertEquals(36, list.size());
		for (int i = 0; i < 36; i++) {
			assertTrue(list.get(i).isEmpty());
		}
	}

	@Test
	void 음수_크기는_거부한다() {
		assertThrows(IllegalArgumentException.class, () -> SharedItemList.ofSize(-1));
	}

	@Test
	void get은_저장된_인스턴스를_그대로_돌려준다() {
		SharedItemList list = SharedItemList.ofSize(36);
		ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
		list.set(0, stack);

		assertSame(stack, list.get(0), "복사본이 아니라 같은 인스턴스여야 한다");
	}

	@Test
	void 반환된_스택을_변형하면_리스트에_반영된다() {
		SharedItemList list = SharedItemList.ofSize(36);
		list.set(0, new ItemStack(Items.DIAMOND, 10));

		list.get(0).shrink(3);

		assertEquals(7, list.get(0).getCount());
	}

	@Test
	void clear는_크기를_유지하고_빈_스택으로_채운다() {
		SharedItemList list = SharedItemList.ofSize(36);
		list.set(5, new ItemStack(Items.DIAMOND));

		list.clear();

		assertEquals(36, list.size());
		assertTrue(list.get(5).isEmpty());
	}

	@Test
	void 코덱으로_왕복시키면_내용과_고정_크기가_보존된다() {
		SharedItemList list = SharedItemList.ofSize(36);
		list.set(0, new ItemStack(Items.DIAMOND_PICKAXE));
		list.set(35, new ItemStack(Items.OAK_LOG, 64));

		SharedItemList round = CodecRoundTrip.through(SharedItemList.codec(36), list);

		assertEquals(36, round.size());
		assertTrue(round.get(0).is(Items.DIAMOND_PICKAXE));
		assertEquals(64, round.get(35).getCount());
		assertTrue(round.get(1).isEmpty());
	}
}
