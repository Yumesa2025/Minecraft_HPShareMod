package com.sharedfate.team;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.PerkChoiceSession;
import com.sharedfate.sync.TeamRosterStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 다시 뽑기의 <b>횟수가 어디에 살아 있는지</b>.
 *
 * <p>다시 뽑기는 값이 두 자리에 나뉘어 있다. 「이 팀은 회차당 몇 번으로 정했다」는
 * {@link TeamCreationSettings} 가 정하고 팀 명단 파일이 회차를 넘겨 이어 가며, 「이번 회차에
 * 몇 번 남았다」는 월드 저장에 들어 있다가 회차가 넘어가면 가득 찬다. 이 둘이 섞이면
 * 회차를 넘길 때마다 횟수가 0 인 채로 시작하거나, 반대로 한 회차 안에서 무한히 뽑히게 된다.
 *
 * <p>재추첨 자체는 살아 있는 서버가 있어야 해서 여기서 다루지 못한다. 대신 세션이 없을 때
 * 요청을 받지 않는다는 방어선만 확인한다.
 */
class TeamRerollSettingTest {
	private static final UUID TEAM = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID MEMBER = UUID.fromString("30000000-0000-0000-0000-0000000000a1");

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

	// ------------------------------------------------------------------ 팀이 정하는 값

	@Test
	void 아무것도_안_적고_만든_팀은_회차당_세_번이다() {
		assertEquals(3, TeamCreationSettings.DEFAULT_REROLL_COUNT);
		assertEquals(3, TeamCreationSettings.defaults(20.0F).rerollCount());
	}

	@Test
	void 범위를_벗어난_값은_조용히_접힌다() {
		// 명령은 이미 0~10 으로 거르지만, 손상된 명단 파일에서도 흘러들어오는 값이다.
		// 그것 때문에 팀 만들기나 회차 복원이 죽으면 안 된다.
		assertEquals(0, TeamCreationSettings.defaults(20.0F).withRerollCount(-4).rerollCount());
		assertEquals(10, TeamCreationSettings.defaults(20.0F).withRerollCount(999).rerollCount());
		assertEquals(0, TeamCreationSettings.sanitizeRerollCount(Integer.MIN_VALUE));
		assertEquals(10, TeamCreationSettings.sanitizeRerollCount(Integer.MAX_VALUE));
	}

	@Test
	void 정한_값이_갓_만든_팀에_가득_찬_채로_새겨진다() {
		TeamState state = TeamState.fresh(20.0F);

		TeamCreationSettings.defaults(20.0F).withRerollCount(5).applyTo(state);

		assertEquals(5, state.rerollAllowance);
		assertEquals(5, state.rerollsRemaining, "갓 만든 팀은 한 번도 쓴 적이 없다");
	}

	@Test
	void 요약에_회차당_몇_번인지가_보인다() {
		assertTrue(TeamCreationSettings.defaults(20.0F).summary().contains("다시 뽑기: 회차당 3회"));
		assertTrue(TeamCreationSettings.defaults(20.0F).withRerollCount(0).summary()
				.contains("다시 뽑기: 회차당 0회"));
	}

	// ------------------------------------------------------------------ 월드 저장 (회차 안)

	@Test
	void 이번_회차에_남은_횟수가_월드_저장을_왕복한다() {
		TeamState state = TeamState.fresh(20.0F);
		state.rerollAllowance = 5;
		state.rerollsRemaining = 2;

		TeamState round = decode(encode(state));

		assertEquals(5, round.rerollAllowance);
		assertEquals(2, round.rerollsRemaining);
	}

	@Test
	void 기본값_그대로인_팀은_reroll_항목을_아예_저장하지_않는다() {
		// 이 기능이 생기기 전과 저장 형태가 같아야 예전 서버도 나머지를 그대로 읽는다.
		assertFalse(encode(TeamState.fresh(20.0F)).contains("reroll"));
	}

	@Test
	void reroll_항목이_없는_기존_월드는_회차당_세_번으로_열린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.rerollsRemaining = 1;
		CompoundTag encoded = encode(state);
		assertTrue(encoded.contains("reroll"), "기본값과 다르면 저장에 있어야 한다");
		encoded.remove("reroll");

		TeamState round = decode(encoded);

		assertEquals(3, round.rerollAllowance);
		assertEquals(3, round.rerollsRemaining);
	}

	@Test
	void 남은_횟수가_허용치보다_클_수는_없다() {
		TeamState state = TeamState.fresh(20.0F);
		state.rerollAllowance = 2;
		state.rerollsRemaining = 9;

		state.sanitize(20.0F);

		assertEquals(2, state.rerollsRemaining);
	}

	@Test
	void 망가진_남은_횟수는_0_으로_되돌린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.rerollsRemaining = -3;

		state.sanitize(20.0F);

		assertEquals(0, state.rerollsRemaining);
	}

	// ------------------------------------------------------------------ 회차 넘기기
	//
	// 명단 파일 자체를 읽고 쓰는 시험은 TeamRosterStore.save/load 가 패키지 전용이라
	// com.sharedfate.sync.TeamRosterRerollTest 에 있다.

	@Test
	void 회차가_넘어가면_횟수가_다시_찬다() {
		TeamManager fresh = new TeamManager();
		ShareTeam team = new ShareTeam(TEAM, "원정대", List.of(MEMBER));

		// 전멸 직전에 다 써 버렸더라도, 명단이 이어 가는 것은 「회차당 몇 번」뿐이다.
		fresh.restoreFreshRoster(List.of(new TeamRosterStore.RestoredTeam(
				team, true, 20.0F, 0, false, false, List.of(), false, 4)));

		TeamState restored = fresh.stateByTeamId(TEAM);
		assertEquals(4, restored.rerollAllowance);
		assertEquals(4, restored.rerollsRemaining, "회차당이므로 새 회차는 가득 찬 채로 시작한다");
	}

	@Test
	void 다시_뽑기를_안_쓰기로_한_팀은_회차가_넘어가도_0회다() {
		TeamManager fresh = new TeamManager();
		ShareTeam team = new ShareTeam(TEAM, "원정대", List.of(MEMBER));

		fresh.restoreFreshRoster(List.of(new TeamRosterStore.RestoredTeam(
				team, true, 20.0F, 0, false, false, List.of(), false, 0)));

		assertEquals(0, fresh.stateByTeamId(TEAM).rerollsRemaining);
	}

	// ------------------------------------------------------------------ 서버 검증

	@Test
	void 진행_중인_세션이_아니면_다시_뽑기를_받지_않는다() {
		PerkChoiceSession.reset();

		assertFalse(PerkChoiceSession.acceptsReroll(TEAM, 15));
		assertFalse(PerkChoiceSession.acceptsReroll(null, 15));
	}
}
