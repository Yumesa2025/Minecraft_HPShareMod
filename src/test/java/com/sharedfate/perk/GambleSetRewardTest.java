package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.ExtraRerollsEffect;
import com.sharedfate.perk.effect.PrismRerollEffect;
import com.sharedfate.team.TeamCreationSettings;
import com.sharedfate.team.TeamState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 세트 「도박」 2·3단계.
 *
 * <ul>
 *   <li><b>2단계 「한 판 더」</b> — 이번 회차에만 다시 뽑기 5회를 더 받는다</li>
 *   <li><b>3단계 「어차피 프리즘」</b> — 다시 뽑으면 프리즘 등급만 나오고, <b>다시 뽑기 3회를
 *       더 받는다</b>(0.24.0-dev 부터). 단계는 누적이라 셋을 모은 팀의 몫은 8 이다</li>
 * </ul>
 *
 * <p>⚠ <b>여기 쓰는 세트 정의({@link #SETS})의 3단계에는 {@code extra_rerolls} 가 없다.</b>
 * 실제 번들 정의에는 있다. 그래서 이 파일은 <b>3단계의 +3 을 확인하지 않는다</b> — 그 몫이
 * 통째로 사라져도 여기서는 아무 일도 안 일어난다. 실제 정의로 재면 도박 셋에 회차당 11회다.
 *
 * <h2>이 시험이 지키는 것</h2>
 * <p>2단계는 세트 보상 중 <b>유일하게 재계산형이 아니다.</b> 다른 보상은 보유 증강에서 매번 다시
 * 세는 파생 상태지만, 다시 뽑기 횟수는 소비되며 줄어드는 값이라 저장된 {@code rerollsRemaining}
 * 을 실제로 늘려야 한다. 거기서 두 가지가 깨지기 쉽다.
 *
 * <ol>
 *   <li><b>저장 왕복</b> — {@code TeamState} 의 클램프 두 곳이 남은 횟수를 회차당 허용치로
 *       잘라 왔다. 세트로 얻은 몫을 함께 저장하지 않으면 서버를 껐다 켜는 순간 3 으로
 *       돌아간다. 이 파일에서 가장 중요한 시험이다.</li>
 *   <li><b>중복 지급</b> — 지급을 {@code PerkManager.refreshPlayer} 같은 곳에 두면 접속할
 *       때마다 5씩 불어난다. 그래서 {@code PerkGrantChain.run} 안에서만 하고, 그마저도
 *       「더한다」가 아니라 「맞춘다」로 되어 있다.</li>
 * </ol>
 *
 * <p>서버가 필요한 자리({@code PerkManager.applyReroll})는 여기서 부를 수 없다. 그래서 3단계는
 * 판정({@link PrismRerollEffect#heldBy})과 「프리즘이 모자라면 적게 준다」는 결정
 * ({@link PerkManager#onlyPrism})을 따로 확인한다.
 */
class GambleSetRewardTest {
	/** 세트가 켜지는 데 필요한 도박 증강 두 개. 둘 다 효과는 없고 유형만 있다. */
	private static final String PERKS = """
			{ "perks": [
			  { "id": "sharedfate:gamble_a", "rarity": "silver", "name": "도박가",
			    "set_types": [ "gamble" ],
			    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
			  { "id": "sharedfate:gamble_b", "rarity": "silver", "name": "도박나",
			    "set_types": [ "gamble" ],
			    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
			  { "id": "sharedfate:plain", "rarity": "silver", "name": "무유형",
			    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
			] }
			""";

	/** 도박 2·3단계에 실제로 붙일 정의. 세트 JSON 에 넣을 내용과 같은 모양이다. */
	private static final String SETS = """
			{ "sets": [
			  { "type": "gamble", "tiers": [
			    { "count": 2, "name": "한 판 더", "description": "다시 뽑기 횟수를 5회 받습니다.",
			      "effects": [ { "type": "extra_rerolls", "amount": 5 } ] },
			    { "count": 3, "name": "어차피 프리즘", "description": "다시 뽑으면 프리즘만 나옵니다.",
			      "effects": [ { "type": "prism_reroll" } ] }
			  ] }
			] }
			""";

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
	}

	// ------------------------------------------------------------------ 등록 확인

	/**
	 * 두 효과 타입이 {@link PerkEffectType} 에 등록되어 있다.
	 *
	 * <p>등록을 빠뜨리면 <b>빌드도 통과하고 서버도 뜨는데</b> 그 단계만 조용히 사라진다.
	 */
	@Test
	void 두_효과_타입이_등록되어_있다() {
		assertNotNull(PerkEffectType.fromId("extra_rerolls"),
				"PerkEffectType 에 EXTRA_REROLLS(\"extra_rerolls\", ExtraRerollsEffect::fromJson) 가 없다");
		assertNotNull(PerkEffectType.fromId("prism_reroll"),
				"PerkEffectType 에 PRISM_REROLL(\"prism_reroll\", PrismRerollEffect::fromJson) 가 없다");
	}

	// ------------------------------------------------------------------ 도박 2 — 지급

	@Test
	void 도박_증강_두_개를_모으면_이번_회차_리롤권이_여덟_번이_된다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		assertEquals(3, state.rerollsRemaining, "받기 전에는 회차당 기본 3회다");

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		assertEquals(5, state.rerollSetBonus);
		assertEquals(8, state.rerollsRemaining);
	}

	@Test
	void 도박_증강이_하나뿐이면_아무_일도_없다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();

		grant(state, "sharedfate:gamble_a");

		assertEquals(0, state.rerollSetBonus);
		assertEquals(3, state.rerollsRemaining);
	}

	@Test
	void 세트를_안_쓰는_서버에서는_아무것도_달라지지_않는다(@TempDir Path dir) throws IOException {
		// 세트 정의 파일이 없는 서버. 도박 증강을 몇 개를 모으든 회차당 3회 그대로다.
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), PERKS, StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		TeamState state = team();

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		assertEquals(0, state.rerollSetBonus);
		assertEquals(3, state.rerollsRemaining);
	}

	// ------------------------------------------------------------------ 도박 2 — 중복 지급

	/**
	 * 접속을 몇 번을 하든, 증강을 몇 개를 더 고르든 5회는 <b>한 번만</b> 붙는다.
	 *
	 * <p>지급 자리가 {@code PerkGrantChain.run} 이고 그 안에서 「맞춘다」를 하기 때문에, 그
	 * 자리를 몇 번 지나든 결과가 같다. 이 시험이 깨지면 접속할 때마다 횟수가 불어난다.
	 */
	@Test
	void 몇_번을_다시_지나가도_두_번_지급되지_않는다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");
		assertEquals(8, state.rerollsRemaining);

		// 접속·부활·다음 증강 선택으로 이 자리를 몇 번이고 다시 지난다.
		for (int i = 0; i < 5; i++) {
			PerkGrantChain.run(null, null, state,
					PerkRegistry.byId("sharedfate:gamble_b").orElseThrow(), RandomSource.create(1L));
		}
		// 세트와 상관없는 증강을 하나 더 골라도 마찬가지다.
		grant(state, "sharedfate:plain");

		assertEquals(5, state.rerollSetBonus);
		assertEquals(8, state.rerollsRemaining, "5회는 회차에 한 번만 붙는다");
	}

	@Test
	void 이미_써_버린_횟수는_되살아나지_않는다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		state.rerollsRemaining = 1; // 세 번 중 두 번을 이미 썼다.

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		assertEquals(6, state.rerollsRemaining, "쓴 것은 쓴 것이고, 세트 몫 5회가 그 위에 얹힌다");
	}

	// ------------------------------------------------------------------ 도박 2 — 저장 왕복

	/**
	 * <b>이 파일에서 가장 깨지기 쉬운 자리다.</b>
	 *
	 * <p>{@code TeamState.sanitize} 와 {@code applyRerollSection} 이 남은 횟수를 회차당 허용치로
	 * 자른다. 세트로 얻은 몫이 저장에 함께 들어가지 않으면 여기서 8회가 3회로 돌아간다.
	 */
	@Test
	void 세트로_받은_리롤권이_월드_저장을_왕복한다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		TeamState round = decode(encode(state));

		assertEquals(5, round.rerollSetBonus);
		assertEquals(8, round.rerollsRemaining, "저장 클램프가 허용치 3 으로 잘라서는 안 된다");
	}

	@Test
	void 저장을_여러_번_왕복해도_늘지도_줄지도_않는다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");
		state.rerollsRemaining = 6; // 두 번 썼다.

		TeamState round = decode(encode(decode(encode(state))));

		assertEquals(5, round.rerollSetBonus);
		assertEquals(6, round.rerollsRemaining);
	}

	@Test
	void 세트를_안_모은_팀은_setBonus_항목을_아예_저장하지_않는다() {
		// 이 항목이 생기기 전과 저장 형태가 같아야 예전 서버도 나머지를 그대로 읽는다.
		TeamState state = TeamState.fresh(20.0F);
		state.rerollAllowance = 5;
		state.rerollsRemaining = 2;

		CompoundTag encoded = encode(state);

		assertTrue(encoded.contains("reroll"), "허용치가 기본과 다르면 묶음 자체는 있어야 한다");
		assertFalse(encoded.getCompound("reroll").orElseThrow().contains("setBonus"));
	}

	@Test
	void setBonus_항목이_없는_기존_월드는_예전처럼_허용치로_잘린다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");
		CompoundTag encoded = encode(state);
		encoded.getCompound("reroll").orElseThrow().remove("setBonus");

		TeamState round = decode(encoded);

		assertEquals(0, round.rerollSetBonus);
		assertEquals(3, round.rerollsRemaining, "몫을 모르면 예전 규칙 그대로 허용치가 상한이다");
	}

	@Test
	void 손상된_몫은_조용히_접힌다() {
		TeamState state = TeamState.fresh(20.0F);
		state.rerollSetBonus = 999;
		state.rerollsRemaining = 999;

		state.sanitize(20.0F);

		assertEquals(TeamCreationSettings.MAX_REROLL_COUNT - 3, state.rerollSetBonus);
		assertEquals(TeamCreationSettings.MAX_REROLL_COUNT, state.rerollsRemaining);

		state.rerollSetBonus = -5;
		state.sanitize(20.0F);
		assertEquals(0, state.rerollSetBonus);
		assertEquals(3, state.rerollsRemaining);
	}

	// ------------------------------------------------------------------ 도박 2 — 되돌아가기

	/**
	 * 회차가 넘어가면 3회로 돌아간다.
	 *
	 * <p>회차를 넘기는 길은 둘이고, 둘 다 남은 횟수를 회차당 허용치로 되돌린다. 전멸로 팀
	 * 상태를 새로 만드는 길({@code TeamManager.restoreFreshRoster})은 {@code TeamState.fresh}
	 * 라 몫이 0 에서 시작하고, 같은 월드에서 이어 가는 길({@code GameStartManager} 의 회차 값
	 * 초기화)은 {@code rerollsRemaining = rerollAllowance} 로 세트 몫을 통째로 버린다.
	 * 여기서는 뒤쪽을 그대로 흉내 낸다.
	 */
	@Test
	void 회차가_넘어가면_세_번으로_돌아간다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");
		assertEquals(8, state.rerollsRemaining);

		// GameStartManager.resetRunProgress 가 하는 일 그대로다.
		state.rerollsRemaining = state.rerollAllowance;

		assertEquals(3, state.rerollsRemaining);
		assertEquals(3, decode(encode(state)).rerollsRemaining, "저장을 왕복해도 되살아나지 않는다");
	}

	/**
	 * 세트가 풀리면 원래대로 돌아간다.
	 *
	 * <p>「환골탈태」가 보유 목록을 통째로 갈아엎는 경우다. 그 연쇄도 결국
	 * {@code PerkGrantChain.run} 의 끝을 지나므로 그 자리에서 몫이 0 으로 내려간다.
	 */
	@Test
	void 세트가_풀리면_원래대로_돌아간다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");
		assertEquals(8, state.rerollsRemaining);

		state.ownedPerks.remove("sharedfate:gamble_b");
		grant(state, "sharedfate:plain");

		assertEquals(0, state.rerollSetBonus);
		assertEquals(3, state.rerollsRemaining, "아직 안 쓴 5회는 그 자리에서 사라진다");
	}

	@Test
	void 세트가_풀려도_이미_쓴_횟수는_그대로다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");
		state.rerollsRemaining = 2; // 여덟 번 중 여섯 번을 썼다.

		state.ownedPerks.remove("sharedfate:gamble_b");
		grant(state, "sharedfate:plain");

		assertEquals(0, state.rerollSetBonus);
		assertEquals(2, state.rerollsRemaining, "상한만 내려갈 뿐, 남은 것을 더 뺏지는 않는다");
	}

	// ------------------------------------------------------------------ 도박 2 — 상한

	@Test
	void 회차당_상한만큼_굴리는_팀은_더_받지_못한다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		state.rerollAllowance = TeamCreationSettings.MAX_REROLL_COUNT;
		state.rerollsRemaining = TeamCreationSettings.MAX_REROLL_COUNT;

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		assertEquals(0, state.rerollSetBonus);
		assertEquals(TeamCreationSettings.MAX_REROLL_COUNT, state.rerollsRemaining,
				"상한을 넘겨 주느니 안 준다");
	}

	@Test
	void 상한을_넘지_않는_만큼만_받는다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		// 허용치를 상한 바로 아래에 두면 세트 몫이 남은 칸만큼만 들어간다.
		int allowance = TeamCreationSettings.MAX_REROLL_COUNT - 2;
		state.rerollAllowance = allowance;
		state.rerollsRemaining = allowance;

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		assertEquals(2, state.rerollSetBonus, "5회를 다 얹으면 상한을 넘는다");
		assertEquals(TeamCreationSettings.MAX_REROLL_COUNT, state.rerollsRemaining);
		assertEquals(TeamCreationSettings.MAX_REROLL_COUNT,
				decode(encode(state)).rerollsRemaining);
	}

	@Test
	void 다시_뽑기를_안_쓰기로_한_팀도_세트로는_받는다(@TempDir Path dir) throws IOException {
		// 허용치 0 은 「기본 지급량이 0」이지 「다시 뽑기 금지」가 아니다. 세트로 직접 얻은
		// 몫까지 막으면 그 팀에게 도박 2단계는 설명만 있고 아무 일도 없는 보상이 된다.
		load(dir);
		TeamState state = team();
		state.rerollAllowance = 0;
		state.rerollsRemaining = 0;

		grant(state, "sharedfate:gamble_a", "sharedfate:gamble_b");

		assertEquals(5, state.rerollSetBonus);
		assertEquals(5, state.rerollsRemaining);
		assertEquals(5, decode(encode(state)).rerollsRemaining);
	}

	// ------------------------------------------------------------------ 도박 3 — 프리즘만

	@Test
	void 도박_증강_셋을_모으면_프리즘_전용이_켜진다(@TempDir Path dir) throws IOException {
		writeSetsWithThirdTier(dir);
		TeamState state = team();
		assertFalse(PrismRerollEffect.heldBy(state), "아직 아무것도 없다");

		state.ownedPerks.add("sharedfate:gamble_a");
		state.ownedPerks.add("sharedfate:gamble_b");
		assertFalse(PrismRerollEffect.heldBy(state), "둘로는 2단계까지다");

		state.ownedPerks.add("sharedfate:gamble_c");
		assertTrue(PrismRerollEffect.heldBy(state));
	}

	@Test
	void 세트가_없으면_등급은_원래대로다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = team();
		state.ownedPerks.add("sharedfate:plain");

		assertFalse(PrismRerollEffect.heldBy(state));
	}

	@Test
	void 증강을_끈_팀에는_두_보상_모두_걸리지_않는다(@TempDir Path dir) throws IOException {
		writeSetsWithThirdTier(dir);
		TeamState state = team();
		state.perksEnabled = false;
		state.ownedPerks.add("sharedfate:gamble_a");
		state.ownedPerks.add("sharedfate:gamble_b");
		state.ownedPerks.add("sharedfate:gamble_c");

		assertEquals(0, ExtraRerollsEffect.bonusOf(state));
		assertFalse(PrismRerollEffect.heldBy(state));
	}

	/**
	 * 프리즘이 모자라면 <b>적게</b> 준다 — 골드를 섞지 않는다.
	 *
	 * <p>{@code PerkDraft.fallbackOrder(PRISM)} 이 프리즘 → 골드 → 실버라, 프리즘 등급으로
	 * 세 장을 뽑으면 아직 안 가진 프리즘이 모자랄 때 골드가 따라 들어온다. 「프리즘만 나옵니다」
	 * 라고 적어 놓고 골드를 보여 주면 설명이 거짓이 되므로 걸러 낸다.
	 */
	@Test
	void 프리즘이_모자라면_골드를_섞지_않고_적게_준다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{ "perks": [
				  { "id": "sharedfate:only_prism", "rarity": "prism", "name": "외톨이프리즘",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:gold_a", "rarity": "gold", "name": "골드가",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:gold_b", "rarity": "gold", "name": "골드나",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:gold_c", "rarity": "gold", "name": "골드다",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				] }
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		// applyReroll 이 실제로 지나가는 두 줄 그대로다.
		List<String> drawn = PerkDraft.draw(PerkRarity.PRISM, PerkMilestones.MAX,
				PerkRegistry.all(), List.of(), List.of(), RandomSource.create(7L), 3);
		assertEquals(3, drawn.size(), "폴백이 골드로 세 장을 채운다 — 이것이 함정이다");

		List<String> filtered = PerkManager.onlyPrism(drawn);

		assertEquals(List.of("sharedfate:only_prism"), filtered,
				"프리즘 한 장만 남고 골드는 버린다");
	}

	@Test
	void 프리즘이_하나도_없으면_후보가_비어_다시_뽑기가_실패한다(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{ "perks": [
				  { "id": "sharedfate:gold_a", "rarity": "gold", "name": "골드가",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:gold_b", "rarity": "gold", "name": "골드나",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				] }
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		List<String> drawn = PerkDraft.draw(PerkRarity.PRISM, PerkMilestones.MAX,
				PerkRegistry.all(), List.of(), List.of(), RandomSource.create(7L), 3);

		// 후보가 비면 applyReroll 은 아무것도 바꾸지 않고 횟수도 깎지 않는다.
		assertTrue(PerkManager.onlyPrism(drawn).isEmpty());
	}

	@Test
	void 풀에서_사라진_id_는_프리즘으로_보지_않는다(@TempDir Path dir) throws IOException {
		load(dir);

		assertTrue(PerkManager.onlyPrism(List.of("sharedfate:없어진증강")).isEmpty());
	}

	// ------------------------------------------------------------------ 도우미

	/** 증강 정의와 세트 정의를 함께 올린다. 도박 2·3단계가 들어 있다. */
	private static void load(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), PERKS, StandardCharsets.UTF_8);
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), SETS, StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);
	}

	/** 3단계까지 켤 수 있도록 도박 증강을 하나 더 얹어 올린다. */
	private static void writeSetsWithThirdTier(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{ "perks": [
				  { "id": "sharedfate:gamble_a", "rarity": "silver", "name": "도박가",
				    "set_types": [ "gamble" ],
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:gamble_b", "rarity": "silver", "name": "도박나",
				    "set_types": [ "gamble" ],
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:gamble_c", "rarity": "silver", "name": "도박다",
				    "set_types": [ "gamble" ],
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				] }
				""", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), SETS, StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);
	}

	/** 증강을 켠 갓 만든 팀. 회차당 3회다. */
	private static TeamState team() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		return state;
	}

	/**
	 * 증강을 고르는 사건을 흉내 낸다.
	 *
	 * <p>{@code PerkManager.commit} 이 하는 일 그대로다 — 보유 목록에 먼저 넣고
	 * {@code PerkGrantChain.run} 을 부른다. 서버와 팀은 {@code null} 로 넘긴다.
	 */
	private static void grant(TeamState state, String... perkIds) {
		for (String perkId : perkIds) {
			Perk perk = PerkRegistry.byId(perkId).orElseThrow();
			if (!state.ownedPerks.contains(perkId)) {
				state.ownedPerks.add(perkId);
			}
			PerkGrantChain.run(null, null, state, perk, RandomSource.create(1L));
		}
	}

	private static CompoundTag encode(TeamState state) {
		return (CompoundTag) TeamState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
	}

	private static TeamState decode(CompoundTag tag) {
		return TeamState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}
}
