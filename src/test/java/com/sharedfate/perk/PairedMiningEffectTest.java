package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.PairedMiningEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code paired_mining}(실버 「공명」)의 정의 읽기와, {@link PerkResonantMining}의 순수 계산
 * 부분(짝 성사 기억 판정, 보유 여부 판정)을 본다.
 *
 * <p>실제로 캘 때 성급함이 걸리는지, 1초마다 혼자 페널티가 붙었다 떨어지는지는 살아 있는
 * 서버·{@code ServerPlayer}가 있어야 확인할 수 있어({@code PositionSwapManagerTest}와 같은
 * 이유) 여기서 다루지 않는다.
 */
class PairedMiningEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkResonantMining.reset();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 필드가_없고_인스턴스를_돌려쓴다() {
		PerkEffect first = create("{ \"type\": \"paired_mining\" }");
		PerkEffect second = create("{ \"type\": \"paired_mining\" }");

		assertSame(PairedMiningEffect.INSTANCE, first);
		assertSame(first, second);
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.PAIRED_MINING, PerkEffectType.fromId("paired_mining"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		PairedMiningEffect effect =
				assertInstanceOf(PairedMiningEffect.class, create("{ \"type\": \"paired_mining\" }"));

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	@Test
	void 확정된_값을_그대로_쓴다() {
		assertEquals(16.0, PairedMiningEffect.DISTANCE, 1.0e-9);
		assertEquals(100, PairedMiningEffect.MEMORY_TICKS, "5초 = 100틱");
		assertEquals(0, PairedMiningEffect.HASTE_AMPLIFIER, "성급함 I");
		assertEquals(100, PairedMiningEffect.HASTE_TICKS, "5초 = 100틱");
		assertEquals(-0.15, PairedMiningEffect.SOLO_PENALTY_MULTIPLIER, 1.0e-9);
	}

	// ------------------------------------------------------------------ 짝 성사 기억 판정

	@Test
	void 같은_블록을_5초_안에_캤으면_짝이_성사된다() {
		PerkResonantMining.LastBreak record = new PerkResonantMining.LastBreak(Blocks.STONE, 1000L);

		assertTrue(PerkResonantMining.recordMatches(record, Blocks.STONE, 1000L + 100L),
				"경계값(정확히 5초)도 성사돼야 한다");
		assertTrue(PerkResonantMining.recordMatches(record, Blocks.STONE, 1050L));
	}

	@Test
	void 다른_블록이면_짝이_안_된다() {
		PerkResonantMining.LastBreak record = new PerkResonantMining.LastBreak(Blocks.STONE, 1000L);

		assertFalse(PerkResonantMining.recordMatches(record, Blocks.DIRT, 1010L));
	}

	@Test
	void 오초가_지나면_기억이_만료된다() {
		PerkResonantMining.LastBreak record = new PerkResonantMining.LastBreak(Blocks.STONE, 1000L);

		assertFalse(PerkResonantMining.recordMatches(record, Blocks.STONE, 1000L + 101L));
	}

	@Test
	void 기록이_없으면_짝이_안_된다() {
		assertFalse(PerkResonantMining.recordMatches(null, Blocks.STONE, 1000L));
	}

	// ------------------------------------------------------------------ 보유 여부 판정

	@Test
	void 공명을_가진_팀만_참이다(@TempDir Path dir) throws IOException {
		write(dir, """
				{ "perks": [
				  { "id": "sharedfate:resonance", "rarity": "silver", "name": "공명",
				    "effects": [ { "type": "paired_mining" } ] },
				  { "id": "sharedfate:etc", "rarity": "silver", "name": "다른것",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				] }
				""");
		PerkRegistry.load(dir);

		TeamState withPerk = TeamState.fresh(20.0F);
		withPerk.perksEnabled = true;
		withPerk.ownedPerks.add("sharedfate:resonance");
		assertTrue(PerkResonantMining.hasPairedMining(withPerk));

		TeamState withoutPerk = TeamState.fresh(20.0F);
		withoutPerk.perksEnabled = true;
		withoutPerk.ownedPerks.add("sharedfate:etc");
		assertFalse(PerkResonantMining.hasPairedMining(withoutPerk));

		assertFalse(PerkResonantMining.hasPairedMining(TeamState.fresh(20.0F)));
	}

	@Test
	void reset_은_안전하다() {
		assertDoesNotThrow(PerkResonantMining::reset);
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect create(String json) {
		com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.PAIRED_MINING.create("sharedfate:테스트", 0, parsed);
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
