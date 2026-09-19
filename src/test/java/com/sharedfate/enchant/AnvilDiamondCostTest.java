package com.sharedfate.enchant;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모루 값을 다이아몬드로 치르는 규칙.
 *
 * <h2>⚠ 실제 {@code AnvilMenu} 를 만들어 시험하지 않는다</h2>
 * <p>이 시험 환경에는 <b>아이템 태그가 묶여 있지 않다.</b> {@code AnvilMenu.createResult} 는
 * 수리 재료를 가릴 때 {@code Repairable.is} 를 거쳐 태그를 보므로, 메뉴를 만들어 결과를 뽑는
 * 순간 {@code IllegalStateException: Tags not bound} 로 죽는다. 실제 메뉴로 「다이아 열 개면
 * 집힌다」 같은 것을 확인하려 들면 시험이 통째로 못 돈다.
 *
 * <p>그래서 여기서는 <b>태그를 지나지 않는 순수 계산</b>만 본다 — 개수 세기, 낼 수 있는지,
 * 걷기, 화면에 보일 숫자. 메뉴와 얽히는 자리(칸이 붙는 위치, 쉬프트 클릭, 경험치가 안 줄어드는
 * 것, 「비용이 너무 비쌉니다」가 안 뜨는 것)는 <b>눈으로 확인해야 하는 항목</b>이다.
 */
class AnvilDiamondCostTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	private static SimpleContainer withDiamonds(int count) {
		SimpleContainer container = new SimpleContainer(1);
		if (count > 0) {
			container.setItem(0, new ItemStack(Items.DIAMOND, count));
		}
		return container;
	}

	@Test
	void 값은_언제나_다이아몬드_열_개다() {
		assertEquals(10, AnvilDiamondCost.DIAMONDS_PER_REPAIR,
				"바닐라처럼 작업 내용에 따라 오르내리지 않는다");
	}

	@Test
	void 개수를_센다() {
		assertEquals(0, AnvilDiamondCost.count(null), "칸이 없으면 0");
		assertEquals(0, AnvilDiamondCost.count(withDiamonds(0)));
		assertEquals(10, AnvilDiamondCost.count(withDiamonds(10)));
		assertEquals(64, AnvilDiamondCost.count(withDiamonds(64)));
	}

	/** 다이아몬드가 아닌 것은 값으로 세지 않는다. */
	@Test
	void 다이아몬드가_아니면_세지_않는다() {
		SimpleContainer other = new SimpleContainer(1);
		other.setItem(0, new ItemStack(Items.EMERALD, 64));

		assertEquals(0, AnvilDiamondCost.count(other));
	}

	/**
	 * 사람이 없으면 언제나 못 낸다.
	 *
	 * <p>{@code canAfford} 는 크리에이티브인지부터 물으므로 {@code Player} 없이는 답할 수
	 * 없다. 그런데 이 시험 환경에서는 {@code Player} 를 만들 수 없다 — 그래서 <b>개수 경계
	 * (아홉 개는 안 되고 열 개는 된다)는 여기서 확인되지 않는다.</b> {@link #개수를_센다} 와
	 * {@link #걷으면_열_개가_줄어든다} 가 그 언저리를 대신 지키고, 실제 경계는 눈으로 봐야 한다.
	 */
	@Test
	void 사람이_없으면_못_낸다() {
		assertFalse(AnvilDiamondCost.canAfford(null, withDiamonds(64)));
		assertFalse(AnvilDiamondCost.canAfford(null, null));
	}

	@Test
	void 걷으면_열_개가_줄어든다() {
		SimpleContainer diamonds = withDiamonds(12);

		int taken = AnvilDiamondCost.consume(null, diamonds);

		assertEquals(10, taken);
		assertEquals(2, AnvilDiamondCost.count(diamonds), "낸 만큼만 줄어든다");
	}

	/**
	 * 모자라면 <b>있는 만큼만</b> 걷고 걷은 개수를 돌려준다.
	 *
	 * <p>⚠ 이것만 보면 「아홉 개밖에 없는데 아홉 개를 빼앗긴다」로 읽히지만, 실제로는 그 길로
	 * 들어올 수 없다. 걷는 자리({@code AnvilMenu.onTake})는 {@code mayPickup} 이 참일 때만
	 * 도달하고, 그 판정이 이미 {@code canAfford} 로 막는다. 여기서 부분 징수를 막지 <b>않는</b>
	 * 이유는, 막아 두면 「열 개가 여러 칸에 나뉘어 있는」 정상적인 경우까지 함께 막히기
	 * 때문이다. 이 값이 몇인지가 뜻을 갖는 자리는 없고 돌려주는 개수만 쓰인다.
	 */
	@Test
	void 모자라면_있는_만큼만_걷는다() {
		SimpleContainer diamonds = withDiamonds(9);

		int taken = AnvilDiamondCost.consume(null, diamonds);

		assertEquals(9, taken, "걷은 개수를 그대로 돌려준다");
		assertEquals(0, AnvilDiamondCost.count(diamonds));
	}

	/** 열 개가 여러 칸에 나뉘어 있어도 합쳐서 걷는다. */
	@Test
	void 여러_칸에_나뉘어_있어도_합쳐서_걷는다() {
		SimpleContainer diamonds = new SimpleContainer(3);
		diamonds.setItem(0, new ItemStack(Items.DIAMOND, 4));
		diamonds.setItem(1, new ItemStack(Items.DIAMOND, 4));
		diamonds.setItem(2, new ItemStack(Items.DIAMOND, 4));

		assertEquals(10, AnvilDiamondCost.consume(null, diamonds));
		assertEquals(2, AnvilDiamondCost.count(diamonds));
	}

	/**
	 * 화면에 보이는 숫자.
	 *
	 * <p>바닐라는 이 값이 40 이상이면 「비용이 너무 비쌉니다」를 띄운다. 값을 다이아몬드로
	 * 치르는 지금은 그 경고가 뜻이 없으므로, <b>결과가 있으면 언제나 10</b>을 돌려줘 그 문구가
	 * 뜨지 않게 한다. 결과가 없을 때(0)는 바닐라처럼 아무것도 안 띄운다.
	 */
	@Test
	void 화면_숫자는_결과가_있으면_언제나_10이다() {
		assertEquals(0, AnvilDiamondCost.displayCost(0), "결과가 없으면 0");
		assertEquals(10, AnvilDiamondCost.displayCost(1));
		assertEquals(10, AnvilDiamondCost.displayCost(39));
		assertEquals(10, AnvilDiamondCost.displayCost(40),
				"40 이상이면 바닐라가 「너무 비쌉니다」를 띄우던 자리다");
		assertEquals(10, AnvilDiamondCost.displayCost(1000));
	}
}
