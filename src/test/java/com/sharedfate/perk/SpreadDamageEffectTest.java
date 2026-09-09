package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.SpreadDamageEffect;
import com.sharedfate.sync.SpreadDamageManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code spread_damage}(프리즘 「완충」)의 정의 읽기와 값 자르기, 그리고 미뤄 둔 몫을 나누는
 * 계산과 무적시간 흉내를 본다.
 *
 * <p>피해를 실제로 가로채고 다시 넣는 부분은 살아 있는 서버와 팀이 있어야 하므로 여기서 다루지
 * 않는다. 대신 그 처리에서 <b>실제로 판단을 내리는 조각</b>({@link SpreadDamageManager#gate},
 * {@link SpreadDamageManager#sliceAmount})은 월드를 읽지 않게 떼어 두었으므로 전부 여기서 본다.
 */
class SpreadDamageEffectTest {

	private static final float EPSILON = 0.0001F;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 값을_적지_않으면_기본값이다() {
		SpreadDamageEffect effect = create("{ \"type\": \"spread_damage\" }");

		assertEquals(SpreadDamageEffect.DEFAULT_SECONDS, effect.seconds());
	}

	@Test
	void 기본값이_설명과_맞는다() {
		assertEquals(4, SpreadDamageEffect.DEFAULT_SECONDS, "기본은 4초에 걸쳐 나뉘어 들어온다");
		assertEquals(1, SpreadDamageEffect.MIN_SECONDS);
		assertEquals(10, SpreadDamageEffect.MAX_SECONDS);
		assertEquals(20, SpreadDamageEffect.SLICE_PERIOD_TICKS,
				"몫은 1초에 한 번씩 들어간다. 바닐라 피격 무적시간과 같은 길이여야 한다");
	}

	@Test
	void 적은_값을_그대로_읽는다() {
		SpreadDamageEffect effect = create("{ \"type\": \"spread_damage\", \"seconds\": 7 }");

		assertEquals(7, effect.seconds());
		assertEquals(7 * 20, effect.spreadTicks());
	}

	@Test
	void 범위를_벗어난_값은_버리지_않고_자른다() {
		SpreadDamageEffect tooBig = create("{ \"type\": \"spread_damage\", \"seconds\": 999 }");
		SpreadDamageEffect tooSmall = create("{ \"type\": \"spread_damage\", \"seconds\": 0 }");
		SpreadDamageEffect negative = create("{ \"type\": \"spread_damage\", \"seconds\": -5 }");

		assertEquals(SpreadDamageEffect.MAX_SECONDS, tooBig.seconds());
		assertEquals(SpreadDamageEffect.MIN_SECONDS, tooSmall.seconds());
		assertEquals(SpreadDamageEffect.MIN_SECONDS, negative.seconds());
	}

	@Test
	void 숫자가_아닌_값은_기본값으로_물러선다() {
		SpreadDamageEffect effect = create("{ \"type\": \"spread_damage\", \"seconds\": \"넷\" }");

		assertEquals(SpreadDamageEffect.DEFAULT_SECONDS, effect.seconds());
	}

	@Test
	void 몫의_개수는_초_수와_같다() {
		assertEquals(4, create("{ \"type\": \"spread_damage\" }").sliceCount());
		assertEquals(1, create("{ \"type\": \"spread_damage\", \"seconds\": 1 }").sliceCount());
		assertEquals(10, create("{ \"type\": \"spread_damage\", \"seconds\": 10 }").sliceCount());
	}

	@Test
	void 몫의_개수는_적어도_하나다() {
		// 간격을 시간보다 길게 잡아도 한 번은 들어가야 미뤄 둔 피해가 사라지지 않는다.
		assertTrue(create("{ \"type\": \"spread_damage\", \"seconds\": 1 }").sliceCount() >= 1);
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		SpreadDamageEffect effect = create("{ \"type\": \"spread_damage\" }");

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	// ------------------------------------------------------------------ 몫 나누기

	@Test
	void 남은_몫을_남은_횟수로_고르게_나눈다() {
		assertEquals(2.5F, SpreadDamageManager.sliceAmount(10.0F, 4), EPSILON);
		assertEquals(5.0F, SpreadDamageManager.sliceAmount(10.0F, 2), EPSILON);
	}

	@Test
	void 마지막_한_번은_남은_전부를_넣는다() {
		assertEquals(3.3F, SpreadDamageManager.sliceAmount(3.3F, 1), EPSILON);
		assertEquals(3.3F, SpreadDamageManager.sliceAmount(3.3F, 0), EPSILON,
				"남은 횟수가 어긋나도 몫을 잃어버리지 않는다");
	}

	@Test
	void 나누어_넣은_합계가_미뤄_둔_총량과_같다() {
		// 3 으로 나누어떨어지지 않는 값이라 마지막 몫이 오차를 흡수해야 한다.
		float total = 10.0F;
		float remaining = total;
		float delivered = 0.0F;
		for (int slicesLeft = 3; slicesLeft > 0; slicesLeft--) {
			float slice = SpreadDamageManager.sliceAmount(remaining, slicesLeft);
			delivered += slice;
			remaining -= slice;
		}

		assertEquals(total, delivered, EPSILON);
		assertEquals(0.0F, remaining, EPSILON, "남는 몫이 없어야 큐가 제때 닫힌다");
	}

	@Test
	void 쓸_수_없는_값은_0_으로_본다() {
		assertEquals(0.0F, SpreadDamageManager.sliceAmount(0.0F, 4), EPSILON);
		assertEquals(0.0F, SpreadDamageManager.sliceAmount(-3.0F, 4), EPSILON);
		assertEquals(0.0F, SpreadDamageManager.sliceAmount(Float.NaN, 4), EPSILON);
		assertEquals(0.0F, SpreadDamageManager.sliceAmount(Float.POSITIVE_INFINITY, 4), EPSILON);
	}

	// ------------------------------------------------------------------ 겹칠 때 합치기

	/**
	 * 분산 중에 또 맞으면 남은 몫과 합쳐 <b>남은 횟수</b>로 다시 나눈다. 횟수는 늘지 않는다.
	 *
	 * <p>큐를 새로 쌓거나 시간을 늘리면 초당 들어가는 양이 한없이 커지거나 회복 금지가 영원히
	 * 풀리지 않는다. 그 규칙을 계산으로 못박아 둔다.
	 */
	@Test
	void 분산_중에_또_맞으면_남은_횟수_안에서_다시_나눈다() {
		int slicesLeft = 4;
		float remaining = 12.0F;
		float delivered = 0.0F;
		int slicesTaken = 0;

		// 1초 — 12 을 넷으로 나눈 3 이 들어간다.
		float first = SpreadDamageManager.sliceAmount(remaining, slicesLeft);
		assertEquals(3.0F, first, EPSILON);
		remaining -= first;
		slicesLeft--;
		delivered += first;
		slicesTaken++;

		// 그 직후 8 을 더 맞는다. 남은 9 에 더해 17 이 되고, 남은 횟수는 셋 그대로다.
		remaining += 8.0F;
		assertEquals(17.0F, remaining, EPSILON);
		assertEquals(3, slicesLeft, "맞았다고 시간이 늘어나면 안 된다");

		while (slicesLeft > 0) {
			float slice = SpreadDamageManager.sliceAmount(remaining, slicesLeft);
			remaining -= slice;
			slicesLeft--;
			delivered += slice;
			slicesTaken++;
		}

		assertEquals(20.0F, delivered, EPSILON, "맞은 20 이 하나도 빠지거나 늘지 않는다");
		assertEquals(4, slicesTaken, "처음 정한 네 번 안에서 끝난다");
		assertEquals(0.0F, remaining, EPSILON);
	}

	// ------------------------------------------------------------------ 무적시간 흉내

	@Test
	void 무적시간_밖에서_맞으면_전부_받고_무적시간이_찬다() {
		SpreadDamageManager.Gate gate = SpreadDamageManager.gate(6.0F, 0.0F, 0, false);

		assertEquals(6.0F, gate.accepted(), EPSILON);
		assertEquals(6.0F, gate.lastAmount(), EPSILON);
		assertEquals(SpreadDamageManager.INVULNERABLE_TICKS, gate.invulnerableTicks());
	}

	/**
	 * 좀비 셋에게 같은 틱에 맞아도 바닐라는 한 대만 센다. 가로채는 자리가 바닐라의 판정보다
	 * 앞이라, 이 규칙을 흉내 내지 않으면 미뤄 둔 피해가 몇 배로 불어난다.
	 */
	@Test
	void 무적시간_안에서_더_약하게_맞으면_받지_않는다() {
		SpreadDamageManager.Gate gate = SpreadDamageManager.gate(4.0F, 4.0F, 20, false);
		SpreadDamageManager.Gate weaker = SpreadDamageManager.gate(2.0F, 4.0F, 20, false);

		assertEquals(0.0F, gate.accepted(), EPSILON, "같은 세기는 더 들어가지 않는다");
		assertEquals(0.0F, weaker.accepted(), EPSILON);
		assertEquals(4.0F, weaker.lastAmount(), EPSILON, "받지 않았으면 아무것도 바뀌지 않는다");
		assertEquals(20, weaker.invulnerableTicks());
	}

	@Test
	void 무적시간_안에서_더_세게_맞으면_넘치는_만큼만_받는다() {
		SpreadDamageManager.Gate gate = SpreadDamageManager.gate(9.0F, 4.0F, 20, false);

		assertEquals(5.0F, gate.accepted(), EPSILON);
		assertEquals(9.0F, gate.lastAmount(), EPSILON);
		assertEquals(20, gate.invulnerableTicks(), "이 갈래에서는 무적시간이 다시 차지 않는다");
	}

	@Test
	void 무적시간이_절반_아래로_내려가면_다시_전부_받는다() {
		// 바닐라의 경계는 invulnerableTime > 10 이다.
		assertEquals(0.0F, SpreadDamageManager.gate(3.0F, 4.0F, 11, false).accepted(), EPSILON);
		assertEquals(3.0F, SpreadDamageManager.gate(3.0F, 4.0F, 10, false).accepted(), EPSILON);
	}

	@Test
	void 무적시간을_무시하는_피해는_그대로_받는다() {
		SpreadDamageManager.Gate gate = SpreadDamageManager.gate(1.0F, 9.0F, 20, true);

		assertEquals(1.0F, gate.accepted(), EPSILON, "bypasses_cooldown 은 창을 보지 않는다");
		assertEquals(1.0F, gate.lastAmount(), EPSILON);
		assertEquals(SpreadDamageManager.INVULNERABLE_TICKS, gate.invulnerableTicks());
	}

	/** 두 대를 이어 맞았을 때 큐에 들어가는 합계가 바닐라가 넣었을 합계와 같아야 한다. */
	@Test
	void 같은_틱에_두_번_맞으면_센_쪽_하나만큼만_쌓인다() {
		SpreadDamageManager.Gate first = SpreadDamageManager.gate(4.0F, 0.0F, 0, false);
		SpreadDamageManager.Gate second = SpreadDamageManager.gate(20.0F,
				first.lastAmount(), first.invulnerableTicks(), false);
		SpreadDamageManager.Gate third = SpreadDamageManager.gate(4.0F,
				second.lastAmount(), second.invulnerableTicks(), false);

		float queued = first.accepted() + second.accepted() + third.accepted();

		assertEquals(20.0F, queued, EPSILON, "4 + 20 + 4 를 맞아도 실제로 들어가는 것은 20 이다");
	}

	// ------------------------------------------------------------------ 도우미

	private static SpreadDamageEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(SpreadDamageEffect.class,
				SpreadDamageEffect.fromJson("sharedfate:테스트", 0, parsed));
	}
}
