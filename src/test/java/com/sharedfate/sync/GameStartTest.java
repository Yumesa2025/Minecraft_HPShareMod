package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
	private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

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

	// ------------------------------------------------------------------ 자동 시작

	/**
	 * 월드 초기화를 끈 서버({@code resetWorldOnTeamDeath=false})의 전멸.
	 *
	 * <p>같은 월드에서 그대로 이어 가므로 회차도 이어져야 한다. 여기서 「시작 대기」로 되돌리면
	 * 사람이 단추를 다시 눌러야 하고, 그 단추가 방금 살아남은 것들까지 지운다.
	 */
	@Test
	void 전멸해도_회차는_이어진다() {
		TeamState state = TeamState.fresh(20.0F);
		state.runStarted = true;

		state.resetAfterDeath(20.0F, false);

		assertTrue(state.runStarted,
				"「게임 시작」은 첫 회차 전 한 번뿐이므로 전멸해도 회차 시작 여부는 그대로다");
	}

	/** 아직 시작하지 않은 팀은 전멸해도 대기 그대로여야 한다 (시작 전에는 죽지도 않지만). */
	@Test
	void 시작하지_않은_팀은_전멸_처리에도_대기_그대로다() {
		TeamState state = TeamState.fresh(20.0F);

		state.resetAfterDeath(20.0F, false);

		assertFalse(state.runStarted);
	}

	private static List<TeamRosterStore.RestoredTeam> roster(ShareTeam team, int swapIntervalTicks,
			List<ItemStack> legacyGear) {
		return List.of(new TeamRosterStore.RestoredTeam(team, true, 20.0F, swapIntervalTicks,
				false, false, legacyGear, false));
	}

	@Test
	void 새_월드에서_회차가_저절로_시작된다() {
		TeamManager source = new TeamManager();
		ShareTeam team = source.createTeam("화이팅", MEMBER, 20.0F);
		TeamManager fresh = new TeamManager();
		fresh.restoreFreshRoster(roster(team, 0, List.of()));
		// 명단 복원만으로는 아직 「시작 대기」다. 회차를 켜는 것은 beginNextRun 이다.
		assertFalse(fresh.stateByTeamId(team.teamId()).runStarted);

		assertEquals(1, GameStartManager.beginNextRun(fresh));

		assertTrue(fresh.stateByTeamId(team.teamId()).runStarted,
				"전멸해서 새 월드로 넘어간 회차는 단추 없이 저절로 진행 중이어야 한다");
	}

	@Test
	void 이미_시작한_팀은_자동_시작이_건드리지_않는다() {
		TeamManager manager = new TeamManager();
		ShareTeam team = manager.createTeam("화이팅", MEMBER, 20.0F);
		TeamState state = manager.stateByTeamId(team.teamId());
		state.runStarted = true;
		state.positionSwapIntervalTicks = 6000;
		state.positionSwapRemainingTicks = 40;

		assertEquals(0, GameStartManager.beginNextRun(manager));

		assertEquals(40, state.positionSwapRemainingTicks,
				"진행 중인 회차의 남은 교환 시간을 되감으면 안 된다");
	}

	/**
	 * 「유산」이 넘긴 장비는 자동 시작 때 인벤토리에 들어가야 한다.
	 *
	 * <p>{@code restoreFreshRoster} 는 목록만 들고 있고 인벤토리에 꽂지 않는다. 1회차 전이라면
	 * 「게임 시작」이 아이템을 지운 뒤에 넣지만, 2회차부터는 그 「게임 시작」이 없으므로 여기서
	 * 넣지 않으면 유산이 영영 사라진다.
	 */
	@Test
	void 자동_시작이_유산_장비를_인벤토리에_넣는다() {
		TeamManager source = new TeamManager();
		ShareTeam team = source.createTeam("화이팅", MEMBER, 20.0F);
		TeamManager fresh = new TeamManager();
		fresh.restoreFreshRoster(roster(team, 0, List.of(new ItemStack(Items.DIAMOND_PICKAXE))));
		TeamState state = fresh.stateByTeamId(team.teamId());
		assertEquals(1, state.legacyGear.size(), "복원 직후에는 아직 들고만 있어야 한다");

		GameStartManager.beginNextRun(fresh);

		assertTrue(state.legacyGear.isEmpty(), "회차가 시작되면 목록이 비어야 한다");
		assertTrue(state.overflowItems.isEmpty(), "빈 인벤토리라 넘칠 것이 없다");
		assertEquals(Items.DIAMOND_PICKAXE, state.mainItems.get(0).getItem());
	}

	/**
	 * 위치 교환의 남은 시간.
	 *
	 * <p>{@code restoreFreshRoster} 는 주기만 이어받고 남은 시간은 0 으로 둔다. 예전에는 그
	 * 뒤에 오는 「게임 시작」이 채웠는데, 자동 시작에는 그 자리가 없으므로 여기서 채우지 않으면
	 * 새 월드에 들어서자마자 첫 교환이 터진다.
	 */
	@Test
	void 자동_시작이_위치_교환_남은_시간을_주기로_채운다() {
		TeamManager source = new TeamManager();
		ShareTeam team = source.createTeam("화이팅", MEMBER, 20.0F);
		TeamManager fresh = new TeamManager();
		fresh.restoreFreshRoster(roster(team, 6000, List.of()));
		TeamState state = fresh.stateByTeamId(team.teamId());
		assertEquals(0, state.positionSwapRemainingTicks);

		GameStartManager.beginNextRun(fresh);

		assertEquals(6000, state.positionSwapRemainingTicks,
				"첫 교환은 새 회차가 열린 뒤 한 주기가 지나야 온다");
	}

	@Test
	void 팀이_없으면_자동_시작할_것도_없다() {
		assertEquals(0, GameStartManager.beginNextRun(null));
		assertEquals(0, GameStartManager.beginNextRun(new TeamManager()));
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
