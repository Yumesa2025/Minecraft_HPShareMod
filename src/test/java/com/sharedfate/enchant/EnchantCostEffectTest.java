package com.sharedfate.enchant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code enchant_cost} 증강이 인챈트 다이아몬드 값을 <b>팀별로</b> 바꾸는지 본다.
 *
 * <p>여기서 지키려는 약속은 셋이다.
 *
 * <ul>
 *   <li>증강이 없는 팀은 지금까지와 완전히 같다 —
 *       {@value EnchantmentDiamondCost#DIAMONDS_PER_ENCHANT} 개</li>
 *   <li>증강을 가진 팀만 그 값이 된다</li>
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

	@Test
	void 여럿을_가지면_가장_싼_쪽이_이긴다(@TempDir Path dir) throws IOException {
		loadPool(dir, """
				{ "id": "sharedfate:cheap", "rarity": "gold", "name": "싼 쪽",
				  "effects": [ { "type": "enchant_cost", "diamonds": 1 } ] },
				{ "id": "sharedfate:cheaper", "rarity": "gold", "name": "더 싼 쪽",
				  "effects": [ { "type": "enchant_cost", "diamonds": 0 } ] },
				{ "id": "sharedfate:pricey", "rarity": "gold", "name": "비싼 쪽",
				  "effects": [ { "type": "enchant_cost", "diamonds": 12 } ] }
				""");

		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:pricey");
		state.ownedPerks.add("sharedfate:cheap");
		state.ownedPerks.add("sharedfate:cheaper");

		assertEquals(0, EnchantmentDiamondCost.forState(state),
				"보유 순서가 답을 바꾸면 안 된다");
	}

	@Test
	void 풀에서_사라진_증강은_건너뛴다() {
		TeamState state = perkTeam();
		state.ownedPerks.add("sharedfate:없어진증강");

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

	private static void loadPool(Path dir, String perksJson) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME),
				"{ \"perks\": [" + perksJson + "] }", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		assertTrue(PerkRegistry.isLoaded(), "증강 풀을 읽지 못했다");
	}

	private static EnchantCostEffect cost(String json) {
		return assertInstanceOf(EnchantCostEffect.class, create(json));
	}

	private static PerkEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.ENCHANT_COST.create("sharedfate:테스트", 0, parsed);
	}
}
