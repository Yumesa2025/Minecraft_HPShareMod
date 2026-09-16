package com.sharedfate.storage;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인벤토리가 꽉 차 못 받은 물건이 쌓이는 <b>팀 창고</b>.
 *
 * <p>여기서 지키는 약속은 하나다 — <b>아이템을 잃지 않는다.</b> 창고는 열 때 대기열에서 꺼내
 * 오고 닫을 때 되돌리는 구조라, 되돌리기를 빠뜨리면 꺼내지 않은 물건이 그대로 사라진다.
 */
class TeamStorageTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------ 되돌리기

	@Test
	void 닫을_때_남은_것이_대기열_앞으로_돌아간다() {
		TeamState state = TeamState.fresh(20.0F);
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));
		state.overflowItems.add(new ItemStack(Items.IRON_INGOT, 12));

		TeamStorage.Container container = openFor(state);
		assertTrue(state.overflowItems.isEmpty(), "열 때 대기열에서 꺼내 온다");

		TeamStorage.writeBack(container);

		assertEquals(2, state.overflowItems.size());
		assertEquals(Items.DIAMOND, state.overflowItems.getFirst().getItem(), "순서가 지켜져야 한다");
		assertEquals(12, state.overflowItems.get(1).getCount());
	}

	/** 다 꺼내 갔으면 대기열도 비어야 한다. 빈 칸이 되돌아가면 유령 묶음이 쌓인다. */
	@Test
	void 다_꺼내_가면_대기열이_빈다() {
		TeamState state = TeamState.fresh(20.0F);
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));

		TeamStorage.Container container = openFor(state);
		container.setItem(0, ItemStack.EMPTY);
		TeamStorage.writeBack(container);

		assertTrue(state.overflowItems.isEmpty());
	}

	/**
	 * 열려 있는 동안 새로 넘친 물건은 <b>뒤에</b> 붙고, 되돌린 것이 <b>앞</b>으로 온다.
	 *
	 * <p>그래야 다음에 열었을 때 아까 보던 것이 그대로 보인다.
	 */
	@Test
	void 열려_있는_동안_들어온_것은_뒤에_남는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.overflowItems.add(new ItemStack(Items.DIAMOND, 5));

		TeamStorage.Container container = openFor(state);
		state.overflowItems.add(new ItemStack(Items.BREAD, 3));   // 창고를 연 사이에 보급이 왔다
		TeamStorage.writeBack(container);

		assertEquals(2, state.overflowItems.size());
		assertEquals(Items.DIAMOND, state.overflowItems.getFirst().getItem());
		assertEquals(Items.BREAD, state.overflowItems.get(1).getItem());
	}

	/** 커서에 든 채 창을 닫아도 바닥에 버리지 않는다. */
	@Test
	void 커서에_든_것은_창고로_되돌아간다() {
		TeamState state = TeamState.fresh(20.0F);
		TeamStorage.Container container = openFor(state);

		TeamStorage.returnCarried(container, new ItemStack(Items.DIAMOND, 5));

		assertEquals(1, state.overflowItems.size());
		assertEquals(5, state.overflowItems.getFirst().getCount());
	}

	/** 화면은 여섯 줄이지만 저장에는 상한이 없다. 넘치는 것은 다음에 열 때 올라온다. */
	@Test
	void 쉰네_개까지만_화면에_올리고_나머지는_남는다() {
		TeamState state = TeamState.fresh(20.0F);
		for (int index = 0; index < TeamStorage.VISIBLE + 7; index++) {
			state.overflowItems.add(new ItemStack(Items.DIAMOND, 1));
		}

		openFor(state);

		assertEquals(7, state.overflowItems.size(), "올리지 못한 것은 대기열에 남아야 한다");
	}

	// ------------------------------------------------------------ 믹스인이 진짜 붙었는가

	/**
	 * refmap 이 없어 <b>대상이 틀려도 빌드는 통과한다.</b> 병합된 메서드 이름으로 확인한다.
	 *
	 * <p>이 둘이 안 붙으면 창고는 「넣을 수 있는 상자」가 되고, 닫을 때 <b>안 꺼낸 물건이
	 * 통째로 사라진다.</b> 둘 다 조용히 잘못되는 종류라 시험으로 못박는다.
	 */
	@Test
	void 창고_믹스인_둘이_실제로_붙는다() {
		assertTrue(merged(Slot.class).stream().anyMatch(n -> n.contains("storageIsTakeOnly")),
				"창고에 물건을 넣지 못하게 막는 주입이 안 붙었다: " + merged(Slot.class));
		assertTrue(merged(AbstractContainerMenu.class).stream()
						.anyMatch(n -> n.contains("returnStorageItems")),
				"닫을 때 되돌리는 주입이 안 붙었다 — 안 꺼낸 물건이 사라진다: "
						+ merged(AbstractContainerMenu.class));
	}

	private static Set<String> merged(Class<?> type) {
		return Arrays.stream(type.getDeclaredMethods())
				.map(Method::getName)
				.filter(name -> name.contains("sharedfate"))
				.collect(Collectors.toSet());
	}

	/** {@code TeamStorage.open} 이 화면을 여는 부분만 뺀, 대기열에서 꺼내 오는 몫. */
	private static TeamStorage.Container openFor(TeamState state) {
		return TeamStorage.takeForScreen(state);
	}
}
