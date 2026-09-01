package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「게임 시작」의 회차 상태.
 *
 * <p>실제로 시작하는 {@code GameStartManager.start} 는 서버와 살아 있는 플레이어가 있어야 해서
 * 단위 시험으로 닿지 않는다. 대신 <b>그 동작이 도는 조건</b>과 <b>저장 호환</b>을 확인한다.
 * 아이템을 지우는 동작이라 「실수로 도는 길」이 없는지가 이 시험의 목적이다.
 */
class GameStartTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static CompoundTag encode(TeamState state) {
		return (CompoundTag) TeamState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
	}

	private static TeamState decode(CompoundTag tag) {
		return TeamState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}

	// ------------------------------------------------------------------ 시작 대기

	@Test
	void 새로_만든_팀은_시작_대기다() {
		assertFalse(TeamState.fresh(20.0F).runStarted);
		assertTrue(GameStartManager.waiting(TeamState.fresh(20.0F)));
		assertFalse(GameStartManager.started(TeamState.fresh(20.0F)));
	}

	@Test
	void 팀이_없으면_대기도_아니다() {
		// 팀에 속하지 않은 사람에게는 회차라는 것이 아예 없다. 여기서 대기로 보면 그 사람까지
		// 무적이 되고 아무 이득도 없다.
		assertFalse(GameStartManager.waiting(null));
		assertTrue(GameStartManager.started(null));
		assertFalse(GameStartManager.blocksDamage(null));
	}

	@Test
	void 전멸하면_다시_시작_대기로_돌아간다() {
		TeamState state = TeamState.fresh(20.0F);
		state.runStarted = true;

		state.resetAfterDeath(20.0F, false);

		assertFalse(state.runStarted,
				"전멸은 회차의 끝이므로 다음 회차는 다시 「게임 시작」에서 시작해야 한다");
	}

	// ------------------------------------------------------------------ 저장 호환

	@Test
	void 회차_시작_여부는_왕복_직렬화된다() {
		TeamState waiting = TeamState.fresh(20.0F);
		TeamState started = TeamState.fresh(20.0F);
		started.runStarted = true;

		assertFalse(decode(encode(waiting)).runStarted);
		assertTrue(decode(encode(started)).runStarted);
	}

	/**
	 * 이 기능이 생기기 전의 월드를 여는 길.
	 *
	 * <p>항목이 없으면 <b>이미 시작했다</b>로 읽어야 한다. 거짓으로 읽으면 이미 몇 시간을
	 * 플레이하던 팀이 갑자기 「시작 대기」가 되고, 그 상태에서 「게임 시작」을 누르면 가진 것이
	 * 전부 사라진다.
	 */
	@Test
	void 항목이_없는_예전_저장은_이미_시작한_것으로_읽는다() {
		TeamState started = TeamState.fresh(20.0F);
		started.runStarted = true;
		CompoundTag tag = encode(started);

		assertFalse(tag.keySet().contains("runStarted"),
				"진행 중인 팀은 이 항목을 적지 않아 예전 저장과 형태가 같아야 한다");
		assertTrue(decode(tag).runStarted);
	}

	@Test
	void 시작_대기인_팀만_저장에_항목을_남긴다() {
		assertTrue(encode(TeamState.fresh(20.0F)).keySet().contains("runStarted"),
				"기본값(참)과 다른 값이므로 반드시 적혀야 한다");
	}

	/** 시작 대기 상태에서도 나머지 저장 항목이 밀리거나 사라지면 안 된다. */
	@Test
	void 시작_대기_항목이_다른_저장을_밀지_않는다() {
		TeamState state = TeamState.fresh(30.0F);
		state.mainItems.set(0, new ItemStack(Items.DIAMOND, 5));
		state.xpLevel = 7;
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:tough_body");

		TeamState round = decode(encode(state));

		assertFalse(round.runStarted);
		assertEquals(5, round.mainItems.get(0).getCount());
		assertEquals(7, round.xpLevel);
		assertTrue(round.perksEnabled);
		assertEquals(1, round.ownedPerks.size());
	}

	// ------------------------------------------------------------------ 보스바 문구

	@Test
	void 아무도_시작하지_않았으면_보스바가_시작_대기라고_적는다() {
		assertEquals("SharedFate · 3회차 · 시작 대기",
				RunProgressManager.label(3, false, "", false));
	}

	@Test
	void 시작한_뒤에는_보스바가_진행_중이라고_적는다() {
		assertEquals("SharedFate · 3회차 진행 중",
				RunProgressManager.label(3, false, "", true));
	}

	@Test
	void 승리는_시작_여부와_무관하게_승리로_적는다() {
		assertEquals("SharedFate · 2회차 · 원정대 승리!",
				RunProgressManager.label(2, true, "원정대", true));
		assertEquals("SharedFate · 2회차 · 원정대 승리!",
				RunProgressManager.label(2, true, "원정대", false));
	}
}
