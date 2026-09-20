package com.sharedfate.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkStatusEffects;
import com.sharedfate.perk.effect.AbsorptionRechargeEffect;
import com.sharedfate.perk.effect.LifestealEffect;
import com.sharedfate.perk.effect.NoNaturalRegenEffect;
import com.sharedfate.perk.effect.StatusEffectPerk;
import com.sharedfate.team.TeamState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 프리즘 「흡혈귀」의 <b>충전되는 흡수 방패</b>({@code absorption_recharge})를 못박는다.
 *
 * <p>플레이어에게 실제로 상태이상을 붙였다 떼는 부분은 살아 있는 서버가 있어야 하므로 여기서
 * 다루지 않는다. 대신 그 코드가 내리는 <b>판단과 셈</b>을 전부 여기서 확인한다. 「지금이
 * 주기인가」·「공유 풀에 얼마를 적는가」·「팀원 넷의 변화가 어떻게 접히는가」 셋이 정해지면
 * 나머지는 그 답을 그대로 쓰는 일뿐이다.
 *
 * <p>{@code com.sharedfate.sync} 패키지에 두는 이유는 {@link StatMirror#fold} 와
 * {@link StatMirror#applyDeltas} 를 직접 부르기 위해서다. 「4인 팀에서 4배가 되지 않는다」는
 * 흉내 낸 셈이 아니라 <b>실제로 도는 그 코드</b>로 확인해야 뜻이 있다.
 */
class AbsorptionRechargeTest {

	/** 「흡혈귀」의 보호막. 흡수 II = 8(4하트), 5분(6000틱) 주기. */
	private static final String VAMPIRE_SHIELD = """
			{ "type": "absorption_recharge", "amplifier": 1, "period_ticks": 6000 }
			""";

	/** 방패 가득 찼을 때의 흡수량. 26.2 의 4 × (등급+1) 이다. */
	private static final float FULL = 8.0F;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		AbsorptionRechargeManager.reset();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 정의를_읽으면_방패_크기와_주기가_그대로_들어간다() {
		AbsorptionRechargeEffect effect = parse(VAMPIRE_SHIELD);

		assertEquals(1, effect.amplifier());
		assertEquals(6000, effect.periodTicks(), "5분");
		assertEquals(FULL, effect.shieldAmount(), "흡수 II 는 4 × (1+1) = 8 이다");
	}

	@Test
	void 등급이_범위를_벗어나면_정의를_버린다() {
		assertNull(create("""
				{ "type": "absorption_recharge", "amplifier": -1, "period_ticks": 6000 }
				"""), "음수 등급");
		assertNull(create("""
				{ "type": "absorption_recharge", "amplifier": 99, "period_ticks": 6000 }
				"""), "상한을 넘는 등급");
	}

	@Test
	void 주기가_없거나_범위를_벗어나면_정의를_버린다() {
		assertNull(create("""
				{ "type": "absorption_recharge", "amplifier": 1 }
				"""), "period_ticks 를 안 적었다");
		assertNull(create("""
				{ "type": "absorption_recharge", "amplifier": 1, "period_ticks": 0 }
				"""), "0 틱");
		assertNull(create("""
				{ "type": "absorption_recharge", "amplifier": 1, "period_ticks": 19 }
				"""), "1초보다 짧으면 「닳으면 다음 주기까지 버틴다」가 뜻을 잃는다");
		assertNull(create("""
				{ "type": "absorption_recharge", "amplifier": 1, "period_ticks": 720000 }
				"""), "한 시간을 넘는 주기");
	}

	/**
	 * 하위로 들어간 정의는 버린다.
	 *
	 * <p>{@code conditional}·{@code holder}·{@code periodic} 의 하위 효과는 순번이 100 이상이다.
	 * 매니저는 최상위 효과만 훑으므로 하위로 들어간 보호막은 아무도 충전해 주지 않는다.
	 * 조용히 멈춰 있느니 정의를 읽을 때 버리는 편이 낫다.
	 */
	@Test
	void 다른_효과의_하위로_들어가면_정의를_버린다() {
		JsonObject json = JsonParser.parseString(VAMPIRE_SHIELD).getAsJsonObject();

		assertNotNull(AbsorptionRechargeEffect.fromJson("test", 3, json), "최상위 순번은 받는다");
		assertNull(AbsorptionRechargeEffect.fromJson("test", 100, json), "하위 순번은 버린다");
		assertNull(AbsorptionRechargeEffect.fromJson("test", 10_099, json), "더 깊은 하위도 버린다");
	}

	/**
	 * {@link PerkEffectType} 에 <b>등록돼 있는가.</b>
	 *
	 * <p>등록을 빠뜨리면 이 효과를 가진 증강이 통째로 풀에서 사라지는데 빌드도 서버도 멀쩡하다.
	 * 이 저장소가 되풀이해 당한 자리라 이름으로 찾는 길까지 못박는다.
	 */
	@Test
	void 효과_타입이_이름으로_등록돼_있다() {
		PerkEffectType type = PerkEffectType.fromId("absorption_recharge");

		assertSame(PerkEffectType.ABSORPTION_RECHARGE, type, "type 문자열이 등록돼 있지 않다");
		assertInstanceOf(AbsorptionRechargeEffect.class,
				type.create("test", 0, JsonParser.parseString(VAMPIRE_SHIELD).getAsJsonObject()),
				"등록은 돼 있는데 팩토리가 다른 것을 만든다");
	}

	// ------------------------------------------------------------------ 기본 풀

	/**
	 * 번들 기본 풀의 「흡혈귀」가 <b>새 정의 그대로</b> 읽힌다.
	 *
	 * <p>등록을 빠뜨렸다면 여기서 「흡혈귀」 자체가 사라져 먼저 걸린다.
	 */
	@Test
	void 기본_풀의_흡혈귀는_흡혈과_보호막과_자연회복_금지를_함께_가진다(@TempDir Path dir) throws IOException {
		Perk vampire = loadVampire(dir);

		AbsorptionRechargeEffect shield = null;
		boolean lifesteal = false;
		boolean noRegen = false;
		for (PerkEffect effect : vampire.effects()) {
			if (effect instanceof AbsorptionRechargeEffect recharge) {
				shield = recharge;
			} else if (effect instanceof LifestealEffect) {
				lifesteal = true;
			} else if (effect instanceof NoNaturalRegenEffect) {
				noRegen = true;
			}
		}

		assertTrue(lifesteal, "흡혈이 사라졌다");
		assertTrue(noRegen, "자연 회복 금지가 사라졌다");
		assertNotNull(shield, "충전되는 보호막이 없다 — PerkEffectType 등록을 빠뜨리면 여기서 걸린다");
		assertEquals(6000, shield.periodTicks(), "5분마다 채운다");
		assertEquals(FULL, shield.shieldAmount(), "4하트");

		for (PerkEffect effect : vampire.effects()) {
			assertFalse(effect instanceof com.sharedfate.perk.effect.PeriodicEffect,
					"옛 periodic 정의가 남아 있다 — 구간이 끝나면 remove 가 보호막을 0 으로 만든다");
		}
	}

	/**
	 * 보호막이 <b>증강분 상태이상으로 잡힌다.</b>
	 *
	 * <p>{@code PerkStatusEffects} 가 이 흡수를 증강분으로 세지 못하면 {@code EffectSync} 가
	 * 그것을 {@code TeamState.effects} 로 퍼 나르고, 증강을 잃은 뒤에도 노란 하트가 되살아난다.
	 * 반대로 포션·황금 사과가 준 <b>유한 지속</b> 흡수는 평소대로 팀에 공유돼야 한다.
	 */
	@Test
	void 보호막은_팀_공유_상태이상_풀로_새어_나가지_않는다(@TempDir Path dir) throws IOException {
		loadVampire(dir);
		TeamState state = TeamState.fresh(40.0F);
		state.ownedPerks.add("sharedfate:vampire");

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		assertTrue(perkEffects.covers(MobEffects.ABSORPTION), "흡수를 증강이 거는 종류로 세지 않는다");
		assertTrue(perkEffects.grants(new MobEffectInstance(MobEffects.ABSORPTION,
						MobEffectInstance.INFINITE_DURATION, 1, false, false, true)),
				"증강이 건 무한 흡수를 제 것으로 알아보지 못한다");
		assertFalse(perkEffects.grants(new MobEffectInstance(MobEffects.ABSORPTION,
						160, 1, false, false, true)),
				"황금 사과가 준 유한 흡수까지 증강분으로 보면 팀 공유가 끊긴다");
		assertTrue(perkEffects.shareable(List.of(new MobEffectInstance(MobEffects.ABSORPTION,
						MobEffectInstance.INFINITE_DURATION, 1, false, false, true))).isEmpty(),
				"증강분이 공유 목록에 섞였다");
	}

	@Test
	void 보호막은_흡수_상태이상을_품는다() {
		List<StatusEffectPerk> inner = parse(VAMPIRE_SHIELD).statusEffects();

		assertEquals(1, inner.size());
		assertEquals(AbsorptionRechargeEffect.ABSORPTION, inner.get(0).effectId());
		assertEquals(1, inner.get(0).amplifier());
	}

	// ------------------------------------------------------------------ 주기 판정

	@Test
	void 주기_번호는_게임_시간을_주기로_나눈_몫이다() {
		assertEquals(0L, AbsorptionRechargeManager.cycleAt(0L, 6000));
		assertEquals(0L, AbsorptionRechargeManager.cycleAt(5999L, 6000));
		assertEquals(1L, AbsorptionRechargeManager.cycleAt(6000L, 6000));
		// 게임 시간이 음수로 조작된 월드에서도 경계가 어긋나지 않아야 한다.
		assertEquals(-1L, AbsorptionRechargeManager.cycleAt(-1L, 6000));
	}

	/**
	 * 처음 보는 팀은 <b>곧바로</b> 채운다.
	 *
	 * <p>{@link ShockwaveManager#advance} 와 일부러 다르다. 방패는 「받는 것」이라 증강을 고르고
	 * 5분을 빈손으로 기다리면 증강이 고장 난 것으로 보인다.
	 */
	@Test
	void 처음_보는_팀은_곧바로_채우고_같은_주기_안에서는_다시_채우지_않는다() {
		UUID team = UUID.randomUUID();

		assertTrue(AbsorptionRechargeManager.advance(team, 6000, 100L), "처음 보는 팀");
		assertFalse(AbsorptionRechargeManager.advance(team, 6000, 101L), "같은 주기");
		assertFalse(AbsorptionRechargeManager.advance(team, 6000, 5999L), "아직 같은 주기");
		assertTrue(AbsorptionRechargeManager.advance(team, 6000, 6000L), "경계를 넘었다");
		assertFalse(AbsorptionRechargeManager.advance(team, 6000, 6001L), "넘은 직후");
	}

	@Test
	void 주기_길이가_바뀌면_번호를_새로_잡는다() {
		UUID team = UUID.randomUUID();

		AbsorptionRechargeManager.advance(team, 6000, 0L);
		assertTrue(AbsorptionRechargeManager.advance(team, 1200, 0L),
				"주기가 달라지면 번호의 뜻도 달라진다");
	}

	// ------------------------------------------------------------------ 공유 풀 셈

	@Test
	void 주기가_오면_절반_닳은_방패도_최대치로_채워진다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 4.0F;

		assertTrue(AbsorptionRechargeManager.fill(state, FULL));

		assertEquals(FULL, state.absorption);
	}

	@Test
	void 다_닳은_방패도_주기가_오면_최대치로_채워진다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 0.0F;

		assertTrue(AbsorptionRechargeManager.fill(state, FULL));

		assertEquals(FULL, state.absorption);
	}

	@Test
	void 이미_가득_차_있으면_아무_일도_하지_않는다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = FULL;

		assertFalse(AbsorptionRechargeManager.fill(state, FULL), "바뀐 것이 없으면 되쓰기도 없어야 한다");
		assertEquals(FULL, state.absorption);
	}

	/** 황금 사과로 더 큰 보호막을 두르고 있으면 「채운다」가 그것을 깎아 내리면 안 된다. */
	@Test
	void 더_큰_보호막을_깎아_내리지_않는다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 12.0F;

		assertFalse(AbsorptionRechargeManager.fill(state, FULL));

		assertEquals(12.0F, state.absorption);
	}

	/**
	 * <b>이 시험이 이 파일의 핵심이다.</b> 4인 팀에서 흡수가 4배로 불어나지 않는다.
	 *
	 * <p>두 겹을 함께 본다.
	 *
	 * <ol>
	 *   <li>{@link AbsorptionRechargeManager#fill} 이 더하지 않고 {@code max} 를 쓴다 —
	 *       실수로 팀원 수만큼 불려도 답이 8 이다.</li>
	 *   <li>보호막 상태이상이 붙는 순간 바닐라가 팀원 <b>각자의</b> 흡수량을 0 → 8 로 올리는데,
	 *       {@link StatMirror#fold} 가 그 획득을 합산하지 않고 최댓값만 취한다 — 그래서 공유
	 *       풀에 들어가는 획득도 8 이다.</li>
	 * </ol>
	 *
	 * <p>어느 한 겹이라도 무너지면 4인 팀의 흡수가 32 가 된다.
	 */
	@Test
	void 사인_팀에서_흡수가_네_배로_불어나지_않는다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = 0.0F;

		// 1겹: 팀원 수만큼 불려도 팀 값은 8 그대로다.
		for (int member = 0; member < 4; member++) {
			AbsorptionRechargeManager.fill(state, FULL);
		}
		assertEquals(FULL, state.absorption, "fill 이 더하기였다면 32 가 된다");

		// 2겹: 네 명의 흡수량이 동시에 0 → 8 로 뛰어도 공유 풀의 획득은 8 이다.
		List<StatMirror.PlayerDelta> deltas = new ArrayList<>();
		for (int member = 0; member < 4; member++) {
			deltas.add(new StatMirror.PlayerDelta(0.0F, FULL, 0.0F, 0, 0.0F, 0));
		}
		StatMirror.StatDelta folded = StatMirror.fold(deltas);
		assertEquals(FULL, folded.absorptionGain(), "획득을 합산하면 32 가 된다");

		TeamState mirrored = TeamState.fresh(40.0F);
		StatMirror.applyDeltas(mirrored, 40.0F, FULL, folded, false);
		assertEquals(FULL, mirrored.absorption);
	}

	/**
	 * 주기 사이에 다 닳으면 다음 주기까지 0 으로 남는다.
	 *
	 * <p>{@code AbsorptionMobEffect} 는 흡수량이 0 이 되면 스스로 사라지고, 그러면
	 * {@code max_absorption} 도 0 이 된다. 그 상태에서는 {@link StatMirror#applyDeltas} 가
	 * 무엇을 받아도 공유 흡수를 0 으로 자른다. 매니저가 다음 경계까지 다시 붙이지 않으므로
	 * 방패가 저절로 돌아오는 길이 없다.
	 */
	@Test
	void 주기_사이에_다_닳으면_다음_주기까지_0으로_남는다() {
		UUID team = UUID.randomUUID();
		TeamState state = TeamState.fresh(40.0F);
		AbsorptionRechargeManager.advance(team, 6000, 0L);
		AbsorptionRechargeManager.fill(state, FULL);

		// 맞는 순간에는 아직 상태이상이 붙어 있다. 8 을 한꺼번에 소비해 방패가 다 닳는다.
		StatMirror.applyDeltas(state, 40.0F, FULL,
				new StatMirror.StatDelta(0.0F, 0.0F, -FULL, 0.0F, 0, 0.0F, 0), false);
		assertEquals(0.0F, state.absorption);
		assertEquals(40.0F, state.health, "방패가 막아 냈으므로 체력은 그대로다");

		// 같은 주기 안에서는 매니저가 경계라고 답하지 않는다 → 채우는 길이 없다.
		for (long time = 1L; time < 6000L; time += 500L) {
			assertFalse(AbsorptionRechargeManager.advance(team, 6000, time),
					time + "틱에 주기가 아닌데 경계라고 답한다");
		}
		// 최대 흡수량이 0 인 동안에는 무슨 일이 있어도 공유 흡수가 살아나지 않는다.
		StatMirror.applyDeltas(state, 40.0F, 0.0F,
				new StatMirror.StatDelta(0.0F, 0.0F, 0.0F, FULL, 0, 0.0F, 0), false);
		assertEquals(0.0F, state.absorption, "상태이상이 없으면 흡수량은 0 으로 잘린다");

		// 다음 경계가 오면 그때 비로소 다시 찬다.
		assertTrue(AbsorptionRechargeManager.advance(team, 6000, 6000L));
		assertTrue(AbsorptionRechargeManager.fill(state, FULL));
		assertEquals(FULL, state.absorption);
	}

	// ------------------------------------------------------------------ 증강을 잃을 때

	/**
	 * 「환골탈태」로 흡혈귀를 잃으면 흡수가 정리된다.
	 *
	 * <p>증강이 사라지면 매니저는 팀원에게 붙은 보호막 상태이상을 걷어내고, 남은 공유 흡수를
	 * 그때의 최대 흡수량까지 내린다. 걷어내지 않으면 상태이상은 사람 쪽에 적혀 있어 팀 상태를
	 * 버려도 그대로 남고, 공유 풀만 남겨 두면 화면에는 노란 하트가 있는데 피해를 하나도 막지
	 * 못하는 상태가 된다.
	 */
	@Test
	void 흡혈귀를_잃으면_공유_흡수가_정리된다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = FULL;

		assertTrue(AbsorptionRechargeManager.trimToCapacity(state, 0.0F),
				"상태이상을 걷어냈으면 공유 흡수도 내려가야 한다");
		assertEquals(0.0F, state.absorption);
	}

	/** 다른 원인으로 흡수가 남아 있으면 그만큼은 남긴다. 황금 사과까지 빼앗지 않는다. */
	@Test
	void 증강을_잃어도_남의_보호막까지_빼앗지는_않는다() {
		TeamState state = TeamState.fresh(40.0F);
		state.absorption = FULL;

		assertTrue(AbsorptionRechargeManager.trimToCapacity(state, 4.0F));
		assertEquals(4.0F, state.absorption, "유한 지속 흡수가 준 몫은 남아야 한다");
		assertFalse(AbsorptionRechargeManager.trimToCapacity(state, 4.0F), "두 번째는 바뀔 것이 없다");
	}

	@Test
	void 팀의_주기_기억은_지울_수_있다() {
		UUID team = UUID.randomUUID();
		AbsorptionRechargeManager.advance(team, 6000, 0L);
		assertTrue(AbsorptionRechargeManager.tracks(team));

		AbsorptionRechargeManager.forget(team);

		assertFalse(AbsorptionRechargeManager.tracks(team));
		assertTrue(AbsorptionRechargeManager.advance(team, 6000, 1L),
				"기억을 지웠으면 다시 「처음 보는 팀」이라 곧바로 채운다");
	}

	// ------------------------------------------------------------------ 후보 고르기

	@Test
	void 후보가_여럿이면_방패가_큰_것_하나만_고른다() {
		AbsorptionRechargeEffect small = parse("""
				{ "type": "absorption_recharge", "amplifier": 0, "period_ticks": 6000 }
				""");
		AbsorptionRechargeEffect big = parse(VAMPIRE_SHIELD);

		assertSame(big, AbsorptionRechargeManager.select(List.of(small, big)));
		assertSame(big, AbsorptionRechargeManager.select(List.of(big, small)));
		assertNull(AbsorptionRechargeManager.select(List.of()));
		assertNull(AbsorptionRechargeManager.select(null));
	}

	// ------------------------------------------------------------------ 도우미

	private static AbsorptionRechargeEffect parse(String json) {
		return assertInstanceOf(AbsorptionRechargeEffect.class, create(json),
				"정의를 읽지 못했다");
	}

	private static PerkEffect create(String json) {
		return AbsorptionRechargeEffect.fromJson(
				"test", 0, JsonParser.parseString(json).getAsJsonObject());
	}

	private static Perk loadVampire(Path dir) throws IOException {
		Path target = dir.resolve(PerkRegistry.FILE_NAME);
		if (!Files.exists(target)) {
			try (InputStream bundled = AbsorptionRechargeTest.class
					.getResourceAsStream("/sharedfate-perks-default.json")) {
				Files.copy(bundled, target);
			}
			PerkRegistry.load(dir);
		}
		return PerkRegistry.byId("sharedfate:vampire")
				.orElseThrow(() -> new AssertionError(
						"흡혈귀가 풀에서 사라졌다 — PerkEffectType 등록을 빠뜨리면 증강이 통째로 버려진다"));
	}
}
