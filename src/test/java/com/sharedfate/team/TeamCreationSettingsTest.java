package com.sharedfate.team;

import com.sharedfate.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀을 만들 때만 정하는 설정들.
 *
 * <p>여섯 항목 모두 <b>만든 뒤에는 바꿀 수 없으므로</b>, 만드는 순간에 정확히 새겨지는지와
 * 막혔을 때 사람에게 무엇이 나가는지가 이 시험의 전부다.
 */
class TeamCreationSettingsTest {

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

	// ------------------------------------------------------------------ 기본값

	@Test
	void 아무것도_안_적고_만든_팀은_증강이_켜져_있다() {
		assertTrue(TeamCreationSettings.defaults(20.0F).perksEnabled());
		assertTrue(TeamCreationSettings.DEFAULT_PERKS_ENABLED);
	}

	@Test
	void 기본값에서_알림_셋과_위치_교환은_꺼져_있다() {
		TeamCreationSettings settings = TeamCreationSettings.defaults(20.0F);

		assertFalse(settings.damageAlertEnabled());
		assertFalse(settings.deathAlertEnabled());
		assertFalse(settings.difficultyEscalationEnabled(),
				"회차를 통째로 어렵게 만드는 설정은 손으로 켜야 한다");
		assertFalse(settings.swapEnabled());
		assertEquals(20.0F, settings.maxHealth());
	}

	// ------------------------------------------------------------------ 새기기

	@Test
	void 정한_값이_갓_만든_팀_상태에_그대로_새겨진다() {
		TeamCreationSettings settings = TeamCreationSettings.defaults(20.0F)
				.withPerks(false).withDamageAlert(true).withDeathAlert(true)
				.withDifficultyEscalation(true).withMaxHealth(34.0F).withSwapIntervalMinutes(5);
		TeamState state = TeamState.fresh(20.0F);

		settings.applyTo(state);

		assertFalse(state.perksEnabled);
		assertTrue(state.damageAlertEnabled);
		assertTrue(state.deathAlertEnabled);
		assertTrue(state.difficultyEscalationEnabled);
		assertEquals(34.0F, state.baseMaxHealth);
		assertEquals(34.0F, state.maxHealth, "증강이 없는 갓 만든 팀은 둘이 같아야 한다");
		assertEquals(5, state.positionSwapIntervalMinutes());
		assertEquals(0, state.difficultyElapsedTicks);
	}

	@Test
	void 위치_교환을_안_적으면_꺼진_채로_새겨진다() {
		TeamState state = TeamState.fresh(20.0F);
		state.enablePositionSwap(7);

		TeamCreationSettings.defaults(20.0F).applyTo(state);

		assertFalse(state.positionSwapEnabled());
	}

	@Test
	void 상한을_올려도_만든_사람의_체력이_공짜로_차지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.health = 7.0F;

		TeamCreationSettings.defaults(20.0F).withMaxHealth(40.0F).applyTo(state);

		assertEquals(7.0F, state.health);
	}

	@Test
	void 상한을_내리면_현재_체력이_거기까지_깎인다() {
		TeamState state = TeamState.fresh(40.0F);
		state.health = 40.0F;

		TeamCreationSettings.defaults(40.0F).withMaxHealth(20.0F).applyTo(state);

		assertEquals(20.0F, state.health);
	}

	// ------------------------------------------------------------------ 값 검사

	@Test
	void 서버_설정이_이상해도_팀_만들기가_죽지_않는다() {
		assertEquals(20.0F, TeamCreationSettings.defaults(Float.NaN).maxHealth());
		assertEquals(1.0F, TeamCreationSettings.defaults(0.0F).maxHealth());
		assertEquals(1024.0F, TeamCreationSettings.defaults(99999.0F).maxHealth());
	}

	@Test
	void 명령이_거르지_못한_교환_주기는_예외로_알린다() {
		assertThrows(IllegalArgumentException.class,
				() -> TeamCreationSettings.defaults(20.0F).withSwapIntervalMinutes(121));
		assertThrows(IllegalArgumentException.class,
				() -> TeamCreationSettings.defaults(20.0F).withSwapIntervalMinutes(-1));
	}

	// ------------------------------------------------------------------ 안내 문구

	@Test
	void 막힌_설정마다_무엇이_막혔는지와_어떻게_바꾸는지가_함께_나간다() {
		for (TeamCreationSettings.Locked locked : TeamCreationSettings.Locked.values()) {
			String message = locked.message();
			assertTrue(message.contains("팀을 만들 때 정한 값이라 바꿀 수 없습니다"),
					locked + ": 왜 막혔는지가 있어야 한다");
			assertTrue(message.contains("/shareteam disband confirm"),
					locked + ": 그래도 바꾸려면 무엇을 해야 하는지가 있어야 한다");
			assertTrue(message.endsWith("."), locked + ": 존댓말 문장으로 끝나야 한다");
		}
	}

	@Test
	void 바꿀_수_없는_설정_넷이_모두_잠겨_있다() {
		// 개수로 못박으면 나중에 잠글 설정이 늘 때마다 이 시험이 애먼 이유로 깨진다.
		// 지금 반드시 잠겨 있어야 하는 넷이 들어 있는지만 본다.
		java.util.Set<TeamCreationSettings.Locked> locked =
				java.util.EnumSet.allOf(TeamCreationSettings.Locked.class);

		assertTrue(locked.contains(TeamCreationSettings.Locked.PERKS));
		assertTrue(locked.contains(TeamCreationSettings.Locked.MAX_HEALTH));
		assertTrue(locked.contains(TeamCreationSettings.Locked.POSITION_SWAP));
		assertTrue(locked.contains(TeamCreationSettings.Locked.DIFFICULTY));
	}

	@Test
	void 요약에_팀이_정한_항목들이_들어간다() {
		String summary = TeamCreationSettings.defaults(20.0F)
				.withDifficultyEscalation(true).withSwapIntervalMinutes(5).summary();

		assertTrue(summary.contains("증강: 켬"));
		assertTrue(summary.contains("피격 알림: 끔"));
		assertTrue(summary.contains("사망 알림: 끔"));
		assertTrue(summary.contains("최대 체력: 20"));
		assertTrue(summary.contains("위치 교환: 5분 주기"));
		assertTrue(summary.contains("난이도 상승: 켬"));
	}

	@Test
	void 소수점이_의미_없는_체력은_정수로_보여_준다() {
		assertEquals("20", TeamCreationSettings.trimZero(20.0F));
		assertEquals("24.5", TeamCreationSettings.trimZero(24.5F)
				.toLowerCase(Locale.ROOT));
	}

	// ------------------------------------------------------------------ 저장 왕복

	@Test
	void 난이도_상승_설정과_흐른_시간이_왕복_저장된다() {
		TeamState state = TeamState.fresh(20.0F);
		state.difficultyEscalationEnabled = true;
		state.difficultyElapsedTicks = 45000;

		TeamState round = decode(encode(state));

		assertTrue(round.difficultyEscalationEnabled);
		assertEquals(45000, round.difficultyElapsedTicks);
	}

	@Test
	void 난이도를_안_쓰는_팀은_difficulty_항목을_아예_저장하지_않는다() {
		CompoundTag encoded = encode(TeamState.fresh(20.0F));

		assertFalse(encoded.contains("difficulty"),
				"안 쓰면 저장 형태가 이 기능 도입 전과 같아야 한다");
	}

	@Test
	void difficulty_항목이_없는_기존_월드는_꺼진_채로_열린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.difficultyEscalationEnabled = true;
		state.difficultyElapsedTicks = 1200;
		state.xpLevel = 9;
		CompoundTag encoded = encode(state);
		assertTrue(encoded.contains("difficulty"), "켜 두었으면 저장에 있어야 한다");
		encoded.remove("difficulty");

		TeamState round = decode(encoded);

		assertFalse(round.difficultyEscalationEnabled);
		assertEquals(0, round.difficultyElapsedTicks);
		assertEquals(9, round.xpLevel, "난이도와 무관한 값은 그대로여야 한다");
	}

	@Test
	void 망가진_흐른_시간은_0_으로_되돌린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.difficultyElapsedTicks = -5;

		state.sanitize(20.0F);

		assertEquals(0, state.difficultyElapsedTicks);
	}
}
