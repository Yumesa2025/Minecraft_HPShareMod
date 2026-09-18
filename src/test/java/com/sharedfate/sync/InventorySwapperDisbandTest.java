package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 「팀 해체는 아이템을 바닥에 뿌리지 않고 지운다」를 못박는 시험.
 *
 * <h2>왜 {@code InventorySwapper.disbandTeam} 을 직접 부르지 않는가</h2>
 * <p>{@code disbandTeam} 은 {@code ServerPlayer}·{@code MinecraftServer}·{@code TeamManager} 가
 * 실제로 살아 있어야 돌아간다. {@code ServerPlayer} 는 시험 환경에서 만들 수 없다 —
 * {@code GameStartTest} 의 클래스 주석이 같은 한계를 이미 적어 뒀다. 그래서 여기서는 disbandTeam
 * 이 부르는 것과 <b>같은 계약</b>으로 {@link InventorySwapper#drainSharedItems} 를 직접 불러
 * 「받는 쪽이 아무 일도 하지 않아도 상태가 통째로 비는가」를 확인한다. disbandTeam 이 실제로 이
 * 계약대로 부르는지는 {@code InventorySwapper.java} 의 소스를 읽어야 하지만, 드랍 여부를 가르는
 * 진짜 갈림길 — 콜백이 아무것도 하지 않을 때 상태가 남김없이 비는가 — 은 이 시험이 닿는다.
 *
 * <p>경험치를 아무에게도 남기지 않는 것(할 일 ②)은 {@code StatMirror.setTotalExperience}가
 * {@code ServerPlayer} 를 받아야 해서 같은 이유로 단위 시험이 닿지 않는다. 실제 서버 없이는
 * 「해체한 사람에게 다시 몰아주지 않는다」를 확인할 방법이 없다 — 보고서에 그대로 적었다.
 */
class InventorySwapperDisbandTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * disbandTeam 이 넘기는 람다는 {@code stack -> {}} — 아무것도 하지 않는다. 드랍은커녕
	 * 어디에도 쌓아 두지 않는데도 공유 아이템이 전부 사라져야 「지운다」가 성립한다.
	 */
	@Test
	void 받는_쪽이_아무것도_하지_않아도_공유_아이템이_전부_사라진다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.DIAMOND, 5));
		state.extraItems.set(2, new ItemStack(Items.EMERALD, 3));
		state.equipment.set(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		state.equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		state.enderContainer.setItem(1, new ItemStack(Items.ENDER_PEARL, 2));
		state.overflowItems.add(new ItemStack(Items.GOLD_INGOT, 6));

		InventorySwapper.drainSharedItems(state, stack -> {
		});

		assertTrue(state.mainItems.stream().allMatch(ItemStack::isEmpty));
		assertTrue(state.extraItems.stream().allMatch(ItemStack::isEmpty));
		assertTrue(state.equipment.isEmpty());
		assertTrue(state.enderContainer.isEmpty());
		assertTrue(state.overflowItems.isEmpty());
		assertFalse(state.hasSharedItems(),
				"disbandTeam 바로 뒤에 도는 manager.disband 는 hasSharedItems() 가 거짓이어야"
						+ " 통과한다 — 여기서 거짓이 아니면 disband 자체가 예외로 막힌다");
	}

	/**
	 * 위 시험만으로는 「훑기 자체가 그 슬롯을 건드리지 않는다」와 「콜백이 불렸지만 아무것도
	 * 하지 않는다」를 구별하지 못한다. 콜백 호출 횟수를 세어, 아이템이 있으면 콜백이 실제로
	 * 최소 한 번은 불린다는 것까지 함께 못박는다.
	 */
	@Test
	void 아이템이_있으면_콜백은_반드시_한_번_이상_불린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.DIAMOND));

		AtomicInteger calls = new AtomicInteger();
		InventorySwapper.drainSharedItems(state, stack -> calls.incrementAndGet());

		if (calls.get() == 0) {
			fail("공유 아이템이 있는데 콜백이 한 번도 불리지 않았다");
		}
	}
}
