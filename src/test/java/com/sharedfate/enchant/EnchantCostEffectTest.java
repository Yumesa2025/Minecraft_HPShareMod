package com.sharedfate.enchant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetRegistry;
import com.sharedfate.perk.effect.EnchantCostEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.DataSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code enchant_cost} 증강이 인챈트 다이아몬드 값을 <b>팀별로</b> 바꾸는지 본다.
 *
 * <p>여기서 지키려는 약속은 넷이다.
 *
 * <ul>
 *   <li>증강도 세트도 없는 팀은 지금까지와 완전히 같다 —
 *       {@value EnchantmentDiamondCost#DIAMONDS_PER_ENCHANT} 개</li>
 *   <li>증강을 가진 팀만 그 값이 된다</li>
 *   <li><b>세트 단계에 적은 {@code enchant_cost} 도 똑같이 먹힌다.</b> 그리고 증강 할인과 세트
 *       할인을 둘 다 가지면 {@value EnchantmentDiamondCost#DIAMONDS_WITH_BOTH_DISCOUNTS} 개다</li>
 *   <li><b>툴팁에 적히는 숫자도 함께</b> 바뀐다. 10개라고 써 놓고 2개만 걷으면 버그로 보인다</li>
 * </ul>
 *
 * <p>실제로 인챈트 탁자에서 걷는 부분은 {@link EnchantmentDiamondCostTest} 가 진짜 메뉴로
 * 확인한다. 여기서는 그 코드가 쓰는 판단(몇 개인가)과 화면 쪽 전달 통로를 본다.
 */
class EnchantCostEffectTest {
	/** 증강이 없을 때의 값. 이 숫자가 달라지면 시험이 아니라 게임이 바뀐 것이다. */
	private static final int DEFAULT_COST = EnchantmentDiamondCost.DIAMONDS_PER_ENCHANT;

	/**
	 * 실제 기본 풀에 적혀 있는 그대로다 — <b>프리즘</b>, 다이아몬드 <b>2개</b>.
	 *
	 * <p>여기를 실물과 다르게 적어 두면 「증강을 가진 팀이 얼마를 내는가」라는 이 시험의
	 * 물음이 뜻을 잃는다. 개수는 {@code sharedfate-perks-default.json} 의
	 * {@code sharedfate:arcane_workshop} 과 맞춰 둔다.
	 */
	private static final String ARCANE_WORKSHOP = """
			{ "id": "sharedfate:arcane_workshop", "rarity": "prism", "name": "비술 공방",
			  "effects": [ { "type": "enchant_cost", "diamonds": 2 } ] }
			""";

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
		// 화면용 값은 정적이라 다음 시험으로 새어 나가면 안 된다.
		EnchantmentDiamondCost.resetShown();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 다이아몬드_개수를_읽는다() {
		assertEquals(1, cost("{ \"type\": \"enchant_cost\", \"diamonds\": 1 }").diamonds());
		assertEquals(0, cost("{ \"type\": \"enchant_cost\", \"diamonds\": 0 }").diamonds(),
				"0 이면 공짜다");
	}

	@Test
	void 개수가_없거나_범위를_벗어나면_효과를_만들지_않는다() {
		assertNull(create("{ \"type\": \"enchant_cost\" }"));
		assertNull(create("{ \"type\": \"enchant_cost\", \"diamonds\": -1 }"));
		assertNull(create("{ \"type\": \"enchant_cost\", \"diamonds\": \"한 개\" }"));
		assertNull(create("{ \"type\": \"enchant_cost\", \"diamonds\": %d }"
				.formatted(EnchantCostEffect.MAX_DIAMONDS + 1)));
	}

	@Test
	void 팀원에게는_아무것도_붙이지_않는다() {
		EnchantCostEffect effect = cost("{ \"type\": \"enchant_cost\", \"diamonds\": 1 }");

		effect.apply(null);
		effect.remove(null);
		assertEquals(1.0, effect.damageDealtMultiplier());
		assertEquals(1.0, effect.damageTakenMultiplier());
	}

	// ------------------------------------------------------------------ 팀별 값

	@Test
	void 증강이_없으면_기본값이다() {
		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(null));
		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(TeamState.fresh(20.0F)));
	}

	@Test
	void 증강을_가진_팀만_값이_바뀐다(@TempDir Path dir) throws IOException {
		loadPool(dir, ARCANE_WORKSHOP);

		TeamState without = perkTeam();
		TeamState with = perkTeam();
		with.ownedPerks.add("sharedfate:arcane_workshop");

		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(without),
				"증강이 없는 팀은 지금과 완전히 같아야 한다");
		assertEquals(2, EnchantmentDiamondCost.forState(with));
	}

	@Test
	void 증강을_꺼_두었으면_기본값이다(@TempDir Path dir) throws IOException {
		loadPool(dir, ARCANE_WORKSHOP);

		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:arcane_workshop");
		state.perksEnabled = false;

		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(state));
	}

	/**
	 * <b>같은 출처</b>에서 여럿이면 가장 싼 쪽이 이긴다.
	 *
	 * <p>여기 있는 셋은 모두 보유 증강이다. 출처가 하나뿐이므로 「둘 다 가지면 1」 규칙은
	 * 걸리지 않는다 — 걸린다면 할인 증강을 둘 이상 가진 팀이 전부 1개가 돼 버린다.
	 */
	@Test
	void 같은_출처에서_여럿을_가지면_가장_싼_쪽이_이긴다(@TempDir Path dir) throws IOException {
		loadPool(dir, """
				{ "id": "sharedfate:cheap", "rarity": "gold", "name": "싼 쪽",
				  "effects": [ { "type": "enchant_cost", "diamonds": 3 } ] },
				{ "id": "sharedfate:cheaper", "rarity": "gold", "name": "더 싼 쪽",
				  "effects": [ { "type": "enchant_cost", "diamonds": 2 } ] },
				{ "id": "sharedfate:pricey", "rarity": "gold", "name": "비싼 쪽",
				  "effects": [ { "type": "enchant_cost", "diamonds": 12 } ] }
				""");

		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:pricey");
		state.ownedPerks.add("sharedfate:cheap");
		state.ownedPerks.add("sharedfate:cheaper");

		assertEquals(2, EnchantmentDiamondCost.forState(state),
				"보유 순서가 답을 바꾸면 안 되고, 한 출처뿐이니 1 이 되어서도 안 된다");
	}

	/** 보유 증강 하나가 0 이면 공짜다. 하한 1 은 두 할인이 겹쳤을 때만 걸린다. */
	@Test
	void 증강_하나가_0이면_공짜다(@TempDir Path dir) throws IOException {
		loadPool(dir, """
				{ "id": "sharedfate:free", "rarity": "gold", "name": "공짜",
				  "effects": [ { "type": "enchant_cost", "diamonds": 0 } ] }
				""");

		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:free");

		assertEquals(0, EnchantmentDiamondCost.forState(state));
	}

	@Test
	void 풀에서_사라진_증강은_건너뛴다() {
		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:없어진증강");

		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(state));
	}

	// ------------------------------------------------------------- 세트와 겹쳤을 때

	/**
	 * 사람이 정한 표 네 줄을 그대로 못박는다.
	 *
	 * <table border="1">
	 *   <caption>규칙</caption>
	 *   <tr><td>아무것도 없음</td><td>10</td></tr>
	 *   <tr><td>세트만 (채굴 4단계, 5)</td><td>5</td></tr>
	 *   <tr><td>증강만 (비술 공방, 2)</td><td>2</td></tr>
	 *   <tr><td>둘 다</td><td>1</td></tr>
	 * </table>
	 *
	 * <p>가운데 두 줄과 마지막 줄은 {@link EnchantmentDiamondCost#forState} 가
	 * {@code PerkSetEffects.activeEffectsOf} 를 이어 훑어야만 나온다. 그 한 줄을 빼면 세트만 가진
	 * 팀은 10, 둘 다 가진 팀은 2 가 되어 여기서 잡힌다.
	 */
	@Test
	void 증강과_세트를_둘_다_가지면_1개다(@TempDir Path dir) throws IOException {
		loadPoolAndSets(dir);

		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(perkTeam()),
				"아무것도 없으면 10");
		assertEquals(5, EnchantmentDiamondCost.forState(miningTeam()),
				"세트만 가지면 세트가 적은 값 그대로다");
		assertEquals(2, EnchantmentDiamondCost.forState(arcaneTeam()),
				"증강만 가지면 증강이 적은 값 그대로다");

		TeamState both = miningTeam();
		both.ownedPerks.add("sharedfate:arcane_workshop");

		assertEquals(1, EnchantmentDiamondCost.forState(both),
				"둘 다 가지면 어느 쪽 값도 아닌 1 이다");
		assertEquals(EnchantmentDiamondCost.DIAMONDS_WITH_BOTH_DISCOUNTS,
				EnchantmentDiamondCost.forState(both));
	}

	/** 세트 단계가 하나 모자라면 켜지지 않는다. 증강만 가진 값 그대로다. */
	@Test
	void 세트가_하나_모자라면_증강_값_그대로다(@TempDir Path dir) throws IOException {
		loadPoolAndSets(dir);

		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:arcane_workshop");
		state.ownedPerks.add("sharedfate:mining1");
		state.ownedPerks.add("sharedfate:mining2");
		state.ownedPerks.add("sharedfate:mining3");

		assertEquals(2, EnchantmentDiamondCost.forState(state),
				"채굴이 셋이면 4단계가 안 켜지므로 겹치지 않는다");
	}

	/**
	 * 세트 쪽에 {@code enchant_cost} 가 둘이어도 그것만으로는 1 이 아니다.
	 *
	 * <p>같은 출처끼리는 종전대로 가장 싼 쪽이다. 「효과가 둘이면 1」로 잘못 구현하면 여기서
	 * 잡힌다.
	 */
	@Test
	void 세트_안에서_여럿이면_가장_싼_쪽이_이긴다(@TempDir Path dir) throws IOException {
		writePerks(dir);
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), """
				{ "sets": [
				  { "type": "mining", "tiers": [
				      { "count": 2, "name": "채굴 2단계",
				        "effects": [ { "type": "enchant_cost", "diamonds": 7 } ] },
				      { "count": 4, "name": "채굴 4단계",
				        "effects": [ { "type": "enchant_cost", "diamonds": 5 } ] }
				  ] }
				] }
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);

		assertEquals(5, EnchantmentDiamondCost.forState(miningTeam()),
				"2단계와 4단계가 함께 켜져도 세트 쪽 값은 가장 싼 5 다");
	}

	/** 증강을 꺼 두면 세트도 함께 꺼진다. */
	@Test
	void 증강을_꺼_두면_세트_할인도_사라진다(@TempDir Path dir) throws IOException {
		loadPoolAndSets(dir);

		TeamState state = miningTeam();
		state.ownedPerks.add("sharedfate:arcane_workshop");
		state.perksEnabled = false;

		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forState(state));
	}

	// ------------------------------------------------------------------ 화면 쪽 전달

	@Test
	void 서버가_내려보낸_값을_화면_경로가_그대로_쓴다() {
		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.forSlot(0), "받은 것이 없으면 기본값");

		EnchantmentDiamondCost.rememberShown(1);

		assertEquals(1, EnchantmentDiamondCost.forSlot(0));
		assertEquals(1, EnchantmentDiamondCost.forSlot(2));
		assertEquals(Arrays.toString(new int[] {1, 1, 0}),
				Arrays.toString(EnchantmentDiamondCost.displayCosts(new int[] {3, 12, 0})),
				"후보가 없는 칸의 0 은 그대로 남는다");
	}

	@Test
	void 범위_밖의_값을_받으면_기본값으로_물러난다() {
		EnchantmentDiamondCost.rememberShown(-1);
		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.shownDiamonds());

		EnchantmentDiamondCost.rememberShown(EnchantCostEffect.MAX_DIAMONDS + 1);
		assertEquals(DEFAULT_COST, EnchantmentDiamondCost.shownDiamonds());
	}

	@Test
	void 데이터_칸이_값을_주고받는다() {
		// 주인이 서버 쪽 플레이어가 아니면(클라이언트) 받은 값을 그대로 기억한다.
		DataSlot slot = new EnchantmentCostDataSlot(null);

		assertEquals(DEFAULT_COST, slot.get());
		slot.set(1);
		assertEquals(1, EnchantmentDiamondCost.shownDiamonds());
		assertEquals(1, slot.get(), "받은 값이 곧 화면이 그릴 값이다");
	}

	// ------------------------------------------------------------------ 툴팁

	@Test
	void 툴팁_숫자도_함께_바뀐다() {
		assertEquals("다이아몬드 " + DEFAULT_COST + "개", rewritten("container.enchant.level.one"));

		EnchantmentDiamondCost.rememberShown(1);

		assertEquals("다이아몬드 1개", rewritten("container.enchant.level.one"));
		assertEquals("다이아몬드 1개", rewritten("container.enchant.level.many", 3));
	}

	@Test
	void 모자랄_때_뜨는_줄도_함께_바뀐다() {
		EnchantmentDiamondCost.rememberShown(1);

		// 인자는 화면 Mixin 이 이미 다이아몬드 개수로 바꿔 둔 costs[칸] 이다.
		assertEquals("다이아몬드 1개가 필요합니다",
				rewritten("container.enchant.level.requirement", 1));
	}

	// ------------------------------------------------------------------ 도우미

	private static String rewritten(String key, Object... args) {
		Component line = Component.translatable(key, args);
		return EnchantmentDiamondTooltip.rewriteLine(line).getString();
	}

	private static TeamState perkTeam() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		return state;
	}

	/** 비술 공방만 가진 팀. */
	private static TeamState arcaneTeam() {
		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:arcane_workshop");
		return state;
	}

	/** 채굴 유형 증강을 넷 가진 팀. 세트 정의가 올라가 있으면 채굴 4단계가 켜진다. */
	private static TeamState miningTeam() {
		TeamState state = perkTeam();
		for (int index = 1; index <= 4; index++) {
			state.ownedPerks.add("sharedfate:mining" + index);
		}
		return state;
	}

	private static void loadPool(Path dir, String perksJson) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME),
				"{ \"perks\": [" + perksJson + "] }", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		assertTrue(PerkRegistry.isLoaded(), "증강 풀을 읽지 못했다");
	}

	/**
	 * 비술 공방과 채굴 증강 넷, 그리고 채굴 4단계 세트를 함께 올린다.
	 *
	 * <p>개수(증강 2 · 세트 5)는 실물 정의와 맞춰 둔다. 여기를 실물과 다르게 적으면 이 시험의
	 * 물음이 뜻을 잃는다.
	 */
	private static void loadPoolAndSets(Path dir) throws IOException {
		writePerks(dir);
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), """
				{ "sets": [
				  { "type": "mining", "tiers": [
				      { "count": 4, "name": "채굴 4단계",
				        "effects": [ { "type": "enchant_cost", "diamonds": 5 } ] }
				  ] }
				] }
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);
		assertTrue(PerkRegistry.isLoaded(), "증강 풀을 읽지 못했다");
		assertFalse(PerkSetRegistry.isEmpty(), "세트 정의를 읽지 못했다");
	}

	/**
	 * 비술 공방 + 채굴 유형 껍데기 넷.
	 *
	 * <p>껍데기 증강의 효과는 세트 판정과 아무 상관이 없지만, {@link PerkRegistry} 가 효과 없는
	 * 증강을 버리므로 상관없는 효과를 하나씩 넣어 둔다.
	 */
	private static void writePerks(Path dir) throws IOException {
		StringBuilder perks = new StringBuilder(ARCANE_WORKSHOP);
		for (int index = 1; index <= 4; index++) {
			perks.append(",\n")
					.append("{ \"id\": \"sharedfate:mining").append(index)
					.append("\", \"rarity\": \"silver\", \"name\": \"채굴").append(index)
					.append("\", \"set_types\": [ \"mining\" ],")
					.append(" \"effects\": [ { \"type\": \"damage_dealt\", \"multiplier\": 1.2 } ] }");
		}
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME),
				"{ \"perks\": [" + perks + "] }", StandardCharsets.UTF_8);
	}

	private static EnchantCostEffect cost(String json) {
		return assertInstanceOf(EnchantCostEffect.class, create(json));
	}

	private static PerkEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.ENCHANT_COST.create("sharedfate:테스트", 0, parsed);
	}
}
