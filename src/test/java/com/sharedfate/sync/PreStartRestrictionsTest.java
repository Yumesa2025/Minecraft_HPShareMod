package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「시작 대기」 중에만 걸리는 네 제한({@link PreStartRestrictions})의 순수 계산부.
 *
 * <p>{@code ServerPlayer}·{@code ServerLevel}·실제 {@code WorldBorder} 조작은 서버가 있어야 해서
 * 여기서 다루지 않는다({@code applySpawnBorder}, {@code onBeforeBlockBreak}, {@code applyMorningLock},
 * {@code freezeHunger} 자체는 시험하지 않는다). 대신 그 안에서 실제 판단을 내리는 순수 함수들 —
 * 반경→지름 환산, 대기/시작 판정, 쿨다운 계산, 되돌릴 값 계산 — 을 하나씩 못박는다.
 */
class PreStartRestrictionsTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ ① 반경 → 지름

	/**
	 * <b>이 시험이 이 작업에서 가장 중요하다.</b> 월드보더의 {@code size} 는 지름이다. 반경 50을
	 * 그대로 넘기면 지름 50 — 반경 25 — 짜리 보더가 걸리는 사고가 난다.
	 */
	@Test
	void 반경_50은_지름_100이다() {
		assertEquals(100.0, PreStartRestrictions.lockDiameterBlocks(50));
		assertEquals(100.0, PreStartRestrictions.lockDiameterBlocks(
				PreStartRestrictions.SPAWN_LOCK_RADIUS_BLOCKS),
				"실제로 쓰는 상수(50)를 넣어도 지름 100이어야 한다");
	}

	@Test
	void 반경을_그대로_지름으로_쓰면_안_된다() {
		// 회귀 방지: "반경 50 == 지름 50"으로 잘못 되돌아가면 실제 반경은 25가 된다.
		assertNotEquals((double) PreStartRestrictions.SPAWN_LOCK_RADIUS_BLOCKS,
				PreStartRestrictions.lockDiameterBlocks(PreStartRestrictions.SPAWN_LOCK_RADIUS_BLOCKS));
	}

	@Test
	void 스폰_중심으로_보더_목표를_만든다() {
		PreStartRestrictions.BorderTarget target =
				PreStartRestrictions.spawnLockTarget(100.5, -200.5);

		assertEquals(100.0, target.size(), "반경 50칸의 지름은 100이어야 한다");
		assertEquals(100.5, target.centerX());
		assertEquals(-200.5, target.centerZ());
	}

	/**
	 * 되돌릴 값은 바닐라 기본값({@code WorldBorder.Settings.DEFAULT})을 그대로 가져온다. 여기서
	 * 다시 정의하지 않고 실제 상수를 그대로 읽었는지를 확인한다.
	 */
	@Test
	void 바닐라_기본값으로_되돌린다() {
		PreStartRestrictions.BorderTarget target = PreStartRestrictions.vanillaDefaultTarget();

		assertEquals(WorldBorder.Settings.DEFAULT.size(), target.size());
		assertEquals(WorldBorder.Settings.DEFAULT.centerX(), target.centerX());
		assertEquals(WorldBorder.Settings.DEFAULT.centerZ(), target.centerZ());
		// javap로 확인한 실제 값(정적 초기화 블록의 5.9999968E7, 센터 0,0)도 함께 못박는다.
		assertEquals(5.9999968E7, target.size(), 1.0e-3);
		assertEquals(0.0, target.centerX());
		assertEquals(0.0, target.centerZ());
	}

	@Test
	void 보더값이_같으면_다르지_않다() {
		PreStartRestrictions.BorderTarget target = PreStartRestrictions.spawnLockTarget(0.5, 0.5);

		assertFalse(PreStartRestrictions.bordersDiffer(100.0, 0.5, 0.5, target));
	}

	@Test
	void 보더값_중_하나라도_다르면_감지한다() {
		PreStartRestrictions.BorderTarget target = PreStartRestrictions.spawnLockTarget(0.5, 0.5);

		assertTrue(PreStartRestrictions.bordersDiffer(99.0, 0.5, 0.5, target), "지름이 다르다");
		assertTrue(PreStartRestrictions.bordersDiffer(100.0, 1.5, 0.5, target), "센터 X가 다르다");
		assertTrue(PreStartRestrictions.bordersDiffer(100.0, 0.5, 1.5, target), "센터 Z가 다르다");
	}

	// ------------------------------------------------------------------ ② 대기/시작 판정

	/**
	 * 시작 전 제한은 <b>팀이 없는 사람에게도 걸린다.</b>
	 *
	 * <p>기준은 「대기 중인 팀인가」가 아니라 <b>「회차가 시작됐는가」</b> 하나다. 팀을 아직
	 * 만들지 않은 사람은 회차를 시작하지 않은 것이므로 똑같이 걸린다. 여기가
	 * {@code GameStartManager.waiting} 과 일부러 다른 자리다 — 그쪽은 팀이 없으면 거짓이라,
	 * 그대로 가져다 쓰면 <b>팀을 만들기 전까지 제한이 하나도 걸리지 않는다.</b>
	 */
	@Test
	void 시작_전_제한은_팀이_없어도_걸린다() {
		assertTrue(PreStartRestrictions.blocksPreStartAction(null),
				"팀이 없는 사람도 회차를 시작하지 않은 것이므로 걸린다");

		TeamState waiting = TeamState.fresh(20.0F);
		assertTrue(PreStartRestrictions.blocksPreStartAction(waiting), "시작 대기 팀에는 걸린다");

		TeamState started = TeamState.fresh(20.0F);
		started.runStarted = true;
		assertFalse(PreStartRestrictions.blocksPreStartAction(started), "이미 시작한 팀에는 안 걸린다");
	}

	/** 서버에 하나뿐인 것(월드보더·시계)은 서버가 {@code null} 이어도 「시작 안 함」으로 본다. */
	@Test
	void 서버가_없으면_시작되지_않은_것으로_본다() {
		assertTrue(PreStartRestrictions.runNotStarted(null));
	}

	// ------------------------------------------------------------------ ③ 블록 파괴 알림 쿨다운

	@Test
	void 쿨다운_전에는_다시_알리지_않는다() {
		long cooldown = PreStartRestrictions.BLOCK_BREAK_NOTICE_COOLDOWN_TICKS;

		assertFalse(PreStartRestrictions.shouldNotifyBlockedBreak(100, 100 + cooldown - 1, cooldown));
	}

	@Test
	void 쿨다운이_지나면_다시_알린다() {
		long cooldown = PreStartRestrictions.BLOCK_BREAK_NOTICE_COOLDOWN_TICKS;

		assertTrue(PreStartRestrictions.shouldNotifyBlockedBreak(100, 100 + cooldown, cooldown),
				"경계값(정확히 쿨다운만큼 지남)도 다시 알려야 한다");
		assertTrue(PreStartRestrictions.shouldNotifyBlockedBreak(100, 100 + cooldown + 500, cooldown));
	}

	/** 서버 시각이 되감기는 이상 현상이 있어도 영영 억눌리면 안 된다. */
	@Test
	void 시각이_거꾸로_가도_억눌리지_않는다() {
		assertTrue(PreStartRestrictions.shouldNotifyBlockedBreak(1000, 10, 20));
	}

	// ------------------------------------------------------------------ ④ 허기 고정

	@Test
	void 이미_가득_찬_허기는_손대지_않는다() {
		assertFalse(PreStartRestrictions.needsHungerReset(
				PreStartRestrictions.HUNGER_FOOD_LEVEL, PreStartRestrictions.HUNGER_SATURATION));
	}

	@Test
	void 허기가_줄었으면_되돌려야_한다() {
		assertTrue(PreStartRestrictions.needsHungerReset(19, PreStartRestrictions.HUNGER_SATURATION));
		assertTrue(PreStartRestrictions.needsHungerReset(PreStartRestrictions.HUNGER_FOOD_LEVEL, 4.9F));
		assertTrue(PreStartRestrictions.needsHungerReset(0, 0.0F));
	}

	// ------------------------------------------------------------------ ⑤ 시각 고정 계산

	/**
	 * 아침으로 되돌리는 계산은 {@code PerkWorldRules.lockedTotalTicks} 에 그대로 위임한다.
	 * 여기서는 그 위임이 "날짜는 보존하고 하루 안 시각만 0으로 만든다"는 약속을 지키는지만
	 * 확인한다.
	 */
	@Test
	void 아침_고정은_날짜를_보존한다() {
		assertEquals(0L, PreStartRestrictions.lockedMorningTicks(0));
		assertEquals(0L, PreStartRestrictions.lockedMorningTicks(23_999), "아직 첫날이면 그대로 0");
		assertEquals(24_000L, PreStartRestrictions.lockedMorningTicks(24_000), "이튿날 아침은 24000");
		assertEquals(72_000L, PreStartRestrictions.lockedMorningTicks(86_000),
				"86000틱째(3일 14000틱)는 3일째 아침인 72000으로 돌아가야 한다");
	}
}
