package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoSilverOffersEffect;
import com.sharedfate.perk.effect.SwapExemptEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 골드 「열외」({@code swap_exempt})의 값 읽기와 명단 계산을 본다.
 *
 * <p>월드가 필요한 것(실제 순간이동·속성 수정자 부착)은 여기서 보지 않는다. 여기서 못박는 것은
 * 셋이다 — 범위 자르기, 「고른 사람」을 찾아내는 계산, 그리고 교환 명단에서 그 사람이 빠진
 * 결과다.
 */
class SwapExemptEffectTest {
	private static final UUID 갑 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID 을 = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final UUID 병 = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

	private static final String 열외 = "sharedfate:swap_exempt";
	private static final String 딴것 = "sharedfate:plain";

	@BeforeAll
	static void bootstrap() {
		// 속성 이름과 수정자 식별자를 만드는 데 레지스트리 부팅이 필요하다.
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 값을_안_적으면_십초_십오퍼센트다() {
		SwapExemptEffect effect = read("{ \"type\": \"swap_exempt\" }");

		assertEquals(SwapExemptEffect.DEFAULT_ON_SWAP_SECONDS, effect.onSwapSeconds());
		assertEquals(SwapExemptEffect.DEFAULT_SPEED_BONUS, effect.speedBonus(), 1.0e-9);
		assertEquals(SwapExemptEffect.DEFAULT_ON_SWAP_SECONDS * 20, effect.onSwapTicks());
	}

	@Test
	void 적은_값을_그대로_읽는다() {
		SwapExemptEffect effect =
				read("{ \"type\": \"swap_exempt\", \"on_swap_seconds\": 10, \"speed_bonus\": 0.15 }");

		assertEquals(10, effect.onSwapSeconds());
		assertEquals(200, effect.onSwapTicks());
		assertEquals(0.15, effect.speedBonus(), 1.0e-9);
	}

	@Test
	void 카멜케이스로_적어도_읽는다() {
		SwapExemptEffect effect =
				read("{ \"type\": \"swap_exempt\", \"onSwapSeconds\": 7, \"speedBonus\": 0.4 }");

		assertEquals(7, effect.onSwapSeconds());
		assertEquals(0.4, effect.speedBonus(), 1.0e-9);
	}

	@Test
	void 시간이_범위를_벗어나면_자른다() {
		assertEquals(SwapExemptEffect.MIN_ON_SWAP_SECONDS,
				read("{ \"on_swap_seconds\": 0 }").onSwapSeconds());
		assertEquals(SwapExemptEffect.MIN_ON_SWAP_SECONDS,
				read("{ \"on_swap_seconds\": -30 }").onSwapSeconds());
		assertEquals(SwapExemptEffect.MAX_ON_SWAP_SECONDS,
				read("{ \"on_swap_seconds\": 600 }").onSwapSeconds());
	}

	@Test
	void 속도가_범위를_벗어나면_자른다() {
		assertEquals(SwapExemptEffect.MIN_SPEED_BONUS,
				read("{ \"speed_bonus\": -1.0 }").speedBonus(), 1.0e-9);
		assertEquals(SwapExemptEffect.MAX_SPEED_BONUS,
				read("{ \"speed_bonus\": 9.0 }").speedBonus(), 1.0e-9);
	}

	@Test
	void 값이_숫자가_아니면_기본값으로_읽는다() {
		// 잘라 쓰는 쪽과 같은 정신이다. 숫자 하나가 틀렸다고 증강을 통째로 버리지 않는다.
		SwapExemptEffect effect =
				read("{ \"on_swap_seconds\": \"열\", \"speed_bonus\": \"조금\" }");

		assertEquals(SwapExemptEffect.DEFAULT_ON_SWAP_SECONDS, effect.onSwapSeconds());
		assertEquals(SwapExemptEffect.DEFAULT_SPEED_BONUS, effect.speedBonus(), 1.0e-9);
	}

	@Test
	void 속도가_영이면_열외만_남는다() {
		// 보너스가 0 이면 얹을 것이 없다. 그래도 정의는 살아 있어야 교환에서는 빠진다.
		SwapExemptEffect effect = read("{ \"speed_bonus\": 0.0 }");

		assertEquals(0.0, effect.speedBonus(), 1.0e-9);
		assertTrue(ownersOf(팀(열외, 갑), effect).containsKey(갑));
	}

	// ------------------------------------------------------------------ 「고른 사람」 찾기

	@Test
	void 고른_사람만_열외다() {
		SwapExemptEffect effect = read("{ }");
		TeamState state = 팀(열외, 갑);

		Map<UUID, SwapExemptEffect> owners = ownersOf(state, effect);

		assertEquals(Set.of(갑), owners.keySet(), "고른 사람 하나뿐이다");
		assertSame(effect, owners.get(갑));
	}

	@Test
	void 고른_사람을_모르면_아무도_빠지지_않는다() {
		// 「숨은 재능」처럼 덤으로 받은 증강은 perkOwners 에 들어오지 않는다. 주인이 없으면
		// 열외도 없다 — 조용히 팀 전원이 빠지는 것보다 아무도 안 빠지는 편이 안전하다.
		SwapExemptEffect effect = read("{ }");
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(열외);

		assertTrue(ownersOf(state, effect).isEmpty());
	}

	@Test
	void 증강을_끈_팀은_열외도_없다() {
		SwapExemptEffect effect = read("{ }");
		TeamState state = 팀(열외, 갑);
		state.perksEnabled = false;

		assertTrue(ownersOf(state, effect).isEmpty());
	}

	@Test
	void 풀에_없는_id_는_건너뛴다() {
		SwapExemptEffect effect = read("{ }");
		TeamState state = 팀("sharedfate:missing", 갑);

		assertTrue(ownersOf(state, effect).isEmpty());
	}

	@Test
	void 열외가_아닌_증강을_고른_사람은_빠지지_않는다() {
		TeamState state = 팀(딴것, 갑);
		Map<String, Perk> pool = Map.of(딴것,
				new Perk(딴것, "딴것", "", PerkRarity.SILVER, List.of(NoSilverOffersEffect.INSTANCE)));

		assertTrue(SwapExemptEffect.ownersIn(state, pool::get).isEmpty());
	}

	// ------------------------------------------------------------------ 교환 명단

	@Test
	void 교환_명단에서_열외만_빠진다() {
		List<UUID> movers = PerkSwapRules.withoutExempt(Set.of(을), List.of(갑, 을, 병));

		assertEquals(List.of(갑, 병), movers, "순서는 그대로 지킨다");
	}

	@Test
	void 열외가_없으면_명단이_그대로다() {
		List<UUID> members = List.of(갑, 을, 병);

		assertEquals(members, PerkSwapRules.withoutExempt(Set.of(), members));
	}

	@Test
	void 명단에_없는_열외는_아무_영향이_없다() {
		// 열외 당사자가 접속을 끊었거나 죽어 명단에 없는 경우다.
		assertEquals(List.of(갑, 을), PerkSwapRules.withoutExempt(Set.of(병), List.of(갑, 을)));
	}

	// ------------------------------------------------------------------ 최소 인원

	@Test
	void 열외로_둘_미만이_되면_교환하지_않고_일초뒤_재시도한다() {
		// 두 명짜리 팀에서 한 명이 열외를 고르면 자리를 바꿀 사람이 하나뿐이다. 그러면 교환
		// 시점이 와도 아무 일도 일어나지 않고 남은 시간이 1초로 되돌아간다 — 그 회차의 위치
		// 교환은 사실상 꺼진 것과 같다.
		TeamState state = TeamState.fresh(20.0F);
		state.enablePositionSwap(1);
		state.positionSwapRemainingTicks = 1;

		List<UUID> movers = PerkSwapRules.withoutExempt(Set.of(을), List.of(갑, 을));
		assertEquals(1, movers.size());

		boolean swapped = state.advancePositionSwapTick(movers.size() >= 2);

		assertFalse(swapped, "자리를 바꿀 사람이 둘 미만이면 교환하지 않는다");
		assertEquals(TeamState.PositionSwapLimits.RETRY_TICKS, state.positionSwapRemainingTicks);
	}

	@Test
	void 셋이면_한_명이_빠져도_교환은_계속된다() {
		TeamState state = TeamState.fresh(20.0F);
		state.enablePositionSwap(1);
		state.positionSwapRemainingTicks = 1;

		List<UUID> movers = PerkSwapRules.withoutExempt(Set.of(병), List.of(갑, 을, 병));

		assertTrue(state.advancePositionSwapTick(movers.size() >= 2));
		assertEquals(state.positionSwapIntervalTicks, state.positionSwapRemainingTicks,
				"교환이 일어났으면 주기가 그대로 다시 채워진다");
	}

	// ------------------------------------------------------------------ 도우미

	private static SwapExemptEffect read(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		PerkEffect effect = SwapExemptEffect.fromJson("sharedfate:테스트", 0, parsed);
		return assertInstanceOf(SwapExemptEffect.class, effect);
	}

	/** 증강 하나를 가지고 그것을 {@code owner} 가 고른 팀. */
	private static TeamState 팀(String perkId, UUID owner) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		state.perkOwners.put(perkId, owner);
		return state;
	}

	/**
	 * 보관소 대신 「{@link #열외} 하나만 들어 있는 풀」을 넘겨 계산한다.
	 *
	 * <p>{@code PerkEffectType} 에 {@code swap_exempt} 를 등록하는 일은 다른 담당의 몫이라,
	 * 여기서는 정의 파일을 거치지 않고 효과 객체를 직접 꽂아 계산만 확인한다.
	 */
	private static Map<UUID, SwapExemptEffect> ownersOf(TeamState state, SwapExemptEffect effect) {
		Perk perk = new Perk(열외, "열외", "", PerkRarity.GOLD, List.of(effect));
		return SwapExemptEffect.ownersIn(state, perkId -> 열외.equals(perkId) ? perk : null);
	}
}
