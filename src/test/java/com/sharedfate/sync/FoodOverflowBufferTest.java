package com.sharedfate.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodOverflowBufferTest {
	@Test
	void simultaneousFoodOverflowIsStoredInsteadOfDiscarded() {
		FoodOverflowBuffer.Result result = FoodOverflowBuffer.calculate(14, 10, 10, 0.0F);

		assertEquals(20, result.foodLevel());
		assertEquals(4.0F, result.reserve());
	}

	@Test
	void localFoodCapLossIsAlsoRecovered() {
		FoodOverflowBuffer.Result result = FoodOverflowBuffer.calculate(19, 2, 10, 0.0F);

		assertEquals(20, result.foodLevel());
		assertEquals(9.0F, result.reserve());
	}

	@Test
	void reserveDelaysLaterHungerLoss() {
		FoodOverflowBuffer.Result result = FoodOverflowBuffer.calculate(20, -1, 0, 4.0F);

		assertEquals(20, result.foodLevel());
		assertEquals(3.0F, result.reserve());
	}

	@Test
	void reserveHasAnAntiAbuseCap() {
		FoodOverflowBuffer.Result result = FoodOverflowBuffer.calculate(20, 0, 200, 70.0F);

		assertEquals(20, result.foodLevel());
		assertEquals(FoodOverflowBuffer.MAX_RESERVE, result.reserve());
	}
}
