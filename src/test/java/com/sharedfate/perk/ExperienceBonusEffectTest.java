package com.sharedfate.perk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.ExperienceBonusEffect;
import com.sharedfate.perk.effect.ExperienceBonusEffect.Source;
import com.sharedfate.perk.effect.LuckyOreEffect;
import com.sharedfate.sync.ExperienceBonus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 출처별 경험치 배율({@code experience_bonus})의 정의 읽기와 순수 계산.
 *
 * <p>실제로 곱하는 자리는 mixin 두 개라 살아 있는 서버 없이는 닿지 않는다. 대신 그 mixin 들이
 * 부르는 계산과 판정을 여기서 전부 못박는다 — 「어떤 값을 받아 주는가」와 「무엇을 광물이라
 * 부르는가」가 조용히 바뀌면 세트 보상 둘이 통째로 어긋난다.
 */
class ExperienceBonusEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 등록

	/**
	 * {@code PerkEffectType} 에 등록돼 있어야 정의 파일에서 쓸 수 있다.
	 *
	 * <p><b>이 시험이 지키는 것은 조용한 실패다.</b> 등록하는 한 줄을 빠뜨리면 클래스는 그대로
	 * 있고 빌드도 통과하는데, 세트 정의를 읽을 때 「알 수 없는 효과 type」 경고 한 줄만 남기고
	 * 그 단계가 통째로 사라진다. 게임 안에서는 「보상이 안 붙는다」로만 보인다.
	 */
	@Test
	void experience_bonus_가_효과_타입으로_등록돼_있다() {
		PerkEffectType type = PerkEffectType.fromId("experience_bonus");

		assertNotNull(type, "PerkEffectType 에 EXPERIENCE_BONUS 를 등록해야 한다");
		assertInstanceOf(ExperienceBonusEffect.class,
				type.create("test:perk", 0, json("""
						{ "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }""")),
				"등록은 됐는데 다른 클래스를 만들고 있다");
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 정상적인_정의를_읽는다() {
		ExperienceBonusEffect ore = parse("""
				{ "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }""");
		assertEquals(Source.ORE, ore.source());
		assertEquals(1.5, ore.multiplier(), 1.0e-9);

		ExperienceBonusEffect mob = parse("""
				{ "type": "experience_bonus", "source": "mob", "multiplier": 1.5 }""");
		assertEquals(Source.MOB, mob.source());

		ExperienceBonusEffect any = parse("""
				{ "type": "experience_bonus", "source": "any", "multiplier": 2.0 }""");
		assertEquals(Source.ANY, any.source());
	}

	@Test
	void 대소문자와_앞뒤_공백은_봐준다() {
		assertEquals(Source.ORE, parse("""
				{ "type": "experience_bonus", "source": "  ORE ", "multiplier": 1.5 }""").source());
	}

	/** 모르는 값을 조용히 {@code any} 로 넘기면 광물 전용 보상이 모든 경험치에 걸린다. */
	@Test
	void 모르는_source_는_정의를_버린다() {
		for (String raw : new String[] {"block", "ores", "mobs", "player", "", "  "}) {
			assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
					{ "type": "experience_bonus", "source": "%s", "multiplier": 1.5 }"""
					.formatted(raw))), raw);
		}
	}

	@Test
	void source_가_없거나_문자열이_아니면_버린다() {
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "multiplier": 1.5 }""")));
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "source": 3, "multiplier": 1.5 }""")));
	}

	@Test
	void multiplier_가_없거나_범위를_벗어나면_버린다() {
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "source": "ore" }""")), "값이 없다");
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "source": "ore", "multiplier": 0 }""")), "0 배");
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "source": "ore", "multiplier": -1.5 }""")), "음수");
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "source": "ore", "multiplier": 1000 }""")), "상한 초과");
		assertNull(ExperienceBonusEffect.fromJson("test:perk", 0, json("""
				{ "type": "experience_bonus", "source": "ore", "multiplier": "1.5" }""")), "문자열");
	}

	// ------------------------------------------------------------------ 출처 판정

	@Test
	void ore_는_몹_경험치에_걸리지_않는다() {
		ExperienceBonusEffect ore = parse("""
				{ "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }""");

		assertTrue(ore.appliesTo(Source.ORE));
		assertFalse(ore.appliesTo(Source.MOB));
		assertFalse(ore.appliesTo(null));
		assertEquals(1.5, ore.multiplierFor(Source.ORE), 1.0e-9);
		assertEquals(1.0, ore.multiplierFor(Source.MOB), 1.0e-9, "해당 없으면 그냥 곱해도 되게 1.0");
	}

	@Test
	void any_는_광물에도_몹에도_걸린다() {
		ExperienceBonusEffect any = parse("""
				{ "type": "experience_bonus", "source": "any", "multiplier": 2.0 }""");

		assertEquals(2.0, any.multiplierFor(Source.ORE), 1.0e-9);
		assertEquals(2.0, any.multiplierFor(Source.MOB), 1.0e-9);
	}

	// ------------------------------------------------------------------ 배율 합성

	@Test
	void 배율이_하나도_없으면_1이다() {
		assertEquals(1.0, ExperienceBonus.sourceMultiplier(List.of(), Source.ORE), 1.0e-9);
		assertEquals(1.0, ExperienceBonus.sourceMultiplier(null, Source.ORE), 1.0e-9);
		assertEquals(1.0,
				ExperienceBonus.sourceMultiplier(List.<PerkEffect>of(parse("""
						{ "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }""")),
						null),
				1.0e-9);
	}

	@Test
	void 배율이_여럿이면_곱한다() {
		List<PerkEffect> effects = List.<PerkEffect>of(
				parse("""
						{ "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }"""),
				parse("""
						{ "type": "experience_bonus", "source": "any", "multiplier": 2.0 }"""),
				parse("""
						{ "type": "experience_bonus", "source": "mob", "multiplier": 3.0 }"""));

		assertEquals(3.0, ExperienceBonus.sourceMultiplier(effects, Source.ORE), 1.0e-9,
				"1.5 × 2.0 — 몹 전용은 안 센다");
		assertEquals(6.0, ExperienceBonus.sourceMultiplier(effects, Source.MOB), 1.0e-9,
				"2.0 × 3.0 — 광물 전용은 안 센다");
	}

	/** {@code experience_bonus} 가 아닌 효과가 섞여 있어도 그냥 지나간다. */
	@Test
	void 다른_종류의_효과는_배율에_끼어들지_않는다() {
		List<PerkEffect> effects = List.<PerkEffect>of(
				new PerkEffect() {
				},
				parse("""
						{ "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }"""));

		assertEquals(1.5, ExperienceBonus.sourceMultiplier(effects, Source.ORE), 1.0e-9);
	}

	// ------------------------------------------------------------------ 광물 판별

	/**
	 * 이름으로 적은 광물은 데이터팩 없이도 걸리고, 돌은 걸리지 않는다.
	 *
	 * <p>블록 <b>태그</b> 판정은 데이터팩이 올라와 있어야 실제로 걸린다({@link BlockSelector}).
	 * 그래서 여기서는 태그가 아니라 이름으로 적힌 항목으로 본다. 다이아몬드 광석처럼 태그로만
	 * 적힌 것은 {@link #다이아몬드_광석은_광물_목록에_들어_있다()} 가 목록 쪽에서 지킨다.
	 */
	@Test
	void 이름으로_적은_광물은_광물이고_돌은_아니다() {
		assertTrue(ExperienceBonusEffect.isOre(Blocks.ANCIENT_DEBRIS.defaultBlockState()));
		assertTrue(ExperienceBonusEffect.isOre(Blocks.NETHER_QUARTZ_ORE.defaultBlockState()));
		assertTrue(ExperienceBonusEffect.isOre(Blocks.NETHER_GOLD_ORE.defaultBlockState()));

		assertFalse(ExperienceBonusEffect.isOre(Blocks.STONE.defaultBlockState()));
		assertFalse(ExperienceBonusEffect.isOre(Blocks.DEEPSLATE.defaultBlockState()));
		assertFalse(ExperienceBonusEffect.isOre(Blocks.DIRT.defaultBlockState()));
		assertFalse(ExperienceBonusEffect.isOre(null));
	}

	/**
	 * 다이아몬드 광석은 {@code #minecraft:diamond_ores} 태그로 덮인다.
	 *
	 * <p>서버가 뜬 뒤에는 태그가 묶여 {@code isOre} 가 참을 돌려주지만, 시험에서는 태그가 아직
	 * 비어 있어 그것을 그대로 물을 수 없다. 그래서 <b>목록에 들어 있는지</b>로 대신 본다.
	 */
	@Test
	void 다이아몬드_광석은_광물_목록에_들어_있다() {
		List<TagKey<Block>> tags = ExperienceBonusEffect.oreSelector().tags();

		assertTrue(tags.contains(TagKey.create(Registries.BLOCK,
						Identifier.parse("minecraft:diamond_ores"))),
				"다이아몬드 광석을 덮는 태그가 빠지면 채굴 2단계가 반쪽이 된다");
		assertTrue(tags.contains(TagKey.create(Registries.BLOCK, Identifier.parse("c:ores"))),
				"규약 태그가 빠지면 다른 모드의 광석이 통째로 빠진다");
		assertFalse(ExperienceBonusEffect.oreSelector().matchesEverything(),
				"모든 블록에 걸리면 「광물에서만」이 아니게 된다");
	}

	/**
	 * 「광물」의 정의는 <b>한 곳에만</b> 적혀 있어야 한다.
	 *
	 * <p>{@link ExperienceBonusEffect#ORE_BLOCKS} 는 {@link LuckyOreEffect#DEFAULT_ORE_BLOCKS} 를
	 * 그대로 가리키고, 그 목록은 다시 {@code sharedfate:greedy_pickaxe}(욕심 많은 곡괭이)의
	 * {@code blocks} 배열과 같아야 한다. 어느 한쪽만 고치면 「욕심 많은 곡괭이는 터지는데
	 * 경험치는 안 붙는」 상태가 조용히 생긴다.
	 */
	@Test
	void 광물_목록은_욕심_많은_곡괭이와_같은_것을_가리킨다() throws IOException {
		assertSame(LuckyOreEffect.DEFAULT_ORE_BLOCKS, ExperienceBonusEffect.ORE_BLOCKS,
				"목록을 여기에 다시 적지 말 것");
		assertEquals(greedyPickaxeBlocks(), ExperienceBonusEffect.ORE_BLOCKS,
				"정의 파일의 「욕심 많은 곡괭이」와 어긋났다");
	}

	// ------------------------------------------------------------------ 도우미

	private static ExperienceBonusEffect parse(String raw) {
		PerkEffect effect = ExperienceBonusEffect.fromJson("test:perk", 0, json(raw));
		return assertInstanceOf(ExperienceBonusEffect.class, effect, raw);
	}

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	/** 번들 증강 정의에서 「욕심 많은 곡괭이」가 광물이라 부르는 목록을 그대로 읽는다. */
	private static List<String> greedyPickaxeBlocks() throws IOException {
		try (InputStream stream = ExperienceBonusEffectTest.class
				.getResourceAsStream("/sharedfate-perks-default.json")) {
			assertNotNull(stream, "번들에 sharedfate-perks-default.json 이 없다");
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement element : root.getAsJsonArray("perks")) {
				JsonObject perk = element.getAsJsonObject();
				if (!"sharedfate:greedy_pickaxe".equals(perk.get("id").getAsString())) {
					continue;
				}
				JsonArray blocks = perk.getAsJsonArray("effects").get(0).getAsJsonObject()
						.getAsJsonArray("blocks");
				List<String> values = new ArrayList<>(blocks.size());
				for (JsonElement entry : blocks) {
					values.add(entry.getAsString());
				}
				return values;
			}
		}
		throw new AssertionError("정의 파일에 sharedfate:greedy_pickaxe 가 없다");
	}
}
