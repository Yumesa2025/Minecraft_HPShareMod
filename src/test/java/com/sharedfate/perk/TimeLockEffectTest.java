package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.perk.effect.TimeLockEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code time_lock} 의 정의 읽기와, 시각을 어디로 돌려놓을지 정하는 산술을 본다.
 *
 * <p>시계를 실제로 돌려놓는 자리({@code ServerClockManager.setTotalTicks})는 살아 있는 서버가
 * 있어야 확인할 수 있다. 여기서는 그 자리에 넘길 값을 정하는
 * {@link PerkWorldRules#lockedDayTime} 과 {@link PerkWorldRules#lockedTotalTicks} 만 본다.
 */
class TimeLockEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 시각을_읽는다() {
		TimeLockEffect effect = create("{ \"type\": \"time_lock\", \"time\": 18000 }");

		assertEquals(18000, effect.time());
	}

	@Test
	void 하루의_양_끝도_받는다() {
		assertEquals(0, create("{ \"type\": \"time_lock\", \"time\": 0 }").time());
		assertEquals(23999, create("{ \"type\": \"time_lock\", \"time\": 23999 }").time());
	}

	@Test
	void time_이_없으면_버린다() {
		// 기본값을 정해 두면 오타로 필드가 빠진 정의가 조용히 엉뚱한 시각으로 세계를 얼린다.
		assertNull(raw("{ \"type\": \"time_lock\" }"));
		assertNull(raw("{ \"type\": \"time_lock\", \"time\": \"자정\" }"));
	}

	@Test
	void 범위를_벗어나면_버린다() {
		assertNull(raw("{ \"type\": \"time_lock\", \"time\": -1 }"));
		assertNull(raw("{ \"type\": \"time_lock\", \"time\": 24000 }"));
		assertNull(raw("{ \"type\": \"time_lock\", \"time\": 100000 }"));
	}

	@Test
	void 하위_효과로는_넣을_수_없다() {
		assertNull(raw("{ \"type\": \"time_lock\", \"time\": 18000 }",
				OnKillEffect.nestedIndex(0, 0)));
	}

	// ------------------------------------------------------------------ 팀 판정

	@Test
	void 증강이_없으면_시각을_고정하지_않는다() {
		assertTrue(PerkWorldRules.lockedDayTime(null).isEmpty());
		assertTrue(PerkWorldRules.lockedDayTime(TeamState.fresh(20.0F)).isEmpty());
	}

	@Test
	void 이_효과를_가진_증강이_있으면_그_시각을_돌려준다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(moonlightPool(dir));

		assertEquals(OptionalInt.of(18000),
				PerkWorldRules.lockedDayTime(owning("sharedfate:moonlight")));
	}

	@Test
	void 다른_증강만_있으면_고정하지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(moonlightPool(dir));

		assertTrue(PerkWorldRules.lockedDayTime(owning("sharedfate:night_eyes")).isEmpty(),
				"status_effect 만 가진 팀의 시간은 평소대로 흐른다");
	}

	@Test
	void 여러_개면_가장_작은_값이_이긴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(moonlightPool(dir));

		TeamState state = owning("sharedfate:moonlight");
		state.ownedPerks.add("sharedfate:eternal_noon");

		// 보유 순서에 따라 답이 달라지면 같은 증강을 가진 팀이 서로 다른 시각에 갇힌다.
		assertEquals(OptionalInt.of(6000), PerkWorldRules.lockedDayTime(state));
	}

	@Test
	void 증강이_꺼져_있으면_고정하지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(moonlightPool(dir));

		TeamState state = owning("sharedfate:moonlight");
		state.perksEnabled = false;

		assertTrue(PerkWorldRules.lockedDayTime(state).isEmpty());
	}

	// ------------------------------------------------------------------ 되돌릴 값 계산

	@Test
	void 날짜를_지우지_않고_하루_안에서만_옮긴다() {
		// 7일째 아침(7 * 24000 + 1000)에서 자정으로 붙들면 7일째 자정이어야 한다.
		assertEquals(7L * 24000 + 18000, PerkWorldRules.lockedTotalTicks(7L * 24000 + 1000, 18000));
	}

	@Test
	void 이미_지난_시각이면_같은_날_안에서_되돌린다() {
		// 자정을 20틱 지났으면 20틱 되돌린다. 다음 날로 넘기지 않는다.
		assertEquals(3L * 24000 + 18000, PerkWorldRules.lockedTotalTicks(3L * 24000 + 18020, 18000));
	}

	@Test
	void 첫날에도_그대로_동작한다() {
		assertEquals(18000L, PerkWorldRules.lockedTotalTicks(0L, 18000));
		assertEquals(0L, PerkWorldRules.lockedTotalTicks(12345L, 0));
	}

	@Test
	void 이미_맞아_있으면_같은_값을_돌려준다() {
		// tick 은 이 값이 지금 값과 같으면 시계를 아예 건드리지 않는다.
		long now = 42L * 24000 + 18000;
		assertEquals(now, PerkWorldRules.lockedTotalTicks(now, 18000));
	}

	// ------------------------------------------------------------------ 도우미

	private static TeamState owning(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	private static TimeLockEffect create(String json) {
		return assertInstanceOf(TimeLockEffect.class, raw(json));
	}

	private static PerkEffect raw(String json) {
		return raw(json, 0);
	}

	private static PerkEffect raw(String json, int index) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.TIME_LOCK.create("sharedfate:테스트", index, parsed);
	}

	/** 달빛이면 충분해(status_effect + time_lock), 야간 투시만, 정오 고정 세 가지를 담은 풀. */
	private static Path moonlightPool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:moonlight", "rarity": "gold", "name": "달빛이면 충분해",
				      "effects": [
				        { "type": "status_effect", "effect": "minecraft:night_vision" },
				        { "type": "time_lock", "time": 18000 }
				      ] },
				    { "id": "sharedfate:night_eyes", "rarity": "silver", "name": "밤눈",
				      "effects": [
				        { "type": "status_effect", "effect": "minecraft:night_vision" }
				      ] },
				    { "id": "sharedfate:eternal_noon", "rarity": "gold", "name": "영원한 정오",
				      "effects": [
				        { "type": "time_lock", "time": 6000 }
				      ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
