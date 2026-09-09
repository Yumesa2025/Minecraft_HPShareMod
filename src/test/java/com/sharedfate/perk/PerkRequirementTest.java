package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강의 전제조건({@code requires})을 읽는 것과, 그것으로 후보를 거르는 것을 본다.
 *
 * <p>{@link PerkDraft} 는 팀 상태를 보지 않고 <b>호출자가 넘긴 조건 집합</b>만 본다. 그래서
 * 살아 있는 서버 없이 여기서 전부 확인할 수 있다. 팀 상태를 조건 집합으로 옮기는 한 줄
 * ({@link PerkSwapRules#satisfiedRequirements})도 함께 본다.
 */
class PerkRequirementTest {
	private static final long SEED = 20260909L;
	private static final int MILESTONE = 20;

	private static final Set<Perk.Requirement> 교환켜짐 = Set.of(Perk.Requirement.POSITION_SWAP);
	private static final Set<Perk.Requirement> 교환꺼짐 = Perk.Requirement.NONE;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 안_적으면_전제조건이_없다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertNull(byId("sharedfate:plain").requires(), "지금까지의 증강은 그대로 읽혀야 한다");
	}

	@Test
	void position_swap_을_읽는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertEquals(Perk.Requirement.POSITION_SWAP, byId("sharedfate:swapper").requires());
	}

	@Test
	void 모르는_값이면_그_증강만_버린다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertTrue(PerkRegistry.byId("sharedfate:typo").isEmpty(), "오타는 조용히 넘기지 않는다");
		assertTrue(PerkRegistry.byId("sharedfate:plain").isPresent(), "나머지는 그대로 읽힌다");
	}

	@Test
	void 문자열이_아니거나_비어_있어도_버린다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertTrue(PerkRegistry.byId("sharedfate:blank").isEmpty());
		assertTrue(PerkRegistry.byId("sharedfate:number").isEmpty());
	}

	@Test
	void json_null_은_안_적은_것으로_본다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertNull(byId("sharedfate:nulled").requires());
	}

	@Test
	void 대소문자와_공백은_봐준다() {
		assertEquals(Perk.Requirement.POSITION_SWAP, Perk.Requirement.fromId(" Position_Swap "));
		assertNull(Perk.Requirement.fromId("position swap"));
		assertNull(Perk.Requirement.fromId(null));
	}

	// ------------------------------------------------------------------ 후보 거르기

	@Test
	void 갖추지_못한_팀에게는_후보로_안_나온다() {
		List<String> drawn = PerkDraft.drawFor(PerkRarity.SILVER, MILESTONE, 섞인풀(), List.of(),
				교환꺼짐, RandomSource.create(SEED), 3);

		assertEquals(List.of("plain1", "plain2", "plain3"), sorted(drawn));
	}

	@Test
	void 갖춘_팀에게는_그대로_나온다() {
		List<String> drawn = PerkDraft.drawFor(PerkRarity.SILVER, MILESTONE, 섞인풀(), List.of(),
				교환켜짐, RandomSource.create(SEED), 4);

		assertEquals(4, drawn.size());
		assertTrue(drawn.contains("swap1"));
	}

	@Test
	void 조건_집합을_안_넘기면_지금까지와_똑같다() {
		// 기본 풀 86개가 하나도 안 버려지는 것이 다른 시험에 못박혀 있다. 옛 시그니처가 조용히
		// 거르기 시작하면 그 시험부터 무너진다.
		List<String> drawn = PerkDraft.draw(PerkRarity.SILVER, MILESTONE, 섞인풀(), List.of(),
				RandomSource.create(SEED), 4);

		assertEquals(4, drawn.size(), "전제조건이 붙은 것까지 그대로 후보다");
	}

	@Test
	void 등급_폴백으로도_새어_나오지_않는다() {
		// 프리즘을 요구했지만 프리즘은 전제조건이 붙은 것 하나뿐이다. 폴백으로 골드·실버까지
		// 내려가도 그 프리즘만은 끝까지 나오지 않아야 한다.
		List<Perk> pool = List.of(
				perk("prism_swap", PerkRarity.PRISM, Perk.Requirement.POSITION_SWAP),
				perk("plain1", PerkRarity.GOLD, null),
				perk("plain2", PerkRarity.SILVER, null));

		List<String> drawn = PerkDraft.drawFor(PerkRarity.PRISM, MILESTONE, pool, List.of(),
				교환꺼짐, RandomSource.create(SEED), 3);

		assertEquals(List.of("plain1", "plain2"), sorted(drawn));
	}

	@Test
	void 다시_뽑기에서도_걸린다() {
		// 회피 목록은 「되도록」이라 모자라면 다시 꺼내 쓰지만, 전제조건은 그렇지 않다.
		List<Perk> pool = List.of(
				perk("swap1", PerkRarity.SILVER, Perk.Requirement.POSITION_SWAP),
				perk("plain1", PerkRarity.SILVER, null));

		List<String> drawn = PerkDraft.drawFor(PerkRarity.SILVER, MILESTONE, pool, List.of(),
				List.of("plain1"), 교환꺼짐, RandomSource.create(SEED), 3);

		assertEquals(List.of("plain1"), drawn, "피하고 싶어도 나올 수 있는 것이 그것뿐이다");
	}

	@Test
	void 보유_증강과_최소_레벨은_그대로_함께_걸린다() {
		List<Perk> pool = List.of(
				perk("swap1", PerkRarity.SILVER, Perk.Requirement.POSITION_SWAP),
				perk("plain1", PerkRarity.SILVER, null),
				new Perk("late", "late", "", PerkRarity.SILVER, null, 30, List.of()));

		List<String> drawn = PerkDraft.drawFor(PerkRarity.SILVER, MILESTONE, pool,
				List.of("plain1"), 교환켜짐, RandomSource.create(SEED), 3);

		assertEquals(List.of("swap1"), drawn, "보유한 것도 min_level 이 높은 것도 함께 빠진다");
	}

	// ------------------------------------------------------------------ 팀 상태 → 조건 집합

	@Test
	void 위치_교환을_켠_팀만_조건을_갖춘다() {
		TeamState 켠팀 = TeamState.fresh(20.0F);
		켠팀.enablePositionSwap(5);
		TeamState 끈팀 = TeamState.fresh(20.0F);

		assertTrue(PerkSwapRules.satisfiedRequirements(켠팀).contains(Perk.Requirement.POSITION_SWAP));
		assertFalse(PerkSwapRules.satisfiedRequirements(끈팀).contains(Perk.Requirement.POSITION_SWAP));
		assertTrue(PerkSwapRules.satisfiedRequirements(null).isEmpty(),
				"팀을 모르면 아무것도 갖추지 못한 것으로 본다");
	}

	@Test
	void 교환을_껐다_켜면_조건도_따라온다() {
		TeamState state = TeamState.fresh(20.0F);
		state.enablePositionSwap(5);
		state.disablePositionSwap();

		assertTrue(PerkSwapRules.satisfiedRequirements(state).isEmpty());
	}

	// ------------------------------------------------------------------ 도우미

	private static Perk perk(String id, PerkRarity rarity, Perk.Requirement requires) {
		return new Perk(id, id, "", rarity, null, 0, List.of(), List.of(), requires);
	}

	/** 전제조건이 붙은 것 하나와 안 붙은 것 셋. 전부 실버다. */
	private static List<Perk> 섞인풀() {
		return List.of(
				perk("swap1", PerkRarity.SILVER, Perk.Requirement.POSITION_SWAP),
				perk("plain1", PerkRarity.SILVER, null),
				perk("plain2", PerkRarity.SILVER, null),
				perk("plain3", PerkRarity.SILVER, null));
	}

	private static List<String> sorted(List<String> ids) {
		return ids.stream().sorted().toList();
	}

	private static Perk byId(String id) {
		Optional<Perk> found = PerkRegistry.byId(id);
		assertTrue(found.isPresent(), id + " 를 읽지 못했다");
		return found.get();
	}

	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] },
				    { "id": "sharedfate:swapper", "rarity": "silver", "name": "교환 증강",
				      "requires": "position_swap",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] },
				    { "id": "sharedfate:typo", "rarity": "silver", "name": "오타",
				      "requires": "positionswap",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] },
				    { "id": "sharedfate:blank", "rarity": "silver", "name": "빈 값",
				      "requires": "   ",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] },
				    { "id": "sharedfate:number", "rarity": "silver", "name": "숫자",
				      "requires": 3,
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] },
				    { "id": "sharedfate:nulled", "rarity": "silver", "name": "널",
				      "requires": null,
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
